package org.enchant.core.crypto

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Identity key management: trust levels, safety numbers, identity change detection,
 * and non-blocking approval.
 *
 * Tracks the identity keys of all contacts and determines whether a key change
 * requires user verification. Supports three trust levels:
 * - VERIFIED: User has manually verified the safety number
 * - TRUSTED_ON_FIRST_USE: Key was accepted on first contact (implicit trust)
 * - UNVERIFIED: Key changed and user has not yet approved
 *
 * NOTE: The DAO interface is defined here for decoupling. The actual DAO
 * implementation lives in the :core:database module.
 */
class IdentityStore {
    private val mutex = Mutex()
    private val identities = mutableMapOf<String, IdentityRecord>()
    private var dao: IdentityDao? = null

    /**
     * Set the DAO for persistent storage.
     * NOTE: IdentityDao interface is expected to be implemented in :core:database module.
     */
    fun setDao(dao: IdentityDao?) {
        this.dao = dao
    }

    /**
     * Save or update an identity key for a user.
     *
     * @param userId the user's ID
     * @param publicKey their Ed25519 identity public key
     * @param trustLevel the trust level to assign
     * @return IdentityChange indicating what happened
     */
    suspend fun saveIdentity(userId: String, publicKey: ByteArray, trustLevel: TrustLevel = TrustLevel.TRUSTED_ON_FIRST_USE): IdentityChange {
        return mutex.withLock {
            val existing = identities[userId]
            if (existing == null) {
                // First time seeing this identity
                val record = IdentityRecord(
                    userId = userId,
                    publicKey = publicKey.copyOf(),
                    trustLevel = trustLevel,
                    timestamp = System.currentTimeMillis()
                )
                identities[userId] = record
                dao?.save(record)
                IdentityChange.NEW
            } else if (!existing.publicKey.contentEquals(publicKey)) {
                // Identity key changed — requires verification
                val record = IdentityRecord(
                    userId = userId,
                    publicKey = publicKey.copyOf(),
                    trustLevel = TrustLevel.UNVERIFIED,
                    timestamp = System.currentTimeMillis()
                )
                identities[userId] = record
                dao?.save(record)
                IdentityChange.CHANGED
            } else {
                IdentityChange.UNCHANGED
            }
        }
    }

    /** Get the identity key for a user. Returns null if not found. */
    suspend fun getIdentity(userId: String): ByteArray? = mutex.withLock {
        identities[userId]?.publicKey?.copyOf()
    }

    /** Get the full identity record for a user. */
    suspend fun getRecord(userId: String): IdentityRecord? = mutex.withLock {
        identities[userId]?.copy()
    }

    /** Check if an identity is trusted for sending. */
    suspend fun isTrustedForSending(userId: String): Boolean = mutex.withLock {
        val record = identities[userId] ?: return@withLock false
        record.trustLevel != TrustLevel.UNVERIFIED
    }

    /** Mark an identity as verified (user confirmed safety number). */
    suspend fun verifyIdentity(userId: String) = mutex.withLock {
        val record = identities[userId] ?: return@withLock
        identities[userId] = record.copy(trustLevel = TrustLevel.VERIFIED)
        dao?.save(identities[userId]!!)
    }

    /** Set non-blocking approval (allow sending without explicit verification). */
    suspend fun setNonBlockingApproval(userId: String, approved: Boolean) = mutex.withLock {
        val record = identities[userId] ?: return@withLock
        identities[userId] = record.copy(nonBlockingApproval = approved)
        dao?.save(identities[userId]!!)
    }

    /** Check if non-blocking approval is set for a user. */
    suspend fun isNonBlockingApproved(userId: String): Boolean = mutex.withLock {
        identities[userId]?.nonBlockingApproval ?: false
    }

    /** Delete an identity record. */
    suspend fun deleteIdentity(userId: String) = mutex.withLock {
        identities.remove(userId)
        dao?.delete(userId)
    }

    /**
     * Compute a safety number fingerprint for two identity keys.
     * Format: groups of 4 hex digits separated by spaces.
     *
     * @param ourPublicKey our Ed25519 public key
     * @param theirPublicKey their Ed25519 public key
     * @return human-readable safety number string
     */
    fun computeSafetyNumber(ourPublicKey: ByteArray, theirPublicKey: ByteArray): String {
        val combined = ByteArray(ourPublicKey.size + theirPublicKey.size)
        ourPublicKey.copyInto(combined, 0)
        theirPublicKey.copyInto(combined, ourPublicKey.size)
        val hash = CryptoPrimitives.sha512(combined)
        val hex = hash.joinToString("") { String.format("%02X", it) }
        return hex.chunked(4).joinToString(" ").take(59)
    }

    /** Check if an identity key has changed from the stored one. */
    suspend fun hasIdentityChanged(userId: String, newPublicKey: ByteArray): Boolean = mutex.withLock {
        val existing = identities[userId] ?: return@withLock false
        !existing.publicKey.contentEquals(newPublicKey)
    }

    /** Load all identities from DB into cache. */
    suspend fun loadFromDb() = mutex.withLock {
        dao?.loadAll()?.forEach { record ->
            identities[record.userId] = record
        }
    }

    /** Clear all cached identities (does not affect DB). */
    fun clearCache() {
        identities.clear()
    }

    // ──────────────────────────────────────────────
    // Data Classes & Enums
    // ──────────────────────────────────────────────

    enum class TrustLevel {
        VERIFIED,
        TRUSTED_ON_FIRST_USE,
        UNVERIFIED
    }

    enum class IdentityChange {
        NEW,
        CHANGED,
        UNCHANGED
    }

    data class IdentityRecord(
        val userId: String,
        val publicKey: ByteArray,
        val trustLevel: TrustLevel,
        val timestamp: Long,
        val nonBlockingApproval: Boolean = false
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is IdentityRecord) return false
            return userId == other.userId && publicKey.contentEquals(other.publicKey)
        }
        override fun hashCode(): Int = userId.hashCode() xor publicKey.contentHashCode()
    }

    /**
     * DAO interface for identity persistence.
     * Implement this in :core:database module.
     */
    interface IdentityDao {
        suspend fun save(record: IdentityRecord)
        suspend fun load(userId: String): IdentityRecord?
        suspend fun delete(userId: String)
        suspend fun loadAll(): List<IdentityRecord>
    }
}
