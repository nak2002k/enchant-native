# Enchant Native — Current Progress (Honest Audit)

> **⚠️ WARNING**: This replaces the previous inaccurate file. Previous claims of "✅ All real" were false.
> This audit compares the codebase against BUILD_PHASES documentation, SECURITY_ANDROID_PRACTICES.md,
> PRODUCTION_REFERENCE.md, LEADING_APPS_REFERENCE_MAP.md, and SCALABILITY_ANDROID.md.

---

## Overall Assessment: ~40-50% Complete

| Phase | Claimed | Reality |
|-------|---------|---------|
| 0 — Build Config | ✅ | 🟡 41 issues (no signing config, no cert pinning, Coil version mismatch) |
| 1 — Foundation | ✅ | 🔴 58+ files missing incl DI container, SignalStore, JobManager, domain models |
| 2 — Auth | ✅ | 🟡 Screens exist, but validateRestoredState is a stub, no 30s OTP cooldown, 0 screen tests |
| 3 — Core Chat | ✅ | 🟡 Files exist, but sessions are in-memory only, SendPipeline incomplete, 0 data layer tests |
| 4 — Calls | ✅ | ✅ Best module. 99 tests pass. Group call features stubbed. |
| 5 — Social | ✅ | 🔴 GroupEditor missing 12/18 functions, screen names wrong, 0 screen tests |
| 6 — Extended | ✅ | 🔴 3 settings screens missing (About, BlockedUsers, BackupSettings), 0 tests |
| 7 — Polish | 🔶 Mostly done | 🔴 NavHost.kt ALL 35+ composable routes are empty bodies. CrashReporter stubbed. |

---

## Files On Disk

| Metric | Count |
|--------|-------|
| Total .kt files | ~187 (167 source + 20 test) |
| Proto files | 28 `.proto` files |
| Build modules | 34 `build.gradle.kts` files |
| Test cases | ~244 (target: ~1015 per phase docs) |

---

## Phase 0 — Build Config (41 issues)

| # | Severity | Issue |
|---|----------|-------|
| 1 | 🔴 | **No release signing config** — `signingConfigs { release { } }` block completely missing. `assembleRelease` will fail. |
| 2 | 🔴 | **No certificate pinning** — `network_security_config.xml` missing `<pin-set>` for production API domain |
| 3 | 🔴 | **Coil version mismatch** — ProGuard rules `keep class coil3.**` but code uses Coil 2.x (`io.coil-kt:coil`) |
| 4 | 🔴 | **Consumer ProGuard files missing** — `consumerProguardFiles("consumer-rules.pro")` not in any of 33 modules |
| 5 | 🟡 | **`useJUnitPlatform()` only on 4/33 modules** — tests won't run on 29 modules |
| 6 | 🟡 | **`-Xcontext-receivers` and `-opt-in=kotlin.RequiresOptIn` missing** from ALL build files (spec requires them) |
| 7 | 🟡 | **`targetSdk = 35` missing from all non-app modules** — spec requires it in every module's `defaultConfig` |
| 8 | 🟡 | **`play-services-auth-api-phone`** and **`bouncycastle`** declared in version catalog but not in spec |
| 9 | 🟡 | **CI/CD missing `lintKotlin` and `ktlintCheck`** — only `./gradlew lint` is configured |
| 10 | 🟡 | **`kotlin.compose` plugin on `:core:auth`** with no Compose deps (previously caused build failure, now removed) |

---

## Phase 1 — Foundation: Critical Missing Files

### Missing Files (58+)
| Module | Files Present | Missing |
|--------|-------------|---------|
| `core:base` | AppConfig, SecurePreferences, KeyStoreManager, CoroutineDispatchers (4 files) | **DI.kt** — spec requires centralized `AppDependencies`-style DI container. Current DI is scattered `object` singletons. |
| `core:model` | `DomainModels.kt` (1 file instead of 5) | `User.kt`, `CallLog.kt`, `Contact.kt`, `Message.kt` (separate) |
| `core:jobmanager` | `JobManager.kt` (1 file, toy impl) | **20 files missing**: `Job.kt` (abstract class), `Constraint.kt`, `JobStorage.kt`, `Scheduler.kt`, 15+ job implementation files (PushSendJob, AttachmentDownloadJob, etc.) |
| `core:signalstore` | `SignalStore.kt` (1 file, 3 values) | **23 Values classes missing**: AccountValues, BackupValues, RegistrationValues, SettingsValues, PinValues, StorageServiceValues, StoryValues, WallpaperValues, LabsValues, PhoneNumberPrivacyValues, EmojiValues, ChatColorsValues, CallQualityValues, ProxyValues, RateLimitValues, OnboardingValues, InternalValues |
| `core:config` | `RemoteConfig.kt` (1 file, static map) | Firebase Remote Config integration missing; no `init()`, no `isEnabled()` |

### Stub/Incomplete Functions
| File | Function | Status |
|------|----------|--------|
| `KeyManager.kt` | `fetchKeyBundle()`, `cleanSignedPreKeys()`, `signWithIdentity()` | **Missing entirely** |
| `KeyManager.kt` | `topUpOpks()` | **Empty body** |
| `KeyManager.kt` | `rotateSignedPreKey()` | **Returns success without doing anything** |
| `KeyManager.kt` | `generateAndUploadKeys()` | **Generates IK locally but never uploads to IKS** |
| `KeyStoreManager.kt` | `getWrappedKeyBytes()` | **Always returns null (stub)** |
| `SessionManager.kt` | `init()` | **Stub — never loads sessions from database** |
| `SecurePreferences.kt` | `clearAll()` | **Missing `sodium_memzero`** to wipe in-memory copies |

### Security Gaps (per SECURITY_ANDROID_PRACTICES.md)
| Issue | Severity |
|-------|----------|
| AES-GCM used instead of XChaCha20-Poly1305 (spec requires XChaCha20) | 🔴 |
| KeyManager stores identity keys in plain base64url (not wrapped by Android KeyStore) | 🔴 |
| No `sodium_memzero` in `SecurePreferences.clearAll()` | 🟡 |
| No `setUserAuthenticationRequired(true)` on identity key KeyStore entry | 🟡 |
| No `setKeyValidityForOriginationEnd()` usage | 🟡 |
| `ed25519SkToX25519` and `ed25519PkToX25519` use raw key copy instead of libsodium-style conversion | 🟡 |

---

## Phase 2 — Auth & Onboarding

### What Exists
- All 11 auth screens (Welcome → AppLock) — real Compose implementations
- AuthStateMachine (157 lines) — 13x13 state matrix
- AuthRepository (191 lines) — 12 API calls
- AuthManager (199 lines) — auth lifecycle
- All 5 push module files (FcmReceiveService etc.)
- NavRoute.kt — 40+ sealed route classes

### What's Wrong
| Issue | Detail |
|-------|--------|
| `AuthStateMachine.validateRestoredState()` | Stub — returns `RegistrationState.Complete` unconditionally instead of calling refresh API |
| `resendOtp()` | No 30s client-side cooldown per spec |
| `AuthManager.searchUsername()` | Returns `List<String>` (usernames only), not `List<User>` with full profiles |
| **Tests** | **0 screen tests** exist (spec requires 100+) |

---

## Phase 3 — Core Chat

### What Exists
- ConversationRepository (233 lines), MessageSendPipeline (331 lines), IncomingMessageProcessor (241 lines)
- MediaService (243 lines), ContentPreProcessor (95 lines), ChatPagingSource (36 lines)
- ConversationViewModel + ConversationListViewModel
- 6+ Compose screens (ConversationList, Conversation, EmojiPicker, MediaViewer, etc.)
- 6 notification files (MessageNotifier, NotificationBuilder, NotificationChannels, etc.)

### What's Wrong
| Issue | Detail |
|-------|--------|
| **SessionManager sessions are purely in-memory** | Lost on app restart. No database persistence. |
| **`core:chat/` module doesn't exist** | All chat code in `feature:chat/` — Phase 3 doc paths are wrong |
| **MessageSendPipeline missing full params** | No mentions, bodyRanges, linkPreview, slideDeck, isViewOnce support |
| **ConversationRepository Flow is single-emit** | Not truly reactive — uses `callbackFlow` that doesn't re-emit on data changes |
| **WebSocketManager delivery/read receipts** | `sendDeliveryReceipt` and `sendReadReceipt` send empty data (just the envelope ID string, no actual receipt protobuf) |
| **OfflineQueue has no persistence** | All queued messages lost on process death |
| **Notifications** | MessageNotifier doesn't use NotificationChannels correctly (channel IDs hardcoded as strings) |
| **Tests** | **0 tests for data layer** (MessageSendPipeline, IncomingMessageProcessor, MediaService, ContentPreProcessor, ConversationRepository) |

---

## Phase 4 — Calls (Best Module)

### What Exists
- 8 core:calls files (CallManager 496 lines, WebRtcService 211, AudioRouter 168, etc.)
- 11 feature:calls files (IncomingCallScreen, OutgoingCallScreen, etc.)
- **99 tests passing** across 6 test files

### What's Wrong
| Issue | Detail |
|-------|--------|
| `process(CallAction)` pattern not implemented | Spec requires per-state action routing via a central `process()` function. Actual implementation calls methods directly. |
| Group call features stubbed | `raiseHand()`, `react()`, `requestRemoteMute()`, `removeParticipant()` all have stub bodies |
| `ActiveCallManager.stopCallScreen()` | **Empty body** |
| `CallManager.init()` requires `setApiClient()` | Fragile DI coupling — spec requires proper constructor injection |

---

## Phase 5 — Social

### What Exists
- Groups: GroupListScreen, CreateGroupScreen, GroupInfoScreen, GroupMemberListScreen, GroupInviteScreen, JoinRequestsScreen, GroupsViewModel, GroupsRepository, GroupEditor, GroupStateProcessor
- Contacts: ContactListScreen, AddContactScreen, ContactProfileScreen, FriendRequestsScreen, ContactSyncService, ContactsViewModel, ContactsRepository
- Status: StatusViewModel + 3 screens (Feed, Create, Viewer)
- Channels: ChannelViewModel + 2 screens (Feed, Search)
- Profile: ProfileViewModel + ProfileScreen

### What's Wrong
| Issue | Detail |
|-------|--------|
| **GroupEditor missing 12/18 functions** | Only `addMembers`, `removeMember`, `setMemberAdmin` exist. Missing: `updateGroupTimer`, `updateAttributesRights`, `updateMembershipRights`, `setAnnouncementGroup`, `revokeInvites`, `banUser`, `unbanUser`, `ejectMember`, `terminateGroup`, `acceptInvite`, `cycleGroupLinkPassword`, `setJoinByGroupLinkState`, `commitChangeWithConflictResolution` |
| **Doc names ≠ actual files** | `GroupsScreen.kt` → `GroupListScreen.kt`, `ContactsScreen.kt` → `ContactListScreen.kt`, `GroupViewModel.kt` → `GroupsViewModel.kt` |
| **GroupStateProcessor has no conflict resolution** | Simplified from spec — doesn't handle `handleP2PChange` or Server/CRDT conflict resolution |
| **Tests** | **0 screen tests** for ANY Phase 5 module |

---

## Phase 6 — Extended

### What Exists
- Stickers: StickerViewModel, StickerPicker, StickerStoreScreen
- Polls: PollViewModel, PollBubble, PollCreateSheet
- Location: LocationPickerScreen
- Settings: SettingsViewModel + 8 screens (Home, Account, Security, Privacy, Notifications, Appearance, Chats, Storage)
- Backup: BackupViewModel, BackupExporter + 5 archive exporters

### What's Wrong
| Issue | Detail |
|-------|--------|
| **🔴 3 settings screens MISSING** | `AboutScreen.kt`, `BlockedUsersScreen.kt`, `BackupSettingsScreen.kt` — do not exist anywhere |
| **SettingsViewModel incomplete** | `updatePrivacy()`, `loadDevices()`, `revokeDevice()`, `getStorageUsage()` are implemented but backend-facing (no offline defaults) |
| **BackupViewModel missing `restoreBackup()`** | Per spec, backup must support restore — only export (upload) is implemented |
| **PollBubble** | No actual poll rendering — doesn't display vote counts, doesn't handle closed polls |
| **Tests** | **0 tests for ANY Phase 6 module** |

---

## Phase 7 — Polish & Ship (Critically Incomplete)

### What Exists
- NavRoute.kt (50 lines) — 40+ sealed route classes
- NavHost.kt (255 lines) — all composable routes wired
- MessageCache.kt, ImagePipeline.kt, MessageTrimmer.kt, PerformanceTracker.kt
- AccessibilityDelegate.kt, RtlSupport.kt, Accessibility.kt
- CrashReporter.kt
- EnchantApp.kt
- MainActivity.kt (with FLAG_SECURE + edge-to-edge)

### What's Wrong

| File | Problem | Severity |
|------|---------|----------|
| **NavHost.kt** | **ALL 35+ composable routes have empty `{}` bodies.** Navigation framework exists but renders NOTHING. | 🔴 |
| **EnchantApp.kt** | `initDi()` is **empty**. `initLeakCanary()` is **empty**. Only `CrashReporter.init()`, `ImagePipeline.init()`, StrictMode, and NotificationChannels are wired. | 🔴 |
| **CrashReporter.kt** | No Crashlytics dependency. Missing: `setUserId()`, `logEvent()`, `logError()`, `logDecryptionFailure()`. Email regex scrubbing missing. Uses `Log.d()` only. | 🔴 |
| **RtlSupport.kt** | Returns `Int` (not a Compose `Modifier` extension). Can't be used with Compose's `Modifier.mirrorLayoutDirection()`. | 🔴 |
| **MessageCache.kt** | Requires `idExtractor` lambda not in spec. No eviction listener. | 🟡 |
| **Tests** | **0 tests for ANY Phase 7 file** | 🔴 |

---

## Test Coverage: Real Numbers

| Test File | Tests | Module |
|-----------|-------|--------|
| `CallManagerStateTest.kt` | 29 | calls |
| `CallLogViewModelTest.kt` | 15 | calls |
| `CallLinkManagerTest.kt` | 11 | calls |
| `CallStateTest.kt` | 22 | calls |
| `CallObserverRegistryTest.kt` | 13 | calls |
| `CallViewModelTest.kt` | 10 | calls |
| `CryptoHelperTest.kt` | 25 | crypto |
| `X3DHTest.kt` | 7 | crypto |
| `DoubleRatchetTest.kt` | 9 | crypto |
| `SessionManagerTest.kt` | 6 | crypto |
| `ApiClientTest.kt` | 13 | network |
| `OfflineQueueTest.kt` | 7 | network |
| `WebSocketManagerTest.kt` | 3 | network |
| `MessageDaoTest.kt` | 8 | database |
| `ConversationDaoTest.kt` | 7 | database |
| `SessionDaoTest.kt` | 5 | database |
| `GroupsViewModelTest.kt` | 24 | groups |
| `ContactsViewModelTest.kt` | 19 | contacts |
| `AuthBackendIntegrationTest.kt` | 8 | auth |
| **Total** | **~244** | — |

### Test Coverage vs Target
| Module | Tests | Target | Gap |
|--------|-------|--------|-----|
| calls | 99 | 100+ | ~1 |
| crypto | 47 | 111 | **-64** |
| network | 23 | 90 | **-67** |
| database | 20 | 88 | **-68** |
| base | 0 | 61 | **-61** |
| jobmanager | 0 | 90 | **-90** |
| signalstore | 0 | 75 | **-75** |
| config | 0 | 5 | **-5** |
| groups | 24 | 30+ | ~6 |
| contacts | 19 | 30+ | ~11 |
| other features | 0 | 250+ | **-250** |
| **Total** | **~244** | **~1015** | **~771** |

---

## Priority Action Plan

### Tier 1 — Make It Build and Run
1. Fix NavHost.kt — replace all 35+ empty `{}` stubs with actual composables
2. Add release signing config so `assembleRelease` works
3. Fix network_security_config.xml with proper cert pinning
4. Fix Coil ProGuard rules (coil3 → coil)
5. Add `consumerProguardFiles` to all modules

### Tier 2 — Security
6. Switch CryptoHelper from AES-GCM to XChaCha20-Poly1305 (libsodium)
7. Wrap KeyManager's key storage with Android KeyStore (use `stored key = keystore.encrypt(raw key)`)
8. Add `sodium_memzero` to SecurePreferences.clearAll()
9. Implement proper SealedSender and safety number verification

### Tier 3 — Core Missing Features
10. Build DI.kt (Signal-style AppDependencies)
11. Implement SessionManager with database persistence
12. Build full JobManager with 20+ files
13. Build full SignalStore with 23 Values classes

### Tier 4 — Tests
14. Write remaining 771+ tests to meet spec targets
15. Add coverage threshold enforcement to CI

---

*Last updated: 2026-05-16 — Honest audit against phase docs, security specs, and Signal reference.*
