package org.enchant.core.base.stream

import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * An [OutputStream] wrapper whose [close] method flushes the underlying stream
 * but does not close it. The caller remains responsible for closing the
 * underlying stream when all writing is complete.
 *
 * This prevents data loss when a wrapper is closed before the caller has
 * finished writing — the flush ensures all buffered bytes reach the
 * underlying stream.
 */
class NonClosingOutputStream(wrapped: OutputStream) : FilterOutputStream(wrapped) {

    /**
     * Flushes the underlying stream without closing it.
     *
     * Unlike the default [FilterOutputStream.close] which does nothing when
     * overridden, this implementation explicitly calls [flush] to guarantee
     * that all buffered data is written to the underlying stream.
     */
    @Throws(IOException::class)
    override fun close() {
        flush()
    }

    /**
     * Closes the underlying stream. Only call this when all writing is complete.
     */
    @Throws(IOException::class)
    fun closeUnderlying() {
        super.close()
    }
}
