package org.enchant.core.crypto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@DisplayName("MediaCipher — AES-256-GCM media encryption")
class MediaCipherTest {

    @Nested @DisplayName("In-Memory Encryption")
    inner class MemoryEncryptTest {
        @Test @DisplayName("encrypt then decrypt returns original")
        fun `encrypt decrypt roundtrip`() {
            val plaintext = "Media content".encodeToByteArray()
            val result = MediaCipher.encrypt(plaintext)
            val decrypted = MediaCipher.decrypt(result.ciphertext, result.mediaKey)
            assertArrayEquals(plaintext, decrypted)
            result.zeroKey()
        }

        @Test @DisplayName("ciphertext includes nonce + ciphertext + tag")
        fun `ciphertext format`() {
            val plaintext = ByteArray(100)
            val result = MediaCipher.encrypt(plaintext)
            assertEquals(100 + 12 + 16, result.ciphertext.size)
            result.zeroKey()
        }

        @Test @DisplayName("SHA-256 digest is 32 bytes")
        fun `digest 32 bytes`() {
            val plaintext = "test".encodeToByteArray()
            val result = MediaCipher.encrypt(plaintext)
            assertEquals(32, result.sha256Digest.size)
            result.zeroKey()
        }

        @Test @DisplayName("same plaintext with different keys produces different ciphertext")
        fun `different keys different ciphertext`() {
            val plaintext = "same content".encodeToByteArray()
            val r1 = MediaCipher.encrypt(plaintext)
            val r2 = MediaCipher.encrypt(plaintext)
            assertFalse(r1.ciphertext.contentEquals(r2.ciphertext))
            r1.zeroKey()
            r2.zeroKey()
        }

        @Test @DisplayName("wrong key throws exception")
        fun `wrong key throws`() {
            val plaintext = "secret".encodeToByteArray()
            val result = MediaCipher.encrypt(plaintext)
            val wrongKey = ByteArray(32) { 0xFF.toByte() }
            assertThrows<Exception> { MediaCipher.decrypt(result.ciphertext, wrongKey) }
            result.zeroKey()
        }

        @Test @DisplayName("SHA-256 mismatch throws IntegrityException")
        fun `sha256 mismatch throws`() {
            val plaintext = "test".encodeToByteArray()
            val result = MediaCipher.encrypt(plaintext)
            val wrongDigest = ByteArray(32) { 0xFF.toByte() }
            assertThrows<MediaCipher.IntegrityException> {
                MediaCipher.decrypt(result.ciphertext, result.mediaKey, wrongDigest)
            }
            result.zeroKey()
        }

        @Test @DisplayName("correct SHA-256 passes verification")
        fun `sha256 correct passes`() {
            val plaintext = "test".encodeToByteArray()
            val result = MediaCipher.encrypt(plaintext)
            val decrypted = MediaCipher.decrypt(result.ciphertext, result.mediaKey, result.sha256Digest)
            assertArrayEquals(plaintext, decrypted)
            result.zeroKey()
        }

        @Test @DisplayName("zeroKey clears the media key")
        fun `zero key clears`() {
            val plaintext = "test".encodeToByteArray()
            val result = MediaCipher.encrypt(plaintext)
            val keyCopy = result.mediaKey.copyOf()
            result.zeroKey()
            assertFalse(result.mediaKey.contentEquals(keyCopy))
            assertTrue(result.mediaKey.all { it == 0.toByte() })
        }
    }

    @Nested @DisplayName("Streaming Encryption")
    inner class StreamingTest {
        @Test @DisplayName("encryptStream then decryptStream roundtrip")
        fun `stream roundtrip`() {
            val plaintext = "Streaming media content".encodeToByteArray()
            val input = ByteArrayInputStream(plaintext)
            val encryptedOutput = ByteArrayOutputStream()
            val result = MediaCipher.encryptStream(input, encryptedOutput)

            val decryptInput = ByteArrayInputStream(encryptedOutput.toByteArray())
            val decryptedOutput = ByteArrayOutputStream()
            MediaCipher.decryptStream(decryptInput, decryptedOutput, result.mediaKey)

            assertArrayEquals(plaintext, decryptedOutput.toByteArray())
            result.zeroKey()
        }

        @Test @DisplayName("stream SHA-256 verification passes")
        fun `stream sha256 verify`() {
            val plaintext = "Streaming with verify".encodeToByteArray()
            val input = ByteArrayInputStream(plaintext)
            val encryptedOutput = ByteArrayOutputStream()
            val result = MediaCipher.encryptStream(input, encryptedOutput)

            val decryptInput = ByteArrayInputStream(encryptedOutput.toByteArray())
            val decryptedOutput = ByteArrayOutputStream()
            MediaCipher.decryptStream(decryptInput, decryptedOutput, result.mediaKey, result.sha256Digest)

            assertArrayEquals(plaintext, decryptedOutput.toByteArray())
            result.zeroKey()
        }

        @Test @DisplayName("stream SHA-256 mismatch throws")
        fun `stream sha256 mismatch`() {
            val plaintext = "Streaming".encodeToByteArray()
            val input = ByteArrayInputStream(plaintext)
            val encryptedOutput = ByteArrayOutputStream()
            val result = MediaCipher.encryptStream(input, encryptedOutput)

            val wrongDigest = ByteArray(32) { 0xFF.toByte() }
            val decryptInput = ByteArrayInputStream(encryptedOutput.toByteArray())
            val decryptedOutput = ByteArrayOutputStream()
            assertThrows<MediaCipher.IntegrityException> {
                MediaCipher.decryptStream(decryptInput, decryptedOutput, result.mediaKey, wrongDigest)
            }
            result.zeroKey()
        }
    }

    @Nested @DisplayName("Digest Computation")
    inner class DigestTest {
        @Test @DisplayName("computeCiphertextDigest produces 32 bytes")
        fun `digest 32 bytes`() {
            val ciphertext = ByteArray(100) { it.toByte() }
            val digest = MediaCipher.computeCiphertextDigest(ciphertext)
            assertEquals(32, digest.size)
        }

        @Test @DisplayName("same ciphertext produces same digest")
        fun `digest deterministic`() {
            val ciphertext = ByteArray(100) { it.toByte() }
            val d1 = MediaCipher.computeCiphertextDigest(ciphertext)
            val d2 = MediaCipher.computeCiphertextDigest(ciphertext)
            assertArrayEquals(d1, d2)
        }
    }
}
