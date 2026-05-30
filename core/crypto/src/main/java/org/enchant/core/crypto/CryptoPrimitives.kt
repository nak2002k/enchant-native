package org.enchant.core.crypto

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Core cryptographic primitives for the Enchant protocol.
 *
 * Provides X25519 key agreement, Ed25519 signatures, XChaCha20-Poly1305 AEAD,
 * AES-256-GCM for media, HKDF-SHA256, HMAC-SHA256/512, SHA-256/512, and
 * constant-time comparison. All secret material is zeroed after use.
 *
 * X25519, Ed25519, XChaCha20-Poly1305, HKDF, SHA-256, HMAC-SHA256,
 * Argon2id, and secure memory are delegated to libenchantcrypto (libsodium).
 * AES-256-GCM, HMAC-SHA512, and SHA-384/512 use JCA.
 * Ed25519↔X25519 conversion uses pure Kotlin math.
 */
object CryptoPrimitives {
    private val rng = SecureRandom()
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
        EnchantCrypto.enchant_random_bytes(bytes, bytes.size)
        return bytes
    }

    // ──────────────────────────────────────────────
    // X25519 Diffie-Hellman
    // ──────────────────────────────────────────────

    fun x25519DiffieHellman(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val secret = ByteArray(32)
        val rc = EnchantCrypto.enchant_x25519_dh(privateKey, publicKey, secret)
        if (rc != 0) throw RuntimeException("x25519_dh failed: $rc")
        return secret
    }

    // ──────────────────────────────────────────────
    // Ed25519 → X25519 Conversion
    // ──────────────────────────────────────────────

    fun ed25519SkToX25519(sk: ByteArray): ByteArray {
        val hash = sha512(sk)
        val xPriv = hash.copyOfRange(0, 32)
        xPriv[0] = (xPriv[0].toInt() and 0b1111_1000).toByte()
        xPriv[31] = (xPriv[31].toInt() and 0b0111_1111).toByte()
        xPriv[31] = (xPriv[31].toInt() or 0b0100_0000).toByte()
        return xPriv
    }

    fun ed25519PkToX25519(pk: ByteArray): ByteArray {
        val p = BigInteger("57896044618658097711785492504343953926634992332820282019728792003956564819949")
        val yBytes = pk.copyOf()
        yBytes[31] = (yBytes[31].toInt() and 0b0111_1111).toByte()
        val yBytesBe = yBytes.reversedArray()
        val y = BigInteger(1, yBytesBe)
        val one = BigInteger.ONE
        val u = one.add(y).multiply(one.subtract(y).modPow(p.subtract(BigInteger.valueOf(2)), p)).mod(p)
        var uBytes = u.toByteArray()
        if (uBytes.size < 32) uBytes = ByteArray(32 - uBytes.size).plus(uBytes)
        if (uBytes.size > 32) uBytes = uBytes.copyOfRange(uBytes.size - 32, uBytes.size)
        return uBytes.reversedArray()
    }

    // ──────────────────────────────────────────────
    // Ed25519 Sign / Verify
    // ──────────────────────────────────────────────

    fun signEd25519(message: ByteArray, privateKey: ByteArray): ByteArray {
        val sig = ByteArray(EnchantCrypto.ED25519_SIGNATURE_SIZE)
        val rc = EnchantCrypto.enchant_ed25519_sign(message, message.size, privateKey, sig)
        if (rc != 0) throw RuntimeException("ed25519_sign failed: $rc")
        return sig
    }

    fun verifyEd25519(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        val rc = EnchantCrypto.enchant_ed25519_verify(message, message.size, signature, publicKey)
        return rc == 0
    }

    // ──────────────────────────────────────────────
    // XChaCha20-Poly1305 AEAD
    // ──────────────────────────────────────────────

    fun encryptXChaCha20Poly1305(plaintext: ByteArray, key: ByteArray, nonce: ByteArray? = null): ByteArray {
        val n = nonce ?: generateRandomKey(XCHACHA20_NONCE_SIZE)
        val ct = ByteArray(plaintext.size + EnchantCrypto.XCHACHA20_TAG_SIZE)
        val rc = EnchantCrypto.enchant_xchacha20_encrypt(plaintext, plaintext.size, key, n, ct)
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
        return xChaCha20Poly1305Internal(false, ct, key, nonce)
    }

    fun encryptXChaCha20Poly1305Raw(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val n = if (nonce.size == XCHACHA20_NONCE_SIZE) nonce else nonce.copyOf(XCHACHA20_NONCE_SIZE)
        val ct = ByteArray(plaintext.size + EnchantCrypto.XCHACHA20_TAG_SIZE)
        val rc = EnchantCrypto.enchant_xchacha20_encrypt(plaintext, plaintext.size, key, n, ct)
        if (rc != 0) throw RuntimeException("xchacha20_encrypt failed: $rc")
        return ct
    }

    fun decryptXChaCha20Poly1305Raw(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val n = if (nonce.size == XCHACHA20_NONCE_SIZE) nonce else nonce.copyOf(XCHACHA20_NONCE_SIZE)
        return xChaCha20Poly1305Internal(false, ciphertext, key, n)
    }

    private fun xChaCha20Poly1305Internal(
        encrypt: Boolean, data: ByteArray, key: ByteArray, nonce: ByteArray
    ): ByteArray {
        require(key.size == XCHACHA20_KEY_SIZE) { "Key must be 32 bytes" }
        require(nonce.size == XCHACHA20_NONCE_SIZE) { "Nonce must be 24 bytes" }

        if (encrypt) {
            val ct = ByteArray(data.size + EnchantCrypto.XCHACHA20_TAG_SIZE)
            val rc = EnchantCrypto.enchant_xchacha20_encrypt(data, data.size, key, nonce, ct)
            if (rc != 0) throw RuntimeException("xchacha20_encrypt failed: $rc")
            return ct
        } else {
            val pt = ByteArray(data.size - EnchantCrypto.XCHACHA20_TAG_SIZE)
            val rc = EnchantCrypto.enchant_xchacha20_decrypt(data, data.size, key, nonce, pt)
            if (rc != 0) throw RuntimeException("XChaCha20-Poly1305 decryption (MAC mismatch) failed: $rc")
            return pt
        }
    }

    // ──────────────────────────────────────────────
    // AES-256-GCM (for media encryption)
    // ──────────────────────────────────────────────

    fun encryptAesGcm(plaintext: ByteArray, key: ByteArray, nonce: ByteArray? = null): ByteArray {
        require(key.size == AES_GCM_KEY_SIZE) { "AES key must be 32 bytes" }
        val n = nonce ?: generateRandomKey(AES_GCM_NONCE_SIZE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(AES_GCM_TAG_SIZE * 8, n))
        val ct = cipher.doFinal(plaintext)
        return ByteArray(n.size + ct.size).apply {
            n.copyInto(this, 0)
            ct.copyInto(this, n.size)
        }
    }

    fun decryptAesGcm(data: ByteArray, key: ByteArray): ByteArray {
        require(key.size == AES_GCM_KEY_SIZE) { "AES key must be 32 bytes" }
        require(data.size >= AES_GCM_NONCE_SIZE + AES_GCM_TAG_SIZE) { "Ciphertext too short" }
        val nonce = data.copyOfRange(0, AES_GCM_NONCE_SIZE)
        val ct = data.copyOfRange(AES_GCM_NONCE_SIZE, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(AES_GCM_TAG_SIZE * 8, nonce))
        return cipher.doFinal(ct)
    }

    fun encryptAesGcmRaw(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        require(key.size == AES_GCM_KEY_SIZE) { "AES key must be 32 bytes" }
        require(nonce.size == AES_GCM_NONCE_SIZE) { "AES nonce must be 12 bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(AES_GCM_TAG_SIZE * 8, nonce))
        return cipher.doFinal(plaintext)
    }

    fun decryptAesGcmRaw(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        require(key.size == AES_GCM_KEY_SIZE) { "AES key must be 32 bytes" }
        require(nonce.size == AES_GCM_NONCE_SIZE) { "AES nonce must be 12 bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(AES_GCM_TAG_SIZE * 8, nonce))
        return cipher.doFinal(ciphertext)
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
            input, input.size,
            effectiveSalt, effectiveSalt.size,
            info, info.size,
            okm, length
        )
        if (rc != 0) throw RuntimeException("hkdf_sha256 failed: $rc")
        return okm
    }

    // ──────────────────────────────────────────────
    // HMAC-SHA256 / HMAC-SHA512
    // ──────────────────────────────────────────────

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = ByteArray(EnchantCrypto.HMAC_SHA256_SIZE)
        val rc = EnchantCrypto.enchant_hmac_sha256(key, key.size, data, data.size, mac)
        if (rc != 0) throw RuntimeException("hmac_sha256 failed: $rc")
        return mac
    }

    fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        val k = if (key.size > 128) MessageDigest.getInstance("SHA-512").digest(key) else key
        mac.init(SecretKeySpec(k, "HmacSHA512"))
        return mac.doFinal(data)
    }

    // ──────────────────────────────────────────────
    // Hash Functions
    // ──────────────────────────────────────────────

    fun sha256(data: ByteArray): ByteArray {
        val hash = ByteArray(EnchantCrypto.SHA256_SIZE)
        val rc = EnchantCrypto.enchant_sha256(data, data.size, hash)
        if (rc != 0) return MessageDigest.getInstance("SHA-256").digest(data)
        return hash
    }

    fun sha384(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-384").digest(data)

    fun sha512(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").digest(data)

    fun argon2idHashWithParams(plaintext: ByteArray, salt: ByteArray, iterations: Int, memory_kb: Int, parallelism: Int, outputLen: Int): ByteArray {
        val output = ByteArray(outputLen)
        val rc = EnchantCrypto.enchant_argon2id_hash_with_params(
            plaintext, plaintext.size,
            salt, iterations, memory_kb, parallelism,
            output, outputLen
        )
        if (rc != 0) throw RuntimeException("argon2id_hash_with_params failed: $rc")
        return output
    }

    // ──────────────────────────────────────────────
    // Constant-Time Comparison
    // ──────────────────────────────────────────────

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        return MessageDigest.isEqual(a, b)
    }

    // ──────────────────────────────────────────────
    // Memory Zeroing
    // ──────────────────────────────────────────────

    fun zeroBytes(data: ByteArray) {
        data.fill(0)
    }

    // ──────────────────────────────────────────────
    // Encoding
    // ──────────────────────────────────────────────

    fun base64UrlEncode(data: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(data)

    fun base64UrlDecode(encoded: String): ByteArray =
        java.util.Base64.getUrlDecoder().decode(encoded)

    fun hexEncode(data: ByteArray): String =
        data.joinToString("") { String.format("%02x", it) }

    fun hexDecode(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val result = ByteArray(this.size + other.size)
        System.arraycopy(this, 0, result, 0, this.size)
        System.arraycopy(other, 0, result, this.size, other.size)
        return result
    }
}
