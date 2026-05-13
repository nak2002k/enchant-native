# Crypto from Scratch — X3DH + Double Ratchet Implementation

> Design document for Enchant's custom implementation of the Signal Protocol
> cryptographic core (X3DH key agreement + Double Ratchet per-message encryption).

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Primitives (via libsodium JNI)](#2-primitives-via-libsodium-jni)
3. [Key Types](#3-key-types)
4. [X3DH Key Agreement](#4-x3dh-key-agreement)
5. [Double Ratchet](#5-double-ratchet)
6. [Session Management](#6-session-management)
7. [Pre-Key Lifecycle](#7-pre-key-lifecycle)
8. [Message Envelope Formats](#8-message-envelope-formats)
9. [Security Invariants](#9-security-invariants)
10. [Known-Answer Tests](#10-known-answer-tests)
11. [Integration Points](#11-integration-points)
12. [File Manifest](#12-file-manifest)

---

## 1. Architecture Overview

### Layered Design

```
┌─────────────────────────────────────────────────────┐
│                  SessionManager                      │
│  (orchestrates encrypt/decrypt, manages sessions)    │
├─────────────┬───────────────────┬────────────────────┤
│   X3DH.kt   │ DoubleRatchet.kt  │   KeyManager.kt    │
│ (key agree) │ (per-msg cipher)  │ (pre-key lifecycle) │
├─────────────┴───────────────────┴────────────────────┤
│                 CryptoHelper.kt                       │
│       (libsodium primitive wrappers)                  │
├──────────────────────────────────────────────────────┤
│              SodiumProvider.kt (JNI bridge)            │
│              libsodium.so (native)                     │
└──────────────────────────────────────────────────────┘
```

### Module: `:core:crypto`

| File | Responsibility |
|------|---------------|
| `SodiumProvider.kt` | Loads libsodium native library, provides `Sodium` instance |
| `CryptoHelper.kt` | Wraps all libsodium primitives (DH, signing, AEAD, KDF) |
| `KeyManager.kt` | Generates, stores, uploads, rotates pre-keys |
| `X3DH.kt` | X3DH key agreement protocol (Alice + Bob) |
| `DoubleRatchet.kt` | Per-message encrypt/decrypt with ratchet stepping |
| `SessionManager.kt` | Orchestrates sessions — encrypt/decrypt via X3DH + Ratchet |
| `SignalProtocolStore.kt` | In-memory + DB-backed store for sessions, identities, pre-keys |

---

## 2. Primitives (via libsodium JNI)

All cryptographic operations use **libsodium** as the backend via JNI. We never implement a primitive from scratch — libsodium provides audited, constant-time implementations.

### 2.1 Curve Operations

| Operation | libsodium API | Output Size | Purpose |
|-----------|--------------|-------------|---------|
| X25519 DH | `crypto_scalarmult()` | 32 bytes | Shared secret computation |
| X25519 keygen | `crypto_scalarmult_base()` | 32 bytes pub, 32 bytes priv | Key pair generation |
| Ed25519 sign | `crypto_sign_detached()` | 64 bytes | SPK signature with IK |
| Ed25519 verify | `crypto_sign_verify_detached()` | — | Verify SPK signature |
| Ed25519 keygen | `crypto_sign_keypair()` | 32 bytes pub, 64 bytes priv | IK generation |

### 2.2 Symmetric Operations

| Operation | libsodium API | Output Size | Purpose |
|-----------|--------------|-------------|---------|
| XChaCha20-Poly1305 AEAD | `crypto_aead_xchacha20poly1305_ietf_encrypt/decrypt` | ct = pt + 16 | Message encryption |
| SHA-512 | `crypto_hash_sha512()` | 64 bytes | Hashing |
| SHA-256 | `crypto_hash_sha256()` | 32 bytes | Hashing, KDF |
| HKDF-SHA256/SHA512 | `crypto_kdf_hkdf_*` (or custom via HMAC) | configurable | Key derivation |
| CSPRNG | `randombytes_buf()` | configurable | Key generation |

### 2.3 Memory Operations

| Operation | libsodium API | Purpose |
|-----------|--------------|---------|
| Secure zero | `sodium_memzero()` | Zero secrets after use |
| Secure malloc | `sodium_malloc()` | Guarded memory allocation |
| Constant-time cmp | `sodium_memcmp()` | Timing-safe comparison |
| Lock memory | `sodium_mlock()` | Prevent swapping secrets to disk |

### 2.4 Key Conversions

| Conversion | Description | Code |
|-----------|-------------|------|
| Ed25519 → X25519 (secret) | `crypto_sign_ed25519_sk_to_curve25519()` | For DH operations |
| Ed25519 → X25519 (public) | `crypto_sign_ed25519_pk_to_curve25519()` | For DH operations |

**Why conversion?** Identity Keys are Ed25519 (for signing SPKs), but X3DH DH operations use X25519. We convert the IK to X25519 for DH computations.

### 2.5 CryptoHelper.kt API

```kotlin
object CryptoHelper {
    fun ed25519SkToX25519(sk: ByteArray): ByteArray
    fun ed25519PkToX25519(pk: ByteArray): ByteArray
    fun x25519DiffieHellman(privateKey: ByteArray, publicKey: ByteArray): ByteArray
    fun hkdfSha256(input: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray
    fun encryptXChaCha20Poly1305(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray
    fun decryptXChaCha20Poly1305(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray
    fun generateRandomKey(size: Int = 32): ByteArray
    fun generateIdentityKeypair(): KeyPair       // Ed25519
    fun generateSignedPrekey(identitySk: ByteArray): SignedPreKeyPair  // X25519 + Ed25519 sig
    fun generateOneTimePrekey(): KeyPair          // X25519
    fun sign(message: ByteArray, secretKey: ByteArray): ByteArray
    fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean
    fun sha256(data: ByteArray): ByteArray
    fun sha512(data: ByteArray): ByteArray
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean
    fun zeroBytes(data: ByteArray)
}
```

---

## 3. Key Types

### 3.1 Identity Key (IK) — `enchant_identity_key`

| Property | Value |
|----------|-------|
| Algorithm | Ed25519 |
| Secret size | 64 bytes (seed + public) |
| Public size | 32 bytes |
| Lifetime | Permanent device identity |
| Storage | Android KeyStore (hardware-backed if available) |
| Uploaded to IKS | Public key only |

**Constraints:**
- One IK per device, generated once on first registration
- **Never** changes for the lifetime of the device
- Stored in Android KeyStore with `setUserAuthenticationRequired(false)` — no user interaction needed for crypto ops, but StrongBox backed when available
- IK secret key is **never** uploaded, never logged, never in crash reports

### 3.2 Signed Prekey (SPK) — `enchant_signed_prekey`

| Property | Value |
|----------|-------|
| Algorithm | X25519 |
| Secret size | 32 bytes |
| Public size | 32 bytes |
| Signature | Ed25519(IK_private, SPK_public) — 64 bytes |
| Lifetime | 30 days (rotated periodically) |
| Storage | SQLCipher database (`key_material` table) |
| Uploaded to IKS | Public key + signature |

**Constraints:**
- Old SPK is deactivated on server (not deleted) for grace period
- At most 1 active SPK per device at a time
- Signature proves ownership of the identity key

### 3.3 One-Time Prekeys (OPK) — `enchant_opk_{id}`

| Property | Value |
|----------|-------|
| Algorithm | X25519 |
| Secret size | 32 bytes |
| Public size | 32 bytes |
| Lifetime | Until consumed (used once for session establishment) |
| Storage | SQLCipher database (`key_material` table) |
| Count on server | 100 initially, topped up when < 10 |
| Max on server | 200 total per device |

**Constraints:**
- Each OPK is used exactly once for one session establishment
- After consumption, server deletes it; client should delete local copy
- If no OPK is available, X3DH proceeds without DH4 (still secure, just can't do one-to-one handshake)
- OPKs older than 90 days are deleted (cleanup)

### 3.4 Ephemeral Key (EK)

| Property | Value |
|----------|-------|
| Algorithm | X25519 |
| Secret size | 32 bytes |
| Public size | 32 bytes |
| Lifetime | Single X3DH handshake |
| Storage | Not persisted — generated per session establishment |

**Constraints:**
- Generated fresh for each X3DH initiation
- Included in the PREKEY_MESSAGE header so Bob can compute DH2 and DH3
- Deleted after shared secret is derived

### 3.5 Key Pair Data Structures

```kotlin
data class KeyPair(
    val publicKey: ByteArray,   // 32 bytes
    val privateKey: ByteArray   // 32 bytes
)

data class SignedPreKeyPair(
    val keyPair: KeyPair,              // X25519 keypair
    val signature: ByteArray,          // Ed25519(IK_priv, SPK_pub) — 64 bytes
    val id: Int,                       // Monotonic ID for server reference
    val createdAt: Long                // Unix timestamp for rotation logic
)

data class OneTimePreKey(
    val keyPair: KeyPair,              // X25519 keypair
    val id: Int,                       // Monotonic ID
    val createdAt: Long
)
```

---

## 4. X3DH Key Agreement

### 4.1 Overview

X3DH establishes a 32-byte shared secret (SK) between Alice and Bob using:
- Alice's Identity Key (IK_A)
- Alice's Ephemeral Key (EK_A)
- Bob's Identity Key (IK_B)
- Bob's Signed Prekey (SPK_B)
- Bob's One-Time Prekey (OPK_B) — optional

### 4.2 Alice Initializes (Sending Side)

```
Input:  IK_A (Ed25519 keypair), EK_A (X25519 keypair),
        IK_B_public (X25519-converted), SPK_B_public (X25519),
        OPK_B_public (X25519 or null)

1. Convert IK_A to X25519:
   ik_a_x25519_priv = CryptoHelper.ed25519SkToX25519(IK_A.privateKey)
   ik_a_x25519_pub  = CryptoHelper.ed25519PkToX25519(IK_A.publicKey)

2. Compute DH agreements:
   DH1 = CryptoHelper.x25519DiffieHellman(ik_a_x25519_priv, SPK_B_public)
   DH2 = CryptoHelper.x25519DiffieHellman(EK_A.privateKey, ik_b_x25519_pub)
   DH3 = CryptoHelper.x25519DiffieHellman(EK_A.privateKey, SPK_B_public)
   DH4 = if (OPK_B_public != null)
           CryptoHelper.x25519DiffieHellman(EK_A.privateKey, OPK_B_public)
         else
           null

3. Derive shared secret:
   dh_input = DH1 || DH2 || DH3 || (DH4 ?: ByteArray(0))
   SK = CryptoHelper.hkdfSha256(dh_input, salt=0x00*32, info="EnchantX3DH", 32)

4. Derive initial root key and chain key:
   root_material = CryptoHelper.hkdfSha256(SK, salt=0x00*32, info="EnchantRoot", 64)
   rootKey = root_material[0..31]
   chainKey = root_material[32..63]

5. Return:
   X3dhResult(SK, rootKey, chainKey,
     X3dhHeader(IK_A.publicKey, EK_A.publicKey, SPK_B.id, OPK_B?.id))
```

### 4.3 Bob Responds (Receiving Side)

```
Input:  IK_B (Ed25519 keypair), SPK_B (X25519 keypair),
        OPK_B (X25519 keypair or null),
        IK_A_public (X25519-converted), EK_A_public (X25519)

1. Convert IK_B to X25519:
   ik_b_x25519_priv = CryptoHelper.ed25519SkToX25519(IK_B.privateKey)

2. Compute DH agreements:
   DH1 = CryptoHelper.x25519DiffieHellman(SPK_B.privateKey, ik_a_x25519_pub)
   DH2 = CryptoHelper.x25519DiffieHellman(ik_b_x25519_priv, EK_A_public)
   DH3 = CryptoHelper.x25519DiffieHellman(SPK_B.privateKey, EK_A_public)
   DH4 = if (OPK_B != null)
           CryptoHelper.x25519DiffieHellman(OPK_B.privateKey, EK_A_public)
         else
           null

3. Derive shared secret (IDENTICAL to Alice's computation):
   dh_input = DH1 || DH2 || DH3 || (DH4 ?: ByteArray(0))
   SK = CryptoHelper.hkdfSha256(dh_input, salt=0x00*32, info="EnchantX3DH", 32)

4. Derive initial root key and chain key:
   root_material = CryptoHelper.hkdfSha256(SK, salt=0x00*32, info="EnchantRoot", 64)
   rootKey = root_material[0..31]
   chainKey = root_material[32..63]

5. Delete OPK (if used) — one-time consumption
   if (OPK_B != null) KeyManager.deleteOneTimePrekey(OPK_B.id)

6. Return X3dhResult
```

### 4.4 X3DH Header (sent in PREKEY_MESSAGE envelope)

```
serialized_header = ik_public (32) || ek_public (32) || spk_id (4) || opk_id (4 or 0)
```

Layout:
| Offset | Bytes | Field | Description |
|--------|-------|-------|-------------|
| 0 | 32 | IK public | Alice's Ed25519 public key (not X25519-converted) |
| 32 | 32 | EK public | Alice's ephemeral X25519 public key |
| 64 | 4 | SPK ID | Which of Bob's SPKs was used (big-endian int32) |
| 68 | 4 | OPK ID | Which of Bob's OPKs was used, 0 = none (big-endian int32) |
| **72** | | | **Total: 72 bytes** |

### 4.5 Handling "No OPK Available"

If Bob has no OPKs when Alice fetches the bundle:
- `one_time_prekey` field in `KeyBundleResponse` is null
- X3DH proceeds with DH1 + DH2 + DH3 only
- SK derived from 3 DH outputs instead of 4
- Security is still strong: 3 DH computations provide forward secrecy
- Communication starts as SIGNAL_MESSAGE immediately (no PREKEY_MESSAGE needed for session setup? Actually no — the first message is still a PREKEY_MESSAGE to establish the session. Bob recognizes it's a first session by the message type, not by presence of OPK.)

Wait — re-reading the Signal protocol: The first message is always a PREKEY_MESSAGE when there's no existing session. The prekey message contains the X3DH header. Bob processes it and establishes the session regardless of whether an OPK was used.

### 4.6 X3DH.kt API

```kotlin
object X3DH {
    data class Result(
        val sharedSecret: ByteArray,      // 32 bytes — SK
        val rootKey: ByteArray,           // 32 bytes — first root key
        val sendingChainKey: ByteArray,   // 32 bytes — initial sending chain
        val receivingChainKey: ByteArray, // 32 bytes — initial receiving chain
        val header: Header
    )

    data class Header(
        val identityKey: ByteArray,       // 32 bytes — IK public
        val ephemeralKey: ByteArray,      // 32 bytes — EK public
        val signedPrekeyId: Int,          // SPK ID
        val oneTimePrekeyId: Int?          // OPK ID or null
    )

    suspend fun aliceInitiate(
        ourIdentityKey: KeyPair,          // Ed25519
        ourEphemeralKey: KeyPair,         // X25519
        theirIdentityKeyPublic: ByteArray, // X25519-converted
        theirSignedPrekeyPublic: ByteArray,
        theirOneTimePrekeyPublic: ByteArray?
    ): Result

    suspend fun bobRespond(
        ourIdentityKey: KeyPair,          // Ed25519
        ourSignedPrekeyKeyPair: KeyPair,  // X25519
        ourOneTimePrekeyKeyPair: KeyPair?, // X25519 or null
        theirIdentityKeyPublic: ByteArray, // X25519-converted
        theirEphemeralKeyPublic: ByteArray
    ): Result
}
```

---

## 5. Double Ratchet

### 5.1 Overview

After X3DH establishes the initial shared secret, all subsequent messages use the Double Ratchet algorithm. Each message triggers a key derivation that provides **forward secrecy** (old keys can't decrypt new messages) and **future secrecy** (new keys can't decrypt old messages).

### 5.2 State Structure

```kotlin
data class RatchetState(
    // Root chain
    val rootKey: ByteArray,                     // 32 bytes

    // Sending chain
    val sendingChainKey: ByteArray?,            // 32 bytes or null (if Bob hasn't sent yet)
    val sendingRatchetKey: ByteArray?,           // 32 bytes — current DH public
    val sendingRatchetKeyPrivate: ByteArray?,    // 32 bytes — current DH secret
    val sendingMessageNumber: Int,               // Next message number in sending chain

    // Receiving chain
    val receivingChainKey: ByteArray?,           // 32 bytes or null
    val receivingRatchetKey: ByteArray?,         // 32 bytes — peer's DH public
    val receivingMessageNumber: Int,             // Next expected message number

    // Skipped message keys
    val skippedMessageKeys: Map<String, MessageKey>,  // "dhPub:msgNum" -> key

    // Metadata
    val previousSendingChainLength: Int,          // For header: prev chain length
    val version: Int = 1                          // For serialization compat
)

data class MessageKey(
    val chainKey: ByteArray,     // Chain key this message key was derived from
    val key: ByteArray,          // 32 bytes — AES key for decryption
    val nonce: ByteArray         // 12 bytes — AEAD nonce
)
```

### 5.3 Message Encryption (Alice sends N-th message)

```
Input:  state (RatchetState), plaintext (ByteArray)
Output: RatchetMessage

1. Ratchet step check:
   if (state.sendingMessageNumber >= RATCHET_INTERVAL) {
       // Nope, we ratchet EVERY message for maximum forward secrecy
       // Actually Signal ratchets every message — each message = a ratchet step
       // Actually wait — let me re-check.
   }

Actually, let me clarify: In the Double Ratchet protocol, the DIFFIE-HELLMAN ratchet step
alternates between Alice and Bob. Each party has a "sending chain" and from that chain
derive individual "message keys."

The standard protocol:
1. Alice sends N messages using message keys derived from her sending chain
2. When Alice needs to send again after receiving, or after a certain number of sends,
   she performs a DH ratchet to create a new sending chain
3. Bob does the same on his side

In practice, Signal's implementation:
- The sending party generates a new DH key pair for each message (or every few messages)
- The DH output advances the root key, producing a new sending chain
- Each message key is derived from the current chain key with a KDF

Let me be precise:
```

**Ratchet step (performed by the sender before encrypting if needed):**

```
1. Generate new ephemeral key pair:
   new_ratchet_key = X25519.generate()
   state.sendingRatchetKeyPublic = new_ratchet_key.public
   state.sendingRatchetKeyPrivate = new_ratchet_key.private

2. DH with receiving ratchet key:
   shared_dh = X25519.DH(state.sendingRatchetKeyPrivate, state.receivingRatchetKeyPublic)

3. Advance root key:
   new_root_and_chain = HKDF_SHA256(state.rootKey, shared_dh, "EnchantRatchet", 64)
   state.rootKey = new_root_and_chain[0..31]
   state.sendingChainKey = new_root_and_chain[32..63]

4. Reset sending message number:
   state.previousSendingChainLength = state.sendingMessageNumber
   state.sendingMessageNumber = 0
```

**Message encryption (after ratchet step if needed):**

```
1. Derive message key from chain key:
   message_key_input = HKDF_SHA256(state.sendingChainKey, salt=0x00*32, "EnchantMsg", 80)
   message_key = message_key_input[0..31]
   message_nonce = message_key_input[32..43]
   next_chain_key = message_key_input[44..75]

2. Advance chain key:
   state.sendingChainKey = next_chain_key
   state.sendingMessageNumber += 1

3. AEAD encrypt:
   ad = state.sendingRatchetKeyPublic || state.receivingRatchetKeyPublic
   ciphertext = XChaCha20_Poly1305_Encrypt(plaintext, message_key, message_nonce, ad)

4. Build header:
   header = {
     dh: state.sendingRatchetKeyPublic,
     msg_num_send: state.sendingMessageNumber - 1,
     msg_num_recv: state.receivingMessageNumber,
     prev_chain_len: previousSendingChainLength
   }

5. Return RatchetMessage(header, ciphertext)
```

**Header serialization:**
```
header_bytes = dh_pub (32) || Ns (4) || Nr (4) || pcl (4)
```
| Offset | Bytes | Field | Type |
|--------|-------|-------|------|
| 0 | 32 | DH public key | X25519 public, big-endian |
| 32 | 4 | Ns (message number send) | Big-endian int32 |
| 36 | 4 | Nr (message number receive) | Big-endian int32 |
| 40 | 4 | Pcl (previous chain length) | Big-endian int32 |
| **44** | | **Total header** | |

### 5.4 Message Decryption (Bob receives N-th message)

```
Input:  state (RatchetState), message (RatchetMessage)
Output: plaintext (ByteArray)

1. Check if it's a ratchet step:
   if (message.header.dh != state.receivingRatchetKey) {
       // Remote ratcheted — we need to ratchet too
       performReceiveRatchet(state, message.header.dh)
   }

2. Check skipped message keys:
   key_id = "${message.header.dh}:${message.header.Ns}"
   if (state.skippedMessageKeys.containsKey(key_id)) {
       key = state.skippedMessageKeys.remove(key_id)
       plaintext = XChaCha20_Poly1305_Decrypt(message.ciphertext, key.key, key.nonce, ad)
       return plaintext  // Skipped key case: don't advance receiving chain
   }

3. Skip message keys (if there's a gap):
   while (state.receivingMessageNumber < message.header.Ns) {
       msg_key = deriveMessageKey(state.receivingChainKey)
       skip_id = "${state.receivingRatchetKey}:${state.receivingMessageNumber}"
       state.skippedMessageKeys[skip_id] = msg_key
       state.receivingChainKey = advanceChainKey(state.receivingChainKey)
       state.receivingMessageNumber += 1
   }

4. Derive message key for current message:
   msg_key = deriveMessageKey(state.receivingChainKey)
   state.receivingChainKey = advanceChainKey(state.receivingChainKey)
   state.receivingMessageNumber += 1

5. Decrypt:
   ad = message.header.dh || state.receivingRatchetKey
   plaintext = XChaCha20_Poly1305_Decrypt(message.ciphertext, msg_key.key, msg_key.nonce, ad)

6. Return plaintext
```

**Receive ratchet step:**

```
1. Generate new ephemeral key for sending:
   new_sending_ratchet = X25519.generate()

2. DH computations:
   shared_dh_1 = X25519.DH(state.sendingRatchetKeyPrivate, message.header.dh)
   shared_dh_2 = X25519.DH(new_sending_ratchet.private, message.header.dh)

3. This is where it gets complex. Let me simplify:

   For the receiving ratchet step:
   a. Save old receiving chain as "previous" for potential skipped keys
   b. Set new receiving ratchet key = message.header.dh
   c. DH = X25519.DH(state.sendingRatchetKeyPrivate, message.header.dh)
   d. Advance root key with DH
   e. New receiving chain from root key
   f. Set up new sending chain:
      - Generate new ratchet key pair
      - DH = X25519.DH(new_priv, message.header.dh)
      - Advance root key again
      - New sending chain from root key
```

Actually, let me simplify this significantly. The full Double Ratchet spec (per the Signal spec document) is quite involved. Let me write the clean version:

### 5.5 Simplified Ratchet Steps

**Sender (Alice) sending:**

```
function encrypt(state, plaintext, AD):
    // Step 1: Ratchet if this is the first send after receiving
    if state.sendingChainKey is null:
        state = RatchetEncrypt(state)
    
    // Step 2: Derive message key
    msg_key = KDF(state.sendingChainKey, "message")
    state.sendingChainKey = KDF(state.sendingChainKey, "chain")
    state.sendingMessageNumber += 1
    
    // Step 3: Encrypt
    ciphertext = AEAD_encrypt(plaintext, msg_key, AD)
    
    return (state, ciphertext)

function RatchetEncrypt(state):
    // Generate new ratchet key pair
    state.sendingRatchetKey = X25519_keygen()
    state.sendingRatchetPublic = state.sendingRatchetKey.public
    
    // DH with receiving ratchet
    dh_out = X25519(state.sendingRatchetKey.private, state.receivingRatchetPublic)
    
    // Derive new root and sending chain
    (state.rootKey, state.sendingChainKey) = HKDF(state.rootKey, dh_out, "ratchet")
    state.sendingMessageNumber = 0
    
    return state
```

**Receiver (Bob) receiving:**

```
function decrypt(state, msg, AD):
    // Step 1: Check if ratchet needed
    if msg.header.dh != state.receivingRatchetPublic:
        state = RatchetDecrypt(state, msg.header.dh)
    
    // Step 2: Skip ahead if needed
    while state.receivingMessageNumber < msg.header.Ns:
        skip_key = KDF(state.receivingChainKey, "message")
        state.skippedMessageKeys[msg.header.dh][state.receivingMessageNumber] = skip_key
        state.receivingChainKey = KDF(state.receivingChainKey, "chain")
        state.receivingMessageNumber += 1
    
    // Step 3: Derive message key
    msg_key = KDF(state.receivingChainKey, "message")
    state.receivingChainKey = KDF(state.receivingChainKey, "chain")
    state.receivingMessageNumber += 1
    
    // Step 4: Decrypt
    plaintext = AEAD_decrypt(msg.ciphertext, msg_key, AD)
    
    return (state, plaintext)

function RatchetDecrypt(state, new_dh_public):
    // Save old receiving keys as skipped
    // (These are messages we'll never receive — they're from the previous ratchet)
    
    // DH with new ratchet key and our private sending key
    dh_out = X25519(state.sendingRatchetPrivate, new_dh_public)
    
    // Derive new root and receiving chain
    (state.rootKey, state.receivingChainKey) = HKDF(state.rootKey, dh_out, "ratchet")
    state.receivingRatchetPublic = new_dh_public
    state.receivingMessageNumber = 0
    
    // Generate new sending keys for next send
    state = RatchetEncrypt(state)
    
    return state
```

### 5.6 Associated Data

The associated data (AD) for AEAD encryption is:

```
AD = IK_A_public || IK_B_public
```

This binds the ciphertext to the specific pair of identity keys, preventing key-misbinding attacks.

### 5.7 Skipped Message Key Limits

- **Maximum skipped keys:** 1000 per session
- **Eviction policy:** Oldest skipped key is evicted when limit is reached
- **Rationale:** Accommodates out-of-order delivery and offline messages while bounding memory usage

```kotlin
object SkippedKeyManager {
    private const val MAX_SKIPPED_KEYS = 1000

    fun store(state: RatchetState, ratchetPublic: ByteArray, msgNum: Int, key: MessageKey) {
        if (state.skippedMessageKeys.size >= MAX_SKIPPED_KEYS) {
            // Evict oldest — find the key with smallest timestamp
            // (We store timestamps alongside skipped keys)
            val oldest = state.skippedMessageKeys.entries.minByOrNull { it.value.timestamp }
            if (oldest != null) state.skippedMessageKeys.remove(oldest.key)
        }
        val keyId = "${ratchetPublic.toHex()}:$msgNum"
        state.skippedMessageKeys[keyId] = key
    }

    fun consume(state: RatchetState, ratchetPublic: ByteArray, msgNum: Int): MessageKey? {
        val keyId = "${ratchetPublic.toHex()}:$msgNum"
        return state.skippedMessageKeys.remove(keyId)
    }
}
```

### 5.8 Replay Protection

- After a message key is consumed (either from skipped keys or direct derivation), it is **deleted immediately**
- If the same `(dh_public, message_number)` pair appears again, it will not be found in skipped keys AND the chain has already advanced past it → decryption will fail
- This provides natural replay protection without separate tracking

### 5.9 DoubleRatchet.kt API

```kotlin
object DoubleRatchet {
    data class Message(
        val header: ByteArray,        // 44 bytes serialized
        val ciphertext: ByteArray
    )

    fun initializeAsAlice(
        sharedSecret: ByteArray,      // SK from X3DH
        theirSignedPrekeyPublic: ByteArray,
        ourIdentityKeyPublic: ByteArray,
        theirIdentityKeyPublic: ByteArray
    ): RatchetState

    fun initializeAsBob(
        sharedSecret: ByteArray,      // SK from X3DH
        theirEphemeralKeyPublic: ByteArray,
        ourIdentityKeyPublic: ByteArray,
        theirIdentityKeyPublic: ByteArray
    ): RatchetState

    fun encrypt(
        state: RatchetState,
        plaintext: ByteArray,
        ad: ByteArray? = null
    ): Pair<RatchetState, Message>

    fun decrypt(
        state: RatchetState,
        message: Message,
        ad: ByteArray? = null
    ): Pair<RatchetState, ByteArray>

    fun serializeState(state: RatchetState): ByteArray
    fun deserializeState(data: ByteArray): RatchetState
}
```

---

## 6. Session Management

### 6.1 Session Record (Persisted)

Each session is serialized to the database for persistence:

```kotlin
data class SessionRecord(
    val version: Int = 1,
    val state: RatchetState,
    val ourIdentityKeyPublic: ByteArray,     // Our Ed25519 public key
    val theirIdentityKeyPublic: ByteArray,    // Their Ed25519 (X25519-converted) public key
    val theirSignedPrekeyId: Int?,            // Which SPK was used for session
    val createdAt: Long,
    val lastUsedAt: Long
)
```

**Serialization format (protobuf):**
```protobuf
message SessionRecord {
    int32 version = 1;
    RatchetState state = 2;
    bytes our_identity_key_public = 3;
    bytes their_identity_key_public = 4;
    optional int32 their_signed_prekey_id = 5;
    int64 created_at = 6;
    int64 last_used_at = 7;

    message RatchetState {
        bytes root_key = 1;
        optional bytes sending_chain_key = 2;
        optional bytes sending_ratchet_key_public = 3;
        optional bytes sending_ratchet_key_private = 4;
        int32 sending_message_number = 5;
        optional bytes receiving_chain_key = 6;
        optional bytes receiving_ratchet_key_public = 7;
        int32 receiving_message_number = 8;
        map<string, MessageKey> skipped_message_keys = 9;
        int32 previous_sending_chain_length = 10;
    }

    message MessageKey {
        bytes key = 1;
        bytes nonce = 2;
        bytes chain_key = 3;
    }
}
```

### 6.2 Session Store

```kotlin
interface SessionStore {
    // CRUD
    suspend fun loadSession(userId: String, deviceId: String): SessionRecord?
    suspend fun storeSession(userId: String, deviceId: String, session: SessionRecord)
    suspend fun deleteSession(userId: String, deviceId: String)
    suspend fun deleteAllSessions(userId: String)

    // Queries
    suspend fun hasSession(userId: String, deviceId: String): Boolean
    suspend fun getSubDevices(userId: String): List<Int>
    suspend fun getAllAddressesWithActiveSessions(): Map<String, Set<String>>

    // Archival
    suspend fun archiveSession(userId: String, deviceId: String)
}
```

### 6.3 Session Locking (Thread Safety)

```kotlin
object SessionLock {
    private val lock = ReentrantLock()

    fun <T> withLock(action: () -> T): T {
        lock.lock()
        try { return action() }
        finally { lock.unlock() }
    }
}
```

All session reads and writes must go through `SessionLock.withLock()` to prevent race conditions during concurrent decrypt operations (e.g., receiving multiple messages simultaneously).

### 6.4 Session Lifecycle

```
New session:
  No session exists → X3DH establish → Double Ratchet init → store

Active session:
  Session exists → load → Double Ratchet encrypt/decrypt → store (after each operation)
  → Session updated with new ratchet state

Identity key change:
  Detect IK change → archive current session → delete → force re-establish
  → User must verify new safety number before proceeding

Session expired:
  Decryption fails repeatedly → archive → retry session establishment
  → If recipient has new keys, establish fresh session

Session deletion (compromise):
  Delete entirely → generate new identity (full re-registration)
```

---

## 7. Pre-Key Lifecycle

### 7.1 Initial Key Generation

On first registration, the client must:

```
1. Generate Identity Key (Ed25519)
   → Store secret in KeyStore (enchant_identity_key)
   → Keep public for upload

2. Generate Signed Prekey (X25519)
   → Generate X25519 keypair
   → Sign SPK_public with Ed25519(IK_private, SPK_public)
   → id = 1
   → Store keypair + signature in database

3. Generate 100 One-Time Prekeys (X25519)
   → For i in 1..100: generate X25519 keypair, id = i
   → Store all in database
```

### 7.2 Upload to IKS

```
POST /v1/keys/register
{
  "identity_key": "<base64url IK_public>",
  "signed_prekey": {
    "public_key": "<base64url SPK_public>",
    "signature": "<base64url Ed25519(IK_priv, SPK_pub)>"
  },
  "one_time_prekeys": [
    {"public_key": "<base64url OPK_1>"},
    ...
  ]
}
```

### 7.3 Signed Prekey Rotation

```
Every 30 days (or on app launch if >25 days since last rotation):

1. Generate new X25519 keypair → SPK_{new}
2. Sign: Ed25519(IK_private, SPK_{new}_public)
3. id = last_id + 1
4. PUT /v1/keys/signed-prekey {public_key, signature}
5. On success: store new SPK locally, deactivate old one
```

### 7.4 One-Time Prekey Top-Up

```
On app launch or after session establishment:

1. GET /v1/keys/opk-count → {remaining: N}
2. If N < 10:
   a. Generate 100 new OPKs
   b. POST /v1/keys/one-time-prekeys {one_time_prekeys: [...]}
```

### 7.5 Cleanup

```
Every app launch:

1. Delete local OPKs that are >90 days old (not uploaded to server)
2. Delete signed prekeys that are >30 days old (deactivated on server)
3. Keep at least 200 OPKs locally if many are unaccounted for
```

---

## 8. Message Envelope Formats

### 8.1 PREKEY_MESSAGE (First message in a new session)

```
prekey_message = {
  header: {
    ik: <32 bytes IK_public>,
    ek: <32 bytes EK_public>,
    spk_id: <int32>,
    opk_id: <int32 or 0>,
    base_key: <32 bytes DH public for first ratchet>  // New: first sending ratchet key
  },
  ciphertext: <AEAD ciphertext of the actual message>
}
```

**Serialized wire format:**
```
| IK (32) | EK (32) | SPK_ID (4) | OPK_ID (4) | BASE_KEY (32) | CIPHERTEXT (variable) |
|---72 bytes header---|---  |   message body  |
```

Total header: 72 bytes

### 8.2 SIGNAL_MESSAGE (Subsequent messages)

```
signal_message = {
  ratchet_key: <32 bytes DH public key>,
  message_number_send: <int32>,
  message_number_receive: <int32>,
  previous_chain_length: <int32>,
  ciphertext: <AEAD ciphertext>
}
```

**Serialized wire format:**
```
| DH (32) | Ns (4) | Nr (4) | PCL (4) | CIPHERTEXT (variable) |
|--- 44 bytes header ---|--- message body ---|
```

### 8.3 Envelope Wrapping (for transport)

The inner message (PREKEY_MESSAGE or SIGNAL_MESSAGE) is wrapped in the WebSocket Envelope protobuf:

```protobuf
message Envelope {
    string envelope_id = 1;           // Server-populated UUID
    string sender_user_id = 2;        // Server-populated
    string sender_device_id = 3;      // Server-populated
    string recipient_user_id = 4;     // Required
    string recipient_device_id = 5;   // Optional — fan-out if empty
    string message_type = 6;          // "PREKEY_MESSAGE" or "SIGNAL_MESSAGE"
    bytes payload = 7;                // Serialized inner message (header + ciphertext)
    uint64 server_ts = 8;             // Server-populated
    string sender_ts = 9;             // Client-set ISO 8601
    bool sealed = 10;                 // Sealed sender flag
    string reply_token = 11;         // Optional
    bool ephemeral = 12;             // True for typing indicators etc
    bool urgent = 13;                // Urgency flag
}
```

---

## 9. Security Invariants

### 9.1 Forward Secrecy

- **Requirement:** Compromise of current keys must not reveal past message content
- **Implementation:** After a ratchet step, the old root key and chain keys are zeroed via `CryptoHelper.zeroBytes()`
- **Verification:** Test that decrypting an old message after ratchet step fails

### 9.2 Future Secrecy (Post-Compromise Security)

- **Requirement:** Compromise of current keys must not reveal future message content after the next ratchet step
- **Implementation:** Each message includes a DH ratchet step (new DH key pair, new DH output mixed into root key)
- **Verification:** Test that after ratchet step, old private keys cannot decrypt new messages

### 9.3 Replay Protection

- **Requirement:** An attacker who captures an encrypted message cannot replay it later
- **Implementation:** Each message key is used exactly once. The `(ratchet_public, message_number)` pair is checked against skipped message keys and current chain position
- **Verification:** Test that decrypting the same message twice fails

### 9.4 No Plaintext Leaks

- **Requirement:** Message content must never appear in logs, crash reports, error messages, or stack traces
- **Implementation:**
  - All `ByteArray` arguments containing secrets are zeroed after use
  - Crypto helper functions never log their inputs
  - Crash reporter explicitly redacts base64-encoded key material
  - ProGuard R8 strips `Log.d()` calls in release

### 9.5 Key Compromise Handling

- **Requirement:** If a session key is compromised, the user must be able to re-establish security
- **Implementation:**
  - `SessionManager.archiveSession()` archives current state and forces re-establishment
  - `SessionManager.deleteSession()` deletes entirely — user re-registers identity
  - Identity key change detected → session archived → user prompted to verify safety numbers

### 9.6 Session Locking

- **Requirement:** Concurrent access to session state must not produce inconsistent state
- **Implementation:** `SessionLock.withLock()` around all session read/write operations

---

## 10. Known-Answer Tests

### 10.1 Primitive KATs

Every cryptographic primitive must pass known-answer tests from the relevant RFC:

| Primitive | Test Vectors | Source |
|-----------|-------------|--------|
| X25519 DH | 5 test vectors | RFC 7748 §6.1 |
| Ed25519 signing | 7 test vectors | RFC 8032 §7.1 |
| Ed25519 → X25519 | Self-generated | libsodium test suite |
| HKDF-SHA256 | 3 test vectors | RFC 5869 §A |
| XChaCha20-Poly1305 | 10+ test vectors | draft-irtf-cfrg-xchacha |
| SHA-256 | 3 test vectors | FIPS 180-4 |

### 10.2 X3DH KATs

Generate specific key material for Alice and Bob, run X3DH, verify SK matches expected output:

```kotlin
@Test
fun `X3DH with OPK produces expected shared secret`() {
    // Given specific Alice IK, Bob IK, Bob SPK, Bob OPK
    // When X3DH.aliceInitiate and X3DH.bobRespond
    // Then both produce identical SK

    val aliceIK = CryptoHelper.generateIdentityKeypair()
    val bobIK = CryptoHelper.generateIdentityKeypair()
    val bobSPK = CryptoHelper.generateSignedPrekey(bobIK.privateKey)
    val bobOPK = CryptoHelper.generateOneTimePrekey()

    val aliceResult = runBlocking {
        X3DH.aliceInitiate(
            aliceIK, generateEphemeralKey(),
            CryptoHelper.ed25519PkToX25519(bobIK.publicKey),
            bobSPK.keyPair.publicKey, bobOPK.publicKey
        )
    }

    val bobResult = runBlocking {
        X3DH.bobRespond(
            bobIK, bobSPK.keyPair, bobOPK,
            CryptoHelper.ed25519PkToX25519(aliceIK.publicKey),
            aliceResult.header.ephemeralKey
        )
    }

    assertTrue(aliceResult.sharedSecret.contentEquals(bobResult.sharedSecret))
}
```

### 10.3 Double Ratchet KATs

Create two sessions, encrypt/decrypt a sequence of messages, verify correctness at each step:

```kotlin
@Test
fun `Double Ratchet 100 message sequence`() {
    // Establish session via X3DH
    // Encrypt 100 messages from Alice
    // Decrypt 100 messages at Bob
    // Verify all plaintext matches
    // Verify state is consistent
}
```

---

## 11. Integration Points

### 11.1 SessionManager.kt

```kotlin
class SessionManager(
    private val sessionStore: SessionStore,
    private val identityStore: IdentityStore,
    private val keyManager: KeyManager,
    private val apiClient: ApiClient
) {
    suspend fun encryptMessage(
        recipientUserId: String,
        recipientDeviceId: String?,
        plaintext: ByteArray
    ): EncryptedPayload

    suspend fun decryptMessage(
        senderUserId: String,
        payload: EncryptedPayload
    ): DecryptedResult

    suspend fun hasSession(userId: String): Boolean
    suspend fun deleteSession(userId: String, deviceId: String?)
    suspend fun archiveSession(userId: String)
    suspend fun getSafetyNumber(userId: String): String
}
```

### 11.2 Integration with Message Pipeline

**Outgoing message flow:**
```
ViewModel.sendTextMessage("Hello")
  → ConversationRepository.insertMessage(status=SENDING)
  → MessageSendPipeline.sendMessage(recipient, plaintext)
    → SessionManager.encryptMessage(recipient, plaintext)
      → Check session exists → if not, X3DH establish
      → DoubleRatchet.encrypt(state, plaintext)
      → Return EncryptedPayload(messageType, payload)
    → WebSocketManager.sendMessage(recipient, payload, messageType)
    → On ack: update status to SENT
```

**Incoming message flow:**
```
WebSocketManager receives envelope
  → IncomingMessageProcessor.processIncoming(envelope)
    → SessionManager.decryptMessage(sender, payload)
      → If PREKEY_MESSAGE: X3DH.bobRespond → session established
      → If SIGNAL_MESSAGE: DoubleRatchet.decrypt(state, message)
      → Return DecryptedResult(plaintext)
    → Determine message type (text, media, receipt, etc.)
    → Insert into DB, update UI
    → Send delivery receipt
```

---

## 12. File Manifest

| File | Path | Purpose | Depends On |
|------|------|---------|------------|
| `SodiumProvider.kt` | `:core:crypto` | libsodium JNI init | — |
| `CryptoHelper.kt` | `:core:crypto` | Primitive wrappers | SodiumProvider |
| `KeyManager.kt` | `:core:crypto` | Key lifecycle | CryptoHelper, KeyStore, API |
| `X3DH.kt` | `:core:crypto` | X3DH protocol | CryptoHelper |
| `DoubleRatchet.kt` | `:core:crypto` | Ratchet encrypt/decrypt | CryptoHelper |
| `SessionManager.kt` | `:core:crypto` | Session orchestration | All above + DB |
| `SessionStore.kt` | `:core:signalstore` | Session persistence DB | Database module |
| `IdentityStore.kt` | `:core:signalstore` | Identity key DB | Database module |
| `PreKeyStore.kt` | `:core:signalstore` | Pre-key DB | Database module |
| `SessionLock.kt` | `:core:crypto` | Reentrant session lock | — |

---

## Appendix A: Media Encryption (AES-256-GCM)

### A.1 Why Not XChaCha20-Poly1305 for Media?

Media files use **AES-256-GCM** instead of XChaCha20-Poly1305 for three reasons:

| Reason | Explanation |
|--------|-------------|
| **Hardware acceleration** | ARMv8 CPUs have AES-NI instructions; `javax.crypto.Cipher` uses them transparently. XChaCha20 requires libsodium JNI per chunk, adding overhead. |
| **Signal compatibility** | Signal uses AES-256-GCM for media. Using the same algorithm ensures our `AttachmentPointer.key` format is interoperable with Signal's backup/export tools. |
| **Streaming** | Large files (128MB) benefit from streaming AEAD. AES-256-GCM with 16KB chunks allows progressive encryption/decryption without loading the entire file into memory. |

### A.2 Media Encryption Format

```kotlin
object MediaEncryption {
    private const val KEY_SIZE = 32      // AES-256
    private const val IV_SIZE = 12       // GCM standard nonce
    private const val TAG_SIZE = 16      // GCM authentication tag

    data class MediaKey(
        val key: ByteArray,              // 32 bytes — stored in AttachmentPointer.key
        val iv: ByteArray,               // 12 bytes — prepended to ciphertext
        val ciphertext: ByteArray        // Encrypted blob (includes GCM tag)
    )

    fun encrypt(plaintext: ByteArray): MediaKey {
        val key = CryptoHelper.generateRandomKey(KEY_SIZE)
        val iv = CryptoHelper.generateRandomKey(IV_SIZE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE * 8, iv))
        val ct = cipher.doFinal(plaintext)
        return MediaKey(key, iv, ct)
    }

    fun decrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE * 8, iv))
        return cipher.doFinal(ciphertext)
    }
}
```

### A.3 Encrypted Blob Format (On Server)

```
Bytes 0-11:   IV (12 bytes)
Bytes 12+:    Ciphertext + GCM tag (ciphertext length + 16)
                ↓
Server stores opaque blob, returns `media_id`
Client downloads → strips IV → decrypts with key from AttachmentPointer
```

### A.4 Encryption Strategy Summary

| Data Type | Algorithm | Key Source | Location |
|-----------|-----------|------------|----------|
| Message payload | XChaCha20-Poly1305 | Double Ratchet derived | Inside Envelope.content |
| Media files | AES-256-GCM | Random 32 bytes (`MediaEncryption.generateKey()`) | AttachmentPointer.key |
| Session records | XChaCha20-Poly1305 | Session SK derived | `signal_sessions` table |
| DB at rest | SQLCipher (AES-256-CBC + HMAC-SHA512) | KeyStore-wrapped passphrase | Full database |
| Preferences | EncryptedSharedPrefs (AES-256-SIV/AES-256-GCM) | KeyStore master key | SecurePreferences |

---

## Appendix B: libsodium JNI Setup

### Build Integration

```kotlin
// core/crypto/build.gradle.kts
dependencies {
    implementation("com.goterl:lazysodium-android:5.2.1")  // libsodium JNI wrapper
    implementation("com.goterl:lazysodium-java:5.2.1")    // Java bindings
}
```

**Alternative:** Use the `libsignal-client` Rust crate for protocol primitives via JNI (more batteries-included but less control).

### Initialization

```kotlin
object SodiumProvider {
    private var initialized = false
    private lateinit var sodium: Sodium

    suspend fun init() {
        if (initialized) return
        sodium = SodiumAndroid()  // Lazysodium's Android implementation
        initialized = true
    }

    fun get(): Sodium {
        if (!initialized) throw IllegalStateException("SodiumProvider not initialized")
        return sodium
    }
}
```

---

## Appendix B: Performance Targets

| Operation | Target Time | Context |
|-----------|-------------|---------|
| X3DH handshake | < 10ms | 4 scalar multiplications |
| Double Ratchet encrypt | < 1ms | 2 KDF + 1 AEAD |
| Double Ratchet decrypt | < 1ms | 1 KDF + 1 AEAD |
| Prekey generation (100 OPKs) | < 500ms | 100 keypairs |
| Session deserialize | < 1ms | Protobuf + state restore |
| Full message encrypt/decrypt pipeline | < 50ms | Session load → crypto → store |
