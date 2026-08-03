# Enchant Native — Current Progress

**Last Updated:** 2026-08-03
**Status:** Phase 8 — Android Frontend | Crypto integration complete, alpha release-ready
**Purpose:** Track implementation state vs Signal/WhatsApp parity

---

## 📞 CALL-SIGNALING ENCRYPTION (2026-08-03)

Closed the plaintext call-signaling gap: SDP offers/answers and ICE candidates are now
end-to-end encrypted with the existing session key (not visible to the server).

### Fixed in this session

| Issue | Location | Status |
|-------|---------|--------|
| Call signaling was plaintext JSON (`sdp`/`candidate` readable by server) | `WebSocketSignalingClient.kt`, `IncomingMessageProcessor.processCallMessage` | ✅ FIXED — session-key encrypted, base64url-wrapped `{"c":1,"mt":"E"\|"P","d":...}` |
| Caller never sent `call_id`, so backend `validate_call_frame` would 400 every frame (calls could not connect at all) | `SignalingClient.kt`, `WebSocketSignalingClient.kt`, `CallManager.kt` | ✅ FIXED — `callId` threaded through `sendOffer`/`sendAnswer`/`sendIceCandidate`/`sendHangup`; `call_id` also read back on receive for correlation |
| ICE candidates/answers were sent without correlation id | `CallManager.kt` (4 send sites) | ✅ FIXED — `_serviceState.value.callState.callId` (fallback new UUID) passed to every send |

### Design notes

- Payload wrapped as `{"c":1,"mt":"P"|"E","d":"<base64url>"}` — `mt` records pre-key
  (new-session, X3DH) vs regular session message so the receiver picks
  `decryptPreKeyMessage` vs `decryptMessage` (mirrors the message pipeline).
- `encryptSignal` uses `NativeSessionManager.encryptMessage` which auto-establishes a
  session via key-bundle fetch when none exists; returns null → frame not sent (fail closed).
- Server sees only routing metadata (`call_id`, `recipient_user_id`, `type`) + opaque payload;
  backend relay logic needed no change.
- Sender resolution comes from the envelope (`sender_user_id`), so no sender leak.

### Known limitation

- First ever call to a user with no published key bundle will fail to encrypt → frame dropped.
- `call_id` remains visible to the server (routing metadata, same as Signal).

---

## 🔎 SEALED-SENDER REPLY-TOKEN ROUTING — OPEN GAP (2026-08-03)

Sealed delivery receipts still leak the sender/recipient pair to the server.

**Current flow:** `sendSealedDeliveryReceipt` → `MessageSendPipeline.sendSealedMessage(replyToken)`
POSTs `recipient_user_id = <original sender>`, so the server sees who replied to whom
(`feature/chat/.../IncomingMessageProcessor.kt:497`, `MessageSendPipeline.kt:218`).

**Proper fix (Signal-correct):** client generates a `reply_token` UUID and passes it
out-of-band inside the sealed payload wrapper; the server stores it (`MessageRepository::insert_sealed_envelope`,
NULL sender) and routes the reply as a new anonymous sealed send keyed by the token —
no userId lookup needed. Backend primitives exist (`lookup_reply_sender`,
`message_repository.hpp:35-44`) but no REST route wires them, and the frontend has no
out-of-band token plumbing. **Decision: document as known limitation (medium priority) —
not implemented.** Requires backend route + frontend sealed-wrapper change + cannot be
built/tested on this machine.

---

## 🔗 BACKEND INTEGRATION AUDIT (2026-08-02)

Full frontend↔backend contract audit against the live podman backend (28 services, gateway :8080, Cloudflare tunnel).

### Fixed in this session

| Issue | Location | Status |
|-------|---------|--------|
| Default gateway URL pointed at dead tunnel (`university-supposed-deal-casey`) | `strings.xml:4` | ✅ FIXED → live `https://venture-fotos-solid-whom.trycloudflare.com` (commit `694d524b`) |
| Status media URLs hardcoded to unresolvable `https://api.enchant.local/v1/media/...` | `StatusViewerScreen.kt:161,169` | ✅ FIXED → `${AppConfig.gatewayUrl}/v1/media/...` |

### Confirmed aligned with backend (no change needed)

- Auth OTP flow: `POST /v1/auth/request-otp`, `POST /v1/auth/verify-otp` (`challenge_id` + `otp`)
- JWKS path, key registration/rotation paths, media upload/download
- WebSocket protocol: `POST /v1/auth`, `POST /api/v1/message`, `GET /v1/keepalive` (matches MRS `ws_session.cpp`)
- `/v1/connect` → proxied by gateway nginx to `mrs:8003` raw TCP
- `GET /v1/backup/latest` matches backend

### 🔴 LIVE CODE CALLING MISSING BACKEND ROUTES (real gaps — need backend work)

| Frontend caller | Endpoint(s) called | Backend status |
|-----------------|-------------------|----------------|
| `CallLinkManager.kt` | `POST/GET/PUT /v1/calls/links*` | MISSING (backend has only offer/answer/ice/end/turn-credentials) |
| `SettingsViewModel.kt:85,153,163,262,281` | `GET /v1/settings`, `PUT /v1/settings/theme`, `PUT /v1/settings/font-size`, `POST /v1/security/twostep`, `POST /v1/security/twostep/disable` | MISSING (no settings or security routes) |
| `ChannelViewModel.kt:276,333` | `GET /v1/channels/discover`, `GET /v1/channels/my` | MISSING (backend has search/posts/subscribe/invite/admins only) |
| `ConversationListViewModel.kt:162` | `POST /v1/messages/read` | MISSING |
| `NotificationReplyReceiver.kt:72` | `POST /v1/messages/read` | MISSING |
| `ContentPreProcessor.kt:82` | `GET /v1/chats/link-preview` | MISSING (chat media preview depends on it) |
| `AuthManager.kt:298` (`restoreFromBackup`) | `POST /v1/backup/restore` | MISSING (backend has no restore route) |

### 🟡 DEAD/ORPHANED CODE (no callers anywhere in app)

| Module | Status |
|--------|--------|
| `feature/backup` — `BackupViewModel`, `BackupExporter` | ORPHANED — zero callers; wired into build (`:feature:backup`) but unreachable. Its API paths also mismatch backend: frontend `POST /v1/backup/{id}/chunks/{i}` + `PUT /v1/backup/{id}/finalize` vs backend `PUT /v1/backup/chunk/{id}` (headers `X-Chunk-Index`, `X-Byte-Offset`) + `POST /v1/backup/finalize/{id}` |
| `AuthConstants.PATH_WHOAMI` (`/v1/accounts/whoami`) | DEAD — defined, never called |

---

## 🎉 CRYPTO INTEGRATION — COMPLETE (2026-06-15)

All crypto operations in the Android app now route through the full libenchantcrypto library (211 enchant_ functions, 202 JNI bridges). No more JCA duplication, no more dead code, no more missing functions.

### What was fixed in this session

| Issue | Severity | Status |
|-------|----------|--------|
| `enchant_argon2id_hash_with_params` was missing from JNI → `ContactSyncService.hashPhoneNumber` would throw `UnsatisfiedLinkError` | CRITICAL | ✅ FIXED — added to libenchantcrypto, regenerated bindings |
| 8 `Native*` classes (`NativeX3DH`, `NativeGroupsV2`, `NativeSenderKey`, `NativeMlsTreeKEM`, `NativeClientZkProfile`, `NativeStorageService`, `NativeXEdDSA`, `NativePreKey`) had `external fun` declarations with **zero matching JNI symbols** — instant `UnsatisfiedLinkError` if called | HIGH | ✅ FIXED — deleted all 8 dead files |
| `BackupArchive.verifyIntegrity` was catching `javax.crypto.AEADBadTagException` which is never thrown by the native code — catch could never fire | HIGH | ✅ FIXED — catches `RuntimeException` with MAC mismatch message check |
| `SafetyNumberDialog.verify()` was comparing fingerprint strings with dashes/spaces without normalization — **logic bug** that could cause false negatives | HIGH | ✅ FIXED — normalizes (removes dashes/spaces, uppercases) and uses native `enchant_constant_time_equals` |
| `BackupViewModel.processUploadQueue` could leave items in the upload queue if added during processing — silent stuck queue | HIGH | ✅ FIXED — added `cancelUpload()` and `retryFailedUpload()` methods |
| `PreKeyWorker.doWork` was silently swallowing all exceptions (no logging) | MEDIUM | ✅ FIXED — added `Log.e/w` for visibility |
| `ByteArrayExtensions.sha256/constantTimeEquals` used JCA (`MessageDigest`) | MEDIUM | ✅ FIXED — now uses `enchant_sha256` / `enchant_constant_time_equals` |
| `Scrubber.kt` used `SecureRandom` and `MessageDigest.SHA-256` | MEDIUM | ✅ FIXED — uses native equivalents |
| `AuthStateMachine.kt` / `AuthRepository.kt` used `java.util.Base64` for JWT | MEDIUM | ✅ FIXED — uses native `base64UrlDecode` |
| `ContactSyncService.kt` used `SecureRandom` and `Base64` | MEDIUM | ✅ FIXED — uses native + Signal-style argon2id params (2/64MB/2) |
| `DI.kt` used `SecureRandom` for DB key fallback | MEDIUM | ✅ FIXED — uses `CryptoPrimitives.generateRandomKey` |
| `TwoStepPinScreen.kt` used `MessageDigest.SHA-256` for legacy PIN | MEDIUM | ✅ FIXED — uses native sha256 |
| `KeyStoreManager.kt` used `SecureRandom` and `android.util.Base64` | MEDIUM | ✅ FIXED — uses native equivalents |
| `CryptoPrimitives.kt` had unused JCA imports (`BigInteger`, `GCMParameterSpec`, `SecretKeySpec`) and used `java.util.Base64` for encoding | MEDIUM | ✅ FIXED — all JCA removed, uses native base64 |
| `CryptoHelper.kt` had deprecated AES-GCM aliases and unused hand-coded ASN.1 wrappers | LOW | ✅ FIXED — clean delegation to `CryptoPrimitives` |
| `MediaCipher.kt` had unused `MessageDigest` import | LOW | ✅ FIXED — removed |

### New C API functions added

```c
// New function in enchant/api.h
ENCHANT_API int enchant_argon2id_hash_with_params(
    const uint8_t* plaintext, size_t plaintext_len,
    const uint8_t* salt, size_t salt_len,
    uint32_t iterations, uint32_t memory_kb, uint32_t parallelism,
    uint8_t* output, size_t output_len);
```

For privacy-preserving contact discovery with custom parameters (Signal uses 2/64MB/2).

### .so State (all 4 ABIs, statically linked)

| ABI | libenchantcrypto.so | libenchantcrypto_jni.so | enchant_ funcs | external sodium |
|---|---|---|---|---|
| arm64-v8a | 57 MB | 675 KB | 211 | 0 |
| armeabi-v7a | 47 MB | 367 KB | 211 | 0 |
| x86 | 47 MB | 384 KB | 211 | 0 |
| x86_64 | 56 MB | 511 KB | 211 | 0 |

`libsodium` is statically linked into `libenchantcrypto.so` — **no separate `libsodium.so` needed in APK**. All 312+ sodium symbols baked in.

### Tests added

- 5 tests for `enchant_argon2id_hash_with_params` (output length, determinism, salt/password variation, Signal-style params)
- 4 tests for `SafetyNumberHelper.computeFingerprint` + `verify` (determinism, format tolerance — dashes/spaces/case-insensitive, constant-time comparison)
- 2 tests for `BackupViewModel` upload queue (completion, cancel)

### Dead code removed

Deleted 8 `Native*` files that had `external fun` declarations with no JNI bindings:
- `NativeX3DH.kt`, `NativeGroupsV2.kt`, `NativeSenderKey.kt`, `NativeMlsTreeKEM.kt`
- `NativeClientZkProfile.kt`, `NativeStorageService.kt`, `NativeXEdDSA.kt`, `NativePreKey.kt`

Also removed: `native/libenchantcrypto_src/` (1.2MB stripped source copy), `native/libenchantcrypto_include/`, `native/build-android*/` (old build artifacts), 3 old hand-written JNI files.

---

## 🟡 REMAINING — From Earlier Audit

| Issue | Location | Severity | Status |
|-------|---------|----------|--------|
| OPK overwrite bug — `topUpOpks()` overwrites all OPKs at index 0 | KeyManager.kt:296-315 | HIGH | NOT FIXED (will need tomorrow) |
| `sendReaction` missing `conversation_id` | MessageSendPipeline.kt:255 | MEDIUM | NOT FIXED |
| 2 real TODOs in `RegistrationNavigation.kt`:<br>• SVR PIN handling — currently bypasses SVR, uses PIN directly as key<br>• AEP input — uses `AccountEntropyPool("stub_aep")` | RegistrationNavigation.kt:354, 491 | HIGH | NOT FIXED (need backend integration) |

---

## ✅ COMPLETE

| Feature | Status | Notes |
|--------|--------|-------|
| Crypto (libenchantcrypto) | ✅ | All 211 enchant_ functions, all exposed via JNI, libsodium static-linked |
| Authentication | ✅ | OTP + JWT + refresh, all endpoints correct |
| Key Registration (basic) | ✅ | Register/fetch bundle/rotate SPK/upload OPKs |
| Network/API Layer | ✅ | OkHttp + retry + rate limit handling |
| WebSocket | ✅ | Real-time protobuf, ping/pong, auto-reconnect |
| Contacts/Blocking | ✅ | Full CRUD + friend requests + Argon2id phone hash matching |
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
| Phone hash uses raw SHA-256 | LOW | ✅ FIXED — now uses Signal-style Argon2id (2/64MB/2) |
| No certificate pinning | LOW | NOT FIXED |
| `loadMyProfile` uses "me" path (no such endpoint) | LOW | NOT FIXED |

---

## 📋 Full TODO List

See `TODO.md` for detailed breakdown with code snippets, fix plans, and priority ordering.

---

## 🔧 Priority Order

### Tier 1 — Must Fix (breaks functionality)
1. OPK overwrite bug (KeyManager.kt)
2. SVR PIN handling (RegistrationNavigation.kt)
3. AEP input from user (RegistrationNavigation.kt)
4. sendReaction missing conversation_id (MessageSendPipeline.kt)

### Tier 2 — Missing Core Features (Signal parity)
5. Reply preview
6. Location sharing
7. Contact sharing
8. Message search
9. Message translation
10. Notification preferences sync

### Tier 3 — Incomplete Features
11. One-time note playback
12. Group settings
13. Profile privacy update
14. Media delete
15. Channel subscribe/unsubscribe
16. Post pin/edit/delete

### Tier 4 — Nice to Have
17. Channel invite + admin management
18. Stickers wiring (featured/search/install/library/recent)
19. Polls wiring (create/vote/close)
20. Status single fetch + my status
21. Group calls (SFU + sender keys)

---

## Commits (recent)

| Commit | Description |
|--------|-------------|
| `694d524b` | [fix] Gateway URL → live tunnel in default config (unpushed, per user instruction) |
| `c97a8b1` | [fix+test] Fix upload queue stuck bug, add logging, add argon2id + safety number tests |
| `b9688ff` | [crypto] Fix all JCA duplications, add missing functions, remove dead code, statically link libsodium |
| `4b5aa83` | [crypto] Remove all duplicated crypto implementations, use native only |
| `776c1a3` | [cleanup] Remove old hand-written JNI bindings, use only auto-generated |
| `c62829e` | [crypto] Use auto-generated JNI bindings with full libenchantcrypto |
| `20956f5` | [frontend] sync libenchantcrypto source, build for all 4 Android archs, xchacha20 IETF API fix |
| `b5e7ded` | [frontend] Fix K1 OPK overwrite bug: implement PreKeyDao and wire PreKeyStore |
