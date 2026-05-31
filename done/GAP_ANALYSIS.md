# Enchant — Comprehensive Gap Analysis vs Production Standards

**Date:** 2026-05-30
**Scope:** Frontend (Android) + Backend
**Purpose:** Identify every gap between current implementation and production-ready Signal Android parity

---

## Executive Summary

| Category | Open Issues | Critical |
|----------|-------------|----------|
| Missing Features | ~120 | 15 |
| Bugs & Logic Errors | ~40 | 8 |
| Stub Functions | ~25 | 3 |
| Security Flaws | ~35 | 12 |
| Production Gaps | ~60 | 20 |
| Backend Gaps | ~40 | 10 |

**Total actionable items: ~280**

---

## CRITICAL PRIORITY — Fix Before Any Release

### 1. OPK Overwrite Bug (`KeyManager.kt:296-315`)
`topUpOpks()` writes new batch starting at index 0, silently destroying unconsumed OPKs from previous batches. A user who opens the app on multiple devices will have their OPK pool destroyed on each session.

### 2. PIN Stored as SHA-256 Only (`TwoStepPinScreen.kt`, `AppLockScreen.kt`)
No key derivation (Argon2id/scrypt). Each PIN has ~10^6 possibilities — trivial dictionary attack. Backend stores the hash, so server compromise = PIN compromise.

### 3. SQLCipher KDF Iter = 1 (`DatabasePool.kt:19-20`)
`PRAGMA cipher_default_kdf_iter = 1` and `kdf_iter = 1` — effectively no key stretching. Production requires 256,000 iterations (PBKDF2_HMAC_SHA512).

### 4. sendReaction Missing conversation_id (`MessageSendPipeline.kt:255`)
Backend requires `conversation_id` in reaction body but client never sends it.

### 5. Media Key in Plaintext Payload (`MessageSendPipeline.kt:213-215`)
Attachment encryption key sent in plaintext alongside encrypted attachment. Should encrypt key using recipient's session chain key (layered encryption).

### 6. Auth State Not Observed (`AuthNavDisplay.kt`)
OTP verification completion, key registration finish — none drive automatic navigation. User must manually tap through screens that should advance automatically.

### 7. WebSocket Fire-and-Forget (`core:network`)
`sendMessage()` returns without waiting for delivery confirmation. User sees "sent" but server may have dropped the message. No retry, no user notification of failure.

### 8. No Foreground Service for WebSocket
WebSocket disconnects when app goes background. No `START_STICKY` service with persistent notification to maintain connection. Messages arrive late or not at all.

### 9. TURN Credentials in Plaintext Memory (`CallManager.kt:68-69`)
`turnServers` list stores username/credential in heap memory. PeerConnection construction passes plaintext credentials. Memory dump = TURN access.

### 10. Missing loadSettings Error Handling (`SettingsViewModel.kt:65-90`)
`onFailure = {}` — all API failures silently swallowed. User sees default settings, never knows sync failed.

### 11. Profile loadMyProfile Uses "me" Path (`ProfileViewModel.kt:75`)
`GET /v1/profile/me` endpoint does not exist. Should use stored `userId` from AuthState.

### 12. Disappearing Timer / Auto-Download Unwired (`ChatsSettingsScreen.kt`)
UI controls exist and accept input but no `ViewModel` function handles them and no API call fires.

---

## Module-by-Module Analysis

---

## core:auth

### Missing Features
- No `/v1/accounts/whoami` call on cold start to validate stored JWT
- No registration lock (2FA/PIN) — SVR not integrated
- No concurrent device linking flow
- No prekey reuse detection (`checkRepeatedUseKeys()`)
- No Kyber (post-quantum) hybrid KEM support
- No SVR (Secure Value Recovery) PIN-based key backup

### Bugs & Logic Errors
- `AuthManager.refreshToken()` swallows exceptions with no retry mechanism
- JWT expiry check uses device system time — no server time sync
- No username availability check wired in registration flow

### Security Flaws
- JWT stored in SecurePreferences — logout clears locally but no server-side revocation
- OTP cooldown enforced client-side only — device clock manipulation bypasses
- No HMAC/JWKS verification on JWT integrity — backend JWKS endpoint deferred

### Production Gaps
- No foreground service for auth state persistence across process death
- `AuthManager.init()` creates new `ApiClient()` directly, bypassing DI

---

## core:crypto

### Missing Features
- No sealed sender certificate chain validation
- No session archive on identity key change (UI notification missing)
- No forward secrecy interval enforcement (1000 messages / 7 days)
- No message gap detection / resync
- No group sender key management beyond minimal implementation
- No PreKeyWorker registered with WorkManager

### Bugs & Logic Errors
- `buildPreKeyPayload()` hardcodes `spkId = 0` — breaks when actual SPK ID differs
- `ensureOpkBatch()` maps `privateKey` to `ByteArray(32)` zeros — decryption would fail
- `SealedSender.deriveAccessKey()` uses wrong nonce derivation vs leading apps reference

### Security Flaws
- Identity key stored in memory only — lost on process death, user must re-register
- No `SecureBuffer` RAII pattern for key material
- CryptoPrimitives not audited for CSPRNG usage (`kotlin.random.Random`)

### Production Gaps
- No key rotation on app version update
- No `PreKeyWorker` WorkManager job scheduled

---

## core:network

### Missing Features
- No TLS 1.3 enforcement — OkHttpClient has no explicit `TlsVersion.TLS_1_3`
- No certificate pinning on gateway
- No WebSocket batch message reading (`readMessageBatch()`)
- No request cancellation support
- No message priority queue
- No connection health monitoring (`HealthMonitor`)

### Bugs & Logic Errors
- `sendMessage()` returns without delivery confirmation — fire-and-forget for both WS and REST
- `OfflineQueue.drain()` silently drops messages with no error propagation
- `isJwtExpired()` catches all exceptions and returns `true` — triggers unnecessary refresh

### Security Flaws
- JWT passed in URL query params for WebSocket — leaks to proxy/access logs
- `SecurePreferences` key names in plaintext strings

### Production Gaps
- No Prometheus `/metrics` endpoint for disconnect rate, decryption failure rate tracking

---

## core:database

### Missing Features
- Missing tables: reactions, mentions, drafts, attachments, pre-keys, sender-keys
- No message retention trimming worker
- No `EncryptedSharedPreferences` for session/identity key storage

### Bugs & Logic Errors
- `readerPool` never validates passphrase correctness
- No transaction timeout on `beginTransaction()`

### Security Flaws
- **KDF iter = 1** — `cipher_default_kdf_iter = 1`, `kdf_iter = 1` — dev-only setting
- No SQLCipher key hierarchy — DB key not derived from user PIN + device secret via HKDF
- No `PRAGMA cipher_page_size = 4096`
- No `PRAGMA cipher_hmac = SHA512`

### Production Gaps
- No message auto-truncate (12 month retention)
- No backup exclusion metadata in manifest

---

## core:store

### Bugs & Logic Errors
- `ApplicationMigrations` — migration logic not confirmed to run on upgrade

### Security Flaws
- `EnchantCrashHandler` not verified for sensitive data scrubbing
- `SecurePreferences` not `FLAG_SECURE` aware

### Notes
- Least problematic module — audit confirmed most items correct

---

## core:push

### Missing Features
- No FCM payload schema validation
- No duplicate message detection (message ID cache)
- No call/group notification handling
- No FCM token encryption at rest
- No `ProcessLifecycleOwner` integration

### Bugs & Logic Errors
- `isAppInForeground()` uses deprecated `ActivityManager.runningAppProcesses` — stale on Android 11+
- `startForegroundService()` from `onMessageReceived` — 5-second timeout on Android 12+ with no handler

### Security Flaws
- Notification content visible on lock screen before unlock
- FCM token transmitted without confirmed encryption

### Production Gaps
- No foreground service type declaration (`dataSync`) for API 34+
- No battery optimization UI guide

---

## core:notifications

### Missing Features
- No custom notification icons — hardcoded `android.R.drawable` system icons
- No notification sound/vibration configuration
- No group/mention channel differentiation

### Bugs & Logic Errors
- `buildMessageNotification()` uses `android.R.drawable.ic_dialog_info` — security exception on Android 13+
- `setGroupSummary(true)` without `setShortcutInfo()` — silently dropped on Android 12+

### Security Flaws
- Lock screen notification could expose message content

### Production Gaps
- No notification channel for group mentions or calls

---

## core:performance

### Missing Features
- No `MessageCache` encryption at rest
- No `ImagePipeline` disk cache limits (file count, size)
- No cache size reporting
- No `MessageCache` TTL or watermark trimming
- No dry-run mode for `MessageTrimmer`
- No battery-aware throttle

### Bugs & Logic Errors
- `MessageTrimmer` exists but no WorkManager job calls it

### Security Flaws
- `ImagePipeline` caches images to disk unencrypted

---

## core:jobmanager

### Missing Features
- No `MessageSendWorker`, `AttachmentDownloadWorker` implementations
- No WorkManager integration — custom `JobManager` instead
- No job deduplication
- No cleanup of old job history

### Bugs & Logic Errors
- `JobManager.initialize()` has no access control
- `MinimalJobSpec` equality ignores `serializedData` — could cause duplicate jobs

### Security Flaws
- Job data stored as plaintext in SQLite (`FastJobStorage`)
- No job data scrubbing in `JobLogger`

### Production Gaps
- Job retry backoff uses `Math.random()` not CSPRNG
- No distinction between transient and permanent `JobResult` failures

---

## core:base

### Missing Features
- No bootstrap coordinator for init order

### Bugs & Logic Errors
- `SecurePreferences.init()` race condition on double-checked locking

### Security Flaws
- `KeyStoreManager` generates identity keys in soft keystore if `StrongBox` fails silently
- `Scrubber` implementation not verified against production rules

### Production Gaps
- No `StrictMode` in debug builds

---

## core:ui

### Missing Features
- No RTL support in `TransitionSpecs`
- No reduced motion accessibility support
- Missing common UI components (buttons, loaders, etc.)

### Bugs & Logic Errors
- Window breakpoint detection exists but no actual tablet/desktop layout variants

### Security Flaws
- No `FLAG_SECURE` mechanism on chat screens — each Activity must add manually

### Production Gaps
- No TalkBack content descriptions on all icon buttons

---

## core:navigation

### Missing Features
- No deep link action verification
- No `SafeArgs` integration
- No nested navigation graph support
- No back stack management utilities

### Security Flaws
- Deep links don't validate decoded parameters against strict schema

---

## core:accessibility

### Missing Features
- Minimal `AccessibilityDelegate` — not wired to all composables
- No TalkBack custom actions for messages
- No focus management

### Production Gaps
- No accessibility testing in CI

---

## core:calls

### Missing Features
- No TURN credential fetch retry logic
- No video quality adaptation (hardcoded resolution)
- No call recording support
- No call transfer/swap support
- No group call signaling methods in `SignalingClient`
- No call link (room-based) signaling API

### Bugs & Logic Errors
- `peekGroupCall` always returns null — stub
- Double processing of answer in `handleReceivedAnswer`
- `handleAcceptIncomingCall` race window
- `CallPhase` vs `CallStatus` inconsistency — two enum systems

### Stub Functions
- `SignalingClient` interface — no encryption contract
- `CallLinkModels` exist with no API

### Security Flaws
- **TURN credentials in plaintext memory** — Critical
- **ICE candidate serialization leaks LAN IPs** — Critical
- `CallNotificationReceiver` broadcast vulnerability — any app with matching intent permission can trigger call actions
- No TLS certificate pinning in WebRTC engine

### Production Gaps
- No foreground service type (`microphone|connectedDevice`) for API 34+
- No call quality metrics reporting

---

## feature:chat

### Missing Features
- Message translation (`POST /v1/messages/{id}/translate`)
- Reply preview (`GET /v1/messages/{id}/reply`)
- Contact card sharing (`POST /v1/contacts/share`)
- Location message integration (data class, ChatService wiring)
- Reactions toggle UI
- Reply chain display
- Edit message isolation
- Clipboard auto-clear
- Circuit breaker pattern

### Bugs & Logic Errors
- `sendReaction` missing `conversation_id` — Critical (P1 in TODO.md)
- Message types (Location/Sticker/ContactCard) need backend schema

### Security Flaws
- **Media encryption key in plaintext payload** — Critical (SEC2 in TODO.md)
- No `SecurePreferences` DI — directly instantiates

### Production Gaps
- No `MessageSearchRepository` — FTS5 exists but not wired
- No `NotificationPreferencesRepository`
- No paging implementation verified against leading apps reference

---

## feature:chatlist

### Missing Features
- No encryption indicator for offline queue
- Pin not synced to server
- No pagination
- No swipe gestures
- Search doesn't cover participant names
- No search-specific empty state

---

## feature:contacts

### Missing Features
- No phone hash salt/HKDF — raw SHA-256 only
- No contact delta sync
- No reverse contact search
- No Room caching
- No `SavedStateHandle` in ViewModel

### Security Flaws
- **Phone hash uses raw SHA-256** — should use Argon2id (SEC3 in TODO.md)

---

## feature:groups

### Missing Features
- No QR code for group invite
- No group avatar upload
- No group search
- No revoke invite link
- No combined block+remove member
- No pagination on members
- No WebSocket updates for real-time group changes
- Group settings (disappearing messages, messaging mode) not wired
- No sender key distribution management

### Bugs & Logic Errors
- Revision conflicts — optimistic locking not properly handled
- Race condition in `GroupStateProcessor` on concurrent modification
- Group preview link may leak metadata

### Security Flaws
- Client trusts server for admin/permission checks rather than validating locally

### Production Gaps
- ViewModel is a god object
- Raw SQL queries throughout

---

## feature:channels

### Missing Features
- Create/edit/delete/pin posts
- Admin management, block/report
- Subscribe/unsubscribe (`POST/DELETE /v1/channels/{id}/subscribe`)
- Generate invite, admin tools
- Channel avatar display, media display
- `hasMore` flag for pagination

### Bugs & Logic Errors
- `hasMore` flag not provided by backend

### Security Flaws
- Admin/permission checks not validated client-side
- Creator validation not done

---

## feature:profile

### Missing Features
- E2E encryption of profile fields (needs SealedSender + profile key exchange)
- Avatar download (cross-module dependency on `MediaService.getBinary()`)
- Edit form UI (`EditProfileScreen` does not exist)
- Profile privacy settings (`PUT /v1/profile/privacy`)

### Bugs & Logic Errors
- **`loadMyProfile` uses `GET /v1/profile/me`** — endpoint does not exist (SEC5 in TODO.md)

### Production Gaps
- `ApiClient.getInstance()` not injected

---

## feature:settings

### Missing Features
- Notification sound/vibration configuration
- Language/locale setting
- Auto-download size threshold
- Message trim (stub-only, needs server support)
- Backup encryption password handling
- Two-step 2FA setup flow (placeholder only)

### Bugs & Logic Errors
- `updateFontSize`/`updateTheme` give no saving/saved feedback — no `isSaving` state
- `onDisappearingTimerChange` callback wired but no ViewModel handler
- `autoDownloadWifi`/`autoDownloadCellular` never synced

### Security Flaws
- Privacy settings sent to server unverified (server enforcement not confirmed)
- `BlockedUsersScreen` uses `ApiClient.getInstance()` not injected

### Production Gaps
- Inconsistent error handling across all methods
- Tests shallow with no `coVerify` assertions
- Tests reference non-existent enum types (passed via string coercion)

---

## feature:status

### Missing Features
- Get single status (`GET /v1/status/{status_id}` not wired)
- Get my status — `loadMyStatus()` uses `userId == "me"` but no `GET /v1/status/me` exists

### Bugs & Logic Errors
- `loadMyStatus` filters by `userId == "me"` — should use stored `status_id`

---

## feature:auth

### Missing Features
- Biometric enrollment check (`BIOMETRIC_STRONG` enrollment vs capability)
- Username availability wired to backend
- Key generation progress feedback to ViewModel
- TwoStepPin flow in navigation
- Rate limiting UI for OTP

### Bugs & Logic Errors
- SMS OTP auto-read and auto-submit without user confirmation
- Silent exception swallowing throughout
- OTP race condition — SMS can overwrite manual entry

### Security Flaws
- **PIN stored as SHA-256 only** — no Argon2id/scrypt KDF — Critical
- **PIN transmitted to server as hash** — server stores crackable hash
- No biometric fallback lockout limit
- No secure text field for PIN entry

### Production Gaps
- `AuthNavDisplay` is a 150-line god composable
- No UI tests for any screen
- Hardcoded delay values (30s, 60s, 300ms) not extracted to constants

---

## feature:registration

### Missing Features
- `/v1/accounts/whoami` endpoint (backend)
- Username reservation endpoint (backend)
- Registration lock (2FA/PIN) backend
- Phone number change flow backend
- FCM token management backend
- Concurrent device linking design

### Security Flaws
- JWT integrity verification (HMAC/JWKS) deferred — backend JWKS endpoint not implemented
- No prekey reuse detection (server-side key state tracking deferred)
- No Kyber post-quantum support
- No SVR integration

---

## feature:location

### Missing Features
- Location message integration (data class + ChatService wiring)
- Location message UI in conversation
- Location persistence/history
- Unsend/delete location message
- `POST /v1/location` and `GET /v1/location/{id}` not wired

### Bugs & Logic Errors
- `reverseGeocodeAddress` uses `GlobalScope` instead of lifecycle scope

### Production Gaps
- 298-line composable with no ViewModel extraction

---

## feature:polls

### Missing Features
- "Close Poll" UI — no composable to trigger `closePoll()`
- Voters list endpoint (`GET /v1/polls/{id}/voters/{option_id}`)
- Loading/error/empty states for `PollBubble`
- Results visibility access control (backend)

### Bugs & Logic Errors
- Auto-close timer invalid input crashes — silently defaults to null
- Duplicate option text allowed — no deduplication warning
- `closes_in_hours` (backend) vs `closeInSeconds` (client) naming inconsistency

### Production Gaps
- Magic numbers (3600, 60, 604800) not extracted to named constants

---

## feature:backup

### Missing Features
- Media data backup (not stored locally, needs backend service)
- Selective restore (backend API is all-or-nothing)
- Server-side backup listing (`GET /v1/backup/`)
- Backup download progress (backend doesn't support callbacks)

### Bugs & Logic Errors
- `downloadBackup` discards downloaded bytes — no `getBinary` endpoint
- `pollExportStatus` infinite polls with no timeout/max retries
- `uploadProgress` shows stale state after chunk failure

### Security Flaws
- `AEADBadTagException` suppressed — returns false silently, user not notified of tampering
- No memory zeroing in `BackupArchive`

### Production Gaps
- No instrumented tests for BackupExporter, BackupArchive, chunk ordering, or export→import round-trip

---

## feature:calls (UI layer)

### Missing Features
- WebRTC video rendering not integrated
- CallLogScreen clustering logic exists but UI renders flat
- GroupCallScreen receives empty participants list
- CallLogScreen entry click navigates backward instead of to conversation
- Edit Name dialog for call links not implemented
- DTMF keypad not implemented
- Safety number dialog not wired
- Group call reactions not sent

### Bugs & Logic Errors
- CallLinkManager write ops return `Unit` and swallow exceptions

### Production Gaps
- ViewModels created inline in composable params (DI anti-pattern)
- `DatabasePool` singleton direct usage in `CallLogViewModel`
- `Long` vs `String` ID inconsistency

---

## feature:stickers

### Missing Features
- **Entire sticker module not wired** — ViewModel exists but no data repository
- 9 backend endpoints (featured, search, pack details, create, install/uninstall, library, recent, record usage) — none called

---

## feature:share

### Missing Features
- Share extension (system share sheet integration)
- Deep linking for share intents
- Share preview before sending

---

## Backend Analysis

### Missing Endpoints (Frontend Calls, Backend Missing)

| Endpoint | Frontend Usage |
|----------|---------------|
| `GET /v1/accounts/whoami` | core:auth — validate JWT on cold start |
| `POST /v1/accounts/username/reserve` | core:auth — username registration |
| `POST /v1/accounts/2fa/setup` | core:auth — registration lock |
| `PUT /v1/accounts/phone` | core:auth — phone change |
| `POST /v1/accounts/fcm` | core:auth — FCM token management |
| `POST /v1/accounts/device/link` | core:auth — multi-device linking |
| `GET /v1/polls/{id}/voters/{option_id}` | feature:polls — voters list |
| `GET /v1/backup/` | feature:backup — list backups |
| `GET /v1/backup/download/{id}` | feature:backup — download (getBinary missing) |
| `POST /v1/contacts/delta-sync` | feature:contacts — delta sync |
| `GET /v1/contacts/reverse-search` | feature:contacts — reverse lookup |
| `PUT /v1/groups/{id}/bans/{uid}` | feature:groups — ban user |
| `GET /v1/groups/{id}/bans` | feature:groups — list bans |
| `POST /v1/channels/{id}/rate-limit` | feature:channels — rate limit config |
| `GET /v1/time` | core:network — server time for JWT validation |
| `POST /v1/messages/dedupe-check` | core:push — duplicate detection |

### Stub Backend Implementations

- **IKS identity key fallback** — `MLSService::get_identity_key` generates random key when IKS unavailable. Production must reject MLS group creation.
- **Epoch secrets in DB** — server can read `epoch_secret` from `mls_groups` table. Production should use client-generated blind relay.
- **MRS delete stub** — expiry worker `delete_from_mrs` lambda always returns `true` without calling actual DELETE.
- **Client notification stub** — expiry worker `notify_clients` only logs, doesn't dispatch `DISAPPEAR_DELETE` via WebSocket.
- **Group admin check** — `set_timer` for GROUP allows any participant, doesn't verify admin role.
- **No Redis caching** — conversation timer settings uncached; should cache with 5-minute TTL.
- **Media proxy** — always proxies through media server instead of S3 pre-signed URL 302 redirect.

### Backend Test Failures

| Suite | Result | Issue |
|-------|--------|-------|
| `test_mls` | **2/36 (6%)** | IKS auth broken |
| `test_rate_limits` | **6/48 (12%)** | Rate limiting globally disabled |
| `test_mrs` | **TIMEOUT** | Service hang |
| `test_hardening` | **TIMEOUT** | Security test incomplete |
| `test_webrtc` | **7/12 (58%)** | 5 rate limit failures |

### Backend Security Gaps (Not Production-Ready)

**Transport:** TLS 1.2/1.0 not disabled, no HSTS header, no cert pinning hashes, no mTLS for internal services.

**Auth:** No JWKS caching (downstream fetches on every request), no `sodium_memcmp` for token comparison, IP PII in logs.

**DB:** No TLS on PostgreSQL, no least-privilege users, no `pg_audit`, backups not encrypted, no column-level encryption for PII.

**Infrastructure:** No load balancer, services on private subnet only, no OS hardening, containers as root, privileged containers possible, no read-only filesystem.

**Monitoring:** No SPK rotation logging, no bundle fetch logging, no Prometheus `/metrics`, no alerts.

**Crypto:** Dev Ed25519 keys in use, no KEK/TURN secrets in secrets manager, no production cert pinning hashes.

**Phase 2 (Messaging):** Sealed sender leaks metadata, `ws://` accepted, no message retention enforcement, no media encryption verification.

**Phase 3 (Social):** Contact discovery uses raw SHA-256 (broken), privacy settings not server-enforced.

**Phase 5 (MLS):** Key transparency not implemented, MLS group key management incomplete.

### Production Shortcuts (Must Fix Before Deploy)

1. Enable rate limiting (`RATE_LIMIT_DISABLED=1` is set)
2. Fix MRS and hardening test hangs
3. Fix MLS integration (IKS auth)
4. Generate production crypto keys
5. Enable TLS 1.3 only
6. Add HSTS header
7. Configure DB TLS
8. Run containers as non-root
9. Set up Prometheus `/metrics`
10. Implement sealed sender properly (no sender/metadata correlation)
11. Replace ephemeral Cloudflare tunnel
12. Fix TURN/UDP availability for WebRTC
13. Implement mTLS for internal endpoints
14. Add pg_audit extension
15. Implement backup encryption at rest

### E2E Messaging Pipeline Gaps

1. `sendMessage` is fire-and-forget — no delivery confirmation wired
2. Offline queue silently drops messages
3. No message deduplication service
4. Sealed sender leaks metadata (server can correlate sender)
5. No connection health monitoring
6. No WebSocket batch reading
7. No message priority queue
8. MRS test hangs — potential reliability issue in core delivery path
9. No Redis caching for device→WebSocket mapping (breaks horizontal scaling)
10. Fan-out limits not enforced (max 8 devices)
11. No backpressure when queue exceeds 10,000 (MRS should return 503)
12. `DISAPPEAR_DELETE` via WebSocket not actually dispatched

---

## Cross-Cutting Issues

### Architecture & DI
- `ApiClient.getInstance()` used directly in 5+ screens — no DI, no test overrides
- `DatabasePool` singleton in `CallLogViewModel` — couples to global state
- `CallsModule` singleton thread-safety issue (recently fixed)
- ViewModels created inline in Composables
- No DI framework for `JobManager`

### Cryptographic
- PIN hashing: SHA-256 only, no KDF
- OPK overwrite: `topUpOpks()` destroys unconsumed OPKs
- Identity key: in-memory only, lost on process death
- TURN credentials: plaintext in memory
- Media key: sent in plaintext
- Phone hash: raw SHA-256, no Argon2id
- Kyber: not integrated

### Network Security
- No TLS 1.3 enforcement
- No certificate pinning
- JWT in URL for WebSocket
- Fire-and-forget message send
- No request cancellation
- No offline queue error propagation
- No connection health monitoring

### Storage
- SQLCipher KDF iter = 1
- No message retention trimming
- Backup exclusion not configured
- No encrypted media storage
- No disk cache bounds

### Background Execution
- No WorkManager for periodic tasks
- No foreground service for WebSocket
- No boot receiver
- FCM sends payloads directly (not wake-up signal)
- No battery optimization handling

### Crash & Error Handling
- No StrictMode in debug
- No global crash handler
- No crash report scrubbing
- Silent failures throughout
- `AEADBadTagException` suppressed

### UI/UX
- No accessibility semantics
- No RTL support
- No reduced motion
- No progressive permissions
- No offline-first
- No loading/error/empty states in most screens
- God composables throughout

### Testing
- No UI tests for most screens
- Tests don't assert actual behavior
- No integration tests
- No E2E tests
- `UnconfinedTestDispatcher` timing issues

### Library & Dependency
- libsignal-client version pinning not verified
- 16KB page size compliance not verified
- LeakCanary not configured
- No static analysis in CI

---

## Priority Order for Fixes

### P0 — Breaks Functionality
1. OPK overwrite bug (KeyManager.kt)
2. sendReaction missing conversation_id
3. SQLCipher KDF iter = 1
4. Auth state not observed in navigation
5. loadSettings swallows all failures

### P1 — Security Critical
6. PIN hashing (SHA-256 → Argon2id)
7. Media key in plaintext
8. TURN credentials in memory plaintext
9. JWT in URL for WebSocket
10. No certificate pinning

### P2 — Production Blocking
11. No foreground service for WebSocket
12. No WorkManager for pre-key rotation
13. No message retention trimming
14. Backup exclusion not configured
15. Profile loadMyProfile wrong endpoint

### P3 — Signal Parity
16. Reply preview / message translation / contact sharing
17. Group disappearing messages
18. Channel post management (edit/delete/pin)
19. Status single fetch and my status
20. Polls close/voters UI

### P4 — Quality & Polish
21. DI framework setup (Hilt/Koin)
22. ViewModel extraction from god composables
23. Proper test coverage with assertions
24. Accessibility semantics everywhere
25. RTL + reduced motion support

---

## Batch 2 Findings (2026-05-31)

### core:network

**Enchant-Only:**
- `ApiClient` singleton with built-in rate limiting, retry logic, JWT refresh
- `AuthInterceptor` OkHttp interceptor with lock.wait() on 401
- `WebSocketManager` with custom envelope parsing
- `OfflineQueue` persistent queue for offline sending
- `RateLimitTracker`, `ConnectivityMonitor` StateFlow

**Missing (Signal):**
- `NetworkResult<T>` sealed class with `.then()`, `.map()`, `.withRetry()` chaining
- `SignalRestClient` with multi-host routing (Service/CDN/Storage), TLS cert pinning
- `WebSocketConnection` interface (OkHttp vs LibSignal native)
- `HealthMonitor` for WebSocket health
- Multi-host routing via `RequestSpec.Host`

**Key Gap:** Enchant uses flat `Result<T>` with exceptions; Signal uses `NetworkResult<T>` sealed class with rich chaining. Enchant lacks CDN/Storage routing.

---

### core:push

**Enchant-Only:**
- `BatteryOptimizationHelper` (Xiaomi, Huawei, OnePlus auto-start)
- `HuaweiPushFallback` HMS polling fallback
- `PushTokenRegistrar` FCM token management

**Missing (Signal):**
- `FcmJobService` JobScheduler fallback
- `FcmFetchBackgroundService` background companion
- Push challenge handling (`PushChallengeRequest`, `SubmitRateLimitPushChallengeJob`)
- `VerificationCodeRequestedPush` handling
- `WebSocketDrainer` integration

**Key Gap:** Signal uses job-based approach with `FcmRefreshJob`; Enchant uses callback-based. Signal has better fallback handling and WebSocket draining.

---

### core:base

**Enchant-Only:**
- `KeyStoreManager` Android Keystore wrapper
- `CoroutineDispatchers` custom dispatchers
- `AppConfig` centralized config
- `SecurePreferences` encrypted prefs wrapper
- `LRUCache`, `EnchantExecutors`
- `Result` discriminated union type
- Extensive `StringExtensions`, `ByteArrayExtensions`, `Scrubber`

**Missing (Signal):**
- `LoggingExtensions` additional helpers
- `JvmRxExtensions` RxJava interop
- `InputStreamExtensions` with `readVarInt32()`, `skipNBytesOrThrow()`
- `OptionalExtensions` (or(), isAbsent(), toOptional())
- `MacInputStream/MacOutputStream`, `Crc32OutputStream`
- `Base64Tools` with `stripPadding()`

**Key Gap:** Enchant's extensions are crypto-focused; Signal's are more I/O oriented. Enchant lacks Okio dependencies.

---

### core:navigation

**Enchant-Only:**
- `NavRoute.kt` sealed class hierarchy (40+ routes)
- `RouteEncoder.kt` URL percent-encoding
- `NavHost.kt` extension functions

**Missing (Signal):**
- `BottomSheetSceneStrategy.kt` Compose SceneStrategy
- `ResultEventBus.kt` typed result passing
- `ResultEffect.kt` composable for results
- `TransitionSpecs.kt` HorizontalSlide/VerticalSlide

**Key Gap:** Enchant uses custom sealed-class route model; Signal uses Navigation 3's `NavKey`/`NavEntry` with DSL builder. Signal's approach is more type-safe.

---

## Batch 3 Findings (2026-05-31)

### core:crypto

**Enchant-Only:**
- `EnchantCrypto.kt` JNI bridge to libenchantcrypto
- `CryptoPrimitives.kt` (X25519, Ed25519, XChaCha20-Poly1305, AES-GCM, HKDF, HMAC, SHA, Argon2id)
- `KeyManager.kt`, `PreKeyStore.kt`, `SessionManager.kt`, `IdentityStore.kt`
- `X3DH.kt`, `KdfChain.kt`, `DoubleRatchet.kt` (manual implementations)
- `SenderKeyManager.kt`, `SealedSender.kt`, `MediaCipher.kt`

**Missing (Signal):**
- libsignal integration (`SessionBuilder`, `SessionCipher`, `GroupCipher`, `SealedSessionCipher`)
- `SignalProtocolStore` unified interface
- `CertificateValidator` and sender certificates
- `ProfileKey`, `ProfileCipher` profile encryption
- `IncrementalMacInputStream` for chunked MAC
- Kyber post-quantum support
- `SignalSessionLock` reentrant mutex

**Key Gap:** Enchant re-implements X3DH/DoubleRatchet manually; Signal delegates to libsignal. Enchant lacks Kyber, certificate validation, and incremental MAC.

---

### core:config

**Enchant-Only:**
- Simple `RemoteConfig.kt` (65 lines)
- Semicolon-separated parsing (`split(";")`)
- `EnchantStore` persistence

**Missing (Signal):**
- `RemoteConfigRefreshJob` background fetch
- `RemoteConfigApi` network layer
- `RemoteConfigResult` with ETag
- `Config<T>` delegate class with `hotSwappable`, `sticky`, `active`, `onChangeListener`
- `OnFlagChange` callback interface
- ~100 concrete config definitions
- `libsignalConfigs` bridge

**Key Gap:** Enchant is a minimal stub (65 lines); Signal is production-grade (1437 lines) with JSON parsing, typed delegates, change listeners, hot-swappable flags.

---

### core:crash

**Enchant-Only:**
- `ScrubbedCrashlyticsTree` Timber tree with PII scrubbing
- `CrashHandler` with FTS corruption detection

**Missing (Signal):**
- `CrashConfig` remote-config driven crash prompting
- `SignalUncaughtExceptionHandler` with SQLite exception handling
- `UncaughtExceptionHandlerManager` multi-handler
- `CrashTable` database for crash storage with `anyMatch()`, `markAsPrompted()`
- Percentage-based crash rollout via BucketingUtil

**Key Gap:** Signal has remote-config driven crash prompting; Enchant uses Firebase Crashlytics directly. Signal has better FTS corruption handling.

---

### core:jobmanager

**Enchant-Only:**
- Full Room database integration (`JobDatabase`, `JobDao`, `FastJobStorage`)
- `CompositeScheduler`, `InAppScheduler`, `JobSchedulerScheduler`, `AlarmManagerScheduler`
- `KeyedSerialExecutor`, constraint observers

**Missing (Signal):**
- `JobManagerExtensions` coroutine-friendly `runJobBlocking()`
- `CoroutineJob` base class
- `MinimalJobSpec` lightweight spec for in-memory tracking
- `ChangeNumberConstraintObserver`, `RestoreAttachmentConstraintObserver`
- `PushProcessMessageJobMigration`, `DonationReceiptRedemptionJobMigration`

**Key Gap:** Signal uses composition (`FullSpec` wraps `JobSpec`); Enchant uses flattening. Signal has `MinimalJobSpec` for efficient in-memory tracking. Enchant's constraints are mostly stubs.

---

## Batch 4 Findings (2026-05-31)

### feature:chat

**Enchant-Only:**
- `ChatNavKey`, `ChatNavDisplay` Navigation3 integration
- `MessageSendPipeline` centralized sending
- `MessageProtobufHelper` proto serialization
- `ChatPagingSource` custom paging
- `ContentPreProcessor`, `IncomingMessageProcessor`

**Missing (Signal):**
- `ConversationFragment` (2000+ lines)
- `ConversationAdapterV2`, `ConversationItem`
- `QuoteModel`, `DraftViewModel`, `ScheduledMessagesRepository`
- `PinnedMessagesRepository`, `EditMessageHistoryRepository`
- `InlineQueryResultsControllerV2` emoji/GIF search
- `MentionsPickerFragmentV2`, `VoiceMessageRecordingDelegate`
- `CreatePollFragment`, `MessageRequestViewModel`

**Key Gap:** Signal has full RecyclerView-based UI with 20+ adapter types; Enchant uses Compose. Signal has polls, drafts, scheduled messages, link previews, mentions.

---

### feature:chatlist

**Enchant-Only:**
- `ChatListNavDisplay`, `ChatListNavKey` Navigation3
- `ConversationListScreen` with FilterChipsRow
- Unit tests (ViewModel, NavKey, NavBackStack)

**Missing (Signal):**
- `ConversationListFragment` with paging
- `ConversationFilterBehavior` pull-to-filter
- `ChatFolderAdapter`, folder management
- `ConversationListSearchAdapter` with ThreadModel, MessageModel
- Pull-to-filter state machine

**Key Gap:** Signal has pull-to-filter mechanism, folder management, paging with Paging3. Enchant is simple Flow-based.

---

### feature:calls

**Enchant-Only:**
- `CallsNavKey`, `CallsNavDisplay` Navigation3
- `SafetyNumberHelper`, `SafetyNumberDialog`
- `CallLinkManager` API client wrapper
- Compose-based call screens

**Missing (Signal):**
- `CallLogFragment`, `CallLogAdapter` RecyclerView
- `CallLinks` central state management
- `CreateCallLinkRepository`, `UpdateCallLinkRepository`
- `CallQualityBottomSheetFragment`, `CallQualityDiagnosticsFragment`
- `CallLinkDetailsActivity` with admin controls
- Paging3 integration

**Key Gap:** Signal has production-grade paging, caching, offline support, admin approval flows, revocation dialogs. Enchant is simplified Compose-first.

---

### feature:auth

**Enchant-Only:**
- `AuthNavKey`, `AuthNavDisplay` Navigation3
- `AuthViewModel` centralized auth state
- `KeyGenerationScreen`, `UsernamePickerScreen`, `TwoStepPinScreen`
- Unit tests

**Missing (Signal):**
- Event-driven state machine (`RegistrationFlowEvent`, `RegistrationFlowState`)
- Separate ViewModels per screen
- `TwoPaneRegistrationScaffold` for tablets
- Full rate limiting, CAPTCHA, account locked flows
- Backup restore flows

**Key Gap:** Signal uses event-driven state machine with separate ViewModels; Enchant consolidates into `AuthViewModel`. Signal has extensive tablet support.

---

## Batch 5 Findings (2026-05-31)

### feature:registration

**Enchant-Only:**
- `AccountEntropyPoolSerializer`
- `RegistrationTypes.kt` stub implementations
- `RestoreLocalBackupNavDisplay`, `QuickTransferOldDeviceNavigation`

**Missing (Signal):**
- `RegistrationRepository`, `NetworkController`, `StorageController`
- Full ViewModels for every screen
- All screen implementations
- State persistence and session validation
- Parcelers for ACI, PNI, MasterKey, KyberPreKey

**Key Gap:** Enchant is ~90% stub code; Signal has complete production implementation. Enchant uses `Any` types instead of concrete repository/network types.

---

### feature:settings

**Enchant-Only:**
- `SettingsViewModel` centralized
- `SettingsUiState` unified state
- Standalone screens (BlockedUsers, Backup, Security)

**Missing (Signal):**
- Notification profiles (20+ files)
- Subscriptions/donations (28+ files)
- Chat folders (12+ files)
- Payment lock, change number, app icon selection
- Remote backups, local backups
- Stories settings (25+ files)

**Key Gap:** Signal has 100+ settings screens; Enchant has ~12. Signal uses per-feature state with LiveData; Enchant uses single unified StateFlow.

---

### feature:contacts

**Enchant-Only:**
- `ContactSyncService` with Argon2id phone hashing
- `ContactsRepository`, `ContactsViewModel`
- `ContactListScreen`, `ContactProfileScreen`, `AddContactScreen`
- `FriendRequestsScreen`

**Missing (Signal):**
- `SystemContactsRepository` low-level access
- `ContactDiscovery`, `ContactDiscoveryRefreshV2`
- `ContactSearchViewModel` with paged data
- Letter header decorations
- Group stories support

**Key Gap:** Signal has paged contact loading with safety number checking; Enchant loads all at once. Signal has full system contacts integration.

---

### feature:groups

**Enchant-Only:**
- `GroupsViewModel` monolithic
- `GroupStateProcessor`, `GroupEditor`
- `GroupsRepository` REST-based
- Full Compose screens

**Missing (Signal):**
- `GroupId` sealed class with V1/V2/MMS types
- `GroupManager`, `GroupManagerV2` with zkGroup cryptography
- `GroupsV2StateProcessor` with P2P changes, server paging
- Member labeling system (12 files)
- Story group reply system (8 files)

**Key Gap:** Signal has sophisticated GV2 protocol with zkGroup cryptography, conflict resolution, change log history. Enchant uses simple REST.

---

## Consolidated Gaps Summary

| Module | Critical Issues | High Priority | Medium Priority |
|--------|----------------|---------------|-----------------|
| core:network | NetworkResult, multi-host routing | TLS cert pinning | Prometheus metrics |
| core:push | FcmJobService fallback | WebSocketDrainer | Push challenge handling |
| core:base | Okio dependencies | InputStreamExtensions | OptionalExtensions |
| core:navigation | NavKey/NavEntry DSL | ResultEventBus | BottomSheetSceneStrategy |
| core:crypto | libsignal delegation, Kyber | CertificateValidator | IncrementalMac |
| core:config | Full implementation | Config delegate | Change listeners |
| core:crash | Remote-config prompting | Multi-handler | CrashTable |
| core:jobmanager | MinimalJobSpec | CoroutineJob | Migrations |
| feature:chat | Polls, drafts, scheduled | Link previews | Mentions |
| feature:chatlist | Pull-to-filter | Folder management | Paging3 |
| feature:calls | Paging, caching | Admin controls | Quality diagnostics |
| feature:auth | Event-driven state | Tablet support | CAPTCHA/rate limits |
| feature:registration | Full implementation | ViewModels | Screen implementations |
| feature:settings | 100+ settings | Notification profiles | Chat folders |
| feature:contacts | Paged loading | System contacts | Safety numbers |
| feature:groups | GV2 protocol | zkGroup crypto | Member labeling |
