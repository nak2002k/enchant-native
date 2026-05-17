package org.enchant.core.performance

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MessageCache — Full Coverage")
class MessageCacheTest {

    private data class TestMessage(val id: String, val content: String)

    private lateinit var cache: MessageCache<TestMessage>

    @BeforeEach
    fun setUp() {
        cache = MessageCache<TestMessage>(maxMessagesPerConversation = 3, maxConversations = 2)
    }

    @Nested @DisplayName("Cache Messages")
    inner class CacheMessagesTest {
        @Test @DisplayName("cacheMessages stores messages for a conversation")
        fun `cache messages`() {
            val messages = listOf(
                TestMessage("1", "Hello"),
                TestMessage("2", "World")
            )
            cache.cacheMessages("conv-1", messages) { it.id }
            val cached = cache.getCachedMessages("conv-1")
            assertNotNull(cached)
            assertEquals(2, cached!!.size)
        }

        @Test @DisplayName("cacheMessages does nothing for empty list")
        fun `cache messages empty`() {
            cache.cacheMessages("conv-1", emptyList()) { it.id }
            val cached = cache.getCachedMessages("conv-1")
            assertNull(cached)
        }

        @Test @DisplayName("cacheMessages overwrites existing messages with same id")
        fun `cache messages overwrite`() {
            cache.cacheMessages("conv-1", listOf(TestMessage("1", "Hello"))) { it.id }
            cache.cacheMessages("conv-1", listOf(TestMessage("1", "Updated"))) { it.id }
            val cached = cache.getCachedMessages("conv-1")
            assertNotNull(cached)
            assertEquals(1, cached!!.size)
            assertEquals("Updated", cached[0].content)
        }

        @Test @DisplayName("cacheMessages respects maxMessagesPerConversation limit")
        fun `cache messages respects limit`() {
            val messages = listOf(
                TestMessage("1", "Msg 1"),
                TestMessage("2", "Msg 2"),
                TestMessage("3", "Msg 3"),
                TestMessage("4", "Msg 4")
            )
            cache.cacheMessages("conv-1", messages) { it.id }
            val cached = cache.getCachedMessages("conv-1")
            assertNotNull(cached)
            assertTrue(cached!!.size <= 3)
        }
    }

    @Nested @DisplayName("Get Cached Messages")
    inner class GetCachedMessagesTest {
        @Test @DisplayName("getCachedMessages returns null for unknown conversation")
        fun `get cached messages unknown`() {
            val result = cache.getCachedMessages("unknown")
            assertNull(result)
        }

        @Test @DisplayName("getCachedMessages returns messages for known conversation")
        fun `get cached messages known`() {
            cache.cacheMessages("conv-1", listOf(TestMessage("1", "Hello"))) { it.id }
            val result = cache.getCachedMessages("conv-1")
            assertNotNull(result)
            assertEquals(1, result!!.size)
            assertEquals("Hello", result[0].content)
        }

        @Test @DisplayName("getCachedMessages returns independent copy")
        fun `get cached messages copy`() {
            cache.cacheMessages("conv-1", listOf(TestMessage("1", "Hello"))) { it.id }
            val first = cache.getCachedMessages("conv-1")
            val second = cache.getCachedMessages("conv-1")
            assertNotSame(first, second)
        }
    }

    @Nested @DisplayName("Invalidate Conversation")
    inner class InvalidateConversationTest {
        @Test @DisplayName("invalidateConversation removes cached messages")
        fun `invalidate conversation`() {
            cache.cacheMessages("conv-1", listOf(TestMessage("1", "Hello"))) { it.id }
            cache.invalidateConversation("conv-1")
            val result = cache.getCachedMessages("conv-1")
            assertNull(result)
        }

        @Test @DisplayName("invalidateConversation does nothing for unknown conversation")
        fun `invalidate unknown conversation`() {
            cache.invalidateConversation("unknown")
        }

        @Test @DisplayName("invalidateConversation does not affect other conversations")
        fun `invalidate preserves others`() {
            cache.cacheMessages("conv-1", listOf(TestMessage("1", "Hello"))) { it.id }
            cache.cacheMessages("conv-2", listOf(TestMessage("2", "World"))) { it.id }
            cache.invalidateConversation("conv-1")
            assertNull(cache.getCachedMessages("conv-1"))
            assertNotNull(cache.getCachedMessages("conv-2"))
        }
    }

    @Nested @DisplayName("Clear All")
    inner class ClearAllTest {
        @Test @DisplayName("clearAll removes all cached messages")
        fun `clear all`() {
            cache.cacheMessages("conv-1", listOf(TestMessage("1", "Hello"))) { it.id }
            cache.cacheMessages("conv-2", listOf(TestMessage("2", "World"))) { it.id }
            cache.clearAll()
            assertNull(cache.getCachedMessages("conv-1"))
            assertNull(cache.getCachedMessages("conv-2"))
        }

        @Test @DisplayName("clearAll on empty cache does nothing")
        fun `clear all empty`() {
            cache.clearAll()
        }
    }

    @Nested @DisplayName("LRU Eviction")
    inner class LruEvictionTest {
        @Test @DisplayName("evicts oldest conversation when maxConversations exceeded")
        fun `evict oldest conversation`() {
            cache.cacheMessages("conv-1", listOf(TestMessage("1", "A"))) { it.id }
            cache.cacheMessages("conv-2", listOf(TestMessage("2", "B"))) { it.id }
            cache.cacheMessages("conv-3", listOf(TestMessage("3", "C"))) { it.id }
            // conv-1 should be evicted (maxConversations = 2)
            assertNull(cache.getCachedMessages("conv-1"))
            assertNotNull(cache.getCachedMessages("conv-2"))
            assertNotNull(cache.getCachedMessages("conv-3"))
        }

        @Test @DisplayName("accessing conversation prevents eviction")
        fun `access prevents eviction`() {
            cache.cacheMessages("conv-1", listOf(TestMessage("1", "A"))) { it.id }
            cache.cacheMessages("conv-2", listOf(TestMessage("2", "B"))) { it.id }
            cache.getCachedMessages("conv-1") // access conv-1
            cache.cacheMessages("conv-3", listOf(TestMessage("3", "C"))) { it.id }
            // conv-2 should be evicted (conv-1 was accessed recently)
            assertNull(cache.getCachedMessages("conv-2"))
            assertNotNull(cache.getCachedMessages("conv-1"))
            assertNotNull(cache.getCachedMessages("conv-3"))
        }
    }

    @Nested @DisplayName("Multiple Conversations")
    inner class MultipleConversationsTest {
        @Test @DisplayName("handles multiple conversations independently")
        fun `multiple conversations`() {
            cache.cacheMessages("conv-1", listOf(TestMessage("1", "Hello"))) { it.id }
            cache.cacheMessages("conv-2", listOf(TestMessage("2", "World"))) { it.id }
            val conv1 = cache.getCachedMessages("conv-1")
            val conv2 = cache.getCachedMessages("conv-2")
            assertNotNull(conv1)
            assertNotNull(conv2)
            assertEquals("Hello", conv1!![0].content)
            assertEquals("World", conv2!![0].content)
        }
    }
}
