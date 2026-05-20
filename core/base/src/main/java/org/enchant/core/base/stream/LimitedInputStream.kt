package org.enchant.core.base.stream

import org.enchant.core.base.logging.Log
import java.io.FilterInputStream
import java.io.InputStream
import kotlin.math.min

/**
 * An [InputStream] wrapper that limits the total number of bytes that can be
 * read from the underlying stream. Once [maxBytes] have been consumed, all
 * subsequent reads return -1 (EOF).
 *
 * This is a security-critical class used to prevent OOM attacks from
 * untrusted streams that claim to be larger than they actually are.
 *
 * @param wrapped the underlying input stream
 * @param maxBytes the maximum number of bytes that may be read, or -1 for unlimited
 */
class LimitedInputStream(
    private val wrapped: InputStream,
    private val maxBytes: Long
) : FilterInputStream(wrapped) {

    private var totalBytesRead: Long = 0
    private var lastMark: Long = -1

    companion object {
        private const val UNLIMITED = -1L
        private val TAG = Log.tag(LimitedInputStream::class)

        /**
         * Creates a [LimitedInputStream] with no byte limit.
         * Equivalent to `LimitedInputStream(wrapped, -1)`.
         */
        fun withoutLimits(wrapped: InputStream): LimitedInputStream {
            return LimitedInputStream(wrapped = wrapped, maxBytes = UNLIMITED)
        }
    }

    @Throws(java.io.IOException::class)
    override fun read(): Int {
        if (maxBytes == UNLIMITED) return wrapped.read()
        if (totalBytesRead >= maxBytes) return -1
        val read = wrapped.read()
        if (read >= 0) totalBytesRead++
        return read
    }

    @Throws(java.io.IOException::class)
    override fun read(destination: ByteArray): Int {
        return read(destination, 0, destination.size)
    }

    @Throws(java.io.IOException::class)
    override fun read(destination: ByteArray, offset: Int, length: Int): Int {
        if (maxBytes == UNLIMITED) return wrapped.read(destination, offset, length)
        if (totalBytesRead >= maxBytes) return -1
        val bytesRemaining = maxBytes - totalBytesRead
        val bytesToRead = min(length, Math.toIntExact(bytesRemaining))
        val bytesRead = wrapped.read(destination, offset, bytesToRead)
        if (bytesRead > 0) totalBytesRead += bytesRead
        return bytesRead
    }

    @Throws(java.io.IOException::class)
    override fun skip(requestedSkipCount: Long): Long {
        if (maxBytes == UNLIMITED) return wrapped.skip(requestedSkipCount)
        val bytesRemaining = maxBytes - totalBytesRead
        val bytesToSkip = min(bytesRemaining, requestedSkipCount)
        val skipCount = super.skip(bytesToSkip)
        totalBytesRead += skipCount
        return skipCount
    }

    @Throws(java.io.IOException::class)
    override fun available(): Int {
        if (maxBytes == UNLIMITED) return wrapped.available()
        val bytesRemaining = minOf(maxBytes - totalBytesRead, Int.MAX_VALUE.toLong())
        return min(Math.toIntExact(bytesRemaining), wrapped.available())
    }

    override fun markSupported(): Boolean = wrapped.markSupported()

    @Throws(java.io.IOException::class)
    override fun mark(readlimit: Int) {
        if (!markSupported()) throw UnsupportedOperationException("Mark not supported")
        wrapped.mark(readlimit)
        if (maxBytes != UNLIMITED) lastMark = totalBytesRead
    }

    @Throws(java.io.IOException::class)
    override fun reset() {
        if (!markSupported()) throw UnsupportedOperationException("Mark not supported")
        if (lastMark == -1L) throw UnsupportedOperationException("Mark not set")
        wrapped.reset()
        if (maxBytes != UNLIMITED) totalBytesRead = lastMark
    }

    /**
     * Returns the remaining underlying stream after the limit has been reached.
     *
     * Warning: calling this before the limit is exhausted means the caller
     * bypasses the byte limit. Use with caution.
     */
    fun leftoverStream(): InputStream {
        if (maxBytes == UNLIMITED) return ByteArray(0).inputStream()
        if (totalBytesRead < maxBytes) {
            Log.w(TAG, "leftoverStream called before limit exhausted: $totalBytesRead/$maxBytes bytes read")
        }
        return wrapped
    }
}
