# Sunday Audit Progress — Enchant Native

> **Date:** 2026-05-17
> **Auditor:** Comprehensive codebase review
> **Target:** Production-grade E2EE messenger for 50,000+ concurrent users
> **Comparison bases:** Signal Android (LEADING_APPS_REFERENCE_MAP.md), WhatsApp feature set

---

## Table of Contents

1. [Overall Assessment](#1-overall-assessment)
2. [File-by-File Audit: What Works & What Doesn't](#2-file-by-file-audit)
3. [Every Stub Function & Empty Implementation](#3-every-stub-function)
4. [Screen-by-Screen Inventory](#4-screen-inventory)
5. [Signal Android Comparison](#5-signal-android-comparison)
6. [WhatsApp Feature Comparison](#6-whatsapp-comparison)
7. [Security Audit Results](#7-security-audit)
8. [50K User Scalability Assessment](#8-scalability-assessment)
9. [Fix Plan: Phased Execution](#9-fix-plan)
10. [File Modification Register](#10-file-modification-register)

---

## 1. Overall Assessment

| Metric | Value |
|--------|-------|
| Total .kt files | 167 source + 20 test = 187 |
| Proto files | 16 |
| Build modules | 34 |
| Working tests | ~244 |
| Required tests | ~1,015 |
| **Real completion** | **~40-50%** |
| Can ship to prod today? | **NO** |
| Estimated work to ship | **3-4 months (focused team)** |

### Critical Blockers (Ship-stopping)

| # | Blocker | Why It Stops Ship |
|---|---------|-------------------|
| 1 | **No E2EE message pipeline** | Messages send as plaintext over REST. Protobuf Content envelope never built. |
| 2 | **Sessions are in-memory only** | All crypto state lost on app restart. Messages undecryptable after kill. |
| 3 | **NavHost has 35+ empty routes** | 90% of navigation targets render blank screens. |
| 4 | **No key upload to IKS** | Other users cannot fetch this device's keys → cannot establish sessions. |
| 5 | **No release signing config** | `assembleRelease` fails immediately. |
| 6 | **0 tests for chat pipeline** | No regression protection for the core feature. |

---

## 2. File-by-File Audit

### Module: `:app`

#### `app/src/main/java/org/enchant/EnchantApp.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `onCreate()` | 🟡 | `initDi()` and `initLeakCanary()` are **empty bodies** |
| `initDi()` | 🔴 STUB | Empty — DI never initialized |
| `initCrashReporting()` | 🟡 | `CrashReporter.init()` called with no arg, but `CrashReporter.init()` expects `Context` |
| `initPerformance()` | ✅ | `ImagePipeline.init(this)` works |
| `initLeakCanary()` | 🔴 STUB | Empty body |
| `initStrictMode()` | ✅ | Properly configured for debug |
| `initNotificationChannels()` | ✅ | `NotificationChannels.createAll(this)` works |

#### `app/src/main/java/org/enchant/MainActivity.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `onCreate()` | 🟡 | `enableEdgeToEdge()` + `FLAG_SECURE` set — but FLAG_SECURE never removed on `onPause` / re-applied on `onResume` per security spec |
| `handleCallIntent()` | 🟡 | Stub — has a comment but no actual handling |
| `AppNavigation()` | 🔴 | NavHost routes have **empty composable bodies** for many destinations; uses string routes instead of sealed `NavRoute` class |

#### `app/src/main/java/org/enchant/DI.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `init()` | 🟡 | Initializes but missing many required dependencies: SignalStore, JobManager, SessionStore, PreKeyStore |
| `reset()` | 🟡 | Doesn't zero crypto material in memory |
| All accessors | ✅ | Null-safe with `checkNotNull` |

#### `app/src/main/java/org/enchant/BootReceiver.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `onReceive()` | ✅ | Starts WebSocketService on boot |

---

### Module: `:core:base`

#### `AppConfig.kt`
| Field | Status | Issue |
|-------|--------|-------|
| `gatewayUrl` | 🟡 | Reads from SharedPreferences — no `BuildConfig` integration |
| `wsUrl` | ✅ | Derived from gatewayUrl |
| `turnUrl/turnUsername/turnPassword` | 🟡 | Read from SharedPreferences — no fallback to BuildConfig |
| `jwtPublicKey` | 🟡 | Null until fetched — no auto-fetch in init |
| All getters | ✅ | Proper `checkInitialized` guard |

#### `SecurePreferences.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `init()` | ✅ | Creates `EncryptedSharedPreferences` via `MasterKey` |
| `putString/getString` | ✅ | Works |
| `putLong/getLong` | ✅ | Works |
| `putBoolean/getBoolean` | ✅ | Works |
| `putInt/getInt` | ✅ | Additional — not in spec but useful |
| `remove()` | ✅ | Works |
| `clearAll()` | 🟡 | Does NOT zero in-memory copies (`sodium_memzero`) |
| `contains()` | ✅ | Works |

#### `KeyStoreManager.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `init()` | 🟡 | Tests StrongBox by generating+deleting a test key — this may fail on devices without StrongBox |
| `generateKey()` | ✅ | Supports EC + AES with StrongBox fallback |
| `keyExists()` | ✅ | Works |
| `deleteKey()` | ✅ | Works |
| `sign()` | ✅ | SHA256withECDSA |
| `verify()` | ✅ | Works |
| `encrypt()` | ✅ | AES/GCM/NoPadding with IV prepended |
| `decrypt()` | ✅ | Works — but no GCM tag length validation |
| `getWrappedKeyBytes()` | 🔴 STUB | Always returns null |
| `getOrCreateDatabaseKey()` | 🔴 | Stores DB key as **comma-separated int string** — extremely fragile serialization |
| `isHardwareBacked()` | ✅ | Returns `_isHardwareBacked` |

#### `CoroutineDispatchers.kt`
| Field | Status | Issue |
|-------|--------|-------|
| All dispatchers | ✅ | Properly defined |

---

### Module: `:core:network`

#### `ApiClient.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `init()` | 🟡 | No certificate pinning, no connection spec restriction (TLS 1.3) |
| `get()` | 🟡 | Recursive retry on 429 can cause **infinite loop** |
| `post()` | 🟡 | Same as get |
| `put()` | 🟡 | Same |
| `del()` | 🟡 | Same |
| `postRaw()` | ✅ | 128MB size check |
| `getBinary()` | ✅ | Works |
| `uploadFile()` | 🟡 | Just delegates to postRaw, no progress tracking |
| `buildRequest()` | 🟡 | **No JWT header added** — relies entirely on AuthInterceptor but `AuthInterceptor` is a singleton object with its own `OkHttpClient` instance that ALSO doesn't auto-inject |
| `request()` | 🔴 | Recursive retry without depth limit on 429/5xx; never calls `AuthInterceptor.intercept()` because it uses the interceptor chain |

#### `AuthInterceptor.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `intercept()` | 🔴 | Uses **separate `OkHttpClient`** for refresh (not the shared one). Race condition: two concurrent 401s both try to refresh. Refresh request sends refresh token as **URL-unescaped JSON string interpolation** — JSON injection risk. |
| `refreshToken()` | 🟡 | Works but fragile — constructs JSON manually with string interpolation |

#### `WebSocketManager.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `init()` | ✅ | Creates scope |
| `connect()` | 🟡 | Auth JWT comes from SecurePreferences — no refresh-on-401 logic in WS auth path |
| `disconnect()` | ✅ | Works |
| `sendMessage()` | 🟡 | Falls back to REST on WS disconnect (correct) but **does NOT encapsulate payload in proper Envelope protobuf for outgoing messages over WS**. Sends protobuf envelope but recipient device ID is never set. |
| `sendTypingStart/Stop()` | 🟡 | Dummy — sends empty payload |
| `sendDeliveryReceipt()` | 🔴 | Sends empty ephemeral envelope — no actual receipt data |
| `sendReadReceipt()` | 🔴 | Same issue |
| `sendCallOffer/Answer/Ice/End()` | 🟡 | Works but minimal |
| `requestRESTFallback()` | 🟡 | Uses JSON with **recipientUserId** (camelCase) but backend expects **recipient_user_id** (snake_case) — will fail |
| `authenticate()` | ✅ | Correct protobuf auth frame |
| `handleFrame()` | 🟡 | Parses incoming PUT messages into `IncomingEnvelope` but: no 200 ACK sent back to server after receiving push message (spec requires it) |
| `startKeepAlive()` | ✅ | 30s interval |
| `scheduleReconnect()` | ✅ | Exponential backoff with jitter |
| `sendEphemeral()` | 🟡 | Doesn't set proper ephemeral protobuf fields |
| `sendCallSignal()` | 🟡 | Doesn't wrap in proper CallMessage protobuf |

#### `WebSocketService.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `onCreate()` | ✅ | Channel created |
| `onStartCommand()` | ✅ | `START_STICKY` |
| `connect()` | ✅ | Foreground service + WS init |
| `onDestroy()` | ✅ | Disconnect cleanup |
| `disconnect()` | 🟡 | Try/catch around stopForeground only |
| Notification | 🟡 | Uses generic `android.R.drawable.ic_dialog_info` icon — not app-specific |

#### `OfflineQueue.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `enqueue()` | 🟡 | **In-memory only** — lost on process death |
| `drain()` | 🟡 | Sends via REST (may miss WS connection) |
| `remove()` | ✅ | Works |
| `clearAll()` | ✅ | Works |

#### `ConnectivityMonitor.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `init()` | ✅ | Proper `NetworkCallback` registration |
| All flows | ✅ | Correct observation |

#### `RateLimitTracker.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `recordCall()` | 🟡 | In-memory only, lost on restart |
| `canCall()` | ✅ | Works |
| `getRemaining()` | ✅ | Works |
| `updateFromHeaders()` | ✅ | Parses all rate-limit headers |
| `waitIfNeeded()` | ✅ | Proper coroutine delay |

#### `models/ApiModels.kt`
| Class | Status | Issue |
|-------|--------|-------|
| All 28 data classes | ✅ | Proper `@Serializable` with `@SerialName` |

---

### Module: `:core:database`

#### `AppDatabase.kt`
| Component | Status | Issue |
|-----------|--------|-------|
| `DatabasePool` | 🟡 | 1 writer + thread-local readers — correct architecture but `writer` is `synchronized` while spec requires `ReentrantReadWriteLock` |
| `DatabaseMigrator` | 🟡 | Class exists but **never instantiated** — no migration runs |
| `createTables()` | ✅ | Creates all 14 tables + indexes + reactions table |
| SQLCipher PRAGMAs | 🟡 | Missing: `PRAGMA cipher_memory_security = ON` is set in preKey hook but NOT in the actual `onConfigure()` |
| Reactions table | ✅ | Extra table beyond spec's 14 |

#### `dao/MessageDao.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `insert()` | ✅ | Uses compileStatement |
| `insertBatch()` | ✅ | Single transaction |
| `getById()` | ✅ | Parameterized |
| `getByEnvelopeId()` | ✅ | Works |
| `getConversationMessages()` | 🟡 | `callbackFlow` — **single emit, not reactive** |
| `updateStatus()` | ✅ | Works |
| `markDeleted()` | ✅ | Works |
| `starMessage()` | ✅ | Works |
| `getUnreadCount()` | ✅ | Works |
| `searchMessages()` | 🟡 | Uses `LIKE` — no FTS5, performance will degrade |
| `deleteExpired()` | ✅ | Works |
| `deleteConversation()` | ✅ | Cascading |

#### `dao/ConversationDao.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `upsert()` | ✅ | `INSERT OR REPLACE` |
| `getAll()` | 🟡 | Same single-emit Flow issue |
| `getById()` | ✅ | Works |
| `setArchived()` | ✅ | Works |
| `setPinned()` | ✅ | Works |
| `setMuted()` | ✅ | Works |
| `incrementUnread()` | ✅ | Works |
| `getUnreadCount()` | ✅ | Aggregate |
| `search()` | 🟡 | `LIKE` on last_message, not conversation name |

#### `dao/SessionDao.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `store()` | ✅ | INSERT OR REPLACE |
| `load()` | ✅ | Returns ByteArray |
| `delete()` | ✅ | Works |
| `hasSession()` | ✅ | LIMIT 1 |
| `deleteAllForUser()` | ✅ | Works |

#### `dao/IdentityDao.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `save()` | ✅ | Works |
| `getByAddress()` | ✅ | Works |
| `setVerified()` | ✅ | Works |
| `delete()` | ✅ | Works |

#### `dao/RecipientDao.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `upsert()` | ✅ | Works |
| `upsertAll()` | ✅ | Batch transaction |
| `getByUserId()` | ✅ | Works |
| `getByUsername()` | ✅ | Works |
| `getAll()` | 🟡 | Single-emit Flow |
| `getBlocked()` | ✅ | Works |
| `search()` | 🟡 | `LIKE` on display_name + username |

#### `entity/Entities.kt`
| Entity | Status | Issue |
|--------|--------|-------|
| All 11 data classes | ✅ | Complete with all fields matching DDL |

#### `util/CursorMapper.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `mapTo()` | 🟡 | Uses reflection (`primaryConstructor`) — works but slow for large result sets |
| `mapToList()` | 🟡 | Same reflection overhead |
| `mapCurrentRow()` | 🟡 | CamelCase→snake_case conversion via regex on every column |

---

### Module: `:core:crypto`

#### `CryptoHelper.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `generateX25519KeyPair()` | 🟡 | Uses Java `KeyPairGenerator("X25519")` — may not exist on all API levels. Spec says to use **libsodium JNI**. |
| `generateEd25519KeyPair()` | 🟡 | Same issue — uses Java crypto, not libsodium. Byte extraction logic fragile. |
| `ed25519SkToX25519()` | 🔴 | Uses Java crypto with raw ASN.1 wrapping — very fragile. Should use libsodium `crypto_sign_ed25519_sk_to_curve25519()` |
| `ed25519PkToX25519()` | 🔴 | Same issue. |
| `x25519DiffieHellman()` | 🟡 | Uses Java KeyAgreement — works but not libsodium |
| `hkdfSha256()` | ✅ | Correct HKDF implementation |
| `encryptAesGcm()` | 🔴 | **AES-GCM instead of XChaCha20-Poly1305** (spec requires XChaCha20) |
| `decryptAesGcm()` | 🔴 | Same. Also no GCM tag validation on failure paths. |
| `generateRandomKey()` | ✅ | Uses `SecureRandom` |
| `signEd25519()` | 🟡 | Uses Java Signature, not libsodium |
| `verifyEd25519()` | 🟡 | Same |
| `sha256()` | ✅ | Works |
| `sha384()` | ✅ | Works (extra, not in spec) |
| `sha512()` | ✅ | Works (extra) |
| `constantTimeEquals()` | ✅ | `MessageDigest.isEqual` |
| `zeroBytes()` | 🟡 | `data.fill(0)` — not libsodium `sodium_memzero`. GC may have moved the data by now. |
| `base64UrlEncode()` | ✅ | Works |
| `base64UrlDecode()` | ✅ | Works |
| `wrapEd25519Public/Private()` | 🔴 | Fragile ASN.1 prefix construction. Any format change breaks everything. |

#### `X3DH.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `aliceInitiate()` | 🟡 | Computes DH1-DH4 correctly BUT: `bobRespond` identity keys conversion uses IK_A for both sides — **actual bug**. Line 73: `CryptoHelper.ed25519SkToX25519(ourIdentityKey.privateKey)` converts Bob's IK but line 76 uses `ourSignedPrekeyKeyPair.privateKey` with `theirIdentityKeyPublic` — this is CORRECT for DH1. But there is NO verification that both sides derive the same SK. |
| `bobRespond()` | 🔴 | Returns `X3dhHeader` with **empty identityKey and ephemeralKey** `ByteArray(0)` — broken header serialization |
| `operator plus()` | ✅ | Byte array concatenation |

#### `DoubleRatchet.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `initializeAsAlice()` | ✅ | Correct initial ratchet |
| `initializeAsBob()` | ✅ | Correct |
| `encrypt()` | 🟡 | Encryption itself works BUT: uses `AES/GCM/NoPadding` instead of `XChaCha20-Poly1305` |
| `decrypt()` | 🔴 | Returns `ByteArray(0)` on **any** failure — silent decryption failures. No replay protection. Skipped message keys accumulation without eviction. |
| `serializeState()` | 🟡 | Encodes version/keys/chain state but **does NOT serialize skipped message keys** — those will be lost on restart |
| `deserializeState()` | 🟡 | Doesn't deserialize skipped keys either |
| `parseHeader()` | 🟡 | Minimal validation on header size |
| `RatchetState.zero()` | ✅ | Zeros all key material |

#### `KeyManager.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `init()` | 🟡 | Loads IK from **plain SharedPreferences** (base64url strings) — not KeyStore-wrapped |
| `generateAndUploadKeys()` | 🔴 | Generates IK locally but **NEVER uploads to IKS server** |
| `getIdentityKeyPair()` | ✅ | Returns in-memory pair |
| `getIdentityPublicKeyBase64()` | ✅ | Encodes public key |
| `hasKeys()` | ✅ | Returns boolean |
| `topUpOpks()` | 🔴 STUB | **Empty body** |
| `rotateSignedPreKey()` | 🔴 STUB | **Returns success without doing anything** |
| `fetchKeyBundle()` | 🔴 STUB | **Missing entirely** |
| `cleanSignedPreKeys()` | 🔴 STUB | **Missing entirely** |
| `signWithIdentity()` | 🔴 STUB | **Missing entirely** |

#### `SessionManager.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `init()` | 🔴 STUB | **Never loads sessions from database** |
| `encryptMessage()` | 🟡 | If no session exists, generates fake SPK and runs X3DH — but **never stores the session**. Creates session with `recipientUserId:0` as key. |
| `decryptMessage()` | 🔴 | Creates a `RatchetMessage` with **hardcoded 44-byte zeroed header** instead of parsing the actual protocol header from the incoming envelope |
| `hasSession()` | ✅ | In-memory check |
| `deleteSession()` | ✅ | Zeros state |
| `archiveSession()` | ✅ | Zeros state (same as delete) |
| `getSafetyNumber()` | 🟡 | Uses SHA-512 of concatenated identity keys — correct but no proper formatting |
| `getIdentityKey()` | ✅ | In-memory map |
| `setIdentityKey()` | ✅ | In-memory map |

#### `SodiumProvider.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `init()` | 🟡 | Tries to load `libsodium.so` via `System.loadLibrary("sodium")` — but **no libsodium native library exists in the project**. Will silently fail. |
| `sodiumMemZero()` | 🟡 | Falls back to `CryptoHelper.zeroBytes()` |
| `sodiumMlock/Munlock` | 🔴 STUB | Empty bodies |

#### `PreKeyWorker.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `doWork()` | 🟡 | Calls `topUpOpks()` and `rotateSignedPreKey()` — both stubs |
| `schedule()` | ✅ | WorkManager scheduled for 30-day periodic run |

---

### Module: `:core:auth`

#### `AuthStateMachine.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `applyEvent()` | ✅ | Pure reducer — handles all 13 state × 19 event combinations |
| `transition()` | ✅ | Delegates to applyEvent |
| `getRequiredPermissions()` | ✅ | API-level-aware permission list |
| `validateRestoredState()` | 🔴 STUB | Returns `RegistrationState.Complete` if any JWT exists — **never validates token has expired** or tries refresh |

#### `AuthRepository.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `requestOtp()` | ✅ | Works |
| `verifyOtp()` | ✅ | Works with device info |
| `refreshToken()` | 🟡 | No rate limit handling |
| `logout()` | 🟡 | Returns success even on network failure — correct fire-and-forget pattern but doesn't clear local state |
| `listDevices()` | ✅ | Works |
| `revokeDevice()` | ✅ | Works |
| `deleteAccount()` | 🟡 | Returns success even on failure |
| `fetchJwks()` | ✅ | Works |
| `registerKeys()` | ✅ | Builds correct JSON |
| `rotateSignedPreKey()` | ✅ | Works |
| `uploadOpks()` | ✅ | Works |
| `getOpkCount()` | ✅ | Works |

#### `AuthManager.kt`
| Function | Status | Issue |
|----------|--------|-------|
| `init()` | 🟡 | Creates AuthRepository + calls `validateRestoredState()` stub |
| `requestOtp()` | ✅ | Proper state transitions |
| `verifyOtp()` | ✅ | Stores JWT, transitions to Permissions |
| `resendOtp()` | 🟡 | No 30s cooldown |
| `refreshToken()` | 🟡 | Calls `refreshToken()` but doesn't check JWT expiry time from `exp` claim |
| `logout()` | ✅ | Clears auth keys |
| `deleteAccount()` | ✅ | Logout + API |
| `registerKeys()` | 🟡 | Generates keys but **never uploads** to IKS |
| `updateProfile()` | ✅ | Local validation + API call |
| `searchUsername()` | 🟡 | Returns `List<String>` (usernames only) instead of `List<User>` |

---

### Module: `:core:push`

#### `FcmReceiveService.kt` — NOT FOUND (referenced in manifest but .kt file not located)

#### `FcmFetchManager.kt` — NOT FOUND

#### `FcmFetchForegroundService.kt` — NOT FOUND

#### `PushTokenRegistrar.kt` — NOT FOUND

#### `HuaweiPushFallback.kt` — NOT FOUND

**All 5 push module files are MISSING** — manifest references them but source files don't exist.

---

### Module: `:core:calls`

| File | Status | Issue |
|------|--------|-------|
| `CallManager.kt` | 🟡 | 496 lines, functional but `process(CallAction)` pattern missing |
| `WebRtcService.kt` | 🟡 | 211 lines, basic PC management |
| `AudioRouter.kt` | 🟡 | 168 lines, audio management |
| `CallForegroundService.kt` | NOT FOUND in codebase (manifest references it) |
| `CallNotificationReceiver.kt` | NOT FOUND in codebase (manifest references it) |

---

### Module: `:feature:auth`

| Screen | Status | Issue |
|--------|--------|-------|
| `AuthViewModel.kt` | 🟡 | Delegates to AuthManager — minimal |
| `WelcomeScreen.kt` | ✅ | Renders but language picker not implemented |
| `PhoneEntryScreen.kt` | ✅ | Renders |
| `CountryCodePickerScreen.kt` | ✅ | Searchable |
| `OtpVerifyScreen.kt` | ✅ | 6-digit fields, timer |
| `PermissionsScreen.kt` | ✅ | Progressive permission cards |
| `ProfileSetupScreen.kt` | ✅ | Avatar, name, about |
| `UsernamePickerScreen.kt` | ✅ | Availability check |
| `KeyGenerationScreen.kt` | ✅ | Animated progress |
| `TwoStepPinScreen.kt` | ✅ | Numpad |
| `RestorePromptScreen.kt` | ✅ | Snapshot |
| `AppLockScreen.kt` | 🟡 | No biometric integration |
| **Tests** | **🔴 0 screen tests** | |

---

### Module: `:feature:chat`

| File | Status | Issue |
|------|--------|-------|
| `MessageSendPipeline.kt` | 🔴 | Uses REST instead of WS protobuf. Sends plaintext prefixed messages. No proper Content protobuf. |
| `IncomingMessageProcessor.kt` | 🔴 | Parses plaintext prefixes instead of protobuf Content. Insecure. |
| `ConversationRepository.kt` | 🟡 | Single-emit Flows, not reactive |
| `ConversationViewModel.kt` | 🟡 | 384 lines, many stubs |
| `ConversationScreen.kt` | ✅ | Renders chat UI |
| `ChatPagingSource.kt` | ✅ | Correct cursor pagination |
| `MediaService.kt` | 🟡 | Basic media picker + encrypt |
| `ContentPreProcessor.kt` | 🟡 | Basic link preview |
| `MessageBubble.kt` | ✅ | 7 bubble types |
| `MessageDataFetcher.kt` | 🟡 | Basic parallel fetch |
| `MediaViewerScreen.kt` | ✅ | Zoom, swipe |
| `EmojiPicker.kt` | ✅ | Categories, search |
| `MessageContextMenu.kt` | ✅ | 9 actions |
| `ChatColorsDrawable.kt` | ✅ | Extra |
| `V2ConversationItemShape.kt` | ✅ | Bubble morph |
| **Tests** | **🔴 0 data layer tests** | |

---

### Module: `:feature:chat-list`

| File | Status | Issue |
|------|--------|-------|
| `ConversationListViewModel.kt` | 🟡 | Filtering works, pin/archive/mute basic |
| `ConversationListScreen.kt` | ✅ | Renders with filter chips, swipe, FAB |

---

### Module: `:feature:calls`

| Screen | Status | Issue |
|--------|--------|-------|
| `CallViewModel.kt` | 🟡 | 10 tests pass |
| `CallLogViewModel.kt` | 🟡 | 15 tests pass |
| `IncomingCallScreen.kt` | ✅ | Full-screen takeover |
| `OutgoingCallScreen.kt` | ✅ | Bouncing dots |
| `ActiveVoiceCallScreen.kt` | ✅ | Timer + controls |
| `ActiveVideoCallScreen.kt` | ✅ | Remote video + PiP |
| `GroupCallScreen.kt` | 🟡 | Grid renders, controls stubbed |
| `CallLogScreen.kt` | ✅ | History list |
| `SafetyNumberDialog.kt` | ✅ | Dialog |
| `CallLinkManager.kt` | 🟡 | 11 tests pass |
| `CallLinkScreen.kt` | ✅ | Renders |

---

### Module: `:feature:groups`

| File | Status | Issue |
|------|--------|-------|
| `GroupsViewModel.kt` | 🟡 | 24 tests pass |
| `GroupsRepository.kt` | 🟡 | Basic CRUD |
| `GroupListScreen.kt` | ✅ | Renders |
| `CreateGroupScreen.kt` | ✅ | Name + member picker |
| `GroupInfoScreen.kt` | ✅ | Info + settings |
| `GroupMemberListScreen.kt` | ✅ | Roles |
| `GroupInviteScreen.kt` | ✅ | Link + QR |
| `JoinRequestsScreen.kt` | ✅ | Approve/reject |
| `GroupEditor.kt` | 🔴 | **Missing 12/18 functions** — see Section 3 |
| `GroupStateProcessor.kt` | 🟡 | No conflict resolution |

---

### Module: `:feature:contacts`

| File | Status | Issue |
|------|--------|-------|
| `ContactsViewModel.kt` | 🟡 | 19 tests pass |
| `ContactsRepository.kt` | 🟡 | Basic |
| `ContactSyncService.kt` | 🟡 | Phone hash + match |
| `ContactListScreen.kt` | ✅ | Renders |
| `AddContactScreen.kt` | ✅ | Search + add |
| `ContactProfileScreen.kt` | ✅ | Profile |
| `FriendRequestsScreen.kt` | ✅ | In/out requests |

---

### Module: `:feature:status`

| File | Status | Issue |
|------|--------|-------|
| `StatusViewModel.kt` | 🟡 | Basic CRUD |
| `StatusFeedScreen.kt` | ✅ | My status + feed |
| `StatusCreateScreen.kt` | ✅ | Text + color + media |
| `StatusViewerScreen.kt` | ✅ | Full-screen viewer |

---

### Module: `:feature:channels`

| File | Status | Issue |
|------|--------|-------|
| `ChannelViewModel.kt` | 🟡 | Basic operations |
| `ChannelFeedScreen.kt` | ✅ | Feed with pagination |
| `ChannelSearchScreen.kt` | ✅ | Search |

---

### Module: `:feature:profile`

| File | Status | Issue |
|------|--------|-------|
| `ProfileViewModel.kt` | 🟡 | Profile CRUD |
| `ProfileScreen.kt` | ✅ | Renders |

---

### Module: `:feature:settings`

| Screen | Status | Issue |
|--------|--------|-------|
| `SettingsViewModel.kt` | 🟡 | Incomplete — missing `updatePrivacy()`, `loadDevices()` implementations |
| `SettingsHomeScreen.kt` | ✅ | Main menu |
| `AccountSettingsScreen.kt` | ✅ | |
| `SecuritySettingsScreen.kt` | ✅ | |
| `PrivacySettingsScreen.kt` | ✅ | |
| `NotificationsSettingsScreen.kt` | ✅ | |
| `AppearanceSettingsScreen.kt` | ✅ | |
| `ChatsSettingsScreen.kt` | ✅ | |
| `StorageSettingsScreen.kt` | ✅ | |
| `AboutScreen.kt` | 🔴 MISSING | Not found |
| `BlockedUsersScreen.kt` | 🔴 MISSING | Not found |
| `BackupSettingsScreen.kt` | 🔴 MISSING | Not found |

---

### Module: `:feature:backup`

| File | Status | Issue |
|------|--------|-------|
| `BackupViewModel.kt` | 🟡 | Initiate + upload + finalize — **`restoreBackup()` MISSING** |
| `BackupExporter.kt` | 🟡 | Full export/import orchestrator |
| `BackupArchive.kt` | 🟡 | Encrypt/decrypt sections |
| `ChatArchiveExporter.kt` | 🟡 | Works |
| `ContactArchiveExporter.kt` | 🟡 | Works |
| `GroupArchiveExporter.kt` | 🟡 | Works |
| `AdHocCallArchiveExporter.kt` | 🟡 | Works |

---

### Module: `:feature:polls`

| File | Status | Issue |
|------|--------|-------|
| `PollViewModel.kt` | 🟡 | Create + vote + close |
| `PollBubble.kt` | 🔴 | **No actual poll rendering** — doesn't show vote counts, percentages, or closed state |
| `PollCreateSheet.kt` | ✅ | Bottom sheet |

---

### Module: `:feature:stickers`

| File | Status | Issue |
|------|--------|-------|
| `StickerViewModel.kt` | 🟡 | Browse + install + send |
| `StickerPicker.kt` | ✅ | Bottom sheet |
| `StickerStoreScreen.kt` | ✅ | Featured + search |

---

### Module: `:core:navigation`

| File | Status | Issue |
|------|--------|-------|
| `NavRoute.kt` | ✅ | 40+ sealed route classes |
| `NavHost.kt` | 🔴 | All 35+ composable routes have **EMPTY BODIES** |

---

### Module: `:core:performance`

| File | Status | Issue |
|------|--------|-------|
| `MessageCache.kt` | 🟡 | LRU — requires lambda not in spec |
| `ImagePipeline.kt` | ✅ | Coil with mem + disk cache |
| `MessageTrimmer.kt` | ✅ | WorkManager trimmer |

---

### Module: `:core:accessibility`

| File | Status | Issue |
|------|--------|-------|
| `AccessibilityDelegate.kt` | ✅ | Content descriptions for messages, avatars, buttons, reactions |
| `RtlSupport.kt` | 🔴 | Returns `Int` instead of Compose `Modifier` extension — **unusable** |

---

### Module: `:core:crash`

| File | Status | Issue |
|------|--------|-------|
| `CrashReporter.kt` | 🔴 | **No Crashlytics dependency**. Missing: `setUserId()`, `logEvent()`, `logError()`, `logDecryptionFailure()`. Email regex scrubbing missing. Uses `Log.d()` only. |

---

### Module: `:core:signalstore` — NOT FOUND (directory exists? Let me check)

**Checked glob results — 0 files found. SignalStore module may be empty or missing entirely.**

### Module: `:core:jobmanager` — NOT FOUND (same)

### Module: `:core:model`

`DomainModels.kt` exists (1 file). But it should be split into: `User.kt`, `CallLog.kt`, `Contact.kt`, `Message.kt`, `Conversation.kt` per spec.

---

## 3. Every Stub Function & Empty Implementation

### Stub Functions (Fully Empty Body)

| # | File | Function | Expected Behavior |
|---|------|----------|-------------------|
| 1 | `EnchantApp.kt:23` | `initDi()` | Initialize all DI dependencies |
| 2 | `EnchantApp.kt:33` | `initLeakCanary()` | Initialize LeakCanary in debug |
| 3 | `KeyStoreManager.kt:149` | `getWrappedKeyBytes()` | Export KeyStore-wrapped key bytes |
| 4 | `KeyManager.kt:58` | `topUpOpks()` | Check OPK count, generate+upload if < 10 |
| 5 | `KeyManager.kt:60` | `rotateSignedPreKey()` | Generate new SPK, sign with IK, upload to IKS |
| 6 | `SodiumProvider.kt:24` | `sodiumMlock()` | Lock memory to prevent swapping |
| 7 | `SodiumProvider.kt:27` | `sodiumMunlock()` | Unlock memory |
| 8 | `AuthStateMachine.kt:144` | `validateRestoredState()` | Actually validate JWT expiry, try refresh |
| 9 | `CallManager.kt:xxx` | `raiseHand()` | Group call: raise hand |
| 10 | `CallManager.kt:xxx` | `react()` | Group call: send reaction |
| 11 | `CallManager.kt:xxx` | `requestRemoteMute()` | Group call: mute participant (admin) |
| 12 | `CallManager.kt:xxx` | `removeParticipant()` | Group call: remove participant (admin) |
| 13 | `ActiveCallManager.kt:xxx` | `stopCallScreen()` | Close call activity |
| 14 | `BackupViewModel.kt:xxx` | `restoreBackup()` | Parse and restore backup data to DB |
| 15 | `MainActivity.kt:72` | `handleCallIntent()` | Handle deep link call intents |

### Missing Functions (Not Implemented At All)

| # | File | Missing Functions |
|---|------|-------------------|
| 1 | `KeyManager.kt` | `fetchKeyBundle()`, `cleanSignedPreKeys()`, `signWithIdentity()` |
| 2 | `GroupEditor.kt` | `updateGroupTimer()`, `updateAttributesRights()`, `updateMembershipRights()`, `setAnnouncementGroup()`, `revokeInvites()`, `banUser()`, `unbanUser()`, `ejectMember()`, `terminateGroup()`, `acceptInvite()`, `cycleGroupLinkPassword()`, `setJoinByGroupLinkState()`, `commitChangeWithConflictResolution()` |
| 3 | `GroupStateProcessor.kt` | `handleP2PChange()` |
| 4 | `CrashReporter.kt` | `setUserId()`, `logEvent()`, `logError()`, `logDecryptionFailure()` |
| 5 | `SettingsViewModel.kt` | `updatePrivacy()` (implemented but doesn't call API), `loadDevices()` |
| 6 | `AuthManager.kt` | Search returns `List<String>` not `List<User>` |

### Missing Files Needed for Build

| # | Missing File | Impact |
|---|-------------|--------|
| 1 | `core/push/FcmReceiveService.kt` | Manifest references it — crash on FCM message |
| 2 | `core/push/FcmFetchManager.kt` | Background fetch logic |
| 3 | `core/push/FcmFetchForegroundService.kt` | Background fetch service |
| 4 | `core/push/PushTokenRegistrar.kt` | FCM token lifecycle |
| 5 | `core/push/HuaweiPushFallback.kt` | Huawei polling fallback |
| 6 | `feature/settings/AboutScreen.kt` | Settings menu has dead entry |
| 7 | `feature/settings/BlockedUsersScreen.kt` | Settings menu has dead entry |
| 8 | `feature/settings/BackupSettingsScreen.kt` | Settings menu has dead entry |
| 9 | All 23 SignalStore Values classes | Entire SignalStore layer |
| 10 | 20 JobManager files | Entire JobManager layer |

---

## 4. Screen-by-Screen Inventory

### Auth Screens (11 total)

| Screen | File | Status | Working? | Notes |
|--------|------|--------|----------|-------|
| Welcome | `WelcomeScreen.kt` | ✅ | Yes | Language picker not wired |
| Phone Entry | `PhoneEntryScreen.kt` | ✅ | Yes | Auto-format works |
| Country Picker | `CountryCodePickerScreen.kt` | ✅ | Yes | Searchable |
| OTP Verify | `OtpVerifyScreen.kt` | ✅ | Yes | 6 digits, timer, SMS auto-fill missing |
| Permissions | `PermissionsScreen.kt` | ✅ | Yes | Progressive |
| Profile Setup | `ProfileSetupScreen.kt` | ✅ | Yes | Avatar picker, name, about |
| Username Picker | `UsernamePickerScreen.kt` | ✅ | Yes | Availability check |
| Key Generation | `KeyGenerationScreen.kt` | ✅ | Visual only | Doesn't actually upload to IKS |
| 2-Step PIN | `TwoStepPinScreen.kt` | ✅ | Yes | Numpad |
| Restore Prompt | `RestorePromptScreen.kt` | ✅ | Yes | Snapshot |
| App Lock | `AppLockScreen.kt` | 🟡 | Partial | No biometric integration |

### Main Chat Screens (6 total)

| Screen | File | Status | Working? | Notes |
|--------|------|--------|----------|-------|
| Conversation List | `ConversationListScreen.kt` | ✅ | Yes | Filter chips, swipe, FAB |
| Conversation | `ConversationScreen.kt` | ✅ | Yes | Message bubbles, composer |
| Media Viewer | `MediaViewerScreen.kt` | ✅ | Yes | Zoom, swipe, share |
| Emoji Picker | `EmojiPicker.kt` | ✅ | Yes | Categories, search |
| Context Menu | `MessageContextMenu.kt` | ✅ | Yes | 9 actions |
| Search | Inline in ViewModel | 🟡 | Partial | Debounced, FTS5 missing |

### Call Screens (6 total)

| Screen | File | Status | Working? | Notes |
|--------|------|--------|----------|-------|
| Incoming Call | `IncomingCallScreen.kt` | ✅ | Yes | Full-screen takeover |
| Outgoing Call | `OutgoingCallScreen.kt` | ✅ | Yes | Bouncing dots |
| Active Voice | `ActiveVoiceCallScreen.kt` | ✅ | Yes | Timer, mute, speaker |
| Active Video | `ActiveVideoCallScreen.kt` | ✅ | Yes | PiP, controls |
| Group Call | `GroupCallScreen.kt` | 🟡 | Partial | Grid renders, admin controls stubbed |
| Call Log | `CallLogScreen.kt` | ✅ | Yes | History, missed indicators |
| Safety Number | `SafetyNumberDialog.kt` | ✅ | Yes | Dialog |
| Call Link | `CallLinkScreen.kt` | ✅ | Yes | Join, share, admin |

### Social Screens (17 total)

| Screen | File | Status | Working? | Notes |
|--------|------|--------|----------|-------|
| Group List | `GroupListScreen.kt` | ✅ | Yes | |
| Create Group | `CreateGroupScreen.kt` | ✅ | Yes | Name + member picker |
| Group Info | `GroupInfoScreen.kt` | ✅ | Yes | |
| Group Members | `GroupMemberListScreen.kt` | ✅ | Yes | |
| Group Invite | `GroupInviteScreen.kt` | ✅ | Yes | Link + QR |
| Join Requests | `JoinRequestsScreen.kt` | ✅ | Yes | |
| Contact List | `ContactListScreen.kt` | ✅ | Yes | |
| Add Contact | `AddContactScreen.kt` | ✅ | Yes | Search + add |
| Contact Profile | `ContactProfileScreen.kt` | ✅ | Yes | |
| Friend Requests | `FriendRequestsScreen.kt` | ✅ | Yes | |
| Status Feed | `StatusFeedScreen.kt` | ✅ | Yes | |
| Status Create | `StatusCreateScreen.kt` | ✅ | Yes | Text + color + media |
| Status Viewer | `StatusViewerScreen.kt` | ✅ | Yes | Full-screen |
| Channel Feed | `ChannelFeedScreen.kt` | ✅ | Yes | |
| Channel Search | `ChannelSearchScreen.kt` | ✅ | Yes | |
| Profile | `ProfileScreen.kt` | ✅ | Yes | |
| Sticker Store | `StickerStoreScreen.kt` | ✅ | Yes | |

### Settings Screens (8 of 11 exist)

| Screen | File | Status | Working? | Notes |
|--------|------|--------|----------|-------|
| Settings Home | `SettingsHomeScreen.kt` | ✅ | Yes | |
| Account | `AccountSettingsScreen.kt` | ✅ | Yes | |
| Security | `SecuritySettingsScreen.kt` | ✅ | Yes | |
| Privacy | `PrivacySettingsScreen.kt` | ✅ | Yes | |
| Notifications | `NotificationsSettingsScreen.kt` | ✅ | Yes | |
| Appearance | `AppearanceSettingsScreen.kt` | ✅ | Yes | |
| Chats | `ChatsSettingsScreen.kt` | ✅ | Yes | |
| Storage | `StorageSettingsScreen.kt` | ✅ | Yes | |
| About | **MISSING** | 🔴 | N/A | Dead menu entry |
| Blocked Users | **MISSING** | 🔴 | N/A | Dead menu entry |
| Backup Settings | **MISSING** | 🔴 | N/A | Dead menu entry |

### Extended Screens (5 total)

| Screen | File | Status | Working? | Notes |
|--------|------|--------|----------|-------|
| Poll Create Sheet | `PollCreateSheet.kt` | ✅ | Yes | |
| Poll Bubble | `PollBubble.kt` | 🔴 | No | Doesn't render votes |
| Sticker Picker | `StickerPicker.kt` | ✅ | Yes | |
| Location Picker | `LocationPickerScreen.kt` | ✅ | Yes | |
| Share Target | `ShareTargetActivity.kt` | ✅ | Yes | |

---

## 5. Signal Android Comparison

| Feature | Signal Android | Enchant (Current) | Gap |
|---------|---------------|-------------------|-----|
| **Session Store** | Full DB persistence via `TextSecureSessionStore` + `ReentrantSessionLock` | In-memory only, lost on restart | 🔴 CRITICAL |
| **Identity Store** | LRU cache (1000) + DB + non-blocking approval + key-change detection | In-memory map, no LRU, no approval | 🔴 CRITICAL |
| **Pre-Key Store** | Full batch gen (100), 30-day SPK rotation, 90-day OPK cleanup | Stub implementations | 🔴 CRITICAL |
| **Buffered Stores** | In-memory buffer during decrypt, batch flush to DB | Not implemented | 🔴 |
| **Message Pipeline** | Protobuf Content envelope (30 field DataMessage) | Plaintext string prefixes | 🔴 CRITICAL |
| **WebSocket** | Proper protobuf binary frames with ACK | Partial — no ACK sent on server push | 🟡 |
| **Sealed Sender** | Profile-key-derived access keys, certificate validation | Not implemented | 🔴 |
| **Multi-Device** | Full StorageService + Manifest protocol | Not implemented | 🔴 |
| **Database** | SQLCipher with triggers + ContentObservers | SQLCipher without reactive triggers | 🟡 |
| **Notifications** | `MessageNotifier` with inline reply, grouping, channels | Basic — no proper grouping | 🟡 |
| **Group V2** | Full encrypted group state, CRDT conflict resolution | Basic CRUD, no conflict resolution | 🟡 |
| **Call State Machine** | `SignalCallManager` with per-state action processors | Direct method calls, no routing | 🟡 |
| **CRDT Sync** | LWW-element-Set for contacts/groups | Not implemented | 🔴 |
| **PQXDH** | Kyber-1024 post-quantum key agreement | Not implemented | 🔴 |
| **Safety Numbers** | Full verification workflow with biometric unlock | Basic SHA-512 hash only | 🟡 |

### Signal's Key Architectural Patterns Missing in Enchant

1. **`ReentrantSessionLock`** — Thread-safe session access. Enchant: no locking.
2. **Buffered Protocol Stores** — Batch DB writes during decrypt. Enchant: no batching.
3. **`MessageContentProcessor`** — 15+ message type dispatch. Enchant: 6 hardcoded prefix checks.
4. **`DatabaseObserver`** — Trigger-based reactive updates. Enchant: single-emit `callbackFlow`.
5. **StorageService** — Manifest-based multi-device sync. Enchant: not implemented.
6. **PreKeyUtil** — Batch generation, signed rotation, cleanup. Enchant: all stubs.
7. **`SealedSenderAccess`** — Three-tier access (group send token, unidentified access, unrestricted). Enchant: not implemented.
8. **`FcmFetchManager`** — Foreground/background fetch strategy on FCM. Enchant: not implemented.

---

## 6. WhatsApp Feature Comparison

| Feature | WhatsApp | Enchant (Current) | Gap |
|---------|----------|-------------------|-----|
| **E2EE by default** | ✅ Signal Protocol (same as Enchant's goal) | 🔴 Not functional — no session persistence | 🔴 |
| **Group Chat** | ✅ Full groups with admin controls | 🟡 Half the GroupEditor functions missing | 🟡 |
| **Voice/Video Calls** | ✅ 8-person group calls | 🟡 1:1 works, group stubbed | 🟡 |
| **Status/Stories** | ✅ 24h ephemeral | ✅ Working | ✅ |
| **Media Sharing** | ✅ End-to-end encrypted | 🔴 Keys sent in plaintext over wire | 🔴 |
| **Disappearing Messages** | ✅ 24h/7d/90d | ✅ Timer support exists | ✅ |
| **Message Reactions** | ✅ Emoji reactions | ✅ Reaction API support | ✅ |
| **Message Editing** | ✅ 15-min window, limited edits | 🟡 Max 2 edits supported | ✅ |
| **Backup** | ✅ E2EE backup (Google Drive) | 🟡 Upload only, no restore | 🟡 |
| **Multi-device** | ✅ 4 linked devices | 🔴 Not implemented | 🔴 |
| **Business API** | ✅ WhatsApp Business | Not planned | N/A |
| **Channels** | ✅ Broadcast channels | ✅ Working | ✅ |
| **Polls** | ✅ In-chat polls | 🔴 PollBubble doesn't render correctly | 🔴 |
| **Location Sharing** | ✅ Real-time + static | 🟡 Static only | 🟡 |
| **Contact Sharing** | ✅ vCard | 🟡 Basic support | 🟡 |
| **Screen Lock** | ✅ Biometric + PIN | 🟡 PIN exists, biometric not integrated | 🟡 |
| **Message Search** | ✅ Full-text search | 🟡 No FTS5 index | 🟡 |
| **Link Previews** | ✅ Automatic | 🟡 Basic support | 🟡 |
| **Stickers** | ✅ Store + custom | ✅ Working | ✅ |
| **Call Links** | ✅ Shareable meeting links | 🟡 Basic support | 🟡 |
| **GDPR Export** | ✅ Data download | 🟡 API exists, UI missing | 🟡 |
| **Rate Limits** | ✅ Client + server enforcement | 🟡 Client RateLimitTracker works | 🟡 |

---

## 7. Security Audit Results

### Critical (Immediate Fix Required)

| # | Issue | File | CVE Analogy |
|---|-------|------|-------------|
| 1 | **Message content sent as plaintext string prefixes** | `MessageSendPipeline.kt:96-100`, `IncomingMessageProcessor.kt:155-175` | Attacker with DB access sees "DELIVERY:uuid123" patterns |
| 2 | **Identity keys stored in plain SharedPreferences** | `KeyManager.kt:36-39` | Root access → key extraction |
| 3 | **Media encryption key sent in the clear inside message** | `MessageSendPipeline.kt:175-180` | `"$mediaId:${base64(mediaKey)}"` is plaintext |
| 4 | **No session persistence** | `SessionManager.kt:24` | App restart = all crypto state lost |
| 5 | **`X3DH.bobRespond()` returns empty header bytes** | `X3DH.kt:103-104` | Broken session establishment |
| 6 | **Double ratchet decrypt silences all errors** | `DoubleRatchet.kt:224-226` | Replay attack not detectable |
| 7 | **Key upload never happens** | `KeyManager.kt:31-46` | Nobody can establish sessions with this device |
| 8 | **Infinite retry on 429/5xx** | `ApiClient.kt:104-121` | Can be used to bypass rate limits |

### High (Fix Before Release)

| # | Issue | File |
|---|-------|------|
| 9 | AES-GCM instead of XChaCha20-Poly1305 | `CryptoHelper.kt:103-122` |
| 10 | No `sodium_memzero` in secure wipe | `SecurePreferences.kt:62`, `CryptoHelper.kt:162` |
| 11 | No certificate pinning | `res/xml/network_security_config.xml` |
| 12 | FLAG_SECURE never removed/re-applied correctly | `MainActivity.kt:54` |
| 13 | No Safety Number verification UI | Missing entirely |
| 14 | No Sealed Sender (anonymous message) support | Missing entirely |
| 15 | No KeyStore wrapping for DB key storage | `KeyStoreManager.kt:164-173` |
| 16 | No Session Lock for thread safety | `SessionManager.kt` |
| 17 | `AuthInterceptor` has its own OkHttpClient | `AuthInterceptor.kt:19` |
| 18 | DB key stored as comma-separated int string | `KeyStoreManager.kt:168` |

### Medium (Address in Phase 2)

| # | Issue | File |
|---|-------|------|
| 19 | Ed25519→X25519 uses ASN.1 byte hacking | `CryptoHelper.kt:50-69` |
| 20 | No FTS5 for message search | `MessageDao.kt:134-140` |
| 21 | Crash reporter has no Crashlytics dependency | `CrashReporter.kt` |
| 22 | No ProGuard stripping of `Log.d()` in release | `app/proguard-rules.pro` |
| 23 | No input validation on message content size | `MessageSendPipeline.kt:65` |
| 24 | `GlobalScope.launch` for delivery receipts | `MessageSendPipeline.kt:214,229` |
| 25 | WakeLock permission declared but unused | `AndroidManifest.xml:27` |

---

## 8. 50K User Scalability Assessment

### Performance Targets vs Reality

| Metric | Target | Current | Gap |
|--------|--------|---------|-----|
| DB: Per-conversation messages | 100K | Works (proper indexes) | ✅ |
| DB: Paginated query P95 | < 30ms | Index structure correct | 🟡 No benchmarks |
| DB: Batch insert (200) | < 50ms | Single transaction | ✅ |
| Memory: Visible list | < 50MB | Paging 3 not implemented | 🔴 |
| Memory: LRU caches | < 10MB | Not implemented | 🔴 |
| Network: WS message P95 | < 100ms | Uses REST fallback too often | 🔴 |
| Network: Reconnect max | < 30s | Exponential backoff works | ✅ |
| Pipeline: Msg/sec | 500 | Not tested | 🔴 |

### Scaling Blockers for 50K Users

1. **No Paging 3 / `PagingDataAdapter`** — conversation list loads ALL messages at once
2. **No FTS5** — message search is `LIKE %query%` (full table scan)
3. **No connection pooling** for API client — single OkHttpClient, no keep-alive tuning
4. **No database connection pool sizing** — WAL mode enabled but thread-local readers may cause issues at scale
5. **No monitoring** — no performance metrics, no ANR tracking, no crash-free session rate
6. **WebSocketManager is singleton** — single point of failure, no reconnection backpressure
7. **No multi-device sync** — each 50K user with 2 devices = 100K syncs, not handled

---

## 9. Fix Plan: Phased Execution

### Phase 1 — Make It Build & Run (Week 1-2)

| # | Task | Files to Modify | Effort |
|---|------|-----------------|--------|
| 1 | Fix release signing config | `app/build.gradle.kts`, create `keystore.properties` | 30min |
| 2 | Fix Coil ProGuard rules (coil3→coil) | `app/proguard-rules.pro:17` | 5min |
| 3 | Add `consumerProguardFiles` to all 33 modules | All 33 `build.gradle.kts` | 1hr |
| 4 | Add `useJUnitPlatform()` to all module build files | 29 `build.gradle.kts` | 1hr |
| 5 | Fix certificate pinning config | `res/xml/network_security_config.xml` | 30min |
| 6 | Fix `protobuf-java` vs `protobuf-javalite` conflict | `gradle/libs.versions.toml` | 15min |
| 7 | Add `-Xcontext-receivers` and `-opt-in` to all builds | 33 `build.gradle.kts` | 30min |
| 8 | Add `kotlin.serialization` plugin to app module | `app/build.gradle.kts` | 5min |
| 9 | Fix CI pipeline with correct lint/tasks | `.github/workflows/ci.yml` | 30min |
| 10 | Populate NavHost composable routes | `NavHost.kt` | 4hr |
| 11 | Fix `EnchantApp.initDi()` and `initLeakCanary()` | `EnchantApp.kt` | 1hr |
| 12 | Create missing push module files (5 files) | `core/push/Fcm*` | 4hr |

### Phase 2 — Security Foundation (Week 3-4)

| # | Task | Files to Modify | Effort |
|---|------|-----------------|--------|
| 13 | Switch CryptoHelper from AES-GCM to XChaCha20-Poly1305 via libsodium | `CryptoHelper.kt` | 3hr |
| 14 | Integrate libsodium JNI properly (add dependency, fix SodiumProvider) | `SodiumProvider.kt`, `build.gradle.kts` | 2hr |
| 15 | Wrap KeyManager keys with Android KeyStore encryption | `KeyManager.kt` | 2hr |
| 16 | Fix DB key storage (stop using comma-separated int strings) | `KeyStoreManager.kt:164-173` | 30min |
| 17 | Add `sodium_memzero` to SecurePreferences.clearAll() | `SecurePreferences.kt:62` | 15min |
| 18 | Fix `ed25519SkToX25519` and `ed25519PkToX25519` to use libsodium | `CryptoHelper.kt:50-69` | 2hr |
| 19 | Add proper FLAG_SECURE lifecycle management | `MainActivity.kt:54` | 30min |
| 20 | Fix certificate pinning with real hashes | `res/xml/network_security_config.xml` | 1hr |
| 21 | Add ProGuard release logging strip | `app/proguard-rules.pro` | 15min |

### Phase 3 — Core E2EE Pipeline (Week 5-8)

| # | Task | Files to Modify | Effort |
|---|------|-----------------|--------|
| 22 | Implement session persistence (load/store from DB) | `SessionManager.kt`, `SessionDao.kt` | 4hr |
| 23 | Build proper protobuf Content message serialization | `MessageSendPipeline.kt`, `IncomingMessageProcessor.kt` | 6hr |
| 24 | Fix X3DH.bobRespond() to return proper header | `X3DH.kt:103-104` | 1hr |
| 25 | Fix DoubleRatchet decrypt with proper error handling + replay protection | `DoubleRatchet.kt:152-235` | 4hr |
| 26 | Implement key upload to IKS | `KeyManager.kt:generateAndUploadKeys()` | 2hr |
| 27 | Implement OPK top-up + SPK rotation | `KeyManager.kt:topUpOpks()`, `rotateSignedPreKey()` | 2hr |
| 28 | Fix ApiClient recursion on retry (add max depth) | `ApiClient.kt:104-137` | 1hr |
| 29 | Fix WebSocket ACK on server push | `WebSocketManager.kt:271` | 1hr |
| 30 | Implement proper delivery/read receipt protobuf messages | `WebSocketManager.kt`, `MessageSendPipeline.kt` | 2hr |
| 31 | Encrypt media keys properly with session cipher | `MessageSendPipeline.kt:175-180` | 1hr |

### Phase 4 — Missing Features (Week 9-12)

| # | Task | Files | Effort |
|---|------|-------|--------|
| 32 | Build SignalStore module (23 Values classes) | `core:signalstore` (23 files) | 6hr |
| 33 | Build JobManager module (20 files) | `core:jobmanager` (20 files) | 8hr |
| 34 | Implement GroupEditor missing 12 functions | `GroupEditor.kt` | 4hr |
| 35 | Build GroupStateProcessor conflict resolution | `GroupStateProcessor.kt` | 2hr |
| 36 | Create missing settings screens (3 files) | `feature/settings/AboutScreen.kt`, `BlockedUsersScreen.kt`, `BackupSettingsScreen.kt` | 3hr |
| 37 | Fix PollBubble to render votes | `PollBubble.kt` | 1hr |
| 38 | Implement backup restore | `BackupViewModel.kt` | 2hr |
| 39 | Build CrashReporter properly with Crashlytics | `CrashReporter.kt` | 2hr |
| 40 | Fix RtlSupport.kt to return Modifier extension | `RtlSupport.kt` | 1hr |
| 41 | Fix NavHost string routes → use NavRoute sealed class | `MainActivity.kt`, `NavHost.kt` | 3hr |

### Phase 5 — Polish & Scale (Week 13-16)

| # | Task | Files | Effort |
|---|------|-------|--------|
| 42 | Add Paging 3 support for message list | `ChatPagingSource.kt`, `ConversationListScreen.kt` | 4hr |
| 43 | Add FTS5 for message search | `MessageDao.kt`, migration | 2hr |
| 44 | Optimize database connection pool sizing | `AppDatabase.kt` | 1hr |
| 45 | Add performance monitoring (ANR, latency) | `core:performance` | 3hr |
| 46 | Implement Safety Number verification UI | `SafetyNumberDialog.kt`, new verification screen | 2hr |
| 47 | Implement Sealed Sender | `MessageSendPipeline.kt`, new `SealedSender.kt` | 4hr |
| 48 | Add multi-device sync (StorageService protobuf) | `core:signalstore`, `StorageService.proto` | 8hr |
| 49 | Write remaining 771 tests | All modules | 40hr |
| 50 | Add Paging 3 for conversation list | `ConversationListViewModel.kt`, `ConversationListScreen.kt` | 3hr |

### Phase 6 — Testing & Release (Week 17-18)

| # | Task | Files | Effort |
|---|------|-------|--------|
| 51 | Write integration tests for full send/receive flow | `feature/chat` | 8hr |
| 52 | Add coverage threshold enforcement to CI | `.github/workflows/ci.yml` | 2hr |
| 53 | Run full security audit | All | 4hr |
| 54 | Run performance benchmarks | All | 4hr |
| 55 | Fix all remaining lint issues | All | 4hr |
| 56 | Beta test with 100 users | N/A | 1 week |
| 57 | Fix beta feedback | Various | 1 week |
| 58 | Release to Play Store | N/A | 1 day |

---

## 10. File Modification Register

### Files Requiring Modification (45 files)

```
app/build.gradle.kts
app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/res/xml/network_security_config.xml
app/src/main/java/org/enchant/EnchantApp.kt
app/src/main/java/org/enchant/MainActivity.kt
app/src/main/java/org/enchant/DI.kt
.gradle/libs.versions.toml
.github/workflows/ci.yml
settings.gradle.kts

core/base/src/main/java/org/enchant/core/base/SecurePreferences.kt
core/base/src/main/java/org/enchant/core/base/KeyStoreManager.kt
core/network/src/main/java/org/enchant/core/network/ApiClient.kt
core/network/src/main/java/org/enchant/core/network/AuthInterceptor.kt
core/network/src/main/java/org/enchant/core/network/WebSocketManager.kt
core/network/src/main/java/org/enchant/core/network/OfflineQueue.kt
core/network/src/main/java/org/enchant/core/network/models/ApiModels.kt
core/database/src/main/java/org/enchant/core/database/AppDatabase.kt
core/database/src/main/java/org/enchant/core/database/util/CursorMapper.kt
core/database/src/main/java/org/enchant/core/database/dao/MessageDao.kt
core/crypto/src/main/java/org/enchant/core/crypto/CryptoHelper.kt
core/crypto/src/main/java/org/enchant/core/crypto/X3DH.kt
core/crypto/src/main/java/org/enchant/core/crypto/DoubleRatchet.kt
core/crypto/src/main/java/org/enchant/core/crypto/KeyManager.kt
core/crypto/src/main/java/org/enchant/core/crypto/SessionManager.kt
core/crypto/src/main/java/org/enchant/core/crypto/SodiumProvider.kt
core/auth/src/main/java/org/enchant/core/auth/AuthStateMachine.kt
core/auth/src/main/java/org/enchant/core/auth/AuthManager.kt
core/navigation/src/main/java/org/enchant/navigation/NavHost.kt
core/accessibility/src/main/java/org/enchant/core/accessibility/RtlSupport.kt
core/crash/src/main/java/org/enchant/core/crash/CrashReporter.kt

feature/chat/src/main/java/org/enchant/chat/data/MessageSendPipeline.kt
feature/chat/src/main/java/org/enchant/chat/data/IncomingMessageProcessor.kt
feature/chat/src/main/java/org/enchant/chat/data/ConversationRepository.kt
feature/groups/src/main/java/org/enchant/groups/GroupEditor.kt
feature/groups/src/main/java/org/enchant/groups/GroupStateProcessor.kt
feature/polls/src/main/java/org/enchant/polls/PollBubble.kt
feature/backup/src/main/java/org/enchant/backup/BackupViewModel.kt
feature/settings/src/main/java/org/enchant/settings/SettingsViewModel.kt
feature/calls/src/main/java/org/enchant/calls/CallViewModel.kt
feature/calls/src/main/java/org/enchant/calls/calllinks/CallLinkManager.kt
```

### Files Requiring Creation (58+ files)

```
core/push/FcmReceiveService.kt
core/push/FcmFetchManager.kt
core/push/FcmFetchForegroundService.kt
core/push/PushTokenRegistrar.kt
core/push/HuaweiPushFallback.kt

core/signalstore/AccountValues.kt
core/signalstore/BackupValues.kt
core/signalstore/RegistrationValues.kt
core/signalstore/SettingsValues.kt
core/signalstore/PinValues.kt
core/signalstore/StorageServiceValues.kt
core/signalstore/StoryValues.kt
core/signalstore/WallpaperValues.kt
core/signalstore/LabsValues.kt
core/signalstore/PhoneNumberPrivacyValues.kt
core/signalstore/EmojiValues.kt
core/signalstore/ChatColorsValues.kt
core/signalstore/CallQualityValues.kt
core/signalstore/ProxyValues.kt
core/signalstore/RateLimitValues.kt
core/signalstore/OnboardingValues.kt
core/signalstore/InternalValues.kt

core/jobmanager/Job.kt
core/jobmanager/Constraint.kt
core/jobmanager/JobStorage.kt
core/jobmanager/Scheduler.kt
core/jobmanager/...
              (20 files total)

core/crypto/SessionLock.kt
core/crypto/SealedSender.kt
core/crypto/SafetyNumber.kt
core/crypto/MediaEncryption.kt

feature/settings/AboutScreen.kt
feature/settings/BlockedUsersScreen.kt
feature/settings/BackupSettingsScreen.kt

core/calls/CallForegroundService.kt
core/calls/CallNotificationReceiver.kt

All test files (~40+)
```

---

## Summary

**The codebase has excellent architectural ambition (Signal-inspired module structure, proper proto definitions, comprehensive build phases) but the execution gap is significant:**

1. **Core E2EE pipeline is non-functional** — the most critical path (encrypt → send → receive → decrypt) has fundamental bugs in X3DH, Double Ratchet, and session management
2. **Security posture has critical gaps** — keys stored in plaintext, AES-GCM instead of XChaCha20, no session persistence
3. **Feature completeness is ~50%** — many screens exist but NavHost renders nothing, GroupEditor is half-implemented, 3 settings screens missing, Push module entirely missing
4. **Test coverage is 24% of target** — crucial modules (chat, base, jobmanager, signalstore) have zero tests
5. **50K user readiness requires significant investment** — missing Paging 3, FTS5, monitoring, multi-device sync, and connection pooling optimization

The 6-phase plan above addresses all issues in priority order. Estimated total effort: **18 weeks** for a focused team of 2-3 engineers.
