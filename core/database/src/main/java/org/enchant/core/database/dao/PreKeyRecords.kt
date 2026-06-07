package org.enchant.core.database.dao

data class SignedPreKeyRecord(
    val id: Int,
    val publicKey: ByteArray,
    val privateKey: ByteArray,
    val signature: ByteArray,
    val timestamp: Long
) {
    fun copy(): SignedPreKeyRecord = SignedPreKeyRecord(
        id = id,
        publicKey = publicKey.copyOf(),
        privateKey = privateKey.copyOf(),
        signature = signature.copyOf(),
        timestamp = timestamp
    )
}

data class OneTimePreKeyRecord(
    val id: Int,
    val publicKey: ByteArray,
    val privateKey: ByteArray,
    val timestamp: Long,
    val isLastResort: Boolean = false
)

data class PreKeyPublic(
    val id: Int,
    val publicKey: ByteArray
)

interface PreKeyDao {
    suspend fun storeSignedPreKey(record: SignedPreKeyRecord)
    suspend fun loadSignedPreKeys(): List<SignedPreKeyRecord>
    suspend fun deleteSignedPreKey(id: Int)
    suspend fun storeOneTimePreKeys(records: List<OneTimePreKeyRecord>)
    suspend fun loadOneTimePreKeys(): List<OneTimePreKeyRecord>
    suspend fun deleteOneTimePreKey(id: Int)
}
