# Enchant Native — Current Progress

> This file tracks all completed work, current status, and next steps.
> Update after every logical change.

---

## Project Status

| Metric | Value |
|--------|-------|
| **Phase** | Phase 7 — Polish & Ship (in progress: ~80% overall) |
| **Kotlin source files** | 177 (108 non-test + 69 new across Sprints 0-5) |
| **Kotlin test files** | 10 |
| **Proto files** | 16 (generated: 25 Java files) |
| **Build modules** | 33 (1 app + 17 core + 15 feature) |
| **Tests written** | 10 test files, 150 test cases (99 in Phase 4) |

---

## Phase 0 — Project Setup ✅

| Module | Files | Status |
|--------|-------|--------|
| Gradle config | `libs.versions.toml`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` | ✅ |
| Gradle wrapper | `gradlew`, `gradlew.bat`, `gradle-wrapper.jar` (8.11.1) | ✅ |
| Android manifest | 27 permissions, 9 services/activities/receivers | ✅ |
| Resources | themes, colors, strings, icons, 4 XML configs | ✅ |
| ProGuard | `proguard-rules.pro` covering all libs | ✅ |
| CI/CD | `.github/workflows/ci.yml` (lint → test → build) | ✅ |
| .gitignore | hardened with secrets, builds, generated, temp | ✅ |
| .env.example | Dev onboarding config | ✅ |
| Proto generation | Manual protoc exec task generating 25 Java lite files | ✅ |

---

## Phase 1 — Foundation ✅

### `:core:base` (4 files)
| File | Lines | Status |
|------|-------|--------|
| `AppConfig.kt` | 64 | Real — config singleton, URL derivation, applicationContext |
| `SecurePreferences.kt` | 66 | Real — EncryptedSharedPreferences wrapper, putInt/getInt |
| `KeyStoreManager.kt` | 180 | Real — Android KeyStore: EC/AES, sign/verify, StrongBox, getOrCreateDatabaseKey |
| `CoroutineDispatchers.kt` | 12 | Real — named dispatchers; crypto single-threaded |

### `:core:network` (8 files)
| File | Lines | Status |
|------|-------|--------|
| `ApiClient.kt` | 164 | Real — OkHttp, retry, JWT refresh, 128MB limit, GET/POST/PUT/DELETE/getBinary/postRaw |
| `AuthInterceptor.kt` | 83 | Real — Bearer token + concurrent-safe 401 refresh |
| `RateLimitTracker.kt` | 61 | Real — header parsing, Retry-After, endpoint-scoped |
| `WebSocketManager.kt` | 356 | Real — protobuf WS frames, exp. backoff, keepalive, auth timeout, REST fallback |
| `WebSocketService.kt` | 102 | Real — foreground service, auto-connect, notification channel |
| `ConnectivityMonitor.kt` | 68 | Real — NetworkCallback → StateFlow |
| `OfflineQueue.kt` | 65 | Real — ConcurrentLinkedQueue, drain, max 5 retries |
| `ApiModels.kt` | 187 | Real — 45 @Serializable data classes |

### `:core:database` (9 files)
| File | Lines | Status |
|------|-------|--------|
| `AppDatabase.kt` | 200 | Real — SQLCipher + WAL + pool (1 writer / thread-local readers) + DDL (15 tables) |
| `Entities.kt` | 148 | Real — 15 entity data classes |
| `CursorMapper.kt` | 62 | Real — reified generics auto-mapping Cursor → Entity |
| `MessageDao.kt` | 150 | Real — Full CRUD, paginated Flow, FTS search, expired deletion, batch insert |
| `ConversationDao.kt` | 72 | Real — CRUD + reactive list + archive/pin/mute + unread counts |
| `SessionDao.kt` | 30 | Real — Store/load/delete for Signal Protocol sessions |
| `IdentityDao.kt` | 36 | Real — Identity key CRUD with verified status |
| `RecipientDao.kt` | 80 | Real — Contact cache, username lookup, blocked list, inline batch upsert |

### `:core:crypto` (6 files)
| File | Lines | Status |
|------|-------|--------|
| `CryptoHelper.kt` | 208 | **Real** — X25519 DH, Ed25519 sign/verify, AES-256-GCM, HKDF-SHA256, SHA-256/512, CSPRNG, base64url, constant-time cmp |
| `X3DH.kt` | 116 | **Real** — Full X3DH: DH1+DH2+DH3+[DH4] → HKDF → SK, aliceInitiate + bobRespond |
| `DoubleRatchet.kt` | 308 | **Real** — Full ratchet: init, encrypt (ratchet step + AES-GCM), decrypt (ratchet + skipped key buffer 1000), serialize/deserialize |
| `SessionManager.kt` | 136 | **Real** — Orchestrates X3DH + DoubleRatchet, encryptMessage, decryptMessage, hasSession, getSafetyNumber |
| `KeyManager.kt` | 62 | **Real** — Ed25519 key generation, SecurePreferences persistence, generateAndUploadKeys |
| `SodiumProvider.kt` | 20 | Real — libsodium JNI loader with JDK fallback |

---

## Phase 2 — Auth & Onboarding ✅

### `:core:auth` (3 files)
| File | Lines | Status |
|------|-------|--------|
| `AuthStateMachine.kt` | 157 | Real — 13x13 state matrix, all transitions, validateRestoredState |
| `AuthRepository.kt` | 191 | Real — All 12 API calls: OTP, JWT, keys, profile |
| `AuthManager.kt` | 199 | Real — Full auth lifecycle: OTP flow, token mgmt, profile CRUD, logout |

### `:feature:auth` (11 screens + ViewModel)
| Screen | Status |
|--------|--------|
| WelcomeScreen, PhoneEntryScreen, CountryCodePickerScreen (130+ countries), OtpVerifyScreen, PermissionsScreen, ProfileSetupScreen, UsernamePickerScreen, KeyGenerationScreen, TwoStepPinScreen, RestorePromptScreen, AppLockScreen | ✅ All real |

### `:core:push` (5 files)
| File | Status |
|------|--------|
| FcmReceiveService, FcmFetchManager, FcmFetchForegroundService, PushTokenRegistrar, HuaweiPushFallback | ✅ All real |

### `:core:navigation` (1 file)
| File | Status |
|------|--------|
| `NavRoute.kt` | ✅ 40+ sealed route classes |

---

## Phase 3 — Core Chat ✅

### Chat Data Layer (6 files)
| File | Lines | Status |
|------|-------|--------|
| `ConversationRepository.kt` | ~180 | Real — CRUD, cursor pagination, reactive Flow, reactions, archive/pin/mute, search, expired deletion |
| `MessageSendPipeline.kt` | ~260 | Real — encrypt→REST send→offline queue→track status. Text, media, reactions, receipts, typing, edits, deletes, forward |
| `IncomingMessageProcessor.kt` | ~160 | Real — decrypt→dispatch. Pre-key (X3DH establish), signal messages, key bundle fetch, blocked senders, delivery receipts |
| `MediaService.kt` | ~200 | Real — pick image/video/doc, voice record, JPEG compress, AES-GCM encrypt+upload, download+decrypt, gallery save |
| `ContentPreProcessor.kt` | ~80 | Real — URL detection, markdown formatting, link preview fetch |
| `ChatPagingSource.kt` | ~35 | Real — cursor-based pagination wrapper |

### ViewModels (2 files)
| File | Status |
|------|--------|
| `ConversationViewModel.kt` | Real — 25 functions: send, resend, delete, edit, forward, reactions, star, pin, copy, report, search, jump, schedule, view-once, contact card |
| `ConversationListViewModel.kt` | Real — filter, search (debounced), archive/pin/mute/delete/mark-read, refresh |

### Compose UI (7 files)
| File | Status |
|------|--------|
| `ConversationListScreen.kt` | Real — filter chips, search, tiles with unread badge, long-press menu, FAB, empty state |
| `ConversationScreen.kt` | Real — message list, composer, reply preview, attachment sheet, emoji picker, voice record, delivery ticks, context menu |
| `EmojiPicker.kt` | Real — bottom sheet, 6 categories, search, quick reactions row |
| `MediaViewerScreen.kt` | Real — full-screen, pinch-to-zoom, share, download to gallery |
| `ChatColorsDrawable.kt` | Real — per-conversation solid/gradient/default |
| `V2ConversationItemShape.kt` | Real — cluster calculator (SINGLE/START/MIDDLE/END) |

### Notifications (6 files)
| File | Status |
|------|--------|
| `NotificationChannels.kt` | Real — 5 channels (messages, silent, calls, voice, other) |
| `MessageNotifier.kt` | Real — grouped notifications, summary for multi-conversation, per-conversation tracking |
| `NotificationBuilder.kt` | Real — MessagingStyle/InboxStyle, inline reply + mark-read actions, call notifications |
| `OptimizedMessageNotifier.kt` | Real — 50ms batch window, conversation grouping, async flush |
| `NotificationProfileHelper.kt` | Real — Android 12+ schedule-based notification profiles |
| `NotificationReplyReceiver.kt` | Real — BroadcastReceiver for inline reply + mark-read |

### Infrastructure
| File | Status |
|------|--------|
| `WebSocketService.kt` | Real — foreground service with auto-connect, notification channel, state tracking |
| `BootReceiver.kt` | Real — starts WebSocketService on BOOT_COMPLETED |
| `ShareTargetActivity.kt` | Real — handles ACTION_SEND text/image intents |
| `ConversationChooserTargetService.kt` | Real — direct share targets |

---

## Integration Test Results — 15/15 Passed

| # | Test | Endpoint | Result |
|---|------|----------|--------|
| 1 | Health check | `GET /health` | ✅ |
| 2 | Request OTP | `POST /v1/auth/request-otp` | ✅ |
| 3 | Verify OTP | `POST /v1/auth/verify-otp` | ✅ |
| 4 | Token refresh | `POST /v1/auth/refresh` | ✅ |
| 5 | Profile creation | `PUT /v1/profile` via Gateway | ✅ |
| 6 | JWKS endpoint | `GET /v1/auth/.well-known/jwks.json` | ✅ |
| 7 | Key registration | `POST /v1/keys/register` | ✅ |
| 8 | List devices | `GET /v1/auth/devices` | ✅ |
| 9-15 | Remaining OTP flow | Auth sub-checks | ✅ All passed |

---

## Phase 4 — Calls ✅

### `:core:calls` (8 files)
| File | Lines | Status |
|------|-------|--------|
| `CallManager.kt` | 459 | Real — state machine, WebSocket signaling, protobuf CallMessage, 6 missing functions fixed, stubs replaced |
| `WebRtcService.kt` | 198 | Real — PC factory, offer/answer/ICE, camera flip tracking, capturer cleanup |
| `AudioRouter.kt` | 168 | Real — audio focus, ringer, device selection (bluetooth/speaker/earpiece/wired) |
| `ActiveCallManager.kt` | 100 | Real — notification with mute/speaker/hangup actions, startCallScreen/stopCallScreen |
| `CallObserver.kt` | 60 | Real — observer registry with synchronized notifications for all events |
| `CallState.kt` | 90 | Real — all enums + data classes (CallState, CallLogEntry, PeekInfo, CallLinkData, etc.) |
| `CallNotificationReceiver.kt` | 27 | Real — broadcast receiver for mute/speaker/hangup notification actions |
| `CallForegroundService.kt` | 102 | Real — foreground service with call notification + mute/speaker actions |

### `:feature:calls` (10 files)
| File | Lines | Status |
|------|-------|--------|
| `IncomingCallScreen.kt` | 139 | Real — avatar, accept audio/video, decline, 30s auto-decline, E2EE label |
| `OutgoingCallScreen.kt` | 116 | Real — bouncing dots, cancel, speaker toggle, 45s timeout |
| `ActiveVoiceCallScreen.kt` | 139 | Real — timer, signal quality, mute/speaker, keypad, safety number |
| `ActiveVideoCallScreen.kt` | 114 | Real — remote video, PiP, controls overlay, camera flip |
| `GroupCallScreen.kt` | 109 | Real — participant grid, speaker view, admin controls, raise hand |
| `CallLogScreen.kt` | 183 | Real — list with search, filter (all/missed/incoming/outgoing), selection, delete |
| `SafetyNumberDialog.kt` | 75 | Real — safety number display with XXXX-XXXX format |
| `CallLogViewModel.kt` | 142 | Real — selection, staged deletion, filter, search (debounced 300ms), DB CRUD |
| `CallViewModel.kt` | 96 | **New** — ViewModel binding all call screens to CallManager via StateFlow |
| `CallLinkScreen.kt` | 111 | Real — display link, join call, share, admin controls |
| `CallLinkManager.kt` | 126 | Real — create/get/update/delete/join links with real backend credentials |

### Integration
| File | Status |
|------|--------|
| `CallManager.init()` in DI.kt | ✅ | |
| Call routes wired into AppNavigation (incoming/outgoing/active voice/video) | ✅ | |
| ConversationScreen `onStartCall` callback | ✅ | |

### Tests Written
| File | Test Cases | Covers |
|------|-----------|--------|
| `CallManagerStateTest.kt` | 29 | Initial state, incoming/outgoing call flow, end/deny, offer expiration, observer notification, state transitions |
| `CallLogViewModelTest.kt` | 15 | Initial state, filter, selection, search, log entry mapping |
| `CallLinkManagerTest.kt` | 11 | Create/get/update/delete/join, API success/failure, data classes |
| `CallStateTest.kt` | 22 | CallState defaults, enums, CallLogEntry, PeekInfo, IceServer, CallLinkData, AudioDevice, SignalStrength |
| `CallObserverRegistryTest.kt` | 13 | Register/unregister, multi-observer, all notification types, edge cases |
| `CallViewModelTest.kt` | 10 | Initial state, navigation, call actions |

---

## Tests Written

| File | Test Cases | Covers |
|------|-----------|--------|
| `GroupsViewModelTest.kt` | 24 | Create, add/remove members, invite links, join requests, delete, update, join via link, clear messages |
| `ContactsViewModelTest.kt` | 19 | Load, search, add, remove, block, unblock, blocked list, clear messages |
| `AuthBackendIntegrationTest.kt` | 8 | Live backend integration tests |
| **Calls (6 files)** | **100** | State machine, observer, log, links, data classes, view model |

---

## Next Up

### Phase 5 — Social: Groups, Contacts sync, Status/Stories, Channels, Profiles (30 files)
### Phase 6 — Extended: Stickers, Polls, Location, Backup, Settings (25 files)
### Phase 7 — Polish & Ship: Accessibility, i18n, Crash handling, Edge-to-edge (15 files)

---

## Sprint 0 — Critical Blockers Fixed
- ✅ `settings.gradle.kts`: `izetetic` → `zetetic` (SQLCipher repo URL)
- ✅ `AndroidManifest.xml`: Added `CallNotificationReceiver` as `<receiver>` with mute/speaker/hangup intent filters
- ✅ `build.gradle.kts`: `protobuf-java` → `protobuf-javalite` force
- ✅ `core/crypto/build.gradle.kts`: `protobuf-java` → `protobuf-javalite`

## Sprint 1 — 9 Missing DAOs Created (Phase 1)
- `KeyMaterialDao.kt`, `GroupDao.kt`, `GroupMemberDao.kt`, `MediaCacheDao.kt`
- `ProfileCacheDao.kt`, `CallLogDao.kt`, `StatusCacheDao.kt`
- `StickerPackDao.kt`, `InstalledStickerDao.kt`

## Sprint 2 — 3 Missing Phase 3 Chat Files
- `MessageBubble.kt` — 7 bubble composables (Text, Media, Voice, Document, Location, Sticker, System)
- `MessageContextMenu.kt` — Long-press actions (Copy, Reply, Edit, Delete, Forward, Star, Info)
- `MessageDataFetcher.kt` — Parallel data loading (reactions, mentions, pinned)

## Sprint 3 — Phase 5 Social (18 files)
- Groups: GroupMemberListScreen, GroupInviteScreen, JoinRequestsScreen, GroupEditor, GroupStateProcessor
- Contacts: ContactSyncService, AddContactScreen, ContactProfileScreen, FriendRequestsScreen
- Status: StatusViewModel + 3 screens (Feed, Create, Viewer)
- Channels: ChannelViewModel + 2 screens (Feed, Search)
- Profile: ProfileViewModel + ProfileScreen

## Sprint 4 — Phase 6 Extended (22 files)
- Stickers: StickerViewModel, StickerPicker, StickerStoreScreen
- Polls: PollViewModel, PollBubble, PollCreateSheet
- Location: LocationPickerScreen
- Backup: BackupViewModel + 6 archive modules (Chat, Contact, Group, Call, BackupArchive, BackupExporter)
- Settings: SettingsViewModel + 8 screens (Home, Account, Security, Privacy, Notifications, Appearance, Chats, Storage)

## Sprint 5 — Phase 7 Polish & Ship (7 files)
- NavHost.kt — Type-safe navigation with sealed NavRoute
- MessageCache.kt — Generic LRU cache
- ImagePipeline.kt — Coil config (25% heap + 50MB disk)
- MessageTrimmer.kt — WorkManager daily cleanup
- AccessibilityDelegate.kt — 4 content description generators
- RtlSupport.kt — RTL detection + layout direction
- EnchantApp.kt — Application class (DI → Crashlytics → LeakCanary → StrictMode)

---

## Pre-Existing Issues Not Yet Fixed (unrelated to Phase 4)

These existed before Phase 4 work and affect Gradle 9.5.1 builds only (not `./gradlew`):
- `core:push` module: needs serialization plugin + Google Play Services dep
- `core:notifications` module: needs `core-ktx` dependency
- `feature:share` module: needs access to `DI` and `chat` module classes
- `core:jobmanager`: contains empty directory

---

*Last updated: 2026-05-16*
