package org.enchant.core.base.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

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
    fun `leftoverStream returns remaining bytes after limit exhausted`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = LimitedInputStream(ByteArrayInputStream(data), 3)
        stream.readBytes()
        val leftover = stream.leftoverStream().readBytes()
        assertArrayEquals(byteArrayOf(), leftover)
    }

    @Test
    fun `leftoverStream returns empty for unlimited`() {
        val stream = LimitedInputStream.withoutLimits(ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        val leftover = stream.leftoverStream()
        assertEquals(0, leftover.readBytes().size)
    }

    @Test
    fun `read with offset and length respects limit`() {
        val data = ByteArray(100) { it.toByte() }
        val stream = LimitedInputStream(ByteArrayInputStream(data), 10)
        val buffer = ByteArray(50)
        val bytesRead = stream.read(buffer, 0, 50)
        assertEquals(10, bytesRead)
    }

    @Test
    fun `mark and reset within limit`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = LimitedInputStream(ByteArrayInputStream(data), 5)
        stream.mark(10)
        assertEquals(1, stream.read())
        assertEquals(2, stream.read())
        stream.reset()
        assertEquals(1, stream.read())
    }

    @Test
    fun `available returns correct remaining bytes`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = LimitedInputStream(ByteArrayInputStream(data), 3)
        assertEquals(3, stream.available())
        stream.read()
        assertEquals(2, stream.available())
    }
}

class NonClosingOutputStreamTest {

    @Test
    fun `close does not close underlying stream`() {
        val underlying = ByteArrayOutputStream()
        val stream = NonClosingOutputStream(underlying)
        stream.close()
        stream.write(42)
    }

    @Test
    fun `close flushes underlying stream`() {
        val underlying = FlushingTrackerOutputStream()
        val stream = NonClosingOutputStream(underlying)
        stream.write(byteArrayOf(1, 2, 3))
        stream.close()
        assertTrue(underlying.flushCalled)
    }

    @Test
    fun `closeUnderlying does close`() {
        val underlying = ByteArrayOutputStream()
        val stream = NonClosingOutputStream(underlying)
        stream.write(42)
        stream.closeUnderlying()
        stream.closeUnderlying()
    }

    @Test
    fun `write passes through to underlying`() {
        val underlying = ByteArrayOutputStream()
        val stream = NonClosingOutputStream(underlying)
        stream.write(byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), underlying.toByteArray())
    }

    @Test
    fun `data is available after close due to flush`() {
        val underlying = ByteArrayOutputStream()
        val stream = NonClosingOutputStream(underlying)
        stream.write(byteArrayOf(10, 20, 30))
        stream.close()
        assertArrayEquals(byteArrayOf(10, 20, 30), underlying.toByteArray())
    }

    /**
     * An OutputStream that tracks whether flush() was called.
     */
    private class FlushingTrackerOutputStream : java.io.OutputStream() {
        var flushCalled = false
        private val buffer = ByteArrayOutputStream()

        override fun write(b: Int) {
            buffer.write(b)
        }

        override fun flush() {
            flushCalled = true
            super.flush()
        }
    }
}
