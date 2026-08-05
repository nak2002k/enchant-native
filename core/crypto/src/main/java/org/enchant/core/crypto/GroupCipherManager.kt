package org.enchant.core.crypto

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * GroupCipherManager — sender-key group messaging (Signal groups V1 pattern).
 *
 * Each group has one sender-key state per member (the state that encrypts with
 * that member's chain). The creator distributes a distribution message to the
 * other members; they process it to build their copy of the sender's state.
 * Group messages are sealed by the sender's state and unsealed by the
 * recipient's copy of that same state (sender-key ratchet).
 *
 * States are persisted (base64 in SecurePreferences) so keys survive restarts.
 */
object GroupCipherManager {

    private const val TAG = "GroupCipher"
    private const val KEY_PREFIX = "group_cipher_state.v6."
    private val mutex = Mutex()

    private val stateCache = mutableMapOf<String, ByteArray>()

    private fun groupKey(groupId: String, senderUserId: String) = "$groupId|$senderUserId"

    fun hasSenderState(groupId: String, senderUserId: String): Boolean =
        stateCache.containsKey(groupKey(groupId, senderUserId)) ||
            SecurePrefs.get(KEY_PREFIX + groupKey(groupId, senderUserId)) != null

    /**
     * Load (or create) the local sending state. MUST be called while holding
     * [mutex] — the callers (encrypt/createDistribution) already lock, and
     * this mutex is not reentrant.
     */
    private suspend fun loadOrCreateSendingState(groupId: String, myUserId: String): ByteArray? {
        val key = groupKey(groupId, myUserId)
        stateCache[key]?.let { return it }
        SecurePrefs.get(KEY_PREFIX + key)?.let { cached ->
            stateCache[key] = cached
            return cached
        }
        // Fresh session: sender id is the local user id, key id 1.
        val groupIdBytes = groupId.toByteArray(Charsets.UTF_8).let { id ->
            if (id.size == 32) id else id.copyOf(32)
        }
        val identity = KeyManager.getIdentityKeyPair()?.publicKey ?: return null
        val state = ByteArray(4096)
        val stateLen = longArrayOf(state.size.toLong())
        val rc = EnchantCrypto.enchant_group_cipher_create_session(
            groupIdBytes, groupIdBytes.size.toLong(),
            identity, myUserId, 1, state, stateLen
        )
        if (rc != EnchantCrypto.SUCCESS) {
            Log.e(TAG, "create_session rc=$rc")
            return null
        }
        val blob = state.copyOf(stateLen[0].toInt())
        stateCache[key] = blob
        SecurePrefs.put(KEY_PREFIX + key, blob)
        return blob
    }

    /** Public entry point for the distribution path (owns the lock). */
    suspend fun getOrCreateSendingState(groupId: String, myUserId: String): ByteArray? =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                loadOrCreateSendingState(groupId, myUserId)
            }
        }

    /** Create the distribution message for a new member. */
    suspend fun createDistribution(groupId: String, myUserId: String): ByteArray? =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val state = loadOrCreateSendingState(groupId, myUserId) ?: return@withLock null
                val identity = KeyManager.getIdentityKeyPair() ?: return@withLock null
                val dist = ByteArray(4096)
                val distLen = longArrayOf(dist.size.toLong())
                val rc = EnchantCrypto.enchant_group_cipher_create_distribution(
                    state, state.size.toLong(), identity.privateKey, dist, distLen
                )
                if (rc != EnchantCrypto.SUCCESS) {
                    Log.e(TAG, "create_distribution rc=$rc")
                    return@withLock null
                }
                dist.copyOf(distLen[0].toInt())
            }
        }

    /** Process a received distribution message for [senderUserId]'s chain. */
    suspend fun processDistribution(
        groupId: String, senderUserId: String, senderIdentityPublic: ByteArray,
        distribution: ByteArray
    ): Boolean = withContext(Dispatchers.Default) {
        mutex.withLock {
            val key = groupKey(groupId, senderUserId)
            val base = ByteArray(4096)
            val baseLen = longArrayOf(4096L)
            val groupIdBytes = groupId.toByteArray(Charsets.UTF_8).let { id ->
                if (id.size == 32) id else id.copyOf(32)
            }
            // The state for the SENDER's chain carries the SENDER's identity.
            val rcCreate = EnchantCrypto.enchant_group_cipher_create_session(
                groupIdBytes, groupIdBytes.size.toLong(),
                senderIdentityPublic, senderUserId, 1, base, baseLen
            )
            if (rcCreate != EnchantCrypto.SUCCESS) {
                Log.e(TAG, "process: create rc=$rcCreate")
                return@withLock false
            }
            val out = ByteArray(4096)
            val outLen = longArrayOf(out.size.toLong())
            val rc = EnchantCrypto.enchant_group_cipher_process_distribution(
                base.copyOf(baseLen[0].toInt()), baseLen[0],
                distribution, distribution.size.toLong(),
                senderIdentityPublic, out, outLen
            )
            if (rc != EnchantCrypto.SUCCESS) {
                Log.e(TAG, "process_distribution rc=$rc")
                return@withLock false
            }
            val blob = out.copyOf(outLen[0].toInt())
            stateCache[key] = blob
            SecurePrefs.put(KEY_PREFIX + key, blob)
            true
        }
    }

    /** Encrypt a group message. Returns the serialized GroupCipherMessage. */
    suspend fun encrypt(
        groupId: String, senderUserId: String, plaintext: ByteArray
    ): ByteArray? = withContext(Dispatchers.Default) {
        mutex.withLock {
            val key = groupKey(groupId, senderUserId)
            val state = stateCache[key] ?: SecurePrefs.get(KEY_PREFIX + key)
                ?: run {
                    val created = loadOrCreateSendingState(groupId, senderUserId)
                    if (created == null) return@withLock null else created
                }
            val msg = ByteArray(4096 + plaintext.size)
            val msgLen = longArrayOf(msg.size.toLong())
            val newState = ByteArray(4096)
            val newStateLen = longArrayOf(newState.size.toLong())
            val rc = EnchantCrypto.enchant_group_cipher_encrypt(
                state, state.size.toLong(),
                plaintext, plaintext.size.toLong(),
                msg, msgLen, newState, newStateLen
            )
            if (rc != EnchantCrypto.SUCCESS) {
                Log.e(TAG, "group encrypt rc=$rc")
                return@withLock null
            }
            val blob = newState.copyOf(newStateLen[0].toInt())
            stateCache[key] = blob
            SecurePrefs.put(KEY_PREFIX + key, blob)
            msg.copyOf(msgLen[0].toInt())
        }
    }

    /** Decrypt a group message from [senderUserId]. Returns plaintext or null. */
    suspend fun decrypt(
        groupId: String, senderUserId: String, ciphertext: ByteArray,
        rcOut: IntArray? = null
    ): ByteArray? = withContext(Dispatchers.Default) {
        mutex.withLock {
            val key = groupKey(groupId, senderUserId)
            val state = stateCache[key] ?: SecurePrefs.get(KEY_PREFIX + key)
                ?: return@withLock null
            val plaintext = ByteArray(ciphertext.size + 256)
            val plaintextLen = longArrayOf(plaintext.size.toLong())
            val newState = ByteArray(4096)
            val newStateLen = longArrayOf(newState.size.toLong())
            val rc = EnchantCrypto.enchant_group_cipher_decrypt(
                state, state.size.toLong(),
                ciphertext, ciphertext.size.toLong(),
                plaintext, plaintextLen, newState, newStateLen
            )
            if (rc != EnchantCrypto.SUCCESS) {
                if (rcOut != null && rcOut.isNotEmpty()) rcOut[0] = rc
                Log.w(TAG, "group decrypt rc=$rc (need distribution for $senderUserId?)")
                return@withLock null
            }
            val blob = newState.copyOf(newStateLen[0].toInt())
            stateCache[key] = blob
            SecurePrefs.put(KEY_PREFIX + key, blob)
            plaintext.copyOf(plaintextLen[0].toInt())
        }
    }

    fun clear() {
        stateCache.clear()
    }

    /** Drop a (group, sender) chain — used to recover from replay desyncs. */
    suspend fun resetState(groupId: String, senderUserId: String) {
        mutex.withLock {
            val key = groupKey(groupId, senderUserId)
            stateCache.remove(key)
            org.enchant.core.base.SecurePreferences.remove(KEY_PREFIX + key)
        }
    }

    /**
     * Create a distribution from a FRESH chain (iteration 0). Members that
     * process it catch up to the sender's current chain via forward jumps.
     */
    suspend fun createFreshDistribution(groupId: String, myUserId: String): ByteArray? =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                resetStateLocked(groupId, myUserId)
                val state = loadOrCreateSendingState(groupId, myUserId) ?: return@withLock null
                val identity = KeyManager.getIdentityKeyPair() ?: return@withLock null
                val dist = ByteArray(4096)
                val distLen = longArrayOf(dist.size.toLong())
                val rc = EnchantCrypto.enchant_group_cipher_create_distribution(
                    state, state.size.toLong(), identity.privateKey, dist, distLen
                )
                if (rc != EnchantCrypto.SUCCESS) {
                    Log.e(TAG, "fresh_distribution rc=$rc")
                    return@withLock null
                }
                dist.copyOf(distLen[0].toInt())
            }
        }

    private fun resetStateLocked(groupId: String, senderUserId: String) {
        val key = groupKey(groupId, senderUserId)
        stateCache.remove(key)
        org.enchant.core.base.SecurePreferences.remove(KEY_PREFIX + key)
    }

    private object SecurePrefs {
        fun get(key: String): ByteArray? {
            val b64 = org.enchant.core.base.SecurePreferences.getString(key) ?: return null
            return runCatching { java.util.Base64.getDecoder().decode(b64) }.getOrNull()
        }

        fun put(key: String, value: ByteArray) {
            org.enchant.core.base.SecurePreferences.putString(key, java.util.Base64.getEncoder().encodeToString(value))
        }
    }
}
