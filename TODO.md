# Enchant Native — Missing & Broken Items

**Last Updated:** 2026-05-29
**Purpose:** Track what doesn't work, what's incomplete, and what must be built for Signal/WhatsApp parity.

---

## 🔴 CRITICAL — Breaks Functionality

### K1: OPK Overwrite Bug (KeyManager.kt:296-315)
**Severity:** HIGH — silently loses one-time prekeys making messages undeliverable

`topUpOpks()` has two issues:
1. Reads `json["opk_count"]` but server returns `json["remaining"]` — top-up never triggers (FIXED: line 307)
2. `PreKeyDao` is not implemented in `:core:database` and not wired to `KeyManager` — `loadFromDb()` never called, so `getOneTimePreKeyCount()` returns 0 after restart, causing `startId=0` to regenerate duplicate OPKs

**Fix:** Implement `PreKeyDao` in `:core:database`, wire to `KeyManager.init(store=preKeyStore)`, call `preKeyStore.loadFromDb()` on startup.
**Status:** PARTIALLY FIXED — `opk_count`→`remaining` typo fixed; full fix requires PreKeyDao implementation and DI wiring

---

### K2: sendReaction Missing conversation_id (MessageSendPipeline.kt:255)
**Severity:** MEDIUM — backend requires `conversation_id` in body, it's not sent

**Actual current code already has the fix:**
```kotlin
suspend fun sendReaction(messageId: String, emoji: String, conversationId: String): Result<Unit> {
    client.put("/v1/reactions/$messageId", buildJsonObject {
        put("emoji", emoji)
        put("conversation_id", conversationId)
    })
}
```
**Status:** FIXED — TODO.md was stale, code already passes conversation_id

---

## 🟡 PARTIAL — Needs Completion

### P1: Notification Preferences (NOT IMPLEMENTED)
No ViewModel/Repository calls `GET /v1/notifications/preferences` or `PUT /v1/notifications/preferences`.
UI exists (`NotificationBuilder.kt`, `MessageNotifier.kt`) but never syncs with backend.

**Fix:** Create `NotificationPreferencesRepository` + call in/settings sync.
**Status:** MISSING

---

### P2: Message Search (NOT IMPLEMENTED)
Backend has `GET /v1/search/messages?q=...&limit=20&before=cursor`, FTS5 exists in SQLCipher.
No repository calls it.

**Fix:** Implement `MessageSearchRepository` + wire to search UI.
**Status:** MISSING (backend ready)

---

### P3: Reply Preview (NOT IMPLEMENTED)
Backend: `GET /v1/messages/{envelope_id}/reply`

Returns `{envelope_id, sender_id, ts}`. Used for "replying to" UI.

**Fix:** Add to `MessageRepository`.
**Status:** MISSING (backend ready)

---

### P4: Location Sharing (NOT IMPLEMENTED)
Backend:
- `POST /v1/location` — body: `{"envelope_id":"uuid"}` (coords encrypted in envelope)
- `GET /v1/location/{envelope_id}` — returns envelope, client must decrypt

**Fix:** Implement `LocationService` + share/location retrieval.
**Status:** MISSING (backend ready)

---

### P5: Contact Sharing (NOT IMPLEMENTED)
Backend: `POST /v1/contacts/share`
Body: `envelope_id`, `name`, `phones[]`, `emails[]` (at least one phone/email required)

**Fix:** Implement in `ContactsRepository`.
**Status:** MISSING (backend ready)

---

### P6: Message Translation (NOT IMPLEMENTED)
Backend: `POST /v1/messages/{envelope_id}/translate`
Body/query: `target_language` (en, es, fr, de, it, pt, zh, ja, ko, ar, ru, hi)
Daily limit 50.

**Fix:** Implement `TranslationService`.
**Status:** MISSING (backend ready)

---

### P7: One-Time Audio/Video Note Playback (NOT IMPLEMENTED)
Backend: `GET /v1/notes/{envelope_id}/play`
- Returns `success, media_id, one_time_playback: true`
- 410 GONE after played once

**Fix:** Add to `MediaService` with consumed-state tracking.
**Status:** MISSING (backend ready)

---

### P8: Group Settings / Disappearing Messages (NOT IMPLEMENTED)
Backend: `PUT /v1/groups/{id}/settings`
Body: `messaging_mode` (CONVERSATIONS|CALLS), `disappear_timer_seconds` (0=off, 86400, 604800, 7776000)

**Fix:** Add to `GroupsRepository`.
**Status:** MISSING (backend ready)

---

### P9: Profile Privacy Settings Update (NOT IMPLEMENTED)
Backend: `PUT /v1/profile/privacy`
Body: `last_seen_visibility`, `online_visibility`, `avatar_visibility`, `about_visibility`, `read_receipts_enabled`, `groups_add_policy`

**Fix:** Implement `<ProfilePrivacyFragment>` + API call.
**Status:** MISSING (backend ready)

---

### P10: Media Delete (NOT IMPLEMENTED)
Backend: `DELETE /v1/media/{media_id}`

Not called anywhere in any repository.

**Fix:** Add `deleteMedia(mediaId)` to `MediaService`.
**Status:** MISSING (backend ready)

---

## 🟠 PARTIAL — Channels Incomplete

### C1: Edit Post
**Backend:** `POST /v1/channels/{id}/posts/{post_id}` (auth required, author or admin)
Body: `text_content`

**Status:** MISSING

---

### C2: Pin Post
**Backend:** `PUT /v1/channels/{id}/posts/{post_id}/pin` (auth, admin only)

**Status:** MISSING

---

### C3: Delete Post
**Backend:** `DELETE /v1/channels/{id}/posts/{post_id}` (auth, admin or author)

**Status:** MISSING

---

### C4: Subscribe/Unsubscribe
**Backend:**
- `POST /v1/channels/{id}/subscribe` — body: `{"invite_token":"..."}` for private channels
- `DELETE /v1/channels/{id}/subscribe`

Max 500 per user. Returns `{"subscribed":true,"subscription_id":"uuid"}`

**Status:** MISSING

---

### C5: Generate Invite
**Backend:** `POST /v1/channels/{id}/invite` (owner/admin only)
Returns: `{"invite_url":"...","expires_ts":"ISO8601"}`

**Status:** MISSING

---

### C6: Admin Management
**Backend:**
- `PUT /v1/channels/{id}/admins/{user_id}` — add admin (owner only)
- `DELETE /v1/channels/{id}/admins/{user_id}` — remove admin

Returns: `{"admin_added":true,"user_id":"uuid"}`

**Status:** MISSING

---

## 🟠 PARTIAL — Status/Stickers/Polls Incomplete

### S1: Get Single Status
**Backend:** `GET /v1/status/{status_id}`

Not called anywhere.

**Status:** MISSING

---

### S2: Get My Status
Current implementation filters `loadMyStatus()` by checking `userId == "me"` from feed.
Backend has no `GET /v1/status/me` — should use `GET /v1/status/feed` and filter or track `my_status_id`.

**Fix:** Add `StatusRepository.getMyStatus()` using stored `status_id`.
**Status:** MISSING

---

### S3: Stickers Repository — NOT WIRED
`StickerViewModel.kt` exists but no repository found in `feature/stickers/data/`.
Backend endpoints:
- `GET /v1/stickers/packs/featured` → `packs[]`
- `GET /v1/stickers/packs/search?q=...&page=0&limit=20` → `packs[], page, has_more`
- `GET /v1/stickers/packs/{pack_id}` → full pack with `stickers[]`
- `POST /v1/stickers/packs` → create pack
- `POST /v1/stickers/library/{pack_id}` → install
- `DELETE /v1/stickers/library/{pack_id}` → uninstall
- `GET /v1/stickers/library?page=0&limit=20`
- `GET /v1/stickers/recent`
- `POST /v1/stickers/recent/{sticker_id}` → record usage

**Status:** MISSING — all of it

---

### S4: Polls Repository — NOT WIRED
`PollViewModel.kt` exists but no repository found in `feature/polls/data/`.
Backend endpoints:
- `POST /v1/polls` → create
- `POST /v1/polls/{id}/vote` → body: `{"option_ids":["1"]}`
- `GET /v1/polls/{id}`
- `GET /v1/polls/{id}/voters/{option_id}`
- `PUT /v1/polls/{id}/close`
- `DELETE /v1/polls/{id}`

**Status:** MISSING — all of it

---

## 🟠 PARTIAL — Calls Incomplete

### W1: Group Calls Stubbed Out
`peekGroupCall()` in `CallManager.kt` returns `null` — no SFU integration.
No sender key distribution for group messaging.

**What exists:**
- `CallLinkManager` — no backend endpoints visible
- `raiseHand()` — no backend endpoint
- Group call hand raising not implemented

**Backend:** TURN credentials via `GET /v1/calls/turn-credentials` — works ✅

**Status:** STUBBED — needs full group call architecture

---

## 🔵 SECURITY ISSUES

### SEC1: OPK Overwrite (HIGH — see K1 above)
---

### SEC2: Media Encryption Key in Payload (MEDIUM)
`sendMediaMessage` (MessageSendPipeline.kt:213-215) encrypts with random key and sends key in plaintext payload. Signal encrypts attachment key WITH the message key (层层加密).

**Fix:** Encrypt attachment key using recipient's session chain key, not in plaintext.
**Status:** NOT FIXED

---

### SEC3: Phone Hash Uses Raw SHA-256 (LOW)
Phone hash matching (`POST /v1/contacts/match`) should use Argon2id for privacy-preserving hash, not raw SHA-256. Backend accepts any hash — client decides format.

**Fix:** Use `enchant_argon2id_hash()` for phone number hashing.
**Status:** NOT FIXED

---

### SEC4: No Certificate Pinning (LOW)
Network layer (`ApiClient.kt`) uses standard OkHttp without pinning.

**Fix:** Add `CertificatePinner` for known backend host.
**Status:** NOT FIXED

---

### SEC5: loadMyProfile Uses "me" Path (LOW)
`ProfileViewModel.kt:75` uses `GET /v1/profile/me`. Backend doesn't have this endpoint. 
Should use the stored `userId` after OTP verify.

**Fix:** Replace `"me"` with stored `userId` from `AuthState`.
**Status:** NOT FIXED

---

## ✅ COMPLETE — Working Fine

| Feature | Notes |
|---------|-------|
| Crypto (libenchantcrypto) | Full Signal protocol, X25519/Ed25519/XChaCha20-Poly1305/HKDF/DoubleRatchet/X3DH |
| Authentication | OTP + JWT + refresh, all endpoints correct |
| Key Registration (basic) | Register/fetch bundle/rotate SPK/upload OPKs — all correct |
| Network/API Layer | OkHttp + retry + rate limit handling, correct endpoints |
| WebSocket | Real-time with protobuf, ping/pong, reconnect |
| Contacts/Blocking | Full CRUD + friend requests + phone hash matching |
| Groups | Full except missing group settings |
| Profile | Get/update/upload avatar |
| 1:1 Calls/WebRTC | Full with TURN creds |
| Backup | Full initiate/chunk/finalize/download/delete |
| SQLCipher Database | FTS5 search, full schema, migrations |
| Encrypted Storage | AES-256-GCM via EncryptedSharedPreferences + Keystore |
| Reactions (basic) | Send/delete/list — except missing conversation_id |

---

## 🔧 Priority Order for Fixes

### Tier 1 — Must Fix (breaks functionality)
1. **K1: OPK overwrite** — Messages become undeliverable
2. **K2: sendReaction missing conversation_id** — Reactions fail on backend validation

### Tier 2 — Missing Core Features (Signal parity)
3. **P3: Reply preview** — Reply UI needs this
4. **P4: Location sharing** — Core WhatsApp feature
5. **P5: Contact sharing** — Core WhatsApp feature
6. **P2: Message search** — FTS5 in DB but no API call
7. **P6: Message translation**
8. **P1: Notification preferences sync**

### Tier 3 — Incomplete Features
9. **P7: Note playback** (one-time)
10. **P8: Group settings** (disappearing msg)
11. **P9: Profile privacy update**
12. **P10: Media delete**
13. **C4: Channel subscribe/unsubscribe**
14. **C2/C3/C1: Post pin/edit/delete**

### Tier 4 — Nice to Have
15. **C5: Channel invite**
16. **C6: Channel admin management**
17. **S3: Stickers wiring**
18. **S4: Polls wiring**
19. **S1/S2: Status fixes**
20. **W1: Group calls**
