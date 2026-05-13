# Phase 2 — Auth & Onboarding

## Overview

Build the complete registration flow: state machine, all 15+ screens, key generation, profile creation. After this phase, users can register, log in, and are ready to message.

**Architecture pattern:** Event-driven MVVM — same as Signal Android. A sealed `RegistrationEvent` class + immutable `RegistrationState` + pure reducer `applyEvent(state, event) → state` + ViewModel connecting UI to state machine.

**Estimated files:** 35 files
**Backend endpoints used:** Auth service (8001), IKS (8002), Profile (8008)
**Prerequisites:** Phase 1 (crypto, network, database, DI)

---

## Backend API Contracts (Complete)

### POST /v1/auth/request-otp
**Auth:** None
**Rate limit:** 10/h per IP, 20/24h per identifier
**Request:** `{"identifier": "+15551234567"}` — E.164 format (`^\+[1-9]\d{1,14}$`)
**Response 200:** `{"challenge_id": "uuid", "expires_in": 600}`
**Errors:** 400 (missing/invalid identifier), 429 (rate limit with `retry_after`)
**Notes:** New request invalidates previous active challenge for same identifier

### POST /v1/auth/verify-otp
**Auth:** None
**Request:** `{"challenge_id": "uuid", "otp": "123456", "device_info": {"device_id": "uuid?", "user_agent": "string?"}}`
- device_id: optional UUID, must not be claimed by another user. If invalid/absent, server generates.
- user_agent: optional, max 255 printable ASCII. Defaults to "SecureChat/1.0.0" if missing.
**Response 200:** `{"user_id": "uuid", "access_token": "jwt", "refresh_token": "base64url", "expires_in": 900}`
- JWT claims: `{sub: user_id, did: device_id, iat: epoch, exp: epoch+900, jti: unique}`
- Refresh token: base64url 32 random bytes, 90 day expiry
**Errors:** 400 (missing fields), 401 (invalid OTP), 404 (challenge not found), 410 (already used), 429 (4+ failed attempts, challenge consumed)
**Pattern:** Peek-verify — OTP validated but NOT consumed until tokens issued. consume_otp() called after issue_tokens() succeeds.

### POST /v1/auth/refresh
**Rate limit:** 120/h per IP
**Request:** `{"refresh_token": "base64url"}`
**Response 200:** `{"access_token": "new_jwt", "refresh_token": "new_base64url", "expires_in": 900}`
**Notes:** Token ROTATED on every refresh. Old one revoked, new one issued.

### POST /v1/auth/logout
**Auth:** JWT required
**Response 200:** `{"status": "logged_out"}`
**Notes:** Revokes ALL refresh tokens for user+device. Local data cleared even if network fails.

### GET /v1/auth/devices
**Auth:** JWT required
**Response 200:** `{"devices": [{"device_id": "uuid", "user_agent": "...", "issued_ts": "...", "last_used_ts": "..."}]}`

### DELETE /v1/auth/devices/{device_id}
**Auth:** JWT required
**Response 200:** `{"status": "device_revoked"}`
**Errors:** 400 (invalid UUID), 401

### DELETE /v1/auth/account
**Auth:** JWT required
**Response 200:** `{"status": "account_deleted"}`
**Notes:** Soft-deletes user account. Revokes ALL tokens. Client MUST also clear all local data.

### GET /v1/auth/.well-known/jwks.json
**Auth:** None
**Response 200:** `{"keys": [{"kty": "OKP", "crv": "Ed25519", "use": "sig", "kid": "securechat-signing-key-1", "x": "base64url"}]}`

### POST /v1/keys/register
**Auth:** JWT required
**Rate limit:** 5/h per user
**Request:** `{"identity_key": "base64url_32", "signed_prekey": {"public_key": "base64url_32", "signature": "base64url_64"}, "one_time_prekeys": [{"public_key": "base64url_32"}, ...]}`
**Constraints:**
- identity_key: exactly 32 bytes decoded
- signed_prekey.public_key: exactly 32 bytes decoded
- signed_prekey.signature: exactly 64 bytes decoded (Ed25519(identity_private_key, spk_public))
- one_time_prekeys: min 20 entries, each 32 bytes decoded. Invalid entries silently skipped.
**Response 201:** `{"device_id": "uuid"}`
**Errors:** 400 (missing fields, invalid sizes), 409 (device already registered for this IK), 422 (signature verification failed), 429
**Notes:** SPK signature verified server-side. Device ID from JWT `did` claim if valid UUID.

### PUT /v1/keys/signed-prekey
**Auth:** JWT required
**Request:** `{"public_key": "base64url_32", "signature": "base64url_64"}`
**Response 200:** `{"status": "ok"}`
**Errors:** 400 (missing/sizes), 404 (device not found), 422 (signature fail)
**Notes:** Old SPK deactivated (not deleted). Appends leaf to Key Transparency tree.

### POST /v1/keys/one-time-prekeys
**Auth:** JWT required
**Rate limit:** 10/day per device
**Request:** `{"one_time_prekeys": [{"public_key": "base64url_32"}, ...]}`
**Constraints:** Max 100 entries. Current + new ≤ 200 total. Invalid entries silently skipped.
**Response 200:** `{"total_opks": N}`

### GET /v1/keys/opk-count
**Auth:** JWT required
**Response 200:** `{"remaining": N}`

### GET /v1/profile/{user_id}
**Auth:** JWT required
**Rate limit:** 100/min per device
**Response 200:** `{"user_id": "uuid", "username": "string", "display_name": "string?", "about": "string?", "avatar_media_id": "uuid?", "avatar_key": "base64url?", "last_seen": "timestamp?", "online": bool?}`

### PUT /v1/profile
**Auth:** JWT required
**Rate limit:** 30/h per device
**Request:** `{"username": "john_doe", "display_name": "John", "about": "Hello"}`
**Constraints:** username `^[a-z0-9_]{3,32}$`, display_name 1-64, about max 139
**Response 200:** `{"updated": true}`
**Errors:** 400 (invalid format, username taken)

### GET /v1/profile/search?username=prefix
**Auth:** JWT required
**Rate limit:** 20/min per device

---

## Architecture: Event-Driven Auth State Machine

Modeled after Signal Android's registration flow. Central state machine that all screens share.

### RegistrationEvent (sealed class)
```
sealed class RegistrationEvent {
    data object ResetState
    data object NavigateToWelcome
    data object NavigateToPhoneEntry
    data class CountryCodeSelected(val countryCode: Int, val regionCode: String, val countryName: String, val countryEmoji: String)
    data class PhoneNumberChanged(val nationalNumber: String)
    data object PhoneNumberSubmitted
    data class OtpCodeEntered(val code: String)
    data object ResendOtp
    data object WrongPhoneNumber
    data object TermsAccepted
    data object PermissionsGranted
    data class ProfileDataEntered(val displayName: String, val about: String?, val avatarUri: Uri?)
    data class UsernameEntered(val username: String)
    data object KeysGenerated
    data object RegistrationComplete
    data object PinCreated(val pin: String)
    data object RestoreDecisionMade(val shouldRestore: Boolean)
}
```

### RegistrationState (sealed)
```
sealed class RegistrationState {
    data object Welcome : RegistrationState()
    data object PhoneEntry : RegistrationState()
    data class OtpVerification(val challengeId: String, val identifier: String, val expiresAt: Long) : RegistrationState()
    data object Permissions : RegistrationState()
    data object ProfileSetup : RegistrationState()
    data object UsernamePicker : RegistrationState()
    data object KeyGeneration : RegistrationState()
    data object PinCreation : RegistrationState()
    data class RestorePrompt(val hasBackup: Boolean, val backupInfo: BackupInfo?) : RegistrationState()
    data object Complete : RegistrationState()
    data class Error(val message: String, val retryAfter: Long?) : RegistrationState()
    data object Loading : RegistrationState()
}
```

### AuthViewModel (the reducer)
```
fun applyEvent(state: RegistrationState, event: RegistrationEvent): RegistrationState
fun processEvent(event: RegistrationEvent)  // Calls applyEvent + side effects
```

---

## File Manifest

### `core/auth/src/main/java/org/enchant/core/auth/AuthStateMachine.kt`
The central state machine — event definitions, state definitions, and the pure reducer function.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `RegistrationEvent` | sealed class | All possible auth events | — |
| `RegistrationState` | sealed class | All possible auth screens/states | — |
| `applyEvent` | `fun applyEvent(state: RegistrationState, event: RegistrationEvent): RegistrationState` | Pure reducer — takes current state + event, returns new state | Every state+event combination must be handled; unknown combinations return current state unchanged |
| `getRequiredPermissions` | `fun getRequiredPermissions(): List<String>` | Returns permissions (POST_NOTIFICATIONS, CAMERA, MICROPHONE, READ_CONTACTS) based on API level | API < 33 → no POST_NOTIFICATIONS; API < 23 → no runtime permissions |
| `validateRestoredState` | `suspend fun validateRestoredState(): RegistrationState` | On app restart, validate stored auth tokens — if expired but refresh valid, refresh; if both expired, go to Welcome | Token absent → Welcome; token valid → Complete; refresh valid → refresh silently; refresh fails → Welcome |

**Test requirements:** 15 tests — every event+state transition tested, reducer purity verified, permission API levels correct, validateRestoredState covers all token scenarios

---

### `core/auth/src/main/java/org/enchant/core/auth/AuthRepository.kt`
All network calls for auth operations. Wraps ApiClient calls with proper error handling.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `requestOtp` | `suspend fun requestOtp(identifier: String): Result<OtpResponse>` | POST /v1/auth/request-otp | 429 → expose retry_after; network → retry once; 400 → surface error to user |
| `verifyOtp` | `suspend fun verifyOtp(challengeId: String, otp: String, deviceId: String?): Result<AuthResponse>` | POST /v1/auth/verify-otp | 401 with remaining attempts → surface count; 410 → challenge expired → go back to phone entry; 429 after 4 fails → show max attempts |
| `refreshToken` | `suspend fun refreshToken(refreshToken: String): Result<RefreshResponse>` | POST /v1/auth/refresh | Invalid token → clear auth, return failure; rate limited → wait and retry |
| `logout` | `suspend fun logout(): Result<Unit>` | POST /v1/auth/logout | Network failure → still clear local data (fire-and-forget) |
| `listDevices` | `suspend fun listDevices(): Result<List<DeviceInfo>>` | GET /v1/auth/devices | Empty list is valid; parse each device correctly |
| `revokeDevice` | `suspend fun revokeDevice(deviceId: String): Result<Unit>` | DELETE /v1/auth/devices/{id} | Cannot revoke own device → return success with warning |
| `deleteAccount` | `suspend fun deleteAccount(): Result<Unit>` | DELETE /v1/auth/account | Must clear ALL local data after (even if network fails) |
| `fetchJwks` | `suspend fun fetchJwks(): Result<JwksResponse>` | GET /v1/auth/.well-known/jwks.json | Used for offline JWT verification |
| `registerKeys` | `suspend fun registerKeys(request: KeyRegisterRequest): Result<String>` | POST /v1/keys/register | Already registered → still success; 422 sig verify → regenerate |
| `rotateSignedPreKey` | `suspend fun rotateSignedPreKey(publicKey: String, signature: String): Result<Unit>` | PUT /v1/keys/signed-prekey | — |
| `uploadOpks` | `suspend fun uploadOpks(prekeys: List<OneTimePrekeyData>): Result<Int>` | POST /v1/keys/one-time-prekeys | Max 100 per call, rate limited to 10/day |
| `getOpkCount` | `suspend fun getOpkCount(): Result<Int>` | GET /v1/keys/opk-count | — |

**Test requirements:** 18 tests — each endpoint success, each error code handled, retry behavior, JWT injection

---

### `core/auth/src/main/java/org/enchant/core/auth/AuthManager.kt`
High-level auth manager used by all screens. Wraps AuthRepository + AuthStateMachine + SecureStorage.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `init` | `suspend fun init()` | Load stored credentials, validate session, set initial state | No stored → Welcome; stored + valid → Complete; stored + expired → auto-refresh |
| `requestOtp` | `suspend fun requestOtp(identifier: String): Result<Unit>` | Call repo, on success transition to OtpVerification state with challengeId | Rate limited → show countdown; network → retry option |
| `verifyOtp` | `suspend fun verifyOtp(code: String): Result<Unit>` | Call repo, on success store JWT+refresh in SecurePreferences, transition to next screen | Invalid → show remaining attempts; expired → go back |
| `resendOtp` | `suspend fun resendOtp(): Result<Unit>` | Call requestOtp again with same identifier | Must wait 30s between resends → enforce client-side |
| `refreshToken` | `suspend fun refreshToken(): Boolean` | Auto-refresh JWT when expiring within 60s | Silent — no UI impact; fail → transition to Welcome |
| `logout` | `suspend fun logout()` | Call API + clear SecurePreferences + clear database + clear KeyStore + reset DI | Even if API fails, local data MUST be cleared |
| `deleteAccount` | `suspend fun deleteAccount(): Result<Unit>` | Call API + same cleanup as logout | Show confirmation dialog first |
| `registerKeys` | `suspend fun registerKeys(): Result<Unit>` | Generate keys via KeyManager → call registerKeys API | Already registered → skip; 422 → regenerate IK |
| `updateProfile` | `suspend fun updateProfile(username: String, displayName: String, about: String?): Result<Unit>` | Validate locally first, then call PUT /v1/profile | Username validation → 3-32 chars lowercase+underscore; display_name → 1-64; about → max 139 |
| `searchUsername` | `suspend fun searchUsername(prefix: String): Result<List<User>>` | GET /v1/profile/search with debounce (300ms) | Empty prefix → return empty; network → cached results |
| `getCurrentState` | `val currentState: StateFlow<RegistrationState>` | Observable current auth state | — |
| `authState` | `val authState: StateFlow<AuthState>` | Observable high-level auth status (Unknown/Authenticated/Unauthenticated) | — |

**AuthState for app-level use:**
```kotlin
sealed class AuthState {
    data object Unknown : AuthState()
    data object Unauthenticated : AuthState()
    data object Authenticating : AuthState()
    data class Authenticated(val userId: String, val deviceId: String) : AuthState()
}
```

**Test requirements:** 25 tests — full registration flow as integration test, each error path, token refresh, logout cleanup, account deletion

---

### `feature/auth/src/main/java/org/enchant/auth/screens/WelcomeScreen.kt`
**Route:** `/welcome` | **State:** `RegistrationState.Welcome`

Landing page with brand messaging, language picker, terms acceptance.

| UI Element | Behavior |
|---|---|
| App logo + name | Centered hero section |
| Language picker | Dropdown with supported locales (stored in SecurePreferences) |
| Terms & Privacy | Tappable text links |
| "Agree & Continue" button | Disabled until terms scrolled/accepted |
| "Restore or Transfer" link | Navigate to restore options |

**Events emitted:** `TermsAccepted`, `NavigateToRestore`
**Tests:** 6 — render, terms accept, continue tap, restore tap, language change, RTL layout

---

### `feature/auth/src/main/java/org/enchant/auth/screens/PhoneEntryScreen.kt`
**Route:** `/auth/phone` | **State:** `RegistrationState.PhoneEntry`

Phone number input with country code picker.

| UI Element | Behavior |
|---|---|
| Country code picker | Bottom sheet with search, scroll to current locale, flag emoji + name + code |
| Phone number input | Auto-format (AsYouTypeFormatter), max 15 digits after `+` |
| Validation | `^\+[1-9]\d{1,14}$` — real-time indicator |
| "Continue" button | Disabled until valid number entered |
| Loading overlay | On submit, show spinner |
| Error state | Rate limit → show countdown; network → retry |

| Function | Description | Constraint |
|---|---|---|
| `phoneNumberChanged(number: String)` | NFKC-normalized digits only, no formatting chars | Strip all non-digit except leading `+` |
| `countrySelected(code: Int, region: String, name: String, emoji: String)` | Update country prefix | Must persist selected country |
| `phoneNumberSubmitted()` | Validate → call AuthManager.requestOtp() | Must ensure E.164 format before sending |
| `getDefaultCountry()` | Use locale MCC or network MCC | Fallback to "US" if unavailable |

**Tests:** 10 — valid/invalid phone, country picker search, submit success, rate limited, network error, country codes, formatting, back button

---

### `feature/auth/src/main/java/org/enchant/auth/screens/OtpVerifyScreen.kt`
**Route:** `/auth/otp` | **State:** `RegistrationState.OtpVerification`

6-digit OTP entry with auto-submit, countdown timer, error handling.

| UI Element | Behavior |
|---|---|
| 6 individual digit fields | Text input per digit, auto-advance to next on entry, backspace goes back |
| Auto-submit | When all 6 digits filled → auto-submit after 500ms debounce |
| Countdown timer | 30s initial, 60s after each resend, displayed as "Resend in MM:SS" |
| Resend button | Enabled only when countdown = 0 |
| Error animation | Shake+clear on wrong code |
| Remaining attempts | Display "N attempts remaining" after each failure |
| "Wrong number" link | Navigate back to phone entry |
| Code paste support | Detect clipboard content matching 6 digits |

| Function | Description | Constraint |
|---|---|---|
| `codeChanged(index: Int, digit: Char)` | Move focus to next field, or wrap SMS auto-fill | Auto-advance only if digit valid |
| `codeSubmitted(code: String)` | Call AuthManager.verifyOtp() | Disable input during submission |
| `resendCode()` | Call AuthManager.resendOtp() | Reset countdown to 60s, max 5 resends |
| `handleSmsAutoFill()` | Register SMS Retriever API (API 26+) | Fallback to manual input if unavailable |
| `countdownTick()` | Decrement countdown every second | Auto-stop at 0 |
| `updateRateLimits(session: SessionMetadata)` | Extract retry info from session metadata | Adjust countdown based on server response |

**Error handling matrix:**
| Error | UX |
|---|---|
| Invalid code (401) | Shake fields, clear all 6 digits, show "Wrong code, X attempts left" |
| Challenge expired (410) | Navigate to phone entry, show "Code expired, please try again" |
| Rate limited (429) | Show retry_after countdown, keep current state |
| Network | Show retry dialog, keep code on retry |

**Tests:** 15 — digit entry, auto-advance, backspace, paste, submit success, wrong code (each attempt count), expired challenge, rate limited, resend countdown, max resends, SMS auto-fill registration

---

### `feature/auth/src/main/java/org/enchant/auth/screens/CountryCodePickerScreen.kt`
**Route:** `/country-code-picker`

Searchable country list with flag, name, code.

| Function | Description | Constraint |
|---|---|---|
| `search(query: String)` | Filter by country name, code, or "USA" special case | Case-insensitive, 150ms debounce |
| `countrySelected(country: Country)` | Return selected country to previous screen | — |
| `loadCountries()` | Load all countries from bundled data, sort by name | Include MCC fallback for current locale |

```kotlin
data class Country(val code: Int, val region: String, val name: String, val emoji: String)
```

**Tests:** 4 — render all, search filter, select, cancel

---

### `feature/auth/src/main/java/org/enchant/auth/screens/PermissionsScreen.kt`
**Route:** `/auth/permissions` | **State:** `RegistrationState.Permissions`

Request permissions progressively — app works without any grant.

| UI Element | Behavior |
|---|---|
| Permission cards | Each has icon, title, subtitle, Allow/Skip button |
| Permissions: Notifications, Microphone, Camera, Contacts | Ordered by importance |
| Notifications at top | Only shown on API 33+ (POST_NOTIFICATIONS) |
| "Continue" button | Enabled when at least shown (not necessarily granted) |
| "Not Now" | Dismiss individual permission |

| Function | Description | Constraint |
|---|---|---|
| `requestPermission(type: PermissionType)` | Launch system permission dialog | Must handle "Don't ask again" → show settings intent |
| `openAppSettings()` | Open app info screen for manual permission grant | — |
| `getRequiredPermissions()` | Return list based on API level | API 33+ adds POST_NOTIFICATIONS |
| `allPermissionDecided()` | True if user has actively granted or denied each permission | Never force — always allow skip |

**Tests:** 8 — each permission card renders, allow/deny flow, skip works, API level variants, settings intent, continue after skip

---

### `feature/auth/src/main/java/org/enchant/auth/screens/ProfileSetupScreen.kt`
**Route:** `/auth/profile` | **State:** `RegistrationState.ProfileSetup`

Avatar picker + name + about + auto-generated username.

| UI Element | Behavior |
|---|---|
| Avatar circle | Tap to open picker (gallery + camera). Show selected or placeholder |
| Display name | Required, 1-64 chars, counter |
| About | Optional, max 139 chars, counter |
| Username preview | Auto-generated: `name_random4digits`. Editable next screen |
| "Continue" button | Enabled when display name non-empty |

| Function | Description | Constraint |
|---|---|---|
| `onAvatarSelected(uri: Uri)` | Compress (max 5MB, JPEG/PNG) → upload via POST /v1/profile/avatar | Validate format (JPEG/PNG), resize to max 1024x1024 |
| `onDisplayNameChanged(name: String)` | Validate 1-64 chars | Trim whitespace |
| `onAboutChanged(about: String)` | Validate max 139 chars | Real-time counter |
| `generateUsername(name: String): String` | Lowercase alphanumeric + underscore, append `_NNNN` | Must match `^[a-z0-9_]{3,32}$` |
| `submitProfile()` | Call PUT /v1/profile with display_name + about + username, then POST /v1/profile/avatar if selected | Navigate to UsernamePickerScreen on success |

**Tests:** 8 — avatar selection, name validation (empty, too long), about counter, username generation uniqueness, submit success, upload error, navigation

---

### `feature/auth/src/main/java/org/enchant/auth/screens/UsernamePickerScreen.kt`
**Route:** `/auth/username` | **State:** `RegistrationState.UsernamePicker`

@handle with real-time availability check.

| UI Element | Behavior |
|---|---|
| Text field with @ prefix | Input in real-time, validate format |
| Availability indicator | Neutral → Validating → Available (green check) → Taken (red X) |
| Suggestions | If taken, show 3 alternatives |
| Skip button | Use auto-generated username from profile |

| Function | Description | Constraint |
|---|---|---|
| `onUsernameChanged(username: String)` | Validate `^[a-z0-9_]{3,32}$`, start 300ms debounced search | Convert to lowercase; strip invalid chars |
| `checkAvailability(username: String)` | GET /v1/profile/search?username= | Empty response = available; exact match = taken |
| `generateSuggestions(desired: String): List<String>` | Append _1, _2, _3; try random suffixes | Must all be valid format |
| `submitUsername(username: String)` | Call PUT /v1/profile with username | Navigate to KeyGeneration on success |

**Tests:** 10 — valid/invalid format, availability check (available, taken, network error), debounce behavior, suggestions generation, skip, submit success

---

### `feature/auth/src/main/java/org/enchant/auth/screens/KeyGenerationScreen.kt`
**Route:** `/auth/generating-keys` | **State:** `RegistrationState.KeyGeneration`

Animated key generation with 5-step progress, auto-redirect when done.

| Step | Backend Call | Duration |
|---|---|---|
| 1. Generating identity keys (Ed25519) | Local (KeyManager) | ~50ms |
| 2. Generating signed prekey (X25519 + Ed25519 sig) | Local (KeyManager) | ~50ms |
| 3. Generating 100 one-time prekeys (X25519) | Local (KeyManager) | ~200ms |
| 4. Uploading key bundle to server | POST /v1/keys/register | ~500ms |
| 5. Setting up local session store | Local DB writes | ~100ms |

| Function | Description | Must Handle |
|---|---|---|
| `startKeyGeneration()` | Run steps sequentially with progress updates | Any step fails → show error with retry; Step 4 fails → retry with exponential backoff |
| `handleRetry()` | Retry from step 4 (keys are already generated) | — |

**UI:** Animated progress bar (fills per step), checkmark + label per completed step, unclear instruction text per current step.
**On success:** Call `AuthManager.authState` → transition to `Authenticated` → navigate to Home screen.

**Tests:** 6 — full success, step 4 network failure → retry, step 1 crypto failure, UI updates correctly, progress animation, auto-redirect on complete

---

### `feature/auth/src/main/java/org/enchant/auth/screens/TwoStepPinScreen.kt`
**Route:** `/auth/two-step` | **State:** `RegistrationState.PinCreation`

Custom numpad with 6-dot indicator. (Shown only if PIN is required/recommended.)

| UI Element | Behavior |
|---|---|
| Custom numpad | Buttons 0-9, backspace, toggle alphanumeric |
| 6-dot indicator | Fills as PIN digits entered |
| Confirm PIN | Re-enter to confirm |
| Error | Mismatch → shake + clear |

| Function | Description |
|---|---|
| `onPinDigitPressed(digit: Int)` | Add digit to current input |
| `onBackspace()` | Remove last digit |
| `onPinComplete(pin: String)` | Call AuthManager.setPin(pin) → backup to server |

**Tests:** 6 — digit entry, backspace, confirm match, confirm mismatch, alphanumeric toggle, submit

---

### `feature/auth/src/main/java/org/enchant/auth/screens/RestorePromptScreen.kt`
**Route:** `/auth/restore-prompt` | **State:** `RegistrationState.RestorePrompt`

If backup exists, show info with Restore / Start Fresh options.

**Tests:** 4 — render backup info, restore tap, start fresh tap, no backup

---

### `feature/auth/src/main/java/org/enchant/auth/screens/AppLockScreen.kt`
**Route:** `/settings/security/app-lock` | **State:** Not in registration flow (settings)

PIN setup/entry with biometric toggle. Accessed from SecuritySettings.

| Function | Description |
|---|---|
| `setupPin(pin: String)` | Hash PIN with Argon2id → store hash in SecurePreferences |
| `verifyPin(pin: String): Boolean` | Compare Argon2id hash |
| `authenticateWithBiometric(): Boolean` | Use BiometricManager to authenticate |
| `isBiometricAvailable(): Boolean` | Check BiometricManager.canAuthenticate(BIOMETRIC_STRONG) |
| `setAppLockEnabled(enabled: Boolean)` | Toggle app lock on/off |
| `isLocked(): Boolean` | Check if app needs unlock (on resume from background, after timeout) |

**Tests:** 8 — PIN setup, PIN verify, wrong PIN, biometric auth success/fail, biometric availability, toggle, lock/unlock lifecycle

---

## Complete Navigation Route Map

```
/welcome                                    → WelcomeScreen
/auth/phone                                 → PhoneEntryScreen
/country-code-picker                        → CountryCodePickerScreen
/auth/otp                                   → OtpVerifyScreen
/auth/two-step                              → TwoStepPinScreen
/auth/permissions                           → PermissionsScreen
/auth/profile                               → ProfileSetupScreen
/auth/username                              → UsernamePickerScreen
/auth/generating-keys                       → KeyGenerationScreen
/auth/restore-prompt                        → RestorePromptScreen
/settings/security/app-lock                 → AppLockScreen
/settings/security                          → SecuritySettingsScreen (with app lock toggle)
```

---

## Module: `:core:push` (5 files)

**Purpose:** FCM push notification infrastructure — wake-up signal for message delivery. FCM never carries message payloads (consistent with Signal's security model). FCM tells the app to reconnect WebSocket and fetch pending messages.

### File: `core/push/src/main/java/org/enchant/core/push/FcmReceiveService.kt`

**Purpose:** Firebase Cloud Messaging service — receives push tokens and wake-up signals. Signal's `FcmReceiveService.java` equivalent.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `onMessageReceived` | `override fun onMessageReceived(message: RemoteMessage)` | FCM push received → trigger WebSocket reconnect | Never extract message payload from FCM (wake-up only). If app in foreground → immediate WS reconnect. If app in background → start FcmFetchForegroundService |
| `onNewToken` | `override fun onNewToken(token: String)` | FCM token refreshed → register with backend via POST /v1/push/register | Store token in SignalStore.registration |
| `onDeletedMessages` | `override fun onDeletedMessages()` | Server throttled messages → force full pending message poll | Called when FCM message limit exceeded |

**Manifest declaration:**
```xml
<service
    android:name=".push.FcmReceiveService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT"/>
    </intent-filter>
</service>
```

**Test requirements:** 4 tests — onMessageReceived triggers WS reconnect, onNewToken registers with backend, onDeletedMessages triggers poll, background message starts foreground service

### File: `core/push/src/main/java/org/enchant/core/push/FcmFetchManager.kt`

**Purpose:** Decides foreground vs background fetch strategy on FCM receipt. Signal's `FcmFetchManager.kt` equivalent.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `onFcmReceived` | `suspend fun onFcmReceived()` | Process FCM: if foreground → reconnect WS immediately; if background → start foreground service with notification | Must not block FCM broadcast receiver |
| `scheduleFetch` | `fun scheduleFetch()` | Schedule an immediate WS reconnect | Cancel any existing scheduled fetch first |
| `cancelFetch` | `fun cancelFetch()` | Cancel any pending fetch | Called when app opens |
| `isFetchScheduled` | `fun isFetchScheduled(): Boolean` | Check if fetch is pending | — |
| `notifyFcmRetryReceived` | `fun notifyFcmRetryReceived()` | Handle FCM retry signal from server | Reset backoff counter |

**Test requirements:** 5 tests — foreground triggers WS reconnect, background starts service, cancelFetch cancels pending, isFetchScheduled accurate, retry resets backoff

### File: `core/push/src/main/java/org/enchant/core/push/FcmFetchForegroundService.kt`

**Purpose:** Foreground service for fetching messages in background. Shows a low-priority notification while active.

| Function | Signature | Description |
|---|---|---|
| `onCreate` | `override fun onCreate()` | Create low-priority notification channel for fetch service |
| `onStartCommand` | `override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int` | Start foreground with notification → trigger WS reconnect → fetch pending messages → stop self when done |
| `onDestroy` | `override fun onDestroy()` | Clean up notification |

**Manifest declaration:**
```xml
<service
    android:name=".push.FcmFetchForegroundService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

**Test requirements:** 2 tests — creates notification, stops self after fetch

### File: `core/push/src/main/java/org/enchant/core/push/PushTokenRegistrar.kt`

**Purpose:** Manages FCM push token lifecycle — register/deregister with backend.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `registerWithBackend` | `suspend fun registerWithBackend(token: String)` | POST /v1/push/register with `{"token": "fcm_token", "platform": "ANDROID"}` | Network fails → retry via JobManager; already registered → skip |
| `deregisterFromBackend` | `suspend fun deregisterFromBackend()` | DELETE /v1/push/register | Called on logout |
| `getFcmToken` | `suspend fun getFcmToken(): String?` | Get current FCM token from Firebase | Firebase unavailable → null |
| `isPlayServicesAvailable` | `fun isPlayServicesAvailable(context: Context): Boolean` | Check Google Play Services availability | Return false on Huawei devices (no GMS) |

**Test requirements:** 4 tests — register success, register retry on failure, deregister on logout, GMS check (mock available/unavailable)

### File: `core/push/src/main/java/org/enchant/core/push/HuaweiPushFallback.kt`

**Purpose:** Fallback push handling for Huawei devices (no Google Play Services). Uses periodic REST polling instead of FCM.

| Function | Signature | Description |
|---|---|---|
| `isHuaweiDevice` | `fun isHuaweiDevice(): Boolean` | Check if running on Huawei device (no GMS) | Check `Build.MANUFACTURER` |
| `startPollingFallback` | `fun startPollingFallback()` | Start periodic polling: GET /v1/messages/pending every 30s | Use WorkManager with 30s interval; stop when app opens |
| `stopPollingFallback` | `fun stopPollingFallback()` | Stop periodic polling | Called when app foregrounds |

**Test requirements:** 3 tests — detect Huawei, start polling, stop polling

---

## Acceptance Criteria (expanded)

All existing criteria plus:
- [ ] FCM receive triggers WebSocket reconnection within 5 seconds
- [ ] FCM token registered on first launch and on token refresh
- [ ] Background FCM starts foreground service to fetch messages
- [ ] Push token deregistered on logout
- [ ] Huawei fallback polling works at 30s intervals
- [ ] No message payloads ever extracted from FCM messages (verified by test)
- [ ] All tests pass (target: 140+ tests across all files)
