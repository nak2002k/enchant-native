package org.enchant.core.jobmanager

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EmptyQueueListenerTest {
    @Test
    fun `listener is called`() {
        var called = false
        val listener = EmptyQueueListener { called = true }
        listener.onQueueEmpty()
        assertTrue(called)
    }

    @Test
    fun `multiple listeners are all called`() {
        var called1 = false
        var called2 = false
        val listener1 = EmptyQueueListener { called1 = true }
        val listener2 = EmptyQueueListener { called2 = true }
        listener1.onQueueEmpty()
        listener2.onQueueEmpty()
        assertTrue(called1)
        assertTrue(called2)
    }
}
