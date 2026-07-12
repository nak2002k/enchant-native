package org.enchant.core.crypto

/**
 * Core cryptographic primitives for the Enchant protocol.
 *
 * All primitives are delegated to libenchantcrypto — every operation
 * here is a thin Kotlin wrapper around the auto-generated JNI bindings
 * in [EnchantCrypto], which in turn calls into the full libenchantcrypto
 * shared library (libsodium + signal protocol + sealed sender + MLS + ZK + SVR + ML-KEM).
 */
object CryptoPrimitives {
    private var nativeReady = false

    @Synchronized
    fun init() {
        if (nativeReady) return
        val rc = EnchantCrypto.enchant_init()
        if (rc != 0) throw RuntimeException("enchant_init failed: $rc")
        nativeReady = true
    }

    const val XCHACHA20_NONCE_SIZE = 24
    const val XCHACHA20_KEY_SIZE = 32
    const val AES_GCM_NONCE_SIZE = 12
    const val AES_GCM_KEY_SIZE = 32
    const val AES_GCM_TAG_SIZE = 16
    const val X25519_KEY_SIZE = 32
    const val ED25519_KEY_SIZE = 32
    const val ED25519_SIG_SIZE = 64

    data class KeyPair(val publicKey: ByteArray, val privateKey: ByteArray)

    // ──────────────────────────────────────────────
    // Key Generation
    // ──────────────────────────────────────────────

    fun generateX25519KeyPair(): KeyPair {
        val pub = ByteArray(EnchantCrypto.X25519_PUBLIC_KEY_SIZE)
        val priv = ByteArray(EnchantCrypto.X25519_PRIVATE_KEY_SIZE)
        val rc = EnchantCrypto.enchant_x25519_keypair(pub, priv)
        if (rc != 0) throw RuntimeException("x25519_keypair failed: $rc")
        return KeyPair(publicKey = pub, privateKey = priv)
    }

    fun generateEd25519KeyPair(): KeyPair {
        val pub = ByteArray(EnchantCrypto.ED25519_PUBLIC_KEY_SIZE)
        val seed = ByteArray(EnchantCrypto.ED25519_SEED_SIZE)
        val rc = EnchantCrypto.enchant_ed25519_keypair(pub, seed)
        if (rc != 0) throw RuntimeException("ed25519_keypair failed: $rc")
        return KeyPair(publicKey = pub, privateKey = seed)
    }

    fun generateRandomKey(size: Int = 32): ByteArray {
        if (size <= 0) throw IllegalArgumentException("Size must be positive")
        val bytes = ByteArray(size)
        EnchantCrypto.enchant_random_bytes(bytes, bytes.size.toLong())
        return bytes
    }

    // ──────────────────────────────────────────────
    // X25519 Diffie-Hellman
    // ──────────────────────────────────────────────

    fun x25519DiffieHellman(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "X25519 private key must be 32 bytes, got ${privateKey.size}" }
        require(publicKey.size == 32) { "X25519 public key must be 32 bytes, got ${publicKey.size}" }
        val secret = ByteArray(32)
        val rc = EnchantCrypto.enchant_x25519_dh(privateKey, publicKey, secret)
        if (rc != 0) throw RuntimeException("x25519_dh failed: $rc")
        return secret
    }

    // ──────────────────────────────────────────────
    // Ed25519 → X25519 Conversion (now native)
    // ──────────────────────────────────────────────

    fun ed25519SkToX25519(sk: ByteArray): ByteArray {
        require(sk.size == 32) { "Ed25519 seed must be 32 bytes, got ${sk.size}" }
        val out = ByteArray(32)
        val rc = EnchantCrypto.enchant_ed25519_sk_to_x25519(sk, out)
        if (rc != 0) throw RuntimeException("ed25519_sk_to_x25519 failed: $rc")
        return out
    }

    fun ed25519PkToX25519(pk: ByteArray): ByteArray {
        require(pk.size == 32) { "Ed25519 public key must be 32 bytes, got ${pk.size}" }
        val out = ByteArray(32)
        val rc = EnchantCrypto.enchant_ed25519_pk_to_x25519(pk, out)
        if (rc != 0) throw RuntimeException("ed25519_pk_to_x25519 failed: $rc")
        return out
    }

    // ──────────────────────────────────────────────
    // Ed25519 Sign / Verify
    // ──────────────────────────────────────────────

    fun signEd25519(message: ByteArray, privateKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "Ed25519 seed must be 32 bytes, got ${privateKey.size}" }
        val sig = ByteArray(EnchantCrypto.ED25519_SIGNATURE_SIZE)
        val rc = EnchantCrypto.enchant_ed25519_sign(message, message.size.toLong(), privateKey, sig)
        if (rc != 0) throw RuntimeException("ed25519_sign failed: $rc")
        return sig
    }

    fun verifyEd25519(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        if (signature.size != EnchantCrypto.ED25519_SIGNATURE_SIZE) return false
        if (publicKey.size != 32) return false
        val rc = EnchantCrypto.enchant_ed25519_verify(message, message.size.toLong(), signature, publicKey)
        return rc == 0
    }

    // ──────────────────────────────────────────────
    // XChaCha20-Poly1305 AEAD
    // ──────────────────────────────────────────────

    fun encryptXChaCha20Poly1305(plaintext: ByteArray, key: ByteArray, nonce: ByteArray? = null): ByteArray {
        require(key.size == 32) { "XChaCha20-Poly1305 key must be 32 bytes" }
        val n = nonce ?: generateRandomKey(XCHACHA20_NONCE_SIZE)
        val ct = ByteArray(plaintext.size + EnchantCrypto.XCHACHA20_TAG_SIZE)
        val rc = EnchantCrypto.enchant_xchacha20_encrypt(plaintext, plaintext.size.toLong(), key, n, ct, ct.size.toLong())
        if (rc != 0) throw RuntimeException("xchacha20_encrypt failed: $rc")
        return ByteArray(n.size + ct.size).apply {
            n.copyInto(this, 0)
            ct.copyInto(this, n.size)
        }
    }

    fun decryptXChaCha20Poly1305(data: ByteArray, key: ByteArray): ByteArray {
        require(data.size >= XCHACHA20_NONCE_SIZE + AES_GCM_TAG_SIZE) { "Ciphertext too short" }
        val nonce = data.copyOfRange(0, XCHACHA20_NONCE_SIZE)
        val ct = data.copyOfRange(XCHACHA20_NONCE_SIZE, data.size)
        return decryptXChaCha20Poly1305Raw(ct, key, nonce)
    }

    fun encryptXChaCha20Poly1305Raw(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val n = if (nonce.size == XCHACHA20_NONCE_SIZE) nonce else nonce.copyOf(XCHACHA20_NONCE_SIZE)
        require(n.size == XCHACHA20_NONCE_SIZE) { "XChaCha20 nonce must be 24 bytes" }
        val ct = ByteArray(plaintext.size + EnchantCrypto.XCHACHA20_TAG_SIZE)
        val rc = EnchantCrypto.enchant_xchacha20_encrypt(plaintext, plaintext.size.toLong(), key, n, ct, ct.size.toLong())
        if (rc != 0) throw RuntimeException("xchacha20_encrypt failed: $rc")
        return ct
    }

    fun decryptXChaCha20Poly1305Raw(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val n = if (nonce.size == XCHACHA20_NONCE_SIZE) nonce else nonce.copyOf(XCHACHA20_NONCE_SIZE)
        require(n.size == XCHACHA20_NONCE_SIZE) { "XChaCha20 nonce must be 24 bytes" }
        require(key.size == XCHACHA20_KEY_SIZE) { "Key must be 32 bytes" }
        val pt = ByteArray(ciphertext.size - EnchantCrypto.XCHACHA20_TAG_SIZE)
        val rc = EnchantCrypto.enchant_xchacha20_decrypt(ciphertext, ciphertext.size.toLong(), key, n, pt, pt.size.toLong())
        if (rc != 0) throw RuntimeException("xchacha20_decrypt (MAC mismatch) failed: $rc")
        return pt
    }

    // ──────────────────────────────────────────────
    // AES-256-GCM (for media encryption) — now native
    // ──────────────────────────────────────────────

    fun encryptAesGcm(plaintext: ByteArray, key: ByteArray, nonce: ByteArray? = null): ByteArray {
        require(key.size == AES_GCM_KEY_SIZE) { "AES key must be 32 bytes" }
        val n = nonce ?: generateRandomKey(AES_GCM_NONCE_SIZE)
        val ct = ByteArray(plaintext.size + AES_GCM_TAG_SIZE)
        val ctLen = longArrayOf(0)
        val rc = EnchantCrypto.enchant_aes_256_gcm_encrypt(
            key, n, plaintext, plaintext.size.toLong(),
            ByteArray(0), 0L, ct, ctLen
        )
        if (rc != 0) throw RuntimeException("aes_256_gcm_encrypt failed: $rc")
        return ByteArray(n.size + ctLen[0].toInt()).apply {
            n.copyInto(this, 0)
            ct.copyInto(this, n.size, 0, ctLen[0].toInt())
        }
    }

    fun decryptAesGcm(data: ByteArray, key: ByteArray): ByteArray {
        require(key.size == AES_GCM_KEY_SIZE) { "AES key must be 32 bytes" }
        require(data.size >= AES_GCM_NONCE_SIZE + AES_GCM_TAG_SIZE) { "Ciphertext too short" }
        val nonce = data.copyOfRange(0, AES_GCM_NONCE_SIZE)
        val ct = data.copyOfRange(AES_GCM_NONCE_SIZE, data.size)
        return decryptAesGcmRaw(ct, key, nonce)
    }

    fun encryptAesGcmRaw(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        require(key.size == AES_GCM_KEY_SIZE) { "AES key must be 32 bytes" }
        require(nonce.size == AES_GCM_NONCE_SIZE) { "AES nonce must be 12 bytes" }
        val ct = ByteArray(plaintext.size + AES_GCM_TAG_SIZE)
        val ctLen = longArrayOf(0)
        val rc = EnchantCrypto.enchant_aes_256_gcm_encrypt(
            key, nonce, plaintext, plaintext.size.toLong(),
            ByteArray(0), 0L, ct, ctLen
        )
        if (rc != 0) throw RuntimeException("aes_256_gcm_encrypt failed: $rc")
        return ct.copyOf(ctLen[0].toInt())
    }

    fun decryptAesGcmRaw(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        require(key.size == AES_GCM_KEY_SIZE) { "AES key must be 32 bytes" }
        require(nonce.size == AES_GCM_NONCE_SIZE) { "AES nonce must be 12 bytes" }
        val pt = ByteArray(ciphertext.size)
        val ptLen = longArrayOf(0)
        val rc = EnchantCrypto.enchant_aes_256_gcm_decrypt(
            key, nonce, ciphertext, ciphertext.size.toLong(),
            ByteArray(0), 0L, pt, ptLen
        )
        if (rc != 0) throw RuntimeException("aes_256_gcm_decrypt failed: $rc")
        return pt.copyOf(ptLen[0].toInt())
    }

    // ──────────────────────────────────────────────
    // HKDF-SHA256 (RFC 5869)
    // ──────────────────────────────────────────────

    fun hkdfSha256(input: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        if (length <= 0) throw IllegalArgumentException("Length must be positive, got $length")
        if (length > 32 * 255) throw IllegalArgumentException("Length exceeds HKDF-SHA256 maximum of ${32 * 255}, got $length")
        val effectiveSalt = salt.takeIf { it.isNotEmpty() } ?: ByteArray(32)
        val okm = ByteArray(length)
        val rc = EnchantCrypto.enchant_hkdf_sha256(
            input, input.size.toLong(),
            effectiveSalt, effectiveSalt.size.toLong(),
            info, info.size.toLong(),
            okm, length.toLong()
        )
        if (rc != 0) throw RuntimeException("hkdf_sha256 failed: $rc")
        return okm
    }

    // ──────────────────────────────────────────────
    // HMAC-SHA256 / HMAC-SHA512 (now both native)
    // ──────────────────────────────────────────────

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = ByteArray(EnchantCrypto.HMAC_SHA256_SIZE)
        val rc = EnchantCrypto.enchant_hmac_sha256(key, key.size.toLong(), data, data.size.toLong(), mac)
        if (rc != 0) throw RuntimeException("hmac_sha256 failed: $rc")
        return mac
    }

    fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = ByteArray(EnchantCrypto.HMAC_SHA512_SIZE)
        val rc = EnchantCrypto.enchant_hmac_sha512(key, key.size.toLong(), data, data.size.toLong(), mac)
        if (rc != 0) throw RuntimeException("hmac_sha512 failed: $rc")
        return mac
    }

    // ──────────────────────────────────────────────
    // Hash Functions (all native now)
    // ──────────────────────────────────────────────

    fun sha256(data: ByteArray): ByteArray {
        val hash = ByteArray(EnchantCrypto.SHA256_SIZE)
        val rc = EnchantCrypto.enchant_sha256(data, data.size.toLong(), hash)
        if (rc != 0) throw RuntimeException("sha256 failed: $rc")
        return hash
    }

    fun sha384(data: ByteArray): ByteArray {
        val hash = ByteArray(EnchantCrypto.SHA384_SIZE)
        val rc = EnchantCrypto.enchant_sha384(data, data.size.toLong(), hash)
        if (rc != 0) throw RuntimeException("sha384 failed: $rc")
        return hash
    }

    fun sha512(data: ByteArray): ByteArray {
        val hash = ByteArray(EnchantCrypto.SHA512_SIZE)
        val rc = EnchantCrypto.enchant_sha512(data, data.size.toLong(), hash)
        if (rc != 0) throw RuntimeException("sha512 failed: $rc")
        return hash
    }

    fun argon2idHashWithParams(plaintext: ByteArray, salt: ByteArray, iterations: Int, memory_kb: Int, parallelism: Int, outputLen: Int): ByteArray {
        val output = ByteArray(outputLen)
        val rc = EnchantCrypto.enchant_argon2id_hash_with_params(
            plaintext, plaintext.size.toLong(),
            salt, salt.size.toLong(), iterations, memory_kb, parallelism,
            output, outputLen.toLong()
        )
        if (rc != 0) throw RuntimeException("argon2id_hash_with_params failed: $rc")
        return output
    }

    // ──────────────────────────────────────────────
    // Constant-Time Comparison (now native)
    // ──────────────────────────────────────────────

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        val out = IntArray(1)
        val rc = EnchantCrypto.enchant_constant_time_equals(a, b, a.size.toLong(), out)
        if (rc != 0) throw RuntimeException("constant_time_equals failed: $rc")
        return out[0] != 0
    }

    // ──────────────────────────────────────────────
    // Memory Zeroing
    // ──────────────────────────────────────────────

    fun zeroBytes(data: ByteArray) {
        if (data.isEmpty()) return
        // Native sodium_memzero cannot be optimized away by the JIT.
        EnchantCrypto.enchant_secure_zero(data, data.size.toLong())
    }

    // ──────────────────────────────────────────────
    // Encoding
    // ──────────────────────────────────────────────

    fun base64UrlEncode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val out = ByteArray(data.size * 2 + 4)
        val rc = EnchantCrypto.enchant_base64_encode(data, data.size.toLong(), out, out.size.toLong())
        if (rc != 0) throw RuntimeException("base64UrlEncode failed: $rc")
        val sb = StringBuilder()
        for (b in out) {
            if (b == 0.toByte()) break
            sb.append(b.toInt().toChar())
        }
        return sb.toString()
    }

    fun base64UrlDecode(encoded: String): ByteArray {
        try {
            return java.util.Base64.getUrlDecoder().decode(encoded)
        } catch (e: Exception) {
            throw IllegalArgumentException("base64UrlDecode failed: ${e.message}")
        }
    }

    fun hexEncode(data: ByteArray): String =
        data.joinToString("") { String.format("%02x", it) }

    fun hexDecode(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
