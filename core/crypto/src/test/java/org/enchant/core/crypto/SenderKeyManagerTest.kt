package org.enchant.core.crypto

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("SenderKey Manager")
class SenderKeyManagerTest {

    @BeforeEach
    fun cleanup() = runTest {
        SenderKeyManager.deleteGroupKeys("test-group")
        SenderKeyManager.deleteGroupKeys("test-group-other")
        SenderKeyManager.deleteGroupKeys("test-group-deletion")
        SenderKeyManager.deleteGroupKeys("test-group-multi")
        SenderKeyManager.deleteGroupKeys("test-group-empty")
        SenderKeyManager.deleteGroupKeys("test-group-wrong-key")
        SenderKeyManager.deleteGroupKeys("test-group-large")
    }

    @Test
    @DisplayName("getOrCreateSenderKey creates a valid SenderKeyState with non-empty chainKey")
    fun `create sender key`() = runTest {
        val state = SenderKeyManager.getOrCreateSenderKey("test-group", "alice")

        assertNotNull(state)
        assertEquals(32, state.seed.size)
        assertEquals(32, state.chainKey.size)
        assertEquals(0, state.messageNumber)
        assertEquals(1, state.version)
        assertFalse(state.chainKey.all { it == 0.toByte() }, "chainKey should not be all zeros")
    }

    @Test
    @DisplayName("getOrCreateSenderKey returns the same state when called twice")
    fun `create sender key is idempotent`() = runTest {
        val first = SenderKeyManager.getOrCreateSenderKey("test-group", "alice")
        val second = SenderKeyManager.getOrCreateSenderKey("test-group", "alice")

        assertTrue(first.seed.contentEquals(second.seed))
        assertTrue(first.chainKey.contentEquals(second.chainKey))
        assertEquals(first.messageNumber, second.messageNumber)
    }

    @Test
    @DisplayName("createDistributionMessage returns a non-null distribution message for existing sender key")
    fun `create distribution message`() = runTest {
        SenderKeyManager.getOrCreateSenderKey("test-group", "alice")
        val dm = SenderKeyManager.createDistributionMessage("test-group", "alice")

        assertNotNull(dm)
        assertEquals("test-group", dm!!.groupId)
        assertEquals("alice", dm.senderUserId)
        assertEquals(32, dm.chainKey.size)
        assertEquals(0, dm.messageNumber)
    }

    @Test
    @DisplayName("createDistributionMessage returns null if no sender key exists")
    fun `create distribution message without key returns null`() = runTest {
        val dm = SenderKeyManager.createDistributionMessage("test-group", "unknown")
        assertNull(dm)
    }

    @Test
    @DisplayName("handleDistributionMessage allows receiver to decrypt messages from sender")
    fun `handle distribution message then decrypt`() = runTest {
        SenderKeyManager.getOrCreateSenderKey("test-group", "alice")
        val dm = SenderKeyManager.createDistributionMessage("test-group", "alice")!!
        SenderKeyManager.handleDistributionMessage(dm)

        val plaintext = "Hello group!".encodeToByteArray()
        val ciphertext = SenderKeyManager.encryptGroupMessage("test-group", "alice", plaintext)!!

        val decrypted = SenderKeyManager.decryptGroupMessage("test-group", "alice", ciphertext)
        assertNotNull(decrypted)
        assertTrue(plaintext.contentEquals(decrypted!!))
    }

    @Test
    @DisplayName("encrypt then decrypt returns original plaintext (full roundtrip)")
    fun `encrypt decrypt roundtrip`() = runTest {
        SenderKeyManager.getOrCreateSenderKey("test-group", "alice")
        val dm = SenderKeyManager.createDistributionMessage("test-group", "alice")!!
        SenderKeyManager.handleDistributionMessage(dm)

        val plaintext = "Secret group message".encodeToByteArray()
        val ciphertext = SenderKeyManager.encryptGroupMessage("test-group", "alice", plaintext)!!
        val decrypted = SenderKeyManager.decryptGroupMessage("test-group", "alice", ciphertext)

        assertNotNull(decrypted)
        assertTrue(plaintext.contentEquals(decrypted!!))
    }

    @Test
    @DisplayName("multiple messages in sequence all decrypt correctly and advance chain")
    fun `multiple messages advance chain correctly`() = runTest {
        SenderKeyManager.getOrCreateSenderKey("test-group", "alice")
        val dm = SenderKeyManager.createDistributionMessage("test-group", "alice")!!
        SenderKeyManager.handleDistributionMessage(dm)

        val messages = listOf("First", "Second", "Third", "Fourth", "Fifth")
        val ciphertexts = messages.map { msg ->
            SenderKeyManager.encryptGroupMessage("test-group", "alice", msg.encodeToByteArray())!!
        }

        for (i in messages.indices) {
            val decrypted = SenderKeyManager.decryptGroupMessage("test-group", "alice", ciphertexts[i])
            assertNotNull(decrypted, "Message $i should decrypt successfully")
            assertEquals(messages[i], decrypted!!.decodeToString(), "Message $i content should match")
        }
    }

    @Test
    @DisplayName("deleteGroupKeys removes all keys for that group")
    fun `group deletion cleans up keys`() = runTest {
        SenderKeyManager.getOrCreateSenderKey("test-group-deletion", "alice")
        val dm = SenderKeyManager.createDistributionMessage("test-group-deletion", "alice")!!
        SenderKeyManager.handleDistributionMessage(dm)

        val ciphertext = SenderKeyManager.encryptGroupMessage("test-group-deletion", "alice", "before delete".encodeToByteArray())
        assertNotNull(ciphertext)

        SenderKeyManager.deleteGroupKeys("test-group-deletion")

        val afterDelete = SenderKeyManager.encryptGroupMessage("test-group-deletion", "alice", "after delete".encodeToByteArray())
        assertNull(afterDelete, "encrypt should return null after group keys deleted")

        val decryptAfter = SenderKeyManager.decryptGroupMessage("test-group-deletion", "alice", ciphertext!!)
        assertNull(decryptAfter, "decrypt should return null after group keys deleted")
    }

    @Test
    @DisplayName("different groups have independent sender keys")
    fun `different groups have independent keys`() = runTest {
        SenderKeyManager.getOrCreateSenderKey("test-group", "alice")
        SenderKeyManager.getOrCreateSenderKey("test-group-other", "alice")

        val dm1 = SenderKeyManager.createDistributionMessage("test-group", "alice")!!
        val dm2 = SenderKeyManager.createDistributionMessage("test-group-other", "alice")!!

        SenderKeyManager.handleDistributionMessage(dm1)
        SenderKeyManager.handleDistributionMessage(dm2)

        val ct1 = SenderKeyManager.encryptGroupMessage("test-group", "alice", "group1 msg".encodeToByteArray())!!
        val ct2 = SenderKeyManager.encryptGroupMessage("test-group-other", "alice", "group2 msg".encodeToByteArray())!!

        val dec1 = SenderKeyManager.decryptGroupMessage("test-group", "alice", ct1)
        val dec2 = SenderKeyManager.decryptGroupMessage("test-group-other", "alice", ct2)

        assertEquals("group1 msg", dec1!!.decodeToString())
        assertEquals("group2 msg", dec2!!.decodeToString())

        val dec1Wrong = SenderKeyManager.decryptGroupMessage("test-group-other", "alice", ct1)
        assertNull(dec1Wrong, "decrypting group1 ciphertext with group2 state should fail")

        SenderKeyManager.deleteGroupKeys("test-group-other")
        assertNull(SenderKeyManager.encryptGroupMessage("test-group-other", "alice", "data".encodeToByteArray()))
        assertNotNull(SenderKeyManager.encryptGroupMessage("test-group", "alice", "still works".encodeToByteArray()))
    }

    @Test
    @DisplayName("decrypt with wrong key returns null")
    fun `decrypt with wrong key returns null`() = runTest {
        SenderKeyManager.getOrCreateSenderKey("test-group-wrong-key", "alice")
        val dm = SenderKeyManager.createDistributionMessage("test-group-wrong-key", "alice")!!
        SenderKeyManager.handleDistributionMessage(dm)

        val ct = SenderKeyManager.encryptGroupMessage("test-group-wrong-key", "alice", "hello".encodeToByteArray())!!

        SenderKeyManager.deleteGroupKeys("test-group-wrong-key")

        SenderKeyManager.getOrCreateSenderKey("test-group-wrong-key", "eve")
        val dmEve = SenderKeyManager.createDistributionMessage("test-group-wrong-key", "eve")!!
        SenderKeyManager.handleDistributionMessage(dmEve)

        val decrypted = SenderKeyManager.decryptGroupMessage("test-group-wrong-key", "eve", ct)
        assertNull(decrypted, "decrypting alice's ciphertext with eve's key should return null")
    }

    @Test
    @DisplayName("encrypt returns null when no sender key exists for the group")
    fun `encrypt without sender key returns null`() = runTest {
        val ct = SenderKeyManager.encryptGroupMessage("test-group", "nobody", "data".encodeToByteArray())
        assertNull(ct)
    }

    @Test
    @DisplayName("decrypt returns null when no receiver key exists for the group")
    fun `decrypt without receiver key returns null`() = runTest {
        val result = SenderKeyManager.decryptGroupMessage("test-group", "nobody", ByteArray(16))
        assertNull(result)
    }

    @Test
    @DisplayName("decrypt returns null on malformed payload (too short)")
    fun `decrypt malformed payload returns null`() = runTest {
        SenderKeyManager.getOrCreateSenderKey("test-group", "alice")
        val dm = SenderKeyManager.createDistributionMessage("test-group", "alice")!!
        SenderKeyManager.handleDistributionMessage(dm)

        val shortPayload = ByteArray(4)
        val result = SenderKeyManager.decryptGroupMessage("test-group", "alice", shortPayload)
        assertNull(result)
    }

    @Test
    @DisplayName("decrypt returns null on corrupted ciphertext")
    fun `decrypt corrupted ciphertext returns null`() = runTest {
        SenderKeyManager.getOrCreateSenderKey("test-group", "alice")
        val dm = SenderKeyManager.createDistributionMessage("test-group", "alice")!!
        SenderKeyManager.handleDistributionMessage(dm)

        val ct = SenderKeyManager.encryptGroupMessage("test-group", "alice", "hello".encodeToByteArray())!!

        val corrupted = ct.copyOf()
        corrupted[corrupted.size - 1] = (corrupted.last().toInt() xor 0xFF).toByte()
        val result = SenderKeyManager.decryptGroupMessage("test-group", "alice", corrupted)
        assertNull(result)
    }

    @Test
    @DisplayName("empty plaintext encrypts and decrypts correctly")
    fun `empty plaintext roundtrip`() = runTest {
        SenderKeyManager.getOrCreateSenderKey("test-group", "alice")
        val dm = SenderKeyManager.createDistributionMessage("test-group", "alice")!!
        SenderKeyManager.handleDistributionMessage(dm)

        val emptyPlaintext = ByteArray(0)
        val ct = SenderKeyManager.encryptGroupMessage("test-group", "alice", emptyPlaintext)!!
        val decrypted = SenderKeyManager.decryptGroupMessage("test-group", "alice", ct)

        assertNotNull(decrypted)
        assertEquals(0, decrypted!!.size)
    }

    @Test
    @DisplayName("large plaintext (10KB) encrypts and decrypts correctly")
    fun `large plaintext roundtrip`() = runTest {
        SenderKeyManager.getOrCreateSenderKey("test-group-large", "alice")
        val dm = SenderKeyManager.createDistributionMessage("test-group-large", "alice")!!
        SenderKeyManager.handleDistributionMessage(dm)

        val largePlaintext = ByteArray(10 * 1024) { (it % 256).toByte() }
        val ct = SenderKeyManager.encryptGroupMessage("test-group-large", "alice", largePlaintext)!!
        val decrypted = SenderKeyManager.decryptGroupMessage("test-group-large", "alice", ct)

        assertNotNull(decrypted)
        assertTrue(largePlaintext.contentEquals(decrypted!!))
    }

    @Test
    @DisplayName("full lifecycle: create keys, distribute, send messages, delete keys, create fresh, send new messages")
    fun `full lifecycle roundtrip`() = runTest {
        SenderKeyManager.getOrCreateSenderKey("test-group-multi", "alice")
        val dm = SenderKeyManager.createDistributionMessage("test-group-multi", "alice")!!
        SenderKeyManager.handleDistributionMessage(dm)

        val ct1 = SenderKeyManager.encryptGroupMessage("test-group-multi", "alice", "old msg".encodeToByteArray())!!
        val dec1 = SenderKeyManager.decryptGroupMessage("test-group-multi", "alice", ct1)
        assertEquals("old msg", dec1!!.decodeToString())

        SenderKeyManager.deleteGroupKeys("test-group-multi")

        assertNull(SenderKeyManager.createDistributionMessage("test-group-multi", "alice"))

        assertNull(SenderKeyManager.encryptGroupMessage("test-group-multi", "alice", "fail".encodeToByteArray()))

        assertNull(SenderKeyManager.decryptGroupMessage("test-group-multi", "alice", ct1))

        SenderKeyManager.getOrCreateSenderKey("test-group-multi", "alice")
        val dm2 = SenderKeyManager.createDistributionMessage("test-group-multi", "alice")!!
        SenderKeyManager.handleDistributionMessage(dm2)

        val ct2 = SenderKeyManager.encryptGroupMessage("test-group-multi", "alice", "new msg".encodeToByteArray())!!
        val dec2 = SenderKeyManager.decryptGroupMessage("test-group-multi", "alice", ct2)
        assertEquals("new msg", dec2!!.decodeToString())
    }
}
