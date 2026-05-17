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
    fun setUp() {
        OfflineQueue.clear()
    }

    @AfterEach
    fun tearDown() {
        OfflineQueue.clear()
    }

    @Nested @DisplayName("Enqueue")
    inner class EnqueueTest {
        @Test @DisplayName("enqueue adds message to queue")
        fun `enqueue adds message`() {
            val msg = QueuedMessage(
                recipientUserId = "user1",
                recipientDeviceId = null,
                messageType = "SIGNAL_MESSAGE",
                payload = "hello".encodeToByteArray(),
                senderTs = System.currentTimeMillis()
            )
            OfflineQueue.enqueue(msg)
            assertEquals(1, OfflineQueue.size())
        }

        @Test @DisplayName("enqueue multiple messages increases size")
        fun `enqueue multiple`() {
            repeat(5) { i ->
                OfflineQueue.enqueue(QueuedMessage(
                    recipientUserId = "user$i",
                    recipientDeviceId = null,
                    messageType = "SIGNAL_MESSAGE",
                    payload = "msg$i".encodeToByteArray(),
                    senderTs = System.currentTimeMillis()
                ))
            }
            assertEquals(5, OfflineQueue.size())
        }

        @Test @DisplayName("enqueue with empty payload is allowed")
        fun `enqueue empty payload`() {
            OfflineQueue.enqueue(QueuedMessage(
                recipientUserId = "user1",
                recipientDeviceId = null,
                messageType = "TYPING_START",
                payload = ByteArray(0),
                senderTs = System.currentTimeMillis()
            ))
            assertEquals(1, OfflineQueue.size())
        }
    }

    @Nested @DisplayName("Dequeue")
    inner class DequeueTest {
        @Test @DisplayName("dequeue returns first message")
        fun `dequeue returns first`() {
            val msg1 = QueuedMessage("user1", null, "SIGNAL_MESSAGE", "first".encodeToByteArray(), 1000)
            val msg2 = QueuedMessage("user2", null, "SIGNAL_MESSAGE", "second".encodeToByteArray(), 2000)
            OfflineQueue.enqueue(msg1)
            OfflineQueue.enqueue(msg2)
            val dequeued = OfflineQueue.dequeue()
            assertNotNull(dequeued)
            assertEquals("user1", dequeued!!.recipientUserId)
        }

        @Test @DisplayName("dequeue removes message from queue")
        fun `dequeue removes`() {
            OfflineQueue.enqueue(QueuedMessage("user1", null, "SIGNAL_MESSAGE", "msg".encodeToByteArray(), 1000))
            OfflineQueue.dequeue()
            assertEquals(0, OfflineQueue.size())
        }

        @Test @DisplayName("dequeue from empty queue returns null")
        fun `dequeue empty returns null`() {
            val result = OfflineQueue.dequeue()
            assertNull(result)
        }

        @Test @DisplayName("dequeue preserves FIFO order")
        fun `dequeue fifo order`() {
            for (i in 1..10) {
                OfflineQueue.enqueue(QueuedMessage("user$i", null, "SIGNAL_MESSAGE", "msg$i".encodeToByteArray(), i.toLong()))
            }
            for (i in 1..10) {
                val msg = OfflineQueue.dequeue()
                assertNotNull(msg)
                assertEquals("user$i", msg!!.recipientUserId)
            }
        }
    }

    @Nested @DisplayName("Size & State")
    inner class StateTest {
        @Test @DisplayName("size returns 0 for empty queue")
        fun `size zero empty`() {
            assertEquals(0, OfflineQueue.size())
        }

        @Test @DisplayName("isEmpty returns true for empty queue")
        fun `isEmpty true`() {
            assertTrue(OfflineQueue.isEmpty())
        }

        @Test @DisplayName("isEmpty returns false after enqueue")
        fun `isEmpty false after enqueue`() {
            OfflineQueue.enqueue(QueuedMessage("user1", null, "SIGNAL_MESSAGE", "msg".encodeToByteArray(), 1000))
            assertFalse(OfflineQueue.isEmpty())
        }

        @Test @DisplayName("clear removes all messages")
        fun `clear removes all`() {
            repeat(5) { i ->
                OfflineQueue.enqueue(QueuedMessage("user$i", null, "SIGNAL_MESSAGE", "msg".encodeToByteArray(), 1000))
            }
            OfflineQueue.clear()
            assertEquals(0, OfflineQueue.size())
            assertTrue(OfflineQueue.isEmpty())
        }
    }

    @Nested @DisplayName("Get All")
    inner class GetAllTest {
        @Test @DisplayName("getAll returns all messages in order")
        fun `getAll returns all`() {
            for (i in 1..3) {
                OfflineQueue.enqueue(QueuedMessage("user$i", null, "SIGNAL_MESSAGE", "msg$i".encodeToByteArray(), i.toLong()))
            }
            val all = OfflineQueue.getAll()
            assertEquals(3, all.size)
            assertEquals("user1", all[0].recipientUserId)
            assertEquals("user2", all[1].recipientUserId)
            assertEquals("user3", all[2].recipientUserId)
        }

        @Test @DisplayName("getAll returns empty list for empty queue")
        fun `getAll empty`() {
            val all = OfflineQueue.getAll()
            assertTrue(all.isEmpty())
        }

        @Test @DisplayName("getAll does not remove messages")
        fun `getAll does not remove`() {
            OfflineQueue.enqueue(QueuedMessage("user1", null, "SIGNAL_MESSAGE", "msg".encodeToByteArray(), 1000))
            OfflineQueue.getAll()
            assertEquals(1, OfflineQueue.size())
        }
    }

    @Nested @DisplayName("Get By Recipient")
    inner class GetByRecipientTest {
        @Test @DisplayName("getByRecipient returns messages for specific user")
        fun `get by recipient`() {
            OfflineQueue.enqueue(QueuedMessage("user1", null, "SIGNAL_MESSAGE", "msg1".encodeToByteArray(), 1000))
            OfflineQueue.enqueue(QueuedMessage("user2", null, "SIGNAL_MESSAGE", "msg2".encodeToByteArray(), 2000))
            OfflineQueue.enqueue(QueuedMessage("user1", null, "SIGNAL_MESSAGE", "msg3".encodeToByteArray(), 3000))
            val msgs = OfflineQueue.getByRecipient("user1")
            assertEquals(2, msgs.size)
        }

        @Test @DisplayName("getByRecipient returns empty for unknown user")
        fun `get by recipient unknown`() {
            OfflineQueue.enqueue(QueuedMessage("user1", null, "SIGNAL_MESSAGE", "msg".encodeToByteArray(), 1000))
            val msgs = OfflineQueue.getByRecipient("unknown")
            assertTrue(msgs.isEmpty())
        }
    }

    @Nested @DisplayName("Remove By Recipient")
    inner class RemoveByRecipientTest {
        @Test @DisplayName("removeByRecipient removes all messages for user")
        fun `remove by recipient`() {
            OfflineQueue.enqueue(QueuedMessage("user1", null, "SIGNAL_MESSAGE", "msg1".encodeToByteArray(), 1000))
            OfflineQueue.enqueue(QueuedMessage("user2", null, "SIGNAL_MESSAGE", "msg2".encodeToByteArray(), 2000))
            OfflineQueue.enqueue(QueuedMessage("user1", null, "SIGNAL_MESSAGE", "msg3".encodeToByteArray(), 3000))
            OfflineQueue.removeByRecipient("user1")
            assertEquals(1, OfflineQueue.size())
        }

        @Test @DisplayName("removeByRecipient does nothing for unknown user")
        fun `remove by recipient unknown`() {
            OfflineQueue.enqueue(QueuedMessage("user1", null, "SIGNAL_MESSAGE", "msg".encodeToByteArray(), 1000))
            OfflineQueue.removeByRecipient("unknown")
            assertEquals(1, OfflineQueue.size())
        }
    }
}
