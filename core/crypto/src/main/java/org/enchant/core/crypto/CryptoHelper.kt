package org.enchant.core.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.KeyFactory
import java.math.BigInteger
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {
    private val rng = SecureRandom()
    private const val AES_GCM_TAG_BITS = 128
    private const val AES_KEY_SIZE = 32
    private const val AES_IV_SIZE = 12
    private const val X25519_KEY_SIZE = 32
    private const val ED25519_KEY_SIZE = 32
    private const val ED25519_SIG_SIZE = 64

    data class KeyPair(val publicKey: ByteArray, val privateKey: ByteArray)

    fun generateX25519KeyPair(): KeyPair {
        val gen = X25519KeyPairGenerator()
        gen.init(X25519KeyGenerationParameters(SecureRandom()))
        val kp = gen.generateKeyPair()
        val priv = (kp.private as X25519PrivateKeyParameters).encoded
        val pub = (kp.public as X25519PublicKeyParameters).encoded
        return KeyPair(publicKey = pub, privateKey = priv)
    }

    fun generateEd25519KeyPair(): KeyPair {
        val seed = ByteArray(32).also { rng.nextBytes(it) }
        val priv = Ed25519PrivateKeyParameters(seed, 0)
        val pub = priv.generatePublicKey()
        return KeyPair(publicKey = pub.encoded, privateKey = seed)
    }

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
        if (uBytes.size < 32) {
            uBytes = ByteArray(32 - uBytes.size).plus(uBytes)
        }
        if (uBytes.size > 32) {
            uBytes = uBytes.copyOfRange(uBytes.size - 32, uBytes.size)
        }
        return uBytes.reversedArray()
    }

    fun x25519DiffieHellman(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val priv = X25519PrivateKeyParameters(privateKey, 0)
        val pub = X25519PublicKeyParameters(publicKey, 0)
        val agreement = X25519Agreement()
        agreement.init(priv)
        val secret = ByteArray(32)
        agreement.calculateAgreement(pub, secret, 0)
        return secret
    }

    fun hkdfSha256(input: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        if (length <= 0) throw IllegalArgumentException("Length must be positive, got $length")
        val effectiveSalt = salt.takeIf { it.isNotEmpty() } ?: ByteArray(32)
        val prk = hmacSha256(effectiveSalt, input)
        val result = ByteArray(length)
        var t = ByteArray(0)
        var counter = 0
        var offset = 0
        while (offset < length) {
            counter++
            val block = hmacSha256(prk, t + info + byteArrayOf(counter.toByte()))
            val copyLen = minOf(block.size, length - offset)
            System.arraycopy(block, 0, result, offset, copyLen)
            t = block
            offset += copyLen
        }
        return result
    }

    fun encryptAesGcm(plaintext: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ct = cipher.doFinal(plaintext)
        return ByteArray(iv.size + ct.size).apply {
            iv.copyInto(this, 0)
            ct.copyInto(this, iv.size)
        }
    }

    fun decryptAesGcm(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        val iv = data.copyOfRange(0, AES_IV_SIZE)
        val ct = data.copyOfRange(AES_IV_SIZE, data.size)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(AES_GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    fun generateRandomKey(size: Int = 32): ByteArray {
        if (size <= 0) throw IllegalArgumentException("Size must be positive")
        val bytes = ByteArray(size)
        rng.nextBytes(bytes)
        return bytes
    }

    fun signEd25519(message: ByteArray, privateKey: ByteArray): ByteArray {
        val sig = Signature.getInstance("Ed25519")
        val factory = KeyFactory.getInstance("Ed25519")
        val keySpec = PKCS8EncodedKeySpec(wrapEd25519Private(privateKey))
        sig.initSign(factory.generatePrivate(keySpec))
        sig.update(message)
        return sig.sign()
    }

    fun verifyEd25519(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return try {
            val sig = Signature.getInstance("Ed25519")
            val factory = KeyFactory.getInstance("Ed25519")
            val keySpec = X509EncodedKeySpec(wrapEd25519Public(publicKey))
            sig.initVerify(factory.generatePublic(keySpec))
            sig.update(message)
            sig.verify(signature)
        } catch (_: Exception) {
            false
        }
    }

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
    fun sha384(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-384").digest(data)
    fun sha512(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").digest(data)

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        return MessageDigest.isEqual(a, b)
    }

    fun zeroBytes(data: ByteArray) {
        data.fill(0)
    }

    fun base64UrlEncode(data: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(data)

    fun base64UrlDecode(encoded: String): ByteArray =
        java.util.Base64.getUrlDecoder().decode(encoded)

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val k = if (key.size > 64) MessageDigest.getInstance("SHA-256").digest(key) else key
        mac.init(SecretKeySpec(k, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val result = ByteArray(this.size + other.size)
        System.arraycopy(this, 0, result, 0, this.size)
        System.arraycopy(other, 0, result, this.size, other.size)
        return result
    }

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
