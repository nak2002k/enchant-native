package org.enchant.core.crypto

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SenderKeyManager — Group messaging keys")
class SenderKeyManagerTest {

    private val groupId = "group1"
    private val senderId = "alice"
    private val receiverId = "bob"

    @BeforeEach
    fun setUp() = runTest {
        SenderKeyManager.clearAll()
    }

    @Nested @DisplayName("Sender Key Creation")
    inner class SenderKeyCreationTest {
        @Test @DisplayName("getOrCreateSenderKey creates new key")
        fun `create sender key`() = runTest {
            val state = SenderKeyManager.getOrCreateSenderKey(groupId, senderId)
            assertEquals(32, state.seed.size)
            assertEquals(32, state.chainKey.size)
            assertEquals(0, state.iteration)
        }

        @Test @DisplayName("getOrCreateSenderKey returns same key on second call")
        fun `same key on second call`() = runTest {
            val state1 = SenderKeyManager.getOrCreateSenderKey(groupId, senderId)
            val state2 = SenderKeyManager.getOrCreateSenderKey(groupId, senderId)
            assertArrayEquals(state1.chainKey, state2.chainKey)
        }

        @Test @DisplayName("different groups have different keys")
        fun `different groups different keys`() = runTest {
            val state1 = SenderKeyManager.getOrCreateSenderKey("group1", senderId)
            val state2 = SenderKeyManager.getOrCreateSenderKey("group2", senderId)
            assertFalse(state1.chainKey.contentEquals(state2.chainKey))
        }

        @Test @DisplayName("different senders have different keys")
        fun `different senders different keys`() = runTest {
            val state1 = SenderKeyManager.getOrCreateSenderKey(groupId, "alice")
            val state2 = SenderKeyManager.getOrCreateSenderKey(groupId, "bob")
            assertFalse(state1.chainKey.contentEquals(state2.chainKey))
        }
    }

    @Nested @DisplayName("Distribution Messages")
    inner class DistributionTest {
        @Test @DisplayName("createDistributionMessage returns valid message")
        fun `create distribution`() = runTest {
            SenderKeyManager.getOrCreateSenderKey(groupId, senderId)
            val dm = SenderKeyManager.createDistributionMessage(groupId, senderId)
            assertNotNull(dm)
            assertEquals(groupId, dm!!.groupId)
            assertEquals(senderId, dm.senderUserId)
            assertEquals(32, dm.chainKey.size)
            assertEquals(0, dm.iteration)
        }

        @Test @DisplayName("createDistributionMessage returns null for non-existent key")
        fun `create distribution null`() = runTest {
            assertNull(SenderKeyManager.createDistributionMessage(groupId, senderId))
        }

        @Test @DisplayName("handleDistributionMessage stores receiver key")
        fun `handle distribution`() = runTest {
            SenderKeyManager.getOrCreateSenderKey(groupId, senderId)
            val dm = SenderKeyManager.createDistributionMessage(groupId, senderId)!!
            SenderKeyManager.handleDistributionMessage(dm)

            // Encrypt and decrypt should work
            val plaintext = "group message".encodeToByteArray()
            val encrypted = SenderKeyManager.encryptGroupMessage(groupId, senderId, plaintext)
            assertNotNull(encrypted)
            val decrypted = SenderKeyManager.decryptGroupMessage(groupId, senderId, encrypted!!, dm.iteration + 1)
            assertArrayEquals(plaintext, decrypted)
        }
    }

    @Nested @DisplayName("Group Message Encryption/Decryption")
    inner class GroupMessageTest {
        @Test @DisplayName("encrypt then decrypt roundtrip")
        fun `encrypt decrypt roundtrip`() = runTest {
            SenderKeyManager.getOrCreateSenderKey(groupId, senderId)
            val dm = SenderKeyManager.createDistributionMessage(groupId, senderId)!!
            SenderKeyManager.handleDistributionMessage(dm)

            val plaintext = "Hello group!".encodeToByteArray()
            val encrypted = SenderKeyManager.encryptGroupMessage(groupId, senderId, plaintext)
            assertNotNull(encrypted)

            val decrypted = SenderKeyManager.decryptGroupMessage(groupId, senderId, encrypted!!, dm.iteration + 1)
            assertArrayEquals(plaintext, decrypted)
        }

        @Test @DisplayName("multiple messages advance iteration")
        fun `multiple messages advance iteration`() = runTest {
            SenderKeyManager.getOrCreateSenderKey(groupId, senderId)
            val dm = SenderKeyManager.createDistributionMessage(groupId, senderId)!!
            SenderKeyManager.handleDistributionMessage(dm)

            repeat(5) { i ->
                val plaintext = "Message $i".encodeToByteArray()
                val encrypted = SenderKeyManager.encryptGroupMessage(groupId, senderId, plaintext)
                val decrypted = SenderKeyManager.decryptGroupMessage(groupId, senderId, encrypted!!, dm.iteration + i + 1)
                assertArrayEquals(plaintext, decrypted)
            }
        }

        @Test @DisplayName("returns null for non-existent sender key")
        fun `encrypt null no key`() = runTest {
            assertNull(SenderKeyManager.encryptGroupMessage(groupId, senderId, "test".encodeToByteArray()))
        }

        @Test @DisplayName("returns null for non-existent receiver key")
        fun `decrypt null no key`() = runTest {
            assertNull(SenderKeyManager.decryptGroupMessage(groupId, senderId, ByteArray(50), 1))
        }

        @Test @DisplayName("returns null for replayed message (iteration <= current)")
        fun `replay returns null`() = runTest {
            SenderKeyManager.getOrCreateSenderKey(groupId, senderId)
            val dm = SenderKeyManager.createDistributionMessage(groupId, senderId)!!
            SenderKeyManager.handleDistributionMessage(dm)

            val plaintext = "msg".encodeToByteArray()
            val encrypted = SenderKeyManager.encryptGroupMessage(groupId, senderId, plaintext)!!
            SenderKeyManager.decryptGroupMessage(groupId, senderId, encrypted, dm.iteration + 1)

            // Replay with same iteration
            assertNull(SenderKeyManager.decryptGroupMessage(groupId, senderId, encrypted, dm.iteration + 1))
        }

        @Test @DisplayName("returns null for too-short payload")
        fun `short payload null`() = runTest {
            SenderKeyManager.getOrCreateSenderKey(groupId, senderId)
            val dm = SenderKeyManager.createDistributionMessage(groupId, senderId)!!
            SenderKeyManager.handleDistributionMessage(dm)
            assertNull(SenderKeyManager.decryptGroupMessage(groupId, senderId, ByteArray(5), dm.iteration + 1))
        }
    }

    @Nested @DisplayName("Group Key Deletion")
    inner class DeleteTest {
        @Test @DisplayName("deleteGroupKeys removes all keys for group")
        fun `delete group keys`() = runTest {
            SenderKeyManager.getOrCreateSenderKey(groupId, senderId)
            val dm = SenderKeyManager.createDistributionMessage(groupId, senderId)!!
            SenderKeyManager.handleDistributionMessage(dm)

            SenderKeyManager.deleteGroupKeys(groupId)

            assertNull(SenderKeyManager.encryptGroupMessage(groupId, senderId, "test".encodeToByteArray()))
        }

        @Test @DisplayName("deleteGroupKeys doesn't affect other groups")
        fun `delete doesn t affect others`() = runTest {
            SenderKeyManager.getOrCreateSenderKey("group1", senderId)
            SenderKeyManager.getOrCreateSenderKey("group2", senderId)

            SenderKeyManager.deleteGroupKeys("group1")

            assertNotNull(SenderKeyManager.getOrCreateSenderKey("group2", senderId))
        }
    }
}
