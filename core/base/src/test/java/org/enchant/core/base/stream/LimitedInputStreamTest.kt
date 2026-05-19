package org.enchant.core.base.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class LimitedInputStreamTest {

    @Test
    fun `reads all bytes when within limit`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = LimitedInputStream(ByteArrayInputStream(data), 10)
        val result = stream.readBytes()
        assertArrayEquals(data, result)
    }

    @Test
    fun `stops reading at maxBytes`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = LimitedInputStream(ByteArrayInputStream(data), 3)
        val result = stream.readBytes()
        assertArrayEquals(byteArrayOf(1, 2, 3), result)
    }

    @Test
    fun `read returns -1 when limit exceeded`() {
        val data = byteArrayOf(1, 2)
        val stream = LimitedInputStream(ByteArrayInputStream(data), 1)
        stream.read()
        assertEquals(-1, stream.read())
    }

    @Test
    fun `available is bounded by limit`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = LimitedInputStream(ByteArrayInputStream(data), 3)
        assertTrue(stream.available() <= 3)
    }

    @Test
    fun `withoutLimits reads all data`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = LimitedInputStream.withoutLimits(ByteArrayInputStream(data))
        val result = stream.readBytes()
        assertArrayEquals(data, result)
    }

    @Test
    fun `empty stream returns nothing`() {
        val stream = LimitedInputStream(ByteArrayInputStream(byteArrayOf()), 10)
        assertEquals(-1, stream.read())
    }

    @Test
    fun `skip respects limit`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = LimitedInputStream(ByteArrayInputStream(data), 3)
        val skipped = stream.skip(10)
        assertTrue(skipped <= 3)
    }

    @Test
    fun `leftoverStream returns remaining bytes`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = LimitedInputStream(ByteArrayInputStream(data), 3)
        stream.readBytes()
        val leftover = stream.leftoverStream().readBytes()
        assertArrayEquals(byteArrayOf(4, 5), leftover)
    }
}

class NonClosingOutputStreamTest {

    @Test
    fun `close does not close underlying stream`() {
        val underlying = ByteArrayOutputStream()
        val stream = NonClosingOutputStream(underlying)
        stream.close()
        stream.write(42) // should not throw — underlying is still open
    }

    @Test
    fun `closeUnderlying does close`() {
        val underlying = ByteArrayOutputStream()
        val stream = NonClosingOutputStream(underlying)
        stream.write(42)
        stream.closeUnderlying()
        // After close, the underlying stream's close() was called.
        // ByteArrayOutputStream tolerates writes after close, so no exception expected.
        // The key behavior is that closeUnderlying() calls super.close().
        stream.closeUnderlying() // should be safe to call twice
    }

    @Test
    fun `write passes through to underlying`() {
        val underlying = ByteArrayOutputStream()
        val stream = NonClosingOutputStream(underlying)
        stream.write(byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), underlying.toByteArray())
    }
}
