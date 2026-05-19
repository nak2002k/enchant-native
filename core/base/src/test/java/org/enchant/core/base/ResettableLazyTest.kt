package org.enchant.core.base

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResettableLazyTest {

    @Test
    fun `lazy initializer runs only once`() {
        var counter = 0
        val lazy = resettableLazy { counter++; "value" }
        assertEquals("value", lazy.getValue(this, ::dummy))
        assertEquals(1, counter)
        assertEquals("value", lazy.getValue(this, ::dummy))
        assertEquals(1, counter)
    }

    @Test
    fun `reset causes reinitialization`() {
        var counter = 0
        val lazy = resettableLazy { counter++; counter }
        assertEquals(1, lazy.getValue(this, ::dummy))
        lazy.reset()
        assertEquals(2, lazy.getValue(this, ::dummy))
        lazy.reset()
        assertEquals(3, lazy.getValue(this, ::dummy))
    }

    @Test
    fun `isInitialized returns false before first access`() {
        val lazy = resettableLazy { "test" }
        assertFalse(lazy.isInitialized())
    }

    @Test
    fun `isInitialized returns true after access`() {
        val lazy = resettableLazy { "test" }
        lazy.getValue(this, ::dummy)
        assertTrue(lazy.isInitialized())
    }

    @Test
    fun `isInitialized returns false after reset`() {
        val lazy = resettableLazy { "test" }
        lazy.getValue(this, ::dummy)
        lazy.reset()
        assertFalse(lazy.isInitialized())
    }

    @Test
    fun `delegated property syntax works`() {
        val test by resettableLazy { "delegated" }
        assertEquals("delegated", test)
    }

    @Test
    fun `thread safe concurrent access`() {
        val lazy = resettableLazy { "threadsafe" }
        val threads = (1..10).map {
            Thread {
                repeat(100) {
                    lazy.getValue(this, ::dummy)
                    lazy.reset()
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals("threadsafe", lazy.getValue(this, ::dummy))
    }

    private val dummy: String by resettableLazy { "dummy" }
}
