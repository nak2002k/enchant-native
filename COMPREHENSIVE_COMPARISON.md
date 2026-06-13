# LibEnchantCrypto vs libsignal: Comprehensive Code Analysis & Comparison

**Date:** 2026-06-13
**Scope:** Full codebase analysis of LibEnchantCrypto (C++) and comparison with libsignal (Rust reference)
**Files Analyzed:** 294 source/header files (LibEnchantCrypto) + 400+ .rs files (libsignal)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [LibEnchantCrypto Codebase Audit](#2-libenchantcrypto-codebase-audit)
   - 2.1 [Bugs Found](#21-bugs-found)
   - 2.2 [Stubs and Missing Implementations](#22-stubs-and-missing-implementations)
   - 2.3 [Unused Code](#23-unused-code)
   - 2.4 [Security Issues](#24-security-issues)
3. [Module-by-Module Comparison](#3-module-by-module-comparison)
   - 3.1 [Primitives (Crypto)](#31-primitives-crypto)
   - 3.2 [Protocol (Sessions, X3DH, Ratchet)](#32-protocol-sessions-x3dh-ratchet)
   - 3.3 [Groups (Group Cipher, Sender Keys, MLS)](#33-groups-group-cipher-sender-keys-mls)
   - 3.4 [Veil / Sealed Sender](#34-veil--sealed-sender)
   - 3.5 [ZK Proofs (zkcredential, zkgroup)](#35-zk-proofs-zkcredential-zkgroup)
   - 3.6 [SVR (Secure Value Recovery)](#36-svr-secure-value-recovery)
   - 3.7 [Backup](#37-backup)
   - 3.8 [Key Transparency](#38-key-transparency)
   - 3.9 [Post-Quantum (ML-KEM)](#39-post-quantum-ml-kem)
   - 3.10 [Media Sanitization](#310-media-sanitization)
   - 3.11 [Usernames](#311-usernames)
   - 3.12 [Device Transfer](#312-device-transfer)
   - 3.13 [Attestation](#313-attestation)
   - 3.14 [Account Keys](#314-account-keys)
   - 3.15 [Crypto Utilities (JWT, Profile Cipher, Certificate Validator)](#315-crypto-utilities-jwt-profile-cipher-certificate-validator)
4. [Architecture Comparison](#4-architecture-comparison)
5. [Wire Format Compatibility](#5-wire-format-compatibility)
6. [Security Posture Comparison](#6-security-posture-comparison)
7. [Feature Gap Analysis](#7-feature-gap-analysis)
8. [Recommendations](#8-recommendations)

---

## 1. Executive Summary

LibEnchantCrypto is a C++ implementation of the Signal Protocol ecosystem with extensions. It reimplements the core cryptographic primitives, protocol logic, zero-knowledge proofs, and application-level features from scratch using OpenSSL and libsodium, rather than wrapping libsignal directly.

**Key Findings:**

| Metric | LibEnchantCrypto | libsignal |
|--------|-----------------|-----------|
| Language | C++ | Rust |
| Crypto backend | OpenSSL + libsodium | Custom (curve25519-dalek, hpke-rs, libcrux-ml-kem) |
| Total source files | 294 | 400+ |
| Critical bugs found | 12 | 0 (reference) |
| Security issues | 18 | 0 (reference) |
| Stub functions | 2 | 0 |
| Wire format compatible | Partial | N/A (reference) |

**Critical Incompatibility:** LibEnchantCrypto uses different domain separation labels (e.g., `"enchant_ZKCredential_*"` vs `"Signal_ZKCredential_*"`), different AEAD choices (XChaCha20 vs AES-GCM-SIV), and different key derivation paths. **Proofs, keys, and ciphertexts produced by LibEnchantCrypto are NOT interoperable with libsignal.**

---

## 2. LibEnchantCrypto Codebase Audit

### 2.1 Bugs Found

#### CRITICAL Bugs

| # | File:Line | Bug | Impact |
|---|-----------|-----|--------|
| 1 | `aes_gcm_siv.cpp:125-189` | **Non-standard key derivation** — derives auth_key and enc_key by splitting AES-ECB outputs into 8-byte halves instead of using successive ECB blocks per RFC 8452 Section A.2 | Produces ciphertexts incompatible with all other GCM-SIV implementations (BoringSSL, Go, etc.) |
| 2 | `mls_state_machine.cpp:157-176` | `compute_epoch_secret` uses old `state.epoch_secret` as HKDF input instead of the tree-derived secret; `commit` parameter is unused | MLS epoch secret derivation is wrong — group security compromised |
| 3 | `endorsement_3hashsdhi.hpp:251-267` | `verify_batch_proof` always returns `true` — the computed expected hash is never compared to the actual hash | Batch proof verification is a no-op — accepts any proof |
| 4 | `svr_manager.cpp:174-230` | `restore_backup()` never decrements `attempts_remaining` — the field exists but is never modified | Brute force protection is non-functional — unlimited PIN attempts |
| 5 | `server_protocol.cpp:554-610` | `handle_backup_request()` receives `secret_share` but never stores it anywhere | Backup requests silently discard the share — data loss |

#### HIGH Bugs

| # | File:Line | Bug | Impact |
|---|-----------|-----|--------|
| 6 | `aes.cpp:583-598` | CBC padding validation is not constant-time — XOR check loop accumulates `pad_valid` with branching | Classic padding oracle vulnerability |
| 7 | `chacha20_poly1305_ietf.cpp:8-32` | No ciphertext capacity check — caller must pre-allocate `plaintext_len + TAG_SIZE` but this is never validated | Buffer overflow if caller provides undersized buffer |
| 8 | `xchacha20.cpp:42-60` | Same as above — `xchacha20_encrypt_ad` writes without capacity check | Buffer overflow |
| 9 | `mls_state_machine.cpp:260-288` | `apply_commit` reads uninitialized `secrets_out.epoch_secret` for transcript hash computation | Reads garbage values — undefined behavior |
| 10 | `device_transfer_rsa.cpp:675-676` | `read_u32()` advances offset without bounds check — if `input_len < 8`, reads out of bounds | Out-of-bounds read on malformed input |
| 11 | `multi_recipient.cpp:243-256` | `MultiRecipientDecoder` stores session by **copy** (not reference) — decrypt works on stale copy | Ratchet advancement is lost — session state not updated |
| 12 | `session_builder.cpp:126-128` | `set_trust()` calls `identity_store_.set_trust()` but `IIdentityKeyStore` interface doesn't declare this method | Likely compile error |

#### MEDIUM Bugs

| # | File:Line | Bug | Impact |
|---|-----------|-----|--------|
| 13 | `hmac.cpp:25-36` | HMAC-SHA512 state not zeroed on error paths (lines 29, 32) | Key material leaked in memory |
| 14 | `session_manager.cpp:124-128` | Archives session after **every** successful decrypt from current session | Excessive session rotation — performance and state bloat |
| 15 | `groups_v2.cpp:164-168` | `apply_commit` doesn't apply tree changes — only copies epoch_secret | Remote commits don't update group state |
| 16 | `mls_state_machine.cpp:948-975` | `verify_external_commit` returns `ENCHANT_SUCCESS` with `valid_out=false` on all failure paths | Callers can't distinguish "invalid commit" from "internal error" |
| 17 | `spqr_state.cpp:255-286` | `consume_receive_chain` advances chain index even when consuming skipped keys | Earlier skipped keys may be lost |
| 18 | `veil_v2.cpp:553-584` | `validate_server_certificate()` does NOT verify the certificate signature — only checks key format | Validates structure but not authenticity |

### 2.2 Stubs and Missing Implementations

| # | File:Line | Function | Status |
|---|-----------|----------|--------|
| 1 | `ed25519.cpp:190-199` | `ed25519_public_key_from_x25519` | Returns `ENCHANT_ERROR_NOT_IMPLEMENTED` — intentionally unimplemented (no standard conversion exists) |
| 2 | `mls_tree_kem.hpp:138` | `MlsTreeKEM::compute_subtree_hash` | Declared but never defined — linker error if called |
| 3 | `sender_key.hpp:102-103` | `DistributionMessage::serialize/deserialize` | Declared but never defined — linker error if called |
| 4 | `backup_entities.cpp:629-658` | `serialize_entity/deserialize_entity` | Only handles 4 of 13 entity types (MESSAGE, CONTACT, GROUP, ATTACHMENT) — returns `ENCHANT_ERROR_INVALID_FORMAT` for others |

### 2.3 Unused Code

| # | File:Line | Function | Notes |
|---|-----------|----------|-------|
| 1 | `key_transparency.cpp` | `next_power_of_two` | Defined but never called internally |
| 2 | `svr_v4.cpp:56-61` | `SvrV4Manager::base_point_scalar()` | Defined but never called |
| 3 | `server_protocol.cpp:132-134` | `set_expected_fmspc()` | Stores value but `expected_fmspc_` is never checked during verification |
| 4 | `triple_ratchet_outgoing.cpp:179-181` | `OutgoingTripleRatchet::advance_ratchet()` | Redundant wrapper — just calls `derive_next_chain()` |
| 5 | `multi_recipient.cpp:236-241` | `MultiRecipientEncoder::finalize()` | Just clears state — no output processing |
| 6 | `address.hpp` | Empty namespace | File exists but contains no declarations |

### 2.4 Security Issues

| # | File | Issue | Severity |
|---|------|-------|----------|
| 1 | `aes.cpp:583-598` | CBC padding oracle (non-constant-time validation) | CRITICAL |
| 2 | `aes_gcm_siv.cpp:125-189` | Non-standard key derivation — incompatible with RFC 8452 | CRITICAL |
| 3 | `endorsement_3hashsdhi.hpp:251-267` | Batch proof verification is a no-op | CRITICAL |
| 4 | `svr_manager.cpp:174-230` | No brute force protection (attempts never decremented) | HIGH |
| 5 | `svr_manager.cpp` destructor | `master_key_` (`std::array`) not zeroed on destruction | HIGH |
| 6 | `backup_encryptor.cpp` destructor | `master_key_` (`std::array`) not zeroed on destruction | HIGH |
| 7 | `backup_encryptor.cpp:346-372` | `compute_frame_mac()` ignores plaintext — MAC doesn't authenticate content | HIGH |
| 8 | `veil_v2.cpp:553-584` | Server certificate signature not verified | HIGH |
| 9 | `device_transfer_rsa.cpp:33` | Only accepts 2048/4096-bit RSA — 2048 approaching end-of-life | MEDIUM |
| 10 | `device_transfer_rsa.cpp:274-380` | OAEP label parameter silently ignored (passes `nullptr, 0`) | MEDIUM |
| 11 | `sesame.cpp:83` | Hardcoded test key ID `0xDEADC357` always returns revoked — test backdoor | MEDIUM |
| 12 | `profile_cipher.cpp` | `salt` parameter accepted but never used in key derivation | MEDIUM |
| 13 | `jwt.cpp` | Custom JSON parser is fragile — not safe for untrusted input | MEDIUM |
| 14 | `constant_time.cpp:23` | `constant_time_is_zero` returns true for null pointers | LOW |
| 15 | `svr_manager.cpp:63,101` | `reinterpret_cast<std::array*>` on raw array — technically UB | LOW |
| 16 | `aes.cpp:422-423` | CTR mode IV is 12 bytes padded to 16 — last 4 bytes zeroed, undocumented | LOW |
| 17 | `xchacha20.cpp:59` | `*ciphertext_len` written even on encryption failure | LOW |
| 18 | `recipient_errors.cpp:68-83` | Fixed 4096-byte buffer with `snprintf` — truncated output includes null bytes | LOW |

---

## 3. Module-by-Module Comparison

### 3.1 Primitives (Crypto)

| Feature | LibEnchantCrypto | libsignal | Verdict |
|---------|-----------------|-----------|---------|
| **AES-256-GCM** | OpenSSL `EVP_aes_256_gcm()` — one-shot | Custom `Aes256Gcm` with streaming `update()` API | libsignal more flexible (streaming); both correct |
| **AES-256-CBC** | OpenSSL with PKCS7 padding — **non-constant-time padding check** | Not implemented (by design) | LibEnchantCrypto has padding oracle vulnerability |
| **AES-256-CTR** | OpenSSL — IV padded to 16 bytes | Custom via `ctr` crate | Equivalent functionality |
| **AES-GCM-SIV** | Custom implementation — **non-standard key derivation** | Uses `aes-gcm-siv` crate (RFC 8452 compliant) | **libsignal correct; LibEnchantCrypto broken** |
| **AES-SIV** | Custom implementation — only 2 AD vectors supported | Not implemented | LibEnchantCrypto has extra feature but limited |
| **ChaCha20-Poly1305** | libsodium — **no capacity check** | Not implemented | LibEnchantCrypto has buffer overflow risk |
| **XChaCha20-Poly1305** | libsodium — **no capacity check in AEAD variant** | Not implemented | LibEnchantCrypto has buffer overflow risk |
| **Ed25519** | libsodium — full keypair, sign, verify, convert | `curve25519-dalek` — equivalent | Equivalent |
| **X25519** | libsodium — DH with small-subgroup check | `x25519-dalek` — equivalent | Equivalent |
| **HKDF** | Custom — `hkdf_expand_label` hardcodes HPKE v1 context | `hkdf` crate — clean separation | libsignal more correct/reusable |
| **HMAC** | libsodium — SHA512 state leak on error | `hmac` crate — no leak | libsignal safer |
| **HPKE** | OpenSSL `EVP_HPKE_CTX` — no type byte prefix | `hpke_rs` crate — 1-byte type tag prepended | **Wire format incompatible** |
| **SHA-256/384/512** | libsodium + OpenSSL | `sha2` crate | Equivalent |
| **Constant-time ops** | `sodium_memcmp` + custom `constant_time_*` | `subtle` crate | Equivalent |
| **Random bytes** | libsodium `randombytes_buf` | `rand` crate | Equivalent |

### 3.2 Protocol (Sessions, X3DH, Ratchet)

| Feature | LibEnchantCrypto | libsignal | Verdict |
|---------|-----------------|-----------|---------|
| **X3DH** | `x3dh_initiate/respond()` — C functions | `Handshake` trait + `pqxdh` module — async | Equivalent protocol, different abstraction |
| **PQXDH** | `pqxdh_initiate/respond()` — inline in x3dh.cpp | Separate `pqxdh` module with `spqr` crate | Equivalent protocol |
| **X4DH** | Implemented in x3dh.cpp | **Not present** | LibEnchantCrypto has extra protocol (Enchant extension) |
| **Double Ratchet** | `veil/chain.cpp` + `envelope_state.cpp` — custom | `ratchet.rs` + `double_ratchet.rs` | Equivalent |
| **Triple Ratchet** | `protocol/triple_ratchet.cpp` — custom | `triple_ratchet.rs` — custom | Both post-quantum ratchet implementations |
| **Session management** | `SessionManager` — synchronous | `SessionManager` — async with store traits | libsignal has persistent storage abstraction |
| **Session record** | `SessionRecord` with archive/promote | `SessionRecord` with promote | Equivalent |
| **Identity trust** | Not checked during decrypt | Checked via `IdentityKeyStore::is_trusted_identity()` | **libsignal more secure** |
| **Pre-key processing** | In `x3dh.cpp` | In `session.rs` with signature verification | Equivalent |
| **Signed prekey** | In `prekey.hpp/cpp` | In `state/signed_prekey.rs` | Equivalent |
| **Kyber prekey** | In `prekey.hpp/cpp` | In `state/kyber_prekey.rs` | Equivalent |
| **Message encryption** | `SessionCipher` — synchronous | `SessionCipher` — async | Equivalent |
| **Forward secrecy** | `forward_secrecy.cpp` — HMAC-based ratchet | Integrated in session management | Equivalent |
| **SPQR state** | `spqr_state.cpp` — separate module | `spqr` crate — integrated | Both support post-quantum ratchet |
| **Error handling** | Integer error codes (`ENCHANT_SUCCESS`, etc.) | Typed `Result<T, SignalProtocolError>` | **libsignal more ergonomic and safe** |

### 3.3 Groups (Group Cipher, Sender Keys, MLS)

| Feature | LibEnchantCrypto | libsignal | Verdict |
|---------|-----------------|-----------|---------|
| **Group cipher** | `GroupCipher` class — sync | `group_encrypt/decrypt()` — async | Equivalent protocol |
| **Sender keys** | Custom structs with HMAC chain key | Protobuf-backed `SenderKeyState` | Both implement same algorithm |
| **Chain key derivation** | HMAC-SHA256 with `0x01`/`0x02` | Same HMAC approach | Equivalent |
| **Message key derivation** | HKDF with info=`"EnvelopeGroup"` | HKDF with info=`"WhisperGroup"` | **Different labels — incompatible** |
| **Encryption** | XChaCha20-Poly1305 | AES-256-CBC | **Different AEAD — incompatible wire format** |
| **Distribution messages** | Custom binary + Ed25519 signature | Protobuf `SenderKeyDistributionMessage` | **Different serialization — incompatible** |
| **MLS state machine** | `mls_state_machine.cpp` — **multiple bugs** | Not implemented (libsignal doesn't have MLS) | LibEnchantCrypto has extra feature but buggy |
| **MLS TreeKEM** | `mls_tree_kem.cpp` — **missing `compute_subtree_hash`** | Not implemented | LibEnchantCrypto has extra feature but incomplete |
| **Storage service** | `storage_service.cpp` | Not implemented | LibEnchantCrypto extra feature |
| **Admin approval** | `admin.hpp` — header-only | Not implemented | LibEnchantCrypto extra feature |
| **Group invite links** | `group_link.hpp` — header-only | Not implemented | LibEnchantCrypto extra feature |

### 3.4 Veil / Sealed Sender

| Feature | LibEnchantCrypto | libsignal | Verdict |
|---------|-----------------|-----------|---------|
| **V1 sealed sender** | XChaCha20 + HMAC-SHA256 | AES-256-CTR + HMAC-SHA256 | **Different symmetric cipher — incompatible** |
| **V2 sealed sender** | XChaCha20 + HKDF | AES-256-GCM-SIV + HKDF | **Different AEAD — incompatible; GCM-SIV is nonce-misuse resistant, XChaCha20 is not** |
| **Multi-recipient V2** | Implemented with `SealedSenderV2SentMessage` | Implemented with same structure | Equivalent |
| **Certificate validation** | `ServerCertificateValidator` — **does NOT verify signature** | `KNOWN_SERVER_CERTIFICATES` with trust roots | **libsignal more secure** |
| **Certificate revocation** | Dynamic `add_revoked_key_id()` | Hardcoded `REVOKED_SERVER_CERTIFICATE_KEY_IDS` | LibEnchantCrypto more flexible |
| **Expiration checking** | Not implemented in decrypt path | Implemented | **libsignal more secure** |
| **Safety numbers** | SHA-512 of identity keys | SHA-512 of identity keys | Equivalent |
| **Trust tokens** | Ed25519 signatures | Ed25519 signatures | Equivalent |
| **Sesame** | `sesame.cpp` — full trust management | Not present (handled at app layer) | LibEnchantCrypto has extra feature |
| **Envelope state** | `envelope_state.cpp` — complex ratchet state | In `ratchet.rs` | Equivalent |
| **Async** | `veil_async.cpp` — coroutine-based | Fully async/await | Equivalent |

### 3.5 ZK Proofs (zkcredential, zkgroup)

| Feature | LibEnchantCrypto | libsignal | Verdict |
|---------|-----------------|-----------|---------|
| **ZKP system** | `enchant_zkp/` — Ristretto255-based | `poksho/` — Curve25519-based | **Different curve — incompatible proofs** |
| **Domain separation** | `"enchant_ZKCredential_*"` labels | `"Signal_ZKCredential_*"` labels | **Critical: keys/proofs incompatible** |
| **Credentials** | `credentials.hpp` — fixed 7 attributes | `credentials.rs` — variable attributes | libsignal more flexible |
| **Attributes** | Free functions + `AttributeKeyPair` | Trait-based `PublicAttribute`/`Domain` | **libsignal has stronger type safety** |
| **Presentation** | `PresentationProofBuilder` — `bool` return | `PresentationProofBuilder` — `Result` return | **libsignal has better error handling** |
| **Blind issuance** | `blind.hpp` — inline | `issuance/blind.rs` | Equivalent |
| **Endorsements** | `endorsement.hpp` | `endorsements.rs` | Equivalent |
| **3HashSDHI** | `endorsement_3hashsdhi.hpp` — **batch verify is no-op** | Not present | LibEnchantCrypto has extra feature but broken |
| **Auth credentials** | `auth_credential.hpp` | `api/auth.rs` | Equivalent |
| **Auth with PNI** | **Not implemented** | Full `AuthCredentialWithPni` with Zkc variants | **libsignal has extra feature** |
| **Group credentials** | `group_credential.hpp` | `api/groups.rs` | Equivalent |
| **Profile key credentials** | `profile_key_credential.hpp` | `api/profiles.rs` | Equivalent |
| **Receipt credentials** | `receipt_credential.hpp` | `api/receipts.rs` | Equivalent |
| **Call link credentials** | `call_link_credential.hpp` | `api/call_links.rs` | Equivalent |
| **Group send endorsements** | `group_send_endorsement.hpp` | `api/groups/group_send_endorsement.rs` | Equivalent |
| **Expiring profile key** | `expiring_profile_key_credential.hpp` | `api/profiles/expiring_profile_key_credential.rs` | Equivalent |
| **Constant-time equality** | Not implemented for ZK types | Implemented via `ConstantTimeEq` | **libsignal more secure** |
| **Inverse key** | Not implemented | `KeyPair::inverse_of()` | libsignal has extra feature |
| **Arbitrary attribute encryption** | Not implemented | `encrypt_arbitrary_attribute<D2>()` | libsignal has extra feature |

### 3.6 SVR (Secure Value Recovery)

| Feature | LibEnchantCrypto | libsignal | Verdict |
|---------|-----------------|-----------|---------|
| **OPRF** | `oprfs.cpp` — Ristretto scalar multiplication | In `attest/svr2.rs` | Equivalent |
| **SVR3** | `svr3.cpp` — additive shares + HKDF | In `attest/svr2.rs` | Equivalent |
| **SVR4** | `svr_v4.cpp` — Shamir secret sharing over Ristretto | Not present | LibEnchantCrypto has extra protocol version |
| **SVRB** | `svrb.cpp` — MAC-based share verification | `svrb/` crate | Both implement SVR-B |
| **Server protocol** | `server_protocol.cpp` — **backup silently discards share** | In `attest/svr2.rs` | **LibEnchantCrypto has critical bug** |
| **Manager** | `svr_manager.cpp` — **brute force protection broken** | In `net/svr.rs` | **LibEnchantCrypto has critical bug** |
| **Rate limiting** | In-memory `SvrRateLimiter` | Server-side | Different implementation layer |
| **Attestation** | `SvrAttestationHandler` | In `attest/` crate | Equivalent |
| **Forward secrecy** | `SvrForwardSecrecyManager` | Not present | LibEnchantCrypto has extra feature |
| **PIN derivation** | `derive_pin_key()` — zero salt | Not present in libsignal-svrb | Different design |
| **Master key zeroing** | **Not zeroed** (`std::array`) | Rust drop semantics (zeroed) | **libsignal more secure** |

### 3.7 Backup

| Feature | LibEnchantCrypto | libsignal | Verdict |
|---------|-----------------|-----------|---------|
| **Encryption** | XChaCha20-Poly1305 for chunks, AES-256-GCM for frames | AES-256-CBC for frames + HMAC | **Different ciphers — incompatible** |
| **Key derivation** | HKDF with `"enchant_BackupKey_*"` labels | HKDF with `"SIGNAL_BACKUP_*"` labels | **Different labels — incompatible** |
| **Forward secrecy** | Not implemented | `BackupForwardSecrecyToken` nonce | **libsignal has extra feature** |
| **Manifest** | `serialize_manifest/deserialize_manifest` | Not in `key.rs` (handled elsewhere) | Different scope |
| **Chunk MAC** | `compute_frame_mac()` — **ignores plaintext content** | In `frame/mac_read.rs` | **LibEnchantCrypto MAC is broken** |
| **Padding** | PKCS7-style | No padding in CBC layer | Different approach |
| **Frame encryption** | `encrypt_frame/decrypt_frame` | In `frame/cbc.rs` + `frame/aes_read.rs` | Equivalent |
| **Transfer** | `transfer.cpp` — X25519 + AES-GCM | Not present | LibEnchantCrypto has extra feature |
| **Entities** | `backup_entities.cpp` — **only 4 of 13 types serializable** | In `message-backup/backup/*.rs` — comprehensive | **libsignal more complete** |
| **Integrity check** | `verify_backup_integrity` — decrypts all frames | Not shown | LibEnchantCrypto has extra feature |
| **Master key zeroing** | **Not zeroed** (`std::array`) | Rust drop semantics | **libsignal more secure** |

### 3.8 Key Transparency

| Feature | LibEnchantCrypto | libsignal | Verdict |
|---------|-----------------|-----------|---------|
| **VRF** | `vrf.cpp` — Ristretto-based | `keytrans/vrf.rs` — Ed25519-based | Both implement VRF |
| **Merkle tree** | `transparency_log.cpp` | `keytrans/left_balanced.rs` + `log.rs` | Equivalent |
| **Prefix tree** | `prefix_tree.cpp` | `keytrans/prefix.rs` | Equivalent |
| **Proof generation** | `key_transparency.cpp` | `keytrans/verify.rs` | Equivalent |
| **Verification** | `verify_audit_proof` | `verify.rs` | Equivalent |
| **Dead code** | `next_power_of_two` — defined but never called | N/A | LibEnchantCrypto cleanup needed |

### 3.9 Post-Quantum (ML-KEM)

| Feature | LibEnchantCrypto | libsignal | Verdict |
|---------|-----------------|-----------|---------|
| **ML-KEM-768** | libsodium/liboqs backend | `libcrux_ml_kem` (formally verified) | **libsignal has formally verified backend** |
| **ML-KEM-1024** | OpenSSL backend | `libcrux_ml_kem` (formally verified) | **libsignal has formally verified backend** |
| **Kyber (legacy)** | `kyber.hpp` — alias definitions | Separate `kyber768.rs`, `kyber1024.rs` | libsignal has migration path |
| **Constant-time key comparison** | **Not implemented** | `subtle::ConstantTimeEq` | **libsignal more secure** |
| **Type safety** | Conditional compilation + if/else | Generic `Key<Public>` with `KeyType` enum | **libsignal more type-safe** |
| **Serialization** | 1-byte type prefix + raw bytes | 1-byte type prefix + raw bytes | Equivalent wire format |
| **Random bytes** | `randombytes.c` — custom | Standard library | Different approach |

### 3.10 Media Sanitization

| Feature | LibEnchantCrypto | libsignal | Verdict |
|---------|-----------------|-----------|---------|
| **MP4** | `mp4_sanitizer.cpp` — custom parser | `mp4san` crate (separate) | Equivalent |
| **WebP** | `webp_sanitizer.cpp` — custom parser | `websan` crate (separate) | Equivalent |
| **Format detection** | Magic bytes for MP4, WebP, JPEG, PNG, GIF | Only MP4 and WebP | **LibEnchantCrypto detects more formats** |
| **Async** | Synchronous | Async via `futures::Stream` | **libsignal more scalable** |
| **Trait extensibility** | None — monolithic | `AsyncSkip`, `InputSpan`, `Skip` traits | **libsignal more extensible** |

### 3.11 Usernames

| Feature | LibEnchantCrypto | libsignal | Verdict |
|---------|-----------------|-----------|---------|
| **Username hashing** | SHA-256 based | Scalar-based (base-37 encoding + ZK) | **Different algorithms — incompatible** |
| **ZK proofs** | **Not implemented** | `poksho`-based proofs of ownership | **libsignal has cryptographic proofs** |
| **Discriminator validation** | None | Format validation (no leading zeros, etc.) | **libsignal more secure** |
| **Character validation** | Accepts any bytes | Restricts to `[_0-9a-z]` | **libsignal more restrictive/secure** |
| **Case sensitivity** | Preserves case | Lowercases for hashing | **libsignal more consistent** |
| **Candidate generation** | Not implemented | Random discriminator generation | libsignal has extra feature |
| **Storage layer** | Full `IUsernameStore` interface | None (pure computation) | **LibEnchantCrypto more complete** |
| **Reservation lifecycle** | Reserve/confirm/delete | None | **LibEnchantCrypto more complete** |
| **Link creation/resolution** | `username_link.cpp/hpp` | `username_links.rs` | Equivalent |

### 3.12 Device Transfer

| Feature | LibEnchantCrypto | libsignal | Verdict |
|---------|-----------------|-----------|---------|
| **Key transfer** | XChaCha20-Poly1305 symmetric key packages | RSA key generation + X.509 certs | **Completely different approaches** |
| **RSA support** | `device_transfer_rsa.cpp` — full RSA operations | `device-transfer` crate — RSA via `boring` | Both implement RSA |
| **Certificate** | Self-signed X.509 with serial number 1 | Full X.509 with CN/O/OU fields | libsignal more complete |
| **Key format** | Raw symmetric keys | DER-encoded RSA keys | Different |
| **Multiple key types** | Identity, prekeys, session, sender keys | Single RSA key | **LibEnchantCrypto more comprehensive** |
| **2048-bit RSA** | Accepted (approaching end-of-life) | Not analyzed | LibEnchantCrypto accepts weaker keys |

### 3.13 Attestation

| Feature | LibEnchantCrypto | libsignal | Verdict |
|---------|-----------------|-----------|---------|
| **SGX DCAP** | `dcap.cpp`, `dcap_full.cpp` | `attest/dcap.rs` — comprehensive | Both implement DCAP |
| **Cert chain** | `cert_chain.cpp` | `attest/cert_chain.rs` | Equivalent |
| **CRL** | `crl.cpp` | In `dcap/revocation_list.rs` | Equivalent |
| **TCB info** | `tcb_info.cpp` | Not present | LibEnchantCrypto has extra |
| **HSM enclave** | Not present | `attest/hsm_enclave.rs` | libsignal has extra |
| **SGX session** | Not present | `attest/sgx_session.rs` | libsignal has extra |
| **Snow Noise** | Not present | `attest/snow_resolver.rs` | libsignal has extra |

### 3.14 Account Keys

| Feature | LibEnchantCrypto | libsignal | Verdict |
|---------|-----------------|-----------|---------|
| **Entropy pool** | `account_entropy_pool.cpp` | Not present | LibEnchantCrypto has extra |
| **Backup key** | `backup_key.cpp` — HKDF-based | `account-keys/backup.rs` — HKDF-based | Equivalent |
| **Password hashing** | Not present | Argon2-based | **libsignal has password hashing** |
| **Key derivation labels** | `"enchant_*"` prefixes | `"SIGNAL_*"` prefixes | **Different labels — incompatible** |

### 3.15 Crypto Utilities (JWT, Profile Cipher, Certificate Validator)

| Feature | LibEnchantCrypto | libsignal | Verdict |
|---------|-----------------|-----------|---------|
| **JWT** | `jwt.cpp` — custom JSON parser, fragile | Not present | LibEnchantCrypto has extra feature |
| **Profile cipher** | `profile_cipher.cpp` — **salt parameter ignored** | Not present (in app layer) | LibEnchantCrypto has extra feature but bug |
| **Certificate validator** | `certificate_validator.cpp` — OpenSSL-based | Not present (in app layer) | LibEnchantCrypto has extra feature |
| **Incremental MAC** | `incremental_mac.cpp` — simple HMAC wrapper | `protocol/incremental_mac.rs` — streaming with chunk verification | **libsignal more complete** |
| **Profile encryption** | `profile_cipher.cpp` with XChaCha20 | In `zkgroup/crypto/profile_key_encryption.rs` | Both implement profile encryption |

---

## 4. Architecture Comparison

| Aspect | LibEnchantCrypto | libsignal |
|--------|-----------------|-----------|
| **Language** | C++17 | Rust |
| **Memory safety** | Manual `sodium_memzero` + RAII | Ownership system + drop semantics |
| **Async** | Coroutine-based (`veil_async.cpp`) | Tokio async/await throughout |
| **Error handling** | Integer error codes | Typed `Result<T, E>` enums |
| **Abstraction** | C-style functions + classes | Trait-based generics |
| **Build system** | CMake | Cargo |
| **Testing** | Separate test files | Inline `#[cfg(test)]` modules |
| **FFI** | C API (`api.h`) | Bridge layer (FFI/JNI/Node) |
| **Dependencies** | OpenSSL, libsodium | curve25519-dalek, hpke-rs, libcrux-ml-kem |
| **Formal verification** | None | libcrux-ml-kem is formally verified |
| **Unsafe code** | Allowed (C++) | `#![deny(unsafe_code)]` in many crates |

---

## 5. Wire Format Compatibility

**LibEnchantCrypto is NOT wire-compatible with libsignal.** Key incompatibilities:

| Component | LibEnchantCrypto | libsignal | Compatible? |
|-----------|-----------------|-----------|-------------|
| **HPKE ciphertext** | `[enc \|\| ct]` (no type byte) | `[type_byte \|\| enc \|\| ct]` | NO |
| **Sealed sender V1** | XChaCha20 + HMAC | AES-256-CTR + HMAC | NO |
| **Sealed sender V2** | XChaCha20 | AES-256-GCM-SIV | NO |
| **Group message encryption** | XChaCha20-Poly1305 | AES-256-CBC | NO |
| **Sender key distribution** | Custom binary + protobuf | Protobuf only | NO |
| **HKDF labels** | `"enchant_*"` prefixes | `"Signal_*"` prefixes | NO |
| **ZK proof labels** | `"enchant_*"` prefixes | `"Signal_*"` prefixes | NO |
| **Message key info** | `"EnvelopeGroup"` | `"WhisperGroup"` | NO |
| **Backup key labels** | `"enchant_BackupKey_*"` | `"SIGNAL_BACKUP_*"` | NO |
| **Username hash** | SHA-256 based | Scalar-based (ZK) | NO |
| **ML-KEM serialization** | 1-byte prefix + raw | 1-byte prefix + raw | YES (format-compatible) |
| **X3DH/PQXDH structure** | Standard | Standard | YES (protocol-compatible) |
| **Double Ratchet** | Standard | Standard | YES (algorithm-compatible) |

---

## 6. Security Posture Comparison

| Security Property | LibEnchantCrypto | libsignal | Advantage |
|-------------------|-----------------|-----------|-----------|
| **Memory safety** | Manual zeroing + RAII | Rust ownership | libsignal |
| **Constant-time crypto** | `sodium_memcmp` + custom ops | `subtle` crate | Equivalent |
| **Padding oracle protection** | **NONE** (non-constant-time CBC) | N/A (no CBC) | libsignal |
| **Nonce-misuse resistance** | **NONE** (XChaCha20 not resistant) | AES-256-GCM-SIV (resistant) | libsignal |
| **Small-subgroup checks** | X25519 DH checks | X25519 DH checks | Equivalent |
| **Certificate verification** | **Incomplete** (V2 doesn't verify sig) | Full verification | libsignal |
| **Brute force protection** | **Broken** (attempts never decremented) | Server-side enforcement | libsignal |
| **Key material zeroing** | Partial (some `std::array` not zeroed) | Complete (Rust drop) | libsignal |
| **Batch proof verification** | **Broken** (always returns true) | Not implemented | N/A |
| **Formal verification** | None | libcrux-ml-kem | libsignal |
| **Replay protection** | Implemented (consumed_keys_) | Implemented | Equivalent |
| **Max forward jumps** | Enforced (25000) | Enforced | Equivalent |
| **Test backdoors** | **Present** (`0xDEADC357` hardcoded) | None | libsignal |

---

## 7. Feature Gap Analysis

### Features in LibEnchantCrypto but NOT in libsignal

| Feature | Module | Status |
|---------|--------|--------|
| X4DH handshake | `protocol/x3dh.cpp` | Implemented, complete |
| SVR4 (Shamir sharing) | `svr/svr_v4.cpp` | Implemented, complete |
| 3HashSDHI endorsements | `zk/zkcredential/endorsement_3hashsdhi.hpp` | **Buggy (batch verify no-op)** |
| MLS state machine | `groups/mls_state_machine.cpp` | **Multiple critical bugs** |
| MLS TreeKEM | `groups/mls_tree_kem.cpp` | **Missing `compute_subtree_hash`** |
| Group admin approval | `groups/admin.hpp` | Implemented, header-only |
| Group invite links | `groups/group_link.hpp` | Implemented, header-only |
| Storage service | `groups/storage_service.cpp` | Implemented, complete |
| Backup transfer | `backup/transfer.cpp` | Implemented, complete |
| Backup integrity check | `backup/restore.cpp` | Implemented, complete |
| Profile cipher | `crypto/profile_cipher.cpp` | **Salt parameter ignored** |
| JWT | `crypto/jwt.cpp` | **Fragile JSON parser** |
| Certificate validator | `crypto/certificate_validator.cpp` | Implemented, complete |
| Username reservation | `username/username_manager.cpp` | Implemented, complete |
| Entropy pool | `account_keys/account_entropy_pool.cpp` | Implemented, complete |
| Device transfer (symmetric) | `protocol/device_transfer.cpp` | Implemented, complete |
| Device transfer RSA | `protocol/device_transfer_rsa.cpp` | Implemented, complete |
| Metrics | `metrics/metrics.cpp` | Implemented, complete |
| Agent sessions | `agents/agent_session.cpp` | Implemented, complete |
| Async crypto | `async/crypto.cpp` | Implemented, complete |
| Protocol logger | `protocol/logger.cpp` | Implemented, complete |
| Error aggregator | `protocol/error_aggregator.cpp` | Implemented, complete |
| Multi-device | `protocol/multi_device.cpp` | Implemented, complete |
| Multi-recipient | `protocol/multi_recipient.cpp` | **Session copied by value** |
| Phone number identity | `protocol/phone_number_identity.cpp` | Implemented, complete |
| Safety numbers | `protocol/safety_number.cpp` | Implemented, complete |
| Session archive | `protocol/session_archive.cpp` | Implemented, complete |
| Session upgrade | `protocol/session_upgrade.cpp` | Implemented, complete |
| Stale devices | `protocol/stale_devices.cpp` | Implemented, complete |
| Forward secrecy | `protocol/forward_secrecy.cpp` | Implemented, complete |
| Mismatched devices | `protocol/mismatched_devices.cpp` | Implemented, complete |
| Fingerprint | `protocol/fingerprint.cpp` | Implemented, complete |
| Prekey store | `protocol/prekey.cpp` | Implemented, complete |
| Identity store | `protocol/identity_store.cpp` | Implemented, complete |
| Trust store | `protocol/identity_trust_store.cpp` | Implemented, complete |
| Session state | `protocol/session_state.cpp` | Implemented, complete |
| Triple ratchet | `protocol/triple_ratchet.cpp` | Implemented, complete |
| SPQR state | `protocol/spqr_state.cpp` | Implemented, **chain index bug** |
| Noise protocol | `protocol/noise.cpp` | Implemented, complete |
| Key chain | `protocol/key_chain.cpp` | Implemented, complete |
| Address | `protocol/address.hpp` | **Empty file** |

### Features in libsignal but NOT in LibEnchantCrypto

| Feature | Module | Notes |
|---------|--------|-------|
| Auth credential with PNI | `zkgroup/api/auth/auth_credential_with_pni.rs` | Phone number identity integration |
| Constant-time ZK types | `poksho` (via `subtle`) | Missing in LibEnchantCrypto |
| `inverse_of()` for keys | `zkcredential/attributes.rs` | Key inversion for cross-domain |
| Arbitrary attribute encryption | `zkcredential/attributes.rs` | Cross-domain encryption |
| Password hashing (Argon2) | `account-keys/hash.rs` | Missing in LibEnchantCrypto |
| SPQR crate (integrated PQ ratchet) | `protocol` (via `spqr`) | LibEnchantCrypto has `pqr_key` as salt only |
| Streaming AES-GCM | `crypto/aes_gcm.rs` | LibEnchantCrypto is one-shot only |
| Streaming incremental MAC | `protocol/incremental_mac.rs` | LibEnchantCrypto is simple HMAC wrapper |
| HSM enclave attestation | `attest/hsm_enclave.rs` | Missing in LibEnchantCrypto |
| SGX session | `attest/sgx_session.rs` | Missing in LibEnchantCrypto |
| Snow Noise protocol | `attest/snow_resolver.rs` | Missing in LibEnchantCrypto |
| Message backup (comprehensive) | `message-backup/` | LibEnchantCrypto has partial implementation |
| Network layer | `net/` | LibEnchantCrypto has no networking |
| Chat API | `net/chat/` | LibEnchantCrypto has no networking |
| CDSI (Contact Discovery) | `net/cdsi.rs` | LibEnchantCrypto has no networking |
| Formally verified KEM | `protocol/kem.rs` (libcrux) | LibEnchantCrypto uses OpenSSL/libsodium |
| Cross-version testing | `protocol/cross-version-testing/` | LibEnchantCrypto has no version compat testing |

---

## 8. Recommendations

### Critical Fixes Required

1. **Fix AES-GCM-SIV key derivation** (`aes_gcm_siv.cpp`) — Must comply with RFC 8452 Section A.2
2. **Fix batch proof verification** (`endorsement_3hashsdhi.hpp:251-267`) — Actually compare the hashes
3. **Fix brute force protection** (`svr_manager.cpp`) — Decrement `attempts_remaining` on failed attempts
4. **Fix backup share storage** (`server_protocol.cpp`) — Actually store the `secret_share` in `handle_backup_request()`
5. **Add constant-time CBC padding validation** (`aes.cpp:583-598`) — Prevent padding oracle
6. **Add buffer capacity checks** (`chacha20_poly1305_ietf.cpp`, `xchacha20.cpp`) — Prevent buffer overflows
7. **Fix MLS epoch secret derivation** (`mls_state_machine.cpp:157-176`) — Use tree-derived secret
8. **Fix uninitialized read** (`mls_state_machine.cpp:260-288`) — Fill `secrets_out` before computing transcript hash
9. **Fix server certificate verification** (`veil_v2.cpp`) — Actually verify the signature
10. **Fix session copy bug** (`multi_recipient.cpp:243-256`) — Store session by reference, not copy

### Security Hardening

1. **Zero master keys on destruction** in `svr_manager.cpp` and `backup_encryptor.cpp`
2. **Remove test backdoor** (`sesame.cpp:83` — `SESAME_REVOCATION_TEST_KEY_ID`)
3. **Add constant-time equality** for ZK credential types
4. **Add certificate expiration checking** in sealed sender decrypt path
5. **Implement missing entity serialization** in `backup_entities.cpp` (9 remaining types)
6. **Complete `compute_subtree_hash`** in `mls_tree_kem.cpp`
7. **Implement `DistributionMessage::serialize/deserialize`** in `sender_key.cpp`

### Wire Format Compatibility

If interoperability with libsignal is required:
- Adopt AES-256-GCM-SIV for sealed sender V2 (instead of XChaCha20)
- Adopt AES-256-CTR + HMAC for sealed sender V1 (instead of XChaCha20)
- Adopt AES-256-CBC for group message encryption (instead of XChaCha20)
- Add 1-byte type prefix to HPKE ciphertext
- Change all domain separation labels from `"enchant_*"` to `"Signal_*"`
- Change message key derivation info from `"EnvelopeGroup"` to `"WhisperGroup"`

### Code Quality

1. Remove empty `address.hpp`
2. Remove unused `next_power_of_two` in key_transparency.cpp
3. Remove unused `base_point_scalar` in svr_v4.cpp
4. Fix `recipient_errors.cpp` buffer truncation to use `std::string(buf, pos)`
5. Add `std::string(buf, pos)` pattern in `mismatched_devices.cpp` and `stale_devices.cpp`
6. Fix `aes.cpp:422-423` — document or fix CTR IV padding behavior
7. Fix `xchacha20.cpp:59` — don't set `*ciphertext_len` on failure
8. Fix `constant_time_is_zero` — return false for null pointers

---

*Report generated by analyzing all 294 source/header files in LibEnchantCrypto and 400+ .rs files in libsignal.*
