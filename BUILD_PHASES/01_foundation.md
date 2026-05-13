# Phase 1 — Foundation: Core Infrastructure

## Overview

Build the entire core infrastructure layer: dependency injection, networking (REST + WebSocket), encrypted database, crypto protocol stores, and shared data models. Every other phase depends on this.

**Estimated files:** 32 files across 5 modules
**Estimated time:** 3-4 days for a focused agent
**Backend services used:** None directly (but prepares for Auth + IKS in Phase 2)

---

## Module: `:core:base` (5 files)

Application configuration, secure preferences, key store management, and dependency injection.

### File: `core/base/src/main/java/org/enchant/core/base/AppConfig.kt`

**Purpose:** Reads environment config, builds API URLs, holds app-wide constants.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `init` | `fun init(context: Context)` | Loads config from `BuildConfig` fields and SharedPreferences | Missing fields → default values; file not found → fallback |
| `gatewayUrl` | `val gatewayUrl: String` | Base URL for all REST API calls (e.g., `https://api.enchant.app`) | Configurable via secure store |
| `wsUrl` | `val wsUrl: String` | WebSocket URL derived from gatewayUrl (http→ws, https→wss) | Derived properly |
| `turnUrl` | `val turnUrl: String?` | TURN server URL for WebRTC calls | Null if not configured |
| `turnUsername` | `val turnUsername: String?` | TURN username | Null if not configured |
| `turnPassword` | `val turnPassword: String?` | TURN password | Null if not configured |
| `jwtPublicKey` | `val jwtPublicKey: String?` | Server's Ed25519 public key for JWT verification (from JWKS endpoint) | Null until first fetch |
| `appVersion` | `val appVersion: String` | BuildConfig.VERSION_NAME | Default "1.0.0" |
| `userAgent` | `val userAgent: String` | `"Enchant-Android/${appVersion}"` | — |

**Constraints:**
- gatewayUrl must not end with `/`
- wsUrl must always match gatewayUrl's origin (just protocol swap)
- All values are read once at init, cached in memory

**Test requirements:** 12 tests
- 5 happy path (each getter returns correct value after init)
- 4 edge cases (missing values → defaults; null TURN fields)
- 3 URL construction (http→ws, https→wss, trailing slash stripping)

---

### File: `core/base/src/main/java/org/enchant/core/base/SecurePreferences.kt`

**Purpose:** Encrypted key-value store for sensitive data (JWT, refresh tokens, keys). Wraps `EncryptedSharedPreferences` or custom SQLCipher-based store.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `init` | `fun init(context: Context)` | Initialize encrypted store with AES-256 GCM key | First run → generate key via KeyStore; KeyStore unavailable → fallback with warning |
| `putString` | `fun putString(key: String, value: String)` | Store encrypted string | Value null → don't store |
| `getString` | `fun getString(key: String, default: String? = null): String?` | Read encrypted string | Missing key → return default |
| `putLong` | `fun putLong(key: String, value: Long)` | Store encrypted long | — |
| `getLong` | `fun getLong(key: String, default: Long = 0): Long` | Read encrypted long | Missing key → return default |
| `putBoolean` | `fun putBoolean(key: String, value: Boolean)` | Store encrypted boolean | — |
| `getBoolean` | `fun getBoolean(key: String, default: Boolean = false): Boolean` | Read encrypted boolean | Missing key → return default |
| `remove` | `fun remove(key: String)` | Delete a stored value | Key doesn't exist → no-op |
| `clearAll` | `fun clearAll()` | Wipe all stored data | Called on logout/account deletion |
| `contains` | `fun contains(key: String): Boolean` | Check if key exists | — |

**Key namespace convention:** `"auth.jwt"`, `"auth.refresh_token"`, `"crypto.identity_key"`, `"crypto.signed_prekey"`, `"db.encryption_key"`, `"settings.theme"`, `"onboarding.complete"`

**Security requirements:**
- All values encrypted with AES-256-GCM
- Encryption key stored in Android KeyStore (hardware-backed if available)
- `clearAll()` must use `sodium_memzero` or equivalent to wipe in-memory copies
- Never log any stored value, even in debug builds
- Never include stored values in crash reports

**Test requirements:** 15 tests
- 5 CRUD operations (put → get, put → remove → get null, clearAll, update existing)
- 4 encrypted storage verification (values not stored in plaintext)
- 3 edge cases (null values, empty string, very long string 10KB)
- 3 concurrent access (multiple threads reading/writing different keys)

---

### File: `core/base/src/main/java/org/enchant/core/base/KeyStoreManager.kt`

**Purpose:** Wraps Android KeyStore for cryptographic key storage. Used for identity keys and database encryption key.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `init` | `suspend fun init(context: Context)` | Initialize KeyStore connection | KeyStore corrupted → regenerate; hardware unavailable → software fallback |
| `generateKey` | `suspend fun generateKey(alias: String, purpose: Int): Boolean` | Generate a new key inside KeyStore (EC or AES) | Key already exists → return false; StrongBox unavailable → fallback to TEE |
| `keyExists` | `fun keyExists(alias: String): Boolean` | Check if key exists in KeyStore | — |
| `deleteKey` | `suspend fun deleteKey(alias: String)` | Delete a key from KeyStore | Key doesn't exist → no-op |
| `sign` | `suspend fun sign(alias: String, data: ByteArray): ByteArray?` | Sign data with EC key | Key missing → return null; data empty → throw |
| `verify` | `suspend fun verify(alias: String, data: ByteArray, signature: ByteArray): Boolean` | Verify signature with EC key | Key missing → return false |
| `encrypt` | `suspend fun encrypt(alias: String, plaintext: ByteArray): ByteArray?` | Encrypt with AES key | Key missing → return null |
| `decrypt` | `suspend fun decrypt(alias: String, ciphertext: ByteArray): ByteArray?` | Decrypt with AES key | Key missing; ciphertext corrupted → return null |
| `getWrappedKeyBytes` | `suspend fun getWrappedKeyBytes(alias: String): ByteArray?` | Export wrapped key (uses KeyStore to wrap) | Key missing → return null |
| `isHardwareBacked` | `fun isHardwareBacked(): Boolean` | Check if StrongBox or TEE is available | — |

**Alias constants:**
```kotlin
const val KEY_ALIAS_IDENTITY = "enchant_identity_key"
const val KEY_ALIAS_DB_ENCRYPTION = "enchant_db_key"
```

**Security requirements:**
- Use `KeyGenParameterSpec.Builder` with `setIsStrongBoxBacked(true)` when available
- Set `setKeyValidityForOriginationEnd()` for purpose-specific keys
- Never log key aliases or key material
- Enable user authentication requirement for identity key (`setUserAuthenticationRequired(true)`)

**Test requirements:** 18 tests
- 6 key lifecycle (generate, exists, sign, verify, delete, delete then check)
- 4 hardware detection (StrongBox available/unavailable, TEE fallback)
- 4 edge cases (generate same alias twice, sign with missing key, corrupt ciphertext, empty data)
- 4 concurrent access (multiple operations on different aliases)

---

### File: `core/base/src/main/java/org/enchant/core/base/DI.kt`

**Purpose:** Manual dependency injection container (no Dagger/Hilt). Singleton registry similar to Signal's `AppDependencies`.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `init` | `suspend fun init(context: Context)` | Initialize all core dependencies in order | Any init failure → throw with clear message; partial init → rollback |
| `securePreferences` | `val securePreferences: SecurePreferences` | Singleton accessor | Not initialized → throw |
| `keyStoreManager` | `val keyStoreManager: KeyStoreManager` | Singleton accessor | Not initialized → throw |
| `appConfig` | `val appConfig: AppConfig` | Singleton accessor | Not initialized → throw |
| `database` | `val database: AppDatabase` | Singleton accessor | Not initialized → throw |
| `apiClient` | `val apiClient: ApiClient` | Singleton accessor | Not initialized → throw |
| `webSocketManager` | `val webSocketManager: WebSocketManager` | Singleton accessor | Not initialized → throw |
| `sessionManager` | `val sessionManager: SessionManager` | Singleton accessor | Not initialized → throw |
| `isInitialized` | `val isInitialized: Boolean` | Check if DI is fully initialized | — |
| `reset` | `fun reset()` | Reset all dependencies (for logout) | Called mid-operation → safe |

**Initialization order:**
1. `AppConfig.init(context)` 
2. `KeyStoreManager.init(context)`
3. `SecurePreferences.init(context)` — requires KeyStore for encryption key
4. `AppDatabase.init(context)` — requires SecurePreferences for DB key
5. `ApiClient.init()` — requires AppConfig for URL
6. `WebSocketManager.init()` — requires ApiClient for JWT
7. `SessionManager.init()` — requires KeyStore + Database

**Security requirements:**
- `reset()` must zero out all in-memory crypto material before releasing references
- `reset()` must close database connections
- Never store a reference to DI in a static field that survives activity destruction

**Test requirements:** 10 tests
- 3 init success (full init chain, partial init rollback, double init)
- 3 accessors (get after init, get before init → throw, get after reset → throw)
- 2 reset (clean reset, reset mid-operation)
- 2 thread safety (concurrent init calls, concurrent accessors)

---

### File: `core/base/src/main/java/org/enchant/core/base/CoroutineDispatchers.kt`

**Purpose:** Provides named coroutine dispatchers for different work types (network, database, crypto, main).

| Function | Signature | Description |
|---|---|---|
| `io` | `val io: CoroutineDispatcher` | For disk I/O, database operations (Dispatchers.IO) |
| `network` | `val network: CoroutineDispatcher` | For network calls (limited concurrency) |
| `crypto` | `val crypto: CoroutineDispatcher` | For CPU-heavy crypto operations (single thread) |
| `computation` | `val computation: CoroutineDispatcher` | General computation (Dispatchers.Default) |
| `main` | `val main: CoroutineDispatcher` | UI thread (Dispatchers.Main) |

**Test requirements:** 4 tests — each dispatcher returns non-null, crypto and network are single-threaded

---

## Module: `:core:network` (8 files)

HTTP client, WebSocket client, authentication interceptor, rate limit handler, protobuf parsing.

### File: `core/network/src/main/java/org/enchant/core/network/ApiClient.kt`

**Purpose:** HTTP client for all REST API calls. Wraps OkHttp with JWT injection, retry, rate limit handling.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `init` | `fun init()` | Initialize OkHttp client with interceptors | Already initialized → no-op |
| `get` | `suspend fun get(path: String, queryParams: Map<String,String>? = null): Result<JsonObject>` | HTTP GET | Network failure → Result.failure; 401 → auto-refresh JWT + retry; 429 → honor Retry-After; non-JSON response → parse error |
| `post` | `suspend fun post(path: String, body: JsonObject? = null): Result<JsonObject>` | HTTP POST with JSON body | Same as GET |
| `put` | `suspend fun put(path: String, body: JsonObject? = null): Result<JsonObject>` | HTTP PUT with JSON body | Same as GET |
| `del` | `suspend fun del(path: String): Result<JsonObject>` | HTTP DELETE | Same as GET |
| `postRaw` | `suspend fun postRaw(path: String, body: ByteArray, mimeType: String = "application/octet-stream"): Result<JsonObject>` | HTTP POST with raw binary body | Same as GET + body > 128MB → fail early |
| `getBinary` | `suspend fun getBinary(path: String): Result<ByteArray>` | HTTP GET returning raw binary | Same as GET |
| `uploadFile` | `suspend fun uploadFile(path: String, fileBytes: ByteArray, mimeType: String): Result<JsonObject>` | Upload file with progress tracking | Same as postRaw + cancellation support |

**Auth behavior:**
- Every request gets `Authorization: Bearer <jwt>` header from SecurePreferences
- On 401 response: call `POST /v1/auth/refresh` with refresh token → get new JWT → retry original request (max 1 retry)
- On refresh 401: clear all auth data, emit `SessionExpiredEvent`
- JWT expiry is checked client-side too (`exp` claim) — prefetch refresh before expiry

**Retry behavior:**
- Network errors: retry up to 2 times with 1s, 2s backoff
- 429 errors: read `Retry-After` header, wait that many seconds, retry once
- 5xx errors: retry once after 2s
- Other 4xx: do NOT retry

**Rate limit headers parsed:**
```
X-RateLimit-Limit: <max>
X-RateLimit-Remaining: <remaining>
X-RateLimit-Reset: <unix_timestamp>
Retry-After: <seconds>
```

**Error response format (parsed):**
```kotlin
data class ApiError(
    val message: String,          // From {"error": "..."}
    val code: String?,            // Optional error code
    val statusCode: Int,          // HTTP status
    val retryAfter: Long?         // From Retry-After header
)
```

**Test requirements:** 22 tests
- 8 HTTP methods (each method success + error response)
- 6 auth behavior (401 → refresh → retry, 401 → refresh fails → clear, JWT prefetch, missing JWT)
- 4 retry (network error retry, 429 retry, 5xx retry, max retries exceeded)
- 2 binary upload (small file, file > 128MB → fail)
- 2 edge cases (non-JSON response, empty body)

---

### File: `core/network/src/main/java/org/enchant/core/network/AuthInterceptor.kt`

**Purpose:** OkHttp interceptor that injects JWT and handles 401 responses.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `intercept` | `fun intercept(chain: Interceptor.Chain): Response` | Add Authorization header; on 401, refresh token and retry | Concurrent 401s → only refresh once; refresh itself fails → don't loop |
| `getValidToken` | `suspend fun getValidToken(): String?` | Returns current JWT, refreshing if expired within 60s | No JWT → return null; refresh fails → return null |

**Test requirements:** 6 tests — adds header correctly, 401 single retry, concurrent 401 dedup, refresh success, refresh fail, non-401 pass through

---

### File: `core/network/src/main/java/org/enchant/core/network/RateLimitTracker.kt`

**Purpose:** Client-side rate limit tracking to avoid hitting server limits.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `recordCall` | `fun recordCall(endpoint: String)` | Record an API call for rate tracking | — |
| `canCall` | `fun canCall(endpoint: String): Boolean` | Check if we can make a call without hitting limits | Unknown endpoint → return true |
| `getRemaining` | `fun getRemaining(endpoint: String): Int` | Get remaining calls for endpoint | Unknown endpoint → return max |
| `getResetTime` | `fun getResetTime(endpoint: String): Long` | When the rate limit resets (epoch ms) | Unknown endpoint → return 0 |
| `updateFromHeaders` | `fun updateFromHeaders(endpoint: String, headers: Map<String, String>)` | Update tracking from response headers | Missing headers → no-op |
| `waitIfNeeded` | `suspend fun waitIfNeeded(endpoint: String)` | Suspend until rate limit resets if exceeded | No reset time → return immediately |

**Rate limit table from backend:**
```kotlin
data class RateLimit(val maxCalls: Int, val windowSeconds: Int, val scope: String)
// See PRODUCTION_REFERENCE.md Appendix A for full table
```

**Test requirements:** 8 tests — record/check, exceeded returns false, header update, wait resumes after reset, unknown endpoint, fresh start has full quota

---

### File: `core/network/src/main/java/org/enchant/core/network/WebSocketManager.kt`

**Purpose:** Signal-correct WebSocket client with protobuf binary frames, keep-alive, exponential backoff reconnect.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `init` | `suspend fun init()` | Initialize WebSocket client | Already connected → no-op |
| `connect` | `suspend fun connect()` | Establish WS connection, send AUTH frame, wait for auth success | Already connecting → wait; auth timeout (10s) → disconnect + retry; auth failure → emit event |
| `disconnect` | `fun disconnect()` | Clean disconnect | Already disconnected → no-op |
| `sendMessage` | `suspend fun sendMessage(recipientUserId: String, recipientDeviceId: String? = null, payload: ByteArray, senderTs: Long? = null, ephemeral: Boolean = false): String?` | Send encrypted message envelope | WS disconnected → queue for REST fallback; rate limited → wait + retry; payload > 2MB → fail |
| `sendTypingStart` | `suspend fun sendTypingStart(recipientUserId: String)` | Send typing indicator | WS disconnected → ephemeral, skip |
| `sendTypingStop` | `suspend fun sendTypingStop(recipientUserId: String)` | Stop typing indicator | Same as sendTypingStart |
| `sendDeliveryReceipt` | `suspend fun sendDeliveryReceipt(envelopeId: String, senderUserId: String)` | Acknowledge delivery | Same as sendTypingStart |
| `sendReadReceipt` | `suspend fun sendReadReceipt(envelopeId: String, senderUserId: String)` | Acknowledge read | Same as sendTypingStart |
| `sendCallOffer` | `suspend fun sendCallOffer(recipientUserId: String, sdp: String)` | Send WebRTC offer | WS disconnected → fail |
| `sendCallAnswer` | `suspend fun sendCallAnswer(recipientUserId: String, sdp: String)` | Send WebRTC answer | WS disconnected → fail |
| `sendCallIce` | `suspend fun sendCallIce(recipientUserId: String, candidate: String)` | Send ICE candidate | WS disconnected → fail (will be re-negotiated) |
| `sendCallEnd` | `suspend fun sendCallEnd(recipientUserId: String)` | End call signal | WS disconnected → will be sent via REST |
| `requestRESTFallback` | `suspend fun requestRESTFallback(message: OutgoingMessage): Result<JsonObject>` | Fallback to POST /v1/messages/send when WS unavailable | REST also fails → queue offline |
| `connectionState` | `val connectionState: StateFlow<ConnectionState>` | Observable connection state | — |
| `incomingMessages` | `val incomingMessages: SharedFlow<IncomingEnvelope>` | Observable incoming messages | — |
| `connectionErrors` | `val incomingMessages: SharedFlow<ConnectionError>` | Observable errors | — |

**Connection states:**
```kotlin
enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, AUTH_FAILED
}
```

**Reconnection behavior:**
```
1. Detect disconnect
2. Wait base delay (1s)
3. Attempt reconnect
4. On 401 → refresh JWT → retry
5. On fail → exponential backoff: 1s → 2s → 4s → 8s → 15s → 30s (cap)
6. Add ±25% jitter
7. Continue at 30s indefinitely
8. 5 consecutive 401s → stop, emit AUTH_FAILED
```

**Protobuf frame format (matching backend):**
- All frames are binary protobuf
- Type 1 = Request (client → server)
- Type 2 = Response (server → client)

**Protocol flow:**
```
Client connects → Server expects AUTH within 10s
Client sends: POST /v1/auth (raw JWT bytes)
Server responds: 200 "Authenticated" or 401 "reason"
If authenticated:
  - Server pushes pending messages as PUT requests
  - Client sends messages as POST /api/v1/message
  - Server responds with envelope_id
  - Client sends ack → server marks delivered
  - Client sends DELIVERY_RECEIPT to original sender

Every 30s: GET /v1/keepalive
90s idle timeout: server disconnects
```

**Wire format (matching server's protobuf):**
```kotlin
// Request frame (type 1)
data class WsRequest(
    val id: Long,                // Client-assigned request ID
    val verb: String,            // POST, GET, PUT
    val path: String,            // /api/v1/message, /v1/auth
    val body: ByteArray,         // Raw bytes (JWT, protobuf Envelope, etc.)
    val headers: List<String> = emptyList()  // Optional: "Key: Value" pairs (field 5)
)

// Response frame (type 2)
data class WsResponse(
    val id: Long,                // Matches request ID
    val status: Int,             // 200, 400, 401, etc.
    val message: String,         // Status message
    val body: ByteArray? = null, // Optional response body
    val headers: List<String> = emptyList()  // Optional response headers (field 5)
)
```

**Test requirements:** 25 tests
- 5 connection lifecycle (connect, disconnect, reconnect, auth timeout, auth fail)
- 5 message sending (normal send, ephemeral send, large payload, WS down → REST fallback, rate limited)
- 4 keep-alive (sent every 30s, disconnect on missed keepalive, reconnect after disconnect)
- 4 incoming messages (normal message, delivery receipt, displaced notification, keepalive response)
- 4 reconnection (exponential backoff, 401 → refresh, max backoff cap, jitter range)
- 3 call signaling (offer, answer, ice)

---

### File: `core/network/src/main/java/org/enchant/core/network/models/ApiModels.kt`

**Purpose:** Data classes for all API request/response models.

```kotlin
// Auth
data class OtpRequest(val identifier: String)  // E.164 phone
data class OtpResponse(val challengeId: String, val expiresIn: Int)
data class VerifyOtpRequest(val challengeId: String, val otp: String, val deviceInfo: DeviceInfo?)
data class DeviceInfo(val deviceId: String? = null, val userAgent: String? = null)
data class AuthResponse(val userId: String, val accessToken: String, val refreshToken: String, val expiresIn: Int)
data class RefreshRequest(val refreshToken: String)
data class RefreshResponse(val accessToken: String, val refreshToken: String, val expiresIn: Int)

// Keys
data class KeyRegisterRequest(
    val identityKey: String,           // base64url 32 bytes
    val signedPrekey: SignedPrekeyData,
    val oneTimePrekeys: List<OneTimePrekeyData>
)
data class SignedPrekeyData(val publicKey: String, val signature: String)
data class OneTimePrekeyData(val publicKey: String)
data class KeyBundleResponse(val devices: List<KeyBundleDevice>)
data class KeyBundleDevice(
    val deviceId: String,
    val identityKey: String,
    val signedPrekey: SignedPrekeyData,
    val oneTimePrekey: String?         // null if no OPK available
)
data class OpkCountResponse(val remaining: Int)
data class RotateSpkRequest(val publicKey: String, val signature: String)
data class UploadOpksRequest(val oneTimePrekeys: List<OneTimePrekeyData>)

// Messages
data class SendMessageRequest(
    val recipientUserId: String,
    val recipientDeviceId: String? = null,
    val messageType: String,
    val payload: String,
    val senderTs: String? = null
)
data class SendMessageResponse(val envelopeIds: List<String>)
data class SealedSendRequest(
    val recipientUserId: String,
    val recipientDeviceId: String? = null,
    val messageType: String,
    val payload: String,
    val replyToken: String? = null
)
data class SealedSendResponse(val envelopeIds: List<String>, val sealed: Boolean)

// Media
data class MediaUploadResponse(val mediaId: String, val downloadUrl: String, val expiresTs: Long)
data class MediaDeleteResponse(val deleted: Boolean)

// Profile
data class ProfileResponse(
    val userId: String,
    val username: String,
    val displayName: String?,
    val about: String?,
    val avatarMediaId: String?,
    val avatarKey: String?,
    val lastSeen: String?,
    val online: Boolean?
)
data class UpdateProfileRequest(val username: String? = null, val displayName: String? = null, val about: String? = null)
data class UpdateProfileResponse(val updated: Boolean)
data class PrivacyRequest(
    val lastSeenVisibility: String? = null,
    val onlineVisibility: String? = null,
    val avatarVisibility: String? = null,
    val aboutVisibility: String? = null,
    val readReceiptsEnabled: Boolean? = null,
    val groupsAddPolicy: String? = null
)
data class UsernameSearchResponse(val results: List<UsernameSearchResult>)
data class UsernameSearchResult(val userId: String, val username: String, val displayName: String?, val avatarMediaId: String?)

// Contacts
data class AddContactRequest(val contactUserId: String, val customName: String? = null)
data class AddContactResponse(val added: Boolean)
data class ContactListResponse(val contacts: List<ContactEntry>)
data class ContactEntry(val contactUserId: String, val customName: String?, val addedTs: String)
data class PhoneMatchRequest(val phoneHashes: List<String>)
data class PhoneMatchResponse(val matches: List<PhoneMatchResult>)
data class PhoneMatchResult(val userId: String, val username: String, val displayName: String?, val phoneHash: String)
data class FriendRequest(val toUserId: String)
data class FriendRequestResponse(val id: String, val status: String)

// Groups
data class CreateGroupRequest(
    val name: String,
    val description: String? = null,
    val initialMemberIds: List<String>? = null,
    val addMembersPolicy: String? = null,
    val joinType: String? = null
)
data class AddMemberRequest(val userIds: List<String>)
data class UpdateRoleRequest(val role: String)
data class InviteLinkRequest(val expiresTs: String? = null, val maxUses: Int = 0)
data class JoinRequestAction(val approve: Boolean = true)

// Common
data class ApiError(val error: String, val code: String? = null, val retryAfter: Int? = null)
```

**Test requirements:** 5 tests — each model serializes/deserializes correctly with JSON

---

### File: `core/network/src/main/java/org/enchant/core/network/ConnectivityMonitor.kt`

**Purpose:** Monitors network connectivity and emits online/offline state.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `init` | `fun init(context: Context)` | Register network callback | Already initialized → no-op |
| `isOnline` | `val isOnline: StateFlow<Boolean>` | Current connectivity state | — |
| `networkType` | `val networkType: StateFlow<NetworkType>` | Current network type (wifi, cellular, ethernet, none) | — |

**Test requirements:** 4 tests — init, online callback, offline callback, network type changes

---

### File: `core/network/src/main/java/org/enchant/core/network/OfflineQueue.kt`

**Purpose:** Queues messages when offline, drains them when connectivity returns.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `enqueue` | `suspend fun enqueue(message: QueuedMessage)` | Add message to offline queue | DB full → evict oldest |
| `drain` | `suspend fun drain()` | Send all queued messages via WS or REST | Partial failure → retry failed individually |
| `pendingCount` | `val pendingCount: StateFlow<Int>` | Number of queued messages | — |
| `remove` | `fun remove(messageId: String)` | Remove specific message from queue | Not found → no-op |
| `clearAll` | `suspend fun clearAll()` | Clear all queued messages | Called on logout |

```kotlin
data class QueuedMessage(
    val id: String = UUID.randomUUID().toString(),
    val recipientUserId: String,
    val recipientDeviceId: String?,
    val messageType: String,
    val payload: ByteArray,
    val senderTs: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)
```

**Test requirements:** 10 tests — enqueue, drain success, drain partial failure, clearAll, pendingCount updates, retryCount increments, max retries exceeded, empty queue drain, message removal, persistence across app restart

---

## Module: `:core:database` (8 files)

SQLCipher database with 14 tables, DAOs, migrations.

### File: `core/database/src/main/java/org/enchant/core/database/AppDatabase.kt`

**Purpose:** SQLCipher database with all tables and migrations.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `init` | `suspend fun init(context: Context)` | Open or create encrypted database | First run → create tables; migration needed → apply sequentially; key wrong → throw |
| `getDbKey` | `private fun getDbKey(): ByteArray` | Get database encryption key from SecurePreferences | No key → generate via KeyStore + store |
| `writableDatabase` | `fun writableDatabase(): SupportSQLiteDatabase` | Get writable database instance | Not initialized → throw |
| `readableDatabase` | `fun readableDatabase(): SupportSQLiteDatabase` | Get readable database instance | Not initialized → throw |
| `close` | `fun close()` | Close database | Already closed → no-op |
| `runInTransaction` | `suspend fun <T> runInTransaction(block: suspend () -> T): T` | Execute block in a transaction | Transaction fails → rollback |
| `isInitialized` | `val isInitialized: Boolean` | Check if database is ready | — |

**Tables (14 total):**

| Table | Columns | Indexes |
|---|---|---|
| `messages` | localId (PK), conversationId, senderId, senderDeviceId, envelopeId (unique), messageType, content TEXT, mediaKey, mediaIv, mediaMimeType, mediaSize, mediaThumbnailPath, replyToEnvelopeId, forwardedFromUserId, status TEXT, timestamp INTEGER, serverTs INTEGER, isEdited INTEGER, editEnvelopeId, isStarred INTEGER, isDeleted INTEGER, disappearAt INTEGER, gifUrl | conversationId+timestamp, envelopeId, status, disappearAt |
| `conversations` | conversationId (PK), type TEXT, lastMessage TEXT, lastMessageEnvelopeId, unreadCount INTEGER, isPinned INTEGER, isArchived INTEGER, isMuted INTEGER, muteUntil INTEGER, disappearTimerSeconds INTEGER | isPinned, isArchived, lastMessageTimestamp |
| `signal_sessions` | userId TEXT, deviceId TEXT, serializedSession BLOB, PRIMARY KEY(userId, deviceId) | userId |
| `identities` | addressName TEXT (PK), recipientId TEXT, identityKey BLOB, verifiedStatus INTEGER, firstUse INTEGER, timestamp INTEGER, nonBlockingApproval INTEGER | — |
| `key_material` | keyType TEXT (PK), keyData BLOB | — |
| `recipients` | recipientId TEXT (PK), username TEXT, displayName TEXT, phoneNumber TEXT, avatarMediaId TEXT, avatarLocalPath TEXT, isBlocked INTEGER | username |
| `groups` | groupId TEXT (PK), name TEXT, description TEXT, avatarMediaId TEXT, myRole TEXT, memberCount INTEGER | — |
| `group_members` | groupId TEXT, userId TEXT, role TEXT, PRIMARY KEY(groupId, userId) | groupId |
| `media_cache` | mediaId TEXT (PK), localPath TEXT, fileSize INTEGER, lastAccessedAt INTEGER | — |
| `profile_cache` | userId TEXT (PK), displayName TEXT, username TEXT, about TEXT, avatarMediaId TEXT, profileJson TEXT | — |
| `call_logs` | callId TEXT (PK), remoteUserId TEXT, type TEXT, direction TEXT, durationSeconds INTEGER, status TEXT, endedAt INTEGER | remoteUserId |
| `status_cache` | statusId TEXT (PK), authorId TEXT, statusType TEXT, textContent TEXT, mediaId TEXT, backgroundColor TEXT, timestamp INTEGER, viewed INTEGER | authorId |
| `sticker_packs` | packId TEXT (PK), title TEXT, cover TEXT, author TEXT, installedAt INTEGER | — |
| `installed_stickers` | packId TEXT, stickerId TEXT, emoji TEXT, position INTEGER, PRIMARY KEY(packId, stickerId) | packId |

**Migration strategy:**
- Start at version 1 with all tables
- Each migration is a numbered file: `Migration2.kt`, `Migration3.kt`, etc.
- Each implements `fun migrate(db: SupportSQLiteDatabase)`
- Apply sequentially in `onUpgrade`

**Test requirements:** 12 tests
- 3 database lifecycle (init first run, init existing, close)
- 3 migrations (v1→v2, v2→v3, multiple migrations)
- 2 transaction (success commits, failure rolls back)
- 2 encryption (DB key generated, DB opens with correct key)
- 2 edge cases (init with corrupt DB → recreate, concurrent write access)

---

### File: `core/database/src/main/java/org/enchant/core/database/dao/MessageDao.kt`

**Purpose:** Data access object for messages table.

| Function | Signature | Description |
|---|---|---|
| `insert` | `suspend fun insert(message: MessageEntity)` | Insert a single message |
| `insertBatch` | `suspend fun insertBatch(messages: List<MessageEntity>)` | Batch insert for bulk imports |
| `getById` | `suspend fun getById(localId: Long): MessageEntity?` | Get message by local ID |
| `getByEnvelopeId` | `suspend fun getByEnvelopeId(envelopeId: String): MessageEntity?` | Get message by server envelope ID |
| `getConversationMessages` | `fun getConversationMessages(conversationId: String, limit: Int = 50, beforeId: Long? = null): Flow<List<MessageEntity>>` | Reactive paginated messages for a conversation |
| `updateStatus` | `suspend fun updateStatus(envelopeId: String, status: String)` | Update delivery status |
| `markDeleted` | `suspend fun markDeleted(envelopeId: String)` | Soft-delete a message |
| `starMessage` | `suspend fun starMessage(envelopeId: String, starred: Boolean)` | Toggle starred status |
| `getUnreadCount` | `suspend fun getUnreadCount(conversationId: String): Int` | Unread message count |
| `searchMessages` | `fun searchMessages(query: String): Flow<List<MessageEntity>>` | Full-text search on content |
| `deleteExpired` | `suspend fun deleteExpired(now: Long)` | Delete messages past disappearAt |
| `deleteConversation` | `suspend fun deleteConversation(conversationId: String)` | Delete all messages in a conversation |

**Test requirements:** 15 tests — CRUD, pagination, reactive flow emits, status updates, soft delete, star toggle, search, expired deletion, conversation deletion

---

### File: `core/database/src/main/java/org/enchant/core/database/dao/ConversationDao.kt`

**Purpose:** Data access object for conversations table.

| Function | Signature | Description |
|---|---|---|
| `upsert` | `suspend fun upsert(conversation: ConversationEntity)` | Insert or update a conversation |
| `getAll` | `fun getAll(): Flow<List<ConversationEntity>>` | Reactive list of all conversations (sorted by last message time) |
| `getById` | `suspend fun getById(conversationId: String): ConversationEntity?` | Get single conversation |
| `getFiltered` | `fun getFiltered(filter: ConversationFilter): Flow<List<ConversationEntity>>` | Filtered list (unread, groups, personal, archived) |
| `setArchived` | `suspend fun setArchived(conversationId: String, archived: Boolean)` | Toggle archive |
| `setPinned` | `suspend fun setPinned(conversationId: String, pinned: Boolean)` | Toggle pin |
| `setMuted` | `suspend fun setMuted(conversationId: String, muted: Boolean, until: Long?)` | Set mute state |
| `incrementUnread` | `suspend fun incrementUnread(conversationId: String, amount: Int = 1)` | Increment unread counter |
| `getUnreadCount` | `suspend fun getUnreadCount(): Int` | Total unread across all conversations |
| `search` | `fun search(query: String): Flow<List<ConversationEntity>>` | Search conversations by name |

**Test requirements:** 12 tests — upsert, reactive list, filtering, archive, pin, mute, unread counting, search

---

### File: `core/database/src/main/java/org/enchant/core/database/dao/SessionDao.kt`

**Purpose:** DAO for Signal Protocol session storage.

| Function | Signature | Description |
|---|---|---|
| `store` | `suspend fun store(userId: String, deviceId: String, session: ByteArray)` | Store serialized session |
| `load` | `suspend fun load(userId: String, deviceId: String): ByteArray?` | Load serialized session |
| `delete` | `suspend fun delete(userId: String, deviceId: String)` | Delete a session |
| `hasSession` | `suspend fun hasSession(userId: String, deviceId: String): Boolean` | Check session exists |
| `deleteAllForUser` | `suspend fun deleteAllForUser(userId: String)` | Delete all sessions for a user |

**Test requirements:** 8 tests — store/load roundtrip, load non-existent → null, delete, hasSession, deleteAllForUser, update existing session

---

### File: `core/database/src/main/java/org/enchant/core/database/dao/IdentityDao.kt`

**Purpose:** DAO for identity key storage.

| Function | Signature | Description |
|---|---|---|
| `save` | `suspend fun save(addressName: String, recipientId: String?, identityKey: ByteArray, verifiedStatus: Int, timestamp: Long)` | Save identity key record |
| `getByAddress` | `suspend fun getByAddress(addressName: String): IdentityEntity?` | Get identity by address |
| `setVerified` | `suspend fun setVerified(addressName: String, status: Int)` | Update verified status |
| `delete` | `suspend fun delete(addressName: String)` | Delete identity record |

**Test requirements:** 6 tests — save, get, update verified, delete, get non-existent, save overwrite

---

### File: `core/database/src/main/java/org/enchant/core/database/dao/RecipientDao.kt`

**Purpose:** DAO for recipient/contact cache.

| Function | Signature | Description |
|---|---|---|
| `upsert` | `suspend fun upsert(recipient: RecipientEntity)` | Insert or update recipient |
| `upsertAll` | `suspend fun upsertAll(recipients: List<RecipientEntity>)` | Batch upsert |
| `getByUserId` | `suspend fun getByUserId(userId: String): RecipientEntity?` | Get by user ID |
| `getByUsername` | `suspend fun getByUsername(username: String): RecipientEntity?` | Get by username |
| `getAll` | `fun getAll(): Flow<List<RecipientEntity>>` | Reactive list of all recipients |
| `getBlocked` | `suspend fun getBlocked(): List<RecipientEntity>` | Get blocked users |
| `search` | `fun search(query: String): Flow<List<RecipientEntity>>` | Search by name or username |

**Test requirements:** 10 tests — upsert, upsertAll, get, search, reactive list, blocked filter, non-existent returns null, username lookup

---

### File: `core/database/src/main/java/org/enchant/core/database/entities/Entities.kt`

**Purpose:** All Room/SQLite entity data classes.

```kotlin
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val conversationId: String,
    val senderId: String,
    val senderDeviceId: String?,
    @Index(unique = true) val envelopeId: String?,
    val messageType: String,
    val content: String,
    val mediaKey: String?,
    val mediaIv: String?,
    val mediaMimeType: String?,
    val mediaSize: Long?,
    val mediaThumbnailPath: String?,
    val replyToEnvelopeId: String?,
    val forwardedFromUserId: String?,
    val status: String,
    val timestamp: Long,
    val serverTs: Long?,
    val isEdited: Boolean = false,
    val editEnvelopeId: String?,
    val isStarred: Boolean = false,
    val isDeleted: Boolean = false,
    val disappearAt: Long?,
    val gifUrl: String?
)
// ... similar for all other entities
```

**Test requirements:** 3 tests — each entity serializable with Room, column count matches

---

## Module: `:core:crypto` (6 files)

Signal Protocol implementation: X3DH key agreement, Double Ratchet, session management, pre-key lifecycle.

### File: `core/crypto/src/main/java/org/enchant/core/crypto/SodiumProvider.kt`

**Purpose:** Provides libsodium instance. Must be initialized before any crypto operations.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `init` | `suspend fun init()` | Initialize libsodium library (load native .so) | Already initialized → no-op; lib not found → throw with clear message |
| `sodium` | `val sodium: Sodium` | Instance for all crypto ops | Not initialized → throw |

**Test requirements:** 3 tests — init succeeds, get instance, init twice is safe

---

### File: `core/crypto/src/main/java/org/enchant/core/crypto/CryptoHelper.kt`

**Purpose:** Wrapper around libsodium for all cryptographic primitives used by the app.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `ed25519SkToX25519` | `fun ed25519SkToX25519(sk: ByteArray): ByteArray` | Convert Ed25519 secret key to X25519 | Invalid key size → throw |
| `ed25519PkToX25519` | `fun ed25519PkToX25519(pk: ByteArray): ByteArray` | Convert Ed25519 public key to X25519 | Invalid key size → throw |
| `x25519DiffieHellman` | `fun x25519DiffieHellman(privateKey: ByteArray, publicKey: ByteArray): ByteArray` | X25519 DH key agreement | Invalid key sizes → throw; both keys same → return valid DH |
| `hkdfSha256` | `fun hkdfSha256(input: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray` | HKDF-SHA256 key derivation | Length <= 0 → throw; salt/info empty → still works |
| `encryptXChaCha20Poly1305` | `fun encryptXChaCha20Poly1305(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray` | AEAD encryption | Key wrong size → throw; nonce wrong size → throw |
| `decryptXChaCha20Poly1305` | `fun decryptXChaCha20Poly1305(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray` | AEAD decryption | Wrong key → throw; corrupted ciphertext → throw; truncated → throw |
| `generateRandomKey` | `fun generateRandomKey(size: Int = 32): ByteArray` | CSPRNG random bytes | Size <= 0 → throw |
| `generateKeyPair` | `fun generateKeyPair(): KeyPair` | Generate Ed25519 key pair | — |
| `sign` | `fun sign(message: ByteArray, secretKey: ByteArray): ByteArray` | Ed25519 sign | Invalid key → throw |
| `verify` | `fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean` | Ed25519 verify | Invalid key/sig → return false (never throw) |
| `sha256` | `fun sha256(data: ByteArray): ByteArray` | SHA-256 hash | Empty data → still returns valid hash |
| `equals` | `fun equals(a: ByteArray, b: ByteArray): Boolean` | Constant-time comparison | Different lengths → return false |
| `zeroBytes` | `fun zeroBytes(data: ByteArray)` | Securely zero memory | Already zeroed → no-op |
| `base64UrlEncode` | `fun base64UrlEncode(data: ByteArray): String` | Base64url encode | Empty → return empty |
| `base64UrlDecode` | `fun base64UrlDecode(encoded: String): ByteArray` | Base64url decode | Invalid → throw |

**Security requirements:**
- All `ByteArray` arguments that contain secrets MUST be zeroed after use via `zeroBytes()`
- Use `Sodium.bytes` for secure memory allocation where possible
- `equals()` MUST be constant-time (use `Sodium.memcmp` or `MessageDigest.isEqual`)
- Never log keys, plaintext, or derived material
- Never include keys in crash reports or stack traces

**Test requirements:** 30 tests
- 10 known-answer tests (KATs) — for each crypto primitive, use test vectors from RFCs
  - HKDF: RFC 5869 test vectors
  - X25519: RFC 7748 test vectors  
  - Ed25519: RFC 8032 test vectors
  - XChaCha20-Poly1305: draft-irtf-cfrg-xchacha test vectors
- 8 conversion (Ed25519→X25519 roundtrip, invalid key sizes)
- 6 encryption/decryption (roundtrip, wrong key, corrupted ciphertext, empty plaintext, large payload 1MB, wrong nonce)
- 3 random generation (different each call, correct size, multiple calls don't block)
- 3 utility (sha256, constant-time equals, base64url roundtrip)

---

### File: `core/crypto/src/main/java/org/enchant/core/crypto/KeyManager.kt`

**Purpose:** Manages key bundle lifecycle: generation, upload to IKS, top-up.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `init` | `suspend fun init()` | Load or generate identity keys | No existing keys → generate new IK |
| `generateAndUploadKeys` | `suspend fun generateAndUploadKeys(): Result<Unit>` | Generate IK, SPK, 100 OPKs → upload to IKS | IKS unavailable → retry; already uploaded → skip; SPK sig verify fails → regenerate |
| `getIdentityKeyPair` | `suspend fun getIdentityKeyPair(): KeyPair?` | Get local identity key pair | Not generated → return null |
| `getIdentityPublicKeyBase64` | `suspend fun getIdentityPublicKeyBase64(): String?` | Get base64url-encoded public key | Not generated → return null |
| `fetchKeyBundle` | `suspend fun fetchKeyBundle(userId: String): Result<KeyBundleDevice?>` | Fetch another user's key bundle from IKS | User not found → Result.failure; rate limited → wait + retry |
| `topUpOpks` | `suspend fun topUpOpks()` | Check OPK count, upload more if below threshold (10) | IKS unavailable → retry next time; rate limited → wait; count already sufficient → skip |
| `rotateSignedPreKey` | `suspend fun rotateSignedPreKey(): Result<Unit>` | Generate new SPK, sign with IK, upload to IKS | IKS unavailable → retry; signature fails → retry |
| `cleanSignedPreKeys` | `suspend fun cleanSignedPreKeys()` | Remove SPKs older than 30 days | No old SPKs → no-op |
| `hasKeys` | `suspend fun hasKeys(): Boolean` | Check if identity keys exist locally | — |
| `signWithIdentity` | `suspend fun signWithIdentity(data: ByteArray): ByteArray?` | Sign data with identity key | No identity key → return null |

**SPK rotation schedule:**
- Rotate every 30 days
- Triggered by: app launch (if 25+ days since last rotation)
- Old SPK is deactivated (not deleted) on server
- Cleanup: remove SPKs older than 30 days

**OPK top-up schedule:**
- Check count on app launch and after every session establishment
- If count < 10: generate 100 new OPKs, upload
- Server max: 200 total OPKs per device, 100 per upload, 10 uploads/day

**Test requirements:** 15 tests
- 4 key generation (IK generated, SPK rotated, OPKs generated, keys stored securely)
- 3 IKS interaction (upload success, rate limited retry, server down)
- 3 state management (hasKeys true after gen, false before, keys survive rotation)
- 3 schedule (SPK rotation triggered at 25+ days, OPK top-up at <10, skip at >10)
- 2 error handling (IKS 422 sig verify → regenerate, network timeout → retry)

---

### File: `core/crypto/src/main/java/org/enchant/core/crypto/X3DH.kt`

**Purpose:** X3DH key agreement implementation.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `aliceInitiate` | `suspend fun aliceInitiate(ourIk: KeyPair, ourEk: KeyPair, bobIkPk: ByteArray, bobSpkPk: ByteArray, bobOpkPk: ByteArray?): X3dhResult` | Alice's X3DH: compute shared secret | Bob has no OPK → compute DH1+DH2+DH3 (skip DH4); invalid keys → throw |
| `bobRespond` | `suspend fun bobRespond(ourIk: KeyPair, ourSpk: KeyPair, ourOpk: KeyPair?, aliceIkPk: ByteArray, aliceEkPk: ByteArray): X3dhResult` | Bob's X3DH: compute shared secret | No OPK consumed → skip DH4; keys don't match → throw |

**Algorithm (matching backend spec):**
```
DH1 = DH(IK_A_private, SPK_B_public)
DH2 = DH(EK_private, IK_B_public)
DH3 = DH(EK_private, SPK_B_public)
DH4 = DH(EK_private, OPK_B_public)  // optional
SK = KDF(DH1 || DH2 || DH3 || DH4)
```

```kotlin
data class X3dhResult(
    val sharedSecret: ByteArray,      // 32 bytes
    val rootKey: ByteArray,           // First 32 bytes of derived key
    val chainKey: ByteArray,          // Second 32 bytes of derived key
    val header: X3dhHeader            // Sent to Bob as PREKEY_MESSAGE header
)

data class X3dhHeader(
    val identityKey: ByteArray,       // Alice's IK public
    val ephemeralKey: ByteArray,      // Alice's EK public
    val usedSignedPrekeyId: Int,      // Which of Bob's SPKs was used
    val usedOneTimePrekeyId: Int?     // Which of Bob's OPKs was used (null = none)
)
```

**Test requirements:** 10 tests
- 3 X3DH (Alice+Bob with OPK, Alice+Bob without OPK, same result on both sides)
- 3 invalid inputs (wrong key sizes, mismatched keys, corrupted data → all throw)
- 2 known-answer (use specific key material and verify SK matches expected)
- 2 edge cases (large number of DH computations, repeated calls produce different SK)

---

### File: `core/crypto/src/main/java/org/enchant/core/crypto/DoubleRatchet.kt`

**Purpose:** Double Ratchet per-message encryption.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `initializeAlice` | `fun initializeAlice(sharedSecret: ByteArray, bobSpk: ByteArray): RatchetState` | Initialize sending side | Invalid key sizes → throw |
| `initializeBob` | `fun initializeBob(sharedSecret: ByteArray, aliceEk: ByteArray): RatchetState` | Initialize receiving side | Invalid key sizes → throw |
| `encrypt` | `fun encrypt(state: RatchetState, plaintext: ByteArray): RatchetMessage` | Encrypt with ratchet step if needed | State invalid → throw; empty plaintext → still encrypt |
| `decrypt` | `fun decrypt(state: RatchetState, message: RatchetMessage): ByteArray` | Decrypt with ratchet step if needed | Wrong key → throw; corrupted → throw; replayed → throw |
| `serializeState` | `fun serializeState(state: RatchetState): ByteArray` | Serialize state for storage | — |
| `deserializeState` | `fun deserializeState(data: ByteArray): RatchetState` | Deserialize state from storage | Corrupted data → throw |

```kotlin
data class RatchetState(
    val sendingChainKey: ByteArray,
    val receivingChainKey: ByteArray,
    val sendingRatchetKey: ByteArray?,
    val sendingRatchetKeyPrivate: ByteArray?,
    val receivingRatchetKey: ByteArray?,
    val previousSendingChainLength: Int,
    val messageNumberSend: Int,
    val messageNumberReceive: Int,
    val skippedMessageKeys: Map<String, ByteArray>  // "ratchetKey:msgNum" → key
)

data class RatchetMessage(
    val header: ByteArray,      // JSON: {dh, msgNumSend, msgNumReceive, previousLength}
    val ciphertext: ByteArray
)
```

**Security requirements:**
- Skipped message keys MUST be limited to max 1000 entries
- Oldest skipped key is evicted when limit is reached
- `decrypt` must check for replay: if a (ratchetKey, messageNumReceive) pair was already decoded, throw
- After ratchet step, zero old chain keys
- Serialized state must include version field for forward compatibility

**Test requirements:** 18 tests
- 6 encrypt/decrypt (roundtrip, 10 messages without ratchet step, ratchet step triggers, large plaintext 64KB, empty plaintext, binary data)
- 4 state management (serialize/deserialize roundtrip, state survives app restart, concurrent encrypt+decrypt, state version mismatch)
- 4 security (replay detection, skipped message key limit, wrong key → throw, corrupted header → throw)
- 2 initialization (Alice init, Bob init with same SK produce same state)
- 2 edge cases (max skipped keys 1000, key eviction)

---

### File: `core/crypto/src/main/java/org/enchant/core/crypto/SessionManager.kt`

**Purpose:** Manages per-conversation sessions using X3DH + Double Ratchet.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `init` | `suspend fun init()` | Load existing sessions from database | No sessions → start fresh |
| `encryptMessage` | `suspend fun encryptMessage(recipientUserId: String, plaintext: ByteArray): EncryptedPayload` | Encrypt: establish session if needed, then Double Ratchet encrypt | No session → fetch bundle → X3DH → then encrypt; recipient has no keys → fail with meaningful error |
| `decryptMessage` | `suspend fun decryptMessage(senderUserId: String, payload: EncryptedPayload): DecryptedResult` | Decrypt: if prekey message → establish as Bob, else Double Ratchet decrypt | No session → try to establish; session expired → try re-establish; corrupted → return error (not throw) |
| `hasSession` | `suspend fun hasSession(userId: String): Boolean` | Check if session exists | — |
| `deleteSession` | `suspend fun deleteSession(userId: String)` | Delete a session | Session doesn't exist → no-op |
| `archiveSession` | `suspend fun archiveSession(userId: String)` | Archive a session (on identity key change) | Session doesn't exist → no-op |
| `getSafetyNumber` | `suspend fun getSafetyNumber(userId: String): String` | Get safety number (XXXX-XXXX-XXXX-XXXX) | No identity key → return "UNVERIFIED" |

```kotlin
data class EncryptedPayload(
    val messageType: String,              // "PREKEY_MESSAGE" or "SIGNAL_MESSAGE"
    val payload: ByteArray,               // Encrypted bytes (base64url encoded for transport)
    val recipientDeviceId: String?        // Specific device or null for fan-out
)

data class DecryptedResult(
    val plaintext: ByteArray,
    val senderDeviceId: String?,
    val isNewSession: Boolean             // True if this message established a new session
)
```

**Thread safety:**
- ALL session operations must be guarded by `ReentrantLock` (like Signal's `ReentrantSessionLock`)
- Lock per session key (userId+deviceId)
- Timeout: 5 seconds max wait for lock
- Deadlock prevention: never acquire two session locks at once

**Test requirements:** 20 tests
- 4 session establishment (Alice initiated with OPK, Alice initiated without OPK, Bob responds, both sides derive same SK)
- 4 message encrypt/decrypt (normal, prekey, large payload 64KB, many messages in a row)
- 3 session lifecycle (save session → restart app → load session → decrypt, delete, archive)
- 3 thread safety (concurrent encrypt on same session, concurrent encrypt on different sessions, lock timeout)
- 3 error handling (recipient has no keys, corrupted payload, session expired)
- 3 safety number (same IK → same number, different IKs → different number, unknown user → UNVERIFIED)

---

## Module: `:core:model` (5 files)

### File: `core/model/src/main/java/org/enchant/core/model/Message.kt`

```kotlin
data class Message(
    val localId: Long = 0,
    val conversationId: String,
    val senderId: String,
    val senderDeviceId: String?,
    val envelopeId: String?,
    val type: MessageType,
    val content: String,
    val media: MediaAttachment? = null,
    val replyTo: MessageRef? = null,
    val forwardedFrom: String? = null,
    val status: MessageStatus,
    val timestamp: Long,
    val serverTs: Long? = null,
    val isEdited: Boolean = false,
    val isStarred: Boolean = false,
    val isDeleted: Boolean = false,
    val disappearAt: Long? = null,
    val reactions: List<Reaction> = emptyList()
)

enum class MessageType { TEXT, IMAGE, VIDEO, VOICE, DOCUMENT, STICKER, LOCATION, CONTACT, POLL, SYSTEM }
enum class MessageStatus { SENDING, SENT, DELIVERED, READ, FAILED }

data class MediaAttachment(
    val mediaId: String,
    val mediaKey: ByteArray,       // Must be zeroed after use
    val mediaIv: ByteArray,        // Must be zeroed after use
    val mimeType: String,
    val size: Long,
    val thumbnailPath: String? = null
)

data class MessageRef(val envelopeId: String, val preview: String)
data class Reaction(val emoji: String, val userId: String, val timestamp: Long)
```

### File: `core/model/src/main/java/org/enchant/core/model/Conversation.kt`

```kotlin
data class Conversation(
    val conversationId: String,
    val type: ConversationType,
    val otherUserId: String? = null,    // For direct messages
    val groupId: String? = null,         // For group conversations
    val displayName: String,
    val avatarUrl: String? = null,
    val lastMessage: String? = null,
    val lastMessageTimestamp: Long? = null,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val muteUntil: Long? = null,
    val disappearTimerSeconds: Int = 0,
    val members: List<String>? = null,   // For groups
    val online: Boolean? = null          // For direct messages
)

enum class ConversationType { DIRECT, GROUP, CHANNEL }
```

### File: `core/model/src/main/java/org/enchant/core/model/User.kt`

```kotlin
data class User(
    val userId: String,
    val username: String,
    val displayName: String? = null,
    val about: String? = null,
    val avatarUrl: String? = null,
    val online: Boolean? = null,
    val lastSeen: Long? = null
)
```

### File: `core/model/src/main/java/org/enchant/core/model/CallLog.kt`

```kotlin
data class CallLog(
    val callId: String,
    val remoteUserId: String,
    val type: CallType,
    val direction: CallDirection,
    val durationSeconds: Int,
    val status: CallStatus,
    val endedAt: Long
)
enum class CallType { AUDIO, VIDEO, GROUP_AUDIO, GROUP_VIDEO }
enum class CallDirection { INCOMING, OUTGOING }
enum class CallStatus { MISSED, ANSWERED, OUTGOING, CANCELLED }
```

### File: `core/model/src/main/java/org/enchant/core/model/Contact.kt`

```kotlin
data class Contact(
    val userId: String,
    val displayName: String?,
    val username: String?,
    val phoneNumber: String?,
    val avatarUrl: String?,
    val isBlocked: Boolean = false,
    val isFriend: Boolean = false,
    val customName: String? = null
)
```

---

## Acceptance Criteria

Before Phase 1 is complete:

- [ ] All 32 files created with all required functions
- [ ] All tests pass (target: 220+ tests)
- [ ] Line coverage > 95% for `:core:crypto`, > 90% for `:core:database` and `:core:network`
- [ ] Known-answer tests for all crypto primitives pass
- [ ] WebSocket connects, authenticates, and receives messages (integration test)
- [ ] Database initializes with correct schema (verified via `PRAGMA table_info`)
- [ ] Offline queue stores messages, drains when connectivity returns
- [ ] Rate limit tracker correctly delays calls
- [ ] JWT auto-refresh works on 401
- [ ] Reconnection with exponential backoff works
- [ ] No secrets logged anywhere (verified via logcat grep)
- [ ] KeyStore correctly stores and retrieves identity keys
- [ ] SecurePreferences encrypts values at rest

---

---

## Module: `:core:jobmanager` (6 files + 15 job files)

**Purpose:** Signal-equivalent `JobManager` system — backbone for all async background work. Every message send, attachment download, profile upload, group operation, and sync task is modeled as a `Job`.

**Architecture:**
```
JobManager (singleton) → JobRunner → Job (abstract work unit)
                                → Constraint (pre-conditions)
                                → Scheduler (triggers when constraints met)
                                → JobStorage (persistence via SQLite)
```

### File: `core/jobmanager/src/main/java/org/enchant/core/jobmanager/Job.kt`

**Purpose:** Abstract base class for all background jobs. Signal's `Job.java` equivalent.

```kotlin
abstract class Job(val parameters: Params) {
    abstract suspend fun onRun(): Result
    abstract fun onShouldReschedule(exception: Exception): Boolean
    open fun onAdded() {}
    open fun onRetry() {}
    open fun onCanceled() {}

    data class Params(
        val queue: String? = null,                    // Queue key for serial execution (e.g., "send_conversation_123")
        val maxAttempts: Int = 3,                      // Max retry count
        val maxInstances: Int = 1,                     // Max simultaneous instances
        val maxInstancesForFactory: Int = Int.MAX_VALUE,
        val constraints: List<KClass<out Constraint>> = emptyList(),  // Pre-conditions
        val lifespan: Long = TimeUnit.DAYS.toMillis(7), // Time before auto-cancel
        val maxRunTime: Long = TimeUnit.MINUTES.toMillis(5) // Max execution time
    )

    sealed class Result {
        data object Success : Result()
        data class Failure(val retryable: Boolean = true) : Result()
    }
}
```

| Function | Signature | Description |
|---|---|---|
| `onRun` | `abstract suspend fun onRun(): Result` | Execute the job — return SUCCESS or FAILURE | Must handle all exceptions internally, return `Result.Failure` not throw |
| `onShouldReschedule` | `abstract fun onShouldReschedule(exception: Exception): Boolean` | Whether job should retry after failure | Default: true for network errors, false for permanent failures |
| `onAdded` | `open fun onAdded()` | Called when job is first added to queue | For initialization |
| `onRetry` | `open fun onRetry()` | Called before each retry attempt | For logging/metrics |
| `onCanceled` | `open fun onCanceled()` | Called when all retries exhausted or job canceled | For cleanup |

**Test requirements:** 5 tests — onRun success, onRun failure, onShouldReschedule true/false, lifecycle callbacks execute in order

### File: `core/jobmanager/src/main/java/org/enchant/core/jobmanager/JobManager.kt`

**Purpose:** Central singleton that tracks, schedules, and runs jobs. Signal's `JobManager.java` equivalent.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `init` | `suspend fun init()` | Initialize job storage, start scheduler | Already initialized → no-op |
| `add` | `suspend fun add(job: Job)` | Add a single job to queue | Validate parameters; dedup by queue+key if needed |
| `addAll` | `suspend fun addAll(jobs: List<Job>)` | Add multiple jobs atomically | All or nothing |
| `schedule` | `suspend fun schedule()` | Run pending jobs whose constraints are met | Called when constraints change |
| `cancel` | `suspend fun cancel(queueKey: String)` | Cancel all jobs for a queue key | Used when leaving group or deleting conversation |
| `cancelAll` | `suspend fun cancelAll()` | Cancel all queued jobs | On logout |
| `getRunningCount` | `fun getRunningCount(): Int` | Current running job count | — |
| `getQueuedCount` | `fun getQueuedCount(): Int` | Currently queued job count | — |
| `onConstraintMet` | `suspend fun onConstraintMet(constraintClass: KClass<out Constraint>)` | Wake up scheduler when constraint is satisfied | Called by ConnectivityMonitor when going online |
| `shutdown` | `suspend fun shutdown()` | Graceful shutdown — wait for running jobs | Don't wait forever (timeout 10s) |

**Job lifecycle:**
```
add → onAdded() → (wait for constraints) → onRetry() → onRun()
                                                          ↓
                                           ┌──────────────┼──────────────┐
                                           ▼              ▼              ▼
                                      Success        Failure          throws
                                                      (retryable)     (non-retryable)
                                                        ↓                 ↓
                                                    onRetry()         onCanceled()
                                                    (maxAttempts)
                                                        ↓
                                                   onCanceled()
```

**Test requirements:** 12 tests — add single job, add batch, cancel by queue, cancel all, schedule triggers when constraints met, running/queued counts, shutdown waits, shutdown timeout, duplicate dedup, job runs in order per queue, max instances enforcement, lifespan timeout

### File: `core/jobmanager/src/main/java/org/enchant/core/jobmanager/Constraint.kt`

**Purpose:** Pre-condition checks that must be satisfied before a job runs. Signal's `Constraint.java` equivalent.

| Function | Signature | Description |
|---|---|---|
| `isMet` | `suspend fun isMet(): Boolean` | Check if constraint is satisfied | Must not block for more than 100ms |
| `getFactoryKey` | `fun getFactoryKey(): String` | Unique key for serialization | Must match across app restarts |

```kotlin
class NetworkConstraint(private val connectivityMonitor: ConnectivityMonitor) : Constraint {
    override suspend fun isMet(): Boolean = connectivityMonitor.isOnline.value
    override fun getFactoryKey(): String = "Network"
}

class WebSocketConnectedConstraint(private val webSocketManager: WebSocketManager) : Constraint {
    override suspend fun isMet(): Boolean = webSocketManager.connectionState.value == ConnectionState.CONNECTED
    override fun getFactoryKey(): String = "WebSocketConnected"
}

class BatteryNotLowConstraint(context: Context) : Constraint {
    override suspend fun isMet(): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return level < 0 || scale < 0 || (level.toFloat() / scale) > 0.15
    }
    override fun getFactoryKey(): String = "BatteryNotLow"
}
```

**Built-in constraints:** `NetworkConstraint`, `WebSocketConnectedConstraint`, `BatteryNotLowConstraint`, `DecryptionDrainedConstraint` (all decryption jobs complete), `ChargingConstraint` (device is charging)

**Test requirements:** 5 tests — each constraint met/unmet, edge cases (battery level boundary, no battery data)

### File: `core/jobmanager/src/main/java/org/enchant/core/jobmanager/JobStorage.kt`

**Purpose:** Persists jobs to SQLite so they survive app restarts. Signal's `JobStorage` equivalent.

| Function | Signature | Description |
|---|---|---|
| `insert` | `suspend fun insert(job: Job, serializedData: ByteArray)` | Insert job to queue | Serialize job parameters and data |
| `getNext` | `suspend fun getNext(): SerializableJob?` | Get next job to run by priority + creation order | — |
| `getByQueue` | `suspend fun getByQueue(queue: String): List<SerializableJob>` | Get all jobs in a queue | For cancel by queue |
| `markRunning` | `suspend fun markRunning(jobId: Long)` | Set job as running | — |
| `markCompleted` | `suspend fun markCompleted(jobId: Long, success: Boolean)` | Set job as completed | — |
| `incrementAttempts` | `suspend fun incrementAttempts(jobId: Long): Int` | Increment retry count, return new count | — |
| `removeExpired` | `suspend fun removeExpired()` | Delete jobs past lifespan | — |
| `clearAll` | `suspend fun clearAll()` | Clear entire job table | On logout |

**Test requirements:** 8 tests — insert/getNext, queue ordering, markRunning/markCompleted, incrementAttempts, removeExpired, clearAll, survive app restart (in-memory + SQLite)

### File: `core/jobmanager/src/main/java/org/enchant/core/jobmanager/Scheduler.kt`

**Purpose:** Triggers job execution when constraints are met. Uses WorkManager for deferrable tasks + in-app immediate execution.

| Function | Signature | Description |
|---|---|---|
| `schedule` | `suspend fun schedule()` | Check all pending jobs, run those with met constraints | Use coroutine per job, respect maxInstances |
| `scheduleBackground` | `fun scheduleBackground(context: Context, jobClass: Class<*>, constraints: Constraints)` | Schedule via WorkManager for periodic or deferred tasks | Platform-specific: WorkManager for Android |
| `cancelBackground` | `fun cancelBackground(context: Context, tag: String)` | Cancel a WorkManager task | — |

**Test requirements:** 3 tests — immediate schedule, background schedule, cancel

### Concrete Jobs (15 files in `core/jobmanager/src/main/java/org/enchant/core/job/jobs/`)

Each file implements `Job` for a specific operation. All follow the same pattern:

```kotlin
class PushSendJob(
    private val conversationId: String,
    private val recipientUserId: String,
    private val encryptedPayload: ByteArray,
    private val messageType: String,
    private val messageId: Long
) : Job(Params(
    queue = "send_$conversationId",
    maxAttempts = 5,
    constraints = listOf(NetworkConstraint::class, WebSocketConnectedConstraint::class),
    lifespan = TimeUnit.DAYS.toMillis(30)
)) {
    override suspend fun onRun(): Result {
        return try {
            AppDependencies.webSocketManager.sendMessage(recipientUserId, null, encryptedPayload)
            AppDependencies.database.messageDao().updateStatus(messageId, "sent")
            Result.Success
        } catch (e: Exception) {
            Result.Failure(retryable = true)
        }
    }
    override fun onShouldReschedule(e: Exception) = true
}
```

**Job 1: `PushSendJob.kt`** — Send message via WebSocket. Queue: per-conversation. Max 5 attempts. Constraints: Network + WebSocket.
**Job 2: `PushGroupSendJob.kt`** — Send group fan-out message. Queue: per-group. Calls `GroupSendUtil` to split sender-key vs legacy.
**Job 3: `AttachmentDownloadJob.kt`** — Download encrypted attachment from Media server. Queue: per-attachment. Constraints: Network.
**Job 4: `AttachmentUploadJob.kt`** — Upload encrypted attachment to Media server. Queue: per-attachment. Constraints: Network + BatteryNotLow.
**Job 5: `ProfileUploadJob.kt`** — Upload profile avatar. Max 3 attempts. No queue.
**Job 6: `MultiDeviceProfileKeyUpdateJob.kt`** — Sync profile key to linked devices. No queue.
**Job 7: `StorageSyncJob.kt`** — Sync contacts/groups to storage service. Constraints: Network + WebSocket.
**Job 8: `SendRetryReceiptJob.kt`** — Send retry receipt for decryption failure. No queue.
**Job 9: `ResendMessageJob.kt`** — Resend message on retry request. Queue: per-conversation.
**Job 10: `SenderKeyDistributionSendJob.kt`** — Send sender key distribution message. Queue: per-group.
**Job 11: `BackupMessagesJob.kt`** — Periodically back up messages. Periodic through WorkManager (daily).
**Job 12: `LocalBackupJob.kt`** — Create local encrypted backup file. Constraints: Network + BatteryNotLow + Charging.
**Job 13: `TrimThreadJob.kt`** — Trim old messages from thread. Periodic through WorkManager (weekly).
**Job 14: `MultiDeviceContactUpdateJob.kt`** — Sync contact update to linked devices.
**Job 15: `RequestGroupInfoJob.kt`** — Fetch group info from server after invite accept.

**Test requirements per job:** 4 tests — onRun success, onRun failure (retryable), onRun failure (permanent → not retryable), onShouldReschedule correct
**Total for all jobs:** 60 tests

---

## Module: `:core:signalstore` (1 file + 23 Values classes)

**Purpose:** Typed, encrypted key-value store modeled after Signal's `SignalStore.kt`. Each feature domain gets its own typed Values class with getters/setters, all backed by the same encrypted SQLite or EncryptedSharedPreferences instance.

Signal has 23 Values classes. We replicate the pattern.

### File: `core/signalstore/src/main/java/org/enchant/core/signalstore/SignalStore.kt`

**Purpose:** Central singleton providing typed accessors for all value domains.

| Function | Signature | Description |
|---|---|---|
| `init` | `suspend fun init(context: Context)` | Initialize encrypted backing store | Must create/find encryption key via KeyStore |
| `account` | `val account: AccountValues` | Registration/account values |
| `backup` | `val backup: BackupValues` | Backup settings |
| `registration` | `val registration: RegistrationValues` | Registration session data |
| `settings` | `val settings: SettingsValues` | User settings (theme, font, notifications) |
| `pin` | `val pin: PinValues` | PIN hash, attempts remaining |
| `storageService` | `val storageService: StorageServiceValues` | Storage service state |
| `story` | `val story: StoryValues` | Story settings, viewed stories |
| `wallpaper` | `val wallpaper: WallpaperValues` | Per-conversation wallpaper |
| `labs` | `val labs: LabsValues` | Feature flags / labs |
| `phoneNumberPrivacy` | `val phoneNumberPrivacy: PhoneNumberPrivacyValues` | Phone number sharing |
| `emoji` | `val emoji: EmojiValues` | Recent emoji, reactions |
| `chatColors` | `val chatColors: ChatColorsValues` | Color preferences |
| `callQuality` | `val callQuality: CallQualityValues` | Call quality surveys |
| `proxy` | `val proxy: ProxyValues` | Proxy settings |
| `rateLimit` | `val rateLimit: RateLimitValues` | Rate limit tracking |
| `onboarding` | `val onboarding: OnboardingValues` | Onboarding completion |
| `internal` | `val internal: InternalValues` | Internal/testing values |

**Each Values class follows this pattern:**
```kotlin
class AccountValues(private val store: KeyValueStore) {
    companion object {
        private const val KEY_USER_ID = "account.user_id"
        private const val KEY_IS_REGISTERED = "account.is_registered"
        private const val KEY_IS_MULTI_DEVICE = "account.is_multi_device"
        private const val KEY_DEVICE_ID = "account.device_id"
    }
    var userId: String?
        get() = store.getString(KEY_USER_ID, null)
        set(value) { value?.let { store.putString(KEY_USER_ID, it) } ?: store.remove(KEY_USER_ID) }
    var isRegistered: Boolean
        get() = store.getBoolean(KEY_IS_REGISTERED, false)
        set(value) = store.putBoolean(KEY_IS_REGISTERED, value)
    var isMultiDevice: Boolean
        get() = store.getBoolean(KEY_IS_MULTI_DEVICE, false)
        set(value) = store.putBoolean(KEY_IS_MULTI_DEVICE, value)
    var deviceId: String?
        get() = store.getString(KEY_DEVICE_ID, null)
        set(value) { value?.let { store.putString(KEY_DEVICE_ID, it) } ?: store.remove(KEY_DEVICE_ID) }
}
```

**23 Values classes and their keys:**

| Values Class | Key Prefix | Notable Keys | Phase Used In |
|---|---|---|---|
| `AccountValues` | `account.` | `user_id`, `is_registered`, `is_multi_device`, `device_id` | 1, 2 |
| `BackupValues` | `backup.` | `last_backup_timestamp`, `last_backup_version`, `backup_key` | 6 |
| `RegistrationValues` | `registration.` | `session_id`, `session_e164`, `challenge_id` | 2 |
| `SettingsValues` | `settings.` | `theme_mode`, `font_size_scale`, `message_notifications_on`, `show_preview`, `enter_is_send` | 6 |
| `PinValues` | `pin.` | `pin_hash`, `attempts_remaining`, `is_alphanumeric` | 2 |
| `StorageServiceValues` | `storage_service.` | `manifest_version`, `last_sync_timestamp` | 5 |
| `StoryValues` | `story.` | `has_seen_release_channel`, `archived_story_ids` | 5 |
| `WallpaperValues` | `wallpaper.` | Per-conversation: `{conv_id}.wallpaper_path`, `{conv_id}.dim_level` | 6 |
| `LabsValues` | `labs.` | `internal_feature_enabled`, `experimental_groups` | 7 |
| `PhoneNumberPrivacyValues` | `pnp.` | `sharing_mode` (EVERYONE/CONTACTS/NOBODY), `discoverable` | 6 |
| `EmojiValues` | `emoji.` | `recent_list` (JSON array), `reactions_enabled` | 3 |
| `ChatColorsValues` | `chat_colors.` | `default_color_id`, `{conv_id}.color_id` | 6 |
| `CallQualityValues` | `call_quality.` | `last_survey_timestamp`, `survey_answered` | 4 |
| `ProxyValues` | `proxy.` | `host`, `port`, `enabled`, `username`, `password` | 1 |
| `RateLimitValues` | `rate_limit.` | `{endpoint}.window_start`, `{endpoint}.call_count` | 1 |
| `OnboardingValues` | `onboarding.` | `completed`, `current_step`, `has_seen_welcome` | 2 |
| `InternalValues` | `internal.` | `debug_logging_enabled`, `last_crash_timestamp` | 7 |

**Backing store:** `KeyValueStore` wraps `EncryptedSharedPreferences` or custom SQLCipher-backed store. Must support:
- `getString(key, default)`, `putString(key, value)`, `remove(key)`
- `getBoolean(key, default)`, `putBoolean(key, value)`
- `getInt(key, default)`, `putInt(key, value)`
- `getLong(key, default)`, `putLong(key, value)`
- `getStringSet(key, default)`, `putStringSet(key, value)`
- `contains(key)`, `clear()`

**Test requirements for SignalStore base:** 6 tests — init, each value type stores/retrieves correctly, clear all, remove individual key, overwrite existing value, survive init/save/close/re-init cycle

**Test requirements per Values class:** 3 tests — default value correct, write and read roundtrip, remove clears
**Total for all Values classes:** 69 tests, plus 6 base = **75 tests**

---

---

## Module: `core/base/src/main/java/org/enchant/core/base/BootReceiver.kt`

**Purpose:** Restarts WebSocket service after device reboot. Signal's `BootReceiver.java` equivalent.

| Function | Signature | Description |
|---|---|---|
| `onReceive` | `override fun onReceive(context: Context, intent: Intent)` | On `ACTION_BOOT_COMPLETED` or `ACTION_QUICKBOOT_POWERON`, start WebSocket foreground service | Must check if user was previously logged in before starting; don't start if user never completed onboarding |

**Manifest entry (in `app/src/main/AndroidManifest.xml`):**
```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<receiver
    android:name=".core.base.BootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
    </intent-filter>
</receiver>
```

**Test requirements:** 2 tests — receives BOOT_COMPLETED and starts service, does NOT start if user is not authenticated

---

## Module: `:core:config` (2 files)

**Purpose:** Feature flags and remote config — allows server-side feature toggling without app updates.

### File: `core/config/src/main/java/org/enchant/core/config/FeatureFlags.kt`

**Purpose:** Firebase Remote Config integration for server-side feature toggles.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `init` | `suspend fun init()` | Fetch and activate Firebase Remote Config defaults | Firebase unavailable → use defaults from XML |
| `isEnabled` | `fun isEnabled(key: String): Boolean` | Check if a feature flag is enabled | Key not found → return false |
| `getInt` | `fun getInt(key: String, default: Int): Int` | Get integer config value | Key not found → return default |
| `getString` | `fun getString(key: String, default: String): String` | Get string config value | Key not found → return default |

**Default flags (in `res/xml/remote_config_defaults.xml`):**

| Flag | Type | Default | Description |
|---|---|---|---|
| `show_announcement_groups` | Boolean | `true` | Enable announcement-only group mode |
| `enable_call_links` | Boolean | `true` | Enable call link feature |
| `enable_stories` | Boolean | `true` | Enable status/stories feature |
| `enable_sealed_sender` | Boolean | `false` | Enable anonymous sealed sender (requires server support) |
| `max_group_size` | Integer | `500` | Maximum members per group |
| `max_attachment_size_mb` | Integer | `128` | Maximum upload size in MB |
| `message_retention_days` | Integer | `365` | Default message retention period |
| `force_certificate_pinning` | Boolean | `true` | Whether cert pinning is enforced |
| `minimum_app_version` | Integer | `1` | Minimum app version code for forced upgrade |

**Test requirements:** 5 tests — init, flag enabled, flag disabled, flag missing returns default, remote config fetch failure uses defaults

---

## Updated File Count

| Module | Files | Tests |
|---|---|---|
| `:core:base` | 6 (+1) | 61 (+2) |
| `:core:network` | 8 | 90 |
| `:core:database` | 8 | 88 |
| `:core:crypto` | 6 | 111 |
| `:core:model` | 5 | 8 |
| `:core:jobmanager` | 21 | 90 |
| `:core:signalstore` | 24 | 75 |
| `:core:config` (NEW) | 2 | 5 |
| **Total** | **~80** | **~528** |
