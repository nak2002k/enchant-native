package org.enchant.core.base.stream

import java.io.FilterInputStream
import java.io.InputStream
import kotlin.math.min

class LimitedInputStream(
    private val wrapped: InputStream,
    private val maxBytes: Long
) : FilterInputStream(wrapped) {

    private var totalBytesRead: Long = 0
    private var lastMark: Long = -1

    companion object {
        private const val UNLIMITED = -1L

        fun withoutLimits(wrapped: InputStream): LimitedInputStream {
            return LimitedInputStream(wrapped = wrapped, maxBytes = UNLIMITED)
        }
    }

    override fun read(): Int {
        if (maxBytes == UNLIMITED) return wrapped.read()
        if (totalBytesRead >= maxBytes) return -1
        val read = wrapped.read()
        if (read >= 0) totalBytesRead++
        return read
    }

    override fun read(destination: ByteArray): Int {
        return read(destination, 0, destination.size)
    }

    override fun read(destination: ByteArray, offset: Int, length: Int): Int {
        if (maxBytes == UNLIMITED) return wrapped.read(destination, offset, length)
        if (totalBytesRead >= maxBytes) return -1
        val bytesRemaining = maxBytes - totalBytesRead
        val bytesToRead = min(length, bytesRemaining.toInt())
        val bytesRead = wrapped.read(destination, offset, bytesToRead)
        if (bytesRead > 0) totalBytesRead += bytesRead
        return bytesRead
    }

    override fun skip(requestedSkipCount: Long): Long {
        if (maxBytes == UNLIMITED) return wrapped.skip(requestedSkipCount)
        val bytesRemaining = maxBytes - totalBytesRead
        val bytesToSkip = min(bytesRemaining, requestedSkipCount)
        val skipCount = super.skip(bytesToSkip)
        totalBytesRead += skipCount
        return skipCount
    }

    override fun available(): Int {
        if (maxBytes == UNLIMITED) return wrapped.available()
        val bytesRemaining = minOf(maxBytes - totalBytesRead, Int.MAX_VALUE.toLong()).toInt()
        return min(bytesRemaining, wrapped.available())
    }

    override fun markSupported(): Boolean = wrapped.markSupported()

    override fun mark(readlimit: Int) {
        if (!markSupported()) throw UnsupportedOperationException("Mark not supported")
        wrapped.mark(readlimit)
        if (maxBytes != UNLIMITED) lastMark = totalBytesRead
    }

    override fun reset() {
        if (!markSupported()) throw UnsupportedOperationException("Mark not supported")
        if (lastMark == -1L) throw UnsupportedOperationException("Mark not set")
        wrapped.reset()
        if (maxBytes != UNLIMITED) totalBytesRead = lastMark
    }

    fun leftoverStream(): InputStream {
        if (maxBytes == UNLIMITED) return ByteArray(0).inputStream()
        return wrapped
    }
}
