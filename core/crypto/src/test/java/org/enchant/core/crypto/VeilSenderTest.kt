package org.enchant.core.crypto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("VeilSender — Native Veil anonymous sender encryption")
class VeilSenderTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun init() {
            CryptoPrimitives.init()
        }
    }

    @Nested @DisplayName("Access Key Derivation")
    inner class AccessKeyTest {
        @Test @DisplayName("deriveAccessKey produces 16-byte key")
        fun `access key 16 bytes`() {
            val profileKey = ByteArray(32) { 1 }
            val senderIk = ByteArray(32) { 2 }
            val accessKey = VeilSender.deriveAccessKey(profileKey, senderIk)
            assertEquals(16, accessKey.size)
        }

        @Test @DisplayName("same inputs produce same access key")
        fun `access key deterministic`() {
            val profileKey = ByteArray(32) { 1 }
            val senderIk = ByteArray(32) { 2 }
            val ak1 = VeilSender.deriveAccessKey(profileKey, senderIk)
            val ak2 = VeilSender.deriveAccessKey(profileKey, senderIk)
            assertArrayEquals(ak1, ak2)
        }

        @Test @DisplayName("different sender keys produce different access keys")
        fun `access key different senders`() {
            val profileKey = ByteArray(32) { 1 }
            val senderIk1 = ByteArray(32) { 2 }
            val senderIk2 = ByteArray(32) { 3 }
            val ak1 = VeilSender.deriveAccessKey(profileKey, senderIk1)
            val ak2 = VeilSender.deriveAccessKey(profileKey, senderIk2)
            assertFalse(ak1.contentEquals(ak2))
        }

        @Test @DisplayName("wrong profile key size throws")
        fun `access key wrong profile size`() {
            assertThrows(IllegalArgumentException::class.java) {
                VeilSender.deriveAccessKey(ByteArray(16), ByteArray(32))
            }
        }

        @Test @DisplayName("wrong sender key size throws")
        fun `access key wrong sender size`() {
            assertThrows(IllegalArgumentException::class.java) {
                VeilSender.deriveAccessKey(ByteArray(32), ByteArray(16))
            }
        }
    }

    @Nested @DisplayName("Veil Sealed Encryption/Decryption")
    inner class SealedEncryptDecryptTest {
        @Test @DisplayName("encrypt then decrypt returns sender identity and message")
        fun `sealed roundtrip`() {
            val recipient = CryptoPrimitives.generateX25519KeyPair()
            val sender = CryptoPrimitives.generateX25519KeyPair()
            val message = "sealed message".encodeToByteArray()

            val sealed = VeilSender.encryptVeiled(
                recipient.publicKey,
                sender.privateKey,
                sender.publicKey,
                message
            )
            val (decryptedSenderKey, decryptedMessage) = VeilSender.decryptVeiled(
                recipient.privateKey,
                recipient.publicKey,
                sealed
            )!!

            assertArrayEquals(sender.publicKey, decryptedSenderKey)
            assertArrayEquals(message, decryptedMessage)
        }

        @Test @DisplayName("wrong recipient private key fails decryption")
        fun `sealed wrong key fails`() {
            val recipient = CryptoPrimitives.generateX25519KeyPair()
            val wrongRecipient = CryptoPrimitives.generateX25519KeyPair()
            val sender = CryptoPrimitives.generateX25519KeyPair()
            val message = "sealed message".encodeToByteArray()

            val sealed = VeilSender.encryptVeiled(
                recipient.publicKey,
                sender.privateKey,
                sender.publicKey,
                message
            )
            assertNull(
                VeilSender.decryptVeiled(
                    wrongRecipient.privateKey,
                    recipient.publicKey,
                    sealed
                )
            )
        }

        @Test @DisplayName("corrupted ciphertext fails decryption")
        fun `sealed corrupted fails`() {
            val recipient = CryptoPrimitives.generateX25519KeyPair()
            val sender = CryptoPrimitives.generateX25519KeyPair()
            val message = "sealed message".encodeToByteArray()

            val sealed = VeilSender.encryptVeiled(
                recipient.publicKey,
                sender.privateKey,
                sender.publicKey,
                message
            )
            sealed[sealed.size / 2] = (sealed[sealed.size / 2].toInt() xor 0xFF).toByte()

            assertNull(
                VeilSender.decryptVeiled(
                    recipient.privateKey,
                    recipient.publicKey,
                    sealed
                )
            )
        }

        @Test @DisplayName("wrong key sizes throw")
        fun `sealed wrong key size`() {
            assertThrows(IllegalArgumentException::class.java) {
                VeilSender.encryptVeiled(
                    ByteArray(8), ByteArray(32), ByteArray(32), ByteArray(10)
                )
            }
        }
    }

    @Nested @DisplayName("Profile Data Encryption")
    inner class ProfileDataTest {
        @Test @DisplayName("encryptProfileData then decryptProfileData roundtrip")
        fun `profile data roundtrip`() {
            val profileKey = ByteArray(32) { 1 }
            val data = "My Profile Name".encodeToByteArray()

            val encrypted = VeilSender.encryptProfileData(profileKey, data)
            val decrypted = VeilSender.decryptProfileData(profileKey, encrypted)

            assertArrayEquals(data, decrypted)
        }

        @Test @DisplayName("wrong profile key fails decryption")
        fun `profile data wrong key`() {
            val profileKey = ByteArray(32) { 1 }
            val wrongKey = ByteArray(32) { 2 }
            val data = "Profile".encodeToByteArray()

            val encrypted = VeilSender.encryptProfileData(profileKey, data)
            assertNull(VeilSender.decryptProfileData(wrongKey, encrypted))
        }

        @Test @DisplayName("wrong profile key size throws")
        fun `profile data wrong size`() {
            assertThrows(IllegalArgumentException::class.java) {
                VeilSender.encryptProfileData(ByteArray(16), ByteArray(10))
            }
        }
    }
}
