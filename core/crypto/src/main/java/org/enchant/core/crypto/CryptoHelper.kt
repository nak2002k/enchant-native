package org.enchant.core.crypto

import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Helper wrapper around [CryptoPrimitives] providing additional JCA key
 * format wrappers for Ed25519 PKCS8/X509 encoding.
 *
 * All cryptographic operations delegate to CryptoPrimitives (libenchantcrypto).
 */
object CryptoHelper {

    data class KeyPair(val publicKey: ByteArray, val privateKey: ByteArray)

    fun generateX25519KeyPair(): KeyPair {
        val kp = CryptoPrimitives.generateX25519KeyPair()
        return KeyPair(kp.publicKey, kp.privateKey)
    }

    fun generateEd25519KeyPair(): KeyPair {
        val kp = CryptoPrimitives.generateEd25519KeyPair()
        return KeyPair(kp.publicKey, kp.privateKey)
    }

    fun ed25519SkToX25519(sk: ByteArray): ByteArray = CryptoPrimitives.ed25519SkToX25519(sk)

    fun ed25519PkToX25519(pk: ByteArray): ByteArray = CryptoPrimitives.ed25519PkToX25519(pk)

    fun x25519DiffieHellman(privateKey: ByteArray, publicKey: ByteArray): ByteArray =
        CryptoPrimitives.x25519DiffieHellman(privateKey, publicKey)

    fun hkdfSha256(input: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray =
        CryptoPrimitives.hkdfSha256(input, salt, info, length)

    fun encryptXChaCha20Poly1305(plaintext: ByteArray, key: ByteArray, nonce: ByteArray? = null): ByteArray =
        CryptoPrimitives.encryptXChaCha20Poly1305(plaintext, key, nonce)

    fun decryptXChaCha20Poly1305(data: ByteArray, key: ByteArray): ByteArray =
        CryptoPrimitives.decryptXChaCha20Poly1305(data, key)

    fun encryptXChaCha20Poly1305Raw(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray =
        CryptoPrimitives.encryptXChaCha20Poly1305Raw(plaintext, key, nonce)

    fun decryptXChaCha20Poly1305Raw(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray =
        CryptoPrimitives.decryptXChaCha20Poly1305Raw(ciphertext, key, nonce)

    @Deprecated("Use encryptXChaCha20Poly1305", ReplaceWith("encryptXChaCha20Poly1305(plaintext, key)"))
    fun encryptAesGcm(plaintext: ByteArray, key: ByteArray): ByteArray =
        encryptXChaCha20Poly1305(plaintext, key)

    @Deprecated("Use decryptXChaCha20Poly1305", ReplaceWith("decryptXChaCha20Poly1305(data, key)"))
    fun decryptAesGcm(data: ByteArray, key: ByteArray): ByteArray =
        decryptXChaCha20Poly1305(data, key)

    fun generateRandomKey(size: Int = 32): ByteArray = CryptoPrimitives.generateRandomKey(size)

    fun signEd25519(message: ByteArray, privateKey: ByteArray): ByteArray =
        CryptoPrimitives.signEd25519(message, privateKey)

    fun verifyEd25519(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean =
        CryptoPrimitives.verifyEd25519(message, signature, publicKey)

    fun sha256(data: ByteArray): ByteArray = CryptoPrimitives.sha256(data)
    fun sha384(data: ByteArray): ByteArray = CryptoPrimitives.sha384(data)
    fun sha512(data: ByteArray): ByteArray = CryptoPrimitives.sha512(data)

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean =
        CryptoPrimitives.constantTimeEquals(a, b)

    fun zeroBytes(data: ByteArray) = CryptoPrimitives.zeroBytes(data)

    fun base64UrlEncode(data: ByteArray): String = CryptoPrimitives.base64UrlEncode(data)

    fun base64UrlDecode(encoded: String): ByteArray = CryptoPrimitives.base64UrlDecode(encoded)

    private fun wrapEd25519Public(raw: ByteArray): ByteArray {
        if (raw.size == 32) {
            val prefix = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)
            return prefix + raw
        }
        return raw
    }

    private fun wrapEd25519Private(raw: ByteArray): ByteArray {
        if (raw.size == 34 && raw[0].toInt() == 0x04) return raw
        val prefix = byteArrayOf(0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20)
        return prefix + raw
    }

    private fun extractEd25519Public(encoded: ByteArray): ByteArray {
        if (encoded.size == 32) return encoded
        return encoded.copyOfRange(encoded.size - 32, encoded.size)
    }

    private fun extractEd25519Private(encoded: ByteArray): ByteArray {
        if (encoded.size == 34 && encoded[0] == 0x04.toByte()) {
            return encoded.copyOfRange(2, 34)
        }
        return encoded.copyOfRange(encoded.size - 32, encoded.size)
    }
}
