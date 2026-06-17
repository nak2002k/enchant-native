# enchant-native (Frontend) — Development Log

## What We Did

### Phase 1: FFI Additions (EnchantCrypto.kt + JNI)
- Added VeilSession FFI: `enchant_veil_session_*` (create, establish, encrypt, decrypt, archive, has_session)
- Added Veil V2 FFI: `enchant_veil_encrypt_v2`, `enchant_veil_decrypt_v2`
- Added USMC accessors: `enchant_usmc_get_sender_uuid`, `get_device_id`, `get_content_hint`, `get_message_type`, `get_contents`, `get_group_id`
- Added certificate FFI: `enchant_server_certificate_*`, `enchant_sender_certificate_*`
- Fixed JNI: method name underscore escaping (`_1`), output length params with `LongArray`/`IntArray`, session cipher encrypt/decrypt output lengths

### Phase 2: Migrate PreKeyStore to Native
- `PreKeyStore.kt` now uses `enchant_prekey_generate_signed` and `enchant_prekey_generate_batch` instead of pure Kotlin
- Created `PrekeyNativeTest.kt` verifying native prekey FFI output format (76-byte wire format)

### Phase 3: SealedSender Native Veil Migration
- **Rewrote `SealedSender.kt`** — replaced custom Kotlin AES-GCM/XChaCha20 with native `enchant_veil_encrypt_v1`/`enchant_veil_decrypt_v1` for anonymous sender, native AES-256-GCM for profile data
- **Created `UsmcHelper.kt`** — wrapper for USMC create/serialize/deserialize/get_* accessors using `LongArray` output lengths
- **Created `UsmcHelperTest.kt`** — tests for USMC create/deserialize roundtrip and metadata preservation
- **Rewrote `SealedSenderTest.kt`** — updated to use X25519 key pairs and new native API
- Added 22 new EnchantCrypto.kt external fun declarations for veil v1/v2, USMC, and certificate functions
- Fixed JNI for `enchant_veil_encrypt_v1`/`enchant_veil_decrypt_v1` — use `GetArrayLength` for output capacity (was 0)
- Fixed JNI for `enchant_usmc_create`/`serialize`/`deserialize` — changed output length from `jlong` to `jlongArray`, use `GetArrayLength` for capacity
- Added JNI bindings for veil v2 encrypt/decrypt (8 new functions) and USMC get_* accessors (6 new functions)

### Phase 6: Cleanup
- Removed 96 unused externals from EnchantCrypto.kt (216→120 declarations)
- Kept agent/sesame/fingerprint/prekey/backup/key-transparency functions for future use

### Phase 7: Agent Sessions
- Created `AgentSessionManager.kt` — wrapper for `enchant_agent_*` FFI (identity, DH session exchange, encrypt/decrypt)
- Fixed agent session DH bug in C++ — uses 2 DH operations (identity×identity + ephemeral×identity)
- Fixed agent encrypt C++ bug — correct ciphertext capacity
- Created `AgentSessionManagerTest.kt` — 20 agent tests passing

### Additional Work
- Created `VeilSession.kt` — class (not singleton) for multi-instance session management
- Created `VeilSealer.kt` — wrapper for 12 veil session FFI functions
- Created `FingerprintHelper.kt` — safety-number fingerprint wrapper with tests
- Created `TrustValidator.kt` — Sesame trust validation wrapper with tests
- Created `Argon2id` tests in `CryptoPrimitivesTest.kt` — hash generation + verify roundtrip
- Fixed `CryptoPrimitives.zeroBytes()` to use native `enchant_secure_zero`
- Fixed pre-existing build errors: base circular dependency, `EnchantCrypto.kt` `package` reserved word → `packageData`, `PreKeyWorker.kt` duplicate companion object, `CryptoHelper.kt` return type mismatch

## What We Found

### Veil V1 Decrypt MAC Mismatch (BLOCKING)
- `enchant_veil_decrypt_v1` returns `-7` on roundtrip
- `eph_shared` differs between encrypt/decrypt despite matching keys
- Root cause investigation in progress — possible key pair validity issue in JNI or C++
- C++ library has debug `fprintf` output tracing the issue

### USMC Empty Certificate Issue
- `enchant_usmc_create` with empty sender cert produces protobuf that fails `UnidentifiedSenderMessageContent::deserialize()`
- `enchant_usmc_get_*` accessors also fail because they deserialize internally
- USMC helper tests (`UsmcHelperTest.CreateDeserializeTest`) failing with `-11` (INVALID_FORMAT)
- Fix needed: either require valid sender cert or handle empty cert gracefully

### Build Dependencies
- JNI build chain: `lib/build_jni` → `frontend/native/build_jni` → copy both `.so` to `test/jniLibs/` → tests
- `lib/build_jni` requires libsodium 1.0.20 at `/tmp/libsodium-install/`
- `lib/build_jni` CMakeCache may need reconfiguration if cached with Android toolchain
- Frontend test output via Gradle captured stdout — C++ `fprintf(stderr)` visible, Kotlin `System.err.println` may not appear

### Key Wire Formats
- Prekey: `[eph(32)][id(32)][spk_id(4)][opk_id(4)][reg_id(4)]` = 76 bytes
- Veil V1: `eph_pub(32) || enc_sender(48) || enc_sender_mac(16) || enc_static_nonce(24) || msg_nonce(24) || msg_mac(16) || ciphertext` = header(160) + plaintext + tag(16)
- Veil versions: V1 (`0x11`), V2 UUID (`0x22`), V2 ServiceID (`0x23`)

### Test Status
- **Full test suite: 765 tasks, BUILD SUCCESSFUL**
- `AgentSessionManagerTest`: 20 tests passing
- `FingerprintHelperTest`: 5 tests passing
- `TrustValidatorTest`: 8 tests passing
- `PrekeyNativeTest`: 2 tests passing
- `CryptoPrimitivesTest.Argon2idHashTest`: 3 tests passing
- `VeilSessionIntegrationTest`: passing
- `VeilSessionTest`: passing
- `SealedSenderTest`: 11/12 passing, 1 failing (veil v1 decrypt MAC mismatch)
- `UsmcHelperTest`: CreateDeserializeTest failing (empty cert issue)

## What Was Planned

1. **Fix veil v1 decrypt MAC mismatch** — Add key pair validity check in C++, trace full 32-byte ephemeral_shared
2. **Fix USMC empty cert** — Create proper server/sender certs via FFI for testing, or handle empty cert gracefully
3. **Remove debug statements** from SealedSender.kt and C++ api.cpp
4. **Further cleanup** of unused EnchantCrypto.kt declarations (~20+ still unused)
5. **Fix `VeilSession.hasIdentityChanged()`** — currently a stub
6. **Expand test coverage** — veil v1 roundtrip tests in C++ bug_fixes_test.cpp
