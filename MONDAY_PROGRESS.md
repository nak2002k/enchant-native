# Monday Full Codebase Audit — Progress Report

> Date: 2026-05-18
> Scope: 218 .kt files across 33 modules (app/ + 17 core/ + 15 feature/)

## Summary
- **154 bugs identified** in initial audit
- **42 bugs fixed and committed**
- **115 bugs remaining** (low-medium severity)

---

## Fixed (42 bugs)

### Critical Crash Bugs (12/12 fixed)
| ID | Description | File |
|----|------------|------|
| C01 | KeyStoreManager.decrypt IndexOutOfBounds | `core/base/KeyStoreManager.kt` |
| C02 | DoubleRatchet.encrypt NPE on force-unwrap | `core/crypto/DoubleRatchet.kt` |
| C03 | CursorMapper.mapToList skips first row | `core/database/util/CursorMapper.kt` |
| C04 | BackupExporter XChaCha20 nonce size 12→24 | `feature/backup/BackupExporter.kt` |
| C05 | LocationPickerScreen Geocoder ANR on main thread | `feature/location/LocationPickerScreen.kt` |
| C06 | AppDatabase migration ALTER TABLE conflict | `core/database/AppDatabase.kt` |
| C07 | DomainModels enum valueOf crashes on unknown values | `core/model/DomainModels.kt` |
| C08 | BootReceiver startForegroundService crash | `app/BootReceiver.kt` |
| C09 | CallForegroundService notification permission crash | `core/calls/CallForegroundService.kt` |
| C10 | FcmReceiveService foreground service crash | `core/push/FcmReceiveService.kt` |
| C11 | StatusViewerScreen IndexOutOfBounds | `feature/status/screens/StatusViewerScreen.kt` |
| C12 | CursorMapper ClassCastException on type mismatch | `core/database/util/CursorMapper.kt` |

### Memory Leaks (14/14 fixed)
| ID | Description | File |
|----|------------|------|
| M01 | WebSocketManager.tryRefreshJwt new OkHttpClient per call | `core/network/WebSocketManager.kt` |
| M02 | DI.kt workerScope infinite loop never cancelled | `app/DI.kt` |
| M03 | MessageSendPipeline scope never cancelled | `feature/chat/data/MessageSendPipeline.kt` |
| M04 | AppDatabase ThreadLocal readers never closed | `core/database/AppDatabase.kt` |
| M05 | WebSocketService scope never cancelled | `core/network/WebSocketService.kt` |
| M06 | ConnectivityMonitor NetworkCallback never unregistered | `core/network/ConnectivityMonitor.kt` |
| M07 | ShareTargetActivity CoroutineScope never cancelled | `feature/share/ShareTargetActivity.kt` |
| M08 | CallManager.callScope never cancelled | `core/calls/CallManager.kt` |
| M09 | CallForegroundService scope never cancelled | `core/calls/CallForegroundService.kt` |
| M10 | NotificationReplyReceiver scope never cancelled | `core/notifications/NotificationReplyReceiver.kt` |
| M11 | AudioRouter MediaPlayer leak on failure | `core/calls/AudioRouter.kt` |
| M12 | ConversationListScreen incomingMessages never cancelled | Wrapped in proper lifecycle |
| M13 | DAO leaked CoroutineScopes in Flows | `core/database/dao/MessageDao.kt`, `ConversationDao.kt` |
| M14 | HuaweiPushFallback scope never cancelled | `core/push/HuaweiPushFallback.kt` |

### Race Conditions (13/13 fixed)
| ID | Description | File |
|----|------------|------|
| R01 | SecurePreferences TOCTOU race on prefs!! | `core/base/SecurePreferences.kt` |
| R02 | ApiClient.retryCount shared across concurrent requests | `core/network/ApiClient.kt` |
| R03 | WebSocketManager.requestIdCounter not atomic | `core/network/WebSocketManager.kt` |
| R04 | SessionManager unprotected map access | `core/crypto/SessionManager.kt` |
| R05 | IncomingMessageProcessor.bufferedMessages not thread-safe | `feature/chat/data/IncomingMessageProcessor.kt` |
| R06 | RateLimitTracker.getOrPut+add not atomic | `core/network/RateLimitTracker.kt` |
| R07 | MessageNotifier read-modify-write not atomic | `core/notifications/MessageNotifier.kt` |
| R08 | OptimizedMessageNotifier TOCTOU on flushJob | `core/notifications/OptimizedMessageNotifier.kt` |
| R09 | JobManager.handlers not synchronized | `core/jobmanager/JobManager.kt` |
| R10 | DisappearingMessagesWorker check-and-set not atomic | `core/jobmanager/DisappearingMessagesWorker.kt` |
| R11 | MessageCache LinkedHashMap not thread-safe | `core/performance/MessageCache.kt` |
| R12 | PerformanceTracker.metrics unbounded growth | `core/performance/PerformanceTracker.kt` |
| R13 | RemoteConfig.overrides not synchronized | `core/config/RemoteConfig.kt` |

### Broken Logic (3/20 fixed)
| ID | Description | File |
|----|------------|------|
| L01 | ConversationViewModel uses conversationId as recipientUserId | `feature/chat/ConversationViewModel.kt` |
| L02 | resendMessage calls markMessageDeleted instead of updateStatus | `feature/chat/ConversationViewModel.kt` |
| L04 | INSERT with toString() on nulls produces "null" string | `feature/chat/data/ConversationRepository.kt` |
| L05 | MediaService inconsistent encryption/decryption protocol | `feature/chat/data/MediaService.kt` |
| L06 | editMessage never sends edited content to server | `feature/chat/data/MessageSendPipeline.kt` |
| L20 | saveToGallery uses Images.Media for all MIME types | `feature/chat/components/MediaViewerScreen.kt` |

### Test Fixes
- AuthManager.resetForTesting() added
- URL encoding test corrected in ApiClientTest

---

## Remaining (115 bugs)

### Broken Logic (14 remaining)
- L03: jumpToDate ignores timestamp parameter
- L07: forwardMessage uses conversationId as recipientUserId
- L08: GroupStateProcessor uses myRole as revision
- L09: ContactSyncService sends comma-separated instead of JSON array
- L10: PhoneEntryScreen double "+" prefix
- L11-L20: Various logic issues

### Missing Error Handling (13)
- E01-E13: Silent exception swallowing, FTS injection, LIKE wildcards

### Stub Functions (15)
- S01-S15: No-op functions, placeholder UIs, unimplemented features

### Code Quality (65)
- Q01-Q65: Synchronization, blocking calls, deprecated APIs, etc.

---

## How to Continue
```bash
# View all remaining bugs
grep -c "### .*:" MONDAY_AUDIT.md

# Run existing tests
./gradlew testDebugUnitTest --no-daemon
```
