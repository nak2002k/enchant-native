package org.enchant.core.base.stream

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class StreamUtilTest {

    @Test
    fun `readFully reads exact number of bytes`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val buffer = ByteArray(5)
        val read = StreamUtil.readFully(ByteArrayInputStream(data), buffer)
        assertEquals(5, read)
        assertArrayEquals(data, buffer)
    }

    @Test
    fun `readFully with offset and length`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val buffer = ByteArray(10)
        val read = StreamUtil.readFully(ByteArrayInputStream(data), buffer, 2, 3)
        assertEquals(3, read)
        assertEquals(0, buffer[0])
        assertEquals(0, buffer[1])
        assertEquals(1, buffer[2])
        assertEquals(2, buffer[3])
        assertEquals(3, buffer[4])
    }

    @Test
    fun `readFully throws on short stream`() {
        val data = byteArrayOf(1, 2)
        val buffer = ByteArray(5)
        assertThrows<IOException> {
            StreamUtil.readFully(ByteArrayInputStream(data), buffer)
        }
    }

    @Test
    fun `readFully with maxBytes reads up to limit`() {
        val data = ByteArray(100) { it.toByte() }
        val result = StreamUtil.readFully(ByteArrayInputStream(data), 10)
        assertEquals(10, result.size)
        assertArrayEquals(data.copyOf(10), result)
    }

    @Test
    fun `readFully with maxBytes returns fewer bytes if stream ends early`() {
        val data = byteArrayOf(1, 2, 3)
        val result = StreamUtil.readFully(ByteArrayInputStream(data), 10)
        assertEquals(3, result.size)
        assertArrayEquals(data, result)
    }

    @Test
    fun `copy transfers all bytes`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val input = ByteArrayInputStream(data)
        val output = ByteArrayOutputStream()
        val copied = StreamUtil.copy(input, output)
        assertEquals(5L, copied)
        assertArrayEquals(data, output.toByteArray())
    }

    @Test
    fun `copy with maxBytes limits transfer`() {
        val data = ByteArray(100) { it.toByte() }
        val input = ByteArrayInputStream(data)
        val output = ByteArrayOutputStream()
        val copied = StreamUtil.copy(input, output, 10)
        assertEquals(10L, copied)
        assertEquals(10, output.size())
    }

    @Test
    fun `getStreamLength measures stream size`() {
        val data = ByteArray(256) { it.toByte() }
        val length = StreamUtil.getStreamLength(ByteArrayInputStream(data))
        assertEquals(256L, length)
    }

    @Test
    fun `getStreamLength returns 0 for empty stream`() {
        val length = StreamUtil.getStreamLength(ByteArrayInputStream(ByteArray(0)))
        assertEquals(0L, length)
    }

    @Test
    fun `close suppresses IOException`() {
        val failingStream = object : java.io.InputStream() {
            override fun read(): Int = -1
            override fun close() { throw IOException("close failed") }
        }
        StreamUtil.close(failingStream)
    }

    @Test
    fun `close handles null gracefully`() {
        StreamUtil.close(null)
    }
}
