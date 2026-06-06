# Frontend Integration Guide — libenchantcrypto

This guide covers every cryptographic feature the frontend (Android/iOS) needs to implement, including exact JNI function signatures, C API function signatures, buffer sizes, wire formats, and complete Kotlin/Swift code examples.

## Table of Contents

- [1. Initialization](#1-initialization)
- [2. Identity Generation](#2-identity-generation)
- [3. Signed Prekey Generation + XEdDSA Signing](#3-signed-prekey-generation--xeddsa-signing)
- [4. One-Time Prekey Generation](#4-one-time-prekey-generation)
- [5. Key Bundle Registration](#5-key-bundle-registration)
- [6. X3DH Key Exchange](#6-x3dh-key-exchange)
- [7. PQXDH Post-Quantum Key Exchange](#7-pqxdh-post-quantum-key-exchange)
- [8. Double Ratchet Session Encrypt/Decrypt](#8-double-ratchet-session-encryptdecrypt)
- [9. Sealed Sender](#9-sealed-sender)
- [10. Sender Key — Group Messaging](#10-sender-key--group-messaging)
- [11. Sender Key Distribution Messages](#11-sender-key-distribution-messages)
- [12. GroupsV2 (MLS TreeKEM)](#12-groupsv2-mls-treekem)
- [13. Group State Serialization](#13-group-state-serialization)
- [14. ZK Profile Key Credential — Show UUID](#14-zk-profile-key-credential--show-uuid)
- [15. ZK Auth Credential — Prove Group Membership](#15-zk-auth-credential--prove-group-membership)
- [16. Profile Key Encryption (At-Rest)](#16-profile-key-encryption-at-rest)
- [17. UUID Encryption](#17-uuid-encryption)
- [18. Profile Key Version Derivation](#18-profile-key-version-derivation)
- [19. XEdDSA Signing](#19-xeddsa-signing)
- [20. StorageService Encryption](#20-storageservice-encryption)
- [21. SecureBuffer Usage](#21-securebuffer-usage)
- [22. XChaCha20-Poly1305](#22-xchacha20-poly1305)
- [23. AES-256-GCM](#23-aes-256-gcm)
- [24. HKDF-SHA256](#24-hkdf-sha256)
- [25. SHA-256](#25-sha-256)
- [26. HMAC-SHA256](#26-hmac-sha256)
- [27. Argon2id Password Hashing](#27-argon2id-password-hashing)
- [28. X25519 DH](#28-x25519-dh)
- [29. Ed25519 Sign/Verify](#29-ed25519-signverify)
- [30. Base64 Encode/Decode](#30-base64-encodedecode)
- [Appendix A: Constants Reference](#appendix-a-constants-reference)
- [Appendix B: Error Codes](#appendix-b-error-codes)
- [Appendix C: Wire Format Reference](#appendix-c-wire-format-reference)

---

## 1. Initialization

Always call `enchant_init()` before any other function. This initializes libsodium.

### C API

```c
int enchant_init(void);
```

Returns `ENCHANT_SUCCESS` (0) on success, `ENCHANT_ERROR_INTERNAL` (-99) on failure.

### JNI

No JNI binding — call `enchant_init()` directly from the C layer on app startup. If using the shared library, call it via the JNI bridge's static initializer.

### Kotlin

```kotlin
// Call in Application.onCreate() or before any crypto operations
companion object {
    init {
        System.loadLibrary("enchantcrypto")
    }
}

// In your crypto initialization:
fun initialize(): Boolean {
    // enchant_init is called internally when the library loads
    // If you need to call it explicitly:
    return NativeCrypto.enchantInit() == 0
}
```

### Swift

```swift
// In your app initialization (e.g., AppDelegate or App init)
import libenchantcrypto  // or your bridging header module

func initialize() -> Bool {
    return enchant_init() == ENCHANT_SUCCESS
}
```

---

## 2. Identity Generation

Generate an Ed25519 key pair for long-term identity. The private key is a 64-byte Ed25519 seed (libsodium's `ed25519_seed_keypair` format). The public key is 32 bytes.

### Buffer Sizes

| Buffer | Size |
|--------|------|
| Public key (Ed25519) | 32 bytes |
| Private key (Ed25519 seed) | 64 bytes |

### C API

```c
int enchant_ed25519_keypair(uint8_t* public_key, uint8_t* private_seed);
```

### JNI

There is no direct JNI wrapper for identity generation — use the C API directly through JNI or wrap it in your Kotlin native bridge.

### Kotlin

```kotlin
object IdentityKey {
    const val PUBLIC_KEY_SIZE = 32
    const val PRIVATE_KEY_SIZE = 64
}

fun generateIdentityKeyPair(): Pair<ByteArray, ByteArray> {
    val publicKey = ByteArray(IdentityKey.PUBLIC_KEY_SIZE)  // 32
    val privateKey = ByteArray(IdentityKey.PRIVATE_KEY_SIZE) // 64
    val rc = NativeCrypto.ed25519Keypair(publicKey, privateKey)
    if (rc != 0) throw EnchantCryptoException("Identity key generation failed: $rc")
    return Pair(publicKey, privateKey)
}
```

### Swift

```swift
func generateIdentityKeyPair() -> (publicKey: Data, privateKey: Data) {
    var publicKey = Data(count: 32)
    var privateKey = Data(count: 64)
    let rc = publicKey.withUnsafeMutableBytes { pubPtr in
        privateKey.withUnsafeMutableBytes { privPtr in
            enchant_ed25519_keypair(
                pubPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                privPtr.baseAddress!.assumingMemoryBound(to: UInt8.self)
            )
        }
    }
    guard rc == ENCHANT_SUCCESS else {
        fatalError("Identity key generation failed: \(rc)")
    }
    return (publicKey, privateKey)
}
```

### Wire Format

The identity public key is sent to the server as part of the key bundle:

```
identity_public_key: 32 bytes (Ed25519 public key)
```

The identity private key is stored locally (encrypted with StorageService or Keychain).

---

## 3. Signed Prekey Generation + XEdDSA Signing

Generate an X25519 signed prekey, then sign the public key with the identity's Ed25519 private key using XEdDSA (converts X25519 key to Ed25519 for signing).

### Buffer Sizes

| Buffer | Size |
|--------|------|
| Signed prekey public (X25519) | 32 bytes |
| Signed prekey private (X25519) | 32 bytes |
| Signed prekey signature (Ed25519) | 64 bytes |

### C API

```c
int enchant_x25519_keypair(uint8_t* public_key, uint8_t* private_key);
int enchant_ed25519_sign(const uint8_t* message, size_t message_len,
                         const uint8_t* private_seed, uint8_t* signature);
```

### JNI — XEdDSA Sign

```
Java_org_enchant_core_crypto_NativeXEdDSA_signNative(
    JNIEnv*, jclass,
    jbyteArray message,      // The X25519 public key (32 bytes)
    jbyteArray x25519PrivateKey, // The X25519 private key (32 bytes)
    jbyteArray signature     // Output: Ed25519 signature (64 bytes)
) -> jint
```

### JNI — XEdDSA Verify

```
Java_org_enchant_core_crypto_NativeXEdDSA_verifyNative(
    JNIEnv*, jclass,
    jbyteArray message,      // The X25519 public key (32 bytes)
    jbyteArray signature,    // The Ed25519 signature (64 bytes)
    jbyteArray x25519PublicKey // The identity X25519-derived public key (32 bytes)
) -> jint
```

### JNI — Derive XEdDSA Public Key

```
Java_org_enchant_core_crypto_NativeXEdDSA_derivePublicKeyNative(
    JNIEnv*, jclass,
    jbyteArray x25519PrivateKey,   // X25519 private key (32 bytes)
    jbyteArray xeddsaPublicKey     // Output: Ed25519 public key (32 bytes)
) -> jint
```

### Kotlin

```kotlin
object SignedPrekey {
    const val PUBLIC_KEY_SIZE = 32
    const val PRIVATE_KEY_SIZE = 32
    const val SIGNATURE_SIZE = 64
}

data class SignedPrekeyBundle(
    val publicKey: ByteArray,
    val privateKey: ByteArray,
    val signature: ByteArray
) {
    fun destroy() {
        enchantSecureZero(publicKey)
        enchantSecureZero(privateKey)
    }
}

fun generateSignedPrekey(identityPrivateKey: ByteArray): SignedPrekeyBundle {
    val pub = ByteArray(32)
    val priv = ByteArray(32)
    val sig = ByteArray(64)

    // Generate X25519 keypair
    val rc = NativeCrypto.x25519Keypair(pub, priv)
    if (rc != 0) throw EnchantCryptoException("Signed prekey generation failed: $rc")

    // Sign the public key with XEdDSA using identity private key
    val signRc = NativeXEdDSA.signNative(pub, identityPrivateKey, sig)
    if (signRc != 0) throw EnchantCryptoException("XEdDSA sign failed: $signRc")

    return SignedPrekeyBundle(pub, priv, sig)
}
```

### Swift

```swift
struct SignedPrekeyBundle {
    let publicKey: Data   // 32 bytes
    let privateKey: Data  // 32 bytes
    let signature: Data   // 64 bytes

    mutating func destroy() {
        enchant_secure_zero(&publicKey, 32)
        enchant_secure_zero(&privateKey, 32)
    }
}

func generateSignedPrekey(identityPrivateKey: Data) -> SignedPrekeyBundle? {
    var pub = Data(count: 32)
    var priv = Data(count: 32)
    var sig = Data(count: 64)

    let rc1 = pub.withUnsafeMutableBytes { p in
        priv.withUnsafeMutableBytes { s in
            enchant_x25519_keypair(
                p.baseAddress!.assumingMemoryBound(to: UInt8.self),
                s.baseAddress!.assumingMemoryBound(to: UInt8.self)
            )
        }
    }
    guard rc1 == ENCHANT_SUCCESS else { return nil }

    let rc2 = pub.withUnsafeBytes { msgPtr in
        identityPrivateKey.withUnsafeBytes { keyPtr in
            sig.withUnsafeMutableBytes { sigPtr in
                enchant_ed25519_sign(
                    msgPtr.baseAddress!.assumingMemoryBound(to: UInt8.self), 32,
                    keyPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                    sigPtr.baseAddress!.assumingMemoryBound(to: UInt8.self)
                )
            }
        }
    }
    guard rc2 == ENCHANT_SUCCESS else { return nil }

    return SignedPrekeyBundle(publicKey: pub, privateKey: priv, signature: sig)
}
```

### Wire Format

```
signed_prekey_public:     32 bytes (X25519 public key)
signed_prekey_signature:  64 bytes (Ed25519 signature over signed_prekey_public)
```

The signature signs the raw 32-byte X25519 public key using the identity Ed25519 private key via XEdDSA.

---

## 4. One-Time Prekey Generation

Generate batches of X25519 one-time prekeys. Each is consumed once during X3DH and then deleted.

### Buffer Sizes

| Buffer | Size |
|--------|------|
| One-time prekey public | 32 bytes |
| One-time prekey private | 32 bytes |

### C API

```c
int enchant_x25519_keypair(uint8_t* public_key, uint8_t* private_key);
```

### Kotlin

```kotlin
data class OneTimePrekey(
    val id: Int,
    val publicKey: ByteArray,
    val privateKey: ByteArray
)

fun generateOneTimePrekeyBatch(startId: Int, count: Int = 100): List<OneTimePrekey> {
    return (0 until count).map { i ->
        val pub = ByteArray(32)
        val priv = ByteArray(32)
        val rc = NativeCrypto.x25519Keypair(pub, priv)
        if (rc != 0) throw EnchantCryptoException("OPK generation failed: $rc")
        OneTimePrekey(startId + i, pub, priv)
    }
}
```

### Swift

```swift
struct OneTimePrekey {
    let id: UInt32
    let publicKey: Data
    let privateKey: Data
}

func generateOneTimePrekeyBatch(startId: UInt32, count: Int = 100) -> [OneTimePrekey] {
    return (0..<count).compactMap { i in
        var pub = Data(count: 32)
        var priv = Data(count: 32)
        let rc = pub.withUnsafeMutableBytes { p in
            priv.withUnsafeMutableBytes { s in
                enchant_x25519_keypair(
                    p.baseAddress!.assumingMemoryBound(to: UInt8.self),
                    s.baseAddress!.assumingMemoryBound(to: UInt8.self)
                )
            }
        }
        guard rc == ENCHANT_SUCCESS else { return nil }
        return OneTimePrekey(id: startId + UInt32(i), publicKey: pub, privateKey: priv)
    }
}
```

### Wire Format

```
one_time_prekey_id:   4 bytes (uint32 big-endian)
one_time_prekey_public: 32 bytes (X25519 public key)
```

---

## 5. Key Bundle Registration

Upload the key bundle to the server. The server stores it and serves it to initiators.

### Bundle Contents

| Field | Size | Description |
|-------|------|-------------|
| identity_public | 32 bytes | Ed25519 identity public key |
| signed_prekey_public | 32 bytes | X25519 signed prekey public |
| signed_prekey_signature | 64 bytes | Ed25519 signature over SPK |
| one_time_prekey_public | 32 bytes | X25519 OPK public (optional) |
| kyber_prekey_public | 1184 bytes | ML-KEM-768 public (optional, for PQXDH) |
| registration_id | 2 bytes | 14-bit registration ID |
| device_id | varies | Device identifier |
| signed_prekey_id | 4 bytes | SPK identifier |
| kyber_prekey_id | 4 bytes | Kyber prekey identifier (optional) |

### Kotlin

```kotlin
data class KeyBundle(
    val identityPublic: ByteArray,      // 32
    val signedPrekeyPublic: ByteArray,  // 32
    val signedPrekeySignature: ByteArray, // 64
    val oneTimePrekeyPublic: ByteArray?, // 32 or null
    val kyberPrekeyPublic: ByteArray?,  // 1184 or null (ML-KEM-768)
    val registrationId: Int,
    val deviceId: Int,
    val signedPrekeyId: Int,
    val kyberPrekeyId: Int? = null,
    val oneTimePrekeyId: Int? = null
) {
    fun toByteArray(): ByteArray {
        // Serialize for server upload — use protobuf or custom format
        // See wire format below
    }
}
```

### Wire Format (Key Bundle Upload)

```
registration_id:       2 bytes (uint16, 14-bit)
identity_key:          32 bytes
signed_prekey:         32 bytes
signed_prekey_sig:     64 bytes
one_time_prekey_id:    4 bytes (0 if none)
one_time_prekey:       32 bytes (empty if none)
kyber_prekey_id:       4 bytes (0 if none)
kyber_prekey:          1184 bytes (empty if none, ML-KEM-768)
```

### Registration ID

The registration ID is a 14-bit value (0–16383) generated once per device installation. It's used to distinguish devices.

```kotlin
fun generateRegistrationId(): Int {
    return (Math.random() * 16384).toInt() and 0x3FFF
}
```

---

## 6. X3DH Key Exchange

### Initiate (Alice's side)

Alice calls this when she wants to start a session with Bob. She needs:
- Her identity private key (Ed25519 seed, 64 bytes)
- An ephemeral X25519 private key (32 bytes, generated fresh)
- Bob's identity public key (32 bytes)
- Bob's signed prekey public (32 bytes)
- Bob's one-time prekey public (32 bytes, optional)

### JNI

```
Java_org_enchant_core_crypto_NativeX3DH_x3dhInitiateNative(
    JNIEnv*, jclass,
    jbyteArray ourIdentityPrivate,    // Ed25519 seed, 64 bytes
    jbyteArray ourEphemeralPrivate,   // X25519, 32 bytes
    jbyteArray theirIdentityPublic,   // 32 bytes
    jbyteArray theirSignedPrekey,     // 32 bytes
    jobject theirOneTimePrekey,       // jbyteArray or null, 32 bytes
    jbyteArray sharedSecret,          // Output: 32 bytes
    jbyteArray rootKey,               // Output: 32 bytes
    jbyteArray sendingChainKey,       // Output: 32 bytes
    jbyteArray receivingChainKey,     // Output: 32 bytes
    jbyteArray pqrKey                 // Output: 32 bytes
) -> jint
```

### C API

No direct C API for X3DH — use the JNI wrappers or call the C++ functions via the protocol layer.

### Kotlin

```kotlin
data class X3DHResult(
    val sharedSecret: ByteArray,  // 32 bytes
    val rootKey: ByteArray,       // 32 bytes
    val sendingChainKey: ByteArray, // 32 bytes
    val receivingChainKey: ByteArray, // 32 bytes
    val pqrKey: ByteArray         // 32 bytes
)

fun x3dhInitiate(
    ourIdentityPrivate: ByteArray,  // 64 bytes (Ed25519 seed)
    ourEphemeralPrivate: ByteArray, // 32 bytes
    theirIdentityPublic: ByteArray, // 32 bytes
    theirSignedPrekey: ByteArray,   // 32 bytes
    theirOneTimePrekey: ByteArray?  // 32 bytes or null
): X3DHResult {
    val sharedSecret = ByteArray(32)
    val rootKey = ByteArray(32)
    val sendingChainKey = ByteArray(32)
    val receivingChainKey = ByteArray(32)
    val pqrKey = ByteArray(32)

    val rc = NativeX3DH.x3dhInitiateNative(
        ourIdentityPrivate,
        ourEphemeralPrivate,
        theirIdentityPublic,
        theirSignedPrekey,
        theirOneTimePrekey,  // nullable jbyteArray
        sharedSecret,
        rootKey,
        sendingChainKey,
        receivingChainKey,
        pqrKey
    )
    if (rc != 0) throw EnchantCryptoException("X3DH initiate failed: $rc")

    return X3DHResult(sharedSecret, rootKey, sendingChainKey, receivingChainKey, pqrKey)
}
```

### Respond (Bob's side)

Bob calls this when he receives a prekey message from Alice.

### JNI

```
Java_org_enchant_core_crypto_NativeX3DH_x3dhRespondNative(
    JNIEnv*, jclass,
    jbyteArray ourIdentityPrivate,       // 64 bytes
    jbyteArray ourSignedPrekeyPrivate,   // 32 bytes
    jobject ourOneTimePrekeyPrivate,     // 32 bytes or null
    jbyteArray theirIdentityPublic,      // 32 bytes
    jbyteArray theirEphemeralPublic,     // 32 bytes
    jbyteArray sharedSecret,             // Output: 32 bytes
    jbyteArray rootKey,                  // Output: 32 bytes
    jbyteArray sendingChainKey,          // Output: 32 bytes
    jbyteArray receivingChainKey,        // Output: 32 bytes
    jbyteArray pqrKey                    // Output: 32 bytes
) -> jint
```

### Kotlin

```kotlin
fun x3dhRespond(
    ourIdentityPrivate: ByteArray,       // 64 bytes
    ourSignedPrekeyPrivate: ByteArray,   // 32 bytes
    ourOneTimePrekeyPrivate: ByteArray?, // 32 bytes or null
    theirIdentityPublic: ByteArray,      // 32 bytes
    theirEphemeralPublic: ByteArray      // 32 bytes
): X3DHResult {
    val sharedSecret = ByteArray(32)
    val rootKey = ByteArray(32)
    val sendingChainKey = ByteArray(32)
    val receivingChainKey = ByteArray(32)
    val pqrKey = ByteArray(32)

    val rc = NativeX3DH.x3dhRespondNative(
        ourIdentityPrivate,
        ourSignedPrekeyPrivate,
        ourOneTimePrekeyPrivate,
        theirIdentityPublic,
        theirEphemeralPublic,
        sharedSecret,
        rootKey,
        sendingChainKey,
        receivingChainKey,
        pqrKey
    )
    if (rc != 0) throw EnchantCryptoException("X3DH respond failed: $rc")

    return X3DHResult(sharedSecret, rootKey, sendingChainKey, receivingChainKey, pqrKey)
}
```

### Swift

```swift
func x3dhInitiate(
    ourIdentityPrivate: Data,   // 64 bytes
    ourEphemeralPrivate: Data,  // 32 bytes
    theirIdentityPublic: Data,  // 32 bytes
    theirSignedPrekey: Data,    // 32 bytes
    theirOneTimePrekey: Data?   // 32 bytes or nil
) -> (sharedSecret: Data, rootKey: Data, sendingChainKey: Data, receivingChainKey: Data, pqrKey: Data)? {
    var sharedSecret = Data(count: 32)
    var rootKey = Data(count: 32)
    var sendingChainKey = Data(count: 32)
    var receivingChainKey = Data(count: 32)
    var pqrKey = Data(count: 32)

    let rc = ourIdentityPrivate.withUnsafeBytes { idPtr in
        ourEphemeralPrivate.withUnsafeBytes { ephPtr in
            theirIdentityPublic.withUnsafeBytes { theirIdPtr in
                theirSignedPrekey.withUnsafeBytes { spkPtr in
                    // Handle optional OPK
                    let opkPtr = theirOneTimePrekey?.withUnsafeBytes { $0 } ?? UnsafeRawBufferPointer(start: nil, count: 0)
                    return sharedSecret.withUnsafeMutableBytes { ssPtr in
                        rootKey.withUnsafeMutableBytes { rkPtr in
                            sendingChainKey.withUnsafeMutableBytes { skPtr in
                                receivingChainKey.withUnsafeMutableBytes { rk2Ptr in
                                    pqrKey.withUnsafeMutableBytes { pqPtr in
                                        enchant_x3dh_initiate(
                                            idPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                                            ephPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                                            theirIdPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                                            spkPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                                            opkPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                                            ssPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                                            rkPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                                            skPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                                            rk2Ptr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                                            pqPtr.baseAddress!.assumingMemoryBound(to: UInt8.self)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    guard rc == ENCHANT_SUCCESS else { return nil }
    return (sharedSecret, rootKey, sendingChainKey, receivingChainKey, pqrKey)
}
```

### Wire Format (Prekey Message)

```
// Sent as the first message to establish a session
identity_key:           32 bytes
ephemeral_public:       32 bytes
signed_prekey_id:       4 bytes (uint32)
one_time_prekey_id:     4 bytes (uint32, 0 if unused)
base_message:           [header + encrypted payload]
```

---

## 7. PQXDH Post-Quantum Key Exchange

PQXDH extends X3DH with ML-KEM (Kyber) for post-quantum security. The initiator encapsulates a shared secret using the recipient's ML-KEM public key.

### Buffer Sizes

| Buffer | Size |
|--------|------|
| ML-KEM-768 public key | 1184 bytes |
| ML-KEM-768 secret key | 2400 bytes |
| ML-KEM-768 ciphertext | 1088 bytes |
| ML-KEM-1024 public key | 1568 bytes |
| ML-KEM-1024 secret key | 3168 bytes |
| ML-KEM-1024 ciphertext | 1568 bytes |

### JNI — PQXDH Initiate

```
Java_org_enchant_core_crypto_NativeX3DH_pqxdhInitiateNative(
    JNIEnv*, jclass,
    jbyteArray ourIdentityPrivate,       // 64 bytes
    jbyteArray ourEphemeralPrivate,      // 32 bytes
    jbyteArray theirIdentityPublic,      // 32 bytes
    jbyteArray theirSignedPrekey,        // 32 bytes
    jbyteArray theirKyberPrekey,         // 1184 bytes (ML-KEM-768)
    jobject theirOneTimePrekey,          // 32 bytes or null
    jbyteArray rootKey,                  // Output: 32 bytes
    jbyteArray chainKey,                 // Output: 32 bytes
    jbyteArray pqrKey,                   // Output: 32 bytes
    jbyteArray kyberCiphertext,          // Output: 1088 bytes
    jbyteArray sharedSecret              // Output: 32 bytes
) -> jint
```

### JNI — PQXDH Respond

```
Java_org_enchant_core_crypto_NativeX3DH_pqxdhRespondNative(
    JNIEnv*, jclass,
    jbyteArray ourIdentityPrivate,       // 64 bytes
    jbyteArray ourSignedPrekeyPrivate,   // 32 bytes
    jobject ourOneTimePrekeyPrivate,     // 32 bytes or null
    jbyteArray ourKyberPrekeyPrivate,    // 2400 bytes
    jbyteArray theirIdentityPublic,      // 32 bytes
    jbyteArray theirEphemeralPublic,     // 32 bytes
    jbyteArray theirKyberCiphertext,     // 1088 bytes
    jbyteArray rootKey,                  // Output: 32 bytes
    jbyteArray chainKey,                 // Output: 32 bytes
    jbyteArray pqrKey,                   // Output: 32 bytes
    jbyteArray sharedSecret              // Output: 32 bytes
) -> jint
```

### Kotlin

```kotlin
data class PQXDHResult(
    val rootKey: ByteArray,              // 32 bytes
    val chainKey: ByteArray,             // 32 bytes
    val pqrKey: ByteArray,               // 32 bytes
    val kyberCiphertext: ByteArray,      // 1088 bytes (ML-KEM-768)
    val sharedSecret: ByteArray          // 32 bytes
)

fun pqxdhInitiate(
    ourIdentityPrivate: ByteArray,       // 64 bytes
    ourEphemeralPrivate: ByteArray,      // 32 bytes
    theirIdentityPublic: ByteArray,      // 32 bytes
    theirSignedPrekey: ByteArray,        // 32 bytes
    theirKyberPrekey: ByteArray,         // 1184 bytes
    theirOneTimePrekey: ByteArray?       // 32 bytes or null
): PQXDHResult {
    val rootKey = ByteArray(32)
    val chainKey = ByteArray(32)
    val pqrKey = ByteArray(32)
    val kyberCt = ByteArray(1088)
    val sharedSecret = ByteArray(32)

    val rc = NativeX3DH.pqxdhInitiateNative(
        ourIdentityPrivate, ourEphemeralPrivate,
        theirIdentityPublic, theirSignedPrekey,
        theirKyberPrekey, theirOneTimePrekey,
        rootKey, chainKey, pqrKey, kyberCt, sharedSecret
    )
    if (rc != 0) throw EnchantCryptoException("PQXDH initiate failed: $rc")

    return PQXDHResult(rootKey, chainKey, pqrKey, kyberCt, sharedSecret)
}
```

### Wire Format (PQXDH Prekey Message)

```
identity_key:           32 bytes
ephemeral_public:       32 bytes
signed_prekey_id:       4 bytes
kyber_prekey_id:        4 bytes
one_time_prekey_id:     4 bytes (0 if unused)
kyber_ciphertext:       1088 bytes (ML-KEM-768)
base_message:           [header + encrypted payload]
```

The HKDF info string for PQXDH is `"EnvelopeText_X25519_SHA-256_ML-KEM-768"`.

---

## 8. Double Ratchet Session Encrypt/Decrypt

After X3DH establishes the initial keys, the Double Ratchet provides per-message encryption.

### Key Derivation

From X3DH result:
1. `root_key` — 32 bytes, used for chain key derivation
2. `sending_chain_key` — 32 bytes, Alice's sending chain
3. `receiving_chain_key` — 32 bytes, Bob's receiving chain

The ratchet derives message keys from chain keys using HKDF-SHA256 with info `"EnvelopeRatchet"`.

### Encryption Flow

1. Derive message key from current sending chain key
2. Encrypt with XChaCha20-Poly1305
3. Ratchet the sending chain forward

### Decryption Flow

1. Determine which chain key and iteration to use (from message header)
2. Skip forward if needed (up to 2000 keys)
3. Decrypt with XChaCha20-Poly1305

### C API

```c
int enchant_hkdf_sha256(const uint8_t* ikm, size_t ikm_len,
                        const uint8_t* salt, size_t salt_len,
                        const uint8_t* info, size_t info_len,
                        uint8_t* okm, size_t okm_len);

int enchant_xchacha20_encrypt(const uint8_t* plaintext, size_t plaintext_len,
                              const uint8_t* key, const uint8_t* nonce,
                              uint8_t* ciphertext, size_t ciphertext_capacity);

int enchant_xchacha20_decrypt(const uint8_t* ciphertext, size_t ciphertext_len,
                              const uint8_t* key, const uint8_t* nonce,
                              uint8_t* plaintext, size_t plaintext_capacity);
```

### Kotlin

```kotlin
object DoubleRatchet {
    const val KEY_SIZE = 32
    const val NONCE_SIZE = 24
    const val MAX_SKIP = 2000

    fun deriveMessageKey(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        // chain_key -> HMAC-SHA256(chain_key, 0x01) -> message_key
        // chain_key -> HMAC-SHA256(chain_key, 0x02) -> next_chain_key
        val msgKey = hmacSha256(chainKey, byteArrayOf(0x01))
        val nextChainKey = hmacSha256(chainKey, byteArrayOf(0x02))
        return Pair(msgKey, nextChainKey)
    }

    fun deriveKeyMaterial(rootKey: ByteArray, dhOutput: ByteArray): Pair<ByteArray, ByteArray> {
        // HKDF-SHA256(dhOutput, salt=root_key, info="EnvelopeRatchet", len=64)
        // -> root_key || chain_key
        val derived = ByteArray(64)
        val rc = NativeCrypto.hkdfSha256(
            dhOutput, dhOutput.size,
            rootKey, rootKey.size,
            "EnvelopeRatchet".toByteArray(), "EnvelopeRatchet".length,
            derived, 64
        )
        if (rc != 0) throw EnchantCryptoException("HKDF derive failed: $rc")
        return Pair(derived.sliceArray(0..31), derived.sliceArray(32..63))
    }

    fun ratchetEncrypt(
        rootKey: ByteArray,
        sendingChainKey: ByteArray,
        plaintext: ByteArray,
        ourRatchetPublic: ByteArray,
        ourRatchetPrivate: ByteArray,
        theirRatchetPublic: ByteArray
    ): Triple<ByteArray, ByteArray, ByteArray> {
        // DH(our_ratchet_private, their_ratchet_public) -> new DH output
        val dhOutput = ByteArray(32)
        NativeCrypto.x25519Dh(ourRatchetPrivate, theirRatchetPublic, dhOutput)

        // Derive new root key and sending chain key
        val (newRootKey, newSendingChainKey) = deriveKeyMaterial(rootKey, dhOutput)

        // Derive message key from sending chain
        val (msgKey, _) = deriveMessageKey(newSendingChainKey)

        // Generate random nonce
        val nonce = ByteArray(24)
        NativeCrypto.randomBytes(nonce, 24)

        // Encrypt
        val ciphertext = ByteArray(plaintext.size + 16)
        val rc = NativeCrypto.xchacha20Encrypt(plaintext, plaintext.size, msgKey, nonce, ciphertext, ciphertext.size)
        if (rc != 0) throw EnchantCryptoException("Encrypt failed: $rc")

        return Triple(newRootKey, newSendingChainKey, ciphertext)
    }

    fun ratchetDecrypt(
        rootKey: ByteArray,
        receivingChainKey: ByteArray,
        ciphertext: ByteArray,
        ourRatchetPublic: ByteArray,
        ourRatchetPrivate: ByteArray,
        theirRatchetPublic: ByteArray
    ): Triple<ByteArray, ByteArray, ByteArray> {
        // Same derivation as encrypt but on receiving side
        val dhOutput = ByteArray(32)
        NativeCrypto.x25519Dh(ourRatchetPrivate, theirRatchetPublic, dhOutput)

        val (newRootKey, newReceivingChainKey) = deriveKeyMaterial(rootKey, dhOutput)
        val (msgKey, _) = deriveMessageKey(newReceivingChainKey)

        // Strip 24-byte nonce from ciphertext
        val nonce = ciphertext.sliceArray(0..23)
        val actualCiphertext = ciphertext.sliceArray(24 until ciphertext.size)

        val plaintext = ByteArray(actualCiphertext.size - 16)
        val rc = NativeCrypto.xchacha20Decrypt(actualCiphertext, actualCiphertext.size, msgKey, nonce, plaintext, plaintext.size)
        if (rc != 0) throw EnchantCryptoException("Decrypt failed: $rc")

        return Triple(newRootKey, newReceivingChainKey, plaintext)
    }
}
```

### Wire Format (Message Header)

```
// Double Ratchet message header
dh_public_key:              32 bytes (ratchet public key)
previous_chain_length:      4 bytes (uint32)
message_number:             4 bytes (uint32)
previous_message_number:    4 bytes (uint32)
```

### Envelope Wire Format

```
// For existing session (WHISPER_MESSAGE)
[header_size(4) | header | ciphertext]

// For new session (PREKEY_MESSAGE)
[ik_size(4) | ik(32) | ek_size(4) | ek(32) | spk_id(4) | opk_id(4) |
 header_size(4) | header | ciphertext]

// ciphertext = nonce(24) + xchacha20_poly1305(plaintext)
```

---

## 9. Sealed Sender

Sealed sender provides anonymous message delivery. The sender encrypts the entire message so only the recipient can identify the sender.

### Flow

1. Alice builds an `UnidentifiedSenderMessageContent` (USMC) with her sender certificate
2. Alice calls `sealed_sender_encrypt_from_usmc()` which:
   - Generates ephemeral X25519 key pair
   - Computes ECDH with recipient's identity public key
   - Encrypts sender's identity with the derived key
   - Encrypts the USMC with a second derived key
3. The sealed message is sent to the server, which cannot read it
4. Bob calls `sealed_sender_decrypt_to_usmc()` which reverses the process

### JNI

No direct JNI wrappers for sealed sender — these are internal C++ functions. The JNI layer should expose `sealedSenderEncrypt` and `sealedSenderDecrypt` as higher-level wrappers.

### Kotlin

```kotlin
object SealedSender {
    const val V1_VERSION: Byte = 0x11
    const val V2_UUID_VERSION: Byte = 0x22
}

data class SealedSenderResult(
    val ciphertext: ByteArray,
    val success: Boolean
)

// High-level sealed sender encryption
// In practice, you'd build the USMC protobuf first, then encrypt
fun sealedSenderEncrypt(
    ourIdentityPrivate: ByteArray,  // 64 bytes
    ourIdentityPublic: ByteArray,   // 32 bytes
    theirIdentityPublic: ByteArray, // 32 bytes
    usmcData: ByteArray             // Serialized USMC protobuf
): SealedSenderResult {
    // Generate ephemeral key pair
    val ephPrivate = ByteArray(32)
    val ephPublic = ByteArray(32)
    NativeCrypto.x25519Keypair(ephPublic, ephPrivate)

    // ECDH with recipient
    val ecdhShared = ByteArray(32)
    NativeCrypto.x25519Dh(ephPrivate, theirIdentityPublic, ecdhShared)

    // Derive keys: chain_key || cipher_key || mac_key (96 bytes)
    val salt = buildSealedSenderSalt(ephPublic, theirIdentityPublic, Direction.Sending)
    val derived = ByteArray(96)
    NativeCrypto.hkdfSha256(ecdhShared, 32, salt, salt.size, byteArrayOf(), 0, derived, 96)

    val chainKey = derived.sliceArray(0..31)
    val cipherKey = derived.sliceArray(32..63)
    val macKey = derived.sliceArray(64..95)

    // Encrypt sender identity
    val senderIdentityCt = ByteArray(32 + 16)  // 32 + Poly1305 tag
    val zeroNonce = ByteArray(24)
    NativeCrypto.xchacha20Encrypt(ourIdentityPublic, 32, cipherKey, zeroNonce, senderIdentityCt, senderIdentityCt.size)

    // Static key derivation
    val staticSalt = chainKey + senderIdentityCt
    val staticShared = ByteArray(32)
    NativeCrypto.x25519Dh(ourIdentityPrivate, theirIdentityPublic, staticShared)
    val staticDerived = ByteArray(64)
    NativeCrypto.hkdfSha256(staticShared, 32, staticSalt, staticSalt.size, byteArrayOf(), 0, staticDerived, 64)

    val staticCipherKey = staticDerived.sliceArray(0..31)
    val staticMacKey = staticDerived.sliceArray(32..63)

    // Encrypt USMC
    val msgNonce = ByteArray(24)
    NativeCrypto.randomBytes(msgNonce, 24)
    val msgCiphertext = ByteArray(usmcData.size + 16)
    NativeCrypto.xchacha20Encrypt(usmcData, usmcData.size, staticCipherKey, msgNonce, msgCiphertext, msgCiphertext.size)

    // Compute MAC
    val macData = msgNonce + msgCiphertext + staticMacKey
    val mac = hmacSha256(staticMacKey, macData)

    // Build output: version(1) + eph_public(32) + enc_static(32+16) + static_mac(32) + encrypted_message
    val output = mutableListOf<Byte>()
    output.add(SealedSender.V1_VERSION)
    output.addAll(ephPublic.toList())
    output.addAll(senderIdentityCt.toList())
    output.addAll(mac.toList())
    output.addAll(msgNonce.toList())
    output.addAll(msgCiphertext.toList())

    return SealedSenderResult(output.toByteArray(), true)
}

private fun buildSealedSenderSalt(
    ephPublic: ByteArray,
    theirPublic: ByteArray,
    direction: Direction
): ByteArray {
    val prefix = "UnidentifiedDelivery".toByteArray()
    return when (direction) {
        Direction.Sending -> prefix + theirPublic + ephPublic
        Direction.Receiving -> prefix + ephPublic + theirPublic
    }
}
```

### Wire Format (Sealed Sender V1)

```
version:                1 byte (0x11)
ephemeral_public:       32 bytes (X25519)
encrypted_static:       32 bytes (encrypted sender identity)
static_mac:             32 bytes (HMAC-SHA256)
encrypted_message:      [nonce(24) | xchacha20_ct | poly1305_tag(16) | hmac(32)]
```

### Wire Format (Sealed Sender V2)

```
version:                1 byte (0x22 for UUID, 0x23 for service ID)
ephemeral_public:       32 bytes
encrypted_static:       variable
encrypted_message:      variable
```

---

## 10. Sender Key — Group Messaging

Sender keys provide efficient group encryption. Each sender has a chain key that ratchets forward with each message.

### Constants

| Constant | Value |
|----------|-------|
| SENDER_KEY_SEED_SIZE | 32 |
| SENDER_KEY_IV_SIZE | 16 |
| SENDER_KEY_CIPHER_KEY_SIZE | 32 |
| SENDER_KEY_MAX_FORWARD_JUMPS | 2000 |
| SENDER_KEY_MAX_MESSAGE_KEYS | 2000 |

### JNI — Create Sender Key

```
Java_org_enchant_core_crypto_NativeSenderKey_createSenderKeyNative(
    JNIEnv*, jclass,
    jstring senderId,       // Sender identifier string
    jint keyId              // Key identifier
) -> jlong  // Returns opaque handle
```

### JNI — Encrypt with Sender Key

```
Java_org_enchant_core_crypto_NativeSenderKey_encryptSenderKeyNative(
    JNIEnv*, jclass,
    jlong stateHandle,      // Handle from createSenderKeyNative
    jbyteArray plaintext,   // Message to encrypt
    jint plaintextLen,      // Plaintext length
    jbyteArray output,      // Output buffer
    jintArray outputLen     // In: capacity, Out: actual length
) -> jint
```

### JNI — Decrypt with Sender Key

```
Java_org_enchant_core_crypto_NativeSenderKey_decryptSenderKeyNative(
    JNIEnv*, jclass,
    jlong stateHandle,      // Handle from processDistributionMessage
    jbyteArray ciphertext,  // Encrypted message
    jint ciphertextLen,     // Ciphertext length
    jbyteArray plaintext,   // Output buffer
    jintArray plaintextLen  // In: capacity, Out: actual length
) -> jint
```

### JNI — Get Sender Key State

```
Java_org_enchant_core_crypto_NativeSenderKey_getSenderKeyStateNative(
    JNIEnv*, jclass,
    jlong stateHandle,      // Handle
    jbyteArray chainKey,    // Output: 32 bytes
    jintArray iteration,    // Output: current iteration
    jintArray epoch         // Output: current epoch
) -> jint
```

### JNI — Destroy Sender Key

```
Java_org_enchant_core_crypto_NativeSenderKey_destroySenderKeyNative(
    JNIEnv*, jclass,
    jlong stateHandle
) -> void
```

### Kotlin

```kotlin
class SenderKeyManager {
    private var stateHandle: Long = 0

    fun create(senderId: String, keyId: Int) {
        stateHandle = NativeSenderKey.createSenderKeyNative(senderId, keyId)
        if (stateHandle == 0L) throw EnchantCryptoException("Sender key creation failed")
    }

    fun encrypt(plaintext: ByteArray): ByteArray {
        val outputLen = intArrayOf(plaintext.size + 4 + 16)
        val output = ByteArray(plaintext.size + 4 + 16)

        val rc = NativeSenderKey.encryptSenderKeyNative(
            stateHandle, plaintext, plaintext.size, output, outputLen
        )
        if (rc != 0) throw EnchantCryptoException("Sender key encrypt failed: $rc")

        return output.copyOfRange(0, outputLen[0])
    }

    fun decrypt(ciphertext: ByteArray): ByteArray {
        val plaintextLen = intArrayOf(ciphertext.size)
        val plaintext = ByteArray(ciphertext.size)

        val rc = NativeSenderKey.decryptSenderKeyNative(
            stateHandle, ciphertext, ciphertext.size, plaintext, plaintextLen
        )
        if (rc != 0) throw EnchantCryptoException("Sender key decrypt failed: $rc")

        return plaintext.copyOfRange(0, plaintextLen[0])
    }

    fun destroy() {
        if (stateHandle != 0L) {
            NativeSenderKey.destroySenderKeyNative(stateHandle)
            stateHandle = 0
        }
    }
}
```

### Swift

```swift
class SenderKeyManager {
    private var stateHandle: Int64 = 0

    func create(senderId: String, keyId: UInt32) {
        stateHandle = senderId.withCString { cStr in
            NativeSenderKey_createSenderKeyNative(cStr, Int32(keyId))
        }
        guard stateHandle != 0 else {
            fatalError("Sender key creation failed")
        }
    }

    func encrypt(plaintext: Data) -> Data? {
        var output = Data(count: plaintext.count + 4 + 16)
        var outputLen = Int32(plaintext.count + 4 + 16)

        let rc = plaintext.withUnsafeBytes { ptPtr in
            output.withUnsafeMutableBytes { outPtr in
                NativeSenderKey_encryptSenderKeyNative(
                    stateHandle,
                    ptPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                    Int32(plaintext.count),
                    outPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                    &outputLen
                )
            }
        }
        guard rc == ENCHANT_SUCCESS else { return nil }
        return output.prefix(Int(outputLen))
    }

    func decrypt(ciphertext: Data) -> Data? {
        var plaintext = Data(count: ciphertext.count)
        var plaintextLen = Int32(ciphertext.count)

        let rc = ciphertext.withUnsafeBytes { ctPtr in
            plaintext.withUnsafeMutableBytes { ptPtr in
                NativeSenderKey_decryptSenderKeyNative(
                    stateHandle,
                    ctPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                    Int32(ciphertext.count),
                    ptPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                    &plaintextLen
                )
            }
        }
        guard rc == ENCHANT_SUCCESS else { return nil }
        return plaintext.prefix(Int(plaintextLen))
    }

    deinit {
        if stateHandle != 0 {
            NativeSenderKey_destroySenderKeyNative(stateHandle)
        }
    }
}
```

### Wire Format (Sender Key Encrypted Message)

```
iteration:      4 bytes (uint32, little-endian)
ciphertext:     xchacha20_poly1305(plaintext, cipher_key, nonce)
// nonce = 16 bytes of zeros || 8-byte IV from message key derivation
```

---

## 11. Sender Key Distribution Messages

The distribution message sends the sender key state to group members. It's signed with the sender's Ed25519 key.

### JNI — Create Distribution Message

```
Java_org_enchant_core_crypto_NativeSenderKey_createDistributionMessageNative(
    JNIEnv*, jclass,
    jlong stateHandle,          // Sender key handle
    jbyteArray signingPrivate,  // Ed25519 private seed, 64 bytes
    jbyteArray output,          // Output buffer
    jintArray outputLen         // In: capacity, Out: actual length
) -> jint
```

### JNI — Process Distribution Message

```
Java_org_enchant_core_crypto_NativeSenderKey_processDistributionMessageNative(
    JNIEnv*, jclass,
    jlong stateHandle,          // Sender key handle
    jbyteArray message,         // Distribution message bytes
    jint messageLen,            // Message length
    jbyteArray signingPublic    // Sender's Ed25519 public key, 32 bytes
) -> jint
```

### Kotlin

```kotlin
fun createDistributionMessage(
    stateHandle: Long,
    signingPrivateKey: ByteArray  // 64 bytes
): ByteArray {
    val output = ByteArray(1024) // Distribution messages are small
    val outputLen = intArrayOf(1024)

    val rc = NativeSenderKey.createDistributionMessageNative(
        stateHandle, signingPrivateKey, output, outputLen
    )
    if (rc != 0) throw EnchantCryptoException("Create distribution message failed: $rc")

    return output.copyOfRange(0, outputLen[0])
}

fun processDistributionMessage(
    stateHandle: Long,
    message: ByteArray,
    signingPublicKey: ByteArray  // 32 bytes
) {
    val rc = NativeSenderKey.processDistributionMessageNative(
        stateHandle, message, message.size, signingPublicKey
    )
    if (rc != 0) throw EnchantCryptoException("Process distribution message failed: $rc")
}
```

### Wire Format (Distribution Message)

```
sender_key_id:      4 bytes (uint32)
epoch:              4 bytes (uint32)
iteration:          4 bytes (uint32)
chain_key:          32 bytes
signature:          64 bytes (Ed25519 over bytes[0..75])
```

Total: 108 bytes

---

## 12. GroupsV2 (MLS TreeKEM)

GroupsV2 uses MLS-inspired epoch-based key management with TreeKEM for efficient key updates.

### Constants

| Constant | Value |
|----------|-------|
| GROUPS_V2_GROUP_ID_SIZE | 32 |
| GROUPS_V2_EPOCH_SECRET_SIZE | 32 |
| GROUPS_V2_MEMBER_ID_SIZE | 32 |
| GROUPS_V2_MAX_MEMBERS | 256 |
| MLS_TREE_KEM_NODE_SIZE | 32 |
| MLS_TREE_KEM_PATH_SECRET_SIZE | 32 |
| MLS_TREE_KEM_HMAC_SIZE | 32 |
| MLS_TREE_KEM_GROUP_SECRET_SIZE | 32 |

### JNI — GroupsV2

```
// Create/Destroy
NativeGroupsV2_createNative() -> jlong
NativeGroupsV2_destroyNative(jlong handle)

// Create Group
NativeGroupsV2_createGroupNative(
    jlong handle,
    jbyteArray creatorId,       // 32 bytes
    jbyteArray creatorSecret,   // 32 bytes
    jstring title,
    jbyteArray groupIdOut,      // Output: 32 bytes
    jbyteArray epochSecretOut   // Output: 32 bytes
) -> jint

// Add Member
NativeGroupsV2_addMemberNative(
    jlong handle,
    jbyteArray groupIdIn,       // 32 bytes
    jbyteArray epochSecretIn,   // 32 bytes
    jbyteArray newMemberId,     // 32 bytes
    jbyteArray newMemberSecret, // 32 bytes
    jbyteArray commitEpochOut   // Output: 32 bytes
) -> jint

// Remove Member
NativeGroupsV2_removeMemberNative(
    jlong handle,
    jbyteArray groupIdIn,       // 32 bytes
    jbyteArray epochSecretIn,   // 32 bytes
    jbyteArray targetMemberId,  // 32 bytes
    jbyteArray commitEpochOut   // Output: 32 bytes
) -> jint

// Apply Commit
NativeGroupsV2_applyCommitNative(
    jlong handle,
    jbyteArray groupIdIn,       // 32 bytes
    jbyteArray epochSecretIn,   // 32 bytes
    jbyteArray commitEpochIn,   // 32 bytes
    jbyteArray epochSecretOut   // Output: 32 bytes
) -> jint

// Get Member Count
NativeGroupsV2_getMemberCountNative(
    jlong handle,
    jbyteArray groupIdIn,
    jbyteArray epochSecretIn
) -> jint

// Update Member Key
NativeGroupsV2_updateMemberKeyNative(
    jlong handle,
    jbyteArray groupIdIn,
    jbyteArray epochSecretIn,
    jbyteArray memberId,
    jbyteArray newSecret,
    jbyteArray commitEpochOut
) -> jint
```

### JNI — MLS TreeKEM

```
// Create/Destroy
NativeMlsTreeKEM_createNative() -> jlong
NativeMlsTreeKEM_destroyNative(jlong handle)

// Initialize with leaf secrets
NativeMlsTreeKEM_initializeNative(
    jlong handle,
    jobjectArray leafSecrets    // Array of byte[32]
) -> jint

// Add member
NativeMlsTreeKEM_addMemberNative(
    jlong handle,
    jbyteArray leafSecret,      // 32 bytes
    jbyteArray newIndex         // Output: 32 bytes
) -> jint

// Remove member
NativeMlsTreeKEM_removeMemberNative(
    jlong handle,
    jint leafIndex
) -> jint

// Update leaf key
NativeMlsTreeKEM_updateLeafKeyNative(
    jlong handle,
    jint leafIndex,
    jbyteArray newSecret,       // 32 bytes
    jbyteArray directPathOut    // Output: variable
) -> jint

// Encrypt path
NativeMlsTreeKEM_encryptPathNative(
    jlong handle,
    jbyteArray directPathIn,
    jint directPathLen,
    jint senderLeafIndex,
    jbyteArray groupSecret      // Output: 32 bytes
) -> jint

// Decrypt path
NativeMlsTreeKEM_decryptPathNative(
    jlong handle,
    jbyteArray directPathIn,
    jint directPathLen,
    jint receiverLeafIndex,
    jbyteArray groupSecret      // Output: 32 bytes
) -> jint

// Get tree info
NativeMlsTreeKEM_leafCountNative(jlong handle) -> jint
NativeMlsTreeKEM_nodeCountNative(jlong handle) -> jint
NativeMlsTreeKEM_computeTreeHashNative(jlong handle, jbyteArray rootHash) -> jint
NativeMlsTreeKEM_getRootPublicKeyNative(jlong handle, jbyteArray publicKey) -> jint
NativeMlsTreeKEM_getNodePublicKeyNative(jlong handle, jint nodeIndex, jbyteArray publicKey) -> jint
NativeMlsTreeKEM_setNodePublicKeyNative(jlong handle, jint nodeIndex, jbyteArray publicKey) -> jint
NativeMlsTreeKEM_setNodePrivateKeyNative(jlong handle, jint nodeIndex, jbyteArray privateKey) -> jint
```

### Kotlin

```kotlin
class GroupsV2Manager {
    private var handle: Long = 0

    fun create() {
        handle = NativeGroupsV2.createNative()
        if (handle == 0L) throw EnchantCryptoException("GroupsV2 creation failed")
    }

    fun createGroup(
        creatorId: ByteArray,     // 32 bytes
        creatorSecret: ByteArray, // 32 bytes
        title: String
    ): Pair<ByteArray, ByteArray> {
        val groupId = ByteArray(32)
        val epochSecret = ByteArray(32)

        val rc = NativeGroupsV2.createGroupNative(
            handle, creatorId, creatorSecret, title, groupId, epochSecret
        )
        if (rc != 0) throw EnchantCryptoException("Create group failed: $rc")

        return Pair(groupId, epochSecret)
    }

    fun addMember(
        groupId: ByteArray,
        epochSecret: ByteArray,
        newMemberId: ByteArray,
        newMemberSecret: ByteArray
    ): ByteArray {
        val commitEpoch = ByteArray(32)

        val rc = NativeGroupsV2.addMemberNative(
            handle, groupId, epochSecret, newMemberId, newMemberSecret, commitEpoch
        )
        if (rc != 0) throw EnchantCryptoException("Add member failed: $rc")

        return commitEpoch
    }

    fun removeMember(
        groupId: ByteArray,
        epochSecret: ByteArray,
        targetMemberId: ByteArray
    ): ByteArray {
        val commitEpoch = ByteArray(32)

        val rc = NativeGroupsV2.removeMemberNative(
            handle, groupId, epochSecret, targetMemberId, commitEpoch
        )
        if (rc != 0) throw EnchantCryptoException("Remove member failed: $rc")

        return commitEpoch
    }

    fun destroy() {
        if (handle != 0L) {
            NativeGroupsV2.destroyNative(handle)
            handle = 0
        }
    }
}
```

### Swift

```swift
class GroupsV2Manager {
    private var handle: Int64 = 0

    func create() {
        handle = NativeGroupsV2_createNative()
        guard handle != 0 else { fatalError("GroupsV2 creation failed") }
    }

    func createGroup(creatorId: Data, creatorSecret: Data, title: String) -> (groupId: Data, epochSecret: Data)? {
        var groupId = Data(count: 32)
        var epochSecret = Data(count: 32)

        let rc = title.withCString { titlePtr in
            creatorId.withUnsafeBytes { cidPtr in
                creatorSecret.withUnsafeBytes { csecPtr in
                    groupId.withUnsafeMutableBytes { gidPtr in
                        epochSecret.withUnsafeMutableBytes { epPtr in
                            NativeGroupsV2_createGroupNative(
                                handle,
                                cidPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                                csecPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                                titlePtr,
                                gidPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                                epPtr.baseAddress!.assumingMemoryBound(to: UInt8.self)
                            )
                        }
                    }
                }
            }
        }
        guard rc == ENCHANT_SUCCESS else { return nil }
        return (groupId, epochSecret)
    }

    deinit {
        if handle != 0 {
            NativeGroupsV2_destroyNative(handle)
        }
    }
}
```

---

## 13. Group State Serialization

### JNI

```
// Serialize
NativeGroupsV2_serializeGroupStateNative(
    jlong handle,
    jbyteArray groupIdIn,
    jbyteArray epochSecretIn,
    jbyteArray output
) -> jint

// Deserialize
NativeGroupsV2_deserializeGroupStateNative(
    jlong handle,
    jbyteArray data,
    jint dataLen,
    jbyteArray groupIdOut,
    jbyteArray epochSecretOut
) -> jint
```

### Kotlin

```kotlin
fun serializeGroupState(groupId: ByteArray, epochSecret: ByteArray): ByteArray {
    val output = ByteArray(4096)
    val rc = NativeGroupsV2.serializeGroupStateNative(handle, groupId, epochSecret, output)
    if (rc != 0) throw EnchantCryptoException("Serialize group state failed: $rc")
    return output
}

fun deserializeGroupState(data: ByteArray): Pair<ByteArray, ByteArray> {
    val groupId = ByteArray(32)
    val epochSecret = ByteArray(32)
    val rc = NativeGroupsV2.deserializeGroupStateNative(handle, data, data.size, groupId, epochSecret)
    if (rc != 0) throw EnchantCryptoException("Deserialize group state failed: $rc")
    return Pair(groupId, epochSecret)
}
```

### Wire Format (Group State Serialization)

```
group_id:           32 bytes
epoch:              4 bytes (uint32)
title_len:          4 bytes (uint32)
title:              variable
member_count:       4 bytes (uint32)
// For each member:
  member_id:        32 bytes
  leaf_index:       4 bytes (uint32)
  is_admin:         1 byte (bool)
  leaf_secret:      32 bytes
epoch_secret:       32 bytes
```

---

## 14. ZK Profile Key Credential — Show UUID

Proves possession of a UUID and profile key without revealing them, using zero-knowledge proofs.

### Constants

| Constant | Value |
|----------|-------|
| CLIENT_ZK_UUID_SIZE | 16 |
| CLIENT_ZK_PROFILE_KEY_SIZE | 32 |

### JNI — Show UUID from Credential

```
Java_org_enchant_core_crypto_NativeClientZkProfile_showUuidFromCredentialNative(
    JNIEnv*, jclass,
    jlong handle,
    jbyteArray credential,      // ProfileKeyCredential struct
    jbyteArray uuid,            // 16 bytes
    jbyteArray profileKey,      // 32 bytes
    jbyteArray randomness,      // Randomness for proof
    jbyteArray presentationOut  // Output: ProfileKeyCredentialPresentation
) -> jint
```

### Kotlin

```kotlin
fun showUuidFromCredential(
    credential: ByteArray,
    uuid: ByteArray,           // 16 bytes
    profileKey: ByteArray,     // 32 bytes
    randomness: ByteArray
): ByteArray {
    val presentation = ByteArray(1024) // Presentation is variable-sized

    val rc = NativeClientZkProfile.showUuidFromCredentialNative(
        handle, credential, uuid, profileKey, randomness, presentation
    )
    if (rc != 0) throw EnchantCryptoException("Show UUID failed: $rc")

    return presentation
}
```

### Swift

```swift
func showUuidFromCredential(
    credential: Data,
    uuid: Data,            // 16 bytes
    profileKey: Data,      // 32 bytes
    randomness: Data
) -> Data? {
    var presentation = Data(count: 1024)

    let rc = credential.withUnsafeBytes { credPtr in
        uuid.withUnsafeBytes { uuidPtr in
            profileKey.withUnsafeBytes { pkPtr in
                randomness.withUnsafeBytes { randPtr in
                    presentation.withUnsafeMutableBytes { presPtr in
                        NativeClientZkProfile_showUuidFromCredentialNative(
                            handle,
                            credPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                            uuidPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                            pkPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                            randPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                            presPtr.baseAddress!.assumingMemoryBound(to: UInt8.self)
                        )
                    }
                }
            }
        }
    }
    guard rc == ENCHANT_SUCCESS else { return nil }
    return presentation
}
```

---

## 15. ZK Auth Credential — Prove Group Membership

Auth credentials prove membership in a group without revealing the user's identity. The server issues an auth credential during registration.

### JNI — Initialize ClientZkProfile

```
Java_org_enchant_core_crypto_NativeClientZkProfile_initNative(
    JNIEnv*, jclass,
    jlong handle,
    jbyteArray serverPublicParams,  // Server public params struct
    jbyteArray groupSecretParams    // Group secret params struct
) -> jint
```

### Kotlin

```kotlin
class ClientZkProfileManager {
    private var handle: Long = 0

    fun create() {
        handle = NativeClientZkProfile.createNative()
        if (handle == 0L) throw EnchantCryptoException("ClientZkProfile creation failed")
    }

    fun initialize(serverPublicParams: ByteArray, groupSecretParams: ByteArray) {
        val rc = NativeClientZkProfile.initNative(handle, serverPublicParams, groupSecretParams)
        if (rc != 0) throw EnchantCryptoException("ClientZkProfile init failed: $rc")
    }

    fun destroy() {
        if (handle != 0L) {
            NativeClientZkProfile.destroyNative(handle)
            handle = 0
        }
    }
}
```

### Wire Format (Auth Credential)

The auth credential is issued by the server during device registration. The ZK proof is presented when the client wants to prove group membership without revealing their UUID.

---

## 16. Profile Key Encryption (At-Rest)

Encrypt/decrypt profile data using the profile key (32-byte symmetric key derived from the user's profile key).

### JNI — Encrypt Profile

```
Java_org_enchant_core_crypto_NativeClientZkProfile_encryptProfileForStorageNative(
    JNIEnv*, jclass,
    jlong handle,
    jbyteArray profile,         // Profile data (variable length)
    jbyteArray profileKey,      // 32 bytes
    jbyteArray encryptedDataOut, // Output: encrypted data
    jintArray versionOut        // Output: version number
) -> jint
```

### JNI — Decrypt Profile

```
Java_org_enchant_core_crypto_NativeClientZkProfile_decryptProfileNative(
    JNIEnv*, jclass,
    jlong handle,
    jbyteArray encryptedDataIn, // Encrypted profile data
    jint versionIn,             // Version from encryption
    jbyteArray profileKey,      // 32 bytes
    jbyteArray plaintextOut     // Output: decrypted profile
) -> jint
```

### Kotlin

```kotlin
fun encryptProfile(profileData: ByteArray, profileKey: ByteArray): Pair<ByteArray, Int> {
    val encrypted = ByteArray(profileData.size + 24 + 16) // nonce + ciphertext + tag
    val version = intArrayOf(0)

    val rc = NativeClientZkProfile.encryptProfileForStorageNative(
        handle, profileData, profileKey, encrypted, version
    )
    if (rc != 0) throw EnchantCryptoException("Encrypt profile failed: $rc")

    return Pair(encrypted, version[0])
}

fun decryptProfile(encryptedData: ByteArray, version: Int, profileKey: ByteArray): ByteArray {
    val plaintext = ByteArray(encryptedData.size) // Overestimate
    val rc = NativeClientZkProfile.decryptProfileNative(
        handle, encryptedData, version, profileKey, plaintext
    )
    if (rc != 0) throw EnchantCryptoException("Decrypt profile failed: $rc")
    return plaintext
}
```

### Wire Format

```
// EncryptedProfile
version:            4 bytes (uint32, currently 1)
encrypted_data:     [nonce(24) | xchacha20_poly1305(profile_json)]
```

---

## 17. UUID Encryption

UUIDs are 16 bytes. The ZK system operates on UUIDs as opaque 16-byte values.

### Constants

```c
CLIENT_ZK_UUID_SIZE = 16
```

### Kotlin

```kotlin
fun uuidToBytes(uuid: UUID): ByteArray {
    val bytes = ByteArray(16)
    // UUID most significant bits (big-endian)
    val msb = uuid.mostSignificantBits
    val lsb = uuid.leastSignificantBits
    for (i in 0..7) {
        bytes[i] = ((msb >> (56 - i * 8)) and 0xFF).toByte()
        bytes[i + 8] = ((lsb >> (56 - i * 8)) and 0xFF).toByte()
    }
    return bytes
}

fun bytesToUuid(bytes: ByteArray): UUID {
    require(bytes.size == 16)
    var msb = 0L
    var lsb = 0L
    for (i in 0..7) {
        msb = (msb shl 8) or (bytes[i].toLong() and 0xFF)
        lsb = (lsb shl 8) or (bytes[i + 8].toLong() and 0xFF)
    }
    return UUID(msb, lsb)
}
```

### Swift

```swift
func uuidToData(_ uuid: UUID) -> Data {
    var data = Data(count: 16)
    let (msb, lsb) = (uuid.uuid.0, uuid.uuid.1)
    // UUID bytes are already in big-endian network byte order
    data[0]  = (msb >> 56) & 0xFF
    data[1]  = (msb >> 48) & 0xFF
    data[2]  = (msb >> 40) & 0xFF
    data[3]  = (msb >> 32) & 0xFF
    data[4]  = (msb >> 24) & 0xFF
    data[5]  = (msb >> 16) & 0xFF
    data[6]  = (msb >> 8)  & 0xFF
    data[7]  = msb & 0xFF
    data[8]  = (lsb >> 56) & 0xFF
    data[9]  = (lsb >> 48) & 0xFF
    data[10] = (lsb >> 40) & 0xFF
    data[11] = (lsb >> 32) & 0xFF
    data[12] = (lsb >> 24) & 0xFF
    data[13] = (lsb >> 16) & 0xFF
    data[14] = (lsb >> 8)  & 0xFF
    data[15] = lsb & 0xFF
    return data
}
```

---

## 18. Profile Key Version Derivation

Derives a 32-byte version hash from a profile key. Used to detect profile key changes.

### JNI

```
Java_org_enchant_core_crypto_NativeClientZkProfile_getProfileKeyVersionNative(
    JNIEnv*, jclass,
    jlong handle,
    jbyteArray profileKey,     // 32 bytes
    jbyteArray versionOut      // Output: 32 bytes
) -> jint
```

### Kotlin

```kotlin
fun getProfileKeyVersion(profileKey: ByteArray): ByteArray {
    require(profileKey.size == 32) { "Profile key must be 32 bytes" }
    val version = ByteArray(32)

    val rc = NativeClientZkProfile.getProfileKeyVersionNative(handle, profileKey, version)
    if (rc != 0) throw EnchantCryptoException("Get profile key version failed: $rc")

    return version
}
```

### Swift

```swift
func getProfileKeyVersion(profileKey: Data) -> Data? {
    require(profileKey.count == 32, "Profile key must be 32 bytes")
    var version = Data(count: 32)

    let rc = profileKey.withUnsafeBytes { pkPtr in
        version.withUnsafeMutableBytes { vPtr in
            NativeClientZkProfile_getProfileKeyVersionNative(
                handle,
                pkPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                vPtr.baseAddress!.assumingMemoryBound(to: UInt8.self)
            )
        }
    }
    guard rc == ENCHANT_SUCCESS else { return nil }
    return version
}
```

### Wire Format

The version is 32 bytes: `SHA-256("enchant_ZKGroup_ProfileKeyVersion_20240101" || profile_key)`

---

## 19. XEdDSA Signing

XEdDSA allows signing with an X25519 private key (converted to Ed25519 internally). Used for signed prekey signatures.

### JNI — Sign

```
Java_org_enchant_core_crypto_NativeXEdDSA_signNative(
    JNIEnv*, jclass,
    jbyteArray message,           // Message to sign (variable length)
    jbyteArray x25519PrivateKey,  // 32 bytes
    jbyteArray signature          // Output: 64 bytes
) -> jint
```

### JNI — Verify

```
Java_org_enchant_core_crypto_NativeXEdDSA_verifyNative(
    JNIEnv*, jclass,
    jbyteArray message,           // Message to verify
    jbyteArray signature,         // 64 bytes
    jbyteArray x25519PublicKey    // 32 bytes (X25519 public key)
) -> jint
```

### JNI — Derive Public Key

```
Java_org_enchant_core_crypto_NativeXEdDSA_derivePublicKeyNative(
    JNIEnv*, jclass,
    jbyteArray x25519PrivateKey,  // 32 bytes
    jbyteArray xeddsaPublicKey    // Output: 32 bytes (Ed25519 public key)
) -> jint
```

### Kotlin

```kotlin
fun xeddsaSign(message: ByteArray, x25519PrivateKey: ByteArray): ByteArray {
    val signature = ByteArray(64)
    val rc = NativeXEdDSA.signNative(message, x25519PrivateKey, signature)
    if (rc != 0) throw EnchantCryptoException("XEdDSA sign failed: $rc")
    return signature
}

fun xeddsaVerify(message: ByteArray, signature: ByteArray, x25519PublicKey: ByteArray): Boolean {
    val rc = NativeXEdDSA.verifyNative(message, signature, x25519PublicKey)
    return rc == 0
}

fun deriveEd25519PublicKey(x25519PrivateKey: ByteArray): ByteArray {
    val edPublicKey = ByteArray(32)
    val rc = NativeXEdDSA.derivePublicKeyNative(x25519PrivateKey, edPublicKey)
    if (rc != 0) throw EnchantCryptoException("Derive Ed25519 public key failed: $rc")
    return edPublicKey
}
```

### Swift

```swift
func xeddsaSign(message: Data, x25519PrivateKey: Data) -> Data? {
    var signature = Data(count: 64)
    let rc = message.withUnsafeBytes { msgPtr in
        x25519PrivateKey.withUnsafeBytes { keyPtr in
            signature.withUnsafeMutableBytes { sigPtr in
                enchant_xeddsa_sign(
                    msgPtr.baseAddress!.assumingMemoryBound(to: UInt8.self), message.count,
                    keyPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                    sigPtr.baseAddress!.assumingMemoryBound(to: UInt8.self)
                )
            }
        }
    }
    guard rc == ENCHANT_SUCCESS else { return nil }
    return signature
}

func xeddsaVerify(message: Data, signature: Data, x25519PublicKey: Data) -> Bool {
    let rc = message.withUnsafeBytes { msgPtr in
        signature.withUnsafeBytes { sigPtr in
            x25519PublicKey.withUnsafeBytes { pubPtr in
                enchant_xeddsa_verify(
                    msgPtr.baseAddress!.assumingMemoryBound(to: UInt8.self), message.count,
                    sigPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                    pubPtr.baseAddress!.assumingMemoryBound(to: UInt8.self)
                )
            }
        }
    }
    return rc == ENCHANT_SUCCESS
}
```

---

## 20. StorageService Encryption

StorageService encrypts local key material using a master key. Each item gets a unique derived key.

### Constants

| Constant | Value |
|----------|-------|
| STORAGE_MASTER_KEY_SIZE | 32 |
| STORAGE_ITEM_KEY_SIZE | 32 |
| STORAGE_ENVELOPE_NONCE_SIZE | 24 |
| STORAGE_ENVELOPE_TAG_SIZE | 16 |
| STORAGE_VERSION | 1 |

### JNI

```
// Create/Destroy
NativeStorageService_createNative() -> jlong
NativeStorageService_destroyNative(jlong handle)

// Initialize with master key
NativeStorageService_initNative(
    jlong handle,
    jbyteArray masterKey     // 32 bytes
) -> jint

// Encrypt item
NativeStorageService_encryptItemNative(
    jlong handle,
    jbyteArray plaintext,    // Variable length
    jbyteArray itemId,       // Item identifier (variable)
    jbyteArray envelopeVersionOut,  // Output: 4 bytes
    jbyteArray envelopeNonceOut,    // Output: 24 bytes
    jbyteArray envelopeCiphertextOut // Output: variable
) -> jint

// Decrypt item
NativeStorageService_decryptItemNative(
    jlong handle,
    jbyteArray envelopeVersionIn,   // 4 bytes
    jbyteArray envelopeNonceIn,     // 24 bytes
    jbyteArray envelopeCiphertextIn, // Variable
    jbyteArray itemId,              // Item identifier
    jbyteArray plaintextOut         // Output: variable
) -> jint

// Rotate master key
NativeStorageService_rotateMasterKeyNative(
    jlong handle,
    jbyteArray newMasterKey   // 32 bytes
) -> jint

// Check initialization
NativeStorageService_isInitializedNative(jlong handle) -> jboolean
```

### Kotlin

```kotlin
class StorageServiceManager {
    private var handle: Long = 0

    fun create() {
        handle = NativeStorageService.createNative()
        if (handle == 0L) throw EnchantCryptoException("StorageService creation failed")
    }

    fun initialize(masterKey: ByteArray) {
        require(masterKey.size == 32) { "Master key must be 32 bytes" }
        val rc = NativeStorageService.initNative(handle, masterKey)
        if (rc != 0) throw EnchantCryptoException("StorageService init failed: $rc")
    }

    fun encryptItem(plaintext: ByteArray, itemId: ByteArray): Triple<ByteArray, ByteArray, ByteArray> {
        val version = ByteArray(4)
        val nonce = ByteArray(24)
        val ciphertext = ByteArray(plaintext.size + 16)

        val rc = NativeStorageService.encryptItemNative(
            handle, plaintext, itemId, version, nonce, ciphertext
        )
        if (rc != 0) throw EnchantCryptoException("StorageService encrypt failed: $rc")

        return Triple(version, nonce, ciphertext)
    }

    fun decryptItem(
        version: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        itemId: ByteArray
    ): ByteArray {
        val plaintext = ByteArray(ciphertext.size) // Overestimate
        val rc = NativeStorageService.decryptItemNative(
            handle, version, nonce, ciphertext, itemId, plaintext
        )
        if (rc != 0) throw EnchantCryptoException("StorageService decrypt failed: $rc")
        return plaintext
    }

    fun rotateMasterKey(newMasterKey: ByteArray) {
        val rc = NativeStorageService.rotateMasterKeyNative(handle, newMasterKey)
        if (rc != 0) throw EnchantCryptoException("Rotate master key failed: $rc")
    }

    val isInitialized: Boolean
        get() = NativeStorageService.isInitializedNative(handle)

    fun destroy() {
        if (handle != 0L) {
            NativeStorageService.destroyNative(handle)
            handle = 0
        }
    }
}
```

### Key Derivation Chain

```
master_key (32 bytes)
  |-- HKDF-SHA256(master_key, salt=0, info="EnchantStorageMasterKey_20240101") -> derived_master_key
        |-- HKDF-SHA256(derived_master_key, salt=0, info=item_id) -> item_key
              |-- HKDF-SHA256(item_key, salt=0, info="EnchantStorageEncrypt_20240101") -> enc_key
                    |-- XChaCha20-Poly1305(plaintext, enc_key, random_nonce) -> ciphertext
```

### Wire Format (StorageEnvelope)

```
version:            4 bytes (uint32, currently 1)
nonce:              24 bytes (random)
ciphertext:         variable (plaintext + 16-byte Poly1305 tag)
```

---

## 21. SecureBuffer Usage

`SecureBuffer` is a RAII wrapper around `sodium_malloc` that automatically zeros memory on destruction.

### C API

```c
int enchant_secure_alloc(void** ptr, size_t len);
void enchant_secure_free(void* ptr, size_t len);
void enchant_secure_zero(void* ptr, size_t len);
```

### Kotlin (Conceptual)

```kotlin
// SecureBuffer is used internally by the C++ library.
// On the Java/Kotlin side, use ByteArray and call enchantSecureZero
// when you need to zero sensitive data.

fun ByteArray.secureZero() {
    enchantSecureZero(this, this.size)
}

// Example: zeroing a key after use
val key = ByteArray(32)
try {
    // Use key...
} finally {
    key.secureZero()
}
```

### Swift

```swift
extension Data {
    mutating func secureZero() {
        self.withUnsafeMutableBytes { ptr in
            enchant_secure_zero(ptr.baseAddress, ptr.count)
        }
    }
}

// Example
var key = Data(count: 32)
// Use key...
key.secureZero()
```

---

## 22. XChaCha20-Poly1305

Authenticated encryption with 256-bit key and 192-bit nonce.

### Constants

| Constant | Value |
|----------|-------|
| Key size | 32 bytes |
| Nonce size | 24 bytes |
| Tag size | 16 bytes (included in ciphertext) |
| Ciphertext overhead | +16 bytes over plaintext |

### C API

```c
int enchant_xchacha20_encrypt(const uint8_t* plaintext, size_t plaintext_len,
                              const uint8_t* key,
                              const uint8_t* nonce,
                              uint8_t* ciphertext, size_t ciphertext_capacity);

int enchant_xchacha20_decrypt(const uint8_t* ciphertext, size_t ciphertext_len,
                              const uint8_t* key,
                              const uint8_t* nonce,
                              uint8_t* plaintext, size_t plaintext_capacity);
```

### Kotlin

```kotlin
fun xchacha20Encrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
    require(key.size == 32) { "Key must be 32 bytes" }
    require(nonce.size == 24) { "Nonce must be 24 bytes" }

    val ciphertext = ByteArray(plaintext.size + 16)
    val rc = NativeCrypto.xchacha20Encrypt(
        plaintext, plaintext.size, key, nonce, ciphertext, ciphertext.size
    )
    if (rc != 0) throw EnchantCryptoException("XChaCha20 encrypt failed: $rc")
    return ciphertext
}

fun xchacha20Decrypt(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
    require(key.size == 32) { "Key must be 32 bytes" }
    require(nonce.size == 24) { "Nonce must be 24 bytes" }
    require(ciphertext.size >= 16) { "Ciphertext too short" }

    val plaintext = ByteArray(ciphertext.size - 16)
    val rc = NativeCrypto.xchacha20Decrypt(
        ciphertext, ciphertext.size, key, nonce, plaintext, plaintext.size
    )
    if (rc != 0) throw EnchantCryptoException("XChaCha20 decrypt failed: $rc")
    return plaintext
}
```

### Swift

```swift
func xchacha20Encrypt(plaintext: Data, key: Data, nonce: Data) -> Data? {
    guard key.count == 32, nonce.count == 24 else { return nil }
    var ciphertext = Data(count: plaintext.count + 16)

    let rc = plaintext.withUnsafeBytes { ptPtr in
        key.withUnsafeBytes { kPtr in
            nonce.withUnsafeBytes { nPtr in
                ciphertext.withUnsafeMutableBytes { ctPtr in
                    enchant_xchacha20_encrypt(
                        ptPtr.baseAddress!.assumingMemoryBound(to: UInt8.self), plaintext.count,
                        kPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                        nPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                        ctPtr.baseAddress!.assumingMemoryBound(to: UInt8.self), ciphertext.count
                    )
                }
            }
        }
    }
    guard rc == ENCHANT_SUCCESS else { return nil }
    return ciphertext
}

func xchacha20Decrypt(ciphertext: Data, key: Data, nonce: Data) -> Data? {
    guard key.count == 32, nonce.count == 24, ciphertext.count >= 16 else { return nil }
    var plaintext = Data(count: ciphertext.count - 16)

    let rc = ciphertext.withUnsafeBytes { ctPtr in
        key.withUnsafeBytes { kPtr in
            nonce.withUnsafeBytes { nPtr in
                plaintext.withUnsafeMutableBytes { ptPtr in
                    enchant_xchacha20_decrypt(
                        ctPtr.baseAddress!.assumingMemoryBound(to: UInt8.self), ciphertext.count,
                        kPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                        nPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                        ptPtr.baseAddress!.assumingMemoryBound(to: UInt8.self), plaintext.count
                    )
                }
            }
        }
    }
    guard rc == ENCHANT_SUCCESS else { return nil }
    return plaintext
}
```

### Wire Format

```
// Encrypted data
nonce(24) || ciphertext(variable) || poly1305_tag(16)
// Or when stored inline: ciphertext = plaintext + 16 bytes tag
```

---

## 23. AES-256-GCM

Authenticated encryption with 256-bit key and 96-bit nonce.

### Constants

| Constant | Value |
|----------|-------|
| Key size | 32 bytes |
| Nonce size | 12 bytes |
| Tag size | 16 bytes |
| Ciphertext overhead | +16 bytes over plaintext |

### C API

```c
int enchant_aes_256_keygen(uint8_t* key);

int enchant_aes_256_gcm_encrypt(const uint8_t* key,
                                const uint8_t* nonce,
                                const uint8_t* plaintext, size_t plaintext_len,
                                const uint8_t* aad, size_t aad_len,
                                uint8_t* ciphertext, size_t* ciphertext_len);

int enchant_aes_256_gcm_decrypt(const uint8_t* key,
                                const uint8_t* nonce,
                                const uint8_t* ciphertext, size_t ciphertext_len,
                                const uint8_t* aad, size_t aad_len,
                                uint8_t* plaintext, size_t* plaintext_len);
```

### Kotlin

```kotlin
fun aes256GcmEncrypt(
    plaintext: ByteArray,
    key: ByteArray,
    nonce: ByteArray,
    aad: ByteArray? = null
): ByteArray {
    require(key.size == 32) { "Key must be 32 bytes" }
    require(nonce.size == 12) { "Nonce must be 12 bytes" }

    val ciphertext = ByteArray(plaintext.size + 16)
    val ciphertextLen = intArrayOf(0)

    val rc = NativeCrypto.aes256GcmEncrypt(
        key, nonce,
        plaintext, plaintext.size,
        aad, aad?.size ?: 0,
        ciphertext, ciphertextLen
    )
    if (rc != 0) throw EnchantCryptoException("AES-256-GCM encrypt failed: $rc")
    return ciphertext.copyOfRange(0, ciphertextLen[0])
}

fun aes256GcmDecrypt(
    ciphertext: ByteArray,
    key: ByteArray,
    nonce: ByteArray,
    aad: ByteArray? = null
): ByteArray {
    require(key.size == 32) { "Key must be 32 bytes" }
    require(nonce.size == 12) { "Nonce must be 12 bytes" }

    val plaintext = ByteArray(ciphertext.size - 16)
    val plaintextLen = intArrayOf(0)

    val rc = NativeCrypto.aes256GcmDecrypt(
        key, nonce,
        ciphertext, ciphertext.size,
        aad, aad?.size ?: 0,
        plaintext, plaintextLen
    )
    if (rc != 0) throw EnchantCryptoException("AES-256-GCM decrypt failed: $rc")
    return plaintext.copyOfRange(0, plaintextLen[0])
}
```

### Swift

```swift
func aes256GcmEncrypt(plaintext: Data, key: Data, nonce: Data, aad: Data? = nil) -> Data? {
    guard key.count == 32, nonce.count == 12 else { return nil }
    var ciphertext = Data(count: plaintext.count + 16)
    var ciphertextLen = Int(0)

    let rc = ciphertext.withUnsafeMutableBytes { ctPtr in
        plaintext.withUnsafeBytes { ptPtr in
            key.withUnsafeBytes { kPtr in
                nonce.withUnsafeBytes { nPtr in
                    (aad ?? Data()).withUnsafeBytes { aadPtr in
                        enchant_aes_256_gcm_encrypt(
                            kPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                            nPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                            ptPtr.baseAddress!.assumingMemoryBound(to: UInt8.self), plaintext.count,
                            aadPtr.baseAddress?.assumingMemoryBound(to: UInt8.self), aad?.count ?? 0,
                            ctPtr.baseAddress!.assumingMemoryBound(to: UInt8.self), &ciphertextLen
                        )
                    }
                }
            }
        }
    }
    guard rc == ENCHANT_SUCCESS else { return nil }
    return ciphertext.prefix(ciphertextLen)
}
```

---

## 24. HKDF-SHA256

Key derivation function using HMAC-SHA256.

### Constants

| Constant | Value |
|----------|-------|
| Max output | 8160 bytes |

### C API

```c
int enchant_hkdf_sha256(const uint8_t* ikm, size_t ikm_len,
                        const uint8_t* salt, size_t salt_len,
                        const uint8_t* info, size_t info_len,
                        uint8_t* okm, size_t okm_len);
```

### Kotlin

```kotlin
fun hkdfSha256(
    ikm: ByteArray,
    salt: ByteArray,
    info: ByteArray,
    okmLen: Int
): ByteArray {
    val okm = ByteArray(okmLen)
    val rc = NativeCrypto.hkdfSha256(
        ikm, ikm.size,
        salt, salt.size,
        info, info.size,
        okm, okmLen
    )
    if (rc != 0) throw EnchantCryptoException("HKDF-SHA256 failed: $rc")
    return okm
}
```

### Swift

```swift
func hkdfSha256(ikm: Data, salt: Data, info: Data, okmLen: Int) -> Data? {
    var okm = Data(count: okmLen)
    let rc = ikm.withUnsafeBytes { ikmPtr in
        salt.withUnsafeBytes { saltPtr in
            info.withUnsafeBytes { infoPtr in
                okm.withUnsafeMutableBytes { okmPtr in
                    enchant_hkdf_sha256(
                        ikmPtr.baseAddress!.assumingMemoryBound(to: UInt8.self), ikm.count,
                        saltPtr.baseAddress!.assumingMemoryBound(to: UInt8.self), salt.count,
                        infoPtr.baseAddress!.assumingMemoryBound(to: UInt8.self), info.count,
                        okmPtr.baseAddress!.assumingMemoryBound(to: UInt8.self), okmLen
                    )
                }
            }
        }
    }
    guard rc == ENCHANT_SUCCESS else { return nil }
    return okm
}
```

---

## 25. SHA-256

Cryptographic hash function, 32-byte output.

### C API

```c
int enchant_sha256(const uint8_t* data, size_t len, uint8_t* hash);
```

### Kotlin

```kotlin
fun sha256(data: ByteArray): ByteArray {
    val hash = ByteArray(32)
    val rc = NativeCrypto.sha256(data, data.size, hash)
    if (rc != 0) throw EnchantCryptoException("SHA-256 failed: $rc")
    return hash
}
```

### Swift

```swift
func sha256(_ data: Data) -> Data? {
    var hash = Data(count: 32)
    let rc = data.withUnsafeBytes { ptr in
        hash.withUnsafeMutableBytes { hPtr in
            enchant_sha256(
                ptr.baseAddress!.assumingMemoryBound(to: UInt8.self), data.count,
                hPtr.baseAddress!.assumingMemoryBound(to: UInt8.self)
            )
        }
    }
    guard rc == ENCHANT_SUCCESS else { return nil }
    return hash
}
```

---

## 26. HMAC-SHA256

Keyed-hash message authentication code.

### Constants

| Constant | Value |
|----------|-------|
| MAC size | 32 bytes |

### C API

```c
int enchant_hmac_sha256(const uint8_t* key, size_t key_len,
                        const uint8_t* data, size_t data_len,
                        uint8_t* mac);
```

### Kotlin

```kotlin
fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val mac = ByteArray(32)
    val rc = NativeCrypto.hmacSha256(key, key.size, data, data.size, mac)
    if (rc != 0) throw EnchantCryptoException("HMAC-SHA256 failed: $rc")
    return mac
}
```

### Swift

```swift
func hmacSha256(key: Data, data: Data) -> Data? {
    var mac = Data(count: 32)
    let rc = key.withUnsafeBytes { kPtr in
        data.withUnsafeBytes { dPtr in
            mac.withUnsafeMutableBytes { mPtr in
                enchant_hmac_sha256(
                    kPtr.baseAddress!.assumingMemoryBound(to: UInt8.self), key.count,
                    dPtr.baseAddress!.assumingMemoryBound(to: UInt8.self), data.count,
                    mPtr.baseAddress!.assumingMemoryBound(to: UInt8.self)
                )
            }
        }
    }
    guard rc == ENCHANT_SUCCESS else { return nil }
    return mac
}
```

---

## 27. Argon2id Password Hashing

Memory-hard password hashing for key derivation.

### Constants

| Constant | Value |
|----------|-------|
| Output size | 128 bytes (crypto_pwhash_STRBYTES) |

### C API

```c
int enchant_argon2id_hash(const char* plaintext, size_t plaintext_len,
                          char* output, size_t output_len);

int enchant_argon2id_verify(const char* hash, size_t hash_len,
                            const char* plaintext, size_t plaintext_len);
```

### Kotlin

```kotlin
fun argon2idHash(password: String): String {
    val output = ByteArray(128)
    val rc = NativeCrypto.argon2idHash(password, password.length, output, output.size)
    if (rc != 0) throw EnchantCryptoException("Argon2id hash failed: $rc")
    return String(output)
}

fun argon2idVerify(hash: String, password: String): Boolean {
    val rc = NativeCrypto.argon2idVerify(hash, hash.length, password, password.length)
    return rc == 0
}
```

### Swift

```swift
func argon2idHash(password: String) -> String? {
    var output = Data(count: 128)
    let rc = password.withCString { pwdPtr in
        output.withUnsafeMutableBytes { outPtr in
            enchant_argon2id_hash(
                pwdPtr, strlen(pwdPtr),
                outPtr.baseAddress!.assumingMemoryBound(to: CChar.self), 128
            )
        }
    }
    guard rc == ENCHANT_SUCCESS else { return nil }
    return String(data: output, encoding: .utf8)
}
```

---

## 28. X25519 DH

Elliptic curve Diffie-Hellman on Curve25519.

### Constants

| Constant | Value |
|----------|-------|
| Public key size | 32 bytes |
| Private key size | 32 bytes |
| Shared secret size | 32 bytes |

### C API

```c
int enchant_x25519_keypair(uint8_t* public_key, uint8_t* private_key);
int enchant_x25519_dh(const uint8_t* private_key, const uint8_t* public_key,
                      uint8_t* shared_secret);
```

### Kotlin

```kotlin
fun x25519Keypair(): Pair<ByteArray, ByteArray> {
    val publicKey = ByteArray(32)
    val privateKey = ByteArray(32)
    val rc = NativeCrypto.x25519Keypair(publicKey, privateKey)
    if (rc != 0) throw EnchantCryptoException("X25519 keypair failed: $rc")
    return Pair(publicKey, privateKey)
}

fun x25519Dh(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
    val sharedSecret = ByteArray(32)
    val rc = NativeCrypto.x25519Dh(privateKey, publicKey, sharedSecret)
    if (rc != 0) throw EnchantCryptoException("X25519 DH failed: $rc")
    return sharedSecret
}
```

### Swift

```swift
func x25519Keypair() -> (publicKey: Data, privateKey: Data)? {
    var pub = Data(count: 32)
    var priv = Data(count: 32)
    let rc = pub.withUnsafeMutableBytes { p in
        priv.withUnsafeMutableBytes { s in
            enchant_x25519_keypair(
                p.baseAddress!.assumingMemoryBound(to: UInt8.self),
                s.baseAddress!.assumingMemoryBound(to: UInt8.self)
            )
        }
    }
    guard rc == ENCHANT_SUCCESS else { return nil }
    return (pub, priv)
}

func x25519Dh(privateKey: Data, publicKey: Data) -> Data? {
    var sharedSecret = Data(count: 32)
    let rc = privateKey.withUnsafeBytes { privPtr in
        publicKey.withUnsafeBytes { pubPtr in
            sharedSecret.withUnsafeMutableBytes { ssPtr in
                enchant_x25519_dh(
                    privPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                    pubPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                    ssPtr.baseAddress!.assumingMemoryBound(to: UInt8.self)
                )
            }
        }
    }
    guard rc == ENCHANT_SUCCESS else { return nil }
    return sharedSecret
}
```

---

## 29. Ed25519 Sign/Verify

Digital signatures using Ed25519.

### Constants

| Constant | Value |
|----------|-------|
| Public key size | 32 bytes |
| Private key size (seed) | 32 bytes |
| Signature size | 64 bytes |

### C API

```c
int enchant_ed25519_keypair(uint8_t* public_key, uint8_t* private_seed);
int enchant_ed25519_sign(const uint8_t* message, size_t message_len,
                         const uint8_t* private_seed, uint8_t* signature);
int enchant_ed25519_verify(const uint8_t* message, size_t message_len,
                           const uint8_t* signature, const uint8_t* public_key);
```

### Kotlin

```kotlin
fun ed25519Keypair(): Pair<ByteArray, ByteArray> {
    val publicKey = ByteArray(32)
    val privateSeed = ByteArray(32)
    val rc = NativeCrypto.ed25519Keypair(publicKey, privateSeed)
    if (rc != 0) throw EnchantCryptoException("Ed25519 keypair failed: $rc")
    return Pair(publicKey, privateSeed)
}

fun ed25519Sign(message: ByteArray, privateSeed: ByteArray): ByteArray {
    val signature = ByteArray(64)
    val rc = NativeCrypto.ed25519Sign(message, message.size, privateSeed, signature)
    if (rc != 0) throw EnchantCryptoException("Ed25519 sign failed: $rc")
    return signature
}

fun ed25519Verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
    val rc = NativeCrypto.ed25519Verify(message, message.size, signature, publicKey)
    return rc == 0
}
```

### Swift

```swift
func ed25519Sign(message: Data, privateSeed: Data) -> Data? {
    var signature = Data(count: 64)
    let rc = message.withUnsafeBytes { msgPtr in
        privateSeed.withUnsafeBytes { seedPtr in
            signature.withUnsafeMutableBytes { sigPtr in
                enchant_ed25519_sign(
                    msgPtr.baseAddress!.assumingMemoryBound(to: UInt8.self), message.count,
                    seedPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                    sigPtr.baseAddress!.assumingMemoryBound(to: UInt8.self)
                )
            }
        }
    }
    guard rc == ENCHANT_SUCCESS else { return nil }
    return signature
}

func ed25519Verify(message: Data, signature: Data, publicKey: Data) -> Bool {
    let rc = message.withUnsafeBytes { msgPtr in
        signature.withUnsafeBytes { sigPtr in
            publicKey.withUnsafeBytes { pubPtr in
                enchant_ed25519_verify(
                    msgPtr.baseAddress!.assumingMemoryBound(to: UInt8.self), message.count,
                    sigPtr.baseAddress!.assumingMemoryBound(to: UInt8.self),
                    pubPtr.baseAddress!.assumingMemoryBound(to: UInt8.self)
                )
            }
        }
    }
    return rc == ENCHANT_SUCCESS
}
```

---

## 30. Base64 Encode/Decode

### C API

```c
int enchant_base64_encode(const uint8_t* data, size_t len,
                          char* output, size_t output_len);

int enchant_base64_decode(const char* input, uint8_t* output, size_t output_len);
```

### Kotlin

```kotlin
fun base64Encode(data: ByteArray): String {
    val maxLen = (data.size * 4 / 3) + 4
    val output = ByteArray(maxLen)
    val rc = NativeCrypto.base64Encode(data, data.size, output, output.size)
    if (rc != 0) throw EnchantCryptoException("Base64 encode failed: $rc")
    return String(output)
}

fun base64Decode(input: String): ByteArray {
    val maxLen = (input.length * 3 / 4) + 4
    val output = ByteArray(maxLen)
    val rc = NativeCrypto.base64Decode(input, output, output.size)
    if (rc != 0) throw EnchantCryptoException("Base64 decode failed: $rc")
    return output
}
```

### Swift

```swift
func base64Encode(_ data: Data) -> String? {
    let maxLen = (data.count * 4 / 3) + 4
    var output = Data(count: maxLen)
    let rc = data.withUnsafeBytes { dPtr in
        output.withUnsafeMutableBytes { oPtr in
            enchant_base64_encode(
                dPtr.baseAddress!.assumingMemoryBound(to: UInt8.self), data.count,
                oPtr.baseAddress!.assumingMemoryBound(to: CChar.self), maxLen
            )
        }
    }
    guard rc == ENCHANT_SUCCESS else { return nil }
    return String(data: output, encoding: .utf8)
}
```

---

## Appendix A: Constants Reference

### Key Sizes

| Constant | Value |
|----------|-------|
| X25519 public key | 32 |
| X25519 private key | 32 |
| Ed25519 public key | 32 |
| Ed25519 seed | 32 |
| Ed25519 signature | 64 |
| XChaCha20 key | 32 |
| XChaCha20 nonce | 24 |
| XChaCha20 tag | 16 |
| AES-256 key | 32 |
| AES-256-GCM nonce | 12 |
| AES-256-GCM tag | 16 |
| SHA-256 output | 32 |
| HMAC-SHA256 output | 32 |
| HKDF max output | 8160 |
| Argon2id output | 128 |

### Protocol Sizes

| Constant | Value |
|----------|-------|
| X3DH shared secret | 32 |
| X3DH root key | 32 |
| X3DH chain key | 32 |
| ML-KEM-768 public key | 1184 |
| ML-KEM-768 secret key | 2400 |
| ML-KEM-768 ciphertext | 1088 |
| ML-KEM-1024 public key | 1568 |
| ML-KEM-1024 secret key | 3168 |
| ML-KEM-1024 ciphertext | 1568 |

### Sender Key Sizes

| Constant | Value |
|----------|-------|
| SENDER_KEY_SEED_SIZE | 32 |
| SENDER_KEY_IV_SIZE | 16 |
| SENDER_KEY_CIPHER_KEY_SIZE | 32 |
| SENDER_KEY_MAX_FORWARD_JUMPS | 2000 |
| SENDER_KEY_MAX_MESSAGE_KEYS | 2000 |

### GroupsV2 Sizes

| Constant | Value |
|----------|-------|
| GROUPS_V2_GROUP_ID_SIZE | 32 |
| GROUPS_V2_EPOCH_SECRET_SIZE | 32 |
| GROUPS_V2_MEMBER_ID_SIZE | 32 |
| GROUPS_V2_MAX_MEMBERS | 256 |
| MLS_TREE_KEM_NODE_SIZE | 32 |
| MLS_TREE_KEM_PATH_SECRET_SIZE | 32 |
| MLS_TREE_KEM_HMAC_SIZE | 32 |
| MLS_TREE_KEM_GROUP_SECRET_SIZE | 32 |

### StorageService Sizes

| Constant | Value |
|----------|-------|
| STORAGE_MASTER_KEY_SIZE | 32 |
| STORAGE_ITEM_KEY_SIZE | 32 |
| STORAGE_ENVELOPE_NONCE_SIZE | 24 |
| STORAGE_ENVELOPE_TAG_SIZE | 16 |
| STORAGE_VERSION | 1 |

### ZK Sizes

| Constant | Value |
|----------|-------|
| CLIENT_ZK_UUID_SIZE | 16 |
| CLIENT_ZK_PROFILE_KEY_SIZE | 32 |

### Protocol Constants

| Constant | Value |
|----------|-------|
| PREKEY_BATCH_SIZE | 100 |
| PREKEY_TOPUP_THRESHOLD | 10 |
| PREKEY_DEFAULT_EXPIRY_MS | 604800000 (7 days) |
| MAX_ARCHIVED_STATES | 40 |
| MAX_RECEIVER_CHAINS | 5 |
| MAX_MESSAGE_KEYS_PER_CHAIN | 2000 |
| MAX_REGISTRATION_ID | 16383 |
| ENVELOPE_PROTOCOL_VERSION | 4 |
| ENVELOPE_LEGACY_VERSION | 3 |

---

## Appendix B: Error Codes

| Code | Name | Description |
|------|------|-------------|
| 0 | SUCCESS | Operation succeeded |
| -1 | NULL_POINTER | Null pointer argument |
| -2 | BUFFER_TOO_SMALL | Output buffer too small |
| -3 | INVALID_KEY_SIZE | Key has wrong size |
| -4 | INVALID_NONCE_SIZE | Nonce has wrong size |
| -5 | CIPHERTEXT_TOO_SHORT | Ciphertext is too short |
| -6 | DECRYPTION_FAILED | Decryption failed (wrong key, corrupted data) |
| -7 | SIGNATURE_INVALID | Signature verification failed |
| -8 | KEY_EXPIRED | Key has expired |
| -9 | REPLAY_DETECTED | Message replay detected |
| -10 | OUT_OF_BOUNDS | Index out of bounds |
| -11 | INVALID_FORMAT | Data format is invalid |
| -12 | NOT_IMPLEMENTED | Feature not implemented |
| -99 | INTERNAL | Internal library error |
| -100 | NO_SESSION | No session found |
| -101 | DUPLICATE_MESSAGE | Duplicate message detected |
| -102 | MAX_SKIPPED_KEYS | Maximum skipped keys exceeded |
| -103 | PREKEY_CONSUMED | One-time prekey already consumed |
| -104 | PREKEY_NOT_FOUND | Prekey not found |
| -105 | EPOCH_MISMATCH | Group epoch mismatch |
| -106 | REGISTRATION_MISMATCH | Registration ID mismatch |
| -107 | SESSION_STATE_INVALID | Session state is corrupted |
| -108 | UNTRUSTED_IDENTITY | Identity key not trusted |
| -109 | INVALID_SESSION_STRUCTURE | Session serialization error |
| -110 | UNRECOGNIZED_MESSAGE_VERSION | Unknown message version |
| -111 | INVALID_PROTOBUF_ENCODING | Protobuf encoding error |
| -200 | MISMATCHED_DEVICES | Device list mismatch |
| -201 | MISSING_DEVICES | Missing devices in sync |
| -202 | EXTRA_DEVICES | Extra devices in sync |
| -203 | STALE_DEVICES | Stale device list |

---

## Appendix C: Wire Format Reference

### Envelope (Protobuf)

```
Envelope {
  string envelope_id = 1;
  string sender_user_id = 2;
  string sender_device_id = 3;
  string recipient_user_id = 4;
  string recipient_device_id = 5;
  string message_type = 6;       // "WHISPER_MESSAGE" | "PREKEY_MESSAGE" | "SENDERKEY_MESSAGE"
  bytes payload = 7;
  uint64 server_ts = 8;
  string sender_ts = 9;
  bool sealed = 10;
  string reply_token = 11;
  bool ephemeral = 12;
  bool urgent = 13;
}
```

### Content (Protobuf)

```
Content {
  oneof content {
    DataMessage    data_message    = 1;
    ReceiptMessage receipt_message = 2;
    TypingMessage  typing_message  = 3;
    CallMessage    call_message    = 4;
  }
}
```

### E2EE Payload Structures

```
WHISPER_MESSAGE (existing session):
  [header_size(4) | header | ciphertext]

PREKEY_MESSAGE (new session):
  [ik_size(4) | ik(32) | ek_size(4) | ek(32) | spk_id(4) | opk_id(4) |
   header_size(4) | header | ciphertext]

SENDERKEY_MESSAGE (group):
  [iteration(4) | xchacha20_poly1305(plaintext)]

Header format:
  [dh_key_size(4) | dh_public_key(32) |
   ns(4) | nr(4) | previous_chain_length(4)]

Ciphertext:
  nonce(24) || xchacha20_poly1305(plaintext) || poly1305_tag(16)

Sealed Sender V1:
  version(1) || eph_pub(32) || enc_static(32+16) || static_mac(32) ||
  [nonce(24) || ct || tag(16) || hmac(32)]

Distribution Message (Sender Key):
  key_id(4) || epoch(4) || iteration(4) || chain_key(32) || signature(64)

Key Bundle Upload:
  registration_id(2) || identity_key(32) || signed_prekey(32) ||
  signed_prekey_sig(64) || opk_id(4) || opk(32) || kyber_id(4) || kyber(1184)

PQXDH Prekey Message:
  [header + ephemeral(32) + kyber_ct(1088) + encrypted_payload]
```

### HKDF Info Strings

| Context | Info String |
|---------|------------|
| X3DH | `"X3DH"` |
| Envelope Ratchet | `"EnvelopeRatchet"` |
| PQXDH | `"EnvelopeText_X25519_SHA-256_ML-KEM-768"` |
| Sender Key | `"EnvelopeGroup"` |
| Storage Master Key | `"EnchantStorageMasterKey_20240101"` |
| Storage Item Key | `"EnchantStorageItemKey_20240101"` |
| Storage Encrypt | `"EnchantStorageEncrypt_20240101"` |
| Profile Storage | `"EnchantClientZkProfile_StorageKey_20240101"` |
| Profile Recipient | `"EnchantClientZkProfile_RecipientKey_20240101"` |
| ZK UUID Show | `"enchant_ZKGroup_ShowUuid_20240101"` |
| ZK Profile Key Show | `"enchant_ZKGroup_ShowProfileKey_20240101"` |
| Profile Key Version | `"enchant_ZKGroup_ProfileKeyVersion_20240101"` |
| Sender Key Seed | `"EnchantSenderKey"` |
| GroupsV2 Epoch | `"EnchantGroupsV2_EpochSecret_20240101"` |
