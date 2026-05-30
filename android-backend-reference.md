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
→ 409: {"error":"Device already registered"} or {"error":"Device already registered for this identity key"}
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

### 2.6 Key Transparency — Latest STH
```
GET /v1/keys/sth/latest      (public)
→ 200: {"tree_size":int64,"root_hash":"base64url","signature":"...","signed_at":"timestamp"}
→ 200: {"tree_size":0,"root_hash":"","message":"No tree heads available yet"}
```

### 2.7 Key Transparency — Specific STH
```
GET /v1/keys/sth/{tree_size}   (public)   // tree_size is numeric
→ 200: {"tree_size":int64,"root_hash":"base64url","signature":"...","signed_at":"timestamp"}
→ 400: {"error":"Invalid tree_size"}
→ 404: {"error":"Tree head not found"}
```

### 2.8 Key Transparency — Inclusion Proof
```
GET /v1/keys/proof/{user_id}[/{device_id}]   (public)
→ 200: {
  "proofs":[{"leaf_index":int64,"siblings":["base64url"],"leaf":"base64url"}],
  "tree_size":int64,"root_hash":"base64url","verified":bool,"user_id":"uuid"
}
→ 400: {"error":"Invalid path"}
→ 404: {"error":"No tree data available"} or {"error":"No key mutations found for this user/device"}
```

---

## 3. Messaging (MRS)

### 3.1 WebSocket Connection (port 8003)
```
wss://host:8003/v1/ws
```
Authenticate via WebSocket frame: POST `/v1/auth` with JWT as body bytes.
The WS protocol uses a custom binary envelope format (Protobuf).

**IMPORTANT:** JWT must NOT be passed as a URL query parameter — it would leak to proxy and access logs (see `SECURITY_ANDROID_PRACTICES.md` section 4.2).

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
→ 200: {"envelope_ids":["uuid"],"server_ts":"ISO8601"}
```

### 3.3 Sealed Sender (Anonymous)
```
POST /v1/messages/sealed-send   (no auth, IP rate limited)
Body: {
  "recipient_user_id": "uuid",
  "message_type": "SIGNAL_MESSAGE",
  "payload": "base64url(encrypted_data)"
}
→ 200: {"envelope_ids":["uuid"],"sealed":true}
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
  }]
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

## 6. Contacts, Blocking & Friend Requests

### 6.1 Add Contact
```
POST /v1/contacts              (auth required)
Body: {
  "contact_user_id": "uuid",         // required
  "custom_name": "string"            // optional, max 64 chars
}
→ 201: {"added":true}
→ 400: {"error":"Invalid contact_user_id"} or {"error":"Cannot add yourself"}
→ 403: {"error":"Cannot add this contact"}
→ 409: {"error":"Contact already exists"}
```

### 6.2 List Contacts
```
GET /v1/contacts               (auth required)
→ 200: {"contacts":[{"contact_user_id":"uuid","custom_name":"Bob","added_ts":"ISO8601"}]}
```

### 6.3 Remove Contact
```
DELETE /v1/contacts/{user_id}  (auth required)
→ 200: {"removed":true}
→ 404: {"error":"Contact not found"}
```

### 6.4 Check Contact
```
GET /v1/contacts/check/{user_id}    (auth required)
→ 200: {"is_contact":bool}
```

### 6.5 Phone Hash Matching (Contact Discovery)
```
POST /v1/contacts/match              (public, IP rate limited)
Body: {"phone_hashes":["hash1","hash2",...]}    // max 1000
→ 200: {"matches":[{"user_id":"uuid","username":"...","display_name":"...","phone_hash":"..."}]}
→ 400: {"error":"Missing or invalid phone_hashes array"} or {"error":"Too many hashes (max 1000)"}
```

### 6.6 Send Friend Request
```
POST /v1/friend-requests          (auth required)
Body: {"to_user_id":"uuid"}
→ 201: {"id":"uuid","status":"pending"}
→ 400: {"error":"Cannot send friend request to yourself"}
→ 409: {"error":"Friend request already exists"}
```

### 6.7 List Incoming Friend Requests
```
GET /v1/friend-requests/incoming    (auth required)
→ 200: {"requests":[{"id":"uuid","from_user_id":"uuid","created_ts":1690000000}]}
```

### 6.8 List Outgoing Friend Requests
```
GET /v1/friend-requests/outgoing    (auth required)
→ 200: {"requests":[{"id":"uuid","to_user_id":"uuid","created_ts":1690000000}]}
```

### 6.9 Accept Friend Request
```
PUT /v1/friend-requests/{id}/accept    (auth required)
→ 200: {"status":"accepted","friend_user_id":"uuid"}
→ 400: {"error":"Friend request is not pending"}
→ 403: {"error":"Not authorized to accept this request"}
→ 404: {"error":"Friend request not found"}
```

### 6.10 Decline Friend Request
```
PUT /v1/friend-requests/{id}/decline    (auth required)
→ 200: {"status":"declined"}
→ 403: {"error":"Not authorized to decline this request"}
→ 404: {"error":"Friend request not found"}
```

### 6.11 Cancel Friend Request
```
DELETE /v1/friend-requests/{id}    (auth required)
→ 200: {"status":"cancelled"}
→ 404: {"error":"Friend request not found or not authorized"}
```

### 6.12 Block User
```
POST /v1/blocks/{user_id}      (auth required)
→ 201
```

### 6.13 Unblock User
```
DELETE /v1/blocks/{user_id}    (auth required)
→ 200
```

### 6.14 List Blocks
```
GET /v1/blocks                 (auth required)
→ 200: {"blocks":[{"blocked_user_id":"uuid","blocked_ts":"ISO8601"}]}
```

---

## 7. Groups

### 7.1 Create
```
POST /v1/groups                (auth required)
Body: {
  "name": "Team Chat",                  // 1-100 chars, required
  "description": "Project group",       // max 512, optional
  "initial_member_ids": ["uuid","uuid"],// optional, max 499
  "add_members_policy": "ALL_MEMBERS",  // optional, default ALL_MEMBERS
  "join_type": "INVITE_ONLY"            // INVITE_ONLY|LINK|APPROVAL_REQUIRED
}
→ 201: {"group_id":"uuid","name":"Team Chat","member_count":int}
```

### 7.2 List Groups
```
GET /v1/groups                 (auth required)
→ 200: {"groups":[{"group_id":"uuid","name":"Team Chat","member_count":5,"role":"OWNER","joined_ts":"ISO8601"}]}
```

### 7.3 Update Group
```
PUT /v1/groups/{id}            (auth required)
Body (all optional): {
  "name": "string",
  "description": "string",
  "add_members_policy": "string",
  "edit_info_policy": "string",
  "join_type": "string"
}
→ 200: {"updated":true}
→ 403: {"error":"Cannot update group"}
```

### 7.4 Group Settings
```
PUT /v1/groups/{id}/settings   (auth required)
Body: {
  "messaging_mode": "CONVERSATIONS",    // optional: ""|"CONVERSATIONS"|"CALLS"
  "disappear_timer_seconds": 0          // optional, default 0
}
→ 200: {"updated":true}
→ 403: {"error":"Not authorized"} or {"error":"Cannot update settings"}
```

### 7.5 Delete Group
```
DELETE /v1/groups/{id}         (auth required, owner only — soft delete)
→ 200: {"deleted":true}
→ 403: {"error":"Only the owner can delete the group"} or {"error":"Cannot delete group"}
```

### 7.6 List Members
```
GET /v1/groups/{id}/members    (auth required)
→ 200: {"members":[{"user_id":"uuid","role":"OWNER|ADMIN|MEMBER|SUPERADMIN","joined_ts":"ISO8601"}],"count":int}
→ 403: {"error":"Not a member"}
```

### 7.7 Add Members
```
POST /v1/groups/{id}/members          (auth required)
Body: {"user_ids":["uuid","uuid"]}    // required, non-empty
→ 200: {"added":int}
→ 400: {"error":"No user_ids provided"} or {"error":"Invalid user_id"}
→ 403: {"error":"Only admins can add members"} or {"error":"Group member limit (500) exceeded"}
```

### 7.8 Remove Member
```
DELETE /v1/groups/{id}/members/{uid}  (auth required)
→ 200: {"removed":true}
→ 403: {"error":"Cannot remove member"}
```

### 7.9 Update Member Role
```
PUT /v1/groups/{id}/members/{uid}/role   (auth required, owner only)
Body: {"role":"ADMIN"}                    // MEMBER|ADMIN|SUPERADMIN
→ 200: {"updated":true}
→ 403: {"error":"Cannot update role"}
```

### 7.10 Invite Link
```
POST   /v1/groups/{id}/invite-link           Body: {"expires_ts":"ISO8601","max_uses":10}
→ 200: {"link_code":"20char_base64url","link_id":"uuid"}
→ 403: {"error":"Only admins can generate invite links"}

DELETE /v1/groups/{id}/invite-link/{link_id}   (auth required)
→ 200: {"revoked":true}
→ 403: {"error":"Not authorized"}

GET    /v1/groups/join/{link_code}              (public — preview group before joining)
→ 200: {"name":"Team Chat","description":"...","member_count":int}
→ 404: {"error":"Invalid invite link"} or {"error":"Group not found"}

POST   /v1/groups/join/{link_code}              (auth required)
→ 200: {"status":"joined","group_id":"uuid","name":"..."}    // or {"status":"pending_approval"}
→ 400: {"error":"Already a member"} or {"error":"Invite link usage limit reached"} or {"error":"Group is full"}
```

### 7.11 Join Requests (Approval-Required Groups)
```
GET  /v1/groups/{id}/join-requests                               (auth required, admins only)
→ 200: {"requests":[{"request_id":"uuid","requester_user_id":"uuid","status":"PENDING","requested_ts":"ISO8601"}]}

PUT  /v1/groups/{id}/join-requests/{request_id}                  (auth required, admins only)
Body: {"approve":true}   // true = approve, false = reject
→ 200: {"approved":true}  // or {"approved":false}
→ 403: {"error":"Not authorized"} or {"error":"Request already processed"}
```

### 7.12 Transfer Ownership
```
PUT /v1/groups/{id}/owner      (auth required)
Body: {"new_owner_user_id":"uuid"}
→ 200: {"updated":true}
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
GET /v1/channels/search?q=tech&limit=20     (public, IP rate limited)
→ 200: {"channels":[{...}],"next_cursor":"..."}
```

### 8.3 Publish Post
```
POST /v1/channels/{id}/posts   (auth, admin only)
Body: {
  "post_type": "TEXT",              // TEXT|IMAGE|VIDEO|FILE|POLL|LINK
  "text_content": "Hello world!",   // max 4096
  "media_id": "uuid",               // optional
  "poll_id": "uuid"                 // optional
}
→ 201: {"post_id":"uuid"}
```

### 8.4 Edit Post
```
POST /v1/channels/{id}/posts/{post_id}   (auth required, author or admin)
Body: {"text_content":"updated content"}
→ 200: {"edited":true,"post_id":"uuid","edited_ts":"ISO8601"}
→ 403: {"error":"..."}    // not authorized
→ 404: {"error":"Post not found"}
```

### 8.5 Pin Post
```
PUT /v1/channels/{id}/posts/{post_id}/pin    (auth required, admin only)
→ 200: {"pinned":true,"post_id":"uuid"}
→ 403: {"error":"Failed to pin post — check permissions or post status"}
```

### 8.6 Delete Post
```
DELETE /v1/channels/{id}/posts/{post_id}    (auth required, admin or post author)
→ 200: {"deleted":true,"post_id":"uuid"}
→ 404: {"error":"Post not found or not authorized"}
```

### 8.7 Subscribe
```
POST   /v1/channels/{id}/subscribe     (auth required, max 500)
Body: {"invite_token":"string"}        // optional, required for private channels
→ 200: {"subscribed":true,"subscription_id":"string"}
→ 403: {"error":"..."}    // private channel, no valid invite
→ 429: Maximum subscriptions reached (500)

DELETE /v1/channels/{id}/subscribe      (auth required)
→ 200: {"subscribed":false}
→ 404: {"error":"Not subscribed to this channel"}
```

### 8.8 Generate Invite
```
POST /v1/channels/{id}/invite      (auth required, owner/admin only)
→ 200: {"invite_url":"string","expires_ts":"ISO8601"}
→ 403: {"error":"..."}    // not authorized
```

### 8.9 Admin Management
```
PUT    /v1/channels/{id}/admins/{user_id}      (auth required, owner only)
→ 200: {"admin_added":true,"user_id":"uuid"}
→ 403: {"error":"..."}    // not authorized

DELETE /v1/channels/{id}/admins/{user_id}      (auth required, owner only)
→ 200: {"admin_removed":true,"user_id":"uuid"}
→ 403: {"error":"..."}    // not authorized
→ 404: {"error":"..."}    // user not an admin
```

### 8.10 Feed
```
GET /v1/channels/{id}/posts?before=cursor&limit=20    (auth optional for public)
→ 200: {"posts":[{...}],"next_cursor":"..."}
```

---

## 9. Reactions & Polls

### 9.1 React to Message
```
PUT    /v1/reactions/{message_id}       Body: {"emoji":"❤️","conversation_id":"uuid"}
DELETE /v1/reactions/{message_id}
GET    /v1/reactions/{message_id}           → aggregate counts
GET    /v1/reactions/{message_id}/{emoji}   → list reactors
```

### 9.2 Polls
```
POST   /v1/polls                    Body: {
  "conversation_id":"uuid","question":"...","options":[{"id":"1","text":"Yes"}],
  "allow_multiple":false,"anonymous":false,"closes_in_hours":24
}
POST   /v1/polls/{id}/vote          Body: {"option_ids":["1"]}
GET    /v1/polls/{id}
GET    /v1/polls/{id}/voters/{option_id}
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
  "timer_seconds": 300,                 // 0=off, 86400, 604800, 7776000
  "timer_mode": "FROM_SEND"             // FROM_SEND|FROM_VIEW
}
→ 200
```

### 10.2 Get Timer
```
GET /v1/disappear/{conversation_id}   (auth required)
→ 200: {"timer_seconds":300,"timer_mode":"FROM_SEND","conversation_type":"DIRECT"}
```

### 10.3 Record Message Viewed
```
POST /v1/disappear/viewed        (auth required)
Body: {"envelope_id":"uuid"}
→ 200: {"status":"viewed","envelope_id":"uuid"}
→ 400: {"error":"Invalid envelope_id"}
→ 410: {"error":"This media has already been viewed"}
```

### 10.4 Batch Record Viewed
```
POST /v1/disappear/bulk_viewed   (auth required)
Body: {"envelope_ids":["uuid","uuid",...]}    // max 500
→ 200: {"status":"viewed","count":int}
→ 400: {"error":"Max 500 envelope_ids per request"}
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
Body: raw binary chunk (max 100 MB)
Headers: X-Chunk-Index: 0, X-SHA256: "base64url(hash)"
→ 200: {"received":true,"chunk_index":0}
```

### 11.3 Finalize
```
POST /v1/backup/finalize/{backup_id}  (auth required)
Body: {"sha256":"base64url(hash)"}
→ 200: {"backup_id":"uuid","version":1,"size_bytes":1048576,"sha256":"base64url"}
```

### 11.4 Get Latest Backup
```
GET /v1/backup/latest         (auth required)
→ 200: {"backup_id":"uuid","version":1,"created_ts":"ISO8601","size_bytes":1048576,"includes_media":false,"sha256":"base64url"}
→ 404: {"error":"No backup found"}
```

### 11.5 Download
```
GET /v1/backup/download/{backup_id}   (auth required)
→ 200: raw binary (Header: X-SHA256)
```

### 11.6 Delete All Backups
```
DELETE /v1/backup             (auth required)
→ 200: {"deleted":true}
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

### 12.3 Get Single Status
```
GET /v1/status/{status_id}     (auth required)
→ 200: same shape as feed entry
```

### 12.4 Get Status Views
```
GET /v1/status/{status_id}/views    (auth required)
→ 200: {"views":[...]}
```

### 12.5 Delete Status
```
DELETE /v1/status/{status_id}  (auth required)
→ 200
```

---

## 13. Stickers

### 13.1 Featured Packs
```
GET /v1/stickers/packs/featured     (public)
→ 200: {"packs":[{"pack_id":"uuid","name":"...","description":"...","sticker_count":12,"tags":"..."}]}
```

### 13.2 Search Packs
```
GET /v1/stickers/packs/search?q=cat&page=0&limit=20    (auth optional)
→ 200: {"packs":[{...}],"page":0,"has_more":true}
→ 400: {"error":"Query parameter 'q' is required"} or {"error":"Query too long (max 100 characters)"}
```

### 13.3 Pack Detail
```
GET /v1/stickers/packs/{pack_id}    (auth required)
→ 200: {
  "pack_id":"uuid","name":"...","description":"...","creator_user_id":"uuid",
  "featured":false,"tags":"...","sticker_count":12,
  "stickers":[{"sticker_id":"uuid","media_id":"uuid","emoji_tags":["❤️"],"text_tags":[],"sort_order":0}]
}
→ 400: {"error":"Invalid pack_id format"}
→ 404: {"error":"Sticker pack not found"}
```

### 13.4 Create Pack
```
POST /v1/stickers/packs         (auth required)
Body: {
  "name":"My Pack",                  // required, max 100
  "stickers": [                      // required, 3-30 items
    {"media_id":"uuid","emoji_tags":["❤️"],"text_tags":["hello"]}
  ]
}
→ 201: {"pack_id":"uuid","name":"My Pack","sticker_count":int}
→ 400: validation errors (missing name, invalid count, etc.)
```

### 13.5 Install Pack
```
POST /v1/stickers/library/{pack_id}     (auth required)
→ 200: {"installed":true,"pack_id":"uuid"}
→ 404: {"error":"Sticker pack not found"}
```

### 13.6 Uninstall Pack
```
DELETE /v1/stickers/library/{pack_id}   (auth required)
→ 200: {"installed":false}
→ 404: {"error":"Sticker pack not installed"}
```

### 13.7 My Library
```
GET /v1/stickers/library            (auth required)
?page=0&limit=20
→ 200: {"packs":[{...}],"page":0,"has_more":false}
```

### 13.8 Recent Stickers
```
GET /v1/stickers/recent             (auth required)
→ 200: {"stickers":[{"sticker_id":"uuid","media_id":"uuid","pack_id":"uuid","emoji_tags":["❤️"],"text_tags":[],"used_ts":"ISO8601"}]}
```

### 13.9 Record Sticker Usage
```
POST /v1/stickers/recent/{sticker_id}    (auth required)
→ 200: {"recorded":true}
→ 404: {"error":"Sticker not found"}
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

### 14.2 Get Bot Info
```
GET /v1/bots/{bot_id}          (auth required)
→ 200: {...bot info...}
```

### 14.3 Set Webhook
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
Body: {"new_envelope_id":"uuid"}    // the new encrypted envelope with the edit
→ 200: {"success":true,"original_envelope_id":"uuid","new_envelope_id":"uuid","edit_count":int}
→ 400: {"error":"Message not found"} or {"error":"Cannot edit another user's message"} or {"error":"Maximum edits reached (2)"}
```

### 15.2 Reply Preview
```
GET /v1/messages/{envelope_id}/reply      (auth required)
→ 200: {"envelope_id":"uuid","sender_id":"uuid","ts":"ISO8601"}
→ 404: {"error":"Message not found"}
```

### 15.3 Search Messages
```
GET /v1/search/messages?q=hello&limit=20&before=cursor   (auth required)
→ 200: {"results":[{...}],"next_cursor":"..."}
```

### 15.4 Play Audio/Video Note (One-Time)
```
GET /v1/notes/{envelope_id}/play       (auth required)
→ 200: {"success":true,"note_id":"...","media_id":"...","one_time_playback":true}
→ 404: {"error":"Note not found"}
→ 410: {"error":"Note already played"}
```

### 15.5 Share Location
```
POST /v1/location                   (auth required)
Body: {"envelope_id":"uuid"}        // coordinates encrypted inside the envelope
→ 200: {"success":true,"envelope_id":"uuid"}
→ 400: {"error":"Invalid envelope_id"}

GET  /v1/location/{envelope_id}     (auth required)
→ 200: {"envelope_id":"uuid","note":"Location data is encrypted inside the message envelope"}
→ 400: {"error":"Invalid envelope_id"}
```

### 15.6 Share Contact
```
POST /v1/contacts/share             (auth required)
Body: {
  "envelope_id":"uuid",             // required
  "name":"Alice",                   // required
  "phones":["+15551234567"],        // optional
  "emails":["alice@example.com"]    // optional — at least one of phones/emails required
}
→ 200: {"success":true,"envelope_id":"uuid","name":"Alice","phones":["..."],"emails":["..."]}
→ 400: {"error":"Invalid envelope_id"} or {"error":"name is required"} or {"error":"At least one phone or email is required"}
```

### 15.7 Translate Message
```
POST /v1/messages/{envelope_id}/translate      (auth required)
Body: {"target_language":"es"}    // or query param ?target=es
Supported: en, es, fr, de, it, pt, zh, ja, ko, ar, ru, hi
→ 200: {"translated_text":"Hola","cached":false,"source_lang":"auto","target_lang":"es"}
→ 400: {"error":"Unsupported language. Supported: en, es, fr, de, it, pt, zh, ja, ko, ar, ru, hi"}
→ 429: {"error":"Daily translation limit reached (50)"}
```

### 15.8 Archive Chat
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

### 17.1 Get Global Preferences
```
GET /v1/notifications/preferences                              (auth required)
→ 200: {
  "master_notifications_on": bool,
  "message_notifications_on": bool,
  "call_notifications_on": bool,
  "status_notifications_on": bool,
  "channel_notifications_on": bool,
  "mention_notifications_on": bool,
  "show_preview": bool,
  "dnd_enabled": bool,
  "dnd_start_time": "HH:MM",
  "dnd_end_time": "HH:MM",
  "dnd_timezone": "string"
}
```

### 17.2 Update Global Preferences
```
PUT /v1/notifications/preferences                              (auth required)
Body: any subset of preference fields (same fields as GET)
→ 200: full updated preferences object
```

### 17.3 List Per-Conversation Preferences
```
GET /v1/notifications/preferences/conversations                (auth required)
?page=0&limit=200
→ 200: {
  "conversations": [{
    "conversation_id":"uuid","conversation_type":"DIRECT",
    "muted":false,"mute_expires_ts":"ISO8601","mentions_only":false,"custom_sound":"default"
  }],
  "page":0,"has_more":false
}
```

### 17.4 Set Per-Conversation Preferences
```
PUT /v1/notifications/preferences/conversations/{conversation_id}  (auth required)
Body: {
  "muted": true,
  "mute_expires_ts": "ISO8601",
  "mentions_only": false,
  "custom_sound": "default"
}
→ 200: full updated conversation preference object
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
4. **Sync contacts:** POST `/v1/contacts/match` with phone hashes
5. **Fetch contacts:** GET `/v1/contacts`
6. **Fetch profiles:** GET `/v1/profile/{user_id}` for each contact
7. **Connect WebSocket:** `ws://host:8003/v1/ws` (send JWT via POST /v1/auth frame)
8. **Fetch pending:** GET `/v1/messages/pending`
9. **Load preferences:** GET `/v1/notifications/preferences`
10. **Ready:** Send/receive messages via WebSocket
11. **Periodic:** Refresh JWT every 15 min via `/v1/auth/refresh`
12. **Periodic:** Check OPK count via `/v1/keys/opk-count`, upload more if < 50
