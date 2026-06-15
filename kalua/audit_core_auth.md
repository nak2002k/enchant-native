# core:auth Audit

**Module:** `/home/nsk/project/personal/Enchant/frontend/core/auth/src/main/java/org/enchant/core/auth/`
**Files examined:** `AuthManager.kt`, `AuthRepository.kt`, `AuthStateMachine.kt`
**Date:** 2026-05-29

---

## Security Issues

### S1 — JWT Stored in Plaintext EncryptedSharedPreferences (Critical)

**Location:** `AuthManager.kt:123-126`, `AuthRepository.kt:175-176`, `SecurePreferences` usage throughout

**Finding:** JWT access tokens and refresh tokens are stored via `SecurePreferences.putString("auth.jwt", ...)`. While `SecurePreferences` uses `EncryptedSharedPreferences` with AES-256-GCM (correct), the JWT payload itself is **never signed or integrity-checked** by the client. Any tampering with the ciphertext (bit flip) will silently produce a modified JWT that the client will use.

**Risk:** If an attacker gains read access to the device's encrypted storage, they can inject a modified JWT with an extended expiry or different `did` claim. The client will accept it.

**Recommendation:**
1. Store a HMAC-SIV of the JWT (using a key derived from the device key in `KeyStoreManager`) alongside the JWT. Validate integrity before use.
2. Alternatively, drop JWT storage entirely and use session-backed auth (rotate a session secret on the server, store only an opaque session ID).

---

### S2 — No Signature Verification on JWT Structure

**Location:** `AuthRepository.kt:54-63`, `AuthStateMachine.kt:155-165`, `WebSocketManager.kt:437-447`

**Finding:** `extractDeviceIdFromJwt()` parses the JWT payload by splitting on `.` and Base64-decoding the payload. No signature verification is performed. Any field in the JWT payload (`exp`, `did`, `sub`) can be forged by a client-side attacker who modifies the stored JWT.

**Recommendation:** Either:
1. Implement full JWT verification using the server's JWKS endpoint (fetched via `fetchJwks()`) with EdDSA signature verification — but this requires ensuring JWKS is fetched before auth decisions are made.
2. Stop trusting JWT payload claims entirely; extract device ID only from server responses (the `device_id` returned by `verifyOtp` is trusted because it comes directly from the server).

---

### S3 — AuthInterceptor Token Refresh Race Condition

**Location:** `AuthInterceptor.kt:18-76`

**Finding:** The `refreshing` flag is set inside a `synchronized(lock)` block, but the wait loop at lines 59-66 uses `lock.wait()` — which releases the lock during wait. However, the flag is only cleared inside `synchronized(lock)` in the `finally` block. This creates a scenario where:

1. Thread A (401 response) acquires lock, sets `refreshing = true`, releases lock, calls `refreshToken()`
2. Thread B (another 401) acquires lock, sees `refreshing = true`, enters wait loop, calls `lock.wait()` (releases lock)
3. `refreshToken()` completes, clears `refreshing = false`, calls `lock.notifyAll()`
4. Thread B wakes but may find `refreshing = false` but the `currentToken` is still being set
5. Thread B reads `currentToken` which may be set OR may be null if there's a timing issue

**Additional issue:** The `currentToken` field is read with `synchronized(lock)` at line 28 but written with `synchronized(lock)` at line 49 — this is correct for visibility. However, if the token refresh fails (returns null), `currentToken` is set to null at line 49, but the retry at line 53 uses `newToken` (which is null), causing an unnecessary second request with a null token.

**Recommendation:** Simplify by removing the interceptor-based refresh entirely. Perform token refresh at a higher level (in `ApiClient` or a dedicated `AuthManager` method) before making requests, rather than in the interceptor hot path.

---

### S4 — OTP Cooldown Stored in SharedPreferences (Tamperable)

**Location:** `AuthManager.kt:138-141`

**Finding:**
```kotlin
private var lastOtpRequestMs: Long
    get() = SecurePreferences.getLong("auth.last_otp_request", 0L)
    set(value) = SecurePreferences.putLong("auth.last_otp_request", value)
private val otpCooldownMs = 30_000L
```

The 30-second OTP cooldown is enforced client-side only. An attacker can clear `auth.last_otp_request` via `adb shell pm clear` or by manipulating SharedPreferences, bypassing the cooldown.

**Recommendation:**
1. Treat client-side cooldown as UX only (prevent accidental double-sends), not a security control.
2. The server must enforce rate limits on `POST /v1/auth/request-otp`.

---

### S5 — Logout Silently Swallows All Exceptions

**Location:** `AuthManager.kt:181-194`

**Finding:**
```kotlin
suspend fun logout() {
    try {
        repository?.logout()
    } catch (e: Exception) {
    }
    // ... clear credentials
}
```

The server logout call failure is silently ignored. An attacker with man-in-the-middle position could prevent the logout call from reaching the server, leaving the session valid server-side while the client believes it is logged out.

**Recommendation:**
1. On logout failure, at minimum log the error.
2. Consider invalidating the local session even if server logout fails — but never clear credentials without attempting server-side logout first (the current behavior is correct in intent, but the silent catch is wrong).

---

### S6 — No TLS Certificate Pinning

**Finding:** Neither `ApiClient` nor `AuthInterceptor` implement certificate pinning. The OkHttp client is configured with default trust settings.

**Recommendation:**
1. At minimum, enforce TLS 1.2+ and remove fallback to older versions.
2. Implement certificate pinning for the gateway domain.

---

## Bugs

### B1 — Race Condition: Concurrent RefreshToken Calls in AuthInterceptor

**Location:** `AuthInterceptor.kt:39-76`

**Scenario:** Two requests fail with 401 simultaneously. Thread A enters the refresh block, sets `refreshing = true`. Thread B enters the wait loop, waits up to 10 seconds. If refresh succeeds quickly, Thread B gets the token and retries. This works. However:

If Thread A's `refreshToken()` throws and returns null:
1. Thread A sets `currentToken = null` (line 49) inside synchronized
2. Thread A sets `refreshing = false` (line 56) inside synchronized
3. Thread A calls `lock.notifyAll()`
4. Thread B wakes, sees `refreshing == false`, exits wait loop
5. Thread B reads `currentToken` — it is `null` (set by Thread A)
6. Thread B calls `chain.proceed()` with original request (no auth header) — fails again

**No maximum refresh retry limit** — if the server returns 401 repeatedly, the interceptor will refresh on every 401.

---

### B2 — WebSocketManager tryRefreshJwt Swallows All Exceptions

**Location:** `WebSocketManager.kt:450-481`

```kotlin
} catch (e: Exception) { Log.w("WS", "JWT check failed: ${e.message}"); null }
```

If JWT refresh fails, the error is logged at WARN level (not ERROR) and null is returned. The connection fails silently with `AUTH_FAILED` state. This is logged but no alert is raised to the application layer.

---

### B3 — AuthStateMachine.validateRestoredState Catches All Exceptions

**Location:** `AuthStateMachine.kt:181-183`

```kotlin
} catch (_: Exception) {
    RegistrationState.Welcome
}
```

Any failure during the refresh token validation (network, parse, server error) results in returning `Welcome` state silently. The user is logged out with no feedback about why.

---

### B4 — extractDeviceIdFromJwt Returns Empty String on All Errors

**Location:** `AuthRepository.kt:54-63`

```kotlin
} catch (_: Exception) { "" }
```

A malformed JWT (wrong Base64, wrong JSON structure) returns `""` as device ID. This is used in `verifyOtp` to set `deviceId` in `AuthResponse`. If the server returns a valid JWT but with a `did` field the client cannot parse, the user's device ID is silently set to empty string. Any subsequent operations requiring the device ID will fail or use wrong values.

---

### B5 — ApiClient Singleton Can Be Reset But Not Properly Reinitialized

**Location:** `AuthManager.kt:65-71`

```kotlin
fun resetForTesting() {
    initialized = false
    repository = null
    apiClient = null
    _currentState.value = RegistrationState.Welcome
    _authState.value = AuthState.Unknown
}
```

This method is for testing but clears `apiClient = null` while the `AuthInterceptor` singleton holds a reference to the old `ApiClient` instance (via `ApiClient.getInstance()`). If `resetForTesting()` is called in production (or test not properly), subsequent API calls may fail with "ApiClient not initialized" at the interceptor level, not at `AuthManager` level.

---

### B6 — Empty JWT Parts Cause Partial Processing

**Location:** `AuthStateMachine.kt:156-162`

```kotlin
val parts = jwt.split(".")
if (parts.size == 3) {
    // parse
}
```

If a JWT has 3 parts but any part is empty (e.g., `a..c`), the code proceeds to parse `parts[1]` as empty string, Base64-decode an empty string (produces empty byte array), parse as JSON `{}`, extract `exp` which will be null, leading to `exp = 0L` and `System.currentTimeMillis() / 1000 < 0` is always false — meaning an empty payload JWT is treated as **permanently expired**.

---

### B7 — Device Info Sent in verifyOtp Before Server Validates

**Location:** `AuthRepository.kt:30-34`

```kotlin
if (deviceId != null) {
    put("device_info", buildJsonObject {
        put("device_id", deviceId)
        put("user_agent", "Enchant-Android/${AppConfig.appVersion}")
    })
}
```

The `deviceId` sent here is extracted from a JWT that was not server-verified (see S2). An attacker could send a different `device_id` than the server issued. The server should validate that the `device_id` matches the device that originally requested the OTP challenge.

---

## Completeness Gaps

### C1 — Missing Account WhoAmI Endpoint

**Location:** `AuthRepository.kt` — not implemented

**Recommendation:** Add `AuthRepository.whoAmI(): Result<UserInfo>` that calls `GET /v1/accounts/whoami`.

---

### C2 — Missing Username Reservation/Confirmation

**Location:** `AuthManager.kt` — no username registration flow

**Recommendation:** Implement username reservation (5-minute hold) + confirmation with proof generated from randomness.

---

### C3 — Missing Registration Lock (2FA/PIN)

**Location:** Not implemented anywhere in auth module

**Recommendation:** Implement registration lock endpoints for PIN-based account recovery.

---

### C4 — Missing Phone Number Change

**Location:** Not implemented

**Recommendation:** Implement phone number change flow with proper device notification.

---

### C5 — Missing FCM Token Management

**Location:** Not implemented in auth module

**Recommendation:** Add FCM token management endpoints for push notification registration.

---

### C6 — No Concurrent Device Linking (Secondary Device Registration)

**Location:** Not implemented

**Recommendation:** Consider whether multi-device is in scope. If yes, implement device linking with verification code.

---

### C7 — Keys API: No Repeated-Use Prekey Check

**Location:** `AuthRepository.kt:146-167`

Enchant has `registerKeys()`, `rotateSignedPreKey()`, `uploadOpks()`, `getOpkCount()`. The reference implementation uses `checkRepeatedUseKeysSync()` which checks whether local repeated-use prekeys match the server's view (all-or-nothing SHA-256 digest comparison).

**Recommendation:** Implement `checkRepeatedUseKeys()` to detect prekey reuse before it causes delivery failures.

---

### C8 — Keys API: No Last-Resort Kyber Prekey Handling

**Location:** `AuthRepository.kt:146-167`

The reference implementation's `KeysApi.setPreKeysSync()` includes `lastResortKyberPreKey` and `oneTimeKyberPreKeys`. Enchant's `KeyRegisterRequest` includes `signedPrekey` and `oneTimePrekeys` but the model may not properly handle Kyber (post-quantum) keys.

**Recommendation:** Verify whether `SignedPrekeyData` includes a key ID and signature algorithm identifier.

---

### C9 — No SVR (Secure Value Recovery) Integration

**Location:** Not in auth module

**Recommendation:** Determine if SVR is in scope. If yes, implement PIN-based SVR for encrypted storage backup.

---

### C10 — fetchJwks Returns Empty Map on All Errors (Silent Failure)

**Location:** `AuthRepository.kt:125-143`

```kotlin
} catch (e: Exception) {
    Result.success(emptyMap())
}
```

If JWKS fetch fails, the function returns `Result.success(emptyMap())` — which tells the caller "keys fetched successfully (with no keys)". Any cryptographic operation relying on this will fail, but the caller won't know why. This masks network failures and parsing errors.

**Recommendation:** Return `Result.failure()` on error to properly propagate failure to callers.

---

## Code Quality Issues

### Q1 — Magic Strings Everywhere

**Locations:**
- `"auth.jwt"`, `"auth.refresh_token"`, `"auth.user_id"`, `"auth.device_id"` — repeated 20+ times
- `"auth.last_otp_request"` — not consolidated
- `"/v1/auth/request-otp"`, `"/v1/auth/verify-otp"`, `"/v1/auth/refresh"`, `"/v1/auth/logout"` — hardcoded
- `"crypto.identity_key"`, `"crypto.signed_prekey"` — in `logout()`

**Recommendation:** Define a constants object:
```kotlin
object AuthConstants {
    const val JWT_KEY = "auth.jwt"
    const val REFRESH_TOKEN_KEY = "auth.refresh_token"
    // ...
}
```

---

### Q2 — AuthStateMachine Uses Object (Singleton) Not Injectable

**Location:** `AuthStateMachine.kt:70`

```kotlin
object AuthStateMachine {
    private val _currentState = MutableStateFlow<RegistrationState>(...)
```

This is a global singleton. It cannot be mocked in tests, cannot have its dependencies injected, and stores mutable state globally. This violates the dependency injection principle and makes testing require global state manipulation.

**Recommendation:** Convert to a class with constructor-injected dependencies. Use a provider pattern for the state flow.

---

### Q3 — AuthManager Uses Object (Singleton) With Side Effects in init()

**Location:** `AuthManager.kt:44-63`

```kotlin
suspend fun init() {
    if (initialized) return  // global mutable flag
    if (apiClient == null) { /* create ApiClient */ }
    // side effect: validates stored JWT, may refresh, updates _authState
    val storedState = AuthStateMachine.validateRestoredState(apiClient!!)
    _currentState.value = storedState
    // ...
}
```

`init()` has hidden side effects: it may make network calls (token refresh) and modify global state. It can only be called once per process lifetime. This is not testable without mocking static dependencies.

**Recommendation:**
1. Make `init()` idempotent and return the result instead of mutating global state.
2. Use a proper `AuthManager.Factory` or dependency injection.

---

### Q4 — AuthStateMachine.validateRestoredState Creates New AuthRepository

**Location:** `AuthStateMachine.kt:171`

```kotlin
val repo = AuthRepository(apiClient)
```

This creates a NEW `AuthRepository` instance inside a suspend function called during initialization. This is wasteful and means the `AuthRepository` used for refresh is not the same instance that `AuthManager` uses. If `AuthManager` has a different `AuthRepository` instance, their state may diverge.

**Recommendation:** Pass the `AuthRepository` instance as a parameter or use the same instance from `AuthManager`.

---

### Q5 — OTP Expiry Not Enforced

**Location:** `AuthStateMachine.kt:52-53`, `AuthManager.kt:96-99`

```kotlin
RegistrationState.OtpVerification(
    challengeId = otpResponse.challengeId,
    identifier = identifier,
    expiresAt = System.currentTimeMillis() + (otpResponse.expiresIn * 1000L)
)
```

The `expiresAt` field is stored but never checked. If the user enters an OTP code after expiry, the `verifyOtp` call will still be made to the server. The server should reject it, but the client doesn't check locally.

**Recommendation:** Check `System.currentTimeMillis() > expiresAt` before allowing OTP submission. Show a "Code expired, request a new one" state.

---

### Q6 — Inconsistent Error Handling in logout() and deleteAccount()

**Location:** `AuthRepository.kt:82-88`, `116-122`

```kotlin
// logout — swallows exception, returns success
suspend fun logout(): Result<Unit> {
    return try {
        apiClient.post("/v1/auth/logout")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.success(Unit)  // silently
    }
}

// deleteAccount — swallows exception, returns success
suspend fun deleteAccount(): Result<Unit> {
    return try {
        apiClient.del("/v1/auth/account")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.success(Unit)  // silently
    }
}
```

Both methods silently swallow all exceptions. But `AuthManager.deleteAccount()` (line 196-206) returns the `Result` from the repository while also calling `logout()` which itself swallows exceptions. The net result is that account deletion failures are hidden from the caller.

**Recommendation:** Return `Result.failure(e)` on network errors. The caller can decide whether to proceed with local cleanup.

---

### Q7 — No Timeout on WebSocket Authentication

**Location:** `WebSocketManager.kt:271-295`

```kotlin
private suspend fun authenticate(ws: WebSocket, jwt: String): Boolean {
    // ...
    try {
        return withTimeoutOrNull(10000L) {
            val response = deferred.await()
            response.status == 200
        } ?: false
    }
}
```

The 10-second timeout on WebSocket auth is good. However, if the server never sends a response (network partition after sending the auth frame), the pending request is never removed (line 294 runs in the `finally`). But if the outer `withTimeoutOrNull` returns null (timeout), the function returns `false` — the connection is considered failed and the pending request for auth is orphaned in `pendingRequests` until disconnect.

**Recommendation:** In the timeout case, complete the deferred exceptionally so the pending request is cleaned up.

---

### Q8 — AuthStateMachine.applyEvent Is Redundant

**Location:** `AuthStateMachine.kt:74-131`, `133-135`

```kotlin
fun applyEvent(state: RegistrationState, event: RegistrationEvent): RegistrationState { ... }
fun transition(current: RegistrationState, event: RegistrationEvent): RegistrationState {
    return applyEvent(current, event)
}
```

`transition()` just calls `applyEvent()`. There's no polymorphic behavior, no base class, no additional logic. This is dead code.

---

### Q9 — AuthManager.requestOtp Returns Result<Unit> but caller expects Result<OtpResponse>

**Location:** `AuthManager.kt:73-111`

The `requestOtp` method returns `Result<Unit>` to the caller. The actual `OtpResponse` (containing `challengeId` and `expiresIn`) is stored in `_currentState` but not returned. The caller cannot access the `challengeId` if they need it programmatically — they must read it from `AuthManager.currentState.value`.

**Recommendation:** Return `Result<OtpResponse>` from `requestOtp`.

---

### Q10 — Sensitive Data in Logs

**Location:** `AuthInterceptor.kt:116`

```kotlin
Log.w("AuthInterceptor", "Refresh failed: ${e.message}")
```

The error message from a failed token refresh is logged. If the exception message contains any sensitive data (rare, but possible if a custom exception includes token fragments), it would appear in the log. No scrubber is applied to this log statement.

**Recommendation:** Apply log scrubbing to redact sensitive values from all auth-related log statements.

---

## Reference Implementation Comparison

### Authentication Model

The reference app uses `Basic <username>:<password>` style credentials with static credentials from a `CredentialsProvider` — tokens are not the auth mechanism at all. The server issues a "recovery password" (an account catalog password) used for authentication.

Enchant uses `Bearer <JWT>` with client-side JWT parsing for identity claims. This creates attack surface that the reference implementation avoids:
1. No client-side parsing of identity claims — identity is established via the server's `Basic` header
2. No JWT storage attack surface
3. No signature verification vulnerabilities

### Registration Session Model

The reference app uses a session-based registration:
- `POST /v1/verification/session` — creates a session
- `GET /v1/verification/session/{id}` — polls session status
- `PATCH /v1/verification/session/{id}` — submit push token / captcha
- `PUT /v1/verification/session/{id}/code` — submit SMS verification code
- `POST /v1/registration` — final account creation with prekeys

Enchant uses a simpler challenge-based OTP:
- `POST /v1/auth/request-otp` — gets challenge_id + expires_in
- `PUT /v1/auth/verify-otp` — submits OTP, gets JWT + device_id

Enchant's model is simpler but less robust against:
- Session hijacking (no session ID to revoke)
- Concurrent verification attempts (no server-side session state)

### Key Management

The reference app has sophisticated prekey management:
- `ACI` and `PNI` (Account Identity + Phone Number Identity) separate key sets
- `lastResortKyberPreKey` for PQ encryption
- `checkRepeatedUseKeysSync` to detect prekey reuse before messaging failures

Enchant has basic prekey upload but:
- No PNI key management
- No repeated-use detection
- No Kyber (post-quantum) prekeys

---

## Recommendations (Prioritized)

### P0 — Critical Security Fixes

1. **Remove client-side JWT claim parsing for identity decisions.** Use the `device_id` returned directly from `verifyOtp` response body — which is server-trusted — not the `did` claim extracted from a JWT the client stored itself.

2. **Implement JWT integrity verification** using JWKS (EdDSA signature) before trusting any JWT payload fields. Or, remove JWT storage entirely and use session-backed auth.

3. **Remove AuthInterceptor refresh logic from hot path.** Perform token refresh at application layer, not inside OkHttp interceptor. This eliminates race conditions and makes retry logic testable.

4. **Add certificate pinning** to the OkHttp client for the gateway domain.

### P1 — High Priority Bugs

5. **Fix silent exception swallowing** in `logout()`, `deleteAccount()`, `fetchJwks()`, and `validateRestoredState()`. Return proper `Result.failure()` to callers.

6. **Add local OTP expiry enforcement** — check `expiresAt` before allowing OTP submission.

7. **Add JWKS fetch error propagation** — `fetchJwks()` should return `Result.failure()` on error, not `Result.success(emptyMap())`.

8. **Fix the AuthInterceptor race condition** — remove the complex wait/notify pattern and use a simpler approach (e.g., a `Mutex` with timeout).

### P2 — Completeness

9. **Implement `/v1/accounts/whoami`** — fetch current account info.

10. **Implement username reservation + confirmation** — two-phase commit with server-side hold.

11. **Implement registration lock** (2FA PIN for account recovery).

12. **Add `checkRepeatedUseKeys()`** to detect prekey reuse.

13. **Add FCM token management** endpoints.

### P3 — Code Quality

14. **Consolidate magic strings** into `AuthConstants` object.

15. **Convert `AuthStateMachine` and `AuthManager` from object singletons to injectable classes.**

16. **Return `Result<OtpResponse>` from `requestOtp()`** instead of `Result<Unit>`.

17. **Remove redundant `transition()` method** in `AuthStateMachine`.

18. **Apply log scrubbing** to all auth-related log statements.

---

*End of Audit*