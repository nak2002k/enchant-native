# Phase 3 — Core Chat

## Overview

Build the complete messaging experience: conversation list, chat screen, E2EE message send/receive pipeline, media upload/download, reactions, disappearing messages, search, pinned messages, editing, deletion, forwarding.

**Architecture pattern:** MVVM with ViewModel + Repository + Paging data source + dedicated message send pipeline + dedicated incoming message processor

**Estimated files:** 40 files
**Backend endpoints:** MRS (8003/8004 WS + REST), Media (8005), Reactions (8012), Disappear (8014), Chats (8011)
**Prerequisites:** Phase 1 (crypto, network, database) + Phase 2 (auth)

---

## Backend API Contracts

### WebSocket Protocol (MRS Port 8003)
**Connection:** `ws://<host>:8003/` — binary protobuf frames
**Auth:** First message is POST /v1/auth with raw JWT bytes. Server responds 200 "Authenticated" or 401.
**Max message size:** 2MB
**Timers:** Auth timeout 10s, idle timeout 90s, ping interval 30s, pending request timeout 30s
**Close codes:** 4001 (auth failure), 4002 (auth timeout), 4003 (idle), 4409 (displaced), 4004 (protocol error)

**Frame flow after auth:**
```
Client → Server: POST /api/v1/message (protobuf Envelope in body)
Server → Client: 200 OK with envelope_id

Server → Client: PUT /api/v1/message (incoming envelope as body)
Client → Server: 200 OK (ack)
Client → Server: DELIVERY_RECEIPT frame to original sender

Client → Server: TYPING_START / TYPING_STOP (ephemeral, no persistence)
Server → Client: (no response — ephemeral)

Client → Server: GET /v1/keepalive
Server → Client: 200 OK
```

**Envelope protobuf fields (matching backend — 13 fields):**
| Field # | Type | Name | Required | Description |
|---------|------|------|----------|-------------|
| 1 | string | `envelope_id` | Optional (server-populated) | Unique UUID assigned by server on send; used for delivery tracking and ack |
| 2 | string | `sender_user_id` | Optional (server-populated) | Sender's user UUID. For SEALED_SENDER, this is empty |
| 3 | string | `sender_device_id` | Optional (server-populated) | Sender's device UUID |
| 4 | string | `recipient_user_id` | **Required** | Recipient's user UUID — determines who receives the message |
| 5 | string | `recipient_device_id` | Optional | Specific device UUID. Empty string = fan-out to all recipient's devices |
| 6 | string | `message_type` | **Required** | One of: `SIGNAL_MESSAGE`, `PREKEY_MESSAGE`, `CALL_OFFER`, `CALL_ANSWER`, `CALL_ICE`, `CALL_END`, `DELIVERY_RECEIPT`, `READ_RECEIPT`, `TYPING_START`, `TYPING_STOP`, `KEY_EXCHANGE`, `MLS_COMMIT`, `MLS_WELCOME` |
| 7 | bytes | `payload` | **Required** | Encrypted message content (server never inspects — E2EE opaque blob) |
| 8 | uint64 | `server_ts` | Optional (server-populated) | Server-assigned Unix timestamp |
| 9 | string | `sender_ts` | Optional | Client-side timestamp (ISO 8601) |
| 10 | bool | `sealed` | Optional | Sealed sender flag: true means sender identity is hidden; sender_user_id will be empty |
| 11 | string | `reply_token` | Optional | For sealed sender — enables delivery receipt routing without server knowing sender |
| 12 | bool | `ephemeral` | Optional | True = online-only delivery, no persistence (used for typing indicators, receipts) |
| 13 | bool | `urgent` | Optional | Urgency flag — may affect push notification behavior |

**Sending rules:**
- Client must set: `recipient_user_id` (required), `message_type` (required), `payload` (required), `sender_ts` (recommended)
- Client may set: `recipient_device_id` (for targeted delivery), `ephemeral` (for typing/receipts), `sealed` + `reply_token` (for sealed sender), `urgent`
- Server populates: `envelope_id`, `sender_user_id`, `sender_device_id`, `server_ts`
- Server relays the envelope to recipient as-is, replacing `sender_user_id` and `sender_device_id` with the authenticated sender

### REST: POST /v1/messages/send (Fallback)
**Auth:** JWT required
**Rate limit:** 200/min per device
**Request:** `{"recipient_user_id": "uuid", "recipient_device_id": "uuid?", "message_type": "string", "payload": "string", "sender_ts": "string?"}`
**message_type must be one of:** `SIGNAL_MESSAGE`, `PREKEY_MESSAGE`, `CALL_OFFER`, `CALL_ANSWER`, `CALL_ICE`, `CALL_END`, `DELIVERY_RECEIPT`, `READ_RECEIPT`, `TYPING_START`, `TYPING_STOP`, `KEY_EXCHANGE`, `MLS_COMMIT`, `MLS_WELCOME`
**Payload max:** 64KB for most, 2MB for KEY_EXCHANGE/MLS_COMMIT/MLS_WELCOME
**Response 200:** `{"envelope_ids": ["uuid1", "uuid2", ...]}`
**Errors:** 400 (missing fields, invalid message_type), 413 (payload too large), 429

### REST: POST /v1/messages/sealed-send
**Auth:** None (anonymous)
**Rate limit:** 100/min per IP
**Same as send but:** `sender_user_id` is NULL, `sealed: true` flag, sender identity encrypted IN payload
**May include:** `reply_token: "uuid"` for anonymous delivery receipt routing

### REST: GET /v1/messages/pending
**Auth:** JWT required
**Response:** `{"messages": [binary-envelope-bytes]}` — max 100 messages
**Notes:** Used as REST fallback when WS unavailable. Messages deleted after delivery.

### REST: POST /v1/media/upload
**Auth:** JWT required
**Rate limit:** 200/day per device, 128MB max body
**Request:** Raw binary body. Optional `X-Mime-Type-Hint`, `X-Media-Size` headers.
**Response 201:** `{"media_id": "uuid", "download_url": "...", "expires_ts": 1234567890}`
**Errors:** 400 (size mismatch, invalid header), 413 (too large), 429

### REST: GET /v1/media/{media_id}
**Auth:** JWT required
**Response:** Raw binary body. Header: `X-SHA256-Ciphertext: base64url`
**Errors:** 400 (invalid media_id), 404 (not found or expired)

### REST: DELETE /v1/media/{media_id}
**Auth:** JWT required
**Response 200:** `{"deleted": true}`
**Errors:** 401, 404

### REST: PUT /v1/reactions/{message_id}
**Auth:** JWT required
**Request:** `{"emoji": "😊"}`
**Response 200:** `{"reacted": true}`
**Notes:** Add or change reaction. Same emoji by same user → remove (toggle behavior).

### REST: DELETE /v1/reactions/{message_id}
**Auth:** JWT required
**Response 200:** `{"deleted": true}`

### REST: GET /v1/reactions/{message_id}
**Auth:** JWT required
**Response 200:** `{"message_id": "uuid", "reactions": {"😊": {"count": 3, "reactors": [{"user_id": "uuid"}]}}, "reacted_by_me": ["😊"]}`

### REST: PUT /v1/disappear/{conversation_id}
**Auth:** JWT required
**Rate limit:** 10/day per device
**Request:** `{"timer_seconds": 0|86400|604800|7776000, "timer_mode": "FROM_SEND"|"FROM_VIEW"}`
**Notes:** timer_seconds = 0 disables. `FROM_SEND` = message disappears X seconds after send. `FROM_VIEW` = X seconds after first view.

### REST: PUT /v1/messages/{envelope_id}
**Auth:** JWT required
**Request:** `{"new_envelope_id": "uuid"}`
**Notes:** E2EE — server never sees edit content. Max 2 edits per message. Only original sender can edit.

### REST: GET /v1/search/messages?q=&chat_id=&from=&to=&limit=
**Auth:** JWT required
**Response 200:** `{"results": [{"envelope_id": "uuid", "sender": "uuid", "ts": "iso8601", "preview": ""}], "count": N}`
**Notes:** Metadata-only. Server cannot search message content (E2EE). Full-text search must be client-side on decrypted messages.

---

## E2EE Message Contract (Client-Side)

### Outgoing Message Flow
```
1. Check session exists for recipient → if not, establish via X3DH:
   a. GET /v1/keys/bundle/{recipient_user_id}
   b. Parse response: device_id, identity_key, signed_prekey, one_time_prekey (optional)
   c. X3DH.aliceInitiate(ourIK, ourEK, bobIK, bobSPK, bobOPK?) → shared secret
   d. DoubleRatchet.initializeAlice(sharedSecret, bobSPK) → session state
   e. Store session in database

2. Ratchet encrypt plaintext:
   a. DoubleRatchet.encrypt(state, plaintext) → RatchetMessage
   b. Serialize RatchetMessage header + ciphertext → payload bytes

3. Send via WebSocket:
   a. POST /api/v1/message with envelope (recipient, payload, type=PREKEY_MESSAGE or SIGNAL_MESSAGE)
   b. Receive envelope_id from server

4. Insert to local DB with status=SENDING, envelope_id from response
5. On server ack → update status to SENT
6. On delivery receipt → update status to DELIVERED
7. On read receipt → update status to READ
```

### Incoming Message Flow
```
1. WebSocket receives PUT /api/v1/message with envelope
2. Client sends 200 ACK back

3. Check session exists for sender → 
   - If PREKEY_MESSAGE: X3DH.bobRespond() → establishes session
   - If SIGNAL_MESSAGE: DoubleRatchet.decrypt(state, message) → plaintext

4. Parse decrypted content → determine message type (text, image, reaction, receipt, etc.)

5. Dispatch to type-specific handler:
   - Text/image/video/voice → insert to DB, update UI
   - Reaction → add/remove reaction on existing message
   - Receipt → update message status
   - Typing → show/hide typing indicator
   - Edit → update message content
   - Delete → mark message as deleted
   - Group update → update group metadata

6. Send delivery receipt back via WS
7. When user views → send read receipt via WS
```

### Error Handling Matrix
| Phase | Error | Handling |
|---|---|---|
| Session establishment | No key bundle for user (404) | Show "User has no keys" error |
| Session establishment | Bundle fetch rate limited (429) | Wait Retry-After, retry |
| Encryption | Ratchet state corrupted | Archive session, re-establish via X3DH |
| Send | WS disconnected | Queue offline, send via REST fallback |
| Send | REST fails too | Store in offline queue, retry on connectivity |
| Send | 413 payload too large | Split message or refuse |
| Decryption | Wrong key | Show "Couldn't decrypt" with safety number change |
| Decryption | Corrupted payload | Show "Couldn't decrypt this message" |
| Decryption | Replay attack | Throw, don't insert duplicate |
| Media upload | File too large (128MB) | Refuse early, show limit |
| Media upload | Rate limited (200/day) | Queue for next day |

---

## File Manifest

### `feature/chat/src/main/java/org/enchant/chat/data/MessageSendPipeline.kt`
**Purpose:** Handles the complete outgoing message flow: encrypt → send → track status.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `sendMessage` | `suspend fun sendMessage(conversationId: String, recipientUserId: String, plaintext: ByteArray, messageType: MessageType, replyTo: String?): SendResult` | Encrypt → establish session if needed → send via WS → insert to DB | No session → establish; WS down → queue; rate limited → wait; payload too large → fail |
| `sendMediaMessage` | `suspend fun sendMediaMessage(conversationId: String, recipientUserId: String, fileUri: Uri, mimeType: String): SendResult` | Encrypt file → upload to Media → send message with media key | Upload fails → clean up; file too large → refuse; unsupported type → refuse |
| `sendReaction` | `suspend fun sendReaction(messageId: String, emoji: String): Result<Unit>` | PUT /v1/reactions/{message_id} | Toggle: same emoji → remove; network → fail silently |
| `sendDeliveryReceipt` | `suspend fun sendDeliveryReceipt(envelopeId: String, senderUserId: String)` | Send via WS | WS down → skip (ephemeral) |
| `sendReadReceipt` | `suspend fun sendReadReceipt(envelopeId: String, senderUserId: String)` | Send via WS | WS down → skip (ephemeral) |
| `sendTypingIndicator` | `suspend fun sendTypingIndicator(recipientUserId: String, isTyping: Boolean)` | Send TYPING_START/STOP | Must throttle: 3s between start, 5s auto-stop |
| `editMessage` | `suspend fun editMessage(originalEnvelopeId: String, newPlaintext: ByteArray): Result<Unit>` | Encrypt new content → PUT /v1/messages/{id} with new envelope_id | Max 2 edits; only original sender |
| `deleteForEveryone` | `suspend fun deleteForEveryone(envelopeId: String): Result<Unit>` | Send delete signal as new message | — |
| `deleteForSelf` | `suspend fun deleteForSelf(envelopeId: String)` | Local DB delete only | — |
| `forwardMessage` | `suspend fun forwardMessage(originalMessage: Message, targetConversationId: String, targetUserId: String): SendResult` | Re-encrypt content for target user | New session may be needed |
| `updateMessageStatus` | `suspend fun updateMessageStatus(envelopeId: String, status: MessageStatus)` | Update local DB | Trigger UI update via Flow |

```kotlin
sealed class SendResult {
    data class Success(val envelopeId: String) : SendResult()
    data class Queued(val messageId: String) : SendResult()
    data class Failed(val error: SendError) : SendResult()
}

enum class SendError { NO_SESSION, KEY_BUNDLE_MISSING, PAYLOAD_TOO_LARGE, RATE_LIMITED, NETWORK, ENCRYPTION_FAILED }
```

**Test requirements:** 18 tests — send without session (establish), send with existing session, WS down → REST fallback, both down → queue, media upload success, media upload fail, reaction toggle, delivery receipt sent, read receipt sent, typing throttle, edit max 2, delete for everyone/self, forward, retry after fail

---

### `feature/chat/src/main/java/org/enchant/chat/data/IncomingMessageProcessor.kt`
**Purpose:** Signal's `MessageContentProcessor` equivalent — handles all incoming message types, decrypts, dispatches.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `processIncoming` | `suspend fun processIncoming(envelope: IncomingEnvelope): ProcessResult` | Main entry: determine message type → decrypt → dispatch | Blocked sender → ignore; unknown group → ignore; announcement-only + non-admin → ignore |
| `processPreKeyMessage` | `suspend fun processPreKeyMessage(envelope: IncomingEnvelope, senderUserId: String): DecryptedContent` | X3DH as Bob → establish session → derive SK → init ratchet | Missing OPK → still establish (DH1+DH2+DH3); duplicate message → skip |
| `processSignalMessage` | `suspend fun processSignalMessage(envelope: IncomingEnvelope, senderUserId: String): DecryptedContent` | Load session → Double Ratchet decrypt | Session missing → fail; ratchet key mismatch → archive + request retry |
| `processDataMessage` | `suspend fun processDataMessage(content: DecryptedContent, envelope: IncomingEnvelope)` | Type-specific: text, image, video, voice, document, sticker, location, contact, poll | Each type handled separately |
| `processReactionMessage` | `suspend fun processReactionMessage(envelopeId: String, emoji: String, senderId: String, remove: Boolean)` | Add or remove reaction on existing message | Message not found → ignore; already has same reaction → remove (toggle) |
| `processEditMessage` | `suspend fun processEditMessage(originalEnvelopeId: String, newContent: DecryptedContent)` | Update existing message content | Original not found → ignore; max 2 edits → still store but don't allow more |
| `processDeleteMessage` | `suspend fun processDeleteMessage(envelopeId: String)` | Mark message as deleted (remote deletion) | Message not found → ignore |
| `processReceiptMessage` | `suspend fun processReceiptMessage(envelopeId: String, receiptType: ReceiptType)` | Update message status to DELIVERED or READ | Message not found → ignore |
| `processTypingMessage` | `suspend fun processTypingMessage(senderUserId: String, conversationId: String, isTyping: Boolean)` | Update typing indicator state | Expire after 5s of no update |
| `processGroupUpdate` | `suspend fun processGroupUpdate(conversationId: String, update: GroupUpdateData)` | Update group membership/metadata | Not a group → ignore |
| `shouldIgnore` | `suspend fun shouldIgnore(senderUserId: String, conversationId: String): Boolean` | Check blocked sender, inactive group, announcement-only group | — |
| `handleDecryptionError` | `suspend fun handleDecryptionError(envelope: IncomingEnvelope, error: DecryptionError)` | Handle decryption failure — send retry receipt if needed | Build retry receipt with envelope metadata; track failure count |
| `handleRetryReceipt` | `suspend fun handleRetryReceipt(senderUserId: String, failedEnvelopeId: String)` | On receiving retry request, archive session + resend message | Find original message in message log; re-encrypt with new session |

```kotlin
sealed class ProcessResult {
    data object Handled : ProcessResult()  // Successfully processed
    data object Ignored : ProcessResult()  // Blocked, duplicate, or irrelevant
    data class Error(val reason: String) : ProcessResult()  // Failed permanently
    data class RetryNeeded(val envelopeId: String) : ProcessResult()  // Need sender to retry
}

enum class ReceiptType { DELIVERY, READ }
enum class DecryptionError { MISSING_SESSION, WRONG_KEY, CORRUPTED_PAYLOAD, REPLAY, DUPLICATE }
```

**Test requirements:** 25 tests — each incoming type handled; prekey message establishes session; signal message decrypts; reaction add/remove; edit (1st, 2nd, 3rd rejected); delete; delivery/read receipts; typing indicators; blocked sender ignored; decryption error → retry receipt; retry receipt → archive + resend; duplicate envelope ignored; group update processed; announcement-only group restrictions

---

### `feature/chat/src/main/java/org/enchant/chat/data/ConversationRepository.kt`
**Purpose:** ALL data operations for conversations and messages. Single source of truth for chat data.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `getConversations` | `fun getConversations(filter: ConversationFilter): Flow<List<Conversation>>` | Reactive list, grouped by filter | Empty → emit empty list; filter changes → re-emit |
| `getMessages` | `fun getMessages(conversationId: String, limit: Int, beforeId: Long?): Flow<List<Message>>` | Reactive paginated messages | Scroll up → load more; empty → emit empty |
| `insertMessage` | `suspend fun insertMessage(message: Message): Long` | Insert into DB | Duplicate envelopeId → ignore |
| `getMessage` | `suspend fun getMessage(envelopeId: String): Message?` | Get by envelope ID | Not found → return null |
| `updateMessageStatus` | `suspend fun updateMessageStatus(envelopeId: String, status: MessageStatus)` | Update delivery status | Not found → no-op |
| `updateMessageContent` | `suspend fun updateMessageContent(envelopeId: String, content: String)` | Update edited content | Not found → no-op |
| `markMessageDeleted` | `suspend fun markMessageDeleted(envelopeId: String)` | Soft delete | Not found → no-op |
| `starMessage` | `suspend fun starMessage(envelopeId: String, starred: Boolean)` | Toggle star | — |
| `addReaction` | `suspend fun addReaction(messageId: Long, reaction: Reaction)` | Add reaction locally (optimistic) | — |
| `removeReaction` | `suspend fun removeReaction(messageId: Long, userId: String)` | Remove reaction locally | — |
| `getUnreadCount` | `fun getUnreadCount(): Flow<Int>` | Total unread across all conversations | — |
| `getConversationUnreadCount` | `suspend fun getConversationUnreadCount(conversationId: String): Int` | Per-conversation unread | — |
| `markConversationRead` | `suspend fun markConversationRead(conversationId: String)` | Mark all messages read, update unread count | — |
| `setArchived` | `suspend fun setArchived(conversationId: String, archived: Boolean)` | Archive/unarchive | — |
| `setPinned` | `suspend fun setPinned(conversationId: String, pinned: Boolean)` | Pin/unpin | — |
| `setMuted` | `suspend fun setMuted(conversationId: String, muted: Boolean, until: Long?)` | Mute/unmute | — |
| `searchConversations` | `fun searchConversations(query: String): Flow<List<Conversation>>` | Search by name | — |
| `searchMessages` | `fun searchMessages(query: String): Flow<List<Message>>` | FTS5 search on decrypted content | Must be client-side only (E2EE) |
| `getConversation` | `suspend fun getConversation(conversationId: String): Conversation?` | Get single conversation | Not found → null |
| `getOrCreateConversation` | `suspend fun getOrCreateConversation(userId: String): Conversation` | Get existing or create new for 1:1 chat | Check if exists first |
| `getPinnedMessages` | `suspend fun getPinnedMessages(conversationId: String): List<Message>` | Get pinned messages | — |
| `deleteExpiredMessages` | `suspend fun deleteExpiredMessages()` | Delete messages past disappearAt | Called periodically by cleanup worker |

**Test requirements:** 25 tests — CRUD operations, reactive flows emit on changes, pagination, search, filtering, pinned, archived, muted, expired deletion, conversation creation

---

### `feature/chat/src/main/java/org/enchant/chat/ConversationViewModel.kt`
**Purpose:** Signal's `ConversationViewModel` equivalent — drives the chat screen UI.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `init` | `fun init(conversationId: String)` | Load messages, connect to DB Flow | — |
| `loadMessages` | `suspend fun loadMessages(conversationId: String)` | Initial load from DB | Observe via Flow for live updates |
| `loadMoreMessages` | `suspend fun loadMoreMessages()` | Load next page (cursor from oldest message ID) | Respect limit; stop when no more |
| `sendTextMessage` | `suspend fun sendTextMessage(text: String, replyTo: String? = null): Boolean` | Send text via MessageSendPipeline | Empty text → refuse; show sending state |
| `sendMediaMessage` | `suspend fun sendMediaMessage(uri: Uri, mimeType: String): Boolean` | Encrypt → upload → send | Show progress; cancel support |
| `sendVoiceMessage` | `suspend fun sendVoiceMessage(audioFile: File, duration: Int): Boolean` | Upload as media → send | — |
| `sendLocationMessage` | `suspend fun sendLocationMessage(lat: Double, lng: Double, label: String?): Boolean` | Encrypt → send as message → POST /v1/location | — |
| `sendSticker` | `suspend fun sendSticker(packId: String, stickerId: String): Boolean` | Send as sticker message type | — |
| `resendMessage` | `suspend fun resendMessage(messageId: Long)` | Retry failed send | Must check if still pending |
| `deleteMessage` | `suspend fun deleteMessage(envelopeId: String, forEveryone: Boolean)` | Delete for self or everyone | Show confirmation dialog first |
| `editMessage` | `suspend fun editMessage(envelopeId: String, newText: String): Boolean` | Edit → encrypt → PUT /v1/messages | Max 2 edits; only own messages |
| `forwardMessage` | `suspend fun forwardMessage(envelopeId: String, targetConversationId: String): Boolean` | Forward to another conversation | Show conversation picker |
| `setReaction` | `suspend fun setReaction(messageId: Long, emoji: String)` | Toggle reaction (this emoji or remove) | Optimistic update → revert on failure |
| `starMessage` | `suspend fun starMessage(messageId: Long, starred: Boolean)` | Toggle star | — |
| `pinMessage` | `suspend fun pinMessage(messageId: Long, conversationId: String)` | Pin to conversation | Max N pinned |
| `unpinMessage` | `suspend fun unpinMessage(messageId: Long)` | Unpin | — |
| `copyToClipboard` | `fun copyToClipboard(text: String)` | Copy to system clipboard | Show snackbar confirmation |
| `reportMessage` | `suspend fun reportMessage(envelopeId: String)` | Submit abuse report | — |
| `searchInConversation` | `fun searchInConversation(query: String): Flow<List<Message>>` | Real-time FTS5 search with highlights | Debounce 300ms |
| `jumpToMessage` | `suspend fun jumpToMessage(envelopeId: String): Int` | Find message position for scroll | — |
| `jumpToDate` | `suspend fun jumpToDate(timestamp: Long): Int` | Find position for date | — |
| `startCall` | `fun startCall(remoteUserId: String, isVideo: Boolean)` | Navigate to call screen | — |
| `scrollToBottom` | `fun scrollToBottom()` | Emit event to scroll to bottom | Show FAB when not at bottom |
| `onCleared` | `fun onCleared()` | Clean up disposables, subscriptions | — |

**State flows exposed:**
```kotlin
val messages: StateFlow<List<Message>>       // Paginated messages
val conversation: StateFlow<Conversation?>   // Current conversation data
val typingIndicator: StateFlow<Boolean>      // Remote user typing
val sendingState: StateFlow<SendState?>      // Current send progress
val scrollToEvent: SharedFlow<ScrollEvent>   // Scroll commands for the RecyclerView

enum class SendState { IDLE, SENDING, UPLOADING, SENT, FAILED }
sealed class ScrollEvent { data class ToPosition(val position: Int) : ScrollEvent(); data object ToBottom : ScrollEvent() }
```

**Test requirements:** 30 tests — send text/reaction/media/voice/sticker — each success + each failure mode; edit with max 2 enforcement; delete for self/everyone; forward; resend; copy to clipboard; search; jump to date/position; scroll behavior; typing indicator; cleanup

---

### `feature/chat-list/src/main/java/org/enchant/chatlist/ConversationListViewModel.kt`
**Purpose:** Drives the conversation list screen with filtering, searching, archiving, pinning.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `init` | `fun init()` | Start observing conversations from DB | — |
| `selectFilter` | `fun selectFilter(filter: ConversationFilter)` | All / Unread / Groups / Personal | Reset pagination |
| `search` | `fun search(query: String)` | Debounced search (300ms) | Empty query → show all |
| `archiveConversation` | `suspend fun archiveConversation(conversationId: String)` | Toggle archive | POST /v1/chats/archive or unarchive |
| `pinConversation` | `suspend fun pinConversation(conversationId: String)` | Toggle pin | Max pinned conversations (Signal: unlimited but ordered) |
| `muteConversation` | `suspend fun muteConversation(conversationId: String, until: Long?)` | Set mute | PUT /v1/notif-pref/conversations/{id} |
| `deleteConversation` | `suspend fun deleteConversation(conversationId: String)` | Delete locally + messages | Confirm dialog first |
| `markRead` | `suspend fun markRead(conversationId: String)` | Mark all messages read | Call markConversationRead |
| `refresh` | `suspend fun refresh()` | Pull-to-refresh → GET /v1/messages/pending | — |
| `selectConversation` | `fun selectConversation(conversationId: String)` | Navigate to chat | Emit navigation event |

**State flows:**
```kotlin
val conversations: StateFlow<List<Conversation>>   // Filtered, sorted, observable
val filter: StateFlow<ConversationFilter>          // Active filter
val searchQuery: StateFlow<String>                 // Current search
val unreadCount: StateFlow<Int>                    // Total unread badge
```

**Tests:** 15 tests — load, filter all/unread/groups, search, archive toggle, pin/unpin, mute, delete, mark read, refresh, navigation

---

### `feature/chat-list/src/main/java/org/enchant/chatlist/ConversationListScreen.kt`
**Purpose:** Main conversation list UI — Signal's `ConversationListFragment` equivalent.

| Component | Behavior |
|---|---|
| Filter chips | All, Unread, Groups, Personal — horizontal scrollable chips |
| Search bar | Expandable at top, debounced real-time search |
| Conversation tile | Avatar (with online dot), display name, last message preview, timestamp, unread badge (circle with count), pin indicator, mute icon |
| Swipe left | Archive (with undo snackbar) |
| Swipe right | Pin |
| Long press | Multi-select mode → archive/delete/mark read |
| Pull-to-refresh | RefreshIndicator → calls `viewModel.refresh()` |
| FAB | Speed dial: New Chat (person icon), New Group (group icon) |
| Empty state | "No conversations yet — start a new chat" with illustration |
| Error state | "Couldn't load conversations" with retry button |

**Tests:** 10 — render conversations, filter chips selection, search filters list, swipe archive with undo, long press multi-select, pull-to-refresh, FAB menu, empty state, error state, badge count

---

### `feature/chat/src/main/java/org/enchant/chat/ConversationScreen.kt`
**Purpose:** Main chat screen — Signal's `ConversationFragment` equivalent.

| Component | Behavior |
|---|---|
| App bar | Back button, contact name/avatar, online dot, typing indicator subtitle, call/video buttons, overflow menu (view contact, search, disappearing timer) |
| Message list (RecyclerView) | Scroll up → older messages (pagination), scroll down → newest |
| Message bubble | Incoming (left, gray), outgoing (right, primary). Morphological corners: single/start/middle/end of cluster |
| Bubble footer | Timestamp, delivery status (sending/sent/delivered/read ticks), edited indicator |
| Reactions | Bubble with emoji + count below message. Tap to see reactor list. Long press to add. |
| Composer | Text field (expandable), emoji button, mic button (hold to record), send button (text mode) |
| Reply preview | Bar above composer when replying: quoted message preview + cancel button |
| Attachment sheet | Gallery, Camera, Document, Location, Contact, Poll |
| Typing indicator | "typing..." in app bar subtitle, auto-hide after 5s |
| E2EE header | "Messages are end-to-end encrypted. Tap for more info." |
| Scroll-to-bottom FAB | Appears when scrolled up. Smooth scroll down. |
| Date separator | "Today", "Yesterday", "Monday", date — inserted between messages from different days |
| System messages | Centered text: "Alice joined the group", "Messages now disappearing after 24h" |

**Test requirements:** 15 — render messages, send text, reply with preview, attachment sheet, voice recording hold/release, reactions add/remove, scroll to bottom FAB, pagination (load older), typing indicator, delivery status updates, date separators, system messages, E2EE header tap

---

### `feature/chat/src/main/java/org/enchant/chat/components/MessageBubble.kt`
**Purpose:** Signal's `ConversationItem` equivalent — renders individual message bubbles with proper shape, colors, reactions, footer.

| Component | Function | Description |
|---|---|---|
| `TextMessageBubble` | `@Composable fun TextMessageBubble(message: Message, isOutgoing: Boolean, clusterPosition: ClusterPosition)` | Text with link detection (URLs tappable), mentions (tap to profile), formatting (bold/italic/code via regex), search highlighting. Footer: timestamp + status ticks + edited indicator. |
| `MediaMessageBubble` | `@Composable fun MediaMessageBubble(message: Message, isOutgoing: Boolean, onTap: () -> Unit)` | Thumbnail with play icon for video. Tap → full screen viewer. Shows sender name for groups. |
| `VoiceMessageBubble` | `@Composable fun VoiceMessageBubble(message: Message, isOutgoing: Boolean)` | Play/pause button + waveform visualization + duration + unplayed indicator. Playback position persisted. |
| `DocumentBubble` | `@Composable fun DocumentBubble(message: Message)` | File icon (by type), filename, size, download/ open button |
| `LocationBubble` | `@Composable fun LocationBubble(message: Message)` | Static map preview (lat/lng), address label, tap to open in maps app |
| `StickerBubble` | `@Composable fun StickerBubble(message: Message)` | Full-size sticker image, no footer |
| `SystemMessageBubble` | `@Composable fun SystemMessageBubble(text: String)` | Centered, gray, smaller font, no bubble background |

**Cluster positions:** `SINGLE`, `START`, `MIDDLE`, `END` — determined by sender continuity, time proximity (5 min threshold), and message type.

**Tests:** 15 — each bubble type renders correctly, cluster positions, link detection tap, mentions, formatting, reactions overlay, footer visibility per status

---

### `feature/chat/src/main/java/org/enchant/chat/components/MediaViewerScreen.kt`
**Purpose:** Full-screen media viewer with zoom, swipe, share, download.

| Function | Description |
|---|---|
| Zoom (pinch + double-tap) | PhotoView-style zoom gesture |
| Swipe to dismiss | Vertical swipe down to close |
| Share | ACTION_SEND intent with file URI |
| Download to gallery | Save via MediaStore (API 30+) |
| Video playback | ExoPlayer integration |

**Tests:** 4 — render, zoom gesture, swipe dismiss, share intent

---

### `feature/chat/src/main/java/org/enchant/chat/components/EmojiPicker.kt`
**Purpose:** Emoji picker bottom sheet for reactions.

| Function | Description |
|---|---|
| Quick reactions row | 6 frequently used emoji in a row |
| Full emoji grid | Categories: Smileys, People, Animals, Food, Travel, Activities, Objects, Symbols, Flags |
| Search | By emoji name or description |
| Recently used | Track last 20 used emoji |

**Tests:** 4 — quick reactions, grid renders, search filters, recent tracking

---

### `feature/chat/src/main/java/org/enchant/chat/components/MessageContextMenu.kt`
**Purpose:** Signal's `ConversationContextMenu` equivalent — long-press menu on messages.

| Action | Condition | Behavior |
|---|---|---|
| Copy | All messages | Copy text to clipboard |
| Reply | All messages | Set reply state in composer |
| Edit | Own messages, sent < 24h ago | Open edit mode in composer |
| Delete for everyone | Own messages, sent < 24h ago | Send delete signal |
| Delete for self | All messages | Local delete only |
| Forward | All messages | Open conversation picker |
| Star/Unstar | All messages | Toggle star |
| Select | All messages | Multi-select mode |
| Info | All messages | Show message details (sent/ delivered/read timestamps) |

**Tests:** 6 — each action shows correctly per message ownership/time condition

---

### `native-app/chat/BUILD_PHASES/03_core_chat_2_message_bubbles.md`

Actually this phase doc is already comprehensive. Let me just finalize it with the remaining files.

---

### `core/chat/src/main/java/org/enchant/chat/MediaService.kt`
**Purpose:** Media picker, compression, encryption, upload.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `pickImage` | `suspend fun pickImage(fromCamera: Boolean): Uri?` | Launch image picker | Permission denied → show rationale; no camera → use gallery only |
| `pickVideo` | `suspend fun pickVideo(): Uri?` | Launch video picker | — |
| `pickDocument` | `suspend fun pickDocument(mimeTypes: Array<String>): Uri?` | Launch file picker | Max 128MB enforced |
| `startRecording` | `fun startRecording(): File` | Start audio recording | Permission denied → fail; storage full → fail |
| `stopRecording` | `fun stopRecording(): File` | Stop, return audio file | Duration < 1s → discard |
| `compressImage` | `suspend fun compressImage(uri: Uri, maxSize: Int = 1024): ByteArray` | Compress JPEG to max dimension | Not JPEG → resize only; already small → skip compression |
| `encryptAndUploadMedia` | `suspend fun encryptAndUploadMedia(fileBytes: ByteArray, mimeType: String): MediaUploadResult` | Generate key → XChaCha20 encrypt → upload to Media server | Upload fails → clean encrypted temp file |
| `downloadAndDecryptMedia` | `suspend fun downloadAndDecryptMedia(mediaId: String, mediaKey: ByteArray, mediaIv: ByteArray): File` | Download → verify SHA-256 → XChaCha20 decrypt → cache to disk | Hash mismatch → retry; corrupted → inform user; disk full → fail |
| `saveToGallery` | `suspend fun saveToGallery(file: File, mimeType: String)` | Save to device gallery via MediaStore | Permission denied → show rationale |

```kotlin
data class MediaUploadResult(
    val mediaId: String,
    val mediaKey: ByteArray,  // MUST zero after use
    val mediaIv: ByteArray    // MUST zero after use
)
```

**Security:** `mediaKey` and `mediaIv` must be zeroed after the message is sent.

**Tests:** 12 — pick image/video/document, compress JPEG, encrypt+upload, download+decrypt, save to gallery, permission denied (each), recording start/stop

---

### `feature/chat/src/main/java/org/enchant/chat/data/ChatPagingSource.kt`
**Purpose:** Paging 3 data source for conversation messages — Signal's `ConversationDataSource` equivalent.

| Function | Signature | Description |
|---|---|---|
| `load` | `suspend fun load(params: LoadParams<Long?>): LoadResult<Long, Message>` | Load messages from DB cursor-based (by localId). Returns `LoadResult.Page` with next key |
| `getRefreshKey` | `fun getRefreshKey(state: PagingState<Long, Message>): Long?` | Return anchor position for refresh |

**Config:** page size = 50, prefetch distance = 10, initial load size = 50
**Tests:** 4 — load page, load next page, load last page (empty), refresh key

---

### `feature/chat/src/main/java/org/enchant/chat/data/MessageDataFetcher.kt`
**Purpose:** Fetches associated message data in parallel (reactions, attachments, mentions, polls) — Signal's `MessageDataFetcher` equivalent.

| Function | Signature | Description |
|---|---|---|
| `fetchExtraData` | `suspend fun fetchExtraData(message: Message): ExtraMessageData` | Load reactions, attachments, mentions, polls in parallel | Use coroutineScope + async for parallelism |
| `fetchExtraDataBatch` | `suspend fun fetchExtraDataBatch(messages: List<Message>): Map<Long, ExtraMessageData>` | Batch version for list loading | — |

```kotlin
data class ExtraMessageData(
    val reactions: List<Reaction>,
    val mentions: List<Mention>,
    val isPinned: Boolean,
    val pollData: PollData?
)
```

**Tests:** 3 — single fetch, batch fetch, empty data for plain text messages

---

### `feature/chat/src/main/java/org/enchant/chat/data/ContentPreProcessor.kt`
**Purpose:** Pre-processes incoming content before display — URL detection, formatting, link previews.

| Function | Signature | Description |
|---|---|---|
| `detectUrls` | `fun detectUrls(text: String): List<UrlSpan>` | Regex URL detection with protocol check | No URLs → empty list |
| `parseFormatting` | `fun parseFormatting(text: String): List<FormattingSpan>` | Parse **bold**, *italic*, `code` | Nested formatting → outer wins |
| `generateLinkPreview` | `suspend fun generateLinkPreview(url: String): LinkPreview?` | Fetch Open Graph metadata (title, description, image) | Network → no preview; no OG tags → no preview |
| `applyFormatting` | `fun applyFormatting(text: String): AnnotatedString` | Combine all formatting into styled text | — |

**Tests:** 6 — URL detection, formatting parsing (bold/italic/code/mixed), link preview fetch, apply formatting, no formatting, no URLs

---

## Module: `:core:notifications` (6 files)

**Purpose:** Signal-equivalent `MessageNotifier` + `NotificationBuilder` system. Handles all user-facing notifications with inline reply, mark-as-read actions, message grouping, and Android 12+ notification profiles.

### File: `core/notifications/src/main/java/org/enchant/core/notifications/MessageNotifier.kt`

**Purpose:** Central notification manager — updates notifications on new message, groups by conversation, handles inline actions. Signal's `MessageNotifier.java` equivalent.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `init` | `fun init(context: Context)` | Create notification channels on API 26+ | Called once per app lifetime |
| `updateNotification` | `fun updateNotification(context: Context, conversationId: String, message: Message)` | Update notification for a new message | Thread-safe — multiple incoming messages at once must batch correctly |
| `updateNotification` (overload) | `fun updateNotification(context: Context, conversationId: String, silent: Boolean)` | Update notification without sound/vibration (for synced messages) | Used when another device already notified |
| `removeConversation` | `fun removeConversation(context: Context, conversationId: String)` | Remove notifications for a specific conversation | When user opens chat |
| `cancelDelayedNotifications` | `fun cancelDelayedNotifications()` | Cancel pending scheduled notifications | — |
| `setPendingIntent` | `fun setPendingIntent(intent: PendingIntent)` | Set the conversation open intent | Updated on app launch |

**Notification behavior:**
1. Single unread message → show notification with sender name, preview, inline reply + mark-read actions
2. Multiple messages in same conversation → update notification message count, show latest preview
3. Multiple conversations with unread → show summary notification: "Alice (2), Bob (1), Group Chat (5)"
4. When user opens conversation → remove that conversation's notifications
5. When user opens app → remove all notifications

**Test requirements:** 8 tests — single message notification, multiple messages same conversation (grouped), multiple conversations (summary), inline reply action, mark-read action, notification removal, silent update, delayed cancel

### File: `core/notifications/src/main/java/org/enchant/core/notifications/NotificationBuilder.kt`

**Purpose:** Builds Android notifications with proper styling, actions, and grouping. Signal's `NotificationBuilder.java` equivalent.

| Function | Signature | Description |
|---|---|---|
| `buildMessageNotification` | `fun buildMessageNotification(context: Context, conversationDisplayName: String, messagePreview: String, senderName: String, conversationId: String, messageCount: Int): Notification` | Build individual or group conversation notification | Must handle both single message and grouped display |
| `buildSummaryNotification` | `fun buildSummaryNotification(context: Context, conversationList: List<ConversationSummary>): Notification` | Build summary notification for multiple conversations | One summary notification, each conversation has its own children |
| `buildReplyAction` | `fun buildReplyAction(context: Context, conversationId: String): Notification.Action` | Build inline reply action (Android 7+) | Returns `RemoteInput`-based action |
| `buildMarkAsReadAction` | `fun buildMarkAsReadAction(context: Context, conversationId: String): Notification.Action` | Build mark-as-read action | — |
| `buildCallNotification` | `fun buildCallNotification(context: Context, callerName: String): Notification` | Build incoming call notification | High priority, heads-up display |

**ConversationSummary data class:**
```kotlin
data class ConversationSummary(
    val conversationId: String,
    val displayName: String,
    val snippet: String,
    val unreadCount: Int,
    val timestamp: Long,
    val avatarUri: Uri?,
    val isMuted: Boolean
)
```

**Test requirements:** 6 tests — build message notification, summary notification, reply action, mark-read action, call notification, muted conversation (no sound)

### File: `core/notifications/src/main/java/org/enchant/core/notifications/NotificationChannels.kt`

**Purpose:** Defines notification channels for Android 8+. Signal's `NotificationChannels.java` equivalent.

| Channel | ID | Importance | Description |
|---|---|---|---|
| Messages | `messages` | HIGH | Default message notifications, sound + vibration |
| Messages Silent | `messages_silent` | LOW | Silent message notifications (for muted conversations) |
| Calls | `calls` | HIGH | Incoming call notifications, ringtone |
| Voice Messages | `voice` | DEFAULT | Voice message playback control |
| Other | `other` | LOW | Background service, other non-intrusive notifications |

| Function | Signature | Description |
|---|---|---|
| `createAll` | `fun createAll(context: Context)` | Create all 5 channels on API 26+ | Must be idempotent; called on every app launch |
| `deleteUnused` | `fun deleteUnused(context: Context)` | Delete channels no longer used | For migration |

**Test requirements:** 2 tests — channels exist after create, delete works

### File: `core/notifications/src/main/java/org/enchant/core/notifications/OptimizedMessageNotifier.kt`

**Purpose:** Batches notifications on a background thread to avoid spamming the system. Signal's `OptimizedMessageNotifier` equivalent.

| Function | Signature | Description |
|---|---|---|
| `onMessageReceived` | `fun onMessageReceived(conversationId: String, message: Message)` | Queue notification update, batch on background thread | Delay 50ms to batch rapid incoming messages |
| `flush` | `suspend fun flush()` | Immediately process all queued notifications | Called when app goes to background |
| `cancelAll` | `fun cancelAll()` | Remove all notifications | On app open |

**Test requirements:** 4 tests — batch multiple messages, flush triggers immediately, cancel all, delay does not exceed 100ms

### File: `core/notifications/src/main/java/org/enchant/core/notifications/NotificationProfileHelper.kt`

**Purpose:** Android 12+ notification profiles — scheduled time-based notification filtering. Signal's `NotificationProfile` equivalent.

| Function | Signature | Description |
|---|---|---|
| `createProfile` | `fun createProfile(context: Context, name: String, icon: Icon, schedule: ProfileSchedule, allowedContacts: List<String>)` | Create a notification profile | API 31+ only; no-op on lower |
| `updateProfileSchedule` | `fun updateProfileSchedule(context: Context, profileId: String, schedule: ProfileSchedule)` | Update profile schedule | — |
| `deleteProfile` | `fun deleteProfile(context: Context, profileId: String)` | Delete profile | — |
| `isProfileActive` | `fun isProfileActive(context: Context): Boolean` | Check if any profile is currently active | — |

```kotlin
data class ProfileSchedule(
    val startHour: Int, val startMinute: Int,
    val endHour: Int, val endMinute: Int,
    val daysOfWeek: List<DayOfWeek>,     // Empty = every day
    val timezone: ZoneId
)
```

**Test requirements:** 4 tests — create profile, update schedule, delete, isActive check

### File: `core/notifications/src/main/java/org/enchant/core/notifications/NotificationReplyReceiver.kt`

**Purpose:** BroadcastReceiver for handling inline reply and mark-as-read actions from notifications.

| Function | Signature | Description |
|---|---|---|
| `onReceive` | `fun onReceive(context: Context, intent: Intent)` | Extract `RemoteInput` results → send message or mark read | Must use `PendingIntent.getBroadcast` with unique request codes |
| `getReplyIntent` | `fun getReplyIntent(context: Context, conversationId: String): PendingIntent` | Build PendingIntent for reply action | Unique request code per conversation |
| `getMarkReadIntent` | `fun getMarkReadIntent(context: Context, conversationId: String): PendingIntent` | Build PendingIntent for mark-read action | — |

**Test requirements:** 3 tests — reply extracts RemoteInput, mark-read calls ConversationRepository, pending intents are unique per conversation

---

## Module: `:core:chat` — Expanded Send Pipeline (additions to existing ConversationViewModel)

These are additions to the existing `ConversationViewModel.sendTextMessage` function — adding all parameters from Signal's `sendMessage`.

### Expanded `sendMessage` Parameters

Replace the existing `sendTextMessage(text, replyTo)` with a full-parameter version matching Signal:

| New Parameter | Type | Description | Backend Handling |
|---|---|---|---|
| `body` | `String` | Message text | E2EE encrypted in payload |
| `replyTo` | `String?` | Envelope ID of replied message | Included in encrypted payload |
| `mentions` | `List<Mention>?` | @mentions with user ID + range | Included in encrypted payload as body ranges |
| `bodyRanges` | `List<BodyRange>?` | Formatting ranges (bold, italic, code, mention, link) | Included in encrypted payload |
| `linkPreview` | `LinkPreview?` | Open Graph link preview (title, description, image URL) | Included in encrypted payload |
| `contacts` | `List<Contact>?` | Shared contact cards | Included in encrypted payload |
| `slideDeck` | `SlideDeck?` | Multi-slide media attachment (image + caption, or multiple images) | Each slide uploaded individually, references in payload |
| `isViewOnce` | `Boolean` | View-once media (disappears after viewing) | Flag in encrypted payload |
| `scheduledDate` | `Long?` | Unix timestamp for scheduled/delayed send | If set, create `ScheduledSendJob` instead of sending immediately |
| `preUploadResults` | `List<PreUploadResult>?` | Pre-uploaded media references (for "send while uploading") | Media uploaded before user taps send |
| `metricId` | `String?` | Performance tracing ID | Logged in analytics only |

```kotlin
data class Mention(val userId: String, val start: Int, val length: Int)
data class BodyRange(val start: Int, val length: Int, val type: BodyRangeType, val value: String?)
enum class BodyRangeType { BOLD, ITALIC, CODE, MENTION, LINK, SPOILER }
data class LinkPreview(val url: String, val title: String?, val description: String?, val imageUrl: String?)
data class SlideDeck(val slides: List<Slide>)
data class Slide(val mediaId: String?, val mimeType: String, val caption: String?, val isViewOnce: Boolean)
data class PreUploadResult(val mediaId: String, val mediaKey: ByteArray, val mediaIv: ByteArray)
```

### Updated `ConversationViewModel.sendMessage`

| Function | Signature | Description |
|---|---|---|
| `sendMessage` | `suspend fun sendMessage(body: String, replyTo: String? = null, mentions: List<Mention>? = null, bodyRanges: List<BodyRange>? = null, linkPreview: LinkPreview? = null, contacts: List<Contact>? = null, slideDeck: SlideDeck? = null, isViewOnce: Boolean = false, scheduledDate: Long? = null, preUploadResults: List<PreUploadResult>? = null): SendResult` | **Full-parameter send.** Encrypts the complete content (body + all attachments + metadata), creates the protobuf payload, sends via MessageSendPipeline. | Empty body + no slide deck → refuse; body > 64KB → split; mentions validate user exists; linkPreview URL must be present in body |

### Scheduled Send

| Function | Signature | Description |
|---|---|---|
| `scheduleMessage` | `suspend fun scheduleMessage(body: String, scheduledDate: Long, replyTo: String? = null): SendResult` | Creates `ScheduledSendJob` with the encrypted payload and future timestamp | Job fires at scheduledDate, sends via normal pipeline |
| `cancelScheduledMessage` | `suspend fun cancelScheduledMessage(messageId: Long)` | Cancel a scheduled message before it sends | — |
| `getScheduledMessageCount` | `fun getScheduledMessageCount(): Flow<Int>` | Number of pending scheduled messages | — |

**Tests:** 10 — send with all params, send with none, scheduled send fires at correct time, cancel before send, scheduled message count, mentions validation, empty body reject, view-once flag set, link preview attached, slide deck with multiple slides

### View-Once Media

| Function | Signature | Description |
|---|---|---|
| `markViewOnceViewed` | `suspend fun markViewOnceViewed(envelopeId: String)` | Mark view-once media as viewed → trigger deletion on server | Only the first view is tracked; subsequent views return "already viewed" |
| `deleteViewOnceMedia` | `suspend fun deleteViewOnceMedia(localPath: String)` | Delete local copy of view-once media after viewing | Called after media viewer closes |

**Tests:** 3 — mark viewed, delete local, already viewed returns false

---

## Additional Backend Endpoints (Phase 3)

These endpoints exist on the backend but are NOT referenced in other phase docs. Add them here since they relate to chat/message features:

| Endpoint | Method | Auth | Purpose | Phase Reference |
|---|---|---|---|---|
| `/v1/location/{envelope_id}` | GET | JWT | Get shared location details from an envelope | ConversationViewModel.getSharedLocation |
| `/v1/notes/{envelope_id}/play` | POST | JWT | Record voice note playback event (for expiring audio) | ConversationViewModel.markVoiceNotePlayed |
| `/v1/contacts/share` | POST | JWT | Server-side contact card share tracking | ConversationViewModel.sendContactCard |
| `/v1/messages/{envelope_id}/translate` | POST | JWT | Request message translation (server may return mock) | ConversationViewModel.translateMessage |
| `/v1/reactions/{message_id}/{emoji}` | GET | JWT | List all users who reacted with a specific emoji | ConversationViewModel.getReactorList |
| `/v1/polls/{poll_id}/voters/{option_id}` | GET | JWT | List voters for a specific poll option (403 if anonymous) | ConversationViewModel.getPollVoters |
| `/v1/disappear/viewed` | POST | JWT | Record a message as viewed (for FROM_VIEW timer mode) | ConversationViewModel.markMessageViewed |
| `/v1/chats/{conversation_id}/archive` | POST | JWT | Archive a conversation | ConversationRepository.setArchived(true) |
| `/v1/chats/{conversation_id}/archive` | DELETE | JWT | Unarchive a conversation | ConversationRepository.setArchived(false) |
| `/v1/chats/archived` | GET | JWT | List all archived conversations | ConversationListViewModel.loadArchived |

---

## Expanded Message Items (additions)

These are additions to the `MessageBubble.kt` component — adding Signal's full item rendering architecture.

### `V2ConversationItemShape.kt` — Bubble Shape Calculator

**Purpose:** Determines if a message bubble is SINGLE, START, MIDDLE, or END of a cluster based on sender continuity, time proximity, and type.

| Function | Signature | Description |
|---|---|---|
| `calculateClusterPosition` | `fun calculateClusterPosition(messages: List<Message>, index: Int): ClusterPosition` | Returns SINGLE/START/MIDDLE/END based on: same sender as previous, within 5 minutes, same message type | First message in list → START; only message → SINGLE |
| `shouldShowSenderName` | `fun shouldShowSenderName(messages: List<Message>, index: Int, isGroup: Boolean): Boolean` | Show sender name for first message in a cluster (groups only) | — |

```kotlin
enum class ClusterPosition { SINGLE, START, MIDDLE, END }
```

**Tests:** 5 — single, start, middle, end, time gap resets cluster

### `ChatColorsDrawable.kt` — Chat Color Theme Provider

**Purpose:** Manages per-conversation chat colors (gradient or solid) rendered behind message bubbles. Signal's `ChatColorsDrawable` equivalent.

| Function | Signature | Description |
|---|---|---|
| `getColor` | `fun getColor(conversationId: String): ChatColor` | Get chat color for a conversation | Default to system blue if none set |
| `getBubbleColor` | `fun getBubbleColor(message: Message, isOutgoing: Boolean): Color` | Get bubble background color | Outgoing uses chat color; incoming uses gray |
| `setConversationColor` | `suspend fun setConversationColor(conversationId: String, color: ChatColor)` | Set color for a conversation | Store in SignalStore.chatColors |

```kotlin
sealed class ChatColor {
    data class Solid(val color: Color) : ChatColor()
    data class Gradient(val start: Color, val end: Color) : ChatColor()
    data object Default : ChatColor()
}
```

**Tests:** 4 — default color, set conversation color, get bubble color outgoing/incoming, gradient rendering

### Share Contact in Message

| Function | Signature | Description |
|---|---|---|
| `sendContactCard` | `suspend fun sendContactCard(contactUserId: String, conversationId: String)` | Encrypt contact vCard → send as message | Contact data encrypted in payload |
| `displayContactBubble` | `@Composable fun ContactBubble(contact: Contact, isOutgoing: Boolean)` | Renders contact card with avatar, name, phone, add contact button | Tap add → navigate to AddContactScreen |

**Tests:** 3 — send contact card, render bubble, add contact tap

---

## Acceptance Criteria (expanded)

All existing criteria plus:

- [ ] Notifications: single message shows preview, multiple batch, summary for multi-conversation, inline reply works, mark-as-read works
- [ ] Notification channels: 5 channels created (Messages, Messages Silent, Calls, Voice, Other)
- [ ] Optimized notifier batches 50ms windows correctly
- [ ] Full send params: mentions, body ranges, link previews, contacts, slide deck, view-once, scheduled send all work
- [ ] Scheduled send fires at correct time via JobManager
- [ ] View-once media deletes after first view
- [ ] Contact sharing in messages renders and works
- [ ] Bubble shapes: single/start/middle/end correct based on sender+time cluster
- [ ] Chat colors: per-conversation customization, renders behind bubbles
- [ ] Message rendering in RTL works with correct cluster shapes
- [ ] FileProvider configured and camera capture does not crash
- [ ] Share target receives text/image/video intents from other apps
- [ ] Direct share shows pinned conversations as share targets
- [ ] PhotoPicker (API 33+) used on supported devices, fallback to legacy picker
- [ ] SavedStateHandle used in all ViewModels for process death survival
- [ ] All tests pass (target: 300+ tests across all chat files)
