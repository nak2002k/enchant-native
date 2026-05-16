package org.enchant.core.crypto

import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    private const val HEADER_SIZE = 44
    private const val DH_KEY_SIZE = 32
    private const val MSG_NUM_SIZE = 4
    private const val PCL_SIZE = 4

    fun initializeAsAlice(
        sharedSecret: ByteArray,
        theirSignedPrekeyPublic: ByteArray,
        ourIdentityKeyPublic: ByteArray,
        theirIdentityKeyPublic: ByteArray
    ): RatchetState {
        val salt = ByteArray(32)
        val sendingKeyPair = CryptoHelper.generateX25519KeyPair()

        val dhOut = CryptoHelper.x25519DiffieHellman(sendingKeyPair.privateKey, theirSignedPrekeyPublic)
        val rootMaterial = CryptoHelper.hkdfSha256(sharedSecret + dhOut, salt, "EnchantRatchet".encodeToByteArray(), 64)
        CryptoHelper.zeroBytes(dhOut)

        return RatchetState(
            rootKey = rootMaterial.copyOfRange(0, 32),
            sendingChainKey = rootMaterial.copyOfRange(32, 64),
            sendingRatchetKeyPublic = sendingKeyPair.publicKey,
            sendingRatchetKeyPrivate = sendingKeyPair.privateKey,
            sendingMessageNumber = 0,
            receivingChainKey = rootMaterial.copyOfRange(32, 64),
            receivingRatchetKeyPublic = theirSignedPrekeyPublic,
            receivingMessageNumber = 0,
            previousSendingChainLength = 0
        )
    }

    fun initializeAsBob(
        sharedSecret: ByteArray,
        theirEphemeralKeyPublic: ByteArray,
        ourIdentityKeyPublic: ByteArray,
        theirIdentityKeyPublic: ByteArray
    ): RatchetState {
        val salt = ByteArray(32)
        val receivingKeyPair = CryptoHelper.generateX25519KeyPair()

        val dhOut = CryptoHelper.x25519DiffieHellman(receivingKeyPair.privateKey, theirEphemeralKeyPublic)
        val rootMaterial = CryptoHelper.hkdfSha256(sharedSecret + dhOut, salt, "EnchantRatchet".encodeToByteArray(), 64)
        CryptoHelper.zeroBytes(dhOut)

        return RatchetState(
            rootKey = rootMaterial.copyOfRange(0, 32),
            receivingChainKey = rootMaterial.copyOfRange(32, 64),
            receivingRatchetKeyPublic = receivingKeyPair.publicKey,
            receivingMessageNumber = 0,
            rootKey = rootMaterial.copyOfRange(0, 32),
            receivingChainKey = rootMaterial.copyOfRange(32, 64),
            receivingRatchetKeyPublic = theirEphemeralKeyPublic,
            receivingMessageNumber = 0
        )
    }

    fun encrypt(state: RatchetState, plaintext: ByteArray, ad: ByteArray? = null): Pair<RatchetState, RatchetMessage> {
        var s = state
        val adBytes = ad ?: ByteArray(0)

        if (s.sendingChainKey == null) {
            val dhPair = CryptoHelper.generateX25519KeyPair()
            val dhOut = CryptoHelper.x25519DiffieHellman(dhPair.privateKey, s.receivingRatchetKeyPublic!!)
            val rootMaterial = CryptoHelper.hkdfSha256(s.rootKey + dhOut, ByteArray(32), "EnchantRatchet".encodeToByteArray(), 64)
            CryptoHelper.zeroBytes(dhOut)
            s = s.copy(
                rootKey = rootMaterial.copyOfRange(0, 32),
                sendingChainKey = rootMaterial.copyOfRange(32, 64),
                sendingRatchetKeyPublic = dhPair.publicKey,
                sendingRatchetKeyPrivate = dhPair.privateKey,
                sendingMessageNumber = 0
            )
        }

        val chainKey = s.sendingChainKey ?: return null
        val msgKeyData = CryptoHelper.hkdfSha256(chainKey, ByteArray(32), "EnchantMsg".encodeToByteArray(), 80)
        val msgKey = MessageKey(
            key = msgKeyData.copyOfRange(0, 32),
            nonce = msgKeyData.copyOfRange(32, 44),
            chainKey = msgKeyData.copyOfRange(44, 76)
        )
        val nextChainKey = msgKeyData.copyOfRange(44, 76)
        CryptoHelper.zeroBytes(msgKeyData)

        val ciphertext = CryptoHelper.encryptAesGcm(plaintext, msgKey.key)
        val headerNonce = msgKey.nonce

        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN).apply {
            put(s.sendingRatchetKeyPublic ?: ByteArray(DH_KEY_SIZE))
            putInt(s.sendingMessageNumber)
            putInt(s.receivingMessageNumber)
            putInt(s.previousSendingChainLength)
        }.array()

        s = s.copy(
            sendingChainKey = nextChainKey,
            sendingMessageNumber = s.sendingMessageNumber + 1
        )

        return Pair(s, RatchetMessage(header = header, ciphertext = ciphertext))
    }

    fun decrypt(state: RatchetState, message: RatchetMessage, ad: ByteArray? = null): Pair<RatchetState, ByteArray> {
        var s = state
        val adBytes = ad ?: ByteArray(0)
        val header = parseHeader(message.header) ?: return Pair(s, ByteArray(0))

        if (header.dhPublicKey.contentEquals(s.receivingRatchetKeyPublic).not()) {
            val dhOut = CryptoHelper.x25519DiffieHellman(s.sendingRatchetKeyPrivate!!, header.dhPublicKey)
            val rootMaterial = CryptoHelper.hkdfSha256(s.rootKey + dhOut, ByteArray(32), "EnchantRatchet".encodeToByteArray(), 64)
            CryptoHelper.zeroBytes(dhOut)

            s = s.copy(
                rootKey = rootMaterial.copyOfRange(0, 32),
                receivingChainKey = rootMaterial.copyOfRange(32, 64),
                receivingRatchetKeyPublic = header.dhPublicKey,
                receivingMessageNumber = 0
            )

            val newDhPair = CryptoHelper.generateX25519KeyPair()
            val dhOut2 = CryptoHelper.x25519DiffieHellman(newDhPair.privateKey, header.dhPublicKey)
            val rootMaterial2 = CryptoHelper.hkdfSha256(s.rootKey + dhOut2, ByteArray(32), "EnchantRatchet".encodeToByteArray(), 64)
            CryptoHelper.zeroBytes(dhOut2)

            s = s.copy(
                rootKey = rootMaterial2.copyOfRange(0, 32),
                sendingChainKey = rootMaterial2.copyOfRange(32, 64),
                sendingRatchetKeyPublic = newDhPair.publicKey,
                sendingRatchetKeyPrivate = newDhPair.privateKey,
                sendingMessageNumber = 0,
                previousSendingChainLength = 0
            )
        }

        val skipKey = "${ByteBuffer.wrap(header.dhPublicKey).long}:${header.messageNumberSend}"
        val existingSkip = s.skippedMessageKeys[skipKey]
        if (existingSkip != null) {
            s.skippedMessageKeys.remove(skipKey)
            val plaintext = CryptoHelper.decryptAesGcm(message.ciphertext, existingSkip.key)
            return Pair(s, plaintext)
        }

        var chainKey = s.receivingChainKey ?: return Pair(s, ByteArray(0))
        var msgNum = s.receivingMessageNumber
        val skipMap = s.skippedMessageKeys.toMutableMap()

        while (msgNum < header.messageNumberSend && skipMap.size < MAX_SKIPPED_KEYS) {
            val msgKeyData = CryptoHelper.hkdfSha256(chainKey, ByteArray(32), "EnchantMsg".encodeToByteArray(), 80)
            val skipMsgKey = MessageKey(
                key = msgKeyData.copyOfRange(0, 32),
                nonce = msgKeyData.copyOfRange(32, 44),
                chainKey = msgKeyData.copyOfRange(44, 76)
            )
            skipMap["${ByteBuffer.wrap(header.dhPublicKey).long}:$msgNum"] = skipMsgKey
            chainKey = msgKeyData.copyOfRange(44, 76)
            msgNum++
            if (skipMap.size >= MAX_SKIPPED_KEYS) break
        }

        val msgKeyData = CryptoHelper.hkdfSha256(chainKey, ByteArray(32), "EnchantMsg".encodeToByteArray(), 80)
        val msgKey = MessageKey(
            key = msgKeyData.copyOfRange(0, 32),
            nonce = msgKeyData.copyOfRange(32, 44),
            chainKey = msgKeyData.copyOfRange(44, 76)
        )
        val nextChainKey = msgKeyData.copyOfRange(44, 76)

        val plaintext = try {
            CryptoHelper.decryptAesGcm(message.ciphertext, msgKey.key)
        } catch (e: Exception) {
            return Pair(s, ByteArray(0))
        }

        s = s.copy(
            receivingChainKey = nextChainKey,
            receivingMessageNumber = msgNum + 1,
            skippedMessageKeys = skipMap
        )

        return Pair(s, plaintext)
    }

    fun serializeState(state: RatchetState): ByteArray {
        val buf = ByteBuffer.allocate(1024).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(state.version)
        buf.putInt(state.rootKey.size)
        buf.put(state.rootKey)

        buf.putInt(if (state.sendingChainKey != null) 1 else 0)
        state.sendingChainKey?.let { buf.put(it) }
        val hasSendingKey = state.sendingRatchetKeyPublic != null
        buf.putInt(if (hasSendingKey) 1 else 0)
        state.sendingRatchetKeyPublic?.let { buf.put(it) }
        state.sendingRatchetKeyPrivate?.let { buf.put(it) }
        buf.putInt(state.sendingMessageNumber)

        buf.putInt(if (state.receivingChainKey != null) 1 else 0)
        state.receivingChainKey?.let { buf.put(it) }
        val hasReceivingKey = state.receivingRatchetKeyPublic != null
        buf.putInt(if (hasReceivingKey) 1 else 0)
        state.receivingRatchetKeyPublic?.let { buf.put(it) }
        buf.putInt(state.receivingMessageNumber)
        buf.putInt(state.previousSendingChainLength)

        val pos = buf.position()
        buf.position(0)
        val result = ByteArray(pos)
        buf.get(result)
        return result
    }

    fun deserializeState(data: ByteArray): RatchetState? {
        return try {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            val version = buf.getInt()
            val rootKeySize = buf.getInt()
            val rootKey = ByteArray(rootKeySize); buf.get(rootKey)

            val hasSendingChain = buf.getInt() == 1
            val sendingChainKey = if (hasSendingChain) { val k = ByteArray(32); buf.get(k); k } else null
            val hasSendingRatchet = buf.getInt() == 1
            val sendingRatchetPublic = if (hasSendingRatchet) { val k = ByteArray(32); buf.get(k); k } else null
            val sendingRatchetPrivate = if (hasSendingRatchet) { val k = ByteArray(32); buf.get(k); k } else null
            val sendingMsgNum = buf.getInt()

            val hasReceivingChain = buf.getInt() == 1
            val receivingChainKey = if (hasReceivingChain) { val k = ByteArray(32); buf.get(k); k } else null
            val hasReceivingRatchet = buf.getInt() == 1
            val receivingRatchetPublic = if (hasReceivingRatchet) { val k = ByteArray(32); buf.get(k); k } else null
            val receivingMsgNum = buf.getInt()
            val pcl = buf.getInt()

            RatchetState(
                version = version,
                rootKey = rootKey,
                sendingChainKey = sendingChainKey,
                sendingRatchetKeyPublic = sendingRatchetPublic,
                sendingRatchetKeyPrivate = sendingRatchetPrivate,
                sendingMessageNumber = sendingMsgNum,
                receivingChainKey = receivingChainKey,
                receivingRatchetKeyPublic = receivingRatchetPublic,
                receivingMessageNumber = receivingMsgNum,
                previousSendingChainLength = pcl
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseHeader(data: ByteArray): RatchetHeader? {
        return try {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            val dhKey = ByteArray(32); buf.get(dhKey)
            val ns = buf.getInt()
            val nr = buf.getInt()
            val pcl = buf.getInt()
            RatchetHeader(dhKey, ns, nr, pcl)
        } catch (_: Exception) {
            null
        }
    }

    private data class RatchetHeader(
        val dhPublicKey: ByteArray,
        val messageNumberSend: Int,
        val messageNumberReceive: Int,
        val previousChainLength: Int
    )

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val result = ByteArray(this.size + other.size)
        System.arraycopy(this, 0, result, 0, this.size)
        System.arraycopy(other, 0, result, this.size, other.size)
        return result
    }
}
