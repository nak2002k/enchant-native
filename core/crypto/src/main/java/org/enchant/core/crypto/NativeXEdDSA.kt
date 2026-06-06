package org.enchant.core.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NativeXEdDSA {

    init {
        System.loadLibrary("enchantcrypto_jni")
    }

    const val SUCCESS = 0
    const val ERROR_NULL_POINTER = -1
    const val ERROR_INVALID_KEY_SIZE = -3
    const val ERROR_INTERNAL = -99

    const val SIGNATURE_SIZE = 64
    const val PUBLIC_KEY_SIZE = 32
    const val PRIVATE_KEY_SIZE = 32

    suspend fun sign(
        message: ByteArray,
        x25519PrivateKey: ByteArray
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        require(x25519PrivateKey.size == PRIVATE_KEY_SIZE) { "Invalid X25519 private key size" }
        require(message.isNotEmpty()) { "Message cannot be empty" }

        val signature = ByteArray(SIGNATURE_SIZE)

        val rc = signNative(message, x25519PrivateKey, signature)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("XEdDSA sign failed: $rc"))
        }

        Result.success(signature)
    }

    suspend fun verify(
        message: ByteArray,
        signature: ByteArray,
        x25519PublicKey: ByteArray
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        require(signature.size == SIGNATURE_SIZE) { "Invalid signature size" }
        require(x25519PublicKey.size == PUBLIC_KEY_SIZE) { "Invalid X25519 public key size" }
        require(message.isNotEmpty()) { "Message cannot be empty" }

        val rc = verifyNative(message, signature, x25519PublicKey)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("XEdDSA verify failed: $rc"))
        }

        Result.success(true)
    }

    suspend fun derivePublicKey(
        x25519PrivateKey: ByteArray
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        require(x25519PrivateKey.size == PRIVATE_KEY_SIZE) { "Invalid X25519 private key size" }

        val publicKey = ByteArray(PUBLIC_KEY_SIZE)

        val rc = derivePublicKeyNative(x25519PrivateKey, publicKey)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("XEdDSA derive public key failed: $rc"))
        }

        Result.success(publicKey)
    }

    private external fun signNative(
        message: ByteArray,
        x25519PrivateKey: ByteArray,
        signature: ByteArray
    ): Int

    private external fun verifyNative(
        message: ByteArray,
        signature: ByteArray,
        x25519PublicKey: ByteArray
    ): Int

    private external fun derivePublicKeyNative(
        x25519PrivateKey: ByteArray,
        xeddsaPublicKey: ByteArray
    ): Int

    class EnchantCryptoException(message: String) : Exception(message)
}
