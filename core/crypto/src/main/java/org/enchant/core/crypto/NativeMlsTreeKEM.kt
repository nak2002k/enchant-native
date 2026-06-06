package org.enchant.core.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NativeMlsTreeKEM {

    init {
        System.loadLibrary("enchantcrypto_jni")
    }

    const val SUCCESS = 0
    const val ERROR_NULL_POINTER = -1
    const val ERROR_BUFFER_TOO_SMALL = -2
    const val ERROR_INVALID_KEY_SIZE = -3
    const val ERROR_INTERNAL = -99

    const val NODE_SIZE = 32
    const val PATH_SECRET_SIZE = 32
    const val HMAC_SIZE = 32
    const val GROUP_SECRET_SIZE = 32

    data class DirectPathNode(
        val nodeIndex: Int,
        val publicKey: ByteArray,
        val pathSecrets: List<Pair<Int, ByteArray>>
    )

    data class DirectPath(val nodes: List<DirectPathNode>)

    suspend fun create(): Result<Long> = withContext(Dispatchers.IO) {
        val handle = createNative()
        if (handle == 0L) {
            return@withContext Result.failure(EnchantCryptoException("MlsTreeKEM creation failed"))
        }
        Result.success(handle)
    }

    fun destroy(handle: Long) {
        if (handle != 0L) {
            destroyNative(handle)
        }
    }

    suspend fun initialize(handle: Long, leafSecrets: List<ByteArray>): Result<Unit> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        leafSecrets.forEach { require(it.size == 32) { "Leaf secret must be 32 bytes" } }

        val rc = initializeNative(handle, leafSecrets)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("MlsTreeKEM initialize failed: $rc"))
        }
        Result.success(Unit)
    }

    suspend fun addMember(handle: Long, leafSecret: ByteArray): Result<ByteArray> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(leafSecret.size == 32) { "Leaf secret must be 32 bytes" }

        val newIndex = ByteArray(NODE_SIZE)
        val rc = addMemberNative(handle, leafSecret, newIndex)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("MlsTreeKEM addMember failed: $rc"))
        }
        Result.success(newIndex)
    }

    suspend fun removeMember(handle: Long, leafIndex: Int): Result<Unit> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }

        val rc = removeMemberNative(handle, leafIndex)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("MlsTreeKEM removeMember failed: $rc"))
        }
        Result.success(Unit)
    }

    suspend fun updateLeafKey(handle: Long, leafIndex: Int, newSecret: ByteArray, directPathOut: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(newSecret.size == 32) { "New secret must be 32 bytes" }

        val rc = updateLeafKeyNative(handle, leafIndex, newSecret, directPathOut)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("MlsTreeKEM updateLeafKey failed: $rc"))
        }
        Result.success(Unit)
    }

    suspend fun encryptPath(handle: Long, directPathIn: ByteArray, directPathLen: Int, senderLeafIndex: Int, groupSecret: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }

        val rc = encryptPathNative(handle, directPathIn, directPathLen, senderLeafIndex, groupSecret)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("MlsTreeKEM encryptPath failed: $rc"))
        }
        Result.success(Unit)
    }

    suspend fun decryptPath(handle: Long, directPathIn: ByteArray, directPathLen: Int, receiverLeafIndex: Int, groupSecret: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }

        val rc = decryptPathNative(handle, directPathIn, directPathLen, receiverLeafIndex, groupSecret)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("MlsTreeKEM decryptPath failed: $rc"))
        }
        Result.success(Unit)
    }

    suspend fun computeTreeHash(handle: Long, rootHash: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(rootHash.size >= HMAC_SIZE) { "Root hash buffer too small" }

        val rc = computeTreeHashNative(handle, rootHash)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("MlsTreeKEM computeTreeHash failed: $rc"))
        }
        Result.success(Unit)
    }

    suspend fun getNodePublicKey(handle: Long, nodeIndex: Int): Result<ByteArray> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }

        val publicKey = ByteArray(NODE_SIZE)
        val rc = getNodePublicKeyNative(handle, nodeIndex, publicKey)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("MlsTreeKEM getNodePublicKey failed: $rc"))
        }
        Result.success(publicKey)
    }

    suspend fun setNodePublicKey(handle: Long, nodeIndex: Int, publicKey: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(publicKey.size == NODE_SIZE) { "Public key must be 32 bytes" }

        val rc = setNodePublicKeyNative(handle, nodeIndex, publicKey)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("MlsTreeKEM setNodePublicKey failed: $rc"))
        }
        Result.success(Unit)
    }

    suspend fun setNodePrivateKey(handle: Long, nodeIndex: Int, privateKey: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(privateKey.size == 32) { "Private key must be 32 bytes" }

        val rc = setNodePrivateKeyNative(handle, nodeIndex, privateKey)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("MlsTreeKEM setNodePrivateKey failed: $rc"))
        }
        Result.success(Unit)
    }

    suspend fun getRootPublicKey(handle: Long): Result<ByteArray> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }

        val publicKey = ByteArray(NODE_SIZE)
        val rc = getRootPublicKeyNative(handle, publicKey)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("MlsTreeKEM getRootPublicKey failed: $rc"))
        }
        Result.success(publicKey)
    }

    fun leafCount(handle: Long): Int {
        if (handle == 0L) return 0
        return leafCountNative(handle)
    }

    fun nodeCount(handle: Long): Int {
        if (handle == 0L) return 0
        return nodeCountNative(handle)
    }

    private external fun createNative(): Long
    private external fun destroyNative(handle: Long)
    private external fun initializeNative(handle: Long, leafSecrets: List<ByteArray>): Int
    private external fun addMemberNative(handle: Long, leafSecret: ByteArray, newIndex: ByteArray): Int
    private external fun removeMemberNative(handle: Long, leafIndex: Int): Int
    private external fun updateLeafKeyNative(handle: Long, leafIndex: Int, newSecret: ByteArray, directPathOut: ByteArray): Int
    private external fun encryptPathNative(handle: Long, directPathIn: ByteArray, directPathLen: Int, senderLeafIndex: Int, groupSecret: ByteArray): Int
    private external fun decryptPathNative(handle: Long, directPathIn: ByteArray, directPathLen: Int, receiverLeafIndex: Int, groupSecret: ByteArray): Int
    private external fun computeTreeHashNative(handle: Long, rootHash: ByteArray): Int
    private external fun getNodePublicKeyNative(handle: Long, nodeIndex: Int, publicKey: ByteArray): Int
    private external fun setNodePublicKeyNative(handle: Long, nodeIndex: Int, publicKey: ByteArray): Int
    private external fun setNodePrivateKeyNative(handle: Long, nodeIndex: Int, privateKey: ByteArray): Int
    private external fun getRootPublicKeyNative(handle: Long, publicKey: ByteArray): Int
    private external fun leafCountNative(handle: Long): Int
    private external fun nodeCountNative(handle: Long): Int

    class EnchantCryptoException(message: String) : Exception(message)
}
