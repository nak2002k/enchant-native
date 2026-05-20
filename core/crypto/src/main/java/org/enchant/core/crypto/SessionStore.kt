package org.enchant.core.crypto

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe session storage with in-memory LRU cache and optional DB persistence.
 *
 * Stores serialized DoubleRatchet states keyed by user ID. Provides atomic
 * load/store/delete operations with a bounded in-memory cache to avoid
 * repeated DB lookups during active conversations.
 *
 * NOTE: The DAO interface is defined here for decoupling. The actual DAO
 * implementation lives in the :core:database module.
 */
class SessionStore {
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, ByteArray>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>): Boolean = size > CACHE_SIZE
    }
    private var dao: SessionDao? = null

    companion object {
        private const val CACHE_SIZE = 500
    }

    /**
     * Set the DAO for persistent storage.
     * NOTE: SessionDao interface is expected to be implemented in :core:database module.
     */
    fun setDao(dao: SessionDao?) {
        this.dao = dao
    }

    /** Load a session from cache or DB. Returns null if not found. */
    suspend fun load(userId: String): ByteArray? = mutex.withLock {
        cache[userId]?.let { return@withLock it.copyOf() }
        dao?.load(userId)?.let {
            cache[userId] = it
            return@withLock it.copyOf()
        }
        null
    }

    /** Store a session in cache and DB. */
    suspend fun store(userId: String, serialized: ByteArray) = mutex.withLock {
        cache[userId] = serialized.copyOf()
        dao?.store(userId, serialized)
    }

    /** Delete a session from cache and DB. */
    suspend fun delete(userId: String) = mutex.withLock {
        cache.remove(userId)
        dao?.delete(userId)
    }

    /** Check if a session exists (cache or DB). */
    suspend fun hasSession(userId: String): Boolean = mutex.withLock {
        cache.containsKey(userId) || dao?.hasSession(userId) == true
    }

    /** Delete all sessions for a user (all device IDs). */
    suspend fun deleteAllForUser(userId: String) = mutex.withLock {
        cache.keys.filter { it.startsWith("$userId:") }.forEach { cache.remove(it) }
        dao?.deleteAllForUser(userId)
    }

    /** Get all user IDs with cached sessions. */
    fun getCachedUserIds(): Set<String> = cache.keys.toSet()

    /** Load all sessions from DB into cache. */
    suspend fun loadAll(): List<SessionRow> = mutex.withLock {
        val rows = dao?.loadAll() ?: emptyList()
        rows.forEach { row ->
            cache[row.userId] = row.serialized.copyOf()
        }
        rows
    }

    /** Clear the in-memory cache (does not affect DB). */
    fun clearCache() {
        cache.clear()
    }

    /**
     * DAO interface for session persistence.
     * Implement this in :core:database module.
     */
    interface SessionDao {
        suspend fun load(userId: String): ByteArray?
        suspend fun store(userId: String, serialized: ByteArray)
        suspend fun delete(userId: String)
        suspend fun hasSession(userId: String): Boolean
        suspend fun deleteAllForUser(userId: String)
        suspend fun loadAll(): List<SessionRow>
    }

    data class SessionRow(
        val userId: String,
        val deviceId: String,
        val serialized: ByteArray
    )
}
