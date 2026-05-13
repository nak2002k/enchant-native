# Protobuf Schema Reference — Complete Protocol Definition

> Master document defining all 16 `.proto` files for Enchant's E2EE messaging protocol.
> Follows Signal's protobuf wire format exactly for WebSocket frames, message envelopes,
> and inner content. All files live in `core/protos/src/main/proto/enchant/`.

---

## Table of Contents

1. [Module Structure](#1-module-structure)
2. [WebSocketResources.proto](#2-websocketresourcesproto)
3. [Envelope.proto](#3-envelopeproto)
4. [Content.proto](#4-contentproto)
5. [DataMessage.proto](#5-datamessageproto)
6. [SyncMessage.proto](#6-syncmessageproto)
7. [CallMessage.proto](#7-callmessageproto)
8. [ReceiptMessage.proto](#8-receiptmessageproto)
9. [TypingMessage.proto](#9-typingmessageproto)
10. [AttachmentPointer.proto](#10-attachmentpointerproto)
11. [BodyRange.proto](#11-bodyrangeproto)
12. [StoryMessage.proto](#12-storymessageproto)
13. [GroupContext.proto](#13-groupcontextproto)
14. [Provisioning.proto](#14-provisioningproto)
15. [StorageService.proto](#15-storageserviceproto)
16. [SessionRecord.proto](#16-sessionrecordproto)
17. [InternalSerialization.proto](#17-internalserializationproto)
18. [Build Integration](#18-build-integration)
19. [Cross-Reference: Backend ↔ Protos](#19-cross-reference-backend--protos)

---

## 1. Module Structure

### 1.1 Module: `:core:protos`

```
core/protos/
├── build.gradle.kts                  ← protobuf codegen config
└── src/main/
    └── proto/
        └── enchant/
            ├── WebSocketResources.proto
            ├── Envelope.proto
            ├── Content.proto
            ├── DataMessage.proto
            ├── SyncMessage.proto
            ├── CallMessage.proto
            ├── ReceiptMessage.proto
            ├── TypingMessage.proto
            ├── AttachmentPointer.proto
            ├── BodyRange.proto
            ├── StoryMessage.proto
            ├── GroupContext.proto
            ├── Provisioning.proto
            ├── StorageService.proto
            └── InternalSerialization.proto

core/crypto/src/main/proto/enchant/
            └── SessionRecord.proto   ← lives in :core:crypto (crypto-specific)
```

### 1.2 Package Convention

All proto files use:
```protobuf
package enchant;
option java_package = "org.enchant.protos";
```

The exception is `SessionRecord.proto` which uses:
```protobuf
package enchant.crypto;
option java_package = "org.enchant.core.crypto.protos";
```

### 1.3 Code Generation

The `protobuf` plugin (version 0.9.4) with `protobuf-javalite` (version 4.29.2) generates Java classes from each `.proto` file. All other modules depend on `:core:protos` (and `:core:crypto` for session records).

Generated classes follow the pattern:
- `WebSocketResources.proto` → `org.enchant.protos.WebSocketResources`
- `Envelope.proto` → `org.enchant.protos.Envelope`
- `Content.proto` → `org.enchant.protos.Content`

The `lite` runtime is used to keep APK size minimal (no reflection-based proto methods).

---

## 2. WebSocketResources.proto

**File:** `core/protos/src/main/proto/enchant/WebSocketResources.proto`
**Generated class:** `org.enchant.protos.WebSocketResources`

### 2.1 WebSocketMessage

```
┌──────────────────────────────────────────────────────────┐
│                    WebSocketMessage                       │
├──────────┬────────┬──────────────────────────────────────┤
│ Field 1  │ enum   │ type: UNKNOWN=0, REQUEST=1, RESPONSE=2│
│ Field 2  │ msg    │ request (WebSocketRequestMessage)     │
│ Field 3  │ msg    │ response (WebSocketResponseMessage)   │
└──────────┴────────┴──────────────────────────────────────┘

Rules:
- Exactly one of request/response is set, determined by `type`
- `UNKNOWN` type is invalid and should never be sent
```

### 2.2 WebSocketRequestMessage

```
┌──────────────────────────────────────────────────────────┐
│                  WebSocketRequestMessage                  │
├──────────┬────────┬──────────────────────────────────────┤
│ Field 1  │ string │ verb — "POST", "GET", "PUT"          │
│ Field 2  │ string │ path — endpoint path                 │
│ Field 3  │ bytes  │ body — payload bytes                 │
│ Field 4  │ uint64 │ id — client-assigned request ID      │
│ Field 5  │ string │ headers[] — extra HTTP headers       │
└──────────┴────────┴──────────────────────────────────────┘

Supported verb+path combinations:
- POST /v1/auth          → body = raw JWT bytes
- POST /api/v1/message   → body = serialized Envelope
- GET  /v1/keepalive     → body = empty
- PUT  /api/v1/message   → body = serialized Envelope (server push)
- POST /api/v1/receipt   → body = serialized ReceiptMessage
```

### 2.3 WebSocketResponseMessage

```
┌──────────────────────────────────────────────────────────┐
│                  WebSocketResponseMessage                  │
├──────────┬────────┬──────────────────────────────────────┤
│ Field 1  │ uint64 │ id — matches request id             │
│ Field 2  │ uint32 │ status — HTTP status code            │
│ Field 3  │ string │ message — human-readable status      │
│ Field 4  │ bytes  │ body — response payload              │
│ Field 5  │ string │ headers[] — response headers         │
└──────────┴────────┴──────────────────────────────────────┘

Status codes:
- 200: OK (message sent, keepalive ok)
- 400: Bad request
- 401: Auth failure (JWT expired/invalid)
- 404: Not found
- 413: Payload too large
- 429: Rate limited
- 500: Server error
```

### 2.4 Protocol Flow (Complete)

```
Client                          Server
  │                               │
  │── CONNECT WebSocket ─────────▶│
  │                               │
  │── REQUEST: POST /v1/auth ────▶│
  │   verb="POST", path="/v1/auth",│
  │   body=<raw JWT>, id=1       │
  │                               │
  │◀── RESPONSE: 200 ────────────│
  │   id=1, status=200,          │
  │   message="Authenticated"    │
  │                               │
  │── REQUEST: POST /api/v1/msg ─▶│
  │   verb="POST",                │
  │   path="/api/v1/message",     │
  │   body=<Envelope bytes>, id=2│
  │                               │
  │◀── RESPONSE: 200 ────────────│
  │   id=2, status=200,          │
  │   body=<envelope_id bytes>   │
  │                               │
  │◀── REQUEST: PUT /api/v1/msg ─│  (server push)
  │   verb="PUT",                 │
  │   path="/api/v1/message",     │
  │   body=<Envelope bytes>, id=3│
  │                               │
  │── RESPONSE: 200 ────────────▶│
  │   id=3, status=200           │
  │                               │
  │── REQUEST: GET /keepalive ──▶│  (every 30s)
  │   verb="GET",                 │
  │   path="/v1/keepalive",       │
  │   body=empty, id=4           │
  │                               │
  │◀── RESPONSE: 200 ────────────│
  │   id=4, status=200           │
  │                               │
```

---

## 3. Envelope.proto

**File:** `core/protos/src/main/proto/enchant/Envelope.proto`
**Generated class:** `org.enchant.protos.Envelope`

### 3.1 Purpose

The Envelope is the **transport wrapper** for ALL messages sent through the MRS service. It carries metadata (sender, recipient, timestamps) plus the encrypted payload blob. The server **never** inspects the payload bytes.

### 3.2 Type Enum

```
Envelope.Type (enum):
  UNKNOWN                = 0   // Invalid; never used
  DOUBLE_RATCHET         = 1   // Normal encrypted message via Double Ratchet
  PREKEY_MESSAGE         = 3   // First message establishing a new session
  SERVER_DELIVERY_RECEIPT= 5   // Server-generated delivery confirmation
  UNIDENTIFIED_SENDER    = 6   // Sealed sender message (sender identity hidden)
  PLAINTEXT_CONTENT      = 8   // Unencrypted content (error receipts only)

Reserved:
  2 = KEY_EXCHANGE (legacy)
  4 = (unused)
  7 = SENDERKEY_MESSAGE (future group messaging)
```

### 3.3 Fields (22 total)

```
┌───────────┬──────────┬────────────────────────────────────────────────┐
│ Field     │ Type     │ Description                                    │
├───────────┼──────────┼────────────────────────────────────────────────┤
│ 1  type   │ enum     │ Envelope.Type — message type                   │
│ 2         │ reserved │ formerly sourceE164                            │
│ 11 source │ string   │ Sender's service ID (ACI UUID)                 │
│ 7  device │ uint32   │ Sender's device ID                             │
│ 13 dest   │ string   │ Recipient's service ID (destination UUID)      │
│ 3         │ reserved │ formerly relay                                 │
│ 5  ts     │ uint64   │ Client timestamp (epoch millis)               │
│ 6         │ reserved │ formerly legacyMessage                         │
│ 8  content│ bytes    │ Encrypted Content protobuf for type 1,3,6,8   │
│ 9  guid   │ string   │ Server-assigned GUID (string format)          │
│ 10 svrTs  │ uint64   │ Server timestamp (epoch millis)               │
│ 12 ephem  │ bool     │ Ephemeral flag (don't persist if offline)     │
│ 14 urgent │ bool     │ Urgent flag (default true)                    │
│ 15 pniUuid│ string   │ Updated PNI for number change sync            │
│ 16 story  │ bool     │ Story message flag                            │
│ 17 spamTok│ bytes    │ Report spam token                              │
│ 18        │ reserved │ internal server use                           │
│ 19 srcBin │ bytes    │ Source service ID binary (16 bytes UUID)      │
│ 20 dstBin │ bytes    │ Destination service ID binary                 │
│ 21 guidBin│ bytes    │ Server GUID binary (16 bytes UUID)            │
│ 22 pniBin │ bytes    │ Updated PNI binary                            │
└───────────┴──────────┴────────────────────────────────────────────────┘

Total: 22 fields (mirrors Signal's Envelope exactly)
```

### 3.4 Sending Rules

| Field | Set By | Required | Notes |
|-------|--------|----------|-------|
| `type` | Client | Yes | Must be DOUBLE_RATCHET or PREKEY_MESSAGE |
| `sourceServiceId` | Server | — | Populated on relay |
| `sourceDeviceId` | Server | — | Populated on relay |
| `destinationServiceId` | Client | Yes | Target user's UUID |
| `clientTimestamp` | Client | Yes | ISO 8601 epoch millis |
| `content` | Client | Yes | Encrypted Content bytes |
| `serverGuid` | Server | — | Assigned on receipt |
| `serverTimestamp` | Server | — | Assigned on receipt |
| `ephemeral` | Client | No | True for typing/receipts |
| `urgent` | Client | No | Defaults to true |
| `story` | Client | No | True for story messages |

### 3.5 Per-Type Content Rules

```
Type DOUBLE_RATCHET (1):
  content = Double Ratchet ciphertext of serialized Content protobuf
  → Recipient: load existing session → DoubleRatchet.decrypt() → Content

Type PREKEY_MESSAGE (3):
  content = X3DH ciphertext (header + Double Ratchet ciphertext)
  → Recipient: X3DH.bobRespond() → establish session → decrypt inner Content

Type UNIDENTIFIED_SENDER (6):
  content = Sealed sender ciphertext
  → Recipient: decrypt with profile key → get sender identity → process inner Content

Type PLAINTEXT_CONTENT (8):
  content = Unencrypted Content protobuf (only for DecryptionErrorMessage)
  → Recipient: process error, archive session, request retry

Type SERVER_DELIVERY_RECEIPT (5):
  content = empty
  → Recipient: update message status to DELIVERED
```

---

## 4. Content.proto

**File:** `core/protos/src/main/proto/enchant/Content.proto`
**Generated class:** `org.enchant.protos.Content`

### 4.1 Purpose

The `Content` message is the **inner payload** that carries the actual message data. It is serialized, then encrypted via Double Ratchet (or X3DH for prekey messages) to produce the `content` field in `Envelope`. The server never sees this — it's opaque E2EE binary.

### 4.2 Structure

```
Content (oneof):
┌───────────┬──────────┬───────────────────────────────────────────────┐
│ Field 1   │ DataMsg  │ data_message — text, media, reactions, etc.  │
│ Field 2   │ SyncMsg  │ sync_message — multi-device synchronization  │
│ Field 3   │ CallMsg  │ call_message — WebRTC signaling              │
│ Field 4   │ NullMsg  │ null_message — padding/cover traffic         │
│ Field 5   │ RcptMsg  │ receipt_message — delivery/read receipts     │
│ Field 6   │ TypeMsg  │ typing_message — typing indicator             │
│ Field 7   │ bytes    │ sender_key_distribution_message (future)     │
│ Field 8   │ bytes    │ decryption_error_message                     │
│ Field 9   │ StoryMsg │ story_message — status/story content          │
│ Field 10  │ PniSig   │ pni_signature_message                        │
│ Field 11  │ EditMsg  │ edit_message — message edits                 │
└───────────┴──────────┴───────────────────────────────────────────────┘

Exactly one content type is set (oneof).
```

### 4.3 Integration Flow

```
┌────────────────────────────────────────────────────────────────┐
│                        Envelope                                  │
│  type: DOUBLE_RATCHET                                            │
│  content: [Double Ratchet encrypted bytes]                       │
│  sourceServiceId: "abc-123"                                      │
│  destinationServiceId: "xyz-789"                                 │
└───────────────────────┬────────────────────────────────────────┘
                        │ decrypt via Double Ratchet
                        ▼
┌────────────────────────────────────────────────────────────────┐
│                       Content                                    │
│  data_message: {                                                 │
│    body: "Hello!",                                               │
│    timestamp: 1715000000000,                                     │
│    expireTimer: 86400                                            │
│  }                                                               │
└────────────────────────────────────────────────────────────────┘
```

---

## 5. DataMessage.proto

**File:** `core/protos/src/main/proto/enchant/DataMessage.proto`
**Generated class:** `org.enchant.protos.DataMessage`

### 5.1 Purpose

`DataMessage` is the actual user-generated content — text, media, reactions, edits, deletions, quotes, contacts, polls, stickers, gift badges, payments, group call updates, and story contexts. This is the message users see in the chat.

### 5.2 Fields (30 total)

```
┌──────────┬──────────┬─────────────────────────────────────────────────┐
│ Field    │ Type     │ Description                                     │
├──────────┼──────────┼─────────────────────────────────────────────────┤
│ 1  body  │ string   │ Message text (empty for media-only messages)    │
│ 2  atts  │ repeated │ AttachmentPointer — media attachments           │
│          │          │                                                  │
│ 3  (reserved)       │ (was GroupContext v1)                           │
│ 15 groupV2│ GroupCtx│ GroupContextV2 — group info for group messages  │
│          │          │                                                  │
│ 4  flags │ uint32   │ Flags: END_SESSION=1, EXPIRATION_TIMER_UPDATE=2,│
│          │          │        PROFILE_KEY_UPDATE=4                     │
│ 5  timer │ uint32   │ Disappearing message timer (seconds)           │
│ 23 timerVer│ uint32 │ Timer version counter                           │
│ 6  profKy│ bytes    │ Profile key (32 bytes)                        │
│ 7  ts    │ uint64   │ Sender timestamp (epoch millis)               │
│          │          │                                                  │
│ 8  quote │ Quote    │ Quoted/replied-to message                      │
│ 9  cntct │ repeated │ Contact — shared contact cards                 │
│ 10 prev  │ repeated │ Preview — link previews (OpenGraph metadata)   │
│ 11 stickr│ Sticker  │ Sticker attachment                             │
│ 12 reqVer│ uint32   │ Required protocol version                      │
│ 14 viewOnce│ bool   │ View-once media flag                           │
│          │          │                                                  │
│ 16 reactn│ Reaction │ Emoji reaction on a message                    │
│ 17 delete│ Delete   │ Delete message (for everyone)                  │
│ 18 bodyRg│ repeated │ BodyRange — text formatting ranges             │
│ 19 grpCal│GroupCall │ Group call update (era ID)                     │
│ 20 paymnt│ Payment  │ Payment notification/activation (MobileCoin)   │
│ 21 storyC│ StoryCtx │ Story context (reply to story)                 │
│ 22 giftBg│ GiftBadge│ Gift badge receiptCredentialPresentation blob  │
│          │          │                                                  │
│ 24 pollCr│PollCreate│ Poll creation                                   │
│ 25 pollTm│PollTerm  │ Poll termination (targetSentTimestamp)          │
│ 26 pollVt│ PollVote │ Poll vote                                       │
│ 27 pinMsg│ PinMsg   │ Pin message to conversation                    │
│ 28 unpin │ UnpinMsg │ Unpin message from conversation                │
│ 29 admDel│ AdminDel │ Admin delete (group admin removes message)     │
└──────────┴──────────┴─────────────────────────────────────────────────┘
```

### 5.3 Sub-Message: Quote

```
Quote:
  id (uint64)         —— target message timestamp
  authorAci (string)  —— original author's ACI
  text (string)       —— quoted text preview
  attachments (repeated AttachmentPointer) —— quoted media
  bodyRanges (repeated BodyRange)
  type (Type: NORMAL=0, GIFT_BADGE=1, POLL=2)
```

### 5.4 Sub-Message: Reaction

```
Reaction:
  emoji (string)       —— Unicode emoji
  remove (bool)        —— true = remove reaction (toggle)
  targetAuthorAci      —— message author's ACI
  targetSentTimestamp  —— target message timestamp
```

### 5.5 Sub-Message: Delete

```
Delete:
  targetSentTimestamp  —— message to delete (for everyone)
```

### 5.6 Sub-Message: Contact

```
Contact:
  name (Name)          —— givenName, familyName, prefix, suffix, middleName, nickname
  number (repeated Phone) —— value, type (HOME=1, MOBILE=2, WORK=3, CUSTOM=4), label
  email (repeated Email)   —— value, type, label
  address (repeated PostalAddress) —— type, label, street, pobox, neighborhood, city, region, postcode, country
  avatar (Avatar)      —— avatar AttachmentPointer + isProfile flag
  organization (string)
```

### 5.7 Sub-Message: Preview (Link Preview)

```
Preview:
  url (string)         —— link URL
  title (string)       —— OpenGraph title
  image (AttachmentPointer) —— preview image
  description (string) —— OpenGraph description
  date (uint64)        —— article date
```

### 5.8 Sub-Message: Sticker

```
Sticker:
  packId (bytes)       —— 16-byte pack UUID
  packKey (bytes)      —— sticker encryption key
  stickerId (uint32)   —— sticker index in pack
  data (AttachmentPointer) —— sticker image
  emoji (string)       —— associated emoji
```

### 5.9 Sub-Message: PollCreate

```
PollCreate:
  question (string)         —— poll question
  allowMultiple (bool)      —— allow multiple votes
  options (repeated string) —— poll options (2-12)
```

### 5.10 Sub-Message: PollVote

```
PollVote:
  targetSentTimestamp (uint64) —— poll message timestamp
  optionIndexes (repeated uint32) —— selected option indices
  voteCount (uint32)              —— total votes cast (for multi-vote)
```

### 5.11 Sub-Message: Payment

```
Payment:
  oneof Item:
    notification (Notification) —— MobileCoin receipt notification
    activation (Activation)      —— REQUEST=0, ACTIVATED=1
```

### 5.12 Sub-Message: PollTerminate

```
PollTerminate:
  targetSentTimestamp (uint64)   —— poll message to close
```

### 5.13 Sub-Message: GroupCallUpdate

GroupCallUpdate:
  eraId (string)        —— call era identifier
```

### 5.12 Sub-Message: Payment

```
Payment:
  oneof:
    notification (Notification) —— MobileCoin receipt
    activation (Activation)      —— payment activation (REQUEST=0, ACTIVATED=1)
```

### 5.13 Sub-Message: GiftBadge

```
GiftBadge:
  receiptCredentialPresentation (bytes) —— presentation blob
```

### 5.14 Sub-Message: StoryContext

```
StoryContext:
  authorAci (string)   —— story author
  sentTimestamp (uint64) —— story timestamp
```

### 5.15 Sub-Message: PinMessage / UnpinMessage / AdminDelete

```
PinMessage:
  targetSentTimestamp (uint64)
  oneof pinDuration:
    pinDurationSeconds (uint32)
    pinDurationForever (bool)

UnpinMessage:
  targetSentTimestamp (uint64)

AdminDelete:
  targetSentTimestamp (uint64)
```

---

## 6. SyncMessage.proto

**File:** `core/protos/src/main/proto/enchant/SyncMessage.proto`
**Generated class:** `org.enchant.protos.SyncMessage`

### 6.1 Purpose

`SyncMessage` carries multi-device synchronization data between a user's own devices. These messages are encrypted with the Double Ratchet session between the user's devices and are only processed by the user's own linked devices.

### 6.2 Structure (oneof + repeated)

```
SyncMessage:
  oneof content:
    sent (Sent)                       —— relayed outgoing messages
    contacts (Contacts)               —— contact sync blob
    request (Request)                 —— request data from other device
    blocked (Blocked)                 —— blocked list sync
    verified (Verified)               —— identity verification status
    configuration (Configuration)     —— app settings sync
    viewOnceOpen (ViewOnceOpen)       —— view-once opened receipt
    fetchLatest (FetchLatest)         —— request latest version of data
    keys (Keys)                       —— sync cryptographic keys
    messageRequestResponse (MsgReqRsp)—— message request decision
    outgoingPayment (OutgoingPayment) —— payment sync
    pniChangeNumber (PniChange)       —— phone number change
    callEvent (CallEvent)             —— call history sync
    callLinkUpdate (CallLinkUpdate)   —— call link metadata sync
    callLogEvent (CallLogEvent)       —— call log management
    deleteForMe (DeleteForMe)         —— sync deletions across devices
    deviceNameChange (DevNameChange)  —— device name change
    attachmentBackfillReq (AttBackReq)  —— request attachment from other device
    attachmentBackfillRsp (AttBackRsp)  —— provide attachment to other device

  repeated read (Read)                —— read receipts
  repeated stickerPackOp (StickerOp)  —— sticker pack operations
  repeated viewed (Viewed)            —— viewed receipts
  padding (bytes)                     —— padding for traffic analysis
```

### 6.3 Sub-Message: Sent

```
Sent:
  destinationServiceId (string)  —— original recipient
  timestamp (uint64)             —— original message timestamp
  message (DataMessage)          —— original message content
  storyMessage (StoryMessage)    —— original story content
  editMessage (EditMessage)      —— original edit content
  expirationStartTimestamp (uint64) —— when timer started
  unidentifiedStatus (repeated UnidentifiedDeliveryStatus)
    —— per-recipient sealed sender status
  isRecipientUpdate (bool)       —— update to existing sent message
  storyMessageRecipients (repeated StoryMessageRecipient)
    —— per-recipient story distribution
```

### 6.4 Sub-Message: Configuration

```
Configuration:
  readReceipts (bool)              —— read receipts enabled
  unidentifiedDeliveryIndicators (bool) —— sealed sender indicators
  typingIndicators (bool)          —— typing indicators enabled
  linkPreviews (bool)              —— link previews enabled
```

### 6.5 Sub-Message: Request

```
Request:
  type (Type: CONTACTS=1, BLOCKED=3, CONFIGURATION=4, KEYS=5)
```

### 6.6 Sub-Message: Blocked

```
Blocked:
  numbers (repeated string)        —— blocked phone numbers
  acis (repeated string)           —— blocked ACI UUIDs
  groupIds (repeated bytes)        —— blocked group IDs
```

### 6.7 Sub-Message: CallEvent

```
CallEvent:
  conversationId (bytes)           —— conversation identifier
  callId (uint64)                  —— call identifier
  timestamp (uint64)               —— when it happened
  type (Type: AUDIO=1, VIDEO=2, GROUP=3, AD_HOC=4)
  direction (Dir: INCOMING=1, OUTGOING=2)
  event (Event: ACCEPTED=1, NOT_ACCEPTED=2, DELETE=3, OBSERVED=4)
```

### 6.8 Sub-Message: DeleteForMe

```
DeleteForMe:
  messageDeletes (repeated MsgDeletes)     —— delete specific messages
  conversationDeletes (repeated ConvDelete) —— delete conversations
  localOnlyConvDeletes (repeated LocalOnly) —— local-only deletion
  attachmentDeletes (repeated AttDelete)    —— delete attachments

  MessageDeletes:
    conversation (ConversationIdentifier)
    messages (repeated AddressableMessage)

  ConversationDelete:
    conversation (ConversationIdentifier)
    mostRecentMessages (repeated AddressableMessage)
    isFullDelete (bool)
```

### 6.9 Sub-Message: Verified

```
Verified:
  destinationAci (string)         —— verified user's ACI
  identityKey (bytes)             —— verified identity key
  state (State: DEFAULT=0, VERIFIED=1, UNVERIFIED=2)
  nullMessage (bytes)             —— padding
```

### 6.10 Sub-Message: OutgoingPayment

```
OutgoingPayment:
  recipientServiceId (string)     —— payment recipient
  note (string)                   —— payment note
  oneof attachment_identifier:
    mobileCoin (MobileCoin)        —— MobileCoin receipt with
      recipientAddress, amountPicoMob, feePicoMob, receipt,
      ledgerBlockTimestamp, ledgerBlockIndex,
      spentKeyImages[], outputPublicKeys[]
```

### 6.11 Sub-Message: PniChangeNumber

```
PniChangeNumber:
  identityKeyPair (bytes)         —— serialized IdentityKeyPair
  signedPreKey (bytes)            —— serialized SignedPreKeyRecord
  lastResortKyberPreKey (bytes)   —— serialized KyberPreKeyRecord
  registrationId (uint32)         —— new registration ID
  newE164 (string)                —— new phone number
```

### 6.12 Sub-Message: CallLinkUpdate

```
CallLinkUpdate:
  rootKey (bytes)                 —— call link root key
  adminPasskey (bytes)            —— admin authentication
  type (Type: UPDATE=0)
```

### 6.13 Sub-Message: DeviceNameChange

```
DeviceNameChange:
  deviceId (uint32)               —— device whose name changed
```

### 6.14 Sub-Message: AttachmentBackfillRequest

```
AttachmentBackfillRequest:
  targetMessage (AddressableMessage)          —— message needing attachment
  targetConversation (ConversationIdentifier) —— which conversation
```

### 6.15 Sub-Message: AttachmentBackfillResponse

```
AttachmentBackfillResponse:
  targetMessage (AddressableMessage)
  targetConversation (ConversationIdentifier)
  oneof data:
    attachments (AttachmentDataList)  —— requested attachment data
      with repeated AttachmentData (each is attachment or PENDING/TERMINAL_ERROR status)
    error (Error: MESSAGE_NOT_FOUND=0)
```

---

## 7. CallMessage.proto

**File:** `core/protos/src/main/proto/enchant/CallMessage.proto`
**Generated class:** `org.enchant.protos.CallMessage`

### 7.1 Purpose

`CallMessage` carries WebRTC signaling data for voice/video calls. It supports both the legacy SDP-based approach (offer/answer with SDP strings) and Signal's newer `Opaque` message approach which uses encrypted opaque bytes for multi-ring support.

### 7.2 Structure

```
CallMessage:
  offer (Offer)                    —— call offer
  answer (Answer)                  —— call answer
  iceUpdate (repeated IceUpdate)   —— ICE candidates
  busy (Busy)                      —— remote party is busy
  hangup (Hangup)                  —— call ended
  destinationDeviceId (uint32)     —— target device for multi-device
  opaque (Opaque)                  —— opaque signaling for multi-ring

Offer:
  id (uint64)                      —— call ID
  type (Type: AUDIO=0, VIDEO=1)
  opaque (bytes)                   —— opaque offer data (Signal's new approach)

Answer:
  id (uint64)                      —— matching offer ID
  opaque (bytes)                   —— opaque answer data

IceUpdate:
  id (uint64)                      —— matching call ID
  opaque (bytes)                   —— opaque ICE candidate

Hangup:
  id (uint64)                      —— call ID
  type (Type: NORMAL=0, ACCEPTED=1, DECLINED=2, BUSY=3, NEED_PERMISSION=4)
  deviceId (uint32)                —— which device hung up

Busy:
  id (uint64)                      —— call ID

Opaque:
  data (bytes)                     —— opaque signaling bytes
  urgency (Urgency: DROPPABLE=0, HANDLE_IMMEDIATELY=1)
```

### 7.3 Signaling Flow

```
Alice                          MRS                           Bob
  │                              │                              │
  │── CALL_OFFER (Envelope ─────▶│── CALL_OFFER (Envelope ────▶│
  │   type=CALL_OFFER)           │   type=CALL_OFFER)           │
  │                              │                              │
  │                              │◀── CALL_ANSWER ─────────────│
  │◀── CALL_ANSWER ─────────────│                              │
  │                              │                              │
  │── CALL_ICE (ICE candidate) ─▶│── CALL_ICE ────────────────▶│
  │                              │                              │
  │◀── CALL_ICE ─────────────────│◀── CALL_ICE ────────────────│
  │                              │                              │
  │                              │       [PEER CONNECTION ESTABLISHED]
  │◀══════════════════ WebRTC media ═══════════════════════════▶│
  │                              │                              │
  │── CALL_END ─────────────────▶│── CALL_END ────────────────▶│
  │                              │                              │
```

---

## 8. ReceiptMessage.proto

**File:** `core/protos/src/main/proto/enchant/ReceiptMessage.proto`
**Generated class:** `org.enchant.protos.ReceiptMessage`

### 8.1 Purpose

Acknowledges message delivery and read status. These are ephemeral messages — the server delivers them but they are not persisted in the offline queue.

### 8.2 Structure

```
ReceiptMessage:
  type (Type: DELIVERY=0, READ=1, VIEWED=2)
  timestamp (repeated uint64)     —— timestamps of messages being acknowledged

DELIVERY: Sent when the message is delivered to at least one device
READ:     Sent when the recipient opens and reads the message
VIEWED:   Sent when view-once media is viewed
```

---

## 9. TypingMessage.proto

**File:** `core/protos/src/main/proto/enchant/TypingMessage.proto`
**Generated class:** `org.enchant.protos.TypingMessage`

### 9.1 Purpose

Indicates that the remote user is typing. These are ephemeral messages with `ephemeral=true` in the Envelope — the server does not persist them.

### 9.2 Structure

```
TypingMessage:
  timestamp (uint64)               —— client timestamp
  action (Action: STARTED=0, STOPPED=1)
  groupId (bytes, optional)       —— group conversation ID
```

### 9.3 Throttling Rules

- Minimum interval between STARTED signals: 3 seconds
- Auto-send STOPPED after 5 seconds of inactivity
- Group typing is only sent for groups < 100 members

---

## 10. AttachmentPointer.proto

**File:** `core/protos/src/main/proto/enchant/AttachmentPointer.proto`
**Generated class:** `org.enchant.protos.AttachmentPointer`

### 10.1 Purpose

`AttachmentPointer` carries metadata about an encrypted media attachment. The actual media bytes are encrypted and stored on the Media server. The pointer contains the decryption key, integrity hash, and display metadata.

### 10.2 Fields (20 total)

```
┌──────────┬──────────┬─────────────────────────────────────────────────┐
│ Field    │ Type     │ Description                                     │
├──────────┼──────────┼─────────────────────────────────────────────────┤
│ 1  cdnId │ fixed64  │ Legacy CDN ID (deprecated, use cdnKey)         │
│ 15 cdnKey│ string   │ CDN key (opaque identifier)                    │
│ 20 clntId│ bytes    │ Cross-client UUID for attachment dedup         │
│          │          │                                                  │
│ 2  cType │ string   │ MIME type (e.g. "image/jpeg")                  │
│ 3  key   │ bytes    │ AES-256-GCM encryption key for media decryption│
│ 4  size  │ uint32   │ File size in bytes                             │
│ 5  thumb │ bytes    │ Encrypted thumbnail (JPEG/PNG)                 │
│ 6  digest│ bytes    │ SHA-256 hash of encrypted blob on CDN          │
│ 7  fName │ string   │ Original filename                              │
│ 8  flags │ uint32   │ Flags: VOICE_MESSAGE=1, BORDERLESS=2, GIF=8   │
│ 9  width │ uint32   │ Image/video width in pixels                    │
│ 10 height│ uint32   │ Image/video height in pixels                   │
│ 11 captn │ string   │ Caption text                                   │
│ 12 blur  │ string   │ BlurHash for blurry placeholder                │
│ 13 upTs  │ uint64   │ Upload timestamp                               │
│ 14 cdnNum│ uint32   │ CDN number (which CDN hosts this file)        │
│ 16 incMac│ bytes    │ Incremental MAC for streaming verification     │
│ 17 chnkSz│ uint32   │ Chunk size for incremental MAC                │
│ 18 incMac│ bytes    │ Incremental MAC (all attachment sizes)        │
│ 19 incMac│ bytes    │ Incremental MAC with implicit chunk sizing    │
└──────────┴──────────┴─────────────────────────────────────────────────┘

Note on incremental MACs: Fields 16-19 are different versions of the same
concept. Field 19 is the current one. Fields 16 and 18 are for
backward compatibility.
```

### 10.3 Media Encryption

```
Media files are encrypted with AES-256-GCM:
  key  = 32 random bytes (AttachmentPointer.key)
  iv   = 12 random bytes (stored as first 12 bytes of ciphertext)
  ad   = empty (no associated data for media)

Encrypted blob format:
  [12-byte IV] [AES-256-GCM ciphertext] [16-byte GCM tag]

The SHA-256 of the complete encrypted blob (IV + ciphertext + tag) is
stored in AttachmentPointer.digest for integrity verification on download.
```

### 10.4 Flags

| Flag | Value | Meaning |
|------|-------|---------|
| VOICE_MESSAGE | 1 | Voice recording (show waveform) |
| BORDERLESS | 2 | Borderless sticker-like display |
| GIF | 8 | Animated GIF (auto-play) |

---

## 11. BodyRange.proto

**File:** `core/protos/src/main/proto/enchant/BodyRange.proto`
**Generated class:** `org.enchant.protos.BodyRange`

### 11.1 Purpose

`BodyRange` describes text formatting and mentions within a message body. It uses UTF-16 code unit offsets (consistent with Java/Kotlin string indexing).

### 11.2 Structure

```
BodyRange:
  start (uint32)           —— start offset in UTF-16 code units
  length (uint32)          —— length in UTF-16 code units

  oneof associatedValue:
    mentionAci (string)    —— mentioned user's ACI (for MENTION type)
    style (Style)          —— formatting style
    mentionAciBinary (bytes) —— 16-byte UUID binary

Style:
  NONE           = 0
  BOLD           = 1
  ITALIC         = 2
  SPOILER        = 3
  STRIKETHROUGH  = 4
  MONOSPACE      = 5
```

### 11.3 Types of BodyRanges

| Style | Display | Example |
|-------|---------|---------|
| BOLD | Bold text | `**hello**` |
| ITALIC | Italic text | `*hello*` |
| SPOILER | Hidden until tapped | `||spoiler||` |
| STRIKETHROUGH | Strikethrough | `~~oops~~` |
| MONOSPACE | Code/monospace | `` `code` `` |
| MENTION | @mention (uses mentionAci) | `@Alice` |

---

## 12. StoryMessage.proto

**File:** `core/protos/src/main/proto/enchant/StoryMessage.proto`
**Generated class:** `org.enchant.protos.StoryMessage`

### 12.1 Purpose

Carries status/story content — 24-hour ephemeral updates that appear in a separate story feed, not the conversation list.

### 12.2 Structure

```
StoryMessage:
  profileKey (bytes)                    —— sender's profile key (for access control)
  group (GroupContextV2, optional)      —— group story
  oneof attachment:
    fileAttachment (AttachmentPointer)  —— image/video story
    textAttachment (TextAttachment)     —— text-only story
  allowsReplies (bool)                  —— whether replies are allowed
  bodyRanges (repeated BodyRange)       —— text formatting

TextAttachment:
  text (string)                         —— story text content
  textStyle (Style: DEFAULT=0, REGULAR=1, BOLD=2, SERIF=3, SCRIPT=4, CONDENSED=5)
  textForegroundColor (uint32)          —— text color as ARGB hex
  textBackgroundColor (uint32)          —— text background ARGB hex
  preview (Preview)                     —— link preview
  oneof background:
    gradient (Gradient)                 —— gradient background
    color (uint32)                      —— solid color background

  Gradient:
    startColor (uint32)                 —— deprecated
    endColor (uint32)                   —— deprecated
    angle (uint32)                      —— gradient angle (degrees)
    colors (repeated uint32)            —— gradient color stops (ARGB)
    positions (repeated float)          —— gradient stop positions (0.0-1.0)
```

### 12.3 Story Lifecycle

```
Create: User captures/takes photo → encrypts → uploads to Media server
        → creates StoryMessage → wrapped in Envelope(type=DOUBLE_RATCHET, story=true)
        → sent to contacts via MRS (or to specific distribution list)

View:   Recipient opens story → sends ReceiptMessage(type=VIEWED)
        → sender can see viewer list for 24h

Expire: Story deleted after 24 hours (server-enforced via expires_at)
        → local DB deletes story after expiry
```

---

## 13. GroupContext.proto

**File:** `core/protos/src/main/proto/enchant/GroupContext.proto`
**Generated class:** `org.enchant.protos.GroupContext`

### 13.1 Purpose

Carries group v2 metadata for group messages. Groups use the Signal v2 protocol with encrypted group state stored on the server and decrypted locally.

### 13.2 Structure

```
GroupContextV2:
  masterKey (bytes)             —— 32-byte group master key
  revision (uint32)             —— group state revision number
  groupChange (bytes, optional) —— encrypted group change proto
```

### 13.3 Group Change Actions

The `groupChange` bytes, when decrypted, contain a `GroupChange.Actions` message:

```
GroupChange:
  revision (uint32)               —— new revision number

  Actions:
    addMember (repeated Member)
    deleteMember (repeated uint32)
    modifyMemberRole (repeated MemberRole)
    modifyTitle (string)
    modifyAvatar (string)
    modifyDisappearTimer (uint32)
    modifyAttributesAccess (AccessControl)
    modifyMemberAccess (AccessControl)
    modifyAddFromInviteLinkAccess (AccessControl)
    addPendingMember (repeated PendingMember)
    deletePendingMember (repeated uint32)
    promotePendingMember (repeated uint32)
    addRequestingMember (repeated RequestingMember)
    deleteRequestingMember (repeated uint32)
    promoteRequestingMember (repeated uint32)
    addBannedMember (repeated BannedMember)
    deleteBannedMember (repeated uint32)
    modifyInviteLinkPassword (bytes)
    modifyAnnouncementsOnly (bool)
    modifyDescription (bytes)
```

---

## 14. Provisioning.proto

**File:** `core/protos/src/main/proto/enchant/Provisioning.proto`
**Generated class:** `org.enchant.protos.Provisioning`

### 14.1 Purpose

Handles linking new devices to the user's account. When the user scans a QR code on a secondary device, the primary device sends provisioning messages through a temporary WebSocket.

### 14.2 Structure

```
ProvisioningAddress:
  uuid (string)                    —— device UUID
  deviceId (uint32)                —— device ID

ProvisionEnvelope:
  publicKey (bytes)                —— secondary device's ephemeral public key
  ciphertext (bytes)               —— encrypted ProvisionMessage (box sealed)

ProvisionMessage:
  aciIdentityKeyPublic (bytes)     —— primary's ACI identity public key
  aciIdentityKeyPrivate (bytes)    —— primary's ACI identity private key
  pniIdentityKeyPublic (bytes)     —— primary's PNI identity public key (if applicable)
  pniIdentityKeyPrivate (bytes)    —— primary's PNI identity private key
  aciUuid (string)                 —— primary's ACI UUID
  pniUuid (string)                 —— primary's PNI UUID (if applicable)
  phoneNumber (string)             —— primary's phone number
  provisioningCode (string)        —— one-time provisioning code
  userAgent (string)               —— primary device user agent
  profileKey (bytes)               —— primary's profile key (32 bytes)
  readReceipts (bool)              —— read receipts setting
  primaryBackupKey (bytes)         —— backup key (32 bytes)
  accountEntropyPool (string)      —— account entropy for key derivation
  masterKey (bytes)                —— group master key
  mediaRootBackupKey (bytes)       —— media backup key
```

---

## 15. StorageService.proto

**File:** `core/protos/src/main/proto/enchant/StorageService.proto`
**Generated class:** `org.enchant.protos.StorageService`

### 15.1 Purpose

Implements manifest-based multi-device state synchronization. Each device maintains a local manifest (version + digest of all synced items). When a change is made on one device, it uploads the change to the server, and other devices fetch and apply it.

### 15.2 Core Messages

```
StorageManifest:
  version (uint64)                 —— manifest version number

ManifestRecord:
  version (uint64)                 —— version
  identifiers (repeated Identifier) —— items in this manifest

  Identifier:
    type (Type: CONTACT=1, GROUPV1=2, GROUPV2=3, ACCOUNT=4, STORY_DIST=5, CALL_LINK=6, CHAT_FOLDER=7, NOTIF_PROFILE=8)
    raw (bytes)                    —— opaque identifier bytes

StorageItem:
  key (bytes)                      —— item key
  value (bytes)                    —— serialized StorageRecord

StorageRecord:
  oneof record:
    contact (ContactRecord)
    groupV1 (GroupV1Record)
    groupV2 (GroupV2Record)
    account (AccountRecord)
    storyDistributionList (StoryDistListRecord)
    callLink (CallLinkRecord)
    chatFolder (ChatFolderRecord)
    notificationProfile (NotifProfileRecord)
```

### 15.3 ContactRecord

```
ContactRecord:
  serviceAci (string)             —— contact's ACI
  servicePni (string)             —— contact's PNI
  e164 (string)                   —— phone number
  profileKey (bytes)              —— contact's profile key
  identityKey (bytes)             —— contact's identity key
  identityState (IdentityState: VERIFIED=1, UNVERIFIED=2)
  blocked (bool)                  —— is blocked
  whitelisted (bool)              —— message request approved
  archived (bool)                 —— conversation archived
  mutedUntilTimestamp (uint64)    —— mute expiration
  hideStory (bool)                —— hide stories from this contact
  unregisteredTimestamp (uint64)  —— when they unregistered
  nickname (Nickname):
    givenName (string)
    familyName (string)
  note (string)                   —— note about contact
  avatarColor (uint32)            —— avatar circle color
```

### 15.4 AccountRecord

```
AccountRecord:
  profileKey (bytes)
  displayName (string)
  about (string)
  avatarUrl (string)
  readReceipts (bool)
  typingIndicators (bool)
  linkPreviews (bool)
  pinnedConversations (repeated PinnedConversation)
  reactionEmoji (repeated string)
  donateSubscriber (bool)
  backupEnabled (bool)
  username (string)
  notificationProfileMode (NotifMode)
  phoneNumberSharing (PhoneNumSharing)
  autoKeyVerify (bool)
  keepMutedChatsArchived (bool)
  myStoriesPrivacy (MyStoriesPrivacy)
  viewedReceipts (bool)
  dataAllDont (bool)
  preferredReactionEmoji (repeated string)
  subscription (Subscription)
  defaultTimer (uint32)
  backupRemote (BackupRemote):
    lastBackupTime (uint64)
    lastBackupSize (uint64)
```

---

## 16. SessionRecord.proto

**File:** `core/crypto/src/main/proto/enchant/SessionRecord.proto`
**Generated class:** `org.enchant.core.crypto.protos.SessionRecord`

### 16.1 Purpose

Serializes the Double Ratchet session state for persistence in the local database. This is the on-disk format for `signal_sessions` table's `serialized_session` BLOB column.

### 16.2 Structure

```
SessionRecord:
  version (int32)                          —— schema version for forward compat

  sessionState (SessionState):
    aliceBaseKey (bytes)                   —— sender's base ratchet key
    rootKey (bytes)                        —— current root key (32 bytes)

    sendingChainKey (bytes, optional)      —— current sending chain key (32 bytes)
    sendingRatchetKeyPublic (bytes, optional)  —— current DH public key
    sendingRatchetKeyPrivate (bytes, optional) —— current DH secret key
    sendingMessageNumber (int32)           —— next sending message number

    receivingChainKey (bytes, optional)    —— current receiving chain key (32 bytes)
    receivingRatchetKeyPublic (bytes, optional) —— peer's DH public key
    receivingMessageNumber (int32)         —— next receiving message number
    previousChainLength (int32)            —— previous chain length for header

    skippedMessageKeys:
      key (string)                         —— "dhPublic:msgNum"
      messageKey (MessageKey):
        key (bytes)                        —— AEAD key (32 bytes)
        nonce (bytes)                      —— AEAD nonce (12 bytes)
        chainKey (bytes)                   —— chain key used to derive this (32 bytes)

    ourIdentityPublic (bytes)              —— our Ed25519 public key (32 bytes)
    theirIdentityPublic (bytes)            —— peer's X25519-converted public key (32 bytes)
    theirSignedPrekeyId (int32, optional)  —— which SPK was used
    createdAt (uint64)                     —— session creation timestamp
```

---

## 17. InternalSerialization.proto

**File:** `core/protos/src/main/proto/enchant/InternalSerialization.proto`
**Generated class:** `org.enchant.protos.InternalSerialization`

### 17.1 Purpose

Internal metadata for the message processing pipeline — wraps content + sender identity + processing metadata. Used in the in-memory processing queue, not sent over the wire.

### 17.2 Structure

```
EnvelopeMetadata:
  sourceServiceId (bytes)            —— sender's ACI binary UUID
  sourceE164 (string, optional)      —— sender's phone number
  sourceDeviceId (int32)             —— sender's device ID
  sealedSender (bool)                —— is sealed sender
  groupId (bytes, optional)          —— group ID if group message
  destinationServiceId (bytes)       —— recipient ACI binary UUID
  ciphertextMessageType (int32)      —— original envelope type (1, 3, 6, or 8)

CompleteMessage:
  envelope (bytes)                   —— raw Envelope bytes (for retry/replay)
  content (bytes)                    —— decrypted Content bytes
  metadata (EnvelopeMetadata)        —— processing metadata
  serverDeliveredTimestamp (int64)   —— server delivery time

SignalServiceContentProto:
  localAddress (AddressProto)        —— our own address
  metadata (MetadataProto)           —— processing metadata
  oneof data:
    legacyDataMessage (DataMessage)  —— legacy Content-less messages
    content (Content)                —— modern Content wrapper

MetadataProto:
  address (AddressProto)             —— sender address
  senderDevice (int32)               —— sender device ID
  timestamp (int64)                  —— client timestamp
  serverReceivedTimestamp (int64)    —— server receive time
  serverDeliveredTimestamp (int64)   —— server delivery time
  needsReceipt (bool)                —— whether to send delivery receipt
  serverGuid (string)                —— server-assigned GUID
  groupId (bytes, optional)          —— group ID
  destinationUuid (string)           —— destination UUID

AddressProto:
  uuid (bytes, optional)             —— service ID binary UUID
  e164 (string, optional)            —— phone number
```

---

## 18. Build Integration

### 18.1 Module Build File

```kotlin
// core/protos/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "org.enchant.protos"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation(libs.protobuf.javalite)
}
```

### 18.2 Module Dependencies

| Module | Depends On |
|--------|------------|
| `:core:network` | `:core:protos` (for WebSocket frames, Envelope) |
| `:core:database` | `:core:protos` (for SessionRecord, Content) |
| `:core:crypto` | `:core:protos` (for SessionRecord, Content) |
| `:core:calls` | `:core:protos` (for CallMessage) |
| `:feature:chat` | `:core:protos` (for DataMessage, Content, BodyRange) |
| `:feature:status` | `:core:protos` (for StoryMessage) |
| `:feature:groups` | `:core:protos` (for GroupContext) |
| `:feature:settings` | `:core:protos` (for SyncMessage.Configuration) |

---

## 19. Cross-Reference: Backend ↔ Protos

| Backend API (Endpoint) | Request/Response Format | Protobuf Type | Phase |
|---|---|---|---|
| WebSocket AUTH | `WebSocketMessage(type=REQUEST).body` = raw JWT | WebSocketResources | 1 |
| WebSocket MESSAGE | `WebSocketMessage(type=REQUEST).body` = Envelope | Envelope | 3 |
| WebSocket KEEPALIVE | `WebSocketMessage(type=REQUEST)`, empty body | WebSocketResources | 1 |
| REST /v1/messages/send | JSON body | (JSON serialized SendMessageRequest) | 3 |
| REST /v1/keys/register | JSON body | (JSON) | 2 |
| REST /v1/media/upload | Raw binary | (none — raw encrypted bytes) | 3 |
| StorageService | WebSocket frames with StorageItem | StorageService | 6 |
| Provisioning | QR code → WebSocket | Provisioning | 5 |

---

## Appendix A: Migration Guide from Current Design

### Changes Required in Phase Docs

| Phase | Current Design | Change Required |
|-------|---------------|-----------------|
| 0 (Setup) | No protos module | Add `:core:protos` module to settings.gradle.kts, version catalog, app deps |
| 1 (Foundation) | `WsRequest`/`WsResponse` as Kotlin data classes | Replace with generated protobuf classes from `WebSocketResources` |
| 1 (Foundation) | No codegen config | Add `protobuf { }` block to `:core:network` and `:core:protos` build files |
| 3 (Chat) | 13-field Envelope table | Replace with 22-field Envelope.proto |
| 3 (Chat) | `DecryptedContent` as vague concept | Replace with `Content` protobuf + `DataMessage` |
| 3 (Chat) | `BodyRange` as Kotlin data class | Replace with `BodyRange.proto` |
| 3 (Chat) | `MediaUploadResult(mediaId, key, iv)` | Replace with `AttachmentPointer.proto` |
| 4 (Calls) | Raw SDP strings in WS messages | Replace with `CallMessage.proto` (offer/answer/ice with opaque bytes) |
| 5 (Social) | StatusViewModel without protos | Add `StoryMessage.proto` + `TextAttachment` |
| 5 (Social) | GroupViewModel without protos | Add `GroupContext.proto` + `GroupChange` |
| 6 (Extended) | Backup exporter without format | Define backup section format using protobuf |
| 6 (Extended) | Multi-device sync stub | Add `SyncMessage.proto` + `StorageService.proto` |
| 6 (Extended) | No provisioning | Add `Provisioning.proto` |
| 7 (Polish) | No internal processing pipeline protos | Add `InternalSerialization.proto` |

### Media Encryption Fix

The crypto doc specifies XChaCha20-Poly1305 for all encryption. The actual media encryption (in `AttachmentPointer.key`) uses **AES-256-GCM**. Fix the crypto doc to clarify:

```
Message payload encryption: XChaCha20-Poly1305 (via libsodium)
Media file encryption:      AES-256-GCM (via javax.crypto/jdk crypto)
Session record encryption:  XChaCha20-Poly1305 (via libsodium)

Rationale for media using AES-256-GCM:
1. Media files can be very large (128MB) — AES-NI hardware acceleration on ARM
   makes AES-256-GCM significantly faster than XChaCha20 for bulk data.
2. Android's javax.crypto.Cipher supports AES-256-GCM natively with hardware
   acceleration (no JNI overhead per chunk).
3. Signal uses AES-256-GCM for media — we follow their proven approach.
```
