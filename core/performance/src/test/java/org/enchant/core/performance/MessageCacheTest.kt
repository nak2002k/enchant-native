package org.enchant.core.performance

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MessageCache")
class MessageCacheTest {

    private data class TestMsg(val id: String, val text: String)

    private lateinit var cache: MessageCache<TestMsg>

    @BeforeEach
    fun setUp() {
        cache = MessageCache(maxMessagesPerConversation = 3, maxConversations = 2)
    }

    @Nested @DisplayName("cache hit")
    inner class CacheHitTest {
        @Test @DisplayName("returns cached messages for conversation")
        fun `cached messages returned`() {
            val msgs = listOf(TestMsg("1", "Hello"), TestMsg("2", "World"))
            cache.cacheMessages("conv1", msgs) { it.id }
            val result = cache.getCachedMessages("conv1")
            assertNotNull(result)
            assertEquals(2, result!!.size)
        }

        @Test @DisplayName("returns null for unknown conversation")
        fun `unknown conversation returns null`() {
            assertNull(cache.getCachedMessages("nonexistent"))
        }

        @Test @DisplayName("empty list produces no cache entry")
        fun `empty list`() {
            cache.cacheMessages("conv1", emptyList()) { it.id }
            assertNull(cache.getCachedMessages("conv1"))
        }
    }

    @Nested @DisplayName("eviction")
    inner class EvictionTest {
        @Test @DisplayName("evicts oldest conversation when at capacity")
        fun `conversation eviction`() {
            cache.cacheMessages("conv1", listOf(TestMsg("1", "A"))) { it.id }
            cache.cacheMessages("conv2", listOf(TestMsg("2", "B"))) { it.id }
            cache.cacheMessages("conv3", listOf(TestMsg("3", "C"))) { it.id }

            assertNull(cache.getCachedMessages("conv1"))
            assertNotNull(cache.getCachedMessages("conv2"))
            assertNotNull(cache.getCachedMessages("conv3"))
        }

        @Test @DisplayName("evicts oldest messages within a conversation")
        fun `message eviction per conversation`() {
            val msgs = (1..5).map { TestMsg(it.toString(), "Msg $it") }
            cache.cacheMessages("conv1", msgs) { it.id }

            val cached = cache.getCachedMessages("conv1")
            assertNotNull(cached)
            assertEquals(3, cached!!.size) // maxMessagesPerConversation = 3
            assertTrue(cached.any { it.id == "3" })
            assertTrue(cached.any { it.id == "4" })
            assertTrue(cached.any { it.id == "5" })
        }
    }

    @Nested @DisplayName("invalidation")
    inner class InvalidationTest {
        @Test @DisplayName("invalidating conversation removes it")
        fun `invalidate removes conversation`() {
            cache.cacheMessages("conv1", listOf(TestMsg("1", "A"))) { it.id }
            assertNotNull(cache.getCachedMessages("conv1"))
            cache.invalidateConversation("conv1")
            assertNull(cache.getCachedMessages("conv1"))
        }

        @Test @DisplayName("clearAll removes everything")
        fun `clear all`() {
            cache.cacheMessages("conv1", listOf(TestMsg("1", "A"))) { it.id }
            cache.cacheMessages("conv2", listOf(TestMsg("2", "B"))) { it.id }
            cache.clearAll()
            assertNull(cache.getCachedMessages("conv1"))
            assertNull(cache.getCachedMessages("conv2"))
        }

        @Test @DisplayName("invalidate unknown conversation is no-op")
        fun `invalidate unknown`() {
            cache.invalidateConversation("unknown")
            assertNull(cache.getCachedMessages("unknown"))
        }
    }

    @Nested @DisplayName("deduplication")
    inner class DedupTest {
        @Test @DisplayName("same id replaces existing message")
        fun `same id replaces`() {
            cache.cacheMessages("conv1", listOf(TestMsg("1", "Original"))) { it.id }
            cache.cacheMessages("conv1", listOf(TestMsg("1", "Updated"))) { it.id }

            val cached = cache.getCachedMessages("conv1")
            assertEquals(1, cached!!.size)
            assertEquals("Updated", cached[0].text)
        }
    }
}