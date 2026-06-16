package org.enchant.core.crypto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("NativeSessionManager — Data Classes & API Shape")
class NativeSessionManagerTest {

    @Nested
    @DisplayName("EncryptedPayload")
    inner class EncryptedPayloadTest {
        @Test
        @DisplayName("equality when all fields match")
        fun `equality match`() {
            val p1 = NativeSessionManager.EncryptedPayload(
                messageType = NativeSessionManager.MessageType.ENCRYPTED_MESSAGE,
                payload = ByteArray(10) { 1 },
                recipientDeviceId = "device-1"
            )
            val p2 = NativeSessionManager.EncryptedPayload(
                messageType = NativeSessionManager.MessageType.ENCRYPTED_MESSAGE,
                payload = ByteArray(10) { 1 },
                recipientDeviceId = "device-1"
            )
            assertEquals(p1, p2)
            assertEquals(p1.hashCode(), p2.hashCode())
        }

        @Test
        @DisplayName("inequality by messageType")
        fun `inequality messageType`() {
            val p1 = NativeSessionManager.EncryptedPayload(
                messageType = NativeSessionManager.MessageType.ENCRYPTED_MESSAGE,
                payload = ByteArray(10) { 1 }
            )
            val p2 = NativeSessionManager.EncryptedPayload(
                messageType = NativeSessionManager.MessageType.PREKEY_MESSAGE,
                payload = ByteArray(10) { 1 }
            )
            assertNotEquals(p1, p2)
        }

        @Test
        @DisplayName("inequality by payload content")
        fun `inequality payload`() {
            val p1 = NativeSessionManager.EncryptedPayload(
                messageType = NativeSessionManager.MessageType.ENCRYPTED_MESSAGE,
                payload = ByteArray(10) { 1 }
            )
            val p2 = NativeSessionManager.EncryptedPayload(
                messageType = NativeSessionManager.MessageType.ENCRYPTED_MESSAGE,
                payload = ByteArray(10) { 2 }
            )
            assertNotEquals(p1, p2)
        }

        @Test
        @DisplayName("inequality by recipientDeviceId")
        fun `inequality device`() {
            val p1 = NativeSessionManager.EncryptedPayload(
                messageType = NativeSessionManager.MessageType.ENCRYPTED_MESSAGE,
                payload = ByteArray(10) { 1 },
                recipientDeviceId = "device-1"
            )
            val p2 = NativeSessionManager.EncryptedPayload(
                messageType = NativeSessionManager.MessageType.ENCRYPTED_MESSAGE,
                payload = ByteArray(10) { 1 },
                recipientDeviceId = "device-2"
            )
            assertNotEquals(p1, p2)
        }

        @Test
        @DisplayName("self-equality")
        fun `self equality`() {
            val p = NativeSessionManager.EncryptedPayload(
                messageType = NativeSessionManager.MessageType.ENCRYPTED_MESSAGE,
                payload = ByteArray(10) { 1 }
            )
            assertEquals(p, p)
        }

        @Test
        @DisplayName("null recipientDeviceId is allowed")
        fun `null device`() {
            val p = NativeSessionManager.EncryptedPayload(
                messageType = NativeSessionManager.MessageType.PREKEY_MESSAGE,
                payload = ByteArray(5),
                recipientDeviceId = null
            )
            assertNull(p.recipientDeviceId)
        }

        @Test
        @DisplayName("copy creates equal instance")
        fun `copy equality`() {
            val p1 = NativeSessionManager.EncryptedPayload(
                messageType = NativeSessionManager.MessageType.ENCRYPTED_MESSAGE,
                payload = ByteArray(10) { 1 },
                recipientDeviceId = "device-1"
            )
            val p2 = p1.copy()
            assertEquals(p1, p2)
        }

        @Test
        @DisplayName("copy with changed field creates unequal instance")
        fun `copy with change`() {
            val p1 = NativeSessionManager.EncryptedPayload(
                messageType = NativeSessionManager.MessageType.ENCRYPTED_MESSAGE,
                payload = ByteArray(10) { 1 },
                recipientDeviceId = "device-1"
            )
            val p2 = p1.copy(recipientDeviceId = "device-2")
            assertNotEquals(p1, p2)
        }

        @Test
        @DisplayName("component functions work correctly")
        fun `component functions`() {
            val p = NativeSessionManager.EncryptedPayload(
                messageType = NativeSessionManager.MessageType.PREKEY_MESSAGE,
                payload = ByteArray(5) { it.toByte() },
                recipientDeviceId = "dev"
            )
            val (type, payload, device) = p
            assertEquals(NativeSessionManager.MessageType.PREKEY_MESSAGE, type)
            assertEquals(5, payload.size)
            assertEquals("dev", device)
        }
    }

    @Nested
    @DisplayName("DecryptedResult")
    inner class DecryptedResultTest {
        @Test
        @DisplayName("equality when all fields match")
        fun `equality match`() {
            val r1 = NativeSessionManager.DecryptedResult(
                plaintext = "hello".encodeToByteArray(),
                senderDeviceId = "device-1",
                isNewSession = true
            )
            val r2 = NativeSessionManager.DecryptedResult(
                plaintext = "hello".encodeToByteArray(),
                senderDeviceId = "device-1",
                isNewSession = true
            )
            assertEquals(r1, r2)
            assertEquals(r1.hashCode(), r2.hashCode())
        }

        @Test
        @DisplayName("inequality by isNewSession")
        fun `inequality isNewSession`() {
            val r1 = NativeSessionManager.DecryptedResult(
                plaintext = "hello".encodeToByteArray(),
                isNewSession = true
            )
            val r2 = NativeSessionManager.DecryptedResult(
                plaintext = "hello".encodeToByteArray(),
                isNewSession = false
            )
            assertNotEquals(r1, r2)
        }

        @Test
        @DisplayName("inequality by plaintext")
        fun `inequality plaintext`() {
            val r1 = NativeSessionManager.DecryptedResult(plaintext = "hello".encodeToByteArray())
            val r2 = NativeSessionManager.DecryptedResult(plaintext = "world".encodeToByteArray())
            assertNotEquals(r1, r2)
        }

        @Test
        @DisplayName("inequality by senderDeviceId")
        fun `inequality senderDeviceId`() {
            val r1 = NativeSessionManager.DecryptedResult(
                plaintext = "hello".encodeToByteArray(),
                senderDeviceId = "device-1"
            )
            val r2 = NativeSessionManager.DecryptedResult(
                plaintext = "hello".encodeToByteArray(),
                senderDeviceId = "device-2"
            )
            assertNotEquals(r1, r2)
        }

        @Test
        @DisplayName("self-equality")
        fun `self equality`() {
            val r = NativeSessionManager.DecryptedResult(plaintext = "hello".encodeToByteArray())
            assertEquals(r, r)
        }

        @Test
        @DisplayName("copy creates equal instance")
        fun `copy equality`() {
            val r1 = NativeSessionManager.DecryptedResult(
                plaintext = "hello".encodeToByteArray(),
                senderDeviceId = "device-1",
                isNewSession = true
            )
            val r2 = r1.copy()
            assertEquals(r1, r2)
        }

        @Test
        @DisplayName("copy with changed field creates unequal instance")
        fun `copy with change`() {
            val r1 = NativeSessionManager.DecryptedResult(
                plaintext = "hello".encodeToByteArray(),
                senderDeviceId = "device-1",
                isNewSession = true
            )
            val r2 = r1.copy(isNewSession = false)
            assertNotEquals(r1, r2)
        }

        @Test
        @DisplayName("default parameters work correctly")
        fun `defaults`() {
            val r = NativeSessionManager.DecryptedResult(plaintext = "test".encodeToByteArray())
            assertNull(r.senderDeviceId)
            assertFalse(r.isNewSession)
        }

        @Test
        @DisplayName("component functions work correctly")
        fun `component functions`() {
            val r = NativeSessionManager.DecryptedResult(
                plaintext = "hello".encodeToByteArray(),
                senderDeviceId = "dev",
                isNewSession = true
            )
            val (pt, sender, isNew) = r
            assertEquals("hello", pt.decodeToString())
            assertEquals("dev", sender)
            assertTrue(isNew)
        }
    }

    @Nested
    @DisplayName("MessageType Enum")
    inner class MessageTypeTest {
        @Test
        @DisplayName("ENCRYPTED_MESSAGE and PREKEY_MESSAGE are distinct")
        fun `enum values distinct`() {
            assertNotEquals(
                NativeSessionManager.MessageType.ENCRYPTED_MESSAGE,
                NativeSessionManager.MessageType.PREKEY_MESSAGE
            )
        }

        @Test
        @DisplayName("values() returns both types")
        fun `enum values count`() {
            assertEquals(2, NativeSessionManager.MessageType.values().size)
        }

        @Test
        @DisplayName("valueOf works for ENCRYPTED_MESSAGE")
        fun `valueOf encrypted`() {
            assertEquals(
                NativeSessionManager.MessageType.ENCRYPTED_MESSAGE,
                NativeSessionManager.MessageType.valueOf("ENCRYPTED_MESSAGE")
            )
        }

        @Test
        @DisplayName("valueOf works for PREKEY_MESSAGE")
        fun `valueOf prekey`() {
            assertEquals(
                NativeSessionManager.MessageType.PREKEY_MESSAGE,
                NativeSessionManager.MessageType.valueOf("PREKEY_MESSAGE")
            )
        }
    }
}
