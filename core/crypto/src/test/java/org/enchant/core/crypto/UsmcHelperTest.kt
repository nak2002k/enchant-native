package org.enchant.core.crypto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("UsmcHelper — Unidentified Sender Message Content")
class UsmcHelperTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun init() {
            CryptoPrimitives.init()
        }
    }

    @Nested @DisplayName("Create / Deserialize Roundtrip")
    inner class CreateDeserializeTest {
        @Test @DisplayName("serialize/deserialize returns original contents and message type")
        fun `usmc roundtrip`() {
            val plaintext = "hello veil".encodeToByteArray()
            val usmc = UsmcHelper.create(
                msgType = 2,
                senderCertData = ByteArray(0),
                plaintext = plaintext,
                contentHint = 0,
                groupId = ByteArray(0)
            )

            val (deserialized, msgType) = UsmcHelper.deserialize(usmc)

            assertEquals(2, msgType)
            assertArrayEquals(plaintext, UsmcHelper.getContents(deserialized))
            assertEquals(0, UsmcHelper.getContentHint(deserialized))
            assertEquals(0, UsmcHelper.getGroupId(deserialized).size)
        }

        @Test @DisplayName("content hint and group id are preserved")
        fun `usmc metadata preserved`() {
            val plaintext = "group msg".encodeToByteArray()
            val groupId = ByteArray(16) { it.toByte() }
            val usmc = UsmcHelper.create(
                msgType = 7,
                senderCertData = ByteArray(0),
                plaintext = plaintext,
                contentHint = 1,
                groupId = groupId
            )

            assertEquals(7, UsmcHelper.getMessageType(usmc))
            assertEquals(1, UsmcHelper.getContentHint(usmc))
            assertArrayEquals(groupId, UsmcHelper.getGroupId(usmc))
            assertArrayEquals(plaintext, UsmcHelper.getContents(usmc))
        }
    }

    @Nested @DisplayName("Error Handling")
    inner class ErrorTest {
        @Test @DisplayName("deserialize on garbage data fails")
        fun `deserialize garbage fails`() {
            val garbage = ByteArray(20) { 0xFF.toByte() }
            assertThrows(IllegalStateException::class.java) {
                UsmcHelper.deserialize(garbage)
            }
        }

        @Test @DisplayName("create with empty plaintext is allowed")
        fun `empty plaintext allowed`() {
            val usmc = UsmcHelper.create(
                msgType = 2,
                senderCertData = ByteArray(0),
                plaintext = ByteArray(0),
                contentHint = 0,
                groupId = ByteArray(0)
            )
            assertEquals(0, UsmcHelper.getContents(usmc).size)
        }
    }
}
