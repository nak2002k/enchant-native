# Enchant Native — Current Progress

> This file tracks all completed work, current status, and next steps.
> Update after every logical change.

---

## Project Status

| Metric | Value |
|--------|-------|
| **Phase** | Phase 4 — Calls (in progress) |
| **Kotlin source files** | 92 |
| **Proto files** | 16 (generated: 25 Java files) |
| **Build modules** | 33 (1 app + 17 core + 15 feature) |
| **Tests written** | 3 test files, 63 test cases |

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
| `AppDatabase.kt` | 200 | Real — SQLite + WAL + pool (1 writer / thread-local readers) + DDL (15 tables) |
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

## Tests Written

| File | Test Cases | Covers |
|------|-----------|--------|
| `GroupsViewModelTest.kt` | 27 | Create, add/remove members, invite links, join requests, delete, update, join via link, clear messages |
| `ContactsViewModelTest.kt` | 21 | Load, search, add, remove, block, unblock, blocked list, clear messages |
| `AuthBackendIntegrationTest.kt` | 15 | Live backend integration tests |

---

## Next Up

### Phase 4 — Calls (18 files)
### Phase 5 — Social: Status/Stories, Channels, Profile (15 files)
### Phase 6 — Extended: Stickers, Polls, Location, Backup, Settings (25 files)
### Phase 7 — Polish & Ship (10 files)

---

*Last updated: 2026-05-16*
