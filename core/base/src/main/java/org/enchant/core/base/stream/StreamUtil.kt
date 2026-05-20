package org.enchant.core.base.stream

import org.enchant.core.base.logging.Log
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Stream utility methods for safe and efficient I/O operations.
 *
 * Provides security-critical functions like bounded reads to prevent OOM
 * attacks from untrusted streams, null-safe close operations, and
 * efficient stream copying.
 */
object StreamUtil {

    private val TAG = Log.tag(StreamUtil::class)

    /**
     * Reads exactly [length] bytes from [input] into [buffer] starting at [offset].
     *
     * Throws [IOException] if the stream ends before [length] bytes are read.
     * This guarantees a full read, unlike [InputStream.read] which may return
     * fewer bytes even when more are available.
     *
     * @return the total number of bytes read (always equals [length] on success)
     */
    @Throws(IOException::class)
    fun readFully(input: InputStream, buffer: ByteArray, offset: Int, length: Int): Int {
        var totalRead = 0
        while (totalRead < length) {
            val read = input.read(buffer, offset + totalRead, length - totalRead)
            if (read == -1) {
                throw IOException("Unexpected end of stream: expected $length bytes, got $totalRead")
            }
            totalRead += read
        }
        return totalRead
    }

    /**
     * Reads exactly [buffer.size] bytes from [input] into [buffer].
     *
     * @see readFully
     */
    @Throws(IOException::class)
    fun readFully(input: InputStream, buffer: ByteArray): Int {
        return readFully(input, buffer, 0, buffer.size)
    }

    /**
     * Reads up to [maxBytes] bytes from [input] and returns them as a new byte array.
     *
     * This is a security-critical method that prevents OOM attacks from untrusted
     * streams that claim to be very large. The caller specifies the maximum number
     * of bytes to read, and any data beyond that limit is ignored.
     *
     * @param input the input stream to read from
     * @param maxBytes the maximum number of bytes to read
     * @return the bytes read (may be fewer than [maxBytes] if the stream ends early)
     */
    @Throws(IOException::class)
    fun readFully(input: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes)
        var totalRead = 0
        while (totalRead < maxBytes) {
            val read = input.read(buffer, totalRead, maxBytes - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return if (totalRead == maxBytes) buffer else buffer.copyOf(totalRead)
    }

    /**
     * Copies all bytes from [input] to [output].
     *
     * @return the total number of bytes copied
     */
    @Throws(IOException::class)
    fun copy(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(8192)
        var total: Long = 0
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
            total += read
        }
        output.flush()
        return total
    }

    /**
     * Copies up to [maxBytes] bytes from [input] to [output].
     *
     * @return the total number of bytes copied
     */
    @Throws(IOException::class)
    fun copy(input: InputStream, output: OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(8192)
        var total: Long = 0
        while (total < maxBytes) {
            val toRead = Math.min(buffer.size.toLong(), maxBytes - total).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read == -1) break
            output.write(buffer, 0, read)
            total += read
        }
        output.flush()
        return total
    }

    /**
     * Consumes the entire [input] stream and returns the total number of bytes.
     *
     * The data is discarded. Useful for measuring stream length or draining
     * a stream that will not be read again.
     *
     * @return the total number of bytes consumed
     */
    @Throws(IOException::class)
    fun getStreamLength(input: InputStream): Long {
        val buffer = ByteArray(8192)
        var total: Long = 0
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            total += read
        }
        return total
    }

    /**
     * Closes a [Closeable] resource, suppressing any IOException and logging a warning.
     *
     * Use this in finally blocks where the close failure should not mask the
     * primary exception.
     */
    fun close(closeable: Closeable?) {
        if (closeable == null) return
        try {
            closeable.close()
        } catch (e: IOException) {
            Log.w(TAG, "Failed to close resource: ${e.message}")
        }
    }
}
