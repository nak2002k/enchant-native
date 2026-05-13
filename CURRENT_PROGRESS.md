# Enchant Native — Current Progress

> This file tracks all completed work, current status, and next steps.
> Update after every logical change.

---

## Project Status

| Metric | Value |
|--------|-------|
| **Phase** | Phase 1 — Foundation |
| **Total files** | ~85 (configs, protos, Kotlin sources, resources) |
| **Kotlin source files** | 31 (core) + 2 (app stubs) |
| **Proto files** | 16 |
| **Build modules** | 30 (1 app + 15 core + 15 feature) |
| **Last commit** | `7da01c7 — Phase 1 fixes` |
| **Tests written** | 0 (dedicated test agent runs after implementation) |

---

## Phase 0 — Project Setup ✅

### Completed
| Module | Files | Status |
|--------|-------|--------|
| Gradle config | `libs.versions.toml`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` | ✅ |
| Gradle wrapper | `gradlew`, `gradlew.bat`, `gradle-wrapper.jar` (8.11.1) | ✅ |
| Android manifest | 27 permissions, 9 services/activities/receivers | ✅ |
| Resources | themes, colors, strings, icons, 4 XML configs | ✅ |
| ProGuard | `proguard-rules.pro` covering all libs | ✅ |
| CI/CD | `.github/workflows/ci.yml` (lint → test → build) | ✅ |
| .gitignore | keys, builds, IDE, env properly excluded | ✅ |
| .env.example | Dev onboarding config | ✅ |

### Proto Files (16)
| File | Status |
|------|--------|
| `WebSocketResources.proto` | ✅ REQUEST/RESPONSE frame format |
| `Envelope.proto` | ✅ 22-field message wrapper |
| `Content.proto` | ✅ 9-type oneof + SenderKeyDistribution |
| `DataMessage.proto` | ✅ 30 fields, 17 nested messages |
| `SyncMessage.proto` | ✅ 19 oneof members, 7+ sub-messages |
| `CallMessage.proto` | ✅ Offer/Answer/ICE/Hangup/Busy/Opaque |
| `ReceiptMessage.proto` | ✅ DELIVERY/READ/VIEWED |
| `TypingMessage.proto` | ✅ STARTED/STOPPED with groupId |
| `AttachmentPointer.proto` | ✅ cdnId/cdnKey, key, size, digest, blurHash |
| `BodyRange.proto` | ✅ Formatting ranges + mentions |
| `StoryMessage.proto` | ✅ File + TextAttachment with gradients |
| `GroupContext.proto` | ✅ V2 + GroupChange.Actions (19 action types) |
| `Provisioning.proto` | ✅ Device linking |
| `StorageService.proto` | ✅ Manifest-based multi-device sync |
| `InternalSerialization.proto` | ✅ Processing pipeline metadata |
| `SessionRecord.proto` | ✅ Ratchet state serialization |

---

## Phase 1 — Foundation ✅

### `:core:base` (5 files)
| File | Purpose | Implementation |
|------|---------|---------------|
| `AppConfig.kt` | Config singleton, URL derivation | ✅ 60 lines — real |
| `SecurePreferences.kt` | EncryptedSharedPreferences wrapper | ✅ 63 lines — real |
| `KeyStoreManager.kt` | Android KeyStore: EC/AES, sign/verify, StrongBox detection | ✅ 166 lines — real |
| `CoroutineDispatchers.kt` | Named dispatchers; crypto is single-threaded | ✅ 10 lines — real |
| `DI.kt` | Manual DI with ordered init chain, mutex-guarded reset | ✅ 69 lines — real |

### `:core:network` (7 files)
| File | Purpose | Implementation |
|------|---------|---------------|
| `ApiClient.kt` | OkHttp HTTP client — retry, JWT refresh, 128MB limit | ✅ 164 lines — real |
| `AuthInterceptor.kt` | Bearer token injection + 401 refresh (concurrent-safe) | ✅ 72 lines — real |
| `RateLimitTracker.kt` | Client-side rate limiting with Retry-After | ✅ 60 lines — real |
| `WebSocketManager.kt` | Protobuf WS frames, exp. backoff, keepalive, auth timeout | ✅ 348 lines — real (ByteString bug fixed) |
| `ConnectivityMonitor.kt` | NetworkCallback → StateFlow (online/offline + type) | ✅ 72 lines — real |
| `OfflineQueue.kt` | ConcurrentLinkedQueue with drain + max 5 retries | ✅ 68 lines — real |
| `ApiModels.kt` | 45 @Serializable data classes for all API endpoints | ✅ 240 lines — real |

### `:core:database` (8 files)
| File | Purpose | Implementation |
|------|---------|---------------|
| `AppDatabase.kt` | SQLCipher + WAL + pool (1 writer / thread-local readers) + DDL (14 tables) + migrator | ✅ 200 lines — real |
| `Entities.kt` | 15 entity data classes matching `docs/DATABASE_ARCHITECTURE.md` | ✅ 185 lines — real |
| `CursorMapper.kt` | Reflection-free auto-mapping: Cursor → Entity | ✅ 47 lines — real |
| `MessageDao.kt` | Full CRUD, paginated Flow, FTS search, expired deletion | ✅ 116 lines — real |
| `ConversationDao.kt` | CRUD + reactive list + archive/pin/mute + unread counts | ✅ 85 lines — real |
| `SessionDao.kt` | Store/load/delete for Signal Protocol sessions | ✅ 27 lines — real |
| `IdentityDao.kt` | Identity key CRUD with verified status | ✅ 33 lines — real |
| `RecipientDao.kt` | Contact cache + username lookup + blocked list + search | ✅ 64 lines — real |

### `:core:crypto` (6 files)
| File | Purpose | Implementation |
|------|---------|---------------|
| `SodiumProvider.kt` | libsodium JNI init placeholder | ✅ 12 lines — stub |
| `CryptoHelper.kt` | HKDF-SHA256, SHA-256/512, CSPRNG, base64url, zeroBytes, constant-time cmp | ✅ 82 lines — real |
| `KeyManager.kt` | Key lifecycle (generate, upload, rotate) | ✅ 47 lines — stub (needs libsodium) |
| `X3DH.kt` | X3DH key agreement data models + API | ✅ 38 lines — stub (needs libsodium DH) |
| `DoubleRatchet.kt` | Ratchet state machine, encrypt/decrypt API, serialization | ✅ 76 lines — stub (needs libsodium) |
| `SessionManager.kt` | Session orchestration API | ✅ 54 lines — stub (needs X3DH+Ratchet) |

---

## Next Up

### Phase 2 — Auth & Onboarding (35 files)
| File | Purpose |
|------|---------|
| `AuthStateMachine.kt` | Event-driven registration state machine |
| `AuthRepository.kt` | Network calls for OTP, keys, profile |
| `AuthManager.kt` | High-level auth orchestration |
| `WelcomeScreen.kt` → `KeyGenerationScreen.kt` | 10+ Compose screens |
| `FcmReceiveService.kt` | FCM push wake-up |
| `FcmFetchManager.kt` | Foreground vs background fetch strategy |
| `PushTokenRegistrar.kt` | FCM token lifecycle |

### Phase 3 — Core Chat (40 files)
### Phase 4 — Calls (18 files)
### Phase 5 — Social (30 files)
### Phase 6 — Extended (25 files)
### Phase 7 — Polish & Ship (15 files)

---

## Known Gaps

| Gap | Reason | When |
|-----|--------|------|
| X3DH/Double Ratchet implementations | Needs libsodium JNI native library | Phase 3 (E2EE pipeline needs it) |
| All 7 manifest service classes have stubs | Implemented in their respective phases | Per phase |
| No tests written | Dedicated test agent writes tests after implementation | After each phase |
| `google-services.json` missing | Firebase project config | Phase 2 (FCM needs it) |
| `kotlin-reflect` hardcoded version | Not in version catalog | Minor — add to libs.versions.toml |

---

*Last updated: 2026-05-14*
