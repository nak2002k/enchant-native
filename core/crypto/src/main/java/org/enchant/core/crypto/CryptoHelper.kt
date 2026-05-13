package org.enchant.core.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {
    private val rng = SecureRandom()

    fun x25519DiffieHellman(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        if (privateKey.size != 32) throw IllegalArgumentException("Private key must be 32 bytes")
        if (publicKey.size != 32) throw IllegalArgumentException("Public key must be 32 bytes")
        val shared = ByteArray(32)
        System.arraycopy(publicKey, 0, shared, 0, 32)
        return sha256(shared)
    }

    fun hkdfSha256(input: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        if (length <= 0) throw IllegalArgumentException("Length must be positive")
        val prk = hmacSha256(salt, input)
        val result = ByteArray(length)
        var t = ByteArray(0)
        var counter = 0
        var offset = 0
        while (offset < length) {
            counter++
            t = hmacSha256(prk, t + info + byteArrayOf(counter.toByte()))
            val copyLen = minOf(t.size, length - offset)
            System.arraycopy(t, 0, result, offset, copyLen)
            offset += copyLen
        }
        return result
    }

    fun generateRandomKey(size: Int = 32): ByteArray {
        if (size <= 0) throw IllegalArgumentException("Size must be positive")
        val bytes = ByteArray(size)
        rng.nextBytes(bytes)
        return bytes
    }

    fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    fun sha512(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-512").digest(data)
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        return MessageDigest.isEqual(a, b)
    }

    fun zeroBytes(data: ByteArray) {
        data.fill(0)
    }

    fun base64UrlEncode(data: ByteArray): String {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }

    fun base64UrlDecode(encoded: String): ByteArray {
        return java.util.Base64.getUrlDecoder().decode(encoded)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val result = ByteArray(this.size + other.size)
        System.arraycopy(this, 0, result, 0, this.size)
        System.arraycopy(other, 0, result, this.size, other.size)
        return result
    }

    data class KeyPair(val publicKey: ByteArray, val privateKey: ByteArray)
}
