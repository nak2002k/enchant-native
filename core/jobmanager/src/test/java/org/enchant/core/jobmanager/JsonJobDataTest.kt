package org.enchant.core.jobmanager

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class JsonJobDataTest {
    @Test
    fun `builder creates empty JsonJobData`() {
        val data = JsonJobData.Builder().build()
        assertNull(data.getString("key"))
        assertNull(data.getLong("key"))
        assertNull(data.getInt("key"))
        assertNull(data.getBoolean("key"))
        assertNull(data.getBlob("key"))
    }

    @Test
    fun `putString and getString roundtrip`() {
        val data = JsonJobData.Builder()
            .putString("name", "test")
            .build()
        assertEquals("test", data.getString("name"))
        assertNull(data.getString("other"))
    }

    @Test
    fun `putLong and getLong roundtrip`() {
        val data = JsonJobData.Builder()
            .putLong("timestamp", 1234567890L)
            .build()
        assertEquals(1234567890L, data.getLong("timestamp"))
        assertNull(data.getLong("other"))
    }

    @Test
    fun `putInt and getInt roundtrip`() {
        val data = JsonJobData.Builder()
            .putInt("count", 42)
            .build()
        assertEquals(42, data.getInt("count"))
        assertNull(data.getInt("other"))
    }

    @Test
    fun `putBoolean and getBoolean roundtrip`() {
        val dataTrue = JsonJobData.Builder()
            .putBoolean("flag", true)
            .build()
        assertTrue(dataTrue.getBoolean("flag")!!)

        val dataFalse = JsonJobData.Builder()
            .putBoolean("flag", false)
            .build()
        assertFalse(dataFalse.getBoolean("flag")!!)
    }

    @Test
    fun `putBlob and getBlob roundtrip`() {
        val blob = byteArrayOf(1, 2, 3, 4, 5)
        val data = JsonJobData.Builder()
            .putBlob("data", blob)
            .build()
        assertArrayEquals(blob, data.getBlob("data"))
        assertNull(data.getBlob("other"))
    }

    @Test
    fun `serialize empty data returns null`() {
        val data = JsonJobData.Builder().build()
        assertNull(data.serialize())
    }

    @Test
    fun `serialize and deserialize roundtrip`() {
        val original = JsonJobData.Builder()
            .putString("name", "test")
            .putLong("timestamp", 1234567890L)
            .putInt("count", 42)
            .putBoolean("flag", true)
            .putBlob("data", byteArrayOf(1, 2, 3))
            .build()

        val serialized = original.serialize()
        assertNotNull(serialized)

        val restored = JsonJobData.deserialize(serialized)
        assertEquals("test", restored.getString("name"))
        assertEquals(1234567890L, restored.getLong("timestamp"))
        assertEquals(42, restored.getInt("count"))
        assertTrue(restored.getBoolean("flag")!!)
        assertArrayEquals(byteArrayOf(1, 2, 3), restored.getBlob("data"))
    }

    @Test
    fun `deserialize null returns empty JsonJobData`() {
        val data = JsonJobData.deserialize(null)
        assertNull(data.getString("key"))
        assertNull(data.getLong("key"))
        assertNull(data.getInt("key"))
        assertNull(data.getBoolean("key"))
        assertNull(data.getBlob("key"))
    }

    @Test
    fun `multiple values of same type`() {
        val data = JsonJobData.Builder()
            .putString("a", "value-a")
            .putString("b", "value-b")
            .putString("c", "value-c")
            .build()
        assertEquals("value-a", data.getString("a"))
        assertEquals("value-b", data.getString("b"))
        assertEquals("value-c", data.getString("c"))
    }

    @Test
    fun `builder chaining works`() {
        val data = JsonJobData.Builder()
            .putString("name", "test")
            .putLong("id", 1L)
            .putInt("count", 10)
            .putBoolean("active", true)
            .build()
        assertEquals("test", data.getString("name"))
        assertEquals(1L, data.getLong("id"))
        assertEquals(10, data.getInt("count"))
        assertTrue(data.getBoolean("active")!!)
    }

    @Test
    fun `overwrite existing key`() {
        val data = JsonJobData.Builder()
            .putString("key", "original")
            .putString("key", "updated")
            .build()
        assertEquals("updated", data.getString("key"))
    }

    @Test
    fun `empty blob returns null`() {
        val data = JsonJobData.Builder().build()
        assertNull(data.getBlob("nonexistent"))
    }

    @Test
    fun `serialize preserves all types independently`() {
        val original = JsonJobData.Builder()
            .putString("str", "hello")
            .putLong("long", Long.MAX_VALUE)
            .putInt("int", Int.MIN_VALUE)
            .putBoolean("bool", false)
            .build()

        val restored = JsonJobData.deserialize(original.serialize())
        assertEquals("hello", restored.getString("str"))
        assertEquals(Long.MAX_VALUE, restored.getLong("long"))
        assertEquals(Int.MIN_VALUE, restored.getInt("int"))
        assertFalse(restored.getBoolean("bool")!!)
    }
}
