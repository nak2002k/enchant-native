package org.enchant.core.base

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class UuidUtilTest {

    @Test
    fun `parseOrNull returns UUID for valid string`() {
        val uuid = UuidUtil.parseOrNull("550e8400-e29b-41d4-a716-446655440000")
        assertEquals(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), uuid)
    }

    @Test
    fun `parseOrNull returns null for null input`() {
        assertNull(UuidUtil.parseOrNull(null))
    }

    @Test
    fun `parseOrNull returns null for invalid string`() {
        assertNull(UuidUtil.parseOrNull("not-a-uuid"))
    }

    @Test
    fun `parseOrThrow returns UUID for valid string`() {
        val uuid = UuidUtil.parseOrThrow("550e8400-e29b-41d4-a716-446655440000")
        assertEquals(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), uuid)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseOrThrow throws for invalid string`() {
        UuidUtil.parseOrThrow("not-a-uuid")
    }

    @Test
    fun `parseOrThrow from byte array`() {
        val uuid = UUID.randomUUID()
        val bytes = UuidUtil.toByteArray(uuid)
        val parsed = UuidUtil.parseOrThrow(bytes)
        assertEquals(uuid, parsed)
    }

    @Test
    fun `isUuid returns true for valid UUID`() {
        assertTrue(UuidUtil.isUuid("550e8400-e29b-41d4-a716-446655440000"))
    }

    @Test
    fun `isUuid returns false for invalid string`() {
        assertFalse(UuidUtil.isUuid("not-a-uuid"))
    }

    @Test
    fun `isUuid returns false for null`() {
        assertFalse(UuidUtil.isUuid(null))
    }

    @Test
    fun `toByteArray and fromByteArray roundtrip`() {
        val original = UUID.randomUUID()
        val bytes = UuidUtil.toByteArray(original)
        val restored = UuidUtil.fromByteArray(bytes)
        assertEquals(original, restored)
    }

    @Test
    fun `toByteArray produces 16 bytes`() {
        val uuid = UUID.randomUUID()
        val bytes = UuidUtil.toByteArray(uuid)
        assertEquals(16, bytes.size)
    }

    @Test
    fun `UNKNOWN_UUID is zero UUID`() {
        assertEquals(UUID(0, 0), UuidUtil.UNKNOWN_UUID)
    }

    @Test
    fun `parseOrThrow from byte array handles MSB and LSB`() {
        val uuid = UUID(0x1234567890ABCDEF, 0x0123456789ABCDEF)
        val bytes = UuidUtil.toByteArray(uuid)
        val parsed = UuidUtil.parseOrThrow(bytes)
        assertEquals(uuid, parsed)
    }

    @Test
    fun `parseOrNull handles empty string`() {
        assertNull(UuidUtil.parseOrNull(""))
    }
}
