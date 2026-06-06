package org.enchant.core.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NativeStorageService {

    init {
        System.loadLibrary("enchantcrypto_jni")
    }

    const val SUCCESS = 0
    const val ERROR_NULL_POINTER = -1
    const val ERROR_BUFFER_TOO_SMALL = -2
    const val ERROR_INVALID_KEY_SIZE = -3
    const val ERROR_DECRYPTION_FAILED = -6
    const val ERROR_INTERNAL = -99

    const val NONCE_SIZE = 24

    data class StorageEnvelope(
        val version: Int,
        val nonce: ByteArray,
        val ciphertext: ByteArray
    ) {
        fun zero() {
            CryptoPrimitives.zeroBytes(nonce)
            CryptoPrimitives.zeroBytes(ciphertext)
        }
    }

    suspend fun create(): Result<Long> = withContext(Dispatchers.IO) {
        val handle = createNative()
        if (handle == 0L) {
            return@withContext Result.failure(EnchantCryptoException("StorageService creation failed"))
        }
        Result.success(handle)
    }

    fun destroy(handle: Long) {
        if (handle != 0L) {
            destroyNative(handle)
        }
    }

    suspend fun initialize(handle: Long, masterKey: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(masterKey.isNotEmpty()) { "Master key cannot be empty" }

        val rc = initNative(handle, masterKey)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("StorageService initialize failed: $rc"))
        }
        Result.success(Unit)
    }

    suspend fun encryptItem(
        handle: Long,
        plaintext: ByteArray,
        itemId: ByteArray
    ): Result<StorageEnvelope> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(plaintext.isNotEmpty()) { "Plaintext cannot be empty" }
        require(itemId.isNotEmpty()) { "Item ID cannot be empty" }

        val envelopeVersion = ByteArray(4)
        val envelopeNonce = ByteArray(NONCE_SIZE)
        val envelopeCiphertext = ByteArray(plaintext.size + 16 + 32)

        val rc = encryptItemNative(handle, plaintext, itemId, envelopeVersion, envelopeNonce, envelopeCiphertext)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("StorageService encryptItem failed: $rc"))
        }

        val version = java.nio.ByteBuffer.wrap(envelopeVersion).int
        Result.success(StorageEnvelope(version, envelopeNonce, envelopeCiphertext))
    }

    suspend fun decryptItem(
        handle: Long,
        envelope: StorageEnvelope,
        itemId: ByteArray
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(itemId.isNotEmpty()) { "Item ID cannot be empty" }

        val envelopeVersion = java.nio.ByteBuffer.allocate(4).putInt(envelope.version).array()
        val plaintextOut = ByteArray(envelope.ciphertext.size)

        val rc = decryptItemNative(handle, envelopeVersion, envelope.nonce, envelope.ciphertext, itemId, plaintextOut)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("StorageService decryptItem failed: $rc"))
        }

        Result.success(plaintextOut)
    }

    suspend fun rotateMasterKey(handle: Long, newMasterKey: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(newMasterKey.isNotEmpty()) { "New master key cannot be empty" }

        val rc = rotateMasterKeyNative(handle, newMasterKey)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("StorageService rotateMasterKey failed: $rc"))
        }
        Result.success(Unit)
    }

    fun isInitialized(handle: Long): Boolean {
        if (handle == 0L) return false
        return isInitializedNative(handle)
    }

    private external fun createNative(): Long
    private external fun destroyNative(handle: Long)
    private external fun initNative(handle: Long, masterKey: ByteArray): Int
    private external fun encryptItemNative(handle: Long, plaintext: ByteArray, itemId: ByteArray, envelopeVersionOut: ByteArray, envelopeNonceOut: ByteArray, envelopeCiphertextOut: ByteArray): Int
    private external fun decryptItemNative(handle: Long, envelopeVersionIn: ByteArray, envelopeNonceIn: ByteArray, envelopeCiphertextIn: ByteArray, itemId: ByteArray, plaintextOut: ByteArray): Int
    private external fun rotateMasterKeyNative(handle: Long, newMasterKey: ByteArray): Int
    private external fun isInitializedNative(handle: Long): Boolean

    class EnchantCryptoException(message: String) : Exception(message)
}
