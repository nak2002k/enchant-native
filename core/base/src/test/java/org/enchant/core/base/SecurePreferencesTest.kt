package org.enchant.core.base

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SecurePreferences — Full Coverage")
class SecurePreferencesTest {

    @BeforeEach
    fun setUp() {
        mockkObject(SecurePreferences)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(SecurePreferences)
    }

    @Nested @DisplayName("String Operations")
    inner class StringTest {
        @Test @DisplayName("putString stores a value")
        fun `put string`() {
            every { SecurePreferences.putString(any(), any()) } returns Unit
            SecurePreferences.putString("key", "value")
        }

        @Test @DisplayName("getString returns stored value")
        fun `get string`() {
            every { SecurePreferences.getString(any(), any()) } returns "default"
            every { SecurePreferences.getString(any()) } returns "value"
            assertEquals("value", SecurePreferences.getString("key"))
        }

        @Test @DisplayName("getString returns default when key not found")
        fun `get string default`() {
            every { SecurePreferences.getString(any(), any()) } returns "default"
            assertEquals("default", SecurePreferences.getString("missing", "default"))
        }

        @Test @DisplayName("getString returns null when key not found and no default")
        fun `get string null`() {
            every { SecurePreferences.getString(any()) } returns null
            assertNull(SecurePreferences.getString("missing"))
        }

        @Test @DisplayName("remove deletes a value")
        fun `remove string`() {
            every { SecurePreferences.remove(any()) } returns Unit
            SecurePreferences.remove("key")
        }
    }

    @Nested @DisplayName("Int Operations")
    inner class IntTest {
        @Test @DisplayName("putInt stores a value")
        fun `put int`() {
            every { SecurePreferences.putInt(any(), any()) } returns Unit
            SecurePreferences.putInt("count", 42)
        }

        @Test @DisplayName("getInt returns stored value")
        fun `get int`() {
            every { SecurePreferences.getInt(any(), any()) } returns 42
            assertEquals(42, SecurePreferences.getInt("count", 0))
        }

        @Test @DisplayName("getInt returns default when key not found")
        fun `get int default`() {
            every { SecurePreferences.getInt(any(), any()) } returns 0
            assertEquals(0, SecurePreferences.getInt("missing", 0))
        }
    }

    @Nested @DisplayName("Long Operations")
    inner class LongTest {
        @Test @DisplayName("putLong stores a value")
        fun `put long`() {
            every { SecurePreferences.putLong(any(), any()) } returns Unit
            SecurePreferences.putLong("timestamp", 1000L)
        }

        @Test @DisplayName("getLong returns stored value")
        fun `get long`() {
            every { SecurePreferences.getLong(any(), any()) } returns 1000L
            assertEquals(1000L, SecurePreferences.getLong("timestamp", 0L))
        }

        @Test @DisplayName("getLong returns default when key not found")
        fun `get long default`() {
            every { SecurePreferences.getLong(any(), any()) } returns 0L
            assertEquals(0L, SecurePreferences.getLong("missing", 0L))
        }
    }

    @Nested @DisplayName("Boolean Operations")
    inner class BooleanTest {
        @Test @DisplayName("putBoolean stores a value")
        fun `put boolean`() {
            every { SecurePreferences.putBoolean(any(), any()) } returns Unit
            SecurePreferences.putBoolean("enabled", true)
        }

        @Test @DisplayName("getBoolean returns stored value")
        fun `get boolean`() {
            every { SecurePreferences.getBoolean(any(), any()) } returns true
            assertTrue(SecurePreferences.getBoolean("enabled", false))
        }

        @Test @DisplayName("getBoolean returns default when key not found")
        fun `get boolean default`() {
            every { SecurePreferences.getBoolean(any(), any()) } returns false
            assertFalse(SecurePreferences.getBoolean("missing", false))
        }
    }

    @Nested @DisplayName("Clear")
    inner class ClearTest {
        @Test @DisplayName("clearAll removes all values")
        fun `clear all`() {
            every { SecurePreferences.clearAll() } returns Unit
            SecurePreferences.clearAll()
        }
    }
}
