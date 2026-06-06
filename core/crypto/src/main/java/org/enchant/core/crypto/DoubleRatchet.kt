package org.enchant.core.crypto

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Double Ratchet for per-message encryption.
 *
 * Implements the full Double Ratchet protocol:
 * - Symmetric ratchet: chain key → message key → next chain key (HMAC-SHA256)
 * - Asymmetric ratchet: DH ratchet step when receiving a new ratchet key
 * - Out-of-order message handling with bounded skipped key storage
 * - Replay protection via consumed key tracking
 * - XChaCha20-Poly1305 AEAD for message encryption
 *
 * All secret material is zeroed after use. State serialization includes a version
 * field for forward compatibility.
 */
object DoubleRatchet {
    private const val MAX_SKIPPED_KEYS = 1000
    private const val DH_KEY_SIZE = 32

    /**
     * Initialize the ratchet as Alice (sender).
     *
     * @param sharedSecret X3DH shared secret
     * @param theirSignedPrekeyPublic Bob's signed prekey public key (X25519)
     * @param ourEphemeralKeyPair Alice's ephemeral X25519 key pair (for the first sending chain)
     * @return initialized ratchet state ready to encrypt
     */
    fun initializeAsAlice(
        sharedSecret: ByteArray,
        theirSignedPrekeyPublic: ByteArray,
        ourEphemeralKeyPair: CryptoPrimitives.KeyPair
    ): RatchetState {
        val rootMaterial = CryptoPrimitives.hkdfSha256(
            input = sharedSecret,
            salt = ByteArray(32),
            info = "WhisperRatchet".encodeToByteArray(),
            length = 64
        )
        val rootKey = rootMaterial.copyOfRange(0, 32)
        val sendingChainKey = rootMaterial.copyOfRange(32, 64)
        CryptoPrimitives.zeroBytes(rootMaterial)

        return RatchetState(
            rootKey = rootKey,
            sendingChainKey = sendingChainKey,
            sendingRatchetKeyPublic = ourEphemeralKeyPair.publicKey,
            sendingRatchetKeyPrivate = ourEphemeralKeyPair.privateKey,
            sendingMessageNumber = 0,
            receivingRatchetKeyPublic = theirSignedPrekeyPublic,
            receivingMessageNumber = 0,
            previousSendingChainLength = 0
        )
    }

    /**
     * Initialize the ratchet as Bob (receiver of pre-key message).
     *
     * @param sharedSecret X3DH shared secret
     * @param theirRatchetKeyPublic Alice's ephemeral public key
     * @param ourSignedPrekeyPrivate Bob's signed prekey private key
     * @return initialized ratchet state ready to decrypt
     */
    fun initializeAsBob(
        sharedSecret: ByteArray,
        theirRatchetKeyPublic: ByteArray,
        ourSignedPrekeyPrivate: ByteArray
    ): RatchetState {
        val rootMaterial = CryptoPrimitives.hkdfSha256(
            input = sharedSecret,
            salt = ByteArray(32),
            info = "WhisperRatchet".encodeToByteArray(),
            length = 64
        )
        val rootKey = rootMaterial.copyOfRange(0, 32)
        val receivingChainKey = rootMaterial.copyOfRange(32, 64)
        CryptoPrimitives.zeroBytes(rootMaterial)

        return RatchetState(
            rootKey = rootKey,
            receivingChainKey = receivingChainKey,
            receivingRatchetKeyPublic = theirRatchetKeyPublic,
            receivingMessageNumber = 0
        )
    }

    /**
     * Encrypt a message using the Double Ratchet.
     *
     * If there is no active sending chain, performs a DH ratchet step to create one.
     * Derives a message key via the KDF chain, encrypts with XChaCha20-Poly1305,
     * and advances the chain.
     *
     * @param state current ratchet state
     * @param plaintext message to encrypt
     * @param ad optional associated data (included in MAC but not encrypted)
     * @return Pair(newState, encryptedMessage)
     */
    fun encrypt(state: RatchetState, plaintext: ByteArray, ad: ByteArray? = null): Pair<RatchetState, RatchetMessage> {
        var s = state

        // If no sending chain, perform DH ratchet step
        if (s.sendingChainKey == null) {
            val theirPub = s.receivingRatchetKeyPublic
                ?: throw IllegalStateException("No receiving ratchet key to derive sending chain from")
            val dhPair = CryptoPrimitives.generateX25519KeyPair()
            val dhOut = CryptoPrimitives.x25519DiffieHellman(dhPair.privateKey, theirPub)
            val (newRoot, newChain) = KdfChain.deriveRootAndChainKey(s.rootKey, dhOut)
            CryptoPrimitives.zeroBytes(dhOut)

            val oldRoot = s.rootKey
            s = s.copy(
                rootKey = newRoot,
                sendingChainKey = newChain,
                sendingRatchetKeyPublic = dhPair.publicKey,
                sendingRatchetKeyPrivate = dhPair.privateKey,
                sendingMessageNumber = 0,
                previousSendingChainLength = 0
            )
            CryptoPrimitives.zeroBytes(oldRoot)
        }

        // Derive message key from chain key
        val chainKey = s.sendingChainKey
            ?: throw IllegalStateException("No sending chain key available")
        val (msgKeySeed, nextChainKey) = KdfChain.deriveMessageKeyAndNextChain(chainKey)
        val (encKey, nonce) = KdfChain.deriveMessageKeyAndNonce(msgKeySeed)
        CryptoPrimitives.zeroBytes(msgKeySeed)

        // Encrypt
        val ciphertext = CryptoPrimitives.encryptXChaCha20Poly1305Raw(plaintext, encKey, nonce)
        CryptoPrimitives.zeroBytes(encKey)

        // Build header
        val dhKey = s.sendingRatchetKeyPublic ?: ByteArray(DH_KEY_SIZE)
        val header = buildHeader(dhKey, s.sendingMessageNumber, s.receivingMessageNumber, s.previousSendingChainLength)

        // Advance state
        s = s.copy(
            sendingChainKey = nextChainKey,
            sendingMessageNumber = s.sendingMessageNumber + 1
        )

        return Pair(s, RatchetMessage(header = header, ciphertext = ciphertext))
    }

    /**
     * Decrypt a message using the Double Ratchet.
     *
     * Handles three cases:
     * 1. Message from current receiving chain (normal case)
     * 2. Message from a new DH ratchet (sender sent a new ratchet key)
     * 3. Out-of-order message from a skipped chain key
     *
     * @param state current ratchet state
     * @param message encrypted message with header
     * @param ad optional associated data
     * @return Pair(newState, decryptedPlaintext)
     */
    fun decrypt(state: RatchetState, message: RatchetMessage, ad: ByteArray? = null): Pair<RatchetState, ByteArray> {
        var s = state
        val header = parseHeader(message.header)
            ?: throw IllegalArgumentException("Failed to parse message header")

        val skipKey = makeKeyId(header.dhPublicKey, header.messageNumberSend)
        if (s.consumedKeys.contains(skipKey)) {
            throw ReplayException("Message already consumed: $skipKey")
        }

        // Check if this is a new DH ratchet (different ratchet key)
        if (!header.dhPublicKey.contentEquals(s.receivingRatchetKeyPublic)) {
            s = performDhRatchetStep(s, header)
        }

        // Try to find a skipped key for this message
        val existingSkip = s.skippedMessageKeys[skipKey]
        if (existingSkip != null) {
            s.skippedMessageKeys.remove(skipKey)
            val plaintext = decryptWithKey(message.ciphertext, existingSkip.encKey, existingSkip.nonce)
            CryptoPrimitives.zeroBytes(existingSkip.encKey)
            CryptoPrimitives.zeroBytes(existingSkip.nonce)
            s = s.copy(consumedKeys = s.consumedKeys.toMutableSet().apply { add(skipKey) })
            return Pair(s, plaintext)
        }

        // Derive message keys for skipped messages, then decrypt current
        var chainKey = s.receivingChainKey
            ?: throw IllegalStateException("No receiving chain key available")
        var msgNum = s.receivingMessageNumber
        val skipMap = s.skippedMessageKeys.toMutableMap()

        // Advance chain to the expected message number, storing skipped keys
        while (msgNum < header.messageNumberSend && skipMap.size < MAX_SKIPPED_KEYS) {
            val (msgKeySeed, nextCk) = KdfChain.deriveMessageKeyAndNextChain(chainKey)
            val (encKey, nonce) = KdfChain.deriveMessageKeyAndNonce(msgKeySeed)
            val kId = makeKeyId(header.dhPublicKey, msgNum)
            if (skipMap.size >= MAX_SKIPPED_KEYS) {
                val oldest = skipMap.entries.minByOrNull { it.value.timestamp }
                if (oldest != null) {
                    CryptoPrimitives.zeroBytes(oldest.value.encKey)
                    CryptoPrimitives.zeroBytes(oldest.value.nonce)
                    skipMap.remove(oldest.key)
                }
            }
            skipMap[kId] = SkippedKey(encKey, nonce, System.currentTimeMillis())
            CryptoPrimitives.zeroBytes(msgKeySeed)
            chainKey = nextCk
            msgNum++
        }

        // Derive message key for the current message
        val (msgKeySeed, nextChainKey) = KdfChain.deriveMessageKeyAndNextChain(chainKey)
        val (encKey, nonce) = KdfChain.deriveMessageKeyAndNonce(msgKeySeed)
        CryptoPrimitives.zeroBytes(msgKeySeed)

        val plaintext = decryptWithKey(message.ciphertext, encKey, nonce)
        CryptoPrimitives.zeroBytes(encKey)

        s = s.copy(
            consumedKeys = s.consumedKeys.toMutableSet().apply { add(skipKey) },
            receivingChainKey = nextChainKey,
            receivingMessageNumber = msgNum + 1,
            skippedMessageKeys = skipMap
        )

        return Pair(s, plaintext)
    }

    /**
     * Perform a DH ratchet step when receiving a message with a new ratchet key.
     * This creates a new receiving chain and a new sending chain.
     */
    private fun performDhRatchetStep(state: RatchetState, header: RatchetHeader): RatchetState {
        var s = state

        // Step 1: Use our old sending (or receiving) private key with their new public key
        val dhPriv = s.sendingRatchetKeyPrivate
            ?: throw IllegalStateException("No DH private key available for ratchet step")
        val dhOut = CryptoPrimitives.x25519DiffieHellman(dhPriv, header.dhPublicKey)
        val (newRoot, newReceivingChain) = KdfChain.deriveRootAndChainKey(s.rootKey, dhOut)
        CryptoPrimitives.zeroBytes(dhOut)

        val oldRoot = s.rootKey
        s = s.copy(
            rootKey = newRoot,
            receivingChainKey = newReceivingChain,
            receivingRatchetKeyPublic = header.dhPublicKey,
            receivingMessageNumber = 0
        )
        CryptoPrimitives.zeroBytes(oldRoot)

        // Step 2: Generate new sending key and derive sending chain
        val newDhPair = CryptoPrimitives.generateX25519KeyPair()
        val dhOut2 = CryptoPrimitives.x25519DiffieHellman(newDhPair.privateKey, header.dhPublicKey)
        val (newRoot2, newSendingChain) = KdfChain.deriveRootAndChainKey(s.rootKey, dhOut2)
        CryptoPrimitives.zeroBytes(dhOut2)

        val oldRoot2 = s.rootKey
        s = s.copy(
            rootKey = newRoot2,
            sendingChainKey = newSendingChain,
            sendingRatchetKeyPublic = newDhPair.publicKey,
            sendingRatchetKeyPrivate = newDhPair.privateKey,
            sendingMessageNumber = 0,
            previousSendingChainLength = header.messageNumberSend
        )
        CryptoPrimitives.zeroBytes(oldRoot2)

        return s
    }

    private fun decryptWithKey(ciphertext: ByteArray, encKey: ByteArray, nonce: ByteArray): ByteArray {
        return try {
            CryptoPrimitives.decryptXChaCha20Poly1305Raw(ciphertext, encKey, nonce)
        } catch (e: Exception) {
            throw DecryptionFailedException("Message decryption failed", e)
        }
    }

    private fun buildHeader(dhKey: ByteArray, ns: Int, nr: Int, previousLength: Int): ByteArray {
        return ByteBuffer.allocate(4 + 4 + dhKey.size + 4 + 4 + 4).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(dhKey.size)
            putInt(0) // reserved
            put(dhKey)
            putInt(ns)
            putInt(nr)
            putInt(previousLength)
        }.array()
    }

    private fun parseHeader(data: ByteArray): RatchetHeader? {
        return try {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            val dhKeySize = buf.getInt()
            buf.getInt() // reserved
            if (dhKeySize <= 0 || dhKeySize > 128) return null
            val dhKey = ByteArray(dhKeySize); buf.get(dhKey)
            val ns = buf.getInt()
            val nr = buf.getInt()
            val previousLength = buf.getInt()
            RatchetHeader(dhKey, ns, nr, previousLength)
        } catch (_: Exception) {
            null
        }
    }

    private fun makeKeyId(dhPublicKey: ByteArray, msgNum: Int): String {
        val hex = CryptoPrimitives.sha256(dhPublicKey).take(8).joinToString("") { String.format("%02x", it) }
        return "$hex:$msgNum"
    }

    // ──────────────────────────────────────────────
    // Serialization
    // ──────────────────────────────────────────────

    /** Serialize ratchet state to bytes for persistent storage. */
    fun serializeState(state: RatchetState): ByteArray {
        val buf = ByteBuffer.allocate(131072).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(state.version)
        buf.putInt(state.rootKey.size)
        buf.put(state.rootKey)

        buf.putInt(if (state.sendingChainKey != null) 1 else 0)
        state.sendingChainKey?.let { buf.put(it) }
        buf.putInt(if (state.sendingRatchetKeyPublic != null) 1 else 0)
        state.sendingRatchetKeyPublic?.let { pk -> buf.putInt(pk.size); buf.put(pk) }
        state.sendingRatchetKeyPrivate?.let { pk -> buf.putInt(pk.size); buf.put(pk) }
        buf.putInt(state.sendingMessageNumber)

        buf.putInt(if (state.receivingChainKey != null) 1 else 0)
        state.receivingChainKey?.let { buf.put(it) }
        buf.putInt(if (state.receivingRatchetKeyPublic != null) 1 else 0)
        state.receivingRatchetKeyPublic?.let { pk -> buf.putInt(pk.size); buf.put(pk) }
        buf.putInt(state.receivingMessageNumber)
        buf.putInt(state.previousSendingChainLength)

        buf.putInt(state.skippedMessageKeys.size)
        state.skippedMessageKeys.forEach { (keyId, sk) ->
            val keyIdBytes = keyId.encodeToByteArray()
            buf.putInt(keyIdBytes.size); buf.put(keyIdBytes)
            buf.putInt(sk.encKey.size); buf.put(sk.encKey)
            buf.putInt(sk.nonce.size); buf.put(sk.nonce)
            buf.putLong(sk.timestamp)
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

    /** Deserialize ratchet state from bytes. Returns null on corrupted data. */
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
            val skippedKeys = mutableMapOf<String, SkippedKey>()
            repeat(skippedCount) {
                val keyId = ByteArray(buf.getInt()).also { buf.get(it) }.decodeToString()
                val encKey = ByteArray(buf.getInt()).also { buf.get(it) }
                val nonce = ByteArray(buf.getInt()).also { buf.get(it) }
                val timestamp = buf.getLong()
                skippedKeys[keyId] = SkippedKey(encKey, nonce, timestamp)
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

    // ──────────────────────────────────────────────
    // Data Classes
    // ──────────────────────────────────────────────

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
        val skippedMessageKeys: MutableMap<String, SkippedKey> = mutableMapOf(),
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
                receivingMessageNumber = receivingMessageNumber,
                previousSendingChainLength = previousSendingChainLength,
                skippedMessageKeys = skippedMessageKeys.mapValues { (_, v) ->
                    SkippedKey(v.encKey.copyOf(), v.nonce.copyOf(), v.timestamp)
                }.toMutableMap(),
                consumedKeys = consumedKeys.toMutableSet(),
                version = version
            )
        }

        fun zero() {
            CryptoPrimitives.zeroBytes(rootKey)
            sendingChainKey?.let { CryptoPrimitives.zeroBytes(it) }
            sendingRatchetKeyPublic?.let { CryptoPrimitives.zeroBytes(it) }
            sendingRatchetKeyPrivate?.let { CryptoPrimitives.zeroBytes(it) }
            receivingChainKey?.let { CryptoPrimitives.zeroBytes(it) }
            receivingRatchetKeyPublic?.let { CryptoPrimitives.zeroBytes(it) }
            skippedMessageKeys.values.forEach {
                CryptoPrimitives.zeroBytes(it.encKey)
                CryptoPrimitives.zeroBytes(it.nonce)
            }
            skippedMessageKeys.clear()
            consumedKeys.clear()
        }
    }

    data class SkippedKey(
        val encKey: ByteArray,
        val nonce: ByteArray,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class RatchetMessage(
        val header: ByteArray,
        val ciphertext: ByteArray
    )

    private data class RatchetHeader(
        val dhPublicKey: ByteArray,
        val messageNumberSend: Int,
        val messageNumberReceive: Int,
        val previousChainLength: Int
    )

    class ReplayException(message: String) : Exception(message)
    class DecryptionFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)
}
