# SecureChat API Reference

End-to-end encrypted messaging platform. The server **never** sees message content, decryption keys, or private key material. All encryption happens client-side.

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Authentication & Account Flow](#2-authentication--account-flow)
3. [Key Management (X3DH + Double Ratchet)](#3-key-management)
4. [Message Flow](#4-message-flow)
5. [Media Flow](#5-media-flow)
6. [WebSocket Protocol (MRS)](#6-websocket-protocol)
7. [REST API Reference](#7-rest-api-reference)
8. [Data Models](#8-data-models)
9. [Error Codes & Rate Limiting](#9-error-codes--rate-limiting)

---

## 1. Architecture Overview

### Base URL
```
Live (Cloudflare Tunnel): https://florist-health-cpu-popular.trycloudflare.com
Local dev:                http://localhost:8080
```

**Current live endpoint:** `https://florist-health-cpu-popular.trycloudflare.com`
All API calls go through this single URL. The Cloudflare Tunnel proxies everything to the OpenResty gateway which routes to the right service.

> ⚠️ **Ephemeral URL** — This is a quick tunnel. The URL changes if the tunnel restarts. Check `/tmp/cloudflared.log` or run `grep -o "https://[a-z-]*\.trycloudflare\.com" /tmp/cloudflared.log` to get the current URL.
> 
> 🚫 **TURN not available** — WebRTC calls requiring TURN relay won't work through the tunnel (free tier doesn't support UDP). P2P calls may still work depending on network.
> 
> 📥 **Media downloads** — Served as direct response body (200 OK), NOT as 302 redirect to S3. The `Content-Type` header has the MIME type, and `X-SHA256-Ciphertext` header has the ciphertext hash for integrity verification.

### Services
| Service | Port | Purpose |
|---------|------|---------|
| Gateway | 8080 | OpenResty reverse proxy with rate limiting |
| Auth | 8001 | OTP, JWT, session management |
| IKS | 8002 | Public key directory (Identity, Signed Prekeys, One-Time Prekeys) |
| MRS | 8003/8004 | WebSocket relay + REST message fallback |
| Media | 8005 | Encrypted file upload/download |
| Profile | 8008 | User profiles, privacy settings, presence |
| Contacts | 8009 | Contact list (server-side) |
| Groups | 8010 | Group management |
| Blocking | 8007 | User blocks |
| Chats | 8011 | Message editing, search (metadata-only), translation, location, archive |
| Reactions | 8012 | Emoji reactions on messages |
| Polls | 8013 | Poll creation and voting |
| Disappear | 8014 | Disappearing message timers |
| Backup | 8015 | Encrypted backup upload |
| Status | 8016 | Stories/status updates |
| Stickers | 8017 | Sticker packs |
| Channels | 8018 | Broadcast channels |
| PNS | 8006 | Push notification tokens |
| Export | 8019 | GDPR data export |
| Notif Pref | 8020 | Notification preferences |
| Bot | 8021 | Bot registration |
| Analytics | 8022 | Aggregate metrics |
| Admin | 8099 | Admin panel, abuse reports |

### Auth — E2EE Principle
- The server issues Ed25519 JWTs for authentication
- The server NEVER stores or sees decryption keys, private keys, or message plaintext
- All keys are generated client-side. Only public keys are uploaded to IKS
- Message payloads are opaque binary blobs to the server. Encryption/decryption is client-only

### Common Response Format
```json
{"error": "<error message>"}
```

### Common Auth
JWT is sent as `Authorization: Bearer <jwt>` header on all authenticated requests.

---

## 2. Authentication & Account Flow

### 2.1 OTP Registration Flow
```
Device → Auth Server                              | Request OTP
Device ← Auth Server: challenge_id                | 
Device → Auth Server: challenge_id + OTP code      | Verify OTP
Device ← Auth Server: JWT (15min) + refresh_token  |
Device → IKS: Register key bundle                  | Upload IK, SPK, OPKs
Device → Profile: Create profile                   | Set username, display name
```

### 2.2 POST /v1/auth/request-otp
Request an OTP code for login/registration.
```json
// Request
{"identifier": "+15551234567", "method": "sms"}
// Response 200
{"challenge_id": "uuid", "expires_in": 600}
// Errors: 400 (missing identifier), 429 (rate limit)
```
Rate limits: 10/24h per identifier, 5/hour per IP.

### 2.3 POST /v1/auth/verify-otp
Verify the OTP and receive JWT tokens.
```json
// Request
{
  "challenge_id": "uuid",
  "otp": "123456",
  "device_info": {"device_id": "uuid (optional)", "user_agent": "app/v1.0"}
}
// Response 200
{
  "user_id": "uuid",
  "access_token": "<jwt>",
  "refresh_token": "<string>",
  "expires_in": 900
}
// Errors: 400, 429
```

### 2.4 POST /v1/auth/refresh
Refresh an expired JWT using a refresh token.
```json
// Request
{"refresh_token": "<string>"}
// Response 200
{"access_token": "<new_jwt>", "refresh_token": "<new_refresh_token>", "expires_in": 900}
// Errors: 400, 429 (60/hour/IP)
```

### 2.5 POST /v1/auth/logout
Revoke the current device session.
```json
// Request: empty (JWT in header)
// Response 200: {"status": "logged_out"}
```

### 2.6 GET /v1/auth/devices
List all devices registered to the authenticated user.
```json
// Response 200
{"devices": [{"device_id": "uuid", "user_agent": "string", "issued_ts": 1234567890, "last_used_ts": 1234567890}]}
```

### 2.7 DELETE /v1/auth/devices/{device_id}
Revoke a specific device session.
```json
// Response 200: {"status": "device_revoked"}
```

### 2.8 DELETE /v1/auth/account
Permanently delete the authenticated user's account. Requires JWT.
```json
// Response 200: {"status": "account_deleted"}
```

### 2.9 GET /v1/auth/.well-known/jwks.json
Get the Ed25519 public key for JWT verification (no auth required).
```json
// Response 200
{"keys": [{"kty": "OKP", "crv": "Ed25519", "use": "sig", "kid": "securechat-signing-key-1", "x": "<base64url>"}]}
```

### 2.10 JWT Structure
```json
// Header: {"alg": "EdDSA", "typ": "JWT"}
// Payload: {"sub": "<user_id>", "did": "<device_id>", "iat": 1234567890, "exp": 1234568790, "jti": "<unique>"}
// Signature: Ed25519(signing_key, header.payload)
```
- Access token TTL: 15 minutes
- Refresh token TTL: 90 days

---

## 3. Key Management (X3DH + Double Ratchet)

### 3.1 Client-Side Key Generation (MANDATORY)
Every device MUST generate the following keys before registering:

| Key | Algorithm | Size | Purpose | Persistence |
|-----|-----------|------|---------|-------------|
| **Identity Key (IK)** | Ed25519 | 32 bytes | Permanent device identity | Stored on device |
| **Identity Key (IK) Public** | Ed25519 | 32 bytes | Shared with IKS for bundle | Uploaded to IKS |
| **Signed Prekey (SPK)** | X25519 | 32 bytes | Medium-term DH key | Stored on device |
| **Signed Prekey (SPK) Public** | X25519 | 32 bytes | Shared with IKS | Uploaded to IKS |
| **SPK Signature** | Ed25519 | 64 bytes | `Sign(IK_private, SPK_public)` | Uploaded to IKS |
| **One-Time Prekeys (OPK)** | X25519 | 32 bytes each | Used once per new session | Stored on device |
| **One-Time Prekeys (OPK) Public** | X25519 | 32 bytes each | Shared with IKS (max 100) | Uploaded to IKS |

### 3.2 POST /v1/keys/register — Upload Key Bundle
```json
// Request
{
  "identity_key": "<base64url 32 bytes>",
  "signed_prekey": {
    "public_key": "<base64url 32 bytes>",
    "signature": "<base64url 64 bytes>"
  },
  "one_time_prekeys": [
    {"public_key": "<base64url 32 bytes>"},
    ...
  ]
}
// Response 201: {"device_id": "uuid", "status": "registered"}
// Errors: 400 (missing fields, invalid keys), 429 (5/hour/user)
```
- The server verifies the SPK signature server-side using the IK public key
- At least 20 OPKs must be uploaded initially
- Max 5 registrations per hour per user

### 3.3 GET /v1/keys/bundle/{target_user_id} — Fetch Key Bundle
Fetch all devices' key bundles for a target user (to establish a session).
```json
// Response 200
{
  "devices": [{
    "device_id": "uuid",
    "identity_key": "<base64url 32 bytes>",
    "signed_prekey": {"public_key": "<base64url 32 bytes>", "signature": "<base64url 64 bytes>"},
    "one_time_prekey": {"public_key": "<base64url 32 bytes>"}
  }]
}
// Errors: 404 (user not found), 429 (100/device/hour)
```
- Each bundle fetch consumes one OPK per device atomically
- Bundle fetch is the ONLY way to get another device's keys
- Rate limit: 100/device/hour

### 3.4 PUT /v1/keys/signed-prekey — Rotate Signed Prekey
```json
// Request
{"public_key": "<base64url 32 bytes>", "signature": "<base64url 64 bytes>"}
// Response 200: {"status": "rotated"}
```
- Rotate SPK every 7-30 days
- Old SPK is deactivated (marked inactive in DB)

### 3.5 POST /v1/keys/one-time-prekeys — Top Up OPKs
```json
// Request
{"one_time_prekeys": [{"public_key": "<base64url 32 bytes>"}, ...]}
// Response 200: {"status": "uploaded", "count": 30}
```
- Max 100 OPKs per upload
- Max 10 uploads per device per day

### 3.6 GET /v1/keys/opk-count — Check OPK Count
```json
// Response 200: {"count": 42}
```
- Client should top up OPKs when count drops below 10

### 3.7 Key Transparency (M5b)
Public endpoints for verifying key history integrity — no auth required.
```
GET /v1/keys/sth/latest           — Latest signed tree head
GET /v1/keys/sth/{tree_size}      — Specific signed tree head
GET /v1/keys/proof/{user_id}/{device_id} — Inclusion proof
```

### 3.8 Session Establishment (X3DH — Client-Side)
When Alice wants to message Bob for the first time:
```
1. Alice → GET /v1/keys/bundle/{bob_user_id}
             ← Get Bob's IK public, SPK public, one OPK public
2. Alice performs X3DH key agreement (client-side):
   - Generate ephemeral key (EK)
   - DH1 = DH(IK_A_private, SPK_B_public)
   - DH2 = DH(EK_private, IK_B_public)
   - DH3 = DH(EK_private, SPK_B_public)
   - DH4 = DH(EK_private, OPK_B_public)
   - SK = KDF(DH1 || DH2 || DH3 || DH4)
3. Alice sends PREKEY_MESSAGE to MRS:
   - Contains: IK_A_public, EK_public, SPK_B_id, OPK_B_id
4. Bob receives PREKEY_MESSAGE via MRS:
   - Looks up SPK, OPK by ID
   - Performs same DH computations with his private keys
   - Derives same SK
   - Deletes OPK (one-time use)
5. Session established. Both sides have SK.
6. Subsequent messages use Double Ratchet (ratchet step per message).
```

---

## 4. Message Flow

### 4.1 End-to-End Encryption Contract
- **Client encrypts** message payload BEFORE sending to server
- **Server relays** opaque encrypted blob to recipient
- **Recipient decrypts** message payload AFTER receiving from server
- **Server NEVER** sees message plaintext, media content, or location coordinates
- Message types determine the content format:
  - `SIGNAL_MESSAGE` — Double Ratchet encrypted message
  - `PREKEY_MESSAGE` — X3DH session setup message (contains EK, key IDs)
  - `DELIVERY_RECEIPT`, `READ_RECEIPT` — Receipt metadata
  - `TYPING_START`, `TYPING_STOP` — Typing indicators
  - `CALL_OFFER`, `CALL_ANSWER`, `CALL_ICE`, `CALL_END` — Call signaling
  - `KEY_EXCHANGE` — Key exchange
  - `MLS_COMMIT`, `MLS_WELCOME` — MLS group operations

### 4.2 Sending a Message
```
1. Client encrypts message content using Double Ratchet session
2. Client sends to MRS WebSocket (or REST fallback)
3. MRS stores encrypted payload in offline queue
4. MRS delivers to recipient's connected device(s)
5. Recipient decrypts using Double Ratchet session
```

### 4.3 POST /v1/messages/send (REST Fallback)
```json
// Request (JWT required)
{
  "sender_device_id": "uuid",
  "recipient_user_id": "uuid",
  "recipient_device_id": "uuid (optional, for specific device)",
  "message_type": "SIGNAL_MESSAGE",
  "payload": "<base64url encrypted blob>",
  "sender_ts": "<iso8601>",
  "ttl_seconds": 604800
}
// Response 200
{"envelope_id": "uuid", "status": "queued"}
// Errors: 400, 401, 429 (100/device/min)
```

### 4.4 POST /v1/messages/sealed-send — Sealed Sender (Anonymous, No JWT)
Send a message **without revealing your identity to the server**. The sender's identity is encrypted inside the payload using Double Ratchet — the server only sees an opaque blob and the recipient.

```json
// Request (NO JWT required — IP rate limited)
{
  "recipient_user_id": "uuid",
  "recipient_device_id": "uuid (optional, for specific device)",
  "message_type": "SIGNAL_MESSAGE",
  "payload": "<base64url encrypted blob (sender identity encrypted inside)>",
  "reply_token": "uuid (optional — enables delivery receipt routing)"
}
// Response 200
{"envelope_ids": ["uuid"], "sealed": true}
// Errors: 400, 413, 429 (50/IP/min)
```

**How it works:**
1. Sender encrypts their identity key into the payload using the recipient's session
2. Server stores with `sender_user_id = NULL` — identity never written to disk
3. Recipient receives with `"sender_user_id": null` and `"sealed": true`
4. Recipient decrypts payload to discover who sent it
5. `reply_token` allows delivery receipts without server knowing sender identity

**Rate limiting:** 50/分钟 per IP (anonymous, no device_id available)

### 4.5 GET /v1/messages/pending — Poll for Messages
```json
// Query params: limit=50, after_envelope_id=uuid
// Response 200
{"messages": [
  {"envelope_id": "uuid", "sender_user_id": "uuid", "message_type": "SIGNAL_MESSAGE",
   "payload": "<base64url encrypted>", "server_ts": "<iso8601>"}
]}
```
- For devices that are offline temporarily; real-time delivery is via WebSocket
- Messages are deleted after delivery

### 4.6 Delivery Guarantees
- Messages are stored in the offline queue for up to 7 days (configurable TTL)
- Messages are deleted immediately after delivery to all target devices
- WebSocket delivery is attempted first; REST polling is fallback
- Delivery receipts (`DELIVERY_RECEIPT`, `READ_RECEIPT`) are sent as separate message types through MRS

### 4.7 Editing a Message (E2EE)
Edits work by sending a new encrypted message envelope that references the original:
```json
// PUT /v1/messages/{original_envelope_id}  (JWT required)
// Request
{"new_envelope_id": "<uuid of the new encrypted edit envelope>"}

// Response 200
{"success": true, "original_envelope_id": "uuid", "new_envelope_id": "uuid", "edit_count": 1}
```

- **E2EE**: The server does NOT receive the edit content. The edit text is encrypted inside the `new_envelope_id` message envelope
- Max 2 edits per message
- Only the original sender can edit
- The client MUST look up edits by `new_envelope_id` references when displaying messages

### 4.8 Location Sharing (E2EE)
Location coordinates are encrypted inside the message envelope:
```json
// POST /v1/location  (JWT required)
// Request
{"envelope_id": "<uuid of the message containing encrypted location>"}

// Response 200
{"success": true, "envelope_id": "uuid"}
```

- **E2EE**: The server does NOT store latitude, longitude, or place name
- Location data is encrypted within the message envelope and sent via MRS
- The client encrypts location coordinates before sending the message
- The receiving client decrypts location from the message payload

### 4.9 Search (E2EE)
Search is **metadata-only** — the server cannot search message content:
```json
// GET /v1/search/messages?q=keyword&chat_id=uuid&from=ts&to=ts&limit=20  (JWT required)
// Response 200
{"results": [{"envelope_id": "uuid", "sender": "uuid", "ts": "iso8601", "preview": ""}], "count": N}
```

- **E2EE**: The `q` query parameter is accepted for API compatibility but the server does NOT use it for ILIKE search on payloads
- Results include NO preview (server cannot read message content)
- Full-text search MUST be performed client-side on decrypted messages
- Filters available: `chat_id` (sender/recipient), `from`/`to` (timestamp range), `limit` (max 100)

### 4.10 Conversation Model
- No "create conversation" API — conversations are implicit
- A conversation exists when two users exchange messages
- MRS delivers by `recipient_user_id` → fan-out to ALL of that user's devices
- Group conversations are explicit (via Groups service)

---

## 5. Media Flow

### 5.1 Upload & Share (E2EE)
```
1. Client encrypts file using a random media key (AES-256-GCM)
2. Client calls POST /v1/media/upload with encrypted binary blob
3. Server returns media_id (opaque identifier)
4. Client sends a SIGNAL_MESSAGE containing the media key + media_id + metadata
5. Recipient downloads encrypted blob, decrypts with media key
```

### 5.2 POST /v1/media/upload
Upload an encrypted file. Body is raw binary (not JSON).
```
POST /v1/media/upload
Content-Type: application/octet-stream
Authorization: Bearer <jwt>
(encrypted binary body)

Response 201: {"media_id": "uuid", "size": 12345}
```
- Max body size: 128MB (image/audio: 16MB, video: 128MB, document: 100MB)
- Rate limit: 100 uploads/user/day
- The server computes SHA-256 of the ciphertext for integrity verification

### 5.3 GET /v1/media/{media_id}
Download an encrypted file. Returns the raw binary blob or 302 redirect to S3.
```json
// Response: binary body (encrypted), Content-Type: application/octet-stream
// Errors: 401, 404
```
- The client MUST decrypt the downloaded blob using the media key received via the message layer

### 5.4 DELETE /v1/media/{media_id}
Delete an uploaded file. Only the uploader can delete.
```json
// Response 200: {"status": "deleted"}
// Errors: 401, 403, 404
```

---

## 6. WebSocket Protocol

The client communicates with the MRS service (port 8003) via **binary protobuf frames** over WebSocket. All frames use a custom protobuf wire format — NOT JSON.

### 6.1 Connection
```
ws://<host>:8003/
```
- No query parameters — authentication is the first frame sent after connect
- Server header: `SecureChat-MRS/1.0`
- All frames are binary (protobuf encoded), never JSON

### 6.2 Frame Format (Protobuf Binary)

All WebSocket frames are binary protobuf messages. The outer message has two types:

| Type | Value | Direction |
|------|-------|-----------|
| `REQUEST` | 1 | Client → Server |
| `RESPONSE` | 2 | Server → Client |

**Outer message fields:**
| Field | Type | Description |
|-------|------|-------------|
| 1 | varint | Frame type: 1 = REQUEST, 2 = RESPONSE |
| 2 | embedded (REQUEST) | Present when type = 1 |
| 3 | embedded (RESPONSE) | Present when type = 2 |

**REQUEST sub-message fields (type = 1):**
| Field | Wire Type | Name | Description |
|-------|-----------|------|-------------|
| 1 | length-delimited (string) | verb | HTTP verb: `POST`, `GET`, `PUT` |
| 2 | length-delimited (string) | path | Request path: `/v1/auth`, `/api/v1/message`, `/v1/keepalive` |
| 3 | length-delimited (bytes) | body | Request body (opaque bytes — depends on path) |
| 4 | varint (uint64) | id | Client-assigned request ID for correlating responses |
| 5 | repeated string | headers | Optional extra headers (`"Key: Value"` format) |

**RESPONSE sub-message fields (type = 2):**
| Field | Wire Type | Name | Description |
|-------|-----------|------|-------------|
| 1 | varint (uint64) | id | Matches the request ID |
| 2 | varint (uint32) | status | HTTP status code: 200, 400, 401, 404, 500, etc. |
| 3 | length-delimited (string) | message | Human-readable status message |
| 4 | length-delimited (bytes) | body | Optional response body |
| 5 | repeated string | headers | Optional response headers |

### 6.3 Protocol Flow

```
1. CONNECT: Client opens WebSocket to ws://<host>:8003/

2. AUTH (Client → Server):
   Frame: REQUEST (type=1)
     verb = "POST"
     path = "/v1/auth"
     body = <raw JWT bytes>   // NOT JSON — just the raw JWT token string
     id = <client-assigned request id>

3. AUTH (Server → Client):
   Frame: RESPONSE (type=2)
     status = 200          // on success
     message = "Authenticated"
     OR
     status = 401          // on failure
     message = "reason"
     // server closes connection on failure

4. On auth success, server pushes pending offline messages:
   Frame: REQUEST (type=1)
     verb = "PUT"
     path = "/api/v1/message"
     body = <Envelope protobuf bytes>   // incoming message

   Client MUST respond with:
   Frame: RESPONSE (type=2)
     status = 200

5. Sending a message (Client → Server):
   Frame: REQUEST (type=1)
     verb = "POST"
     path = "/api/v1/message"
     body = <Envelope protobuf bytes>   // outgoing encrypted message
     id = <client-assigned request id>

   Server responds:
   Frame: RESPONSE (type=2)
     status = 200
     message = "OK"
     body = <bytes containing the envelope_id>

6. Keepalive (every 30 seconds):
   Frame: REQUEST (type=1)
     verb = "GET"
     path = "/v1/keepalive"
     body = (empty)

   Server responds:
   Frame: RESPONSE (type=2)
     status = 200
     message = "OK"
```

### 6.4 Frame Limits & Close Codes

**Frame limits:**
- Max message size: 2 MB (server-side limit: `ws_.read_message_max(2 * 1024 * 1024)`)
- Regular payload in Envelope: 64KB max
- MLS/KEY_EXCHANGE payload in Envelope: 2MB max

**Close codes:**
| Code | Reason | Trigger |
|------|--------|---------|
| 4001 | Auth failure | JWT verification failed |
| 4002 | Auth timeout | No auth frame received within 10s of connect |
| 4003 | Idle timeout | No frames received for 90s |
| 4409 | Displaced | Another device connected with same device_id |
| 4004 | Protocol error | Malformed frame or unexpected path |

**Timers:**
| Timer | Duration | Action |
|-------|----------|--------|
| Auth timeout | 10s | Server disconnects with code 4002 if no AUTH frame received |
| Idle timeout | 90s | Server disconnects with code 4003 on inactivity |
| Ping interval | 30s | Server sends WebSocket native ping; missed ping = disconnect |
| Pending request timeout | 30s | Server discards pending request if no response

### 6.5 WebSocket Reconnection Flow

When the WebSocket disconnects unexpectedly (network loss, server restart, timeout), the client **must** follow this reconnection protocol:

```
1. Detect disconnect (onDone callback, ping timeout, or socket error)
2. Wait 1 second (base delay)
3. Attempt reconnect with current JWT
4. If 401 received → refresh JWT using refresh token → retry
5. If reconnect fails → double delay: 1s → 2s → 4s → 8s → 15s → 30s (cap)
6. Add random jitter (±25%) to prevent thundering herd
7. Continue retrying at 30s intervals indefinitely
8. If 5 consecutive 401s → stop retrying, force user re-authentication
```

**Key behaviors:**
- Max backoff: 30 seconds (±25% jitter = 22.5s–37.5s)
- JWT refresh: attempt on any 401 response, if refresh also fails → force logout
- On reconnect success: drain any queued messages, resume normal operation
- Ping/keep-alive: send empty frame every 30 seconds. If no response within 15s, treat as disconnect
- Fallback: if WS unavailable for > 60s, switch to REST polling (`GET /v1/messages/pending`) every 5s
- When WS recovers, stop REST polling and resume WS

### 6.6 Error Response Formats

All error responses follow one of these patterns depending on the service:

**Standard error (most endpoints):**
```json
{"error": "<error message string>"}
```

**Validation errors (400 Bad Request):**
```json
{"error": "field_name: validation message"}
```

**Rate limit errors (429):**
```json
{"error": "rate_limit_exceeded", "retry_after": 30}
```
Also includes headers: `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`

**Auth errors (401):**
```json
{"error": "unauthorized", "code": "TOKEN_EXPIRED"}
```
Client must refresh JWT and retry on `TOKEN_EXPIRED`. Any other code means force re-login.

**Not found errors (404):**
```json
{"error": "not_found", "resource": "<resource_type>", "id": "<requested_id>"}
```

**Conflict errors (409):**
```json
{"error": "conflict", "detail": "resource already exists", "existing_id": "uuid"}
```

**Server errors (500):**
```json
{"error": "internal_server_error", "request_id": "uuid"}
```
Client should retry with backoff. The `request_id` is for server-side debugging.

### 6.7 Multi-Device Sync Protocol

When the app supports multiple devices per user, the following protocol syncs state between them:

**Architecture:**
```
StorageService (server) ←→ Device A (primary)
                        ←→ Device B (linked)
                        ←→ Device C (linked)
```

**Manifest-based sync (inspired by Signal's Storage Service):**
1. Each device maintains a local manifest (version number + digest of all items)
2. The server stores the latest manifest per user
3. On sync trigger (interval, push notification, or manual):
   - Fetch remote manifest version
   - If remote > local: download remote items, apply changes
   - If local > remote: upload local items to server
   - If equal: nothing to do

**Syncable record types:**
| Record Type | Fields | Conflict Resolution |
|---|---|---|
| Contact | userId, displayName, username, avatarId, color, isBlocked | Last-write-wins per field |
| Group | groupId, name, avatarId, members, revision | Server revision wins (group state is authoritative) |
| Account | profileName, about, avatarId, privacySettings | Last-write-wins |
| Call Link | roomId, name, restrictions, revision | Server revision wins |
| Notification Profile | profileId, name, schedule, allowedContacts | Last-write-wins |
| Chat Folder | folderId, name, icon, threadIds, ordering | Last-write-wins |
| Story Distribution List | listId, name, memberIds, privacyMode | Last-write-wins |

**Linking a new device:**
1. Primary device generates a linking QR code (contains temporary provisioning key)
2. Secondary device scans QR → establishes secure channel with primary
3. Primary shares identity keys + registration data via the secure channel
4. Secondary registers with server using the shared credentials
5. Secondary downloads the full manifest and syncs all records
6. Both devices now share the same identity and message history

**Conflict resolution strategy:**
- **For server-authoritative records** (groups, call links): server version wins
- **For user-authoritative records** (contacts, settings): last-write-wins per field
- **For account records**: last-write-wins, but some fields merge (e.g., privacy settings merge per-key)

---

## 7. REST API Reference

### 7.1 Health
All services expose `GET /health`:
```json
{"status": "ok", "service": "<service_name>"}
```

### 7.2 Profile Service (:8008)

#### GET /v1/profile/{user_id}
Get a user's public profile. Privacy-filtered.
```json
// Response 200
{"user_id": "uuid", "username": "string", "display_name": "string",
 "about": "string (may be hidden by privacy)", "avatar_media_id": "uuid (optional)",
 "online": true, "last_seen": "iso8601 (may be hidden by privacy)"}
```
Rate limit: 100/device/min. Auth required.

#### PUT /v1/profile
Update own profile.
```json
// Request
{"username": "alice_123", "display_name": "Alice", "about": "Hello!"}
// Response 200: {"updated": true}
```
- Username: 3-32 chars, lowercase alphanumeric + underscore, unique
- Display name: 1-100 chars
- About: 0-200 chars
- Rate limit: 10/device/hour

#### POST /v1/profile/avatar
Upload avatar. Raw binary body.
```
Content-Type: application/octet-stream
(avatar binary, max 5MB)
// Response 200: {"avatar_media_id": "uuid"}
```
Rate limit: 5/device/day.

#### PUT /v1/profile/privacy
Update privacy settings.
```json
// Request
{"last_seen_visibility": "EVERYONE", "online_visibility": "CONTACTS",
 "avatar_visibility": "EVERYONE", "about_visibility": "EVERYONE"}
// Response 200: {"updated": true}
```
Values: `EVERYONE` | `CONTACTS` | `NOBODY`

#### GET /v1/profile/search?username=prefix
Search users by username prefix.
```json
// Response 200: {"results": [{"user_id": "uuid", "username": "str", "display_name": "str"}]}
```
Rate limit: 20/device/min.

### 7.3 Contacts Service (:8009)

#### POST /v1/contacts
Add a contact.
```json
// Request
{"contact_user_id": "uuid", "custom_name": "optional"}
// Response 200: {"contact_id": "uuid", "status": "added"}
```

#### GET /v1/contacts
List contacts.
```json
// Response 200: {"contacts": [{"user_id": "uuid", "custom_name": "str", "username": "str"}]}
```

#### DELETE /v1/contacts/{contact_user_id}
Remove a contact.

#### GET /v1/contacts/check/{user_id}
Check if user is a contact.
```json
// Response 200: {"is_contact": true}
```

### 7.4 Groups Service (:8010)

#### POST /v1/groups
Create a group.
```json
// Request
{"name": "Group Name", "description": "optional", "add_members_policy": "ALL_MEMBERS",
 "join_type": "INVITE_ONLY", "initial_member_ids": ["uuid", ...]}
// Response 201: {"group_id": "uuid", "name": "...", "member_count": N}
```
- Max 500 members
- Rate limit: 5/device/day

#### POST /v1/groups/{group_id}/members
Add members to group.
```json
// Request
{"user_ids": ["uuid", ...]}
// Response 200: {"added": 3}
```

#### DELETE /v1/groups/{group_id}/members/{user_id}
Remove member from group.

#### PUT /v1/groups/{group_id}
Update group metadata (name, description, add_members_policy, edit_info_policy, join_type).

#### POST /v1/groups/{group_id}/invite-link
Generate an invite link.
```json
// Request
{"expires_ts": "iso8601 (optional, default 7 days)", "max_uses": 10}
// Response 200: {"link_code": "base64url", "expires_ts": "iso8601"}
```
Max 10 active invite links per group.

#### POST /v1/groups/join/{link_code}
Join a group via invite link.

#### GET /v1/groups/join/{link_code}
Preview group from invite link (no auth required).
```json
{"name": "Group Name", "description": "...", "member_count": 42}
```

#### GET /v1/groups/{group_id}/members
List members.
```json
// Response 200: {"members": [{"user_id": "uuid", "role": "OWNER|ADMIN|MEMBER", ...}]}
```

#### GET /v1/groups
List user's groups.
```json
// Response 200: {"groups": [{"group_id": "uuid", "name": "str", "role": "str", ...}]}
```

#### PUT /v1/groups/{group_id}/members/{user_id}/role
Update member role: `OWNER` | `ADMIN` | `MEMBER`.

#### PUT /v1/groups/{group_id}/owner
Transfer ownership to another member.
```json
// Request: {"new_owner_user_id": "uuid"}
// Response 200: {"updated": true}
```

#### PUT /v1/groups/{group_id}/settings
Update group settings.
```json
// Request: {"messaging_mode": "CONVERSATIONS", "disappear_timer_seconds": 86400}
// Response 200: {"updated": true}
```

#### DELETE /v1/groups/{group_id}
Delete group (owner only). Soft delete — marks group as deleted.

#### DELETE /v1/groups/{group_id}/invite-link/{link_id}
Revoke an invite link.

#### GET /v1/groups/{group_id}/join-requests
List pending join requests (admin).
```json
{"requests": [{"request_id": "uuid", "requester_user_id": "uuid", "status": "PENDING", "requested_ts": "..."}]}
```

#### PUT /v1/groups/{group_id}/join-requests/{request_id}
Approve or reject a join request.
```json
// Request: {"approve": true}
// Response 200: {"approved": true}
```

### 7.5 Blocking Service (:8007)

#### POST /v1/blocks/{target_user_id}
Block a user.

#### DELETE /v1/blocks/{target_user_id}
Unblock a user.

#### GET /v1/blocks
List blocked users.

### 7.6 Reactions Service (:8012)

#### PUT /v1/reactions/{message_id}
Add or change a reaction.
```json
// Request: {"emoji": "😊"}
// Response 200: {"reacted": true}
```

#### DELETE /v1/reactions/{message_id}
Remove reaction.

#### GET /v1/reactions/{message_id}
Get reaction aggregate.
```json
{"message_id": "uuid", "reactions": {"😊": {"count": 3}, "😂": {"count": 1}}, "reacted_by_me": ["😊"]}
```

#### GET /v1/reactions/{message_id}/{emoji}
List reactors for a specific emoji.
```json
{"emoji": "😊", "reactors": [{"user_id": "uuid", "display_name": "str"}]}
```

### 7.7 Polls Service (:8013)

#### POST /v1/polls
Create a poll.
```json
// Request
{"conversation_id": "uuid", "question": "Favorite color?", "closes_in_seconds": 3600,
 "options": [{"text": "Red"}, {"text": "Blue"}, {"text": "Green"}],
 "allow_multiple": false, "anonymous": false}
// Response 201: {"poll_id": "uuid", "question": "...", "options": [...], "status": "OPEN"}
```
- 2-12 options
- `closes_in_seconds`: 60-604800 (optional)
- Rate limit: 5/device/day

#### POST /v1/polls/{poll_id}/vote
Vote on a poll.
```json
// Request: {"option_ids": ["uuid"]}
// Response 200: {"your_vote": ["uuid"], "results": {...}, "total_votes": N}
```

#### GET /v1/polls/{poll_id}
Get poll with results. Your vote included if non-anonymous.
```json
{"poll_id": "uuid", "question": "...", "options": [...], "results": {...}, "your_vote": [...]}
```

#### GET /v1/polls/{poll_id}/voters/{option_id}
List voters for an option. Returns 403 for anonymous polls.

#### PUT /v1/polls/{poll_id}/close
Close poll (creator only).

#### DELETE /v1/polls/{poll_id}
Delete poll (creator only). Cascades to delete votes and aggregates.

### 7.8 Disappearing Messages (:8014)

#### PUT /v1/disappear/{conversation_id}
Set disappearing message timer.
```json
// Request: {"timer_seconds": 86400, "timer_mode": "FROM_SEND"}
// Response 200: {"timer_seconds": 86400}
```
- timer_seconds: 0 (off), 86400 (1day), 604800 (1week), 7776000 (90days)
- timer_mode: `FROM_SEND` | `FROM_VIEW`
- Rate limit: 10/device/day

#### GET /v1/disappear/{conversation_id}
Get timer setting.

#### POST /v1/disappear/viewed
Record message as viewed (for FROM_VIEW mode).
```json
// Request: {"envelope_ids": ["uuid"]}
// Response 200: {"recorded": true}
```

### 7.9 Backup Service (:8015)

#### POST /v1/backup/initiate
Start a backup session.
```json
// Request: {"version": 1, "total_chunks": 10, "total_size": 1000000}
// Response 201: {"backup_id": "uuid", "status": "INITIATED"}
```
Max 3 versions per user.

#### PUT /v1/backup/chunk/{backup_id}
Upload a backup chunk. Raw binary body. Headers: `X-Chunk-Index`, `X-Byte-Offset`.

#### POST /v1/backup/finalize/{backup_id}
Finalize backup. Verifies SHA-256.
```json
// Request: {"sha256": "<hex>"}
// Response 200: {"status": "COMPLETED", "version": 2}
```

#### GET /v1/backup/latest
Get latest backup metadata.
```json
{"backup_id": "uuid", "version": 2, "total_size": 1000000, "completed_ts": "iso8601"}
```

#### GET /v1/backup/download/{backup_id}
Download backup (raw binary). Rate limit: 3/user/day.

#### DELETE /v1/backup
Delete all user backups.

### 7.10 Status/Stories Service (:8016)

#### POST /v1/status
Create a status update.
```json
// Request
{"status_type": "TEXT", "text_content": "Hello!", "background_color": "#FF5733",
 "media_id": "uuid (for IMAGE/VIDEO/AUDIO/GIF)", "privacy": "ALL_CONTACTS",
 "selected_contacts": ["uuid", ...]}
// Response 201: {"status_id": "uuid", "expires_at": "iso8601"}
```
Types: `TEXT`, `IMAGE`, `VIDEO`, `AUDIO`, `GIF`. Max 30 active. Rate limit: 1/device/min.

#### GET /v1/status/feed
Get contacts' status feed. Grouped by author, unseen first.

#### GET /v1/status/{status_id}
View a specific status (records the view event).

#### GET /v1/status/{status_id}/views
Get view receipts (poster only). Rate limit: 60/device/hour.

#### DELETE /v1/status/{status_id}
Delete a status.

### 7.11 Stickers Service (:8017)

#### GET /v1/stickers/packs/featured
List featured sticker packs (no auth).
```json
{"packs": [{"pack_id": "uuid", "name": "str", "sticker_count": 3}]}
```

#### GET /v1/stickers/packs/search?q=keyword
Search sticker packs (no auth).

#### GET /v1/stickers/packs/{pack_id}
Get pack detail with stickers.

#### POST /v1/stickers/library/{pack_id}
Install a sticker pack.
```json
// Response 200: {"installed": true, "pack_id": "uuid"}
```

#### DELETE /v1/stickers/library/{pack_id}
Uninstall a sticker pack.

#### GET /v1/stickers/library
Get user's installed packs.

#### GET /v1/stickers/recent
Get recently used stickers.

#### POST /v1/stickers/recent/{sticker_id}
Record sticker usage.

#### POST /v1/stickers/packs
Create a custom sticker pack.
```json
// Request
{"name": "My Pack", "stickers": [
  {"media_id": "uuid", "emoji_tags": ["😊"], "text_tags": ["hello"]},
  ...
]}
// Response 201: {"pack_id": "uuid"}
```
- 3-30 stickers per pack
- Max 3 emoji tags per sticker
- Max 5 text tags per sticker

### 7.12 Channels Service (:8018)

#### POST /v1/channels
Create a channel.
```json
// Request: {"name": "News", "handle": "news_channel", "channel_type": "PUBLIC", "description": "..."}
// Response 201: {"channel_id": "uuid", "handle": "news_channel"}
```
Max 10 owned channels. Rate limit: 5/device/day.

#### POST /v1/channels/{channel_id}/posts
Publish a post.
```json
// Request: {"post_type": "TEXT", "text_content": "Hello!", "publish_ts": "iso8601"}
// Response 201: {"post_id": "uuid"}
```
Rate limit: 20/device/hour.

#### GET /v1/channels/{channel_id}/posts?before=uuid&limit=20
Get channel feed. Auth optional for public channels. Cursor-based pagination.

#### POST /v1/channels/{channel_id}/subscribe
Subscribe to a channel. Max 500 subscriptions per user.

#### DELETE /v1/channels/{channel_id}/subscribe
Unsubscribe.

#### POST /v1/channels/{channel_id}/invite
Generate invite link for private channels.

#### GET /v1/channels/search?q=keyword&page=0&limit=20
Search public channels (no auth). Rate limit: 60/IP/min.

#### PUT /v1/channels/{channel_id}/admins/{user_id}
Add channel admin (owner only).

#### DELETE /v1/channels/{channel_id}/admins/{user_id}
Remove channel admin (owner only).

#### POST /v1/channels/{channel_id}/posts/{post_id}
Edit a post.

#### PUT /v1/channels/{channel_id}/posts/{post_id}/pin
Pin a post (unpins previous pinned post).

#### DELETE /v1/channels/{channel_id}/posts/{post_id}
Delete a post (soft delete).

### 7.13 Push Notifications (:8006)

#### POST /v1/push/register
Register a push notification token.
```json
// Request: {"token": "string", "platform": "IOS" | "ANDROID"}
// Response 200: {"registered": true}
```
Rate limit: 10/device/hour.

#### DELETE /v1/push/register
Deregister push token.

### 7.14 Notification Preferences (:8020)

#### GET /v1/notifications/preferences
Get global notification preferences (JWT required).
```json
{"master_notifications_on": true, "message_notifications_on": true, "show_preview": true,
 "dnd_enabled": false, "dnd_start_time": "", "dnd_end_time": "", "dnd_timezone": ""}
```

#### PUT /v1/notifications/preferences
Update global preferences. Partial update.
```json
// Request: {"show_preview": false, "dnd_enabled": true, "dnd_start_time": "22:00", "dnd_end_time": "07:00", "dnd_timezone": "Asia/Tokyo"}
```

#### GET /v1/notifications/preferences/conversations
Get per-conversation notification overrides.

#### PUT /v1/notifications/preferences/conversations/{conversation_id}
Set per-conversation override.
```json
// Request: {"muted": true, "mute_duration_seconds": 28800, "mentions_only": false, "custom_sound": "chime"}
```
Valid mute durations: 3600 (1h), 28800 (8h), 604800 (1w), null (forever).
Valid custom_sounds: `chime`, `bell`, `ding`, `pop`, `marimba`, `xylophone`, `` (default).

### 7.15 Bot API (:8021)

#### POST /v1/bots/register
Register a bot.
```json
// Request: {"display_name": "WeatherBot", "description": "Weather updates", "commands": ["/weather", "/forecast"]}
// Response 201: {"bot_id": "uuid", "bot_token": "hex_secret", "username": "bot_abc123"}
```
Rate limit: 10/device/day.

#### GET /v1/bots/{bot_id}
Get bot details (owner only).

### 7.16 Data Export (GDPR) (:8019)

#### POST /v1/export/request
Request a data export.
```json
// Request: {"include_media": false}
// Response 201: {"export_id": "uuid", "status": "PROCESSING", "estimated_ready_ts": "up to 24 hours"}
```
Rate limit: 1/device/7 days.

#### GET /v1/export/{export_id}
Check export status.
```json
{"status": "READY", "download_url": "/v1/export/uuid/download?token=...", "expires_ts": "iso8601"}
```

### 7.17 Analytics (:8022)

#### POST /v1/telemetry
Send client-side telemetry (no auth, IP rate limited).
```json
// Request: {"startup_ms": 1200, "os": "ios"}
// Response 200: {"received": true}
```

#### POST /v1/telemetry/crash
Send crash report (no auth, IP rate limited).
```json
// Request: {"stack": "SIGSEGV", "version": "1.0"}
// Response 200: {"received": true}
```

### 7.18 Admin Panel (:8099)

#### POST /v1/report
Submit an abuse report (JWT required).
```json
// Request: {"target_user_id": "uuid", "reason": "spam"}
// Response 201: {"report_id": "uuid", "status": "PENDING"}
```

Admin endpoints (require admin JWT, role-based):
| Method | Path | Min Role |
|--------|------|----------|
| `GET` | `/admin/reports` | MODERATOR |
| `GET` | `/admin/reports/{id}` | MODERATOR |
| `PUT` | `/admin/reports/{id}/assign` | MODERATOR |
| `PUT` | `/admin/reports/{id}/resolve` | MODERATOR |
| `POST` | `/admin/users/{id}/suspend` | MODERATOR |
| `DELETE` | `/admin/users/{id}/suspend` | MODERATOR |
| `DELETE` | `/admin/users/{id}` | SUPER_ADMIN |
| `GET` | `/admin/moderation/sticker-packs` | MODERATOR |
| `PUT` | `/admin/moderation/sticker-packs/{id}/approve` | MODERATOR |
| `PUT` | `/admin/moderation/sticker-packs/{id}/reject` | MODERATOR |
| `GET` | `/admin/metrics/summary` | READ_ONLY |
| `GET` | `/admin/metrics/system-health` | READ_ONLY |
| `GET` | `/admin/audit-log` | SUPER_ADMIN |

Admin roles hierarchy: `READ_ONLY` < `SUPPORT` < `MODERATOR` < `SUPER_ADMIN`

---

## 8. Data Models

### User
```json
{"user_id": "uuid", "username": "alice_123", "display_name": "Alice", "about": "Hi!"}
```

### Device
```json
{"device_id": "uuid", "user_id": "uuid", "ik_public": "<base64url>", "registration_ts": "iso8601"}
```

### Key Bundle (for session establishment)
```json
{"device_id": "uuid", "identity_key": "<base64url>",
 "signed_prekey": {"public_key": "<base64url>", "signature": "<base64url>"},
 "one_time_prekey": {"public_key": "<base64url>"}}
```

### Group
```json
{"group_id": "uuid", "name": "Friends", "member_count": 5, "role": "OWNER", "joined_ts": "iso8601"}
```

### Channel
```json
{"channel_id": "uuid", "name": "News", "handle": "news_channel", "channel_type": "PUBLIC", "subscriber_count": 100}
```

### Poll
```json
{"poll_id": "uuid", "question": "?", "status": "OPEN",
 "options": [{"option_id": "uuid", "text": "Yes", "position": 0}],
 "results": {"<option_id>": {"count": 5, "percentage": 50.0}},
 "your_vote": ["<option_id>"], "total_votes": 10}
```

### Status
```json
{"status_id": "uuid", "author_user_id": "uuid", "status_type": "TEXT",
 "text_content": "Hello!", "created_ts": "iso8601", "expires_at": "iso8601"}
```

### Sticker Pack
```json
{"pack_id": "uuid", "name": "Fun Pack", "sticker_count": 5, "tags": ["😊", "cute"]}
```

---

## 9. Error Codes & Rate Limiting

### Standard Error Responses
| HTTP Status | Meaning |
|-------------|---------|
| 200 | Success |
| 201 | Created |
| 400 | Bad request (missing/invalid fields) |
| 401 | Unauthorized (missing/invalid JWT) |
| 403 | Forbidden (authenticated but not authorized) |
| 404 | Not found |
| 409 | Conflict (duplicate handle, already exists) |
| 410 | Gone (resource no longer available, e.g. closed poll) |
| 413 | Request body too large |
| 429 | Rate limit exceeded (includes `Retry-After` header) |
| 500 | Internal server error |

### Rate Limit Headers
```
X-RateLimit-Limit: <max_requests>
X-RateLimit-Remaining: <remaining>
X-RateLimit-Reset: <unix_timestamp>
Retry-After: <seconds>
```

### Global Rate Limits
| Scope | Limit | Applied By |
|-------|-------|------------|
| Per IP | 300 req/min | Gateway |
| Per device | 100 req/min | Gateway + per-service |
| OTP request | 10/24h per identifier, 5/h per IP | Auth |
| OTP verify | 10/min per IP | Auth |
| Refresh token | 60/h per IP | Auth |
| Key registration | 5/h per user | IKS |
| Bundle fetch | 100/h per device | IKS |
| Message send | 100/min per device | MRS |
| Media upload | 100/day per user | Media |

---

## E2EE Compliance Summary

| Feature | Server Has Plaintext? | Should Be Client-Side? |
|---------|----------------------|------------------------|
| Message content | **NO** — encrypted blob | ✅ |
| Message editing | **NO** — reference to new envelope | ✅ |
| Location coordinates | **NO** — encrypted in message | ✅ |
| Search | **NO** — metadata only | ✅ |
| Sender identity (sealed) | **NO** — `sender_user_id` is NULL, identity encrypted in payload | ✅ |
| Media files | **NO** — encrypted blob | ✅ |
| Backup data | **NO** — encrypted blob | ✅ |
| Private keys | **NO** — never stored | ✅ |
| User keys (IK, SPK, OPK) | **Public keys only** | ✅ |
| Profile data | **YES** — by design (public) | ✅ |
| Group metadata | **YES** — by design | ✅ |
| Contacts | **YES** — by design | ✅ |
