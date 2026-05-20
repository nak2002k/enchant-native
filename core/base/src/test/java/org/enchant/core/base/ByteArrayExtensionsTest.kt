package org.enchant.core.base

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.MessageDigest

class ByteArrayExtensionsTest {

    @Test
    fun `toHexString produces uppercase hex`() {
        assertEquals("48656C6C6F", "Hello".toByteArray().toHexString())
    }

    @Test
    fun `toHexStringLower produces lowercase hex`() {
        assertEquals("48656c6c6f", "Hello".toByteArray().toHexStringLower())
    }

    @Test
    fun `toBase64 produces padded base64`() {
        assertEquals("SGVsbG8=", "Hello".toByteArray().toBase64())
    }

    @Test
    fun `toBase64UrlSafe produces unpadded url-safe base64`() {
        assertEquals("SGVsbG8", "Hello".toByteArray().toBase64UrlSafe())
    }

    @Test
    fun `zero clears all bytes`() {
        val arr = byteArrayOf(1, 2, 3, 4, 5)
        arr.zero()
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0), arr)
    }

    @Test
    fun `zero on empty array does nothing`() {
        val arr = ByteArray(0)
        arr.zero()
        assertArrayEquals(ByteArray(0), arr)
    }

    @Test
    fun `constantTimeEquals returns true for identical arrays`() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(1, 2, 3)
        assertTrue(a constantTimeEquals b)
    }

    @Test
    fun `constantTimeEquals returns false for different arrays`() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(1, 2, 4)
        assertFalse(a constantTimeEquals b)
    }

    @Test
    fun `constantTimeEquals returns false for different lengths`() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(1, 2)
        assertFalse(a constantTimeEquals b)
    }

    @Test
    fun `constantTimeEquals returns true for same reference`() {
        val a = byteArrayOf(1, 2, 3)
        assertTrue(a constantTimeEquals a)
    }

    @Test
    fun `constantTimeEquals returns true for empty arrays`() {
        val a = ByteArray(0)
        val b = ByteArray(0)
        assertTrue(a constantTimeEquals b)
    }

    @Test
    fun `constantTimeEquals uses MessageDigest isEqual for timing safety`() {
        val a = ByteArray(32) { 0x42.toByte() }
        val b = ByteArray(32) { 0x42.toByte() }
        val expected = MessageDigest.isEqual(a, b)
        val actual = a constantTimeEquals b
        assertEquals(expected, actual)
    }

    @Test
    fun `constantTimeEquals detects single byte difference`() {
        val a = ByteArray(32) { 0x00.toByte() }
        val b = ByteArray(32) { 0x00.toByte() }
        b[15] = 0x01.toByte()
        assertFalse(a constantTimeEquals b)
    }

    @Test
    fun `sha256 produces correct hash size`() {
        val input = "Hello".toByteArray()
        val hash = input.sha256()
        assertEquals(32, hash.size)
    }

    @Test
    fun `sha256 is deterministic`() {
        val input = "Hello".toByteArray()
        val hash1 = input.sha256()
        val hash2 = input.sha256()
        assertArrayEquals(hash1, hash2)
    }

    @Test
    fun `sha256 of empty input`() {
        val hash = ByteArray(0).sha256()
        assertEquals(32, hash.size)
    }

    @Test
    fun `xor produces correct result`() {
        val a = byteArrayOf(0x0F.toByte(), 0xF0.toByte())
        val b = byteArrayOf(0xFF.toByte(), 0x00.toByte())
        val result = a xor b
        assertArrayEquals(byteArrayOf(0xF0.toByte(), 0xF0.toByte()), result)
    }

    @Test
    fun `xor with same values produces zeros`() {
        val a = byteArrayOf(0x55.toByte(), 0xAA.toByte(), 0x0F.toByte())
        val result = a xor a
        assertArrayEquals(ByteArray(3), result)
    }

    @Test
    fun `xor with zeros returns original`() {
        val a = byteArrayOf(0x55.toByte(), 0xAA.toByte(), 0x0F.toByte())
        val zeros = ByteArray(3)
        val result = a xor zeros
        assertArrayEquals(a, result)
    }

    @Test
    fun `xor throws on different lengths`() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(1, 2)
        assertThrows<IllegalArgumentException> { a xor b }
    }
}

class StringExtensionsTest {

    @Test
    fun `truncate returns full string if short enough`() {
        assertEquals("Hello", "Hello".truncate(10))
    }

    @Test
    fun `truncate shortens long string with ellipsis`() {
        assertEquals("He...", "Hello World".truncate(5))
    }

    @Test
    fun `truncate uses custom ellipsis`() {
        assertEquals("He~~", "Hello World".truncate(4, "~~"))
    }

    @Test
    fun `isBlankOrEmpty returns true for null`() {
        assertTrue(null.isBlankOrEmpty())
    }

    @Test
    fun `isBlankOrEmpty returns true for empty`() {
        assertTrue("".isBlankOrEmpty())
    }

    @Test
    fun `isBlankOrEmpty returns true for blank`() {
        assertTrue("   ".isBlankOrEmpty())
    }

    @Test
    fun `isBlankOrEmpty returns false for non-blank`() {
        assertTrue(!"abc".isBlankOrEmpty())
    }

    @Test
    fun `nullIfBlank returns null for null`() {
        assertEquals(null, null.nullIfBlank())
    }

    @Test
    fun `nullIfBlank returns null for blank`() {
        assertEquals(null, "   ".nullIfBlank())
    }

    @Test
    fun `nullIfBlank returns original string`() {
        assertEquals("abc", "abc".nullIfBlank())
    }

    @Test
    fun `decodeHex returns bytes`() {
        assertArrayEquals("Hello".toByteArray(), "48656C6C6F".decodeHex())
    }
}
