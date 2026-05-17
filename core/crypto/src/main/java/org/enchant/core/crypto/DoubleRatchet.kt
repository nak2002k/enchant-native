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
    val receivingRatchetKeyPrivate: ByteArray? = null,
    val receivingMessageNumber: Int = 0,
    val previousSendingChainLength: Int = 0,
    val skippedMessageKeys: MutableMap<String, MessageKey> = mutableMapOf(),
    val consumedKeys: MutableSet<String> = mutableSetOf(),
    val version: Int = 1
) {
    fun deepCopy(): RatchetState {
        return RatchetState(
            rootKey = rootKey.copyOf(),
            sendingChainKey = sendingChainKey?.copyOf(),
            sendingRatchetKeyPublic = sendingRatchetKeyPublic?.copyOf(),
            sendingRatchetKeyPrivate = sendingRatchetKeyPrivate?.copyOf(),
            sendingMessageNumber = sendingMessageNumber,
            receivingChainKey = receivingChainKey?.copyOf(),
            receivingRatchetKeyPublic = receivingRatchetKeyPublic?.copyOf(),
            receivingRatchetKeyPrivate = receivingRatchetKeyPrivate?.copyOf(),
            receivingMessageNumber = receivingMessageNumber,
            previousSendingChainLength = previousSendingChainLength,
            skippedMessageKeys = skippedMessageKeys.mapValues { (_, v) ->
                MessageKey(v.key.copyOf(), v.nonce.copyOf(), v.chainKey.copyOf(), v.timestamp)
            }.toMutableMap(),
            consumedKeys = consumedKeys.toMutableSet(),
            version = version
        )
    }

    fun zero() {
        CryptoHelper.zeroBytes(rootKey)
        sendingChainKey?.let { CryptoHelper.zeroBytes(it) }
        sendingRatchetKeyPublic?.let { CryptoHelper.zeroBytes(it) }
        sendingRatchetKeyPrivate?.let { CryptoHelper.zeroBytes(it) }
        receivingChainKey?.let { CryptoHelper.zeroBytes(it) }
        receivingRatchetKeyPublic?.let { CryptoHelper.zeroBytes(it) }
        receivingRatchetKeyPrivate?.let { CryptoHelper.zeroBytes(it) }
        skippedMessageKeys.values.forEach { CryptoHelper.zeroBytes(it.key) }
        skippedMessageKeys.clear()
        consumedKeys.clear()
    }
}

data class MessageKey(
    val key: ByteArray,
    val nonce: ByteArray,
    val chainKey: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun zero() {
        CryptoHelper.zeroBytes(key)
        CryptoHelper.zeroBytes(nonce)
        CryptoHelper.zeroBytes(chainKey)
    }
}

data class RatchetMessage(
    val header: ByteArray,
    val ciphertext: ByteArray
)

object DoubleRatchet {
    private const val MAX_SKIPPED_KEYS = 1000
    private const val DH_KEY_SIZE = 32
    private const val HEADER_SIZE = 128

    fun initializeAsAlice(
        sharedSecret: ByteArray,
        theirSignedPrekeyPublic: ByteArray
    ): RatchetState {
        val salt = ByteArray(32)
        val sendingKeyPair = CryptoHelper.generateX25519KeyPair()

        val rootMaterial = CryptoHelper.hkdfSha256(sharedSecret, salt, "EnchantRatchet".encodeToByteArray(), 64)

        return RatchetState(
            rootKey = rootMaterial.copyOfRange(0, 32),
            sendingChainKey = rootMaterial.copyOfRange(32, 64),
            sendingRatchetKeyPublic = sendingKeyPair.publicKey,
            sendingRatchetKeyPrivate = sendingKeyPair.privateKey,
            sendingMessageNumber = 0,
            receivingRatchetKeyPublic = theirSignedPrekeyPublic,
            receivingMessageNumber = 0,
            previousSendingChainLength = 0
        )
    }

    fun initializeAsBob(
        sharedSecret: ByteArray,
        theirRatchetKeyPublic: ByteArray,
        ourSignedPrekeyPrivate: ByteArray
    ): RatchetState {
        val salt = ByteArray(32)

        val rootMaterial = CryptoHelper.hkdfSha256(sharedSecret, salt, "EnchantRatchet".encodeToByteArray(), 64)

        return RatchetState(
            rootKey = rootMaterial.copyOfRange(0, 32),
            receivingChainKey = rootMaterial.copyOfRange(32, 64),
            receivingRatchetKeyPublic = theirRatchetKeyPublic,
            receivingMessageNumber = 0
        )
    }

    fun encrypt(state: RatchetState, plaintext: ByteArray, ad: ByteArray? = null): Pair<RatchetState, RatchetMessage> {
        var s = state
        val adBytes = ad ?: ByteArray(0)

        if (s.sendingChainKey == null) {
            val theirPub = s.receivingRatchetKeyPublic ?: return Pair(s, RatchetMessage(ByteArray(44), ByteArray(0)))
            val dhPair = CryptoHelper.generateX25519KeyPair()
            val dhOut = CryptoHelper.x25519DiffieHellman(dhPair.privateKey, theirPub)
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

        val chainKey = s.sendingChainKey ?: return Pair(s, RatchetMessage(ByteArray(44), ByteArray(0)))
        val msgKeyData = CryptoHelper.hkdfSha256(chainKey, ByteArray(32), "EnchantMsg".encodeToByteArray(), 80)
        val msgKey = MessageKey(
            key = msgKeyData.copyOfRange(0, 32),
            nonce = msgKeyData.copyOfRange(32, 44),
            chainKey = msgKeyData.copyOfRange(44, 76)
        )
        val nextChainKey = msgKeyData.copyOfRange(44, 76)
        CryptoHelper.zeroBytes(msgKeyData)

        val ciphertext = CryptoHelper.encryptXChaCha20Poly1305Raw(plaintext, msgKey.key, msgKey.nonce)

        val dhKey = s.sendingRatchetKeyPublic ?: ByteArray(DH_KEY_SIZE)
        val header = ByteBuffer.allocate(4 + 4 + dhKey.size + 4 + 4 + 4).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(dhKey.size)
            putInt(0)
            put(dhKey)
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

        val skipKey = makeKeyId(header.dhPublicKey, header.messageNumberSend)
        if (s.consumedKeys.contains(skipKey)) {
            return Pair(s, ByteArray(0))
        }

        if (header.dhPublicKey.contentEquals(s.receivingRatchetKeyPublic).not()) {
            val dhPriv = s.sendingRatchetKeyPrivate ?: s.receivingRatchetKeyPrivate ?: return Pair(s, ByteArray(0))
            val dhOut = try {
                CryptoHelper.x25519DiffieHellman(dhPriv, header.dhPublicKey)
            } catch (_: Exception) {
                return Pair(s, ByteArray(0))
            }
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

        val existingSkip = s.skippedMessageKeys[skipKey]
        if (existingSkip != null) {
            s.skippedMessageKeys.remove(skipKey)
            val plaintext = try {
                CryptoHelper.decryptXChaCha20Poly1305Raw(message.ciphertext, existingSkip.key, existingSkip.nonce)
            } catch (_: Exception) {
                return Pair(s, ByteArray(0))
            }
            s = s.copy(consumedKeys = s.consumedKeys.toMutableSet().apply { add(skipKey) })
            return Pair(s, plaintext)
        }

        var chainKey = s.receivingChainKey ?: return Pair(s, ByteArray(0))
        var msgNum = s.receivingMessageNumber
        var skipMap = s.skippedMessageKeys.toMutableMap()

        while (msgNum < header.messageNumberSend && skipMap.size < MAX_SKIPPED_KEYS) {
            val msgKeyData = CryptoHelper.hkdfSha256(chainKey, ByteArray(32), "EnchantMsg".encodeToByteArray(), 80)
            val skipMsgKey = MessageKey(
                key = msgKeyData.copyOfRange(0, 32),
                nonce = msgKeyData.copyOfRange(32, 44),
                chainKey = msgKeyData.copyOfRange(44, 76)
            )
            val kId = makeKeyId(header.dhPublicKey, msgNum)
            if (skipMap.size >= MAX_SKIPPED_KEYS) {
                val oldest = skipMap.entries.minByOrNull { it.value.timestamp }
                if (oldest != null) skipMap.remove(oldest.key)
            }
            skipMap[kId] = skipMsgKey
            chainKey = msgKeyData.copyOfRange(44, 76)
            msgNum++
        }

        val msgKeyData = CryptoHelper.hkdfSha256(chainKey, ByteArray(32), "EnchantMsg".encodeToByteArray(), 80)
        val msgKey = MessageKey(
            key = msgKeyData.copyOfRange(0, 32),
            nonce = msgKeyData.copyOfRange(32, 44),
            chainKey = msgKeyData.copyOfRange(44, 76)
        )
        val nextChainKey = msgKeyData.copyOfRange(44, 76)

        val plaintext = try {
            CryptoHelper.decryptXChaCha20Poly1305Raw(message.ciphertext, msgKey.key, msgKey.nonce)
        } catch (_: Exception) {
            return Pair(s, ByteArray(0))
        }

        s = s.copy(
            consumedKeys = s.consumedKeys.toMutableSet().apply { add(skipKey) },
            receivingChainKey = nextChainKey,
            receivingMessageNumber = msgNum + 1,
            skippedMessageKeys = skipMap
        )

        return Pair(s, plaintext)
    }

    private fun makeKeyId(dhPublicKey: ByteArray, msgNum: Int): String {
        val hex = CryptoHelper.sha256(dhPublicKey).take(8).joinToString("") { String.format("%02x", it) }
        return "$hex:$msgNum"
    }

    fun serializeState(state: RatchetState): ByteArray {
        val buf = ByteBuffer.allocate(131072).order(ByteOrder.BIG_ENDIAN) // 128KB for skipped keys
        buf.putInt(state.version)
        buf.putInt(state.rootKey.size)
        buf.put(state.rootKey)

        buf.putInt(if (state.sendingChainKey != null) 1 else 0)
        state.sendingChainKey?.let { buf.put(it) }
        val hasSendingKey = state.sendingRatchetKeyPublic != null
        buf.putInt(if (hasSendingKey) 1 else 0)
        state.sendingRatchetKeyPublic?.let { pk ->
            buf.putInt(pk.size); buf.put(pk)
        }
        state.sendingRatchetKeyPrivate?.let { pk ->
            buf.putInt(pk.size); buf.put(pk)
        }
        buf.putInt(state.sendingMessageNumber)

        buf.putInt(if (state.receivingChainKey != null) 1 else 0)
        state.receivingChainKey?.let { buf.put(it) }
        val hasReceivingKey = state.receivingRatchetKeyPublic != null
        buf.putInt(if (hasReceivingKey) 1 else 0)
        state.receivingRatchetKeyPublic?.let { pk ->
            buf.putInt(pk.size); buf.put(pk)
        }
        buf.putInt(state.receivingMessageNumber)
        buf.putInt(state.previousSendingChainLength)

        buf.putInt(state.skippedMessageKeys.size)
        state.skippedMessageKeys.forEach { (keyId, msgKey) ->
            val keyIdBytes = keyId.encodeToByteArray()
            buf.putInt(keyIdBytes.size); buf.put(keyIdBytes)
            buf.putInt(msgKey.key.size); buf.put(msgKey.key)
            buf.putInt(msgKey.nonce.size); buf.put(msgKey.nonce)
            buf.putInt(msgKey.chainKey.size); buf.put(msgKey.chainKey)
            buf.putLong(msgKey.timestamp)
        }

        buf.putInt(state.consumedKeys.size)
        state.consumedKeys.forEach { keyId ->
            val keyIdBytes = keyId.encodeToByteArray()
            buf.putInt(keyIdBytes.size); buf.put(keyIdBytes)
        }

        val pos = buf.position()
        val result = ByteArray(pos)
        buf.flip()
        buf.get(result, 0, pos)
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
            val sendingRatchetPublic = if (hasSendingRatchet) { val k = ByteArray(buf.getInt()); buf.get(k); k } else null
            val sendingRatchetPrivate = if (sendingRatchetPublic != null) { val k = ByteArray(buf.getInt()); buf.get(k); k } else null
            val sendingMsgNum = buf.getInt()

            val hasReceivingChain = buf.getInt() == 1
            val receivingChainKey = if (hasReceivingChain) { val k = ByteArray(32); buf.get(k); k } else null
            val hasReceivingRatchet = buf.getInt() == 1
            val receivingRatchetPublic = if (hasReceivingRatchet) { val k = ByteArray(buf.getInt()); buf.get(k); k } else null
            val receivingMsgNum = buf.getInt()
            val pcl = buf.getInt()

            val skippedCount = buf.getInt()
            val skippedKeys = mutableMapOf<String, MessageKey>()
            repeat(skippedCount) {
                val keyId = ByteArray(buf.getInt()).also { buf.get(it) }.decodeToString()
                val key = ByteArray(buf.getInt()).also { buf.get(it) }
                val nonce = ByteArray(buf.getInt()).also { buf.get(it) }
                val chainKey = ByteArray(buf.getInt()).also { buf.get(it) }
                val timestamp = buf.getLong()
                skippedKeys[keyId] = MessageKey(key, nonce, chainKey, timestamp)
            }

            val consumedCount = buf.getInt()
            val consumed = mutableSetOf<String>()
            repeat(consumedCount) {
                ByteArray(buf.getInt()).also { buf.get(it) }.decodeToString().let { consumed.add(it) }
            }

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
                previousSendingChainLength = pcl,
                skippedMessageKeys = skippedKeys,
                consumedKeys = consumed
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseHeader(data: ByteArray): RatchetHeader? {
        return try {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            val dhKeySize = buf.getInt()
            buf.getInt()
            val dhKey = ByteArray(if (dhKeySize in 1..128) dhKeySize else 32); buf.get(dhKey)
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
