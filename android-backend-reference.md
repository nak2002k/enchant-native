# SecureChat Backend — Android Integration Guide

Complete API reference for integrating a SecureChat Android client with the backend.

- **Base URL (dev):** `http://<host>:8080` (gateway proxies to all services)
- **Auth:** Ed25519 JWT via `Authorization: Bearer <token>`
- **Crypto:** libenchantcrypto (see `docs/libenchantcrypto-android-reference.md`)

---

## 1. Authentication Flow

### 1.1 Request OTP
```
POST /v1/auth/request-otp
Body: {"identifier": "+14155551234"}   // phone: +[1-9]\\d{6,14} or email
→ 200: {"challenge_id":"uuid","expires_in":600}
→ 400: {"error":"Invalid identifier format"}
```

### 1.2 Verify OTP
```
POST /v1/auth/verify-otp
Body: {
  "challenge_id": "uuid",
  "otp": "123456",
  "device_info": {                      // optional
    "device_id": "uuid",                // UUID, reuses existing device if available
    "user_agent": "SecureChat/1.0.0"    // 255 char max, printable ASCII
  }
}
→ 200: {
  "access_token": "eyJ...",
  "refresh_token": "abc...",
  "device_id": "uuid",
  "user_id": "uuid",
  "expires_in": 900
}
→ 401: {"error":"Invalid OTP"}
→ 429: {"error":"Too many attempts"}
```

### 1.3 Refresh Token
```
POST /v1/auth/refresh
Body: {
  "refresh_token": "abc...",
  "device_info": {"device_id":"uuid","user_agent":"..."}
}
→ 200: {new access_token, new refresh_token, device_id, user_id, expires_in}
→ 401: {"error":"Invalid or expired refresh token"}
```

### 1.4 JWT Format
```
Header:  {"alg":"EdDSA","typ":"JWT"}
Payload: {"sub":"user_id","did":"device_id","iat":1690000000,"exp":1690000900,"jti":"random_hex_32"}
Signature: Ed25519 (64 bytes, base64url)
```
All authenticated requests: `Authorization: Bearer <jwt>`

### 1.5 Logout
```
POST /v1/auth/logout       (auth required)
Body: {"refresh_token":"abc..."}
→ 200
```

### 1.6 List Devices
```
GET /v1/auth/devices        (auth required)
→ 200: {"devices":[{"device_id":"uuid","created_ts":"ISO8601","last_seen_ts":"ISO8601"}]}
```

### 1.7 Revoke Device
```
DELETE /v1/auth/devices/{device_id}   (auth required)
→ 200
```

### 1.8 Delete Account
```
DELETE /v1/auth/account     (auth required)
→ 200
```

### 1.9 JWKS (Public Key)
```
GET /v1/auth/.well-known/jwks.json   (public)
→ 200: {"keys":[{"kty":"OKP","crv":"Ed25519","x":"base64url_public_key","use":"sig","alg":"EdDSA"}]}
```

---

## 2. Key Registration (IKS)

### 2.1 Register Keys
```
POST /v1/keys/register      (auth required)
Body: {
  "identity_key": "base64url(32 bytes)",          // X25519 public key
  "signed_prekey": {
    "public_key": "base64url(32 bytes)",           // X25519 public key
    "signature": "base64url(64 bytes)"             // Ed25519 sig of public_key by identity_key
  },
  "one_time_prekeys": [
    {"public_key": "base64url(32 bytes)"},         // X25519 public, min 20, max 100
    ...
  ]
}
→ 201: {"device_id":"uuid"}
→ 409: {"error":"Device already registered"}
→ 400: {"error":"At least 20 one-time prekeys required"}
```

### 2.2 Fetch Key Bundle
```
GET /v1/keys/bundle/{user_id}    (auth required)
→ 200: {
  "devices": [{
    "device_id": "uuid",
    "identity_key": "base64url(32)",
    "signed_prekey": {"public_key":"base64url(32)","signature":"base64url(64)"},
    "one_time_prekey": {"key_id":0,"public_key":"base64url(32)"}  // null if exhausted
  }]
}
→ 400: {"error":"Invalid user UUID"}
→ 404: {"error":"No devices found for user"}
```

### 2.3 Rotate Signed Prekey
```
PUT /v1/keys/signed-prekey   (auth required)
Body: {
  "public_key": "base64url(32)",
  "signature": "base64url(64)"
}
→ 200: {"status":"ok"}
→ 422: {"error":"Signature verification failed"}
→ 422: {"error":"Signed prekey already used"}
```

### 2.4 Upload More OPKs
```
POST /v1/keys/one-time-prekeys   (auth required)
Body: {"one_time_prekeys": [{"public_key":"..."}, ...]}   // max 100 per batch
→ 200: {"total_opks": 75}
→ 400: {"error":"Maximum 100 one-time prekeys per upload"}
```

### 2.5 OPK Count
```
GET /v1/keys/opk-count       (auth required)
→ 200: {"device_id":"uuid","opk_count":42}
```

---

## 3. Messaging (MRS)

### 3.1 WebSocket Connection (port 8003)
```
wss://host:8003/v1/ws?token={jwt}
```
Authenticate by sending the JWT as a query param on connect.
The WS protocol uses a custom binary envelope format (Protobuf).

**Message Types:**
- `SIGNAL_MESSAGE` — encrypted text
- `PREKEY_MESSAGE` — initial X3DH handshake message
- `CALL_OFFER/CALL_ANSWER/CALL_ICE/CALL_END` — WebRTC signaling
- `DELIVERY_RECEIPT/READ_RECEIPT` — delivery confirmations
- `TYPING_START/TYPING_STOP` — typing indicators

### 3.2 REST Send Message
```
POST /v1/messages/send        (auth required)
Body: {
  "recipient_user_id": "uuid",
  "recipient_device_id": "uuid",       // optional, broadcasts to all devices if omitted
  "message_type": "SIGNAL_MESSAGE",
  "payload": "base64url(encrypted_data)",
  "sender_ts": 1690000000
}
→ 201: {"envelope_id":"uuid","server_ts":"ISO8601"}
```

### 3.3 Sealed Sender (Anonymous)
```
POST /v1/messages/sealed-send   (no auth, IP rate limited)
Body: {
  "recipient_user_id": "uuid",
  "message_type": "SIGNAL_MESSAGE",
  "payload": "base64url(encrypted_data)"
}
→ 201
```

### 3.4 Fetch Pending Messages
```
GET /v1/messages/pending       (auth required)
→ 200: {
  "messages": [{
    "envelope_id": "uuid",
    "sender_user_id": "uuid",
    "sender_device_id": "uuid",
    "message_type": "SIGNAL_MESSAGE",
    "payload": "base64url",
    "server_ts": "ISO8601",
    "sender_ts": 1690000000
  }],
  "has_more": false
}
```

### 3.5 TURN Credentials
```
GET /v1/calls/turn-credentials   (auth required)
→ 200: {
  "username": "timestamp:session_id",
  "password": "hmac_sha256(username, secret)",
  "ttl": 86400,
  "uris": ["turn:host:3478?transport=udp"]
}
```

---

## 4. Media

### 4.1 Upload
```
POST /v1/media/upload          (auth required)
Headers: Content-Type: application/octet-stream
Body: raw encrypted bytes (max 128 MB)
→ 201: {
  "media_id": "uuid",
  "download_url": "/v1/media/{media_id}",
  "expires_ts": "ISO8601"
}
```

### 4.2 Download
```
GET /v1/media/{media_id}       (auth required)
→ 200: raw binary (Content-Type from stored mime hint)
→ 404: {"error":"Media not found"}
```

### 4.3 Delete
```
DELETE /v1/media/{media_id}    (auth required)
→ 200
```

---

## 5. Profile

### 5.1 Get Profile
```
GET /v1/profile/{user_id}      (auth required)
→ 200: {
  "user_id": "uuid",
  "username": "alice123",
  "display_name": "Alice",
  "about": "Hey there!",
  "avatar_media_id": "uuid",        // null if no avatar
  "last_seen_ts": "ISO8601",
  "privacy": {                      // visibility respects privacy settings
    "last_seen_visibility": "CONTACTS",
    "online_visibility": "CONTACTS",
    "avatar_visibility": "EVERYONE",
    "about_visibility": "EVERYONE"
  }
}
```

### 5.2 Update Profile
```
PUT /v1/profile                 (auth required)
Body: {
  "username": "new_username",       // [a-z0-9_]{3,32}
  "display_name": "New Name",       // 1-64 chars
  "about": "New bio"                // max 139 chars
}
→ 200
```

### 5.3 Upload Avatar
```
POST /v1/profile/avatar         (auth required)
Content-Type: application/octet-stream
Body: raw encrypted avatar bytes (max 5 MB)
→ 200: {"avatar_media_id":"uuid","avatar_updated_ts":"ISO8601"}
```

### 5.4 Privacy Settings
```
PUT /v1/profile/privacy         (auth required)
Body: {
  "last_seen_visibility": "CONTACTS",     // EVERYONE|CONTACTS|NOBODY
  "online_visibility": "CONTACTS",
  "avatar_visibility": "EVERYONE",
  "about_visibility": "EVERYONE",
  "read_receipts_enabled": true,
  "groups_add_policy": "CONTACTS"
}
→ 200
```

### 5.5 Search Users
```
GET /v1/profile/search?q=alice    (auth required)
→ 200: {"users":[{"user_id":"uuid","username":"alice123","display_name":"Alice","avatar_media_id":"uuid"}]}
```

---

## 6. Contacts & Blocking

### 6.1 Add Contact
```
POST /v1/contacts              (auth required)
Body: {"contact_user_id":"uuid"}
→ 201
```

### 6.2 List Contacts
```
GET /v1/contacts               (auth required)
→ 200: {"contacts":[{"contact_id":"uuid","contact_user_id":"uuid","custom_name":"Bob","added_ts":"ISO8601"}]}
```

### 6.3 Remove Contact
```
DELETE /v1/contacts/{user_id}  (auth required)
→ 200
```

### 6.4 Block User
```
POST /v1/blocks/{user_id}      (auth required)
→ 201
```

### 6.5 Unblock User
```
DELETE /v1/blocks/{user_id}    (auth required)
→ 200
```

### 6.6 List Blocks
```
GET /v1/blocks                 (auth required)
→ 200: {"blocks":[{"blocked_user_id":"uuid","blocked_ts":"ISO8601"}],"next_cursor":null}
```

---

## 7. Groups

### 7.1 Create
```
POST /v1/groups                (auth required)
Body: {
  "name": "Team Chat",              // 1-100 chars
  "description": "Project group",  // max 512
  "member_ids": ["uuid","uuid"],   // initial members
  "join_type": "INVITE_ONLY"       // INVITE_ONLY|LINK|APPROVAL_REQUIRED
}
→ 201: {"group_id":"uuid","created_ts":"ISO8601"}
```

### 7.2 List Groups
```
GET /v1/groups                 (auth required)
→ 200: {"groups":[{"group_id":"uuid","name":"Team Chat","member_count":5,"role":"OWNER"}]}
```

### 7.3 Add/Remove Members
```
POST   /v1/groups/{id}/members          Body: {"user_ids":["uuid"]}
DELETE /v1/groups/{id}/members/{uid}
PUT    /v1/groups/{id}/members/{uid}/role   Body: {"role":"ADMIN"}  // MEMBER|ADMIN|SUPERADMIN
```

### 7.4 Invite Link
```
POST   /v1/groups/{id}/invite-link          Body: {"max_uses":10,"expires_in_hours":24}
DELETE /v1/groups/{id}/invite-link/{link_id}
POST   /v1/groups/join/{link_code}          → Join via code
```

### 7.5 Transfer Ownership
```
PUT /v1/groups/{id}/owner
Body: {"new_owner_user_id":"uuid"}
```

---

## 8. Channels (Broadcast)

### 8.1 Create
```
POST /v1/channels              (auth required)
Body: {
  "name": "My Channel",             // 1-128 chars
  "handle": "mychannel_123",        // [a-z0-9_]{3,32}
  "description": "About",
  "channel_type": "PUBLIC"          // PUBLIC|PRIVATE
}
→ 201: {"channel_id":"uuid"}
```

### 8.2 Search
```
GET /v1/channels/search?q=tech&limit=20     (public, rate limited)
→ 200: {"channels":[{...}],"next_cursor":"..."}
```

### 8.3 Publish Post
```
POST /v1/channels/{id}/posts   (auth, admin only)
Body: {
  "post_type": "TEXT",              // TEXT|IMAGE|VIDEO|FILE|POLL|LINK
  "text_content": "Hello world!",   // max 4096
  "media_id": "uuid",
  "poll_id": "uuid"
}
→ 201: {"post_id":"uuid"}
```

### 8.4 Subscribe
```
POST   /v1/channels/{id}/subscribe     (max 500 per user)
DELETE /v1/channels/{id}/subscribe
```

### 8.5 Feed
```
GET /v1/channels/{id}/posts?before=cursor&limit=20    (auth optional for public)
→ 200: {"posts":[{...}],"next_cursor":"..."}
```

---

## 9. Reactions & Polls

### 9.1 React to Message
```
PUT    /v1/reactions/{message_id}    Body: {"emoji":"❤️","conversation_id":"uuid"}
DELETE /v1/reactions/{message_id}
GET    /v1/reactions/{message_id}        → aggregate counts
GET    /v1/reactions/{message_id}/{emoji} → list reactors
```

### 9.2 Polls
```
POST   /v1/polls                    Body: {
  "conversation_id":"uuid","question":"...","options":[{"id":"1","text":"Yes"}],
  "allow_multiple":false,"anonymous":false,"closes_in_hours":24
}
POST   /v1/polls/{id}/vote          Body: {"option_ids":["1"]}
GET    /v1/polls/{id}
PUT    /v1/polls/{id}/close
DELETE /v1/polls/{id}
```

---

## 10. Disappearing Messages

### 10.1 Set Timer
```
PUT /v1/disappear/{conversation_id}   (auth required)
Body: {
  "conversation_type": "DIRECT",        // DIRECT|GROUP
  "timer_seconds": 300,                 // 0 = off
  "timer_mode": "FROM_SEND"             // FROM_SEND|FROM_VIEW
}
→ 200
```

### 10.2 Get Timer
```
GET /v1/disappear/{conversation_id}   (auth required)
→ 200: {"timer_seconds":300,"timer_mode":"FROM_SEND"}
```

---

## 11. Encrypted Backup

### 11.1 Initiate
```
POST /v1/backup/initiate       (auth required)
Body: {
  "total_size": 1048576,            // total ciphertext size in bytes
  "total_chunks": 10,
  "includes_media": false,
  "sha256": "hex_hash"              // SHA-256 of entire plaintext backup
}
→ 201: {"backup_id":"uuid","version":1,"chunk_size":104857}
```

### 11.2 Upload Chunks
```
PUT /v1/backup/chunk/{backup_id}    (auth required)
Body: raw binary chunk
Headers: X-Chunk-Index: 0, X-SHA256: "hex_hash_of_chunk"
→ 200: {"received":true,"chunk_index":0}
```

### 11.3 Finalize
```
POST /v1/backup/finalize/{backup_id}  (auth required)
Body: {"sha256":"hex_hash_of_complete_backup"}
→ 200: {"backup_id":"uuid","size_bytes":1048576}
```

### 11.4 Download
```
GET /v1/backup/download/{backup_id}   (auth required)
→ 200: raw binary (Header: X-SHA256)
```

---

## 12. Status / Stories

### 12.1 Post Status
```
POST /v1/status                (auth required)
Body: {
  "status_type": "TEXT",            // TEXT|IMAGE|VIDEO|AUDIO|GIF
  "text_content": "Hello!",         // max 700
  "text_background": "#FF0000",
  "media_id": "uuid",
  "privacy_setting": "ALL_CONTACTS"  // ALL_CONTACTS|SELECTED_CONTACTS|ALL_EXCEPT
}
→ 201: {"status_id":"uuid","expires_ts":"ISO8601"}  // 24h from now
```

### 12.2 Get Feed
```
GET /v1/status/feed            (auth required)
→ 200: {"entries":[{"author_user_id":"uuid","status_id":"uuid","seen":false}]}
```

---

## 13. Stickers

### 13.1 Featured Packs
```
GET /v1/stickers/packs/featured     (public)
→ 200: {"featured":[{"pack_id":"uuid","name":"...","sticker_count":12}]}
```

### 13.2 Install Pack
```
POST /v1/stickers/library/{pack_id}  (auth required)
→ 200
```

### 13.3 Recent Stickers
```
GET /v1/stickers/recent         (auth required)
→ 200: {"stickers":[{"sticker_id":"uuid","media_id":"uuid","use_count":5}]}
```

---

## 14. Bot API

### 14.1 Register Bot
```
POST /v1/bots/register         (auth required)
Body: {
  "display_name": "MyBot",
  "description": "A helpful bot",
  "commands": [{"command":"/help","description":"Show help"}],
  "is_group_bot": false,
  "is_channel_bot": false
}
→ 201: {"bot_id":"uuid","bot_token":"hex_token"}
```

### 14.2 Set Webhook
```
POST /v1/bots/webhook          (auth required)
Body: {"bot_id":"uuid","webhook_url":"https://myserver.com/bot"}
→ 200
```

---

## 15. Chat Features

### 15.1 Edit Message
```
PUT /v1/messages/{envelope_id}      (auth required)
Body: {"text":"updated content"}
→ 200
```

### 15.2 Search Messages
```
GET /v1/search/messages?q=hello&limit=20&before=cursor   (auth required)
→ 200: {"results":[{...}],"next_cursor":"..."}
```

### 15.3 Archive Chat
```
POST   /v1/chats/{chat_id}/archive
DELETE /v1/chats/{chat_id}/archive
GET    /v1/chats/archived
```

---

## 16. Data Export
```
POST /v1/export/request               (auth required)
Body: {"include_media":true}
→ 201: {"export_id":"uuid"}

GET /v1/export/{export_id}            (auth required)
→ 200: {"status":"COMPLETED","download_token":"..."}  // PENDING|PROCESSING|COMPLETED|FAILED
```

---

## 17. Notification Preferences
```
GET  /v1/notifications/preferences                              (auth required)
→ 200: {"master_notifications_on":true,"show_preview":true,"sound":"default"}

PUT  /v1/notifications/preferences                              (auth required)
Body: {"master_notifications_on":false,"show_preview":false,"sound":"silent"}

PUT  /v1/notifications/preferences/conversations/{conversation_id}  (auth required)
Body: {"notification_type":"none","mute_until":"ISO8601"}
```

---

## 18. Common Error Codes

| Code | Meaning |
|------|---------|
| 200 | Success |
| 201 | Created |
| 400 | Bad request / validation error |
| 401 | Unauthorized (missing/invalid/expired JWT) |
| 403 | Forbidden (insufficient permissions) |
| 404 | Not found |
| 409 | Conflict (duplicate, already exists) |
| 410 | Gone (resource already consumed) |
| 413 | Payload too large |
| 422 | Unprocessable (signature/verification failed) |
| 429 | Rate limited |
| 500 | Internal server error |

---

## 19. Crypto Integration (libenchantcrypto)

See `docs/libenchantcrypto-android-reference.md` for the full C API.

Key operations your Android client must perform:
1. **Generate X25519 identity key** → `enchant_x25519_keypair`
2. **Generate signed prekey** → `enchant_x25519_keypair` + `enchant_ed25519_sign`
3. **Generate OPKs** → `enchant_x25519_keypair` (25 per batch)
4. **X3DH key agreement** → `enchant_x25519_dh` + `enchant_hkdf_sha256`
5. **Message encrypt/decrypt** → `enchant_xchacha20_encrypt/decrypt`
6. **JWT signing** → `enchant_ed25519_sign` (use server's JWKS public key for verify)
7. **Identity key verification** → `enchant_ed25519_verify`

---

## 20. Quick Client Startup Checklist

1. **First launch:** Generate X25519 identity key → store in secure keychain
2. **Register:** POST `/v1/auth/request-otp` → `/v1/auth/verify-otp` → get JWT
3. **Upload keys:** POST `/v1/keys/register` with identity key + SPK + 25 OPKs
4. **Fetch contacts:** GET `/v1/contacts`
5. **Fetch profiles:** GET `/v1/profile/{user_id}` for each contact
6. **Connect WebSocket:** `ws://host:8003/v1/ws?token={jwt}`
7. **Fetch pending:** GET `/v1/messages/pending`
8. **Ready:** Send/receive messages via WebSocket
9. **Periodic:** Refresh JWT every 15 min via `/v1/auth/refresh`
10. **Periodic:** Check OPK count via `/v1/keys/opk-count`, upload more if < 50
