package org.enchant.core.base.stream

import java.io.FilterOutputStream
import java.io.OutputStream

class NonClosingOutputStream(wrapped: OutputStream) : FilterOutputStream(wrapped) {

    override fun close() {
        // Intentionally no-op. The caller is responsible for closing the underlying stream.
    }

    fun closeUnderlying() {
        super.close()
    }
}
