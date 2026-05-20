package org.enchant.core.crypto

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SessionStore — Thread-safe session storage")
class SessionStoreTest {

    private lateinit var store: SessionStore
    private lateinit var mockDao: SessionStore.SessionDao

    @BeforeEach
    fun setUp() {
        store = SessionStore()
        mockDao = InMemorySessionDao()
        store.setDao(mockDao)
    }

    @Nested @DisplayName("Store and Load")
    inner class StoreLoadTest {
        @Test @DisplayName("store then load returns same data")
        fun `store load roundtrip`() = runTest {
            val userId = "user1"
            val data = ByteArray(100) { it.toByte() }
            store.store(userId, data)
            val loaded = store.load(userId)
            assertNotNull(loaded)
            assertArrayEquals(data, loaded)
        }

        @Test @DisplayName("load non-existent returns null")
        fun `load non existent returns null`() = runTest {
            assertNull(store.load("nonexistent"))
        }

        @Test @DisplayName("store overwrites existing")
        fun `store overwrites`() = runTest {
            val userId = "user1"
            store.store(userId, ByteArray(10) { 1 })
            store.store(userId, ByteArray(10) { 2 })
            val loaded = store.load(userId)
            assertNotNull(loaded)
            assertTrue(loaded!!.all { it == 2.toByte() })
        }
    }

    @Nested @DisplayName("Delete")
    inner class DeleteTest {
        @Test @DisplayName("delete removes from cache and DB")
        fun `delete removes`() = runTest {
            val userId = "user1"
            store.store(userId, ByteArray(10))
            store.delete(userId)
            assertNull(store.load(userId))
        }

        @Test @DisplayName("delete non-existent is no-op")
        fun `delete non existent no op`() = runTest {
            store.delete("nonexistent")
            assertNull(store.load("nonexistent"))
        }

        @Test @DisplayName("deleteAllForUser removes all sessions")
        fun `delete all for user`() = runTest {
            store.store("user1:0", ByteArray(10))
            store.store("user1:1", ByteArray(10))
            store.store("user2:0", ByteArray(10))
            store.deleteAllForUser("user1")
            assertNull(store.load("user1:0"))
            assertNull(store.load("user1:1"))
            assertNotNull(store.load("user2:0"))
        }
    }

    @Nested @DisplayName("Has Session")
    inner class HasSessionTest {
        @Test @DisplayName("hasSession true after store")
        fun `has session true`() = runTest {
            store.store("user1", ByteArray(10))
            assertTrue(store.hasSession("user1"))
        }

        @Test @DisplayName("hasSession false for non-existent")
        fun `has session false`() = runTest {
            assertFalse(store.hasSession("nonexistent"))
        }
    }

    @Nested @DisplayName("Cache")
    inner class CacheTest {
        @Test @DisplayName("clearCache removes cached entries")
        fun `clear cache`() = runTest {
            store.store("user1", ByteArray(10))
            store.clearCache()
            // Should still be in DB
            assertNotNull(store.load("user1"))
        }

        @Test @DisplayName("getCachedUserIds returns cached keys")
        fun `get cached user ids`() = runTest {
            store.store("user1", ByteArray(10))
            store.store("user2", ByteArray(10))
            val ids = store.getCachedUserIds()
            assertTrue(ids.contains("user1"))
            assertTrue(ids.contains("user2"))
        }
    }

    @Nested @DisplayName("LRU Eviction")
    inner class LruEvictionTest {
        @Test @DisplayName("cache evicts oldest entries when full")
        fun `lru eviction`() = runTest {
            // Fill cache beyond capacity
            repeat(501) { i ->
                store.store("user_$i", ByteArray(10) { i.toByte() })
            }
            // Cache should have at most 500 entries
            assertTrue(store.getCachedUserIds().size <= 500)
        }
    }

    private class InMemorySessionDao : SessionStore.SessionDao {
        private val store = mutableMapOf<String, ByteArray>()
        override suspend fun load(userId: String): ByteArray? = store[userId]?.copyOf()
        override suspend fun store(userId: String, serialized: ByteArray) { store[userId] = serialized.copyOf() }
        override suspend fun delete(userId: String) { store.remove(userId) }
        override suspend fun hasSession(userId: String): Boolean = store.containsKey(userId)
        override suspend fun deleteAllForUser(userId: String) { store.keys.filter { it.startsWith("$userId:") }.forEach { store.remove(it) } }
        override suspend fun loadAll(): List<SessionStore.SessionRow> = store.map { (k, v) -> SessionStore.SessionRow(k, "0", v.copyOf()) }
    }
}
