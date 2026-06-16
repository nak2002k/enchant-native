package org.enchant.core.crypto

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sender Key management for group messaging.
 *
 * Implements the sender keys protocol: each group member generates a
 * sender key and distributes it to all other members. Messages are then encrypted
 * with the sender key's symmetric chain, avoiding the O(n) encryption cost of
 * pairwise Double Ratchet.
 *
 * Features:
 * - Sender key generation with HKDF-based chain derivation
 * - Distribution message creation for sharing keys with group members
 * - Replay protection via message number tracking
 * - Per-group, per-sender key isolation
 * - Memory zeroing on key deletion
 */
object SenderKeyManager {
    private val mutex = Mutex()
    private val senderKeyStore = mutableMapOf<String, SenderKeyState>()
    private val receiverKeyStore = mutableMapOf<String, SenderKeyState>()

    /**
     * Get or create a sender key for a group.
     *
     * @param groupId unique group identifier
     * @param senderUserId the user who is sending messages
     * @return the sender key state (newly generated if not exists)
     */
    suspend fun getOrCreateSenderKey(groupId: String, senderUserId: String): SenderKeyState = mutex.withLock {
        val key = senderKey(groupId, senderUserId)
        senderKeyStore.getOrPut(key) {
            val seed = CryptoPrimitives.generateRandomKey(32)
            val chainKey = CryptoPrimitives.hkdfSha256(
                input = seed,
                salt = ByteArray(32),
                info = "EnchantSenderKey".encodeToByteArray(),
                length = 32
            )
            SenderKeyState(seed = seed, chainKey = chainKey, iteration = 0)
        }.copy()
    }

    /**
     * Create a sender key distribution message to share with group members.
     *
     * @param groupId unique group identifier
     * @param senderUserId the user who owns the sender key
     * @return distribution message, or null if no sender key exists
     */
    suspend fun createDistributionMessage(groupId: String, senderUserId: String): SenderKeyDistributionMessage? = mutex.withLock {
        val key = senderKey(groupId, senderUserId)
        val state = senderKeyStore[key] ?: return@withLock null
        SenderKeyDistributionMessage(
            groupId = groupId,
            senderUserId = senderUserId,
            chainKey = state.chainKey.copyOf(),
            iteration = state.iteration
        )
    }

    /**
     * Process a received sender key distribution message.
     *
     * @param dm the distribution message from a group member
     */
    suspend fun handleDistributionMessage(dm: SenderKeyDistributionMessage) = mutex.withLock {
        val key = receiverKey(dm.senderUserId, dm.groupId)
        receiverKeyStore[key] = SenderKeyState(
            seed = ByteArray(32),
            chainKey = dm.chainKey.copyOf(),
            iteration = dm.iteration
        )
    }

    /**
     * Derive a message key and the next chain key from the current chain key.
     *
     * chain_key → HMAC(chain_key, 0x01) = message_key_seed
     * chain_key → HMAC(chain_key, 0x02) = next_chain_key
     */
    private fun deriveMessageKeyAndNextChain(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        val msgKeySeed = CryptoPrimitives.hmacSha256(chainKey, byteArrayOf(0x01))
        val nextChainKey = CryptoPrimitives.hmacSha256(chainKey, byteArrayOf(0x02))
        return Pair(msgKeySeed, nextChainKey)
    }

    /**
     * Derive encryption key (32 bytes) and nonce (12 bytes) from a message key seed.
     */
    private fun deriveMessageKeyAndNonce(msgKeySeed: ByteArray): Pair<ByteArray, ByteArray> {
        val derived = CryptoPrimitives.hkdfSha256(
            input = msgKeySeed,
            salt = ByteArray(32),
            info = "WhisperMessageKey".encodeToByteArray(),
            length = 44
        )
        val encKey = derived.copyOfRange(0, 32)
        val nonce = derived.copyOfRange(32, 44)
        CryptoPrimitives.zeroBytes(derived)
        return Pair(encKey, nonce)
    }

    /**
     *
     * @param groupId unique group identifier
     * @param senderUserId the user sending the message
     * @param plaintext the message to encrypt
     * @return encrypted payload (nonce + ciphertext), or null if no sender key
     */
    suspend fun encryptGroupMessage(groupId: String, senderUserId: String, plaintext: ByteArray): ByteArray? = mutex.withLock {
        val key = senderKey(groupId, senderUserId)
        val state = senderKeyStore[key] ?: return@withLock null

        val (msgKeySeed, nextChainKey) = deriveMessageKeyAndNextChain(state.chainKey)
        val (encKey, nonce) = deriveMessageKeyAndNonce(msgKeySeed)
        CryptoPrimitives.zeroBytes(msgKeySeed)

        val ciphertext = CryptoPrimitives.encryptXChaCha20Poly1305Raw(plaintext, encKey, nonce)
        CryptoPrimitives.zeroBytes(encKey)

        senderKeyStore[key] = state.copy(
            chainKey = nextChainKey,
            iteration = state.iteration + 1
        )

        ByteArray(nonce.size + ciphertext.size).apply {
            nonce.copyInto(this, 0)
            ciphertext.copyInto(this, nonce.size)
        }
    }

    /**
     * Decrypt a group message using the receiver's copy of the sender key.
     *
     * @param groupId unique group identifier
     * @param senderUserId the user who sent the message
     * @param payload encrypted payload (nonce + ciphertext)
     * @param iteration expected message iteration number
     * @return decrypted plaintext, or null on failure
     */
    suspend fun decryptGroupMessage(
        groupId: String,
        senderUserId: String,
        payload: ByteArray,
        iteration: Int
    ): ByteArray? = mutex.withLock {
        val key = receiverKey(senderUserId, groupId)
        val state = receiverKeyStore[key] ?: return@withLock null

        // Replay protection: reject messages with iteration <= current
        if (iteration <= state.iteration) {
            return@withLock null
        }

        if (payload.size < CryptoPrimitives.XCHACHA20_NONCE_SIZE) return@withLock null
        val nonce = payload.copyOfRange(0, CryptoPrimitives.XCHACHA20_NONCE_SIZE)
        val ciphertext = payload.copyOfRange(CryptoPrimitives.XCHACHA20_NONCE_SIZE, payload.size)

        // Advance chain to the expected iteration
        var chainKey = state.chainKey
        var currentIteration = state.iteration
        while (currentIteration < iteration - 1) {
            val (_, nextCk) = deriveMessageKeyAndNextChain(chainKey)
            chainKey = nextCk
            currentIteration++
        }

        // Derive message key for this iteration
        val (msgKeySeed, nextChainKey) = deriveMessageKeyAndNextChain(chainKey)
        val (encKey, msgNonce) = deriveMessageKeyAndNonce(msgKeySeed)
        CryptoPrimitives.zeroBytes(msgKeySeed)

        val plaintext = try {
            CryptoPrimitives.decryptXChaCha20Poly1305Raw(ciphertext, encKey, msgNonce)
        } catch (_: Exception) {
            CryptoPrimitives.zeroBytes(encKey)
            return@withLock null
        }
        CryptoPrimitives.zeroBytes(encKey)

        receiverKeyStore[key] = state.copy(
            chainKey = nextChainKey,
            iteration = iteration
        )

        plaintext
    }

    /** Delete all sender and receiver keys for a group. */
    suspend fun deleteGroupKeys(groupId: String) = mutex.withLock {
        senderKeyStore.keys.filter { it.endsWith(":$groupId") }.forEach { key ->
            senderKeyStore.remove(key)?.let { state ->
                CryptoPrimitives.zeroBytes(state.seed)
                CryptoPrimitives.zeroBytes(state.chainKey)
            }
        }
        receiverKeyStore.keys.filter { it.endsWith(":$groupId") }.forEach { key ->
            receiverKeyStore.remove(key)?.let { state ->
                CryptoPrimitives.zeroBytes(state.seed)
                CryptoPrimitives.zeroBytes(state.chainKey)
            }
        }
    }

    /** Clear all cached keys. */
    suspend fun clearAll() = mutex.withLock {
        senderKeyStore.values.forEach { state ->
            CryptoPrimitives.zeroBytes(state.seed)
            CryptoPrimitives.zeroBytes(state.chainKey)
        }
        receiverKeyStore.values.forEach { state ->
            CryptoPrimitives.zeroBytes(state.seed)
            CryptoPrimitives.zeroBytes(state.chainKey)
        }
        senderKeyStore.clear()
        receiverKeyStore.clear()
    }

    private fun senderKey(groupId: String, senderUserId: String) = "$senderUserId:$groupId"
    private fun receiverKey(senderUserId: String, groupId: String) = "$senderUserId:$groupId"

    // ──────────────────────────────────────────────
    // Data Classes
    // ──────────────────────────────────────────────

    data class SenderKeyState(
        val seed: ByteArray,
        val chainKey: ByteArray,
        val iteration: Int = 0
    ) {
        fun copy(): SenderKeyState = SenderKeyState(
            seed = seed.copyOf(),
            chainKey = chainKey.copyOf(),
            iteration = iteration
        )
    }

    data class SenderKeyDistributionMessage(
        val groupId: String,
        val senderUserId: String,
        val chainKey: ByteArray,
        val iteration: Int
    )
}
