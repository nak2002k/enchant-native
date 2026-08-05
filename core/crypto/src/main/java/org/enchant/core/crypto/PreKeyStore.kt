package org.enchant.core.crypto

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.enchant.core.database.dao.OneTimePreKeyRecord
import org.enchant.core.database.dao.PreKeyDao
import org.enchant.core.database.dao.PreKeyPublic
import org.enchant.core.database.dao.SignedPreKeyRecord
import java.util.concurrent.atomic.AtomicInteger

/**
 * PreKey lifecycle management: generation, storage, consumption, and staleness cleanup.
 *
 * Manages three types of prekeys:
 * - Signed PreKeys (SPK): medium-term keys rotated every 30 days, signed with identity key
 * - One-Time PreKeys (OPK): X25519 keys used once per session establishment
 * - Last-Resort PreKey: a single OPK that is never consumed (ensures delivery)
 *
 * Maintains an atomic ID counter to ensure unique prekey IDs across app restarts.
 *
 * NOTE: PreKeyDao interface is defined here for decoupling. The actual DAO
 * implementation lives in :core:database module.
 */
class PreKeyStore {
    private val mutex = Mutex()
    private val signedPreKeys = mutableMapOf<Int, SignedPreKeyRecord>()
    private val oneTimePreKeys = mutableMapOf<Int, OneTimePreKeyRecord>()
    private var lastResortPreKey: OneTimePreKeyRecord? = null
    private val nextId = AtomicInteger(0)
    private var dao: PreKeyDao? = null
    private var currentSignedPreKeyId: Int = 0

    companion object {
        private const val OPK_BATCH_SIZE = 100
        private const val OPK_TOP_UP_THRESHOLD = 10
        private const val SPK_ROTATION_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        private const val OPK_STALE_THRESHOLD_MS = 90L * 24 * 60 * 60 * 1000 // 90 days
    }

    /**
     * Set the DAO for persistent storage.
     * NOTE: PreKeyDao interface is expected to be implemented in :core:database module.
     */
    fun setDao(dao: PreKeyDao?) {
        this.dao = dao
    }

    // ──────────────────────────────────────────────
    // Signed PreKey
    // ──────────────────────────────────────────────

    /**
     * Generate and store a new signed prekey.
     *
     * @param identityKeyPair Ed25519 identity key pair for signing
     * @return the new signed prekey record
     */
    suspend fun generateSignedPreKey(identityKeyPair: CryptoPrimitives.KeyPair): SignedPreKeyRecord {
        return mutex.withLock {
            val id = nextId.incrementAndGet()
            val publicKey = ByteArray(32)
            val privateKey = ByteArray(32)
            val signature = ByteArray(64)
            val signatureLen = longArrayOf(signature.size.toLong())
            val rc = EnchantCrypto.enchant_prekey_generate_signed(
                id, identityKeyPair.privateKey, publicKey, privateKey, signature, signatureLen
            )
            if (rc != EnchantCrypto.SUCCESS) {
                throw IllegalStateException("enchant_prekey_generate_signed failed: $rc")
            }
            val record = SignedPreKeyRecord(
                id = id,
                publicKey = publicKey,
                privateKey = privateKey,
                signature = signature,
                timestamp = System.currentTimeMillis()
            )
            signedPreKeys[id] = record
            currentSignedPreKeyId = id
            dao?.storeSignedPreKey(record)
            record
        }
    }

    /** Get the current signed prekey. */
    suspend fun getCurrentSignedPreKey(): SignedPreKeyRecord? = mutex.withLock {
        signedPreKeys[currentSignedPreKeyId]?.copy()
    }

    /** Get a signed prekey by ID. */
    suspend fun getSignedPreKey(id: Int): SignedPreKeyRecord? = mutex.withLock {
        signedPreKeys[id]?.copy()
    }

    /** Check if a signed prekey exists. */
    suspend fun hasSignedPreKey(id: Int): Boolean = mutex.withLock {
        signedPreKeys.containsKey(id)
    }

    /** Check if the current signed prekey needs rotation. */
    fun needsSignedPreKeyRotation(): Boolean {
        val current = signedPreKeys[currentSignedPreKeyId] ?: return true
        return (System.currentTimeMillis() - current.timestamp) > SPK_ROTATION_INTERVAL_MS
    }

    /** Remove old signed prekeys (older than threshold). */
    suspend fun cleanSignedPreKeys(thresholdMs: Long = SPK_ROTATION_INTERVAL_MS) = mutex.withLock {
        val now = System.currentTimeMillis()
        val toRemove = signedPreKeys.filter { (id, record) ->
            id != currentSignedPreKeyId && (now - record.timestamp) > thresholdMs
        }.keys
        toRemove.forEach { id ->
            signedPreKeys[id]?.let { CryptoPrimitives.zeroBytes(it.privateKey) }
            signedPreKeys.remove(id)
            dao?.deleteSignedPreKey(id)
        }
    }

    // ──────────────────────────────────────────────
    // One-Time PreKeys
    // ──────────────────────────────────────────────

    /**
     * Generate a batch of one-time prekeys.
     *
     * @param count number of prekeys to generate (default 100)
     * @param startId the starting ID for the batch; if null, uses next available ID from counter
     * @return list of generated prekey records
     */
    suspend fun generateOneTimePreKeys(count: Int = OPK_BATCH_SIZE, startId: Int? = null): List<OneTimePreKeyRecord> {
        return mutex.withLock {
            require(count > 0) { "count must be positive" }
            val requestedFirstId = startId ?: (nextId.get() + 1)
            require(requestedFirstId > 0) { "startId must be positive" }
            val firstId = maxOf(requestedFirstId, nextId.get() + 1)
            val lastId = Math.addExact(firstId, count - 1)
            nextId.set(lastId)
            val buffer = ByteArray(count * 68)
            val len = longArrayOf(buffer.size.toLong())
            val rc = EnchantCrypto.enchant_prekey_generate_batch(count, firstId, buffer, len)
            if (rc != EnchantCrypto.SUCCESS) {
                throw IllegalStateException("enchant_prekey_generate_batch failed: $rc")
            }
            val records = (0 until count).map { offset ->
                val base = offset * 68
                val id = (buffer[base].toInt() and 0xFF) or
                        ((buffer[base + 1].toInt() and 0xFF) shl 8) or
                        ((buffer[base + 2].toInt() and 0xFF) shl 16) or
                        ((buffer[base + 3].toInt() and 0xFF) shl 24)
                val publicKey = buffer.copyOfRange(base + 4, base + 36)
                val privateKey = buffer.copyOfRange(base + 36, base + 68)
                val record = OneTimePreKeyRecord(
                    id = id,
                    publicKey = publicKey,
                    privateKey = privateKey,
                    timestamp = System.currentTimeMillis()
                )
                oneTimePreKeys[id] = record
                record
            }
            // Clear native buffer of private key material
            EnchantCrypto.enchant_secure_zero(buffer, buffer.size.toLong())
            dao?.storeOneTimePreKeys(records)
            records
        }
    }

    /**
     * Generate and store a last-resort prekey. This key is never consumed
     * and ensures that session establishment can always succeed even when
     * all OPKs are exhausted.
     */
    suspend fun generateLastResortPreKey(): OneTimePreKeyRecord {
        return mutex.withLock {
            val id = nextId.incrementAndGet()
            val buffer = ByteArray(68)
            val len = longArrayOf(buffer.size.toLong())
            val rc = EnchantCrypto.enchant_prekey_generate_batch(1, id, buffer, len)
            if (rc != EnchantCrypto.SUCCESS) {
                throw IllegalStateException("enchant_prekey_generate_batch failed: $rc")
            }
            val publicKey = buffer.copyOfRange(4, 36)
            val privateKey = buffer.copyOfRange(36, 68)
            EnchantCrypto.enchant_secure_zero(buffer, buffer.size.toLong())
            val record = OneTimePreKeyRecord(
                id = id,
                publicKey = publicKey,
                privateKey = privateKey,
                timestamp = System.currentTimeMillis(),
                isLastResort = true
            )
            lastResortPreKey = record
            dao?.storeOneTimePreKeys(listOf(record))
            record
        }
    }

    /**
     * Consume a one-time prekey by ID. The private key is zeroed and the
     * record is removed from storage.
     *
     * @param id the prekey ID to consume
     * @return the prekey record (with private key zeroed after return), or null if not found
     */
    suspend fun consumeOneTimePreKey(id: Int): OneTimePreKeyRecord? {
        return mutex.withLock {
            val record = oneTimePreKeys.remove(id) ?: return@withLock null
            dao?.deleteOneTimePreKey(id)
            record
        }
    }

    /** Get a one-time prekey by ID without consuming it. */
    suspend fun getOneTimePreKey(id: Int): OneTimePreKeyRecord? = mutex.withLock {
        oneTimePreKeys[id]?.copy() ?: lastResortPreKey?.takeIf { it.id == id }?.copy()
    }

    /** Check if a one-time prekey exists. */
    suspend fun hasOneTimePreKey(id: Int): Boolean = mutex.withLock {
        oneTimePreKeys.containsKey(id)
    }

    /** Get all non-last-resort one-time prekey IDs (including consumed-eligible set). */
    suspend fun getAllOneTimePreKeyIds(): List<Int> = mutex.withLock {
        oneTimePreKeys.filter { !it.value.isLastResort }.map { it.key }
    }

    /** Get the count of available one-time prekeys (excluding last-resort). */
    suspend fun getOneTimePreKeyCount(): Int = mutex.withLock {
        oneTimePreKeys.count { !it.value.isLastResort }
    }

    /** Check if OPK count is below the top-up threshold. */
    suspend fun needsOpkTopUp(): Boolean = mutex.withLock {
        oneTimePreKeys.count { !it.value.isLastResort } < OPK_TOP_UP_THRESHOLD
    }

    /** Remove stale one-time prekeys (older than threshold). */
    suspend fun cleanStaleOneTimePreKeys(thresholdMs: Long = OPK_STALE_THRESHOLD_MS, minCount: Int = 20) = mutex.withLock {
        val currentCount = oneTimePreKeys.count { !it.value.isLastResort }
        if (currentCount <= minCount) return@withLock

        val now = System.currentTimeMillis()
        val stale = oneTimePreKeys.filter { (id, record) ->
            !record.isLastResort && (now - record.timestamp) > thresholdMs
        }.toList().sortedBy { it.second.timestamp }

        val toRemoveCount = minOf(stale.size, currentCount - minCount)
        for (i in 0 until toRemoveCount) {
            val (id, record) = stale[i]
            CryptoPrimitives.zeroBytes(record.privateKey)
            oneTimePreKeys.remove(id)
            dao?.deleteOneTimePreKey(id)
        }
    }

    /** Get all non-last-resort OPK public keys for upload to the server. */
    suspend fun getOneTimePreKeyPublicKeys(): List<PreKeyPublic> = mutex.withLock {
        oneTimePreKeys.filter { !it.value.isLastResort }.map { (id, record) ->
            PreKeyPublic(id = id, publicKey = record.publicKey)
        }
    }

    /** Get the last-resort prekey public key. */
    suspend fun getLastResortPreKeyPublic(): PreKeyPublic? = mutex.withLock {
        lastResortPreKey?.let { PreKeyPublic(id = it.id, publicKey = it.publicKey) }
    }

    /** Load all prekeys from DB into cache. */
    suspend fun loadFromDb() = mutex.withLock {
        dao?.loadSignedPreKeys()?.forEach { record ->
            signedPreKeys[record.id] = record
            currentSignedPreKeyId = maxOf(currentSignedPreKeyId, record.id)
        }
        dao?.loadOneTimePreKeys()?.forEach { record ->
            if (record.isLastResort) {
                lastResortPreKey = record
            } else {
                oneTimePreKeys[record.id] = record
            }
            nextId.updateAndGet { maxOf(it, record.id) }
        }
    }

    /** Clear all cached prekeys (does not affect DB). */
    fun clearCache() {
        signedPreKeys.clear()
        oneTimePreKeys.clear()
        lastResortPreKey = null
    }
}
