package org.enchant.core.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NativeGroupsV2 {

    init {
        System.loadLibrary("enchantcrypto_jni")
    }

    const val SUCCESS = 0
    const val ERROR_NULL_POINTER = -1
    const val ERROR_BUFFER_TOO_SMALL = -2
    const val ERROR_INVALID_KEY_SIZE = -3
    const val ERROR_INTERNAL = -99

    const val GROUP_ID_SIZE = 32
    const val EPOCH_SECRET_SIZE = 32
    const val MEMBER_ID_SIZE = 32

    data class GroupState(
        val groupId: ByteArray,
        val epochSecret: ByteArray
    ) {
        fun zero() {
            CryptoPrimitives.zeroBytes(groupId)
            CryptoPrimitives.zeroBytes(epochSecret)
        }
    }

    data class GroupCommit(
        val epochSecret: ByteArray,
        val newEpoch: Int
    ) {
        fun zero() {
            CryptoPrimitives.zeroBytes(epochSecret)
        }
    }

    suspend fun create(): Result<Long> = withContext(Dispatchers.IO) {
        val handle = createNative()
        if (handle == 0L) {
            return@withContext Result.failure(EnchantCryptoException("GroupsV2 creation failed"))
        }
        Result.success(handle)
    }

    fun destroy(handle: Long) {
        if (handle != 0L) {
            destroyNative(handle)
        }
    }

    suspend fun createGroup(
        handle: Long,
        creatorId: ByteArray,
        creatorSecret: ByteArray,
        title: String
    ): Result<GroupState> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(creatorId.size == MEMBER_ID_SIZE) { "Creator ID must be 32 bytes" }
        require(creatorSecret.size == 32) { "Creator secret must be 32 bytes" }

        val groupId = ByteArray(GROUP_ID_SIZE)
        val epochSecret = ByteArray(EPOCH_SECRET_SIZE)

        val rc = createGroupNative(handle, creatorId, creatorSecret, title, groupId, epochSecret)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("GroupsV2 createGroup failed: $rc"))
        }

        Result.success(GroupState(groupId, epochSecret))
    }

    suspend fun addMember(
        handle: Long,
        groupId: ByteArray,
        epochSecret: ByteArray,
        newMemberId: ByteArray,
        newMemberSecret: ByteArray
    ): Result<GroupCommit> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(groupId.size == GROUP_ID_SIZE) { "Group ID must be 32 bytes" }
        require(epochSecret.size == EPOCH_SECRET_SIZE) { "Epoch secret must be 32 bytes" }
        require(newMemberId.size == MEMBER_ID_SIZE) { "Member ID must be 32 bytes" }
        require(newMemberSecret.size == 32) { "Member secret must be 32 bytes" }

        val commitEpoch = ByteArray(EPOCH_SECRET_SIZE)

        val rc = addMemberNative(handle, groupId, epochSecret, newMemberId, newMemberSecret, commitEpoch)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("GroupsV2 addMember failed: $rc"))
        }

        Result.success(GroupCommit(commitEpoch, 0))
    }

    suspend fun removeMember(
        handle: Long,
        groupId: ByteArray,
        epochSecret: ByteArray,
        targetMemberId: ByteArray
    ): Result<GroupCommit> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(groupId.size == GROUP_ID_SIZE) { "Group ID must be 32 bytes" }
        require(epochSecret.size == EPOCH_SECRET_SIZE) { "Epoch secret must be 32 bytes" }
        require(targetMemberId.size == MEMBER_ID_SIZE) { "Target member ID must be 32 bytes" }

        val commitEpoch = ByteArray(EPOCH_SECRET_SIZE)

        val rc = removeMemberNative(handle, groupId, epochSecret, targetMemberId, commitEpoch)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("GroupsV2 removeMember failed: $rc"))
        }

        Result.success(GroupCommit(commitEpoch, 0))
    }

    suspend fun applyCommit(
        handle: Long,
        groupId: ByteArray,
        epochSecret: ByteArray,
        commitEpoch: ByteArray
    ): Result<GroupState> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(groupId.size == GROUP_ID_SIZE) { "Group ID must be 32 bytes" }
        require(epochSecret.size == EPOCH_SECRET_SIZE) { "Epoch secret must be 32 bytes" }
        require(commitEpoch.size == EPOCH_SECRET_SIZE) { "Commit epoch must be 32 bytes" }

        val newEpochSecret = ByteArray(EPOCH_SECRET_SIZE)

        val rc = applyCommitNative(handle, groupId, epochSecret, commitEpoch, newEpochSecret)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("GroupsV2 applyCommit failed: $rc"))
        }

        Result.success(GroupState(groupId, newEpochSecret))
    }

    suspend fun serializeGroupState(
        handle: Long,
        groupId: ByteArray,
        epochSecret: ByteArray
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(groupId.size == GROUP_ID_SIZE) { "Group ID must be 32 bytes" }
        require(epochSecret.size == EPOCH_SECRET_SIZE) { "Epoch secret must be 32 bytes" }

        val output = ByteArray(4096)
        val rc = serializeGroupStateNative(handle, groupId, epochSecret, output)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("GroupsV2 serializeGroupState failed: $rc"))
        }

        Result.success(output)
    }

    suspend fun deserializeGroupState(
        handle: Long,
        data: ByteArray
    ): Result<GroupState> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(data.isNotEmpty()) { "Data cannot be empty" }

        val groupId = ByteArray(GROUP_ID_SIZE)
        val epochSecret = ByteArray(EPOCH_SECRET_SIZE)

        val rc = deserializeGroupStateNative(handle, data, data.size, groupId, epochSecret)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("GroupsV2 deserializeGroupState failed: $rc"))
        }

        Result.success(GroupState(groupId, epochSecret))
    }

    suspend fun getMemberCount(
        handle: Long,
        groupId: ByteArray,
        epochSecret: ByteArray
    ): Result<Int> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(groupId.size == GROUP_ID_SIZE) { "Group ID must be 32 bytes" }
        require(epochSecret.size == EPOCH_SECRET_SIZE) { "Epoch secret must be 32 bytes" }

        val count = getMemberCountNative(handle, groupId, epochSecret)
        if (count < 0) {
            return@withContext Result.failure(EnchantCryptoException("GroupsV2 getMemberCount failed"))
        }

        Result.success(count)
    }

    suspend fun updateMemberKey(
        handle: Long,
        groupId: ByteArray,
        epochSecret: ByteArray,
        memberId: ByteArray,
        newSecret: ByteArray
    ): Result<GroupCommit> = withContext(Dispatchers.IO) {
        require(handle != 0L) { "Invalid handle" }
        require(groupId.size == GROUP_ID_SIZE) { "Group ID must be 32 bytes" }
        require(epochSecret.size == EPOCH_SECRET_SIZE) { "Epoch secret must be 32 bytes" }
        require(memberId.size == MEMBER_ID_SIZE) { "Member ID must be 32 bytes" }
        require(newSecret.size == 32) { "New secret must be 32 bytes" }

        val commitEpoch = ByteArray(EPOCH_SECRET_SIZE)

        val rc = updateMemberKeyNative(handle, groupId, epochSecret, memberId, newSecret, commitEpoch)
        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("GroupsV2 updateMemberKey failed: $rc"))
        }

        Result.success(GroupCommit(commitEpoch, 0))
    }

    private external fun createNative(): Long
    private external fun destroyNative(handle: Long)
    private external fun createGroupNative(handle: Long, creatorId: ByteArray, creatorSecret: ByteArray, title: String, groupIdOut: ByteArray, epochSecretOut: ByteArray): Int
    private external fun addMemberNative(handle: Long, groupIdIn: ByteArray, epochSecretIn: ByteArray, newMemberId: ByteArray, newMemberSecret: ByteArray, commitEpochOut: ByteArray): Int
    private external fun removeMemberNative(handle: Long, groupIdIn: ByteArray, epochSecretIn: ByteArray, targetMemberId: ByteArray, commitEpochOut: ByteArray): Int
    private external fun applyCommitNative(handle: Long, groupIdIn: ByteArray, epochSecretIn: ByteArray, commitEpochIn: ByteArray, epochSecretOut: ByteArray): Int
    private external fun serializeGroupStateNative(handle: Long, groupIdIn: ByteArray, epochSecretIn: ByteArray, output: ByteArray): Int
    private external fun deserializeGroupStateNative(handle: Long, data: ByteArray, dataLen: Int, groupIdOut: ByteArray, epochSecretOut: ByteArray): Int
    private external fun getMemberCountNative(handle: Long, groupIdIn: ByteArray, epochSecretIn: ByteArray): Int
    private external fun updateMemberKeyNative(handle: Long, groupIdIn: ByteArray, epochSecretIn: ByteArray, memberId: ByteArray, newSecret: ByteArray, commitEpochOut: ByteArray): Int

    class EnchantCryptoException(message: String) : Exception(message)
}
