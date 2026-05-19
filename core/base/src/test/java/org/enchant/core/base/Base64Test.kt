package org.enchant.core.base

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException

class Base64Test {

    @Test
    fun `encodeWithPadding produces valid base64`() {
        assertEquals("SGVsbG8=", Base64.encodeWithPadding("Hello".toByteArray()))
    }

    @Test
    fun `encodeWithoutPadding strips padding`() {
        assertEquals("SGVsbG8", Base64.encodeWithoutPadding("Hello".toByteArray()))
    }

    @Test
    fun `encodeUrlSafeWithPadding replaces special chars`() {
        val bytes = byteArrayOf(-1, -1, -1) // produces //8=
        val encoded = Base64.encodeUrlSafeWithPadding(bytes)
        assertArrayEquals(bytes, Base64.decode(encoded))
    }

    @Test
    fun `encodeUrlSafeWithoutPadding has no padding`() {
        val encoded = Base64.encodeUrlSafeWithoutPadding("Hello".toByteArray())
        assertEquals("SGVsbG8", encoded)
    }

    @Test
    fun `decode standard base64`() {
        val decoded = Base64.decode("SGVsbG8=")
        assertArrayEquals("Hello".toByteArray(), decoded)
    }

    @Test
    fun `decode url-safe base64`() {
        val decoded = Base64.decode("SGVsbG8")
        assertArrayEquals("Hello".toByteArray(), decoded)
    }

    @Test
    fun `decodeOrNull returns null for invalid input`() {
        assertNull(Base64.decodeOrNull("!!!invalid!!!"))
    }

    @Test
    fun `decodeOrNull returns null for null input`() {
        assertNull(Base64.decodeOrNull(null))
    }

    @Test
    fun `decodeOrThrow decodes valid input`() {
        val decoded = Base64.decodeOrThrow("SGVsbG8=")
        assertArrayEquals("Hello".toByteArray(), decoded)
    }

    @Test
    fun `decodeOrThrow throws on invalid input`() {
        assertThrows(AssertionError::class.java) {
            Base64.decodeOrThrow("!!!")
        }
    }

    @Test
    fun `extension decodeBase64OrThrow`() {
        val decoded = "SGVsbG8=".decodeBase64OrThrow()
        assertArrayEquals("Hello".toByteArray(), decoded)
    }

    @Test
    fun `extension decodeBase64 returns null for invalid`() {
        assertNull("!!!".decodeBase64())
    }

    @Test
    fun `roundtrip encode then decode`() {
        val original = "The quick brown fox jumps over the lazy dog"
        val encoded = Base64.encodeWithPadding(original.toByteArray())
        val decoded = Base64.decode(encoded)
        assertEquals(original, String(decoded))
    }

    @Test
    fun `roundtrip url-safe encode then decode`() {
        val original = byteArrayOf(0, 1, 2, -1, -2, -3)
        val encoded = Base64.encodeUrlSafeWithoutPadding(original)
        val decoded = Base64.decode(encoded)
        assertArrayEquals(original, decoded)
    }

    @Test
    fun `decode throws IOException on garbage`() {
        assertThrows(IOException::class.java) {
            Base64.decode("!!!")
        }
    }
}

class HexTest {

    @Test
    fun `encode produces uppercase hex`() {
        assertEquals("48656C6C6F", Hex.encode("Hello".toByteArray()))
    }

    @Test
    fun `encodeLower produces lowercase hex`() {
        assertEquals("48656c6c6f", Hex.encodeLower("Hello".toByteArray()))
    }

    @Test
    fun `decode hex string`() {
        val decoded = Hex.decode("48656C6C6F")
        assertArrayEquals("Hello".toByteArray(), decoded)
    }

    @Test
    fun `decode lowercase hex`() {
        val decoded = Hex.decode("48656c6c6f")
        assertArrayEquals("Hello".toByteArray(), decoded)
    }

    @Test
    fun `roundtrip encode then decode`() {
        val original = "The quick brown fox"
        val encoded = Hex.encode(original.toByteArray())
        val decoded = Hex.decode(encoded)
        assertEquals(original, String(decoded))
    }

    @Test
    fun `empty bytes encode to empty string`() {
        assertEquals("", Hex.encode(byteArrayOf()))
    }

    @Test
    fun `decode empty string produces empty bytes`() {
        assertArrayEquals(byteArrayOf(), Hex.decode(""))
    }

    @Test
    fun `decode throws on odd length`() {
        assertThrows(IllegalArgumentException::class.java) {
            Hex.decode("486")
        }
    }

    @Test
    fun `encode zero bytes`() {
        assertEquals("0000", Hex.encode(byteArrayOf(0, 0)))
    }

    @Test
    fun `encode max byte values`() {
        assertEquals("FFFF", Hex.encode(byteArrayOf(-1, -1)))
    }
}
