# libenchantcrypto — Android Integration Guide

## What is libenchantcrypto?

C++17 E2EE cryptography library built on libsodium. Provides all cryptographic primitives
needed for the Signal Protocol: X25519, Ed25519, XChaCha20-Poly1305, HKDF, Argon2id, SHA-256.

- **Language:** C++ (C ABI exposed via `extern "C"`)
- **Crypto:** libsodium
- **Output:** Static library (`libenchantcrypto.a`) + C headers
- **Repo:** `/home/nsk/project/personal/libenchantcrypto`
- **Headers:** `include/enchant/api.h`, `include/enchant/error.h`

---

## API Reference (C ABI — Callable from JNI/Kotlin via FFI)

All functions return `int`. `0` = success, negative = error code.

### Initialization

```c
int enchant_init(void);
```
Call once at app startup. Initializes libsodium. Thread-safe after this.

### Random Bytes

```c
void enchant_random_bytes(uint8_t* buf, size_t len);
```
CSPRNG. Use for generating nonces, keys, IDs.

### X25519 — ECDH Key Exchange (Curve25519)

```c
#define ENCHANT_X25519_PUBLIC_KEY_SIZE  32
#define ENCHANT_X25519_PRIVATE_KEY_SIZE 32

int enchant_x25519_keypair(uint8_t* public_key,  // [out] 32 bytes
                           uint8_t* private_key); // [out] 32 bytes

int enchant_x25519_dh(const uint8_t* private_key,  // [in] 32 bytes
                      const uint8_t* public_key,   // [in] 32 bytes
                      uint8_t* shared_secret);      // [out] 32 bytes
```

### Ed25519 — Signatures (Identity Key Signing)

```c
#define ENCHANT_ED25519_PUBLIC_KEY_SIZE 32
#define ENCHANT_ED25519_SEED_SIZE       32
#define ENCHANT_ED25519_SIGNATURE_SIZE  64

int enchant_ed25519_keypair(uint8_t* public_key,   // [out] 32 bytes
                            uint8_t* private_seed); // [out] 32 bytes

int enchant_ed25519_sign(const uint8_t* message, size_t message_len,
                         const uint8_t* private_seed,  // 32 bytes
                         uint8_t* signature);           // [out] 64 bytes

int enchant_ed25519_verify(const uint8_t* message, size_t message_len,
                           const uint8_t* signature,   // 64 bytes
                           const uint8_t* public_key);  // 32 bytes
// Returns 0 if valid, -7 (ENCHANT_ERROR_SIGNATURE_INVALID) if invalid
```

### XChaCha20-Poly1305 — AEAD Encryption (Message Encryption)

```c
#define ENCHANT_XCHACHA20_KEY_SIZE   32
#define ENCHANT_XCHACHA20_NONCE_SIZE 24
#define ENCHANT_XCHACHA20_TAG_SIZE   16   // appended to ciphertext

int enchant_xchacha20_encrypt(const uint8_t* plaintext, size_t plaintext_len,
                              const uint8_t* key,     // 32 bytes
                              const uint8_t* nonce,   // 24 bytes, MUST be unique
                              uint8_t* ciphertext);    // [out] plaintext_len + 16

int enchant_xchacha20_decrypt(const uint8_t* ciphertext, size_t ciphertext_len,
                              const uint8_t* key,     // 32 bytes
                              const uint8_t* nonce,   // 24 bytes
                              uint8_t* plaintext);     // [out] ciphertext_len - 16
```

### SHA-256

```c
#define ENCHANT_SHA256_SIZE 32

int enchant_sha256(const uint8_t* data, size_t len, uint8_t* hash); // [out] 32 bytes
```

### HMAC-SHA256

```c
#define ENCHANT_HMAC_SHA256_SIZE 32

int enchant_hmac_sha256(const uint8_t* key, size_t key_len,
                        const uint8_t* data, size_t data_len,
                        uint8_t* mac); // [out] 32 bytes
```

### HKDF-SHA256 — Key Derivation

```c
#define ENCHANT_HKDF_MAX_OUTPUT 8160

int enchant_hkdf_sha256(const uint8_t* ikm, size_t ikm_len,
                        const uint8_t* salt, size_t salt_len,    // NULL/0 = zeros
                        const uint8_t* info, size_t info_len,    // NULL/0 = empty
                        uint8_t* okm, size_t okm_len);           // [out]
```

### Argon2id — Password Hashing

```c
#define ENCHANT_ARGON2_STRBYTES 128

int enchant_argon2id_hash(const char* plaintext, size_t plaintext_len,
                          char* output, size_t output_len);  // [out] 128 bytes, PHC string

int enchant_argon2id_verify(const char* hash, size_t hash_len,  // stored PHC string
                            const char* plaintext, size_t plaintext_len);
// Returns 0 if match, -11 if mismatch
```

### Base64

```c
int enchant_base64_encode(const uint8_t* data, size_t len,
                          char* output, size_t output_len);   // output: ceil(len/3)*4 + 1

int enchant_base64_decode(const char* input,
                          uint8_t* output, size_t output_len); // output: strlen(input)*3/4
```

### Secure Memory

```c
void enchant_secure_zero(void* ptr, size_t len);              // zero before free
int  enchant_secure_alloc(void** ptr, size_t len);            // guarded heap
void enchant_secure_free(void* ptr, size_t len);
```

---

## Error Codes

| Code | Name | Meaning |
|------|------|---------|
| 0 | `ENCHANT_SUCCESS` | OK |
| -1 | `ENCHANT_ERROR_NULL_POINTER` | NULL input |
| -2 | `ENCHANT_ERROR_BUFFER_TOO_SMALL` | Output buffer too small |
| -3 | `ENCHANT_ERROR_INVALID_KEY_SIZE` | Wrong key size |
| -6 | `ENCHANT_ERROR_DECRYPTION_FAILED` | AEAD tag mismatch |
| -7 | `ENCHANT_ERROR_SIGNATURE_INVALID` | Ed25519 verify failed |
| -11 | `ENCHANT_ERROR_INVALID_FORMAT` | Bad format (e.g. wrong Argon2 hash) |
| -99 | `ENCHANT_ERROR_INTERNAL` | libsodium failure |

---

## Android Build Setup

### Option A: Cross-compile for Android (NDK)

```cmake
# CMakeLists.txt for Android native library
cmake_minimum_required(VERSION 3.20)
project(securechat_crypto)

# Point to libenchantcrypto source
add_subdirectory(path/to/libenchantcrypto libenchantcrypto_build)

# Your JNI wrapper
add_library(securechat_crypto SHARED
    jni/crypto_bridge.cpp
)
target_link_libraries(securechat_crypto PRIVATE enchantcrypto)
```

### Option B: Use pre-built .so

Cross-compile libenchantcrypto for each ABI:
```bash
# arm64-v8a
cmake -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 ..
make -j$(nproc)

# armeabi-v7a, x86_64, x86 — same pattern
```

Then load from Kotlin:
```kotlin
object EnchantCrypto {
    init { System.loadLibrary("enchantcrypto") }
    external fun init(): Int
    external fun randomBytes(len: Int): ByteArray
    external fun x25519Keypair(): Pair<ByteArray, ByteArray>
    // ... etc
}
```

---

## Usage Examples

### Generate Identity Key

```c
uint8_t ik_pub[32], ik_priv[32];
enchant_init();
enchant_x25519_keypair(ik_pub, ik_priv);
// ik_pub = identity key (upload to IKS)
// ik_priv = keep secret
```

### Sign Prekey

```c
// Use Ed25519 for SPK signing (different key from X25519 identity)
uint8_t ed_pub[32], ed_seed[32];
enchant_ed25519_keypair(ed_pub, ed_seed);

uint8_t spk[32] = {/* generated X25519 key */};
uint8_t sig[64];
enchant_ed25519_sign(spk, 32, ed_seed, sig);
```

### ECDH Shared Secret (X3DH)

```c
uint8_t shared[32];
enchant_x25519_dh(my_private_key, their_public_key, shared);

// Derive session key
uint8_t session_key[32];
enchant_hkdf_sha256(shared, 32, NULL, 0, (uint8_t*)"enchant-session-v1", 18, session_key, 32);
enchant_secure_zero(shared, 32);
```

### Encrypt Message

```c
uint8_t nonce[24];
enchant_random_bytes(nonce, 24);

uint8_t ciphertext[plaintext_len + 16];
enchant_xchacha20_encrypt(plaintext, plaintext_len, key, nonce, ciphertext);
// Send: base64(nonce) + base64(ciphertext)
```

### Hash Password (Backend Auth)

```c
char hash[128];
enchant_argon2id_hash("userpassword", 12, hash, 128);
// Store hash string directly

// Verify later:
int ok = enchant_argon2id_verify(hash, 0, "userpassword", 12);
// ok == 0 → match, ok == -11 → wrong
```

---

## Thread Safety

All `enchant_*` functions are thread-safe. No shared mutable state.

## Memory Safety

- Zero all secret buffers before free: `enchant_secure_zero(ptr, len)`
- For long-lived secrets (identity keys), use `enchant_secure_alloc` / `enchant_secure_free`
- Never log key material

---

## Backend Endpoints (Where to Register Keys)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/v1/auth/request-otp` | POST | Request OTP login |
| `/v1/auth/verify-otp` | POST | Verify OTP, get JWT |
| `/v1/auth/refresh` | POST | Refresh JWT |
| `/v1/keys/register` | POST | Register identity key + signed prekey + OPKs |
| `/v1/keys/bundle/:user_id` | GET | Fetch key bundle (Auth required) |
| `/v1/keys/signed-prekey` | PUT | Rotate signed prekey |
| `/v1/keys/one-time-prekeys` | POST | Upload more OPKs |
| `/v1/messages` (WebSocket) | WS | Connect for real-time messaging |

### JWT Format

Header: `{"alg":"EdDSA","typ":"JWT"}`  
Payload: `{"sub":"user_id","did":"device_id","iat":timestamp,"exp":timestamp,"jti":"random_hex"}`  
Signature: Ed25519 (64 bytes, base64url)

All authenticated requests: `Authorization: Bearer <jwt_token>`
