# Enchant Native — Current Progress

**Last Updated:** 2026-05-29
**Status:** Phase 8 — Android Frontend | Backend ready, frontend has critical gaps
**Purpose:** Track implementation state vs Signal/WhatsApp parity

---

## 🔴 CRITICAL — Breaks Functionality

| Issue | Location | Severity | Status |
|------|---------|----------|--------|
| OPK overwrite bug — `topUpOpks()` overwrites all OPKs at index 0 | KeyManager.kt:296-315 | HIGH | NOT FIXED |
| `sendReaction` missing `conversation_id` | MessageSendPipeline.kt:255 | MEDIUM | NOT FIXED |

---

## ✅ COMPLETE

| Feature | Status | Notes |
|--------|--------|-------|
| Crypto (libenchantcrypto) | ✅ | X25519/Ed25519/XChaCha20-Poly1305/HKDF/DoubleRatchet/X3DH |
| Authentication | ✅ | OTP + JWT + refresh, all endpoints correct |
| Key Registration (basic) | ✅ | Register/fetch bundle/rotate SPK/upload OPKs |
| Network/API Layer | ✅ | OkHttp + retry + rate limit handling |
| WebSocket | ✅ | Real-time protobuf, ping/pong, auto-reconnect |
| Contacts/Blocking | ✅ | Full CRUD + friend requests + phone hash matching |
| Groups | ✅ | Full except group settings (disappearing/messaging mode) |
| Profile | ✅ | Get/update/upload avatar |
| 1:1 Calls/WebRTC | ✅ | Full with TURN creds |
| Backup | ✅ | Full initiate/chunk/finalize/download/delete |
| SQLCipher + FTS5 | ✅ | 14 tables, full schema, migrations |
| Encrypted Storage | ✅ | AES-256-GCM + Keystore |

---

## 🟡 PARTIAL — Core Features Missing

| Feature | Backend Endpoint | Status |
|---------|-----------------|--------|
| Reply preview | `GET /v1/messages/{id}/reply` | MISSING |
| Location sharing | `POST /v1/location` + `GET /v1/location/{id}` | MISSING |
| Contact sharing | `POST /v1/contacts/share` | MISSING |
| Message translation | `POST /v1/messages/{id}/translate` | MISSING |
| Message search | `GET /v1/search/messages` | MISSING (FTS5 in DB) |
| Notification preferences sync | `GET/PUT /v1/notifications/preferences` | MISSING |
| Group settings (disappearing) | `PUT /v1/groups/{id}/settings` | MISSING |
| Profile privacy update | `PUT /v1/profile/privacy` | MISSING |
| One-time note playback | `GET /v1/notes/{id}/play` | MISSING |
| Media delete | `DELETE /v1/media/{id}` | MISSING |

---

## 🟠 PARTIAL — Channels Incomplete

| Feature | Status |
|---------|--------|
| Create/search/post | ✅ |
| Edit post | MISSING |
| Pin post | MISSING |
| Delete post | MISSING |
| Subscribe/unsubscribe | MISSING |
| Generate invite | MISSING |
| Admin management | MISSING |

---

## 🟠 PARTIAL — Groups Incomplete

| Feature | Status |
|---------|--------|
| All core features | ✅ |
| Group settings (disappearing messages, messaging mode) | MISSING |

---

## 🟠 PARTIAL — Other Features

| Feature | Status |
|---------|--------|
| Status/Stories feed + view + delete | ✅ |
| Get single status | MISSING |
| Get my status | MISSING (filters by "me" wrong) |
| Stickers repository | MISSING (ViewModel exists, no data/) |
| Polls repository | MISSING (ViewModel exists, no data/) |
| Group calls | STUBBED (peekGroupCall returns null) |
| Call links | STUBBED (manager exists, no backend) |

---

## 🔵 SECURITY ISSUES

| Issue | Severity | Status |
|-------|----------|--------|
| OPK overwrite (silent key loss) | HIGH | NOT FIXED |
| Media key in plaintext payload | MEDIUM | NOT FIXED |
| Phone hash uses raw SHA-256 | LOW | NOT FIXED |
| No certificate pinning | LOW | NOT FIXED |
| `loadMyProfile` uses "me" path (no such endpoint) | LOW | NOT FIXED |

---

## 📋 Full TODO List

See `TODO.md` for detailed breakdown with code snippets, fix plans, and priority ordering.

---

## 🔧 Priority Order

### Tier 1 — Must Fix (breaks functionality)
1. OPK overwrite bug (KeyManager.kt)
2. sendReaction missing conversation_id (MessageSendPipeline.kt)

### Tier 2 — Missing Core Features (Signal parity)
3. Reply preview
4. Location sharing
5. Contact sharing
6. Message search
7. Message translation
8. Notification preferences sync

### Tier 3 — Incomplete Features
9. One-time note playback
10. Group settings
11. Profile privacy update
12. Media delete
13. Channel subscribe/unsubscribe
14. Post pin/edit/delete

### Tier 4 — Nice to Have
15. Channel invite + admin management
16. Stickers wiring (featured/search/install/library/recent)
17. Polls wiring (create/vote/close)
18. Status single fetch + my status
19. Group calls (SFU + sender keys)
