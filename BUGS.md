# Enchant Native — Bug Audit Report

> **Generated:** 2026-05-18
> **Auditor:** Codebase-wide analysis of all Kotlin source files
> **Total bugs found:** 40 (excluding 1 verified-correct)

---

## Priority Legend

| Icon | Meaning |
|------|---------|
| 🔴 P0 | Ship-stopping — must fix before any release |
| 🟠 P1 | High — security or major feature gap |
| 🟡 P2 | Medium — important but not blocking |

---

## Fix Execution Log

| # | Issue | Status | Date |
|---|-------|--------|------|
| 1 | SessionManager.init() called with no DAOs | ✅ Fixed 2026-05-18 | |
| 2 | selfUserId defaults to "self" before auth | ✅ Fixed 2026-05-18 | |
| 3 | "self" check always false, unread count wrong | ✅ Fixed 2026-05-18 | |
| 4 | OfflineQueue SecurePreferences overflow risk | ✅ Fixed 2026-05-18 | |
| 5 | editMessage: conversationId passed as recipientUserId | ✅ Fixed 2026-05-18 | |
| 6 | deleteForEveryone: conversationId passed as recipientUserId | ⏳ Pending | |
| 7 | WebSocket retryCount never resets on success | ⏳ Pending | |
| 8 | cleanup() cancels call log insertion | ⏳ Pending | |
| 9 | ICE candidates buffered but never sent | ⏳ Pending | |
| 10 | JSON injection in refresh token | ⏳ Pending | |
| 11 | Double base64 encoding in sealed sender | ⏳ Pending | |
| 12 | Redundant key bundle fetch in prekey processing | ⏳ Pending | |
| 13 | Cursor management in reactive Flow | ⏳ Pending | |
| 14 | FTS5 MATCH query not sanitized | ⏳ Pending | |
| 15 | OTP cooldown not persisted across restarts | ⏳ Pending | |
| 16 | needsKeyRotation() always returns false initially | ⏳ Pending | |
| 17 | disconnect() doesn't cancel pending requests | ⏳ Pending | |
| 18 | acceptCall silently fails if state changed | ⏳ Pending | |
| 19 | Duplicate LaunchedEffect for call state | ⏳ Pending | |
| 20 | Temporary ApiClient created and leaked | ⏳ Pending | |
| 21 | Location sent as emoji text, not structured | ⏳ Pending | |
| 22 | Sticker sent as emoji text, not structured | ⏳ Pending | |
| 23 | vCard created but never sent | ⏳ Pending | |
| 24 | scheduleMessage/cancel ID format mismatch | ⏳ Pending | |
| 25 | Delivery receipt uses timestamp as envelope ID | ⏳ Pending | |
| 26 | Read receipt uses timestamp as envelope ID | ⏳ Pending | |
| 27 | Sealed sender decoded as JSON, not protobuf | ⏳ Pending | |
| 28 | Unlimited reader connections via ThreadLocal | ⏳ Pending | |
| 29 | X3DH DH key material not fully zeroed | ⏳ Pending | |
| 30 | consumedKeys strings not zeroed | ⏳ Pending | |
| 31 | logout/deleteAccount always return success | ⏳ Pending | |
| 32 | REST fallback missing recipientDeviceId | ⏳ Pending | |
| 33 | removeParticipant uses remoteUserId as groupId | ⏳ Pending | |
| 34 | saveKeyPair alias prefix | ✅ Verified correct | |
| 35 | getPinnedMessages queries is_starred not is_pinned | ⏳ Pending | |
| 36 | sender_ts uses ISO-8601 instead of milliseconds | ⏳ Pending | |
| 37 | searchUsername result discarded in UI | ⏳ Pending | |
| 38 | PendingIntent hash collision risk | ⏳ Pending | |
| 39 | No auth check before starting WebSocketService on boot | ⏳ Pending | |
| 40 | getString silently returns default when uninitialized | ⏳ Pending | |

---

## 🔴 P0 — Critical Bugs

### Bug #1: SessionManager.init() called with no DAOs
**File:** `app/src/main/java/org/enchant/DI.kt:130`
**Impact:** Sessions are never persisted to disk. All crypto sessions are lost on app restart, requiring full re-handshake with every contact.
**Fix:** Pass `sessionDao` and `identityDao` to `SessionManager.init()`.

### Bug #2: selfUserId defaults to "self" before auth
**File:** `core/crypto/src/main/java/org/enchant/core/crypto/SessionManager.kt:57`
**Impact:** If encryption is attempted before `init()` runs, session keys are computed with wrong user ID, causing decryption failures.
**Fix:** Ensure `selfUserId` is set before any encryption operations, or fail fast if not initialized.

### Bug #3: "self" check always false, unread count wrong
**File:** `feature/chat/src/main/java/org/enchant/chat/data/ConversationRepository.kt:113`
**Impact:** Outgoing messages incorrectly increment unread count. Users see unread badges on their own sent messages.
**Fix:** Compare `senderId` against the actual self user ID from SecurePreferences, not the hardcoded string "self".

### Bug #4: OfflineQueue SecurePreferences overflow risk
**File:** `core/network/src/main/java/org/enchant/core/network/OfflineQueue.kt:57-71`
**Impact:** Large encrypted payloads stored in SharedPreferences can exceed storage limits (~1MB total), causing crashes or data loss.
**Fix:** Store queue in a file-based format or limit payload size before persisting.

### Bug #5: editMessage passes conversationId as recipientUserId
**File:** `feature/chat/src/main/java/org/enchant/chat/ConversationViewModel.kt:252`
**Impact:** In group chats, edit messages are encrypted to the wrong recipient (group ID instead of actual user), causing decryption failures.
**Fix:** Pass `recipientUserId` instead of `conversationId` from the ViewModel.

### Bug #6: deleteForEveryone passes conversationId as recipientUserId
**File:** `feature/chat/src/main/java/org/enchant/chat/ConversationViewModel.kt:242`
**Impact:** Same as #5 — delete signals encrypted to wrong recipient in group chats.
**Fix:** Pass `recipientUserId` instead of `conversationId`.

### Bug #7: WebSocket retryCount never resets on successful connection
**File:** `core/network/src/main/java/org/enchant/core/network/WebSocketManager.kt:355-361`
**Impact:** After network recovery, reconnection delay stays at 30 seconds instead of resetting to 1 second.
**Fix:** Reset `retryCount = 0` in `onOpen()` callback.

### Bug #8: cleanup() cancels call log insertion
**File:** `core/calls/src/main/java/org/enchant/core/calls/CallManager.kt:504`
**Impact:** Call logs are never written to database when a call ends because the insertion coroutine is cancelled.
**Fix:** Use a separate scope for call log insertion, or insert synchronously before cleanup.

### Bug #9: ICE candidates buffered but never sent
**File:** `core/calls/src/main/java/org/enchant/core/calls/CallManager.kt:439-440`
**Impact:** ICE candidates are collected locally but never transmitted to the remote peer. WebRTC connection will fail to establish.
**Fix:** Send ICE candidates via WebSocket in the `onIceCandidate` callback.

### Bug #10: JSON injection in refresh token
**File:** `core/network/src/main/java/org/enchant/core/network/AuthInterceptor.kt:83`
**Impact:** Malformed refresh tokens can break JSON parsing or enable injection attacks.
**Fix:** Use proper JSON serialization (buildJsonObject) instead of string interpolation.

---

## 🟠 P1 — High Priority Bugs

### Bug #11: Double base64 encoding in sealed sender
**File:** `feature/chat/src/main/java/org/enchant/chat/data/MessageSendPipeline.kt:176-177`
**Impact:** Server cannot decode double-encoded payload. Sealed sender messages fail.
**Fix:** Remove the second base64 encoding layer.

### Bug #12: Redundant key bundle fetch in prekey processing
**File:** `feature/chat/src/main/java/org/enchant/chat/data/IncomingMessageProcessor.kt:125-128`
**Impact:** Wasted network round-trip. The fetched key bundle is never used.
**Fix:** Remove the unused `fetchKeyBundle()` call or use its result.

### Bug #13: Cursor management in reactive Flow
**File:** `core/database/src/main/java/org/enchant/core/database/dao/ConversationDao.kt:36-54`
**Impact:** Multiple cursors can accumulate if Flow collector is slow, causing memory leaks.
**Fix:** Use `cursor.use {}` inside the readWith block and emit the list.

### Bug #14: FTS5 MATCH query not sanitized
**File:** `core/database/src/main/java/org/enchant/core/database/dao/MessageDao.kt:153-158`
**Impact:** User searches containing FTS5 special characters (`"`, `*`, `NEAR`, `AND`, `OR`, `NOT`) cause SQL syntax errors.
**Fix:** Wrap query with FTS5 quote function or escape special characters.

### Bug #15: OTP cooldown not persisted across restarts
**File:** `core/auth/src/main/java/org/enchant/core/auth/AuthManager.kt:138-139`
**Impact:** Users can bypass the 30-second OTP cooldown by killing and restarting the app.
**Fix:** Store `lastOtpRequestMs` in SecurePreferences.

### Bug #16: needsKeyRotation() always returns false initially
**File:** `core/crypto/src/main/java/org/enchant/core/crypto/KeyManager.kt:353-356`
**Impact:** Signed prekeys are never rotated because the function returns false when `lastSpkRotationMs == 0`.
**Fix:** Return true when `lastSpkRotationMs == 0` (needs initial rotation).

### Bug #17: disconnect() doesn't cancel pending requests
**File:** `core/network/src/main/java/org/enchant/core/network/WebSocketManager.kt:155-160`
**Impact:** In-flight message sends hang for 10 seconds after disconnect.
**Fix:** Cancel all CompletableDeferreds in `pendingRequests` on disconnect.

### Bug #18: acceptCall silently fails if state changed
**File:** `feature/calls/src/main/java/org/enchant/calls/CallViewModel.kt:52-55`
**Impact:** User taps accept on a call that already timed out — no error feedback shown.
**Fix:** Show error toast/snackbar when acceptCall fails due to state mismatch.

### Bug #19: Duplicate LaunchedEffect for call state
**File:** `app/src/main/java/org/enchant/MainActivity.kt:156-191`
**Impact:** Rapid call state changes can push multiple screens onto the back stack.
**Fix:** Combine into a single LaunchedEffect or add navigation guards.

### Bug #20: Temporary ApiClient created and leaked
**File:** `core/auth/src/main/java/org/enchant/core/auth/AuthStateMachine.kt:171-175`
**Impact:** Creates a separate OkHttpClient instance that's never reused or cleaned up.
**Fix:** Use the shared ApiClient instance or pass it as a required parameter.

---

## 🟡 P2 — Medium Priority Bugs

### Bug #21: Location sent as emoji text
**File:** `feature/chat/src/main/java/org/enchant/chat/ConversationViewModel.kt:184`
**Impact:** Receiver cannot parse location to display on a map.

### Bug #22: Sticker sent as emoji text
**File:** `feature/chat/src/main/java/org/enchant/chat/ConversationViewModel.kt:209`
**Impact:** Receiver cannot render sticker — sees raw text instead.

### Bug #23: vCard created but never sent
**File:** `feature/chat/src/main/java/org/enchant/chat/ConversationViewModel.kt:414-415`
**Impact:** Contact card sharing sends only a text placeholder, not the actual vCard.

### Bug #24: scheduleMessage/cancel ID format mismatch
**File:** `feature/chat/src/main/java/org/enchant/chat/ConversationViewModel.kt:371, 386`
**Impact:** Scheduled messages can never be cancelled.

### Bug #25: Delivery receipt uses timestamp as envelope ID
**File:** `feature/chat/src/main/java/org/enchant/chat/data/MessageSendPipeline.kt:270-271`
**Impact:** Sender never receives proper delivery confirmations.

### Bug #26: Read receipt uses timestamp as envelope ID
**File:** `feature/chat/src/main/java/org/enchant/chat/data/MessageSendPipeline.kt:288-289`
**Impact:** Same as #25 — read confirmations fail.

### Bug #27: Sealed sender decoded as JSON, not protobuf
**File:** `feature/chat/src/main/java/org/enchant/chat/data/IncomingMessageProcessor.kt:254`
**Impact:** Real sealed sender messages fail to parse.

### Bug #28: Unlimited reader connections via ThreadLocal
**File:** `core/database/src/main/java/org/enchant/core/database/AppDatabase.kt:62-63`
**Impact:** Can exhaust SQLite connection pool under heavy concurrent reads.

### Bug #29: X3DH DH key material not fully zeroed
**File:** `core/crypto/src/main/java/org/enchant/core/crypto/X3DH.kt:46-51`
**Impact:** DH shared secrets remain in memory after use.

### Bug #30: consumedKeys strings not zeroed
**File:** `core/crypto/src/main/java/org/enchant/core/crypto/DoubleRatchet.kt:41-52`
**Impact:** Minor — strings can't be zeroed in JVM, but set should be cleared.

### Bug #31: logout/deleteAccount always return success
**File:** `core/auth/src/main/java/org/enchant/core/auth/AuthRepository.kt:82-89, 116-123`
**Impact:** Caller cannot distinguish success from network failure.

### Bug #32: REST fallback missing recipientDeviceId
**File:** `core/network/src/main/java/org/enchant/core/network/WebSocketManager.kt:251-262`
**Impact:** Server cannot route message to correct device.

### Bug #33: removeParticipant uses remoteUserId as groupId
**File:** `core/calls/src/main/java/org/enchant/core/calls/CallManager.kt:429-434`
**Impact:** Removes participant from wrong group.

### Bug #34: saveKeyPair alias prefix
**File:** `core/crypto/src/main/java/org/enchant/core/crypto/KeyManager.kt:108-115`
**Status:** ✅ Verified correct — no bug.

### Bug #35: getPinnedMessages queries is_starred not is_pinned
**File:** `feature/chat/src/main/java/org/enchant/chat/data/ConversationRepository.kt:294-298`
**Impact:** Returns starred messages instead of pinned messages.

### Bug #36: sender_ts uses ISO-8601 instead of milliseconds
**File:** `feature/chat/src/main/java/org/enchant/chat/data/MessageSendPipeline.kt:122`
**Impact:** Server may fail to parse timestamp.

### Bug #37: searchUsername result discarded in UI
**File:** `app/src/main/java/org/enchant/MainActivity.kt:362-368`
**Impact:** Username availability check result is computed but never displayed.

### Bug #38: PendingIntent hash collision risk
**File:** `core/notifications/src/main/java/org/enchant/core/notifications/NotificationBuilder.kt:107-108`
**Impact:** Notification actions can overwrite each other on hash collision.

### Bug #39: No auth check before starting WebSocketService on boot
**File:** `app/src/main/java/org/enchant/BootReceiver.kt:9-22`
**Impact:** WebSocketService runs indefinitely showing "Connecting..." for unauthenticated users.

### Bug #40: getString silently returns default when uninitialized
**File:** `core/base/src/main/java/org/enchant/core/base/SecurePreferences.kt:37-39`
**Impact:** Masks initialization failures, making debugging harder.
