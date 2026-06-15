package org.enchant.core.crypto

/**
 * Helper wrapper around [CryptoPrimitives] providing convenient aliases for
 * the most common crypto operations.
 *
 * All cryptographic operations delegate to CryptoPrimitives (libenchantcrypto).
 */
object CryptoHelper {

    data class KeyPair(val publicKey: ByteArray, val privateKey: ByteArray)

    fun generateX25519KeyPair(): KeyPair = CryptoPrimitives.generateX25519KeyPair()
    fun generateEd25519KeyPair(): KeyPair = CryptoPrimitives.generateEd25519KeyPair()

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

    fun generateRandomKey(size: Int = 32): ByteArray = CryptoPrimitives.generateRandomKey(size)
    fun signEd25519(message: ByteArray, privateKey: ByteArray): ByteArray =
        CryptoPrimitives.signEd25519(message, privateKey)
    fun verifyEd25519(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean =
        CryptoPrimitives.verifyEd25519(message, signature, publicKey)

    fun sha256(data: ByteArray): ByteArray = CryptoPrimitives.sha256(data)
    fun sha384(data: ByteArray): ByteArray = CryptoPrimitives.sha384(data)
    fun sha512(data: ByteArray): ByteArray = CryptoPrimitives.sha512(data)
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = CryptoPrimitives.constantTimeEquals(a, b)
    fun zeroBytes(data: ByteArray) = CryptoPrimitives.zeroBytes(data)
    fun base64UrlEncode(data: ByteArray): String = CryptoPrimitives.base64UrlEncode(data)
    fun base64UrlDecode(encoded: String): ByteArray = CryptoPrimitives.base64UrlDecode(encoded)
}
