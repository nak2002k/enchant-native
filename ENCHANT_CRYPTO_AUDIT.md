# LibEnchantCrypto Module-Wise Audit

**Scope:** Every module, every function, every implementation. Bugs, feature gaps, stubs, security weaknesses.

---

## 1. Primitives (`src/primitives/`)

### Bugs

| File | Bug | Severity |
|------|-----|----------|
| `aes.cpp:583-598` | `aes_256_cbc_decrypt` — PKCS7 padding validation is not constant-time. The XOR check loop and `pad_valid` accumulation leak timing information. Classic padding oracle. | CRITICAL |
| `aes.cpp:217` | `aes_256_gcm_decrypt` — on error, calls `memset(plaintext, 0, ciphertext_body_len)`. `ciphertext_body_len` is derived from attacker-controlled `ciphertext_len`. If caller passes inconsistent values, this zeroes more memory than the plaintext buffer actually holds. | HIGH |
| `aes.cpp:422-423` | `aes_256_ctr_encrypt` — copies only 12 bytes (`AES_GCM_NONCE_SIZE`) into a 16-byte IV, leaving last 4 bytes zeroed. CTR mode expects 16-byte IV. Undocumented behavior. | MEDIUM |
| `aes_gcm_siv.cpp:125-189` | **Key derivation is non-standard.** Derives `auth_key` and `enc_key` by splitting AES-ECB outputs into 8-byte halves. RFC 8452 Section A.2 specifies successive ECB blocks with counter blocks, producing full 16-byte keys. This implementation produces only 8 bytes per split. Ciphertexts will be incompatible with other GCM-SIV implementations. | CRITICAL |
| `aes_gcm_siv.cpp:285` | `nonce_cleared` not zeroed on early error return at line 285. | LOW |
| `chacha20_poly1305_ietf.cpp:8-32` | `chacha20_poly1305_ietf_encrypt` — no ciphertext capacity check. Caller must pre-allocate `plaintext_len + CHACHA20_POLY1305_IETF_TAG_SIZE` bytes but this is never validated. Buffer overflow possible. | HIGH |
| `xchacha20.cpp:42-60` | `xchacha20_encrypt_ad` — same issue. No output capacity check. Buffer overflow possible. | HIGH |
| `xchacha20.cpp:59` | `*ciphertext_len = out_len` is written even when encryption fails (`rc != 0`). Caller gets a non-zero length for failed encryption. | LOW |
| `hmac.cpp:25-36` | `hmac_sha512` — state is not zeroed on error paths. If `crypto_auth_hmacsha512_update` (line 29) or `crypto_auth_hmacsha512_final` (line 32) fails, the function returns without calling `sodium_memzero(&state, sizeof(state))`. Key material leaked in memory. | MEDIUM |
| `hkdf.cpp:131-156` | `hkdf_expand_label` — hardcodes HPKE v1 context bytes (lines 134-148). The function name is misleading — it will produce wrong results if used for TLS 1.3 `hkdf_expand_label` which uses different context. | MEDIUM |
| `constant_time.cpp:23` | `constant_time_is_zero` — returns `true` when `data` is null. Could cause null-pointer dereference issues to be silently ignored as "zero". | LOW |

### Stubs / Unimplemented

| Function | File | Status |
|----------|------|--------|
| `ed25519_public_key_from_x25519` | `ed25519.cpp:190-199` | Returns `ENCHANT_ERROR_NOT_IMPLEMENTED`. Correctly left unimplemented — no standard conversion from X25519 public key to Ed25519 public key exists. |

### Feature Gaps (vs libsignal)

| Gap | Notes |
|-----|-------|
| No streaming/incremental AES-GCM | libsignal has `Aes256GcmEncryption`/`Decryption` with `update()` calls. LibEnchantCrypto is one-shot only — must buffer entire plaintext. |
| No streaming HPKE | Same issue — one-shot only. |
| No AES-CBC streaming | libsignal has stream adapter. LibEnchantCrypto is batch only. |
| No formally verified ML-KEM backend | libsignal uses `libcrux_ml_kem` (formally verified). LibEnchantCrypto uses OpenSSL/libsodium. |
| No constant-time equality for ZK types | libsignal has `ConstantTimeEq` for credential key pairs, public keys, ciphertexts. LibEnchantCrypto has none of these. |
| AES-SIV limited to 2 AD vectors | `ad_ptrs[2]` at `aes_siv.cpp:128` — only AD + nonce supported. RFC 5297 processes all AD vectors. |

---

## 2. Protocol (`src/protocol/`)

### Bugs

| File:Line | Bug | Severity |
|-----------|-----|----------|
| `session_builder.cpp:126-128` | `set_trust()` calls `identity_store_.set_trust(address, trusted)` but `IIdentityKeyStore` (in `i_identity_store.hpp`) does not declare a `set_trust()` method. **Likely compile error.** | HIGH |
| `multi_recipient.cpp:243-256` | `MultiRecipientDecoder` stores session by **copy** (`session_ = session;`). Decrypt works on a stale copy — ratchet advancement is lost. Original session state never updated. | HIGH |
| `device_transfer_rsa.cpp:675-676` | `read_u32()` and `read_u64()` advance `offset` without bounds check. If `input_len < 8`, reads out of bounds on the initial reads for version and device_id. | HIGH |
| `session_manager.cpp:124-128` | Archives session after **every** successful decrypt from current session. After decrypting one message, current session is archived and next message triggers session promotion. Causes excessive session rotation and state bloat. | MEDIUM |
| `spqr_state.cpp:255-286` | `consume_receive_chain` — when a skipped key is found, sets `recv_chain_.chain_index = target_index + 1`. This is wrong for skipped keys — chain index should remain unchanged because earlier skipped keys may still be needed. | MEDIUM |
| `recipient_errors.cpp:68-83` | `summarize()` uses fixed 4096-byte `buf` with `snprintf`. If truncated, `std::string(buf)` includes null bytes after truncation. Should use `std::string(buf, pos)`. | LOW |
| `mismatched_devices.cpp:14-40` | Same issue — fixed 1024-byte `char buf[1024]` with `snprintf`. Final `message_ = buf` includes null bytes if truncated. | LOW |
| `stale_devices.cpp:13-27` | Same issue as above. | LOW |

### Stubs / Unimplemented

None — all declared functions have implementations.

### Feature Gaps (vs libsignal)

| Gap | Notes |
|-----|-------|
| No identity trust checking during decrypt | libsignal checks `IdentityKeyStore::is_trusted_identity()` before processing. LibEnchantCrypto does not. |
| No async protocol operations | libsignal is fully async/await. LibEnchantCrypto is synchronous. |
| No persistent storage abstraction | libsignal has `SessionStore`, `PreKeyStore`, `IdentityStore` traits. LibEnchantCrypto has `InMemory*` stores but no trait-based persistence. |
| No cross-version testing | libsignal has `cross-version-testing/` crate. LibEnchantCrypto has none. |
| No fuzzing targets | libsignal has `fuzz/fuzz_targets/`. LibEnchantCrypto has none. |
| RSA OAEP label ignored | `device_transfer_rsa.cpp:274-380` — accepts `label` and `label_len` but passes `nullptr, 0` to OpenSSL. Silent failure. |
| X509 serial number always 1 | `device_transfer_rsa.cpp:95` — all self-signed certs get serial number 1. Two certs from same device indistinguishable. |
| 2048-bit RSA accepted | `device_transfer_rsa.cpp:33` — NIST recommends transitioning to 3072+ bits by 2030. |

---

## 3. Groups (`src/groups/`)

### Bugs

| File:Line | Bug | Severity |
|-----------|-----|----------|
| `mls_state_machine.cpp:157-176` | `compute_epoch_secret` uses `state.epoch_secret` as HKDF input instead of the tree-derived secret. `commit` parameter is unused (`(void)commit`). The epoch secret derivation is fundamentally wrong — group security compromised. | CRITICAL |
| `mls_state_machine.cpp:260-288` | `apply_commit` calls `compute_transcript_hash` with `secrets_out` which is **uninitialized** at that point. `secrets_out` is only filled after `derive_epoch_secrets`. Reads garbage values from `secrets_out.epoch_secret`. | CRITICAL |
| `mls_state_machine.cpp:215` | `derive_epoch_secrets` — `transcript_hash` parameter is unused. | MEDIUM |
| `mls_state_machine.cpp:948-975` | `verify_external_commit` returns `ENCHANT_SUCCESS` with `valid_out=false` on all failure paths. Callers cannot distinguish "invalid commit" from "internal error". | MEDIUM |
| `groups_v2.cpp:164-168` | `apply_commit` only copies `epoch_secret` and increments epoch. Does **not** apply tree changes from the commit. Proposals are ignored. Remote commits don't update group state. | HIGH |

### Stubs / Unimplemented

| File:Line | Function | Status |
|-----------|----------|--------|
| `mls_tree_kem.hpp:138` | `MlsTreeKEM::compute_subtree_hash` | Declared but **never defined** in the .cpp file. Linker error if called. |
| `sender_key.hpp:102-103` | `DistributionMessage::serialize` and `DistributionMessage::deserialize` | Declared but **never defined**. Linker error if called. |

### Feature Gaps (vs libsignal)

| Gap | Notes |
|-----|-------|
| No async group operations | libsignal uses async with store traits. LibEnchantCrypto is sync with in-memory state. |
| No protobuf-backed sender key state | libsignal uses `SenderKeyStateStructure` protobuf. LibEnchantCrypto uses custom structs. |
| MLS TreeKEM incomplete | Missing `compute_subtree_hash`. `leaf_to_node` hardcodes tree width to 256 via `1u << 8`. `level_offset` and `root_index` ignore `leaf_count` parameter. |
| No admin approval implementation | `admin.hpp` is header-only with no backing implementation file. |

---

## 4. Veil / Sealed Sender (`src/veil/`)

### Bugs

| File:Line | Bug | Severity |
|-----------|-----|----------|
| `veil_v2.cpp:553-584` | `validate_server_certificate()` does **NOT** verify the certificate signature. It only checks key format. Validates structure but not authenticity. | HIGH |
| `veil_v2.cpp:252` | `sealed_sender_v2_decrypt_to_usmc` — `min_possible` calculation uses hardcoded `4 + 32 + 32 + 16` = 84 bytes per entry but doesn't account for variable recipient count. Crafted messages with `num_recipients=0` could pass initial check (caught later at line 268). | MEDIUM |
| `envelope_state.cpp:602` | `*plaintext_len = aead_ciphertext_len - ENVELOPE_AEAD_TAG_SIZE` is set **before** decryption succeeds. If decryption fails at line 606, `*plaintext_len` is already modified. Could leak expected plaintext length. | LOW |

### Stubs / Unimplemented

None — all functions fully implemented.

### Feature Gaps (vs libsignal)

| Gap | Notes |
|-----|-------|
| No certificate expiration checking in decrypt path | libsignal checks `validation_time > self.expiration`. LibEnchantCrypto does not check expiration during sealed sender decrypt. |
| No `KNOWN_SERVER_CERTIFICATES` trust store | libsignal has hardcoded trust roots. LibEnchantCrypto has dynamic revocation but no known-good certificate store. |
| No constant-time tag comparison in V1/V2 | libsignal uses `subtle::ConstantTimeEq`. LibEnchantCrypto uses `sodium_memcmp` (constant-time) — equivalent but no explicit constant-time for all comparisons. |
| Envelope state complexity | `envelope_state.cpp` is ~900 lines with complex chain management. `ensure_receiver_chain()` only returns existing chain or nullptr — never creates new one. Name is misleading. |

---

## 5. ZK Proofs (`src/zk/`)

### Bugs

| File:Line | Bug | Severity |
|-----------|-----|----------|
| `endorsement_3hashsdhi.hpp:251-267` | `verify_batch_proof` — computes `expected` hash but **never compares it** to the actual hash. Always returns `true`. Batch proof verification is a no-op. Accepts any proof. | CRITICAL |

### Stubs / Unimplemented

None — all functions have implementations.

### Feature Gaps (vs libsignal)

| Gap | Notes |
|-----|-------|
| No constant-time equality for credential types | libsignal implements `ConstantTimeEq` for `CredentialKeyPair`, `PublicKey`, `Ciphertext`. LibEnchantCrypto has none. |
| No `inverse_of()` for attribute keys | libsignal has `KeyPair::inverse_of()` for key inversion. LibEnchantCrypto does not. |
| No `encrypt_arbitrary_attribute` | libsignal supports cross-domain attribute encryption. LibEnchantCrypto does not. |
| Fixed 7 attributes | `credential_core` takes `std::array<RistrettoPoint, 7>` (fixed). libsignal takes `&[RistrettoPoint]` (variable-length). |
| `get_I` potential off-by-one | `credentials.hpp` uses `num_attrs - 1` (1-indexed). libsignal uses `num_attrs - 2` (zero-indexed). May produce different identity points. |
| No basepoint check on decrypt | libsignal checks `E_A1 == RISTRETTO_BASEPOINT_POINT` before decryption. LibEnchantCrypto does not. |
| Presentation proof returns `bool` | libsignal returns `Result<(), VerificationFailure>`. LibEnchantCrypto loses error specificity. |

---

## 6. SVR (`src/svr/`)

### Bugs

| File:Line | Bug | Severity |
|-----------|-----|----------|
| `svr_manager.cpp:174-230` | `restore_backup()` never decrements `attempts_remaining`. Field exists but is never modified. **Brute force protection is completely non-functional.** Unlimited PIN attempts allowed. | CRITICAL |
| `server_protocol.cpp:554-610` | `handle_backup_request()` receives `secret_share` but **never stores it anywhere**. Backup requests silently discard the share. Data loss. | CRITICAL |
| `server_protocol.cpp:210` | `verify_client_attestation()` sets `initialized_ = true` as side effect. Verification should not initialize the handler. | MEDIUM |
| `server_protocol.cpp:132-134` | `set_expected_fmspc()` stores the value but `expected_fmspc_` is **never checked** during `verify_client_attestation()`. Dead configuration. | MEDIUM |
| `svr_manager.cpp:63,101` | `reinterpret_cast<std::array<uint8_t,32>*>(pin_key)` on raw array — undefined behavior per the standard (alignment may differ). | LOW |

### Stubs / Unimplemented

None.

### Feature Gaps (vs libsignal)

| Gap | Notes |
|-----|-------|
| No forward secrecy token cleanup | `SvrForwardSecrecyManager::cleanup_expired_tokens()` exists but `verify_token()` and `rotate_token()` are never called by `SvrServerProtocol`. |
| Rate limiter is in-memory only | No persistence across restarts. |
| `make_client_key`/`make_session_key` treat binary client_id as string | Could cause issues with null bytes in client IDs. |
| Master key not zeroed on destruction | `master_key_` is `std::array`, not `SecureBuffer`. Destructor doesn't call `sodium_memzero`. |

---

## 7. Backup (`src/backup/`)

### Bugs

| File:Line | Bug | Severity |
|-----------|-----|----------|
| `backup_encryptor.cpp:346-372` | `compute_frame_mac()` — `data`/`data_len` parameters are accepted but **never used**. MAC is derived purely from `master_key`, `frame_number`, and `frame_type`. The MAC does **not authenticate the frame content**. | HIGH |
| `backup_encryptor.cpp` destructor | `master_key_` is `std::array`, not `SecureBuffer`. Not zeroed on destruction. | MEDIUM |
| `backup_entities.cpp:629-658` | `serialize_entity()` and `deserialize_entity()` only handle 4 of 13 entity types (MESSAGE, CONTACT, GROUP, ATTACHMENT). Other 9 types return `ENCHANT_ERROR_INVALID_FORMAT`. | MEDIUM |

### Stubs / Unimplemented

None.

### Feature Gaps (vs libsignal)

| Gap | Notes |
|-----|-------|
| No forward secrecy token | libsignal has `BackupForwardSecrecyToken` nonce for HKDF salt. LibEnchantCrypto uses zero salt. |
| No streaming backup decryption | libsignal has stream-based CBC decryption adapter. LibEnchantCrypto is batch only. |
| No separate HMAC/AES key derivation | libsignal derives separate `hmac_key` and `aes_key`. LibEnchantCrypto derives a single master key. |
| Incomplete entity serialization | 9 of 13 entity types not serializable. |
| No version field in serialized format | Forward compatibility difficult. |
| Master key not zeroed on destruction | Same as SVR issue. |

---

## 8. Key Transparency (`src/keytrans/`)

### Bugs

None significant.

### Dead Code

| File:Line | Function | Notes |
|-----------|----------|-------|
| `key_transparency.cpp` | `next_power_of_two` | Defined but never called internally. |

### Feature Gaps (vs libsignal)

| Gap | Notes |
|-----|-------|
| VRF uses Ristretto | libsignal uses Ed25519-based VRF. Different curve, different proof format. |
| No implicit key operations | libsignal has `keytrans/implicit.rs`. LibEnchantCrypto does not. |

---

## 9. Post-Quantum (`src/pq/`)

### Bugs

None significant.

### Feature Gaps (vs libsignal)

| Gap | Notes |
|-----|-------|
| No formally verified backend | libsignal uses `libcrux_ml_kem` which is formally verified. LibEnchantCrypto uses OpenSSL/libsodium. |
| No constant-time key comparison | libsignal uses `subtle::ConstantTimeEq` for ML-KEM public key comparison. LibEnchantCrypto has none. |
| No generic KeyPair abstraction | libsignal has `Key<Public>`/`Key<Secret>` with `KeyType` enum. LibEnchantCrypto has separate functions per size. |
| Conditional compilation fallback | `ENCHANT_HAVE_LIBSODIUM_MLKEM768` / `ENCHANT_ENABLE_PQ` — if unavailable, returns `ENCHANT_ERROR_NOT_IMPLEMENTED`. libsignal always has the backend. |

---

## 10. Media (`src/media/`)

### Bugs

None significant.

### Feature Gaps (vs libsignal)

| Gap | Notes |
|-----|-------|
| No async sanitization | libsignal uses `futures::Stream` adapters. LibEnchantCrypto is synchronous. |
| No trait extensibility | libsignal has `AsyncSkip`, `InputSpan`, `Skip` traits. LibEnchantCrypto is monolithic. |
| More format detection | LibEnchantCrypto detects MP4, WebP, JPEG, PNG, GIF. libsignal only MP4 and WebP. **LibEnchantCrypto advantage.** |

---

## 11. Usernames (`src/username/`)

### Bugs

None significant.

### Feature Gaps (vs libsignal)

| Gap | Notes |
|-----|-------|
| No ZK proof of ownership | libsignal uses `poksho`-based ZK proofs. LibEnchantCrypto uses SHA-256 hash. |
| No discriminator validation | libsignal validates format (no leading zeros, no single digit, no zero). LibEnchantCrypto accepts anything. |
| No character validation | libsignal restricts to `[_0-9a-z]`. LibEnchantCrypto accepts any bytes. |
| No case normalization | libsignal lowercases nicknames for hashing. LibEnchantCrypto preserves case. |
| No candidate generation | libsignal generates random discriminators from predefined ranges. LibEnchantCrypto does not. |
| Has storage layer | LibEnchantCrypto has full `IUsernameStore` interface with reserve/confirm/delete lifecycle. **LibEnchantCrypto advantage.** |
| Has link creation/resolution | `username_link.cpp/hpp`. Equivalent to libsignal. |

---

## 12. Device Transfer (`src/protocol/device_transfer*`)

### Bugs

| File:Line | Bug | Severity |
|-----------|-----|----------|
| `device_transfer_rsa.cpp:675-676` | `read_u32()` advances offset without bounds check on short input. | HIGH |
| `device_transfer_rsa.cpp:95` | All self-signed certs get serial number 1. | LOW |
| `device_transfer_rsa.cpp:274-380` | OAEP label parameter silently ignored. | MEDIUM |

### Feature Gaps (vs libsignal)

| Gap | Notes |
|-----|-------|
| No RSA key generation | libsignal generates RSA keypairs. LibEnchantCrypto uses symmetric key transfer. Different approach. |
| No DER/PKCS8 format | libsignal uses standard key formats. LibEnchantCrypto uses raw bytes. |
| 2048-bit RSA accepted | Approaching end-of-life. |

---

## 13. Attestation (`src/attest/`)

### Bugs

None significant.

### Feature Gaps (vs libsignal)

| Gap | Notes |
|-----|-------|
| No HSM enclave | libsignal has `attest/hsm_enclave.rs`. LibEnchantCrypto does not. |
| No SGX session | libsignal has `attest/sgx_session.rs`. LibEnchantCrypto does not. |
| No Snow Noise protocol | libsignal uses Snow for Noise protocol. LibEnchantCrypto does not. |
| Has TCB info | LibEnchantCrypto has `tcb_info.cpp`. libsignal does not. **LibEnchantCrypto advantage.** |

---

## 14. Account Keys (`src/account_keys/`)

### Bugs

None significant.

### Feature Gaps (vs libsignal)

| Gap | Notes |
|-----|-------|
| No password hashing | libsignal has Argon2-based password hashing. LibEnchantCrypto does not. |
| No entropy pool | LibEnchantCrypto has `account_entropy_pool.cpp`. libsignal does not. **LibEnchantCrypto advantage.** |

---

## 15. Crypto Utilities (`src/crypto/`)

### Bugs

| File:Line | Bug | Severity |
|-----------|-----|----------|
| `jwt.cpp:317-318` | JSON parser comma-skipping logic is fragile. After a numeric value, advances past comma, but same is done for non-numeric values — comma consumed twice on malformed input. Parser doesn't handle nested objects, arrays, or escaped quotes. | MEDIUM |
| `profile_cipher.cpp` | `salt` parameter in `encrypt_field`/`decrypt_field` is accepted but **never used** in key derivation. Key is derived from `profile_key` only using fixed string `"EnchPrfl"`. Salt provides no domain separation. | MEDIUM |

### Feature Gaps (vs libsignal)

| Gap | Notes |
|-----|-------|
| Custom JWT parser | Not production-safe for untrusted input. libsignal has no JWT (handled at app layer). |
| Profile cipher salt unused | Equivalent functionality exists in libsignal's `zkgroup/crypto/profile_key_encryption.rs` with proper domain separation. |

---

## 16. Other Modules

### `src/async/`
- Coroutine-based async with thread pool executor. No issues found. No equivalent in libsignal at this layer.

### `src/secure/`
- `SecureBuffer` using `sodium_malloc` for guarded memory. No issues found. Good practice.

### `src/proto/`
- Protobuf serializer, varint encoding. No issues found.

### `src/metrics/`
- Atomic counter-based metrics. No issues found.

### `src/agents/`
- Agent identity management with X3DH sessions. No issues found.

---

## Summary: All Issues by Severity

### CRITICAL (5)

1. **`aes_gcm_siv.cpp`** — Non-standard key derivation. Ciphertexts incompatible with RFC 8452.
2. **`mls_state_machine.cpp:157-176`** — `compute_epoch_secret` uses wrong input. Group security compromised.
3. **`mls_state_machine.cpp:260-288`** — `apply_commit` reads uninitialized `secrets_out`. Undefined behavior.
4. **`endorsement_3hashsdhi.hpp:251-267`** — Batch proof verification is a no-op. Accepts any proof.
5. **`svr_manager.cpp:174-230`** — Brute force protection broken. Unlimited PIN attempts.

### HIGH (7)

6. **`aes.cpp:583-598`** — CBC padding oracle (non-constant-time validation).
7. **`chacha20_poly1305_ietf.cpp:8-32`** — No ciphertext capacity check. Buffer overflow.
8. **`xchacha20.cpp:42-60`** — No ciphertext capacity check. Buffer overflow.
9. **`session_builder.cpp:126-128`** — `set_trust()` calls non-existent method. Likely compile error.
10. **`multi_recipient.cpp:243-256`** — Session copied by value. Ratchet state lost.
11. **`device_transfer_rsa.cpp:675-676`** — Out-of-bounds read on short input.
12. **`veil_v2.cpp:553-584`** — Server certificate signature not verified.
13. **`backup_encryptor.cpp:346-372`** — Frame MAC doesn't authenticate content.
14. **`groups_v2.cpp:164-168`** — `apply_commit` doesn't apply tree changes.
15. **`server_protocol.cpp:554-610`** — Backup silently discards secret share.

### MEDIUM (10)

16. **`hmac.cpp:25-36`** — HMAC-SHA512 state not zeroed on error.
17. **`session_manager.cpp:124-128`** — Archives session after every decrypt.
18. **`mls_state_machine.cpp:948-975`** — `verify_external_commit` returns success on failure.
19. **`spqr_state.cpp:255-286`** — Chain index incorrectly advanced on skipped keys.
20. **`server_protocol.cpp:210`** — `verify_client_attestation` sets `initialized_=true`.
21. **`server_protocol.cpp:132-134`** — `set_expected_fmspc` never checked.
22. **`sesame.cpp:83`** — Hardcoded test key ID `0xDEADC357` always returns revoked.
23. **`profile_cipher.cpp`** — Salt parameter ignored in key derivation.
24. **`jwt.cpp:317-318`** — Fragile JSON parser. Not safe for untrusted input.
25. **`device_transfer_rsa.cpp:274-380`** — OAEP label silently ignored.
26. **`backup_entities.cpp:629-658`** — Only 4 of 13 entity types serializable.

### LOW (8)

27. **`aes_gcm_siv.cpp:285`** — `nonce_cleared` not zeroed on early error.
28. **`aes.cpp:422-423`** — CTR IV padded to 16 bytes, undocumented.
29. **`xchacha20.cpp:59`** — `*ciphertext_len` written on failure.
30. **`constant_time.cpp:23`** — Returns true for null pointers.
31. **`recipient_errors.cpp:68-83`** — Truncated output includes null bytes.
32. **`mismatched_devices.cpp:14-40`** — Same truncation issue.
33. **`stale_devices.cpp:13-27`** — Same truncation issue.
34. **`svr_manager.cpp:63,101`** — `reinterpret_cast` on raw array (UB).

---

## Summary: All Stubs / Missing Implementations

| # | File | Function | Impact |
|---|------|----------|--------|
| 1 | `ed25519.cpp:190-199` | `ed25519_public_key_from_x25519` | Correctly unimplemented — no standard conversion exists |
| 2 | `mls_tree_kem.hpp:138` | `compute_subtree_hash` | Linker error if called |
| 3 | `sender_key.hpp:102-103` | `DistributionMessage::serialize/deserialize` | Linker error if called |
| 4 | `backup_entities.cpp` | 9 entity type serializers | Returns `ENCHANT_ERROR_INVALID_FORMAT` |

---

## Summary: Dead Code

| # | File | Function |
|---|------|----------|
| 1 | `key_transparency.cpp` | `next_power_of_two` |
| 2 | `svr_v4.cpp:56-61` | `SvrV4Manager::base_point_scalar()` |
| 3 | `triple_ratchet_outgoing.cpp:179-181` | `OutgoingTripleRatchet::advance_ratchet()` |
| 4 | `multi_recipient.cpp:236-241` | `MultiRecipientEncoder::finalize()` |
| 5 | `address.hpp` | Empty file — no declarations |

---

## Summary: Key Material Not Zeroed on Destruction

| # | File | Variable |
|---|------|----------|
| 1 | `svr_manager.cpp` | `master_key_` (`std::array`) |
| 2 | `backup_encryptor.cpp` | `master_key_` (`std::array`) |
