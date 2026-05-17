# Monday Full Codebase Audit — 2026-05-18

> Scope: Every .kt file across app/, core/, feature/ (218 files total)
> Methodology: Line-by-line review of all source files against production standards

## Fix Status

### Fixed (2026-05-18)
- **C01**: KeyStoreManager.decrypt() — Added length check before slicing
- **C02**: DoubleRatchet.encrypt() — Added null guard for receivingRatchetKeyPublic
- **C03**: CursorMapper.mapToList() — Fixed to use do-while pattern
- **C04**: BackupExporter — Fixed nonce size from 12 to 24 bytes (XCHACHA_NONCE_SIZE)
- **C05**: LocationPickerScreen — Moved Geocoder calls to Dispatchers.IO
- **C06**: AppDatabase migration v3 — Wrapped ALTER TABLE in try/catch
- **C07**: DomainModels — Added safeValueOf() for ConversationType and MessageStatus enums
- **C08**: BootReceiver — Added try/catch for startForegroundService
- **C09**: CallForegroundService — Added try/catch for startForeground, cancel scope in onDestroy
- **C10**: FcmReceiveService — Added try/catch for startForegroundService
- **C11**: StatusViewerScreen — Added bounds check for currentIndex
- **C12**: CursorMapper — Added try/catch for type conversion
- **M01**: WebSocketManager — Added singleton refreshClient instead of creating new OkHttpClient per call
- **M02**: DI.kt — Stored workerScope as field, cancel in reset()
- **M03**: MessageSendPipeline — Added shutdown() method, scope now cancellable
- **M09**: CallForegroundService — Cancel scope in onDestroy()
- **Test fixes**: AuthManager.resetForTesting() added, URL encoding test updated

### Remaining (To Be Fixed)
- **M04-M08, M10-M14**: Remaining memory leaks
- **R01-R15**: Race conditions
- **L01-L20**: Broken logic / wrong behavior
- **E01-E13**: Missing error handling
- **S01-S15**: Stub / incomplete functions
- **Q01-Q65**: Code quality / minor issues

---

## CRITICAL — Crash Bugs (Ship Blockers)

### C01: `KeyStoreManager.decrypt()` — IndexOutOfBounds on short ciphertext
- **File**: `core/base/src/main/java/org/enchant/core/base/KeyStoreManager.kt:141`
- **Issue**: `ciphertext.copyOfRange(0, 12)` crashes if `ciphertext.size < 12`
- **Fix**: Add length check before slicing

### C02: `DoubleRatchet.encrypt()` — NPE on force-unwrap
- **File**: `core/crypto/src/main/java/org/enchant/core/crypto/DoubleRatchet.kt:122`
- **Issue**: `s.receivingRatchetKeyPublic!!` crashes if null (freshly initialized Bob)
- **Fix**: Add null guard, return error instead of crashing

### C03: `CursorMapper.mapToList()` — Skips first row
- **File**: `core/database/src/main/java/org/enchant/core/database/util/CursorMapper.kt:17`
- **Issue**: Calls `cursor.moveToNext()` as first operation, skipping row 0
- **Fix**: Use `do { ... } while (cursor.moveToNext())` pattern

### C04: `BackupExporter.kt` — XChaCha20 nonce size mismatch
- **File**: `feature/backup/src/main/java/org/enchant/backup/BackupExporter.kt:98`
- **Issue**: `val nonce = ByteArray(12)` but XChaCha20 requires 24-byte nonce
- **Fix**: Use `ByteArray(24)` or `BackupArchive.XCHACHA_NONCE_SIZE`

### C05: `LocationPickerScreen` — Blocking Geocoder on main thread → ANR
- **File**: `feature/location/src/main/java/org/enchant/location/LocationPickerScreen.kt:36,109`
- **Issue**: `Geocoder.getFromLocation()` is synchronous blocking, called from Compose main thread
- **Fix**: Move to `withContext(Dispatchers.IO)`

### C06: `AppDatabase.migration v3` — ALTER TABLE conflict
- **File**: `core/database/src/main/java/org/enchant/core/database/AppDatabase.kt:45-48`
- **Issue**: `ALTER TABLE messages ADD COLUMN is_pinned` throws if column already exists
- **Fix**: Wrap in try/catch or use `IF NOT EXISTS` equivalent

### C07: `DomainModels` — Enum valueOf crashes on unknown DB values
- **File**: `core/model/src/main/java/org/enchant/core/model/DomainModels.kt:21,59`
- **Issue**: `ConversationType.valueOf(e.type.uppercase())` crashes on unknown values
- **Fix**: Use `enumValueOfOrNull` or fallback to UNKNOWN

### C08: `BootReceiver` — Foreground service crash on Android 12+
- **File**: `app/src/main/java/org/enchant/BootReceiver.kt:14`
- **Issue**: `startForegroundService` without `FOREGROUND_SERVICE` permission check
- **Fix**: Add permission check + try/catch

### C09: `CallForegroundService` — startForeground without notification permission
- **File**: `core/calls/src/main/java/org/enchant/core/calls/CallForegroundService.kt:34`
- **Issue**: Crashes on Android 13+ if `POST_NOTIFICATIONS` not granted
- **Fix**: Add permission check before `startForeground()`

### C10: `FcmReceiveService` — startForegroundService IllegalStateException
- **File**: `core/push/src/main/java/org/enchant/core/push/FcmReceiveService.kt:22`
- **Issue**: Can throw `IllegalStateException` on Android 8+ if app in background too long
- **Fix**: Add try/catch + fallback

### C11: `StatusViewerScreen` — IndexOutOfBoundsException on status navigation
- **File**: `feature/status/src/main/java/org/enchant/status/screens/StatusViewerScreen.kt:57`
- **Issue**: `statuses[currentIndex]` can exceed bounds when timer + tap race
- **Fix**: Add bounds check before array access

### C12: `CursorMapper` — ClassCastException on type mismatch
- **File**: `core/database/src/main/java/org/enchant/core/database/util/CursorMapper.kt:41`
- **Issue**: `cursor.getInt(columnIndex)` throws if column contains non-integer
- **Fix**: Add type-safe getter with fallback

---

## HIGH — Memory Leaks

### M01: `WebSocketManager.tryRefreshJwt()` — New OkHttpClient per call
- **File**: `core/network/src/main/java/org/enchant/core/network/WebSocketManager.kt:432`
- **Issue**: Creates new `OkHttpClient` on every JWT refresh — each leaks dispatcher + connection pool
- **Fix**: Reuse singleton client

### M02: `DI.kt` — workerScope infinite loop never cancelled
- **File**: `app/src/main/java/org/enchant/DI.kt:143-149`
- **Issue**: `while(true) { delay(60_000L); DisappearingMessagesWorker.tick() }` runs forever
- **Fix**: Use `SupervisorJob()` stored as field, cancel in `reset()`

### M03: `MessageSendPipeline` — scope never cancelled
- **File**: `feature/chat/src/main/java/org/enchant/chat/data/MessageSendPipeline.kt:42`
- **Issue**: `scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` — no cleanup method
- **Fix**: Add `shutdown()` method, call from DI reset

### M04: `AppDatabase.readerThreadLocal` — ThreadLocal readers never closed
- **File**: `core/database/src/main/java/org/enchant/core/database/AppDatabase.kt:62`
- **Issue**: Each thread gets a new `SQLiteDatabase` that is never closed
- **Fix**: Track readers, close in `close()` method

### M05: `WebSocketService` — scope never cancelled in onDestroy
- **File**: `core/network/src/main/java/org/enchant/core/network/WebSocketService.kt:20`
- **Issue**: `scope` never cancelled, `connect()` coroutine runs forever
- **Fix**: Cancel scope in `onDestroy()`

### M06: `ConnectivityMonitor` — NetworkCallback never unregistered
- **File**: `core/network/src/main/java/org/enchant/core/network/ConnectivityMonitor.kt:51`
- **Issue**: `callback` never unregistered, leaks `ConnectivityManager` reference
- **Fix**: Add `unregister()` method, call on cleanup

### M07: `ShareTargetActivity` — CoroutineScope never cancelled
- **File**: `feature/share/src/main/java/org/enchant/share/ShareTargetActivity.kt:20`
- **Issue**: `scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` — never cancelled
- **Fix**: Use `lifecycleScope` or cancel in `onDestroy()`

### M08: `CallManager.callScope` — never cancelled
- **File**: `core/calls/src/main/java/org/enchant/core/calls/CallManager.kt:47`
- **Issue**: `callScope` is never cancelled, coroutines leak across app lifetime
- **Fix**: Cancel in `cleanup()` or provide `shutdown()` method

### M09: `CallForegroundService` — scope never cancelled
- **File**: `core/calls/src/main/java/org/enchant/core/calls/CallForegroundService.kt:17`
- **Issue**: `scope` never cancelled in `onDestroy()`
- **Fix**: Cancel in `onDestroy()`

### M10: `NotificationReplyReceiver` — scope never cancelled
- **File**: `core/notifications/src/main/java/org/enchant/core/notifications/NotificationReplyReceiver.kt:13`
- **Issue**: `scope` never cancelled
- **Fix**: Use `goAsync()` + finish properly

### M11: `AudioRouter` — MediaPlayer leaks on failure
- **File**: `core/calls/src/main/java/org/enchant/core/calls/AudioRouter.kt:157-166`
- **Issue**: `playDisconnectTone()` creates MediaPlayer that only releases on completion — if playback fails, never releases
- **Fix**: Add `OnErrorListener` that calls `release()`

### M12: `ConversationListScreen` — incomingMessages Flow never cancelled
- **File**: `feature/chat-list/src/main/java/org/enchant/chatlist/ConversationListScreen.kt:62`
- **Issue**: `LaunchedEffect(Unit)` collects `WebSocketManager.incomingMessages` but never cancels
- **Fix**: Use `LaunchedEffect` with proper key or `DisposableEffect`

### M13: `MessageDao`/`ConversationDao`/`RecipientDao` — leaked CoroutineScopes in Flows
- **File**: `core/database/src/main/java/org/enchant/core/database/dao/MessageDao.kt:118`
- **Issue**: `CoroutineScope(Dispatchers.Default).launch { ... }` in Flow callbacks — scopes never tied to lifecycle
- **Fix**: Use `callbackFlow` with proper `awaitClose`

### M14: `HuaweiPushFallback` — scope never cancelled
- **File**: `core/push/src/main/java/org/enchant/core/push/HuaweiPushFallback.kt:15`
- **Issue**: `scope` lives forever, `pollingJob` can be cancelled but scope persists
- **Fix**: Cancel scope in cleanup

---

## HIGH — Race Conditions

### R01: `SecurePreferences` — TOCTOU race on `prefs!!`
- **File**: `core/base/src/main/java/org/enchant/core/base/SecurePreferences.kt`
- **Issue**: `prefs` is `@Volatile` but `init()` not synchronized; between null check and `!!`, another thread could set to null
- **Fix**: Synchronize `init()`, use local variable pattern

### R02: `ApiClient.retryCount` — shared across concurrent requests
- **File**: `core/network/src/main/java/org/enchant/core/network/ApiClient.kt:114`
- **Issue**: `@Volatile var retryCount` incremented by all concurrent requests — corrupts counter
- **Fix**: Use `AtomicInteger` or per-request counter

### R03: `WebSocketManager.requestIdCounter` — not atomic
- **File**: `core/network/src/main/java/org/enchant/core/network/WebSocketManager.kt:58`
- **Issue**: Plain `var` incremented from multiple coroutines — duplicate IDs possible
- **Fix**: Use `AtomicInteger`

### R04: `SessionManager` — unprotected map access
- **File**: `core/crypto/src/main/java/org/enchant/core/crypto/SessionManager.kt:33-37`
- **Issue**: `sessions`, `identityKeys`, `nonBlockingApproval` accessed without `sessionLock`
- **Fix**: Protect all map access with lock or use `ConcurrentHashMap`

### R05: `IncomingMessageProcessor.bufferedMessages` — not thread-safe
- **File**: `feature/chat/src/main/java/org/enchant/chat/data/IncomingMessageProcessor.kt:45`
- **Issue**: `mutableListOf` accessed from multiple coroutines — `ConcurrentModificationException`
- **Fix**: Use `ConcurrentLinkedQueue` or `Mutex`

### R06: `RateLimitTracker` — getOrPut + add not atomic
- **File**: `core/network/src/main/java/org/enchant/core/network/RateLimitTracker.kt:15`
- **Issue**: `getOrPut` lambda can be called twice, creating two lists and losing one
- **Fix**: Use `computeIfAbsent` or synchronize

### R07: `MessageNotifier` — read-modify-write not atomic
- **File**: `core/notifications/src/main/java/org/enchant/core/notifications/MessageNotifier.kt:37-47`
- **Issue**: Two threads calling `onMessageReceived` for same conversation can lose count increment
- **Fix**: Use `AtomicInteger` or synchronize

### R08: `OptimizedMessageNotifier` — TOCTOU race on flushJob
- **File**: `core/notifications/src/main/java/org/enchant/core/notifications/OptimizedMessageNotifier.kt:30-34`
- **Issue**: Two rapid calls can schedule two flushes
- **Fix**: Use `Mutex` or `AtomicBoolean`

### R09: `JobManager.handlers` — not synchronized
- **File**: `core/jobmanager/src/main/java/org/enchant/core/jobmanager/JobManager.kt:20`
- **Issue**: `mutableMapOf` accessed without synchronization
- **Fix**: Use `ConcurrentHashMap`

### R10: `DisappearingMessagesWorker` — check-and-set not atomic
- **File**: `core/jobmanager/src/main/java/org/enchant/core/jobmanager/DisappearingMessagesWorker.kt:17-20`
- **Issue**: Two threads can both pass interval check
- **Fix**: Use `AtomicLong` with compareAndSet

### R11: `MessageCache` — LinkedHashMap not thread-safe
- **File**: `core/performance/src/main/java/org/enchant/core/performance/MessageCache.kt:9-15`
- **Issue**: `LinkedHashMap` accessed from multiple threads
- **Fix**: Use `Collections.synchronizedMap` or `ConcurrentHashMap`

### R12: `PerformanceTracker.metrics` — unbounded growth
- **File**: `core/performance/src/main/java/org/enchant/core/performance/PerformanceTracker.kt:4`
- **Issue**: Map grows forever, never trimmed
- **Fix**: Use LRU cache with max size

### R13: `RemoteConfig.overrides` — not synchronized
- **File**: `core/config/src/main/java/org/enchant/core/config/RemoteConfig.kt:16`
- **Issue**: `mutableMapOf` accessed without synchronization
- **Fix**: Use `ConcurrentHashMap`

### R14: `ConversationViewModel._sendingState` — concurrent mutations
- **File**: `feature/chat/src/main/java/org/enchant/chat/ConversationViewModel.kt`
- **Issue**: `_sendingState` set from multiple concurrent `viewModelScope.launch` blocks
- **Fix**: Use `Mutex` or channel for serialization

### R15: `MessageSendPipeline.lastTypingTs/typingJob` — not synchronized
- **File**: `feature/chat/src/main/java/org/enchant/chat/data/MessageSendPipeline.kt:43-44`
- **Issue**: Plain `var`s accessed from multiple coroutines
- **Fix**: Use `Mutex` or `AtomicLong`

---

## HIGH — Broken Logic / Wrong Behavior

### L01: `ConversationViewModel` — uses conversationId as recipientUserId
- **File**: `feature/chat/src/main/java/org/enchant/chat/ConversationViewModel.kt:112,136,155,174,197,389`
- **Issue**: All send methods pass `conversationId` as `recipientUserId` — broken for group chats
- **Fix**: Resolve conversation to actual recipient(s)

### L02: `ConversationViewModel.resendMessage` — calls markMessageDeleted on success
- **File**: `feature/chat/src/main/java/org/enchant/chat/ConversationViewModel.kt:218`
- **Issue**: Should update status to SENT, not delete
- **Fix**: Call `updateMessageStatus` instead

### L03: `ConversationViewModel.jumpToDate` — ignores timestamp parameter
- **File**: `feature/chat/src/main/java/org/enchant/chat/ConversationViewModel.kt:340`
- **Issue**: Always scrolls to position 0
- **Fix**: Find message by timestamp and scroll to it

### L04: `ConversationRepository.insertMessageAndUpdateConversation` — raw SQL with toString() on nulls
- **File**: `feature/chat/src/main/java/org/enchant/chat/data/ConversationRepository.kt:103`
- **Issue**: `toString()` on nullable values produces `"null"` string instead of SQL NULL
- **Fix**: Use parameterized queries or proper null handling

### L05: `MediaService` — inconsistent encryption protocol
- **File**: `feature/chat/src/main/java/org/enchant/chat/data/MediaService.kt:155,188`
- **Issue**: `encryptAndUploadMedia` prepends IV to plaintext before encryption, `decryptAndDownloadMedia` expects IV prepended to ciphertext
- **Fix**: Consistent protocol: IV prepended to ciphertext

### L06: `MessageSendPipeline.editMessage` — never sends edited content
- **File**: `feature/chat/src/main/java/org/enchant/chat/data/MessageSendPipeline.kt:324`
- **Issue**: Sends PUT with only `new_envelope_id`, not actual edited content
- **Fix**: Include edited content in request body

### L07: `MessageSendPipeline.forwardMessage` — uses conversationId as recipientUserId
- **File**: `feature/chat/src/main/java/org/enchant/chat/data/MessageSendPipeline.kt:370`
- **Issue**: Semantically wrong for group conversations
- **Fix**: Accept explicit recipientUserId parameter

### L08: `GroupStateProcessor.updateLocalGroupToRevision` — uses myRole as revision
- **File**: `feature/groups/src/main/java/org/enchant/groups/GroupStateProcessor.kt:97`
- **Issue**: `current?.myRole` is a role string like "member", not a revision string
- **Fix**: Use `current?.revision` or proper revision tracking

### L09: `ContactSyncService.syncContacts` — sends comma-separated string instead of JSON array
- **File**: `feature/contacts/src/main/java/org/enchant/contacts/ContactSyncService.kt:39`
- **Issue**: Joins hashed numbers with comma, server likely expects JSON array
- **Fix**: Use `JsonArray` or `buildJsonArray`

### L10: `PhoneEntryScreen` — double "+" prefix on phone number
- **File**: `feature/auth/src/main/java/org/enchant/auth/screens/PhoneEntryScreen.kt:78-80`
- **Issue**: If phoneNumber already starts with "+", concatenates "+" again
- **Fix**: Check if already has "+" prefix

### L11: `MessageContextMenu` — canEdit/canDeleteForEveryone logic inverted
- **File**: `feature/chat/src/main/java/org/enchant/chat/components/MessageContextMenu.kt:32-33`
- **Issue**: `sentAt > twentyFourHoursAgo` means message is *newer* than 24h, but label says "can edit"
- **Fix**: Invert the comparison

### L12: `BackupViewModel.uploadChunk` — progress calculation wrong
- **File**: `feature/backup/src/main/java/org/enchant/backup/BackupViewModel.kt:64-78`
- **Issue**: `uploadProgress = (chunkIndex + 1).toFloat()` — chunk 5 → progress = 6.0 (>100%)
- **Fix**: `(chunkIndex + 1) / totalChunks.toFloat()`

### L13: `NotificationBuilder` — PendingIntent hashCode collision
- **File**: `core/notifications/src/main/java/org/enchant/core/notifications/NotificationBuilder.kt:108,126,166`
- **Issue**: `conversationId.hashCode()` as requestCode — collisions cause wrong PendingIntent reuse
- **Fix**: Use unique ID (e.g., incrementing counter or UUID)

### L14: `NotificationProfileHelper` — createProfile never writes keys
- **File**: `core/notifications/src/main/java/org/enchant/core/notifications/NotificationProfileHelper.kt:24-35`
- **Issue**: `createProfile` only increments counter, never writes `profile_${i}_start_h` keys that `isProfileActive` reads
- **Fix**: Actually write profile data to SecurePreferences

### L15: `JobManager` — restored job has empty lambda
- **File**: `core/jobmanager/src/main/java/org/enchant/core/jobmanager/JobManager.kt:44`
- **Issue**: Creates `Job(id, delayMs, tag, run = {})` — empty lambda, handler receives different instance
- **Fix**: Store and restore the original lambda or re-lookup handler

### L16: `MessageProtobufHelper.buildReceiptContent` — envelopeIds parsed as timestamps
- **File**: `feature/chat/src/main/java/org/enchant/chat/data/MessageProtobufHelper.kt:37`
- **Issue**: Converts `envelopeIds` (List<String>) to timestamps via `toLongOrNull() ?: 0L` — if not numeric, all become 0
- **Fix**: Pass actual timestamps or fix protobuf schema

### L17: `ConversationListViewModel.refresh` — loading state stuck on error
- **File**: `feature/chat-list/src/main/java/org/enchant/chatlist/ConversationListViewModel.kt:141`
- **Issue**: `_isRefreshing.value = false` outside try/catch — if exception thrown, loading stays true forever
- **Fix**: Move to finally block

### L18: `ChannelViewModel.loadMore` — silently discards errors
- **File**: `feature/channels/src/main/java/org/enchant/channels/ChannelViewModel.kt:127,130`
- **Issue**: `onFailure` doesn't set error state, `catch (_: Exception)` discards all errors
- **Fix**: Set error state and log

### L19: `StickerViewModel.sendSticker` — doesn't actually send
- **File**: `feature/stickers/src/main/java/org/enchant/stickers/StickerViewModel.kt:214-217`
- **Issue**: Only calls `recordStickerUse` and `loadRecent`, never posts to conversation
- **Fix**: Call message send pipeline

### L20: `MediaViewerScreen.saveToGallery` — uses Images.Media for all MIME types
- **File**: `feature/chat/src/main/java/org/enchant/chat/components/MediaViewerScreen.kt:166`
- **Issue**: Uses `MediaStore.Images.Media` for videos too
- **Fix**: Use `MediaStore.Video.Media` for video MIME types

---

## MEDIUM — Missing Error Handling

### E01: `KeyStoreManager.getOrCreateDatabaseKey` — NumberFormatException not caught
- **File**: `core/base/src/main/java/org/enchant/core/base/KeyStoreManager.kt:163`
- **Issue**: `raw.split(",").map { it.toInt().toByte() }` throws on invalid integer
- **Fix**: Wrap in try/catch

### E02: `WebSocketManager.handleFrame` — ACK not sent on parse failure
- **File**: `core/network/src/main/java/org/enchant/core/network/WebSocketManager.kt:296`
- **Issue**: If `Envelope.parseFrom` throws, ACK never sent → server re-sends indefinitely
- **Fix**: Send NACK or ACK even on parse failure

### E03: `OfflineQueue.drain` — exception stops entire drain loop
- **File**: `core/network/src/main/java/org/enchant/core/network/OfflineQueue.kt:102`
- **Issue**: If `WebSocketManager.requestRESTFallback` throws, remaining queued messages lost
- **Fix**: Wrap each message in try/catch, re-enqueue on failure

### E04: `KeyManager.uploadOpks` — result ignored
- **File**: `core/crypto/src/main/java/org/enchant/core/crypto/KeyManager.kt:265`
- **Issue**: If upload fails, OPKs stored locally but never uploaded
- **Fix**: Check result, retry on failure

### E05: `IncomingMessageProcessor.processUnidentifiedSender` — exceptions not logged
- **File**: `feature/chat/src/main/java/org/enchant/chat/data/IncomingMessageProcessor.kt:244`
- **Issue**: Catches exceptions but doesn't log them
- **Fix**: Add logging

### E06: `StickerViewModel.loadLibrary/loadRecent` — errors silently swallowed
- **File**: `feature/stickers/src/main/java/org/enchant/stickers/StickerViewModel.kt:180,199`
- **Issue**: `onFailure = {}` — no logging
- **Fix**: Log errors

### E07: `BackupArchive.verifyIntegrity` — silently swallows security errors
- **File**: `feature/backup/src/main/java/org/enchant/backup/archive/BackupArchive.kt:45`
- **Issue**: `catch (_: Exception)` swallows all errors including security-relevant ones
- **Fix**: Log and return false with reason

### E08: `PushTokenRegistrar` — silently swallows all errors
- **File**: `core/push/src/main/java/org/enchant/core/push/PushTokenRegistrar.kt:27-28,38-39,50-51`
- **Issue**: `catch (_: Exception) {}` — no logging, no retry
- **Fix**: Log errors, implement retry

### E09: `HuaweiPushFallback` — result of pending messages discarded
- **File**: `core/push/src/main/java/org/enchant/core/push/HuaweiPushFallback.kt:26-29`
- **Issue**: `apiClient.get("/v1/messages/pending")` — result discarded, no processing
- **Fix**: Process returned messages

### E10: `ImagePipeline` — no error listener on Coil requests
- **File**: `core/performance/src/main/java/org/enchant/core/performance/ImagePipeline.kt:48,59`
- **Issue**: No error handling on image load failures
- **Fix**: Add error listener

### E11: `MessageTrimmer` — no try/catch around DB operations
- **File**: `core/performance/src/main/java/org/enchant/core/performance/MessageTrimmer.kt:23-31,56-66`
- **Issue**: If DB is corrupted or closed, crashes
- **Fix**: Wrap in try/catch

### E12: `ConversationDao.search` — LIKE wildcards not escaped
- **File**: `core/database/src/main/java/org/enchant/core/database/dao/ConversationDao.kt:84-92`
- **Issue**: User input with `%` or `_` treated as wildcards
- **Fix**: Escape special characters

### E13: `MessageDao.searchMessages` — FTS syntax characters not escaped
- **File**: `core/database/src/main/java/org/enchant/core/database/dao/MessageDao.kt:161`
- **Issue**: Query with `"`, `+`, `-`, `NEAR` throws SQLite exception
- **Fix**: Escape or sanitize FTS query

---

## MEDIUM — Stub / Incomplete Functions

### S01: `SessionManager.loadSessionsFromDb` — no-op
- **File**: `core/crypto/src/main/java/org/enchant/core/crypto/SessionManager.kt:48-53`
- **Issue**: Only contains comment, sessions never loaded from DB
- **Fix**: Implement DB query and deserialization

### S02: `SodiumProvider.sodiumMlock/munlock/init` — no-ops
- **File**: `core/crypto/src/main/java/org/enchant/core/crypto/SodiumProvider.kt:6-21`
- **Issue**: Memory NOT locked — sensitive key material can be swapped to disk
- **Fix**: Bundle libsodium JNI or use Android Keystore for memory protection

### S03: `FcmFetchManager.onFcmReceived/scheduleFetch` — only toggle boolean
- **File**: `core/push/src/main/java/org/enchant/core/push/FcmFetchManager.kt:17-28`
- **Issue**: Never actually fetches messages from server
- **Fix**: Implement actual message fetch

### S04: `NotificationProfileHelper.createProfile` — only increments counter
- **File**: `core/notifications/src/main/java/org/enchant/core/notifications/NotificationProfileHelper.kt:24-35`
- **Issue**: Doesn't actually create notification profile
- **Fix**: Write profile data to SecurePreferences

### S05: `PermissionsScreen` — no actual permission request logic
- **File**: `feature/auth/src/main/java/org/enchant/auth/screens/PermissionsScreen.kt`
- **Issue**: PermissionCard is purely informational
- **Fix**: Implement actual permission requests

### S06: `ProfileSetupScreen` — avatar always null
- **File**: `feature/auth/src/main/java/org/enchant/auth/screens/ProfileSetupScreen.kt:58`
- **Issue**: No avatar picking functionality
- **Fix**: Implement image picker

### S07: `ContactProfileScreen` — hardcoded placeholder data
- **File**: `feature/contacts/src/main/java/org/enchant/contacts/screens/ContactProfileScreen.kt`
- **Issue**: Hardcoded "User", "@user_${userId.take(8)}", "No about text"
- **Fix**: Fetch actual profile data from API

### S08: `ContactProfileScreen/AddContactScreen/GroupInviteScreen` — back button does nothing
- **File**: Multiple screens
- **Issue**: `onClick = {}` — back button is non-functional
- **Fix**: Call `onBackPressedDispatcher.onBackPressed()` or navigate back

### S09: `CallLinkScreen` — edit name dialog is comment placeholder
- **File**: `feature/calls/src/main/java/org/enchant/calls/calllinks/CallLinkScreen.kt:96`
- **Issue**: `/* edit name dialog */` — does nothing
- **Fix**: Implement dialog

### S10: `ActiveVideoCallScreen` — remote/local video are Text placeholders
- **File**: `feature/calls/src/main/java/org/enchant/calls/screens/ActiveVideoCallScreen.kt`
- **Issue**: "Remote Video" and "You" text instead of actual video rendering
- **Fix**: Integrate WebRTC video renderer

### S11: `CallLinkManager.joinCallLink` — doesn't actually join
- **File**: `feature/calls/src/main/java/org/enchant/calls/calllinks/CallLinkManager.kt:83`
- **Issue**: Just calls `getCallLink` and returns it
- **Fix**: Implement actual join logic

### S12: `CreateGroupScreen` — member selection not implemented
- **File**: `feature/groups/src/main/java/org/enchant/groups/screens/CreateGroupScreen.kt`
- **Issue**: No actual contact picker
- **Fix**: Implement contact selection UI

### S13: `OtpVerifyScreen` — countdown race condition
- **File**: `feature/auth/src/main/java/org/enchant/auth/screens/OtpVerifyScreen.kt:71,131-132`
- **Issue**: Two concurrent countdown loops can run (LaunchedEffect + button click)
- **Fix**: Cancel previous job before starting new countdown

### S14: `KeyGenerationScreen` — onKeysGenerated called on every recomposition
- **File**: `feature/auth/src/main/java/org/enchant/auth/screens/KeyGenerationScreen.kt:20`
- **Issue**: `LaunchedEffect(Unit)` triggers on every recomposition when progress >= 1f
- **Fix**: Use a flag or `LaunchedEffect(progress)` with guard

### S15: `CountryCodePickerScreen` — duplicate country entries
- **File**: `feature/auth/src/main/java/org/enchant/auth/screens/CountryCodePickerScreen.kt`
- **Issue**: Multiple countries appear twice (US, UK, India, etc.)
- **Fix**: Deduplicate list

---

## LOW — Code Quality / Minor Issues

### Q01: `AppThemeManager.currentTheme` — no synchronization
- **File**: `core/base/src/main/java/org/enchant/core/base/AppTheme.kt`

### Q02: `AppConfig.init` — not synchronized despite @Volatile
- **File**: `core/base/src/main/java/org/enchant/core/base/AppConfig.kt:37`

### Q03: `KeyStoreManager.deleteKey` — silently swallows errors
- **File**: `core/base/src/main/java/org/enchant/core/base/KeyStoreManager.kt:90`

### Q04: `AuthInterceptor` — Thread.sleep blocks OkHttp dispatcher
- **File**: `core/network/src/main/java/org/enchant/core/network/AuthInterceptor.kt:61-63`

### Q05: `OfflineQueue.persistToDisk` — blocking SharedPreferences on arbitrary dispatcher
- **File**: `core/network/src/main/java/org/enchant/core/network/OfflineQueue.kt`

### Q06: `DoubleRatchet.serializeState` — allocates fixed 128KB ByteBuffer
- **File**: `core/crypto/src/main/java/org/enchant/core/crypto/DoubleRatchet.kt:269`

### Q07: `DoubleRatchet.deserializeState` — catches all exceptions, returns null
- **File**: `core/crypto/src/main/java/org/enchant/core/crypto/DoubleRatchet.kt:319`

### Q08: `StatusCacheDao.getFeed` — null timestamp → epoch 0
- **File**: `core/database/src/main/java/org/enchant/core/database/dao/StatusCacheDao.kt:20`

### Q09: `CallLogViewModel.confirmDeletion` — uses raw SQL instead of DAO
- **File**: `feature/calls/src/main/java/org/enchant/calls/CallLogViewModel.kt:97`

### Q10: `BackupSettingsScreen` — sends total_size: 0, total_chunks: 1
- **File**: `feature/settings/src/main/java/org/enchant/settings/screens/BackupSettingsScreen.kt:83`

### Q11: `AppLockScreen/TwoStepPinScreen` — sha256 defined inside @Composable
- **File**: `feature/auth/src/main/java/org/enchant/auth/screens/AppLockScreen.kt:43`

### Q12: `FriendRequestsScreen/BlockedUsersScreen/BackupSettingsScreen/JoinRequestsScreen` — creates ApiClient inside composable
- **File**: Multiple screens

### Q13: `TwoStepPinScreen` — creates new ApiClient instance inside composable
- **File**: `feature/auth/src/main/java/org/enchant/auth/screens/TwoStepPinScreen.kt:48`

### Q14: `AccessibilityDelegate` — SimpleDateFormat not thread-safe
- **File**: `core/accessibility/src/main/java/org/enchant/core/accessibility/AccessibilityDelegate.kt:8`

### Q15: `CallManager.retrieveTurnServers` — suspend function called without awaiting
- **File**: `core/calls/src/main/java/org/enchant/core/calls/CallManager.kt:76,97,259`

### Q16: `AudioRouter` — deprecated isWiredHeadsetOn API
- **File**: `core/calls/src/main/java/org/enchant/core/calls/AudioRouter.kt:76`

### Q17: `CallNotificationReceiver` — wraps non-suspend functions in scope.launch
- **File**: `core/calls/src/main/java/org/enchant/core/calls/CallNotificationReceiver.kt:16-18`

### Q18: `StickerPicker` — incorrect LazyVerticalGrid API usage
- **File**: `feature/stickers/src/main/java/org/enchant/stickers/StickerPicker.kt:72-95`

### Q19: `ChannelSearchScreen/ChannelFeedScreen` — infinite loop risk on LaunchedEffect
- **File**: `feature/channels/src/main/java/org/enchant/channels/screens/ChannelSearchScreen.kt:28-33`

### Q20: `ChannelFeedScreen` — back button does nothing
- **File**: `feature/channels/src/main/java/org/enchant/channels/screens/ChannelFeedScreen.kt:43`

### Q21: `ProfileScreen` — back button does nothing, readOnly fields should be Text
- **File**: `feature/profile/src/main/java/org/enchant/profile/screens/ProfileScreen.kt:36,157-179`

### Q22: `MainActivity` — navigation state bug on rapid call state changes
- **File**: `app/src/main/java/org/enchant/MainActivity.kt:156-179`

### Q23: `MainActivity` — duplicate LaunchedEffect on callUiState
- **File**: `app/src/main/java/org/enchant/MainActivity.kt:156,181`

### Q24: `MainActivity` — hardcoded DND and chat settings
- **File**: `app/src/main/java/org/enchant/MainActivity.kt:545-585`

### Q25: `ConversationListScreen.showSearch` — reads after setting, always inverted
- **File**: `feature/chat-list/src/main/java/org/enchant/chatlist/ConversationListScreen.kt:96`

### Q26: `CallManager.apiClient` — crashes with error() if never set
- **File**: `core/calls/src/main/java/org/enchant/core/calls/CallManager.kt:50`

### Q27: `DI.kt` — fallback database key is insecure
- **File**: `app/src/main/java/org/enchant/DI.kt:96-97`

### Q28: `EnchantApp` — DI initialized asynchronously, race with other components
- **File**: `app/src/main/java/org/enchant/EnchantApp.kt:25-27`

### Q29: `ConversationScreen.cameraUri` — force unwraps nullable
- **File**: `feature/chat/src/main/java/org/enchant/chat/ConversationScreen.kt:119`

### Q30: `ConversationScreen` — ReplyPreview type mismatch
- **File**: `feature/chat/src/main/java/org/enchant/chat/ConversationScreen.kt:185,235`

### Q31: `ConversationViewModel.jumpToMessage` — toInt() overflow
- **File**: `feature/chat/src/main/java/org/enchant/chat/ConversationViewModel.kt:336`

### Q32: `IncomingMessageProcessor` — receipt handling doesn't save to DB
- **File**: `feature/chat/src/main/java/org/enchant/chat/data/IncomingMessageProcessor.kt:202-211`

### Q33: `IncomingMessageProcessor` — delete message type does nothing
- **File**: `feature/chat/src/main/java/org/enchant/chat/data/IncomingMessageProcessor.kt:216-218`

### Q34: `IncomingMessageProcessor` — typing handler returns Handled but doesn't emit
- **File**: `feature/chat/src/main/java/org/enchant/chat/data/IncomingMessageProcessor.kt:213`

### Q35: `ContentPreProcessor.detectUrls` — creates duplicate URL entries
- **File**: `feature/chat/src/main/java/org/enchant/chat/data/ContentPreProcessor.kt:41`

### Q36: `ContentPreProcessor.applyFormatting` — doesn't apply parsed formatting spans
- **File**: `feature/chat/src/main/java/org/enchant/chat/data/ContentPreProcessor.kt:98`

### Q37: `CallLogViewModel` — uses pool?.writer which could be null
- **File**: `feature/calls/src/main/java/org/enchant/calls/CallLogViewModel.kt:98`

### Q38: `OutgoingCallScreen` — hardcoded 45-second auto-end timeout
- **File**: `feature/calls/src/main/java/org/enchant/calls/screens/OutgoingCallScreen.kt:35`

### Q39: `IncomingCallScreen` — hardcoded 30-second auto-decline timeout
- **File**: `feature/channels/src/main/java/org/enchant/calls/screens/IncomingCallScreen.kt:33`

### Q40: `GroupCallScreen` — dropdown has no anchor, appears at wrong position
- **File**: `feature/groups/src/main/java/org/enchant/groups/screens/GroupCallScreen.kt:98`

### Q41: `GroupEditor.executeWithRetry` — lastError initialized to generic failure
- **File**: `feature/groups/src/main/java/org/enchant/groups/GroupEditor.kt:163`

### Q42: `PollViewModel` — no outer try/catch around API calls
- **File**: `feature/polls/src/main/java/org/enchant/polls/PollViewModel.kt`

### Q43: `LocationPickerScreen` — missing permission check before isProviderEnabled
- **File**: `feature/location/src/main/java/org/enchant/location/LocationPickerScreen.kt:50-51`

### Q44: `LocationPickerScreen` — requestSingleUpdate listener never unregistered
- **File**: `feature/location/src/main/java/org/enchant/location/LocationPickerScreen.kt:69`

### Q45: `ShareTargetActivity` — no try/catch around MessageSendPipeline calls
- **File**: `feature/share/src/main/java/org/enchant/share/ShareTargetActivity.kt:38-55`

### Q46: `ConversationChooserTargetService` — stale data, only reads last conversation
- **File**: `feature/share/src/main/java/org/enchant/share/ConversationChooserTargetService.kt:20`

### Q47: `formTimestamp` — two separate Calendar.getInstance() calls
- **File**: `feature/calls/src/main/java/org/enchant/calls/screens/CallLogScreen.kt:180-182`

### Q48: `ActiveVideoCallScreen` — PiP drag offsets tracked but never applied
- **File**: `feature/calls/src/main/java/org/enchant/calls/screens/ActiveVideoCallScreen.kt`

### Q49: `SettingsViewModel.deleteAccount` — doesn't navigate or clear session
- **File**: `feature/settings/src/main/java/org/enchant/settings/SettingsViewModel.kt:251`

### Q50: `ContactsViewModel` — uses DatabasePool.instance!! which could crash
- **File**: `feature/contacts/src/main/java/org/enchant/contacts/ContactsViewModel.kt:28`

### Q51: `GroupsViewModel` — uses DatabasePool.instance!! which could crash
- **File**: `feature/groups/src/main/java/org/enchant/groups/GroupsViewModel.kt:31`

### Q52: `SettingsViewModel` — creates ApiClient in secondary constructor
- **File**: `feature/settings/src/main/java/org/enchant/settings/SettingsViewModel.kt:58-59`

### Q53: `BackupViewModel` — no constructor params, uses ApiClient.getInstance()
- **File**: `feature/backup/src/main/java/org/enchant/backup/BackupViewModel.kt`

### Q54: `ChatArchiveExporter` — cursor not closed on exception
- **File**: `feature/backup/src/main/java/org/enchant/backup/archive/ChatArchiveExporter.kt:33`

### Q55: `GroupArchiveExporter` — cursor not closed on exception
- **File**: `feature/backup/src/main/java/org/enchant/backup/archive/GroupArchiveExporter.kt`

### Q56: `ContactArchiveExporter` — cursor not closed on exception
- **File**: `feature/backup/src/main/java/org/enchant/backup/archive/ContactArchiveExporter.kt`

### Q57: `AdHocCallArchiveExporter` — cursor not closed on exception
- **File**: `feature/backup/src/main/java/org/enchant/backup/archive/AdHocCallArchiveExporter.kt`

### Q58: `BackupArchive.decryptSection` — no try/catch
- **File**: `feature/backup/src/main/java/org/enchant/backup/archive/BackupArchive.kt:17-22`

### Q59: `BackupExporter.importFullBackup` — no partial rollback on failure
- **File**: `feature/backup/src/main/java/org/enchant/backup/BackupExporter.kt`

### Q60: `StickerViewModel.installPack/uninstallPack` — race on async load
- **File**: `feature/stickers/src/main/java/org/enchant/stickers/StickerViewModel.kt:128-152`

### Q61: `ChannelViewModel.subscribe/unsubscribe` — lost updates on concurrent load
- **File**: `feature/channels/src/main/java/org/enchant/channels/ChannelViewModel.kt:140-161`

### Q62: `StatusViewerScreen` — progress/timer race with tap navigation
- **File**: `feature/status/src/main/java/org/enchant/status/screens/StatusViewerScreen.kt:35-48`

### Q63: `OtpVerifyScreen` — BroadcastReceiver registered even if SmsRetriever throws
- **File**: `feature/auth/src/main/java/org/enchant/auth/screens/OtpVerifyScreen.kt`

### Q64: `AppLockScreen` — Change PIN doesn't verify old PIN
- **File**: `feature/auth/src/main/java/org/enchant/auth/screens/AppLockScreen.kt:197-204`

### Q65: `PhoneEntryScreen` — hardcoded "+1" US default
- **File**: `feature/auth/src/main/java/org/enchant/auth/screens/PhoneEntryScreen.kt:24`

---

## FILES WITH NO TEST COVERAGE

### Core modules (16 files)
- `CoroutineDispatchers.kt`
- `AuthInterceptor.kt`
- `WebSocketService.kt`
- `ApiModels.kt`
- `RateLimitTracker.kt`
- `PreKeyWorker.kt`
- `CursorMapper.kt`
- `DatabaseNotifier.kt`
- `GroupMemberDao.kt`
- `GroupDao.kt`
- `InstalledStickerDao.kt`
- `StickerPackDao.kt`
- `StatusCacheDao.kt`
- `CallLogDao.kt`
- `ProfileCacheDao.kt`
- `MediaCacheDao.kt`
- `KeyMaterialDao.kt`
- `RecipientDao.kt`
- `IdentityDao.kt`
- `AudioRouter.kt`
- `ActiveCallManager.kt`
- `WebRtcService.kt`
- `CallForegroundService.kt`
- `CallNotificationReceiver.kt`
- `NotificationReplyReceiver.kt`
- `MessageNotifier.kt`
- `NotificationBuilder.kt`
- `OptimizedMessageNotifier.kt`
- `NotificationProfileHelper.kt`
- `FcmFetchForegroundService.kt`
- `FcmFetchManager.kt`
- `PushTokenRegistrar.kt`
- `HuaweiPushFallback.kt`
- `FcmReceiveService.kt`
- `AccessibilityDelegate.kt`
- `Accessibility.kt`
- `MessageTrimmer.kt`
- `PerformanceTracker.kt`
- `DomainModels.kt`
- `SignalStore.kt`
- `RemoteConfig.kt`

### Feature modules (all 76 main source files have inadequate or no tests)
- Every screen, ViewModel, repository, and utility in feature/ modules

### App module (4 files)
- `DI.kt`
- `EnchantApp.kt`
- `MainActivity.kt`
- `BootReceiver.kt`

---

## TOTALS

| Category | Count |
|----------|-------|
| Critical crash bugs | 12 |
| Memory leaks | 14 |
| Race conditions | 15 |
| Broken logic / wrong behavior | 20 |
| Missing error handling | 13 |
| Stub / incomplete functions | 15 |
| Code quality / minor issues | 65 |
| **Total bugs found** | **154** |
| Files with no test coverage | 45+ |
