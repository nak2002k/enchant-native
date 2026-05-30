package org.enchant.core.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NativeSenderKey {

    init {
        System.loadLibrary("enchantcrypto_jni")
    }

    const val SUCCESS = 0
    const val ERROR_NULL_POINTER = -1
    const val ERROR_BUFFER_TOO_SMALL = -2
    const val ERROR_INVALID_KEY_SIZE = -3
    const val ERROR_DECRYPTION_FAILED = -6
    const val ERROR_INVALID_FORMAT = -11
    const val ERROR_INTERNAL = -99

    const val SENDER_KEY_SEED_SIZE = 32
    const val SENDER_KEY_IV_SIZE = 16
    const val SENDER_KEY_CIPHER_KEY_SIZE = 32
    const val SENDER_KEY_ITERATION_SIZE = 4
    const val SENDER_KEY_MAX_FORWARD_JUMPS = 2000

    data class SenderKeyState(
        val senderId: String,
        val keyId: Int,
        val chainKey: ByteArray,
        val iteration: Int,
        val epoch: Int
    ) {
        fun zero() {
            CryptoPrimitives.zeroBytes(chainKey)
        }
    }

    data class DistributionMessage(
        val senderKeyId: Int,
        val epoch: Int,
        val iteration: Int,
        val chainKey: ByteArray,
        val signature: ByteArray
    )

    suspend fun createSenderKey(
        senderId: String,
        keyId: Int
    ): Result<SenderKeyState> = withContext(Dispatchers.IO) {
        require(senderId.isNotEmpty()) { "senderId cannot be empty" }

        val statePtr = createSenderKeyNative(senderId, keyId)
        if (statePtr == 0L) {
            return@withContext Result.failure(EnchantCryptoException("createSenderKey failed"))
        }

        val chainKey = ByteArray(SENDER_KEY_SEED_SIZE)
        val iteration = IntArray(1)
        val epoch = IntArray(1)

        val rc = getSenderKeyStateNative(
            statePtr,
            chainKey,
            iteration,
            epoch
        )

        if (rc != SUCCESS) {
            destroySenderKeyNative(statePtr)
            return@withContext Result.failure(EnchantCryptoException("getSenderKeyState failed: $rc"))
        }

        Result.success(SenderKeyState(senderId, keyId, chainKey, iteration[0], epoch[0]))
    }

    suspend fun encryptSenderKey(
        stateHandle: Long,
        plaintext: ByteArray
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        require(plaintext.isNotEmpty()) { "plaintext cannot be empty" }
        require(stateHandle != 0L) { "Invalid state handle" }

        val maxOutput = 4 + plaintext.size + 16
        val output = ByteArray(maxOutput)
        val outputLen = IntArray(1)

        val rc = encryptSenderKeyNative(
            stateHandle,
            plaintext,
            plaintext.size,
            output,
            outputLen
        )

        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("encryptSenderKey failed: $rc"))
        }

        Result.success(output.copyOf(outputLen[0]))
    }

    suspend fun decryptSenderKey(
        stateHandle: Long,
        ciphertext: ByteArray
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        require(ciphertext.size > 4 + 16) { "Ciphertext too short" }
        require(stateHandle != 0L) { "Invalid state handle" }

        val maxOutput = ciphertext.size - 4 - 16
        val output = ByteArray(maxOutput)
        val outputLen = IntArray(1)

        val rc = decryptSenderKeyNative(
            stateHandle,
            ciphertext,
            ciphertext.size,
            output,
            outputLen
        )

        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("decryptSenderKey failed: $rc"))
        }

        Result.success(output.copyOf(outputLen[0]))
    }

    suspend fun createDistributionMessage(
        stateHandle: Long,
        signingPrivate: ByteArray
    ): Result<DistributionMessage> = withContext(Dispatchers.IO) {
        require(signingPrivate.size == EnchantCrypto.ED25519_SEED_SIZE) { "Invalid signing private key size" }
        require(stateHandle != 0L) { "Invalid state handle" }

        val output = ByteArray(4 + 4 + 4 + SENDER_KEY_SEED_SIZE + EnchantCrypto.ED25519_SIGNATURE_SIZE)
        val outputLen = IntArray(1)

        val rc = createDistributionMessageNative(
            stateHandle,
            signingPrivate,
            output,
            outputLen
        )

        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("createDistributionMessage failed: $rc"))
        }

        val buffer = output.copyOf(outputLen[0])
        val senderKeyId = java.nio.ByteBuffer.wrap(buffer, 0, 4).int
        val epoch = java.nio.ByteBuffer.wrap(buffer, 4, 4).int
        val iteration = java.nio.ByteBuffer.wrap(buffer, 8, 4).int
        val chainKey = buffer.copyOfRange(12, 12 + SENDER_KEY_SEED_SIZE)
        val signature = buffer.copyOfRange(12 + SENDER_KEY_SEED_SIZE, buffer.size)

        Result.success(DistributionMessage(senderKeyId, epoch, iteration, chainKey, signature))
    }

    suspend fun processDistributionMessage(
        stateHandle: Long,
        message: ByteArray,
        signingPublic: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        require(message.size >= 4 + 4 + 4 + SENDER_KEY_SEED_SIZE + EnchantCrypto.ED25519_SIGNATURE_SIZE) { "Message too short" }
        require(signingPublic.size == EnchantCrypto.ED25519_PUBLIC_KEY_SIZE) { "Invalid signing public key size" }
        require(stateHandle != 0L) { "Invalid state handle" }

        val rc = processDistributionMessageNative(
            stateHandle,
            message,
            message.size,
            signingPublic
        )

        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("processDistributionMessage failed: $rc"))
        }

        Result.success(Unit)
    }

    suspend fun serializeSenderKeyRecord(
        stateHandle: Long
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        require(stateHandle != 0L) { "Invalid state handle" }

        val maxOutput = 8192
        val output = ByteArray(maxOutput)
        val outputLen = IntArray(1)

        val rc = serializeSenderKeyRecordNative(
            stateHandle,
            output,
            outputLen
        )

        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("serializeSenderKeyRecord failed: $rc"))
        }

        Result.success(output.copyOf(outputLen[0]))
    }

    suspend fun deserializeSenderKeyRecord(
        data: ByteArray
    ): Result<Long> = withContext(Dispatchers.IO) {
        require(data.isNotEmpty()) { "Data cannot be empty" }

        val stateHandle = deserializeSenderKeyRecordNative(data, data.size)
        if (stateHandle == 0L) {
            return@withContext Result.failure(EnchantCryptoException("deserializeSenderKeyRecord failed"))
        }

        Result.success(stateHandle)
    }

    fun destroySenderKey(stateHandle: Long) {
        if (stateHandle != 0L) {
            destroySenderKeyNative(stateHandle)
        }
    }

    private external fun createSenderKeyNative(senderId: String, keyId: Int): Long

    private external fun getSenderKeyStateNative(
        stateHandle: Long,
        chainKey: ByteArray,
        iteration: IntArray,
        epoch: IntArray
    ): Int

    private external fun encryptSenderKeyNative(
        stateHandle: Long,
        plaintext: ByteArray,
        plaintextLen: Int,
        output: ByteArray,
        outputLen: IntArray
    ): Int

    private external fun decryptSenderKeyNative(
        stateHandle: Long,
        ciphertext: ByteArray,
        ciphertextLen: Int,
        plaintext: ByteArray,
        plaintextLen: IntArray
    ): Int

    private external fun createDistributionMessageNative(
        stateHandle: Long,
        signingPrivate: ByteArray,
        output: ByteArray,
        outputLen: IntArray
    ): Int

    private external fun processDistributionMessageNative(
        stateHandle: Long,
        message: ByteArray,
        messageLen: Int,
        signingPublic: ByteArray
    ): Int

    private external fun serializeSenderKeyRecordNative(
        stateHandle: Long,
        output: ByteArray,
        outputLen: IntArray
    ): Int

    private external fun deserializeSenderKeyRecordNative(
        data: ByteArray,
        dataLen: Int
    ): Long

    private external fun destroySenderKeyNative(stateHandle: Long)

    class EnchantCryptoException(message: String) : Exception(message)
}