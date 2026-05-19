package org.enchant.core.base

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LRUCacheTest {

    @Test
    fun `put and get returns stored value`() {
        val cache = LRUCache<String, String>(10)
        cache.put("key", "value")
        assertEquals("value", cache.get("key"))
    }

    @Test
    fun `get returns null for missing key`() {
        val cache = LRUCache<String, String>(10)
        assertNull(cache.get("missing"))
    }

    @Test
    fun `evicts eldest when over max size`() {
        val cache = LRUCache<String, String>(3)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.put("c", "3")
        cache.put("d", "4")
        assertNull(cache.get("a"))
        assertNotNull(cache.get("d"))
    }

    @Test
    fun `remove deletes key`() {
        val cache = LRUCache<String, String>(10)
        cache.put("key", "value")
        cache.remove("key")
        assertNull(cache.get("key"))
    }

    @Test
    fun `clear empties cache`() {
        val cache = LRUCache<String, String>(10)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.clear()
        assertEquals(0, cache.size())
    }

    @Test
    fun `size returns correct count`() {
        val cache = LRUCache<String, String>(10)
        assertEquals(0, cache.size())
        cache.put("a", "1")
        assertEquals(1, cache.size())
    }

    @Test
    fun `contains returns true for existing key`() {
        val cache = LRUCache<String, String>(10)
        cache.put("key", "value")
        assertTrue(cache.contains("key"))
    }

    @Test
    fun `contains returns false for missing key`() {
        val cache = LRUCache<String, String>(10)
        assertFalse(cache.contains("missing"))
    }

    @Test
    fun `evict removes specified count`() {
        val cache = LRUCache<String, String>(10)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.put("c", "3")
        cache.evict(2)
        assertEquals(1, cache.size())
        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }

    @Test
    fun `values returns all entries`() {
        val cache = LRUCache<String, String>(10)
        cache.put("a", "1")
        cache.put("b", "2")
        assertEquals(listOf("1", "2"), cache.values())
    }

    @Test
    fun `put updates existing value`() {
        val cache = LRUCache<String, String>(10)
        cache.put("key", "first")
        cache.put("key", "second")
        assertEquals("second", cache.get("key"))
    }

    @Test
    fun `access prevents eviction`() {
        val cache = LRUCache<String, String>(3)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.put("c", "3")
        cache.get("a")
        cache.put("d", "4")
        assertEquals("1", cache.get("a"))
        assertNull(cache.get("b"))
        assertEquals("4", cache.get("d"))
    }

    @Test
    fun `evict with count larger than size clears all`() {
        val cache = LRUCache<String, String>(10)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.evict(100)
        assertEquals(0, cache.size())
    }

    @Test
    fun `zero max size evicts immediately`() {
        val cache = LRUCache<String, String>(0)
        cache.put("a", "1")
        assertEquals(0, cache.size())
    }

}
