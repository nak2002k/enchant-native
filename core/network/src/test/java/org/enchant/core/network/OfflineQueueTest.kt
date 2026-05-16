package org.enchant.core.network

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("OfflineQueue")
class OfflineQueueTest {
    @BeforeEach
    fun setUp() = runTest {
        OfflineQueue.clearAll()
    }

    @Test @DisplayName("enqueue adds message and updates count")
    fun `enqueue`() = runTest {
        val msg = QueuedMessage(recipientUserId = "user1", recipientDeviceId = null, messageType = "text", payload = ByteArray(10), senderTs = 1000L)
        OfflineQueue.enqueue(msg)
        assertEquals(1, OfflineQueue.pendingCount.value)
    }

    @Test @DisplayName("enqueue multiple messages")
    fun `enqueue multiple`() = runTest {
        for (i in 1..5) {
            OfflineQueue.enqueue(QueuedMessage(recipientUserId = "user$i", recipientDeviceId = null, messageType = "text", payload = ByteArray(10), senderTs = i.toLong()))
        }
        assertEquals(5, OfflineQueue.pendingCount.value)
    }

    @Test @DisplayName("clearAll removes all messages")
    fun `clear all`() = runTest {
        OfflineQueue.enqueue(QueuedMessage(recipientUserId = "user1", recipientDeviceId = null, messageType = "text", payload = ByteArray(10), senderTs = 1L))
        OfflineQueue.clearAll()
        assertEquals(0, OfflineQueue.pendingCount.value)
    }

    @Test @DisplayName("remove specific message")
    fun `remove message`() = runTest {
        val msg = QueuedMessage(id = "msg1", recipientUserId = "user1", recipientDeviceId = null, messageType = "text", payload = ByteArray(10), senderTs = 1L)
        OfflineQueue.enqueue(msg)
        OfflineQueue.remove("msg1")
        assertEquals(0, OfflineQueue.pendingCount.value)
    }

    @Test @DisplayName("remove non-existent message is no-op")
    fun `remove nonexistent`() = runTest {
        OfflineQueue.enqueue(QueuedMessage(recipientUserId = "user1", recipientDeviceId = null, messageType = "text", payload = ByteArray(10), senderTs = 1L))
        OfflineQueue.remove("nonexistent")
        assertEquals(1, OfflineQueue.pendingCount.value)
    }

    @Test @DisplayName("pending count starts at 0")
    fun `initial count`() {
        assertEquals(0, OfflineQueue.pendingCount.value)
    }

    @Test @DisplayName("drain with empty queue is no-op")
    fun `drain empty`() = runTest {
        OfflineQueue.drain()
        assertEquals(0, OfflineQueue.pendingCount.value)
    }
}
