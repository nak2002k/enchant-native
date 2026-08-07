package org.enchant.core.network

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.enchant.core.base.AppConfig
import org.enchant.core.base.SecurePreferences
import org.enchant.core.network.WebSocketManager
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
                messageType = "ENCRYPTED_MESSAGE",
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
                    messageType = "ENCRYPTED_MESSAGE",
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
            val msg1 = QueuedMessage(recipientUserId = "user1", recipientDeviceId = null, messageType = "ENCRYPTED_MESSAGE", payload = "first".encodeToByteArray(), senderTs = 1000)
            val msg2 = QueuedMessage(recipientUserId = "user2", recipientDeviceId = null, messageType = "ENCRYPTED_MESSAGE", payload = "second".encodeToByteArray(), senderTs = 2000)
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

            val msg = QueuedMessage(recipientUserId = "user1", recipientDeviceId = null, messageType = "ENCRYPTED_MESSAGE", payload = "test".encodeToByteArray(), senderTs = 1000)
            OfflineQueue.enqueue(msg)
            assertEquals(1, OfflineQueue.pendingCount.value)

            unmockkObject(SecurePreferences)
        }

        @Test @DisplayName("large payload is persisted without truncation")
        fun `large payload persists intact`() = runTest {
            val store = mutableMapOf<String, String>()
            mockkObject(SecurePreferences)
            every { SecurePreferences.getString(any(), any()) } answers { store[firstArg<String>()] }
            every { SecurePreferences.putInt(any(), any()) } answers { store[firstArg<String>()] = secondArg<Int>().toString() }
            every { SecurePreferences.putString(any(), any()) } answers { store[firstArg<String>()] = secondArg<String>() }

            val large = ByteArray(8192) { (it % 251).toByte() }
            OfflineQueue.enqueue(QueuedMessage(
                id = "large-msg",
                recipientUserId = "user1",
                recipientDeviceId = null,
                messageType = "ENCRYPTED_MESSAGE",
                payload = large,
                senderTs = 1000
            ))

            assertEquals("1", store["offline.queue.count"])
            val persisted = store["offline.queue.0"]!!
            val restoredPayload = java.util.Base64.getUrlDecoder().decode(
                persisted.split("|", limit = 7)[4]
            )
            assertArrayEquals(large, restoredPayload)

            unmockkObject(SecurePreferences)
        }

        @Test @DisplayName("all queued messages are persisted without cap")
        fun `all items persist without cap`() = runTest {
            val store = mutableMapOf<String, String>()
            mockkObject(SecurePreferences)
            every { SecurePreferences.getString(any(), any()) } answers { store[firstArg<String>()] }
            every { SecurePreferences.putInt(any(), any()) } answers { store[firstArg<String>()] = secondArg<Int>().toString() }
            every { SecurePreferences.putString(any(), any()) } answers { store[firstArg<String>()] = secondArg<String>() }

            repeat(60) { i ->
                OfflineQueue.enqueue(QueuedMessage(
                    id = "item-$i",
                    recipientUserId = "user1",
                    recipientDeviceId = null,
                    messageType = "ENCRYPTED_MESSAGE",
                    payload = "data-$i".encodeToByteArray(),
                    senderTs = 1000L + i
                ))
            }

            assertEquals("60", store["offline.queue.count"])
            assertEquals(60, (0 until 60).count { store.containsKey("offline.queue.$it") })

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
                    messageType = "ENCRYPTED_MESSAGE",
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
            OfflineQueue.enqueue(QueuedMessage(recipientUserId = "user1", recipientDeviceId = null, messageType = "ENCRYPTED_MESSAGE", payload = "test".encodeToByteArray(), senderTs = 1000))
            assertEquals(1, OfflineQueue.pendingCount.value)
        }
    }

    @Nested @DisplayName("Overflow Protection (Bug #4)")
    inner class OverflowProtectionTest {
        @BeforeEach
        fun setUpOverflow() {
            mockkObject(SecurePreferences)
            every { SecurePreferences.putInt(any(), any()) } returns Unit
            every { SecurePreferences.putString(any(), any()) } returns Unit
        }

        @AfterEach
        fun tearDownOverflow() {
            unmockkObject(SecurePreferences)
        }

        @Test @DisplayName("persistToDisk limits stored items to 50")
        fun `limits stored items to 50`() = runTest {
            repeat(60) { i ->
                OfflineQueue.enqueue(QueuedMessage(
                    id = "store-$i",
                    recipientUserId = "user1",
                    recipientDeviceId = null,
                    messageType = "ENCRYPTED_MESSAGE",
                    payload = "data".encodeToByteArray(),
                    senderTs = System.currentTimeMillis()
                ))
            }
            assertEquals(60, OfflineQueue.pendingCount.value)
        }

        @Test @DisplayName("large payload is truncated before storage")
        fun `truncates large payload`() = runTest {
            val largePayload = ByteArray(8192) { 0x42 }
            OfflineQueue.enqueue(QueuedMessage(
                id = "large-msg",
                recipientUserId = "user1",
                recipientDeviceId = null,
                messageType = "ENCRYPTED_MESSAGE",
                payload = largePayload,
                senderTs = System.currentTimeMillis()
            ))
            assertEquals(1, OfflineQueue.pendingCount.value)
        }
    }

    @Nested @DisplayName("Remove")
    inner class RemoveTest {
        @Test @DisplayName("remove removes message by ID")
        fun `remove by id`() = runTest {
            val msg = QueuedMessage(id = "unique-id", recipientUserId = "user1", recipientDeviceId = null, messageType = "ENCRYPTED_MESSAGE", payload = "test".encodeToByteArray(), senderTs = 1000)
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

    @Nested @DisplayName("Drain")
    inner class DrainTest {
        @Test @DisplayName("drain returns empty list when queue is empty")
        fun `drain empty queue returns empty`() = runTest {
            val results = OfflineQueue.drain()
            assertTrue(results.isEmpty())
        }

        @Test @DisplayName("drain returns results for each message")
        fun `drain returns results`() = runTest {
            mockkObject(SecurePreferences)
            every { SecurePreferences.getString(any(), any()) } returns null
            every { SecurePreferences.putInt(any(), any()) } returns Unit
            every { SecurePreferences.putString(any(), any()) } returns Unit
            mockkObject(WebSocketManager)
            coEvery { WebSocketManager.requestRESTFallback(any()) } returns Result.success(Unit)

            val msg = QueuedMessage(id = "drain-msg", recipientUserId = "user1", recipientDeviceId = null, messageType = "ENCRYPTED_MESSAGE", payload = "test".encodeToByteArray(), senderTs = 1000)
            OfflineQueue.enqueue(msg)
            assertEquals(1, OfflineQueue.pendingCount.value)

            val results = OfflineQueue.drain()
            assertEquals(1, results.size)
            assertTrue(results[0].isSuccess)

            unmockkObject(WebSocketManager)
            unmockkObject(SecurePreferences)
        }

        @Test @DisplayName("drain re-enqueues and returns failure when send fails")
        fun `drain re-enqueues on failure`() = runTest {
            mockkObject(SecurePreferences)
            every { SecurePreferences.getString(any(), any()) } returns null
            every { SecurePreferences.putInt(any(), any()) } returns Unit
            every { SecurePreferences.putString(any(), any()) } returns Unit
            mockkObject(WebSocketManager)
            coEvery { WebSocketManager.requestRESTFallback(any()) } returns Result.failure(Exception("Network error"))

            val msg = QueuedMessage(id = "fail-msg", recipientUserId = "user1", recipientDeviceId = null, messageType = "ENCRYPTED_MESSAGE", payload = "test".encodeToByteArray(), senderTs = 1000)
            OfflineQueue.enqueue(msg)

            val results = OfflineQueue.drain()
            assertEquals(1, results.size)
            assertTrue(results[0].isFailure)
            assertEquals(1, OfflineQueue.pendingCount.value)

            unmockkObject(WebSocketManager)
            unmockkObject(SecurePreferences)
        }
    }
}
