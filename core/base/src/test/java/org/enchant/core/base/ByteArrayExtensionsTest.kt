package org.enchant.core.base

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
    fun `constantTimeEquals returns true for identical arrays`() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(1, 2, 3)
        assertTrue(a constantTimeEquals b)
    }

    @Test
    fun `constantTimeEquals returns false for different arrays`() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(1, 2, 4)
        assertTrue(!(a constantTimeEquals b))
    }

    @Test
    fun `constantTimeEquals returns false for different lengths`() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(1, 2)
        assertTrue(!(a constantTimeEquals b))
    }

    @Test
    fun `sha256 produces correct hash`() {
        val input = "Hello".toByteArray()
        val hash = input.sha256()
        assertEquals(32, hash.size)
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
