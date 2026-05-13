package org.enchant.core.crypto

data class RatchetState(
    val rootKey: ByteArray,
    val sendingChainKey: ByteArray? = null,
    val sendingRatchetKeyPublic: ByteArray? = null,
    val sendingRatchetKeyPrivate: ByteArray? = null,
    val sendingMessageNumber: Int = 0,
    val receivingChainKey: ByteArray? = null,
    val receivingRatchetKeyPublic: ByteArray? = null,
    val receivingMessageNumber: Int = 0,
    val previousSendingChainLength: Int = 0,
    val skippedMessageKeys: MutableMap<String, MessageKey> = mutableMapOf(),
    val version: Int = 1
)

data class MessageKey(
    val key: ByteArray,
    val nonce: ByteArray,
    val chainKey: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
)

data class RatchetMessage(
    val header: ByteArray,
    val ciphertext: ByteArray
)

object DoubleRatchet {
    private const val MAX_SKIPPED_KEYS = 1000

    fun initializeAsAlice(
        sharedSecret: ByteArray,
        theirSignedPrekeyPublic: ByteArray,
        ourIdentityKeyPublic: ByteArray,
        theirIdentityKeyPublic: ByteArray
    ): RatchetState? {
        return null
    }

    fun initializeAsBob(
        sharedSecret: ByteArray,
        theirEphemeralKeyPublic: ByteArray,
        ourIdentityKeyPublic: ByteArray,
        theirIdentityKeyPublic: ByteArray
    ): RatchetState? {
        return null
    }

    fun encrypt(state: RatchetState, plaintext: ByteArray, ad: ByteArray? = null): Pair<RatchetState, RatchetMessage>? {
        return null
    }

    fun decrypt(state: RatchetState, message: RatchetMessage, ad: ByteArray? = null): Pair<RatchetState, ByteArray>? {
        return null
    }

    fun serializeState(state: RatchetState): ByteArray {
        return ByteArray(0)
    }

    fun deserializeState(data: ByteArray): RatchetState? {
        return null
    }
}
