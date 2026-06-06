package org.enchant.core.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NativeClientZkProfile {

    init {
        System.loadLibrary("enchantcrypto_jni")
    }

    const val SUCCESS = 0
    const val ERROR_NULL_POINTER = -1
    const val ERROR_BUFFER_TOO_SMALL = -2
    const val ERROR_INTERNAL = -99

    const val UUID_SIZE = 16
    const val PROFILE_KEY_SIZE = 32

    data class EncryptedProfile(
        val version: Int,
        val encryptedData: ByteArray
    ) {
        fun zero() {
            CryptoPrimitives.zeroBytes(encryptedData)
        }
    }

    suspend fun create(): Result<Long> = withContext(Dispatchers.IO) {
        val handle = createNative()
        if (handle == 0L) {
            return@withContext Result.failure(EnchantCryptoException("ClientZkProfile creation failed"))
        }
        Result.success(handle)
    }

    fun destroy(handle: Long) {
        if (handle != 0L) {
            destroyNative(handle)
        }
    }

    suspend fun initialize(
        handle: Long,
        serverPublicParams: ByteArray,
        groupSecretParams: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(serverPublicParams.isNotEmpty()) { "Server public params cannot be empty" }
        require(groupSecretParams.isNotEmpty()) { "Group secret params cannot be empty" }

        val rc = initNative(handle, serverPublicParams, groupSecretParams)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("ClientZkProfile initialize failed: $rc"))
        }
        Result.success(Unit)
    }

    suspend fun encryptProfileForStorage(
        handle: Long,
        profile: ByteArray,
        profileKey: ByteArray
    ): Result<EncryptedProfile> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(profile.isNotEmpty()) { "Profile cannot be empty" }
        require(profileKey.size == PROFILE_KEY_SIZE) { "Profile key must be 32 bytes" }

        val encryptedData = ByteArray(profile.size + 64)
        val versionOut = IntArray(1)

        val rc = encryptProfileForStorageNative(handle, profile, profileKey, encryptedData, versionOut)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("ClientZkProfile encryptProfileForStorage failed: $rc"))
        }

        Result.success(EncryptedProfile(versionOut[0], encryptedData))
    }

    suspend fun decryptProfile(
        handle: Long,
        encryptedData: ByteArray,
        version: Int,
        profileKey: ByteArray
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(encryptedData.isNotEmpty()) { "Encrypted data cannot be empty" }
        require(profileKey.size == PROFILE_KEY_SIZE) { "Profile key must be 32 bytes" }

        val plaintextOut = ByteArray(encryptedData.size)

        val rc = decryptProfileNative(handle, encryptedData, version, profileKey, plaintextOut)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("ClientZkProfile decryptProfile failed: $rc"))
        }

        Result.success(plaintextOut)
    }

    suspend fun showUuidFromCredential(
        handle: Long,
        credential: ByteArray,
        uuid: ByteArray,
        profileKey: ByteArray,
        randomness: ByteArray
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(uuid.size == UUID_SIZE) { "UUID must be 16 bytes" }
        require(profileKey.size == PROFILE_KEY_SIZE) { "Profile key must be 32 bytes" }

        val presentationOut = ByteArray(512)

        val rc = showUuidFromCredentialNative(handle, credential, uuid, profileKey, randomness, presentationOut)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("ClientZkProfile showUuidFromCredential failed: $rc"))
        }

        Result.success(presentationOut)
    }

    suspend fun getProfileKeyVersion(
        handle: Long,
        profileKey: ByteArray
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(profileKey.size == PROFILE_KEY_SIZE) { "Profile key must be 32 bytes" }

        val versionOut = ByteArray(32)

        val rc = getProfileKeyVersionNative(handle, profileKey, versionOut)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("ClientZkProfile getProfileKeyVersion failed: $rc"))
        }

        Result.success(versionOut)
    }

    private external fun createNative(): Long
    private external fun destroyNative(handle: Long)
    private external fun initNative(handle: Long, serverPublicParams: ByteArray, groupSecretParams: ByteArray): Int
    private external fun encryptProfileForStorageNative(handle: Long, profile: ByteArray, profileKey: ByteArray, encryptedDataOut: ByteArray, versionOut: IntArray): Int
    private external fun decryptProfileNative(handle: Long, encryptedDataIn: ByteArray, versionIn: Int, profileKey: ByteArray, plaintextOut: ByteArray): Int
    private external fun showUuidFromCredentialNative(handle: Long, credential: ByteArray, uuid: ByteArray, profileKey: ByteArray, randomness: ByteArray, presentationOut: ByteArray): Int
    private external fun getProfileKeyVersionNative(handle: Long, profileKey: ByteArray, versionOut: ByteArray): Int

    class EnchantCryptoException(message: String) : Exception(message)
}
