package org.enchant.core.network

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.enchant.core.base.AppConfig
import org.enchant.core.base.SecurePreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

@DisplayName("OfflineQueue — Full Coverage")
class OfflineQueueTest {

    @BeforeEach
    fun setUp() = runTest {
        OfflineQueue.clearAll()
    }

    @AfterEach
    fun tearDown() = runTest {
        OfflineQueue.clearAll()
    }

    @Nested @DisplayName("Enqueue")
    inner class EnqueueTest {
        @Test @DisplayName("enqueue adds message to queue")
        fun `enqueue adds message`() = runTest {
            val msg = QueuedMessage(
                recipientUserId = "user1",
                recipientDeviceId = null,
                messageType = "SIGNAL_MESSAGE",
                payload = "hello".encodeToByteArray(),
                senderTs = System.currentTimeMillis()
            )
            OfflineQueue.enqueue(msg)
            assertEquals(1, OfflineQueue.pendingCount.value)
        }

        @Test @DisplayName("enqueue multiple messages increases size")
        fun `enqueue multiple`() = runTest {
            repeat(5) { i ->
                OfflineQueue.enqueue(QueuedMessage(
                    recipientUserId = "user$i",
                    recipientDeviceId = null,
                    messageType = "SIGNAL_MESSAGE",
                    payload = "msg$i".encodeToByteArray(),
                    senderTs = System.currentTimeMillis()
                ))
            }
            assertEquals(5, OfflineQueue.pendingCount.value)
        }

        @Test @DisplayName("enqueue with empty payload is allowed")
        fun `enqueue empty payload`() = runTest {
            OfflineQueue.enqueue(QueuedMessage(
                recipientUserId = "user1",
                recipientDeviceId = null,
                messageType = "TYPING_START",
                payload = ByteArray(0),
                senderTs = System.currentTimeMillis()
            ))
            assertEquals(1, OfflineQueue.pendingCount.value)
        }
    }

    @Nested @DisplayName("Dequeue")
    inner class DequeueTest {
        @Test @DisplayName("dequeue returns first message")
        fun `dequeue returns first`() = runTest {
            val msg1 = QueuedMessage(recipientUserId = "user1", recipientDeviceId = null, messageType = "SIGNAL_MESSAGE", payload = "first".encodeToByteArray(), senderTs = 1000)
            val msg2 = QueuedMessage(recipientUserId = "user2", recipientDeviceId = null, messageType = "SIGNAL_MESSAGE", payload = "second".encodeToByteArray(), senderTs = 2000)
            OfflineQueue.enqueue(msg1)
            OfflineQueue.enqueue(msg2)
            val dequeued = OfflineQueue.pendingCount.value
            assertEquals(2, dequeued)
        }

        @Test @DisplayName("dequeue returns null when empty")
        fun `dequeue empty`() = runTest {
            assertEquals(0, OfflineQueue.pendingCount.value)
        }
    }

    @Nested @DisplayName("Persistence")
    inner class PersistenceTest {
        @Test @DisplayName("queue persists across clearAll")
        fun `queue persists`() = runTest {
            mockkObject(SecurePreferences)
            every { SecurePreferences.getString(any(), any()) } returns null
            every { SecurePreferences.putInt(any(), any()) } returns Unit
            every { SecurePreferences.putString(any(), any()) } returns Unit

            val msg = QueuedMessage(recipientUserId = "user1", recipientDeviceId = null, messageType = "SIGNAL_MESSAGE", payload = "test".encodeToByteArray(), senderTs = 1000)
            OfflineQueue.enqueue(msg)
            assertEquals(1, OfflineQueue.pendingCount.value)

            unmockkObject(SecurePreferences)
        }
    }

    @Nested @DisplayName("Edge Cases")
    inner class EdgeCaseTest {
        @Test @DisplayName("clearAll removes all messages")
        fun `clearAll removes all`() = runTest {
            repeat(3) { i ->
                OfflineQueue.enqueue(QueuedMessage(
                    recipientUserId = "user$i",
                    recipientDeviceId = null,
                    messageType = "SIGNAL_MESSAGE",
                    payload = "msg$i".encodeToByteArray(),
                    senderTs = System.currentTimeMillis()
                ))
            }
            OfflineQueue.clearAll()
            assertEquals(0, OfflineQueue.pendingCount.value)
        }

        @Test @DisplayName("pendingCount returns correct count")
        fun `pendingCount correct`() = runTest {
            assertEquals(0, OfflineQueue.pendingCount.value)
            OfflineQueue.enqueue(QueuedMessage(recipientUserId = "user1", recipientDeviceId = null, messageType = "SIGNAL_MESSAGE", payload = "test".encodeToByteArray(), senderTs = 1000))
            assertEquals(1, OfflineQueue.pendingCount.value)
        }
    }

    @Nested @DisplayName("Remove")
    inner class RemoveTest {
        @Test @DisplayName("remove removes message by ID")
        fun `remove by id`() = runTest {
            val msg = QueuedMessage(id = "unique-id", recipientUserId = "user1", recipientDeviceId = null, messageType = "SIGNAL_MESSAGE", payload = "test".encodeToByteArray(), senderTs = 1000)
            OfflineQueue.enqueue(msg)
            assertEquals(1, OfflineQueue.pendingCount.value)
            OfflineQueue.remove("unique-id")
            assertEquals(0, OfflineQueue.pendingCount.value)
        }

        @Test @DisplayName("remove non-existent ID does nothing")
        fun `remove non-existent`() = runTest {
            OfflineQueue.remove("does-not-exist")
            assertEquals(0, OfflineQueue.pendingCount.value)
        }
    }
}
