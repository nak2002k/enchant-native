package org.enchant.core.crypto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SealedSender — Anonymous sender encryption")
class SealedSenderTest {

    @Nested @DisplayName("Access Key Derivation")
    inner class AccessKeyTest {
        @Test @DisplayName("deriveAccessKey produces 16-byte key")
        fun `access key 16 bytes`() {
            val profileKey = ByteArray(32) { 1 }
            val senderIk = ByteArray(32) { 2 }
            val accessKey = SealedSender.deriveAccessKey(profileKey, senderIk)
            assertEquals(16, accessKey.size)
        }

        @Test @DisplayName("same inputs produce same access key")
        fun `access key deterministic`() {
            val profileKey = ByteArray(32) { 1 }
            val senderIk = ByteArray(32) { 2 }
            val ak1 = SealedSender.deriveAccessKey(profileKey, senderIk)
            val ak2 = SealedSender.deriveAccessKey(profileKey, senderIk)
            assertArrayEquals(ak1, ak2)
        }

        @Test @DisplayName("different sender keys produce different access keys")
        fun `access key different senders`() {
            val profileKey = ByteArray(32) { 1 }
            val senderIk1 = ByteArray(32) { 2 }
            val senderIk2 = ByteArray(32) { 3 }
            val ak1 = SealedSender.deriveAccessKey(profileKey, senderIk1)
            val ak2 = SealedSender.deriveAccessKey(profileKey, senderIk2)
            assertFalse(ak1.contentEquals(ak2))
        }

        @Test @DisplayName("wrong profile key size throws")
        fun `access key wrong profile size`() {
            assertThrows<IllegalArgumentException> {
                SealedSender.deriveAccessKey(ByteArray(16), ByteArray(32))
            }
        }

        @Test @DisplayName("wrong sender key size throws")
        fun `access key wrong sender size`() {
            assertThrows<IllegalArgumentException> {
                SealedSender.deriveAccessKey(ByteArray(32), ByteArray(16))
            }
        }
    }

    @Nested @DisplayName("Sealed Encryption/Decryption")
    inner class SealedEncryptDecryptTest {
        @Test @DisplayName("encrypt then decrypt returns sender identity and message")
        fun `sealed roundtrip`() {
            val profileKey = ByteArray(32) { 1 }
            val senderIk = ByteArray(32) { 2 }
            val accessKey = SealedSender.deriveAccessKey(profileKey, senderIk)
            val message = "sealed message".encodeToByteArray()

            val sealed = SealedSender.encryptSealed(accessKey, senderIk, message)
            val (decryptedSenderIk, decryptedMessage) = SealedSender.decryptSealed(accessKey, sealed)!!

            assertArrayEquals(senderIk, decryptedSenderIk)
            assertArrayEquals(message, decryptedMessage)
        }

        @Test @DisplayName("wrong access key fails decryption")
        fun `sealed wrong key fails`() {
            val profileKey = ByteArray(32) { 1 }
            val senderIk = ByteArray(32) { 2 }
            val accessKey = SealedSender.deriveAccessKey(profileKey, senderIk)
            val wrongAccessKey = ByteArray(16) { 0xFF.toByte() }
            val message = "sealed message".encodeToByteArray()

            val sealed = SealedSender.encryptSealed(accessKey, senderIk, message)
            assertNull(SealedSender.decryptSealed(wrongAccessKey, sealed))
        }

        @Test @DisplayName("wrong access key size throws")
        fun `sealed wrong key size`() {
            assertThrows<IllegalArgumentException> {
                SealedSender.encryptSealed(ByteArray(8), ByteArray(32), ByteArray(10))
            }
        }
    }

    @Nested @DisplayName("Profile Data Encryption")
    inner class ProfileDataTest {
        @Test @DisplayName("encryptProfileData then decryptProfileData roundtrip")
        fun `profile data roundtrip`() {
            val profileKey = ByteArray(32) { 1 }
            val data = "My Profile Name".encodeToByteArray()

            val encrypted = SealedSender.encryptProfileData(profileKey, data)
            val decrypted = SealedSender.decryptProfileData(profileKey, encrypted)

            assertArrayEquals(data, decrypted)
        }

        @Test @DisplayName("wrong profile key fails decryption")
        fun `profile data wrong key`() {
            val profileKey = ByteArray(32) { 1 }
            val wrongKey = ByteArray(32) { 2 }
            val data = "Profile".encodeToByteArray()

            val encrypted = SealedSender.encryptProfileData(profileKey, data)
            assertNull(SealedSender.decryptProfileData(wrongKey, encrypted))
        }

        @Test @DisplayName("wrong profile key size throws")
        fun `profile data wrong size`() {
            assertThrows<IllegalArgumentException> {
                SealedSender.encryptProfileData(ByteArray(16), ByteArray(10))
            }
        }
    }
}
