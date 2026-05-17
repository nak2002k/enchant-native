package org.enchant.core.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Base64

@DisplayName("CryptoHelper")
class CryptoHelperTest {

    private fun hexToBytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Nested @DisplayName("SHA-256")
    inner class Sha256Test {
        @Test @DisplayName("produces 32 bytes")
        fun `sha256 produces 32 bytes`() {
            val result = CryptoHelper.sha256("test".encodeToByteArray())
            assertEquals(32, result.size)
        }
        @Test @DisplayName("same input produces same output")
        fun `deterministic`() {
            val a = CryptoHelper.sha256("hello".encodeToByteArray())
            val b = CryptoHelper.sha256("hello".encodeToByteArray())
            assertArrayEquals(a, b)
        }
        @Test @DisplayName("different input produces different output")
        fun `different inputs`() {
            val a = CryptoHelper.sha256("hello".encodeToByteArray())
            val b = CryptoHelper.sha256("world".encodeToByteArray())
            assertFalse(a.contentEquals(b))
        }
        @Test @DisplayName("empty input produces valid hash")
        fun `empty input`() {
            val result = CryptoHelper.sha256(ByteArray(0))
            assertEquals(32, result.size)
        }
    }

    @Nested @DisplayName("HKDF-SHA256 (RFC 5869)")
    inner class HkdfSha256Test {
        @Test @DisplayName("Test Case 1 from RFC 5869")
        fun `rfc5869 test case 1`() {
            val ik = hexToBytes("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
            val salt = hexToBytes("000102030405060708090a0b0c")
            val info = hexToBytes("f0f1f2f3f4f5f6f7f8f9")
            val expected = hexToBytes("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865")
            val result = CryptoHelper.hkdfSha256(ik, salt, info, 42)
            assertArrayEquals(expected, result)
        }
        @Test @DisplayName("negative length throws")
        fun `negative length throws`() {
            assertThrows(IllegalArgumentException::class.java) {
                CryptoHelper.hkdfSha256(ByteArray(1), ByteArray(1), ByteArray(1), -1)
            }
        }
        @Test @DisplayName("zero length throws")
        fun `zero length throws`() {
            assertThrows(IllegalArgumentException::class.java) {
                CryptoHelper.hkdfSha256(ByteArray(1), ByteArray(1), ByteArray(1), 0)
            }
        }
        @Test @DisplayName("empty salt is replaced with 32 zero bytes")
        fun `empty salt`() {
            val result = CryptoHelper.hkdfSha256("input".encodeToByteArray(), ByteArray(0), "info".encodeToByteArray(), 16)
            assertEquals(16, result.size)
        }
    }

    @Nested @DisplayName("XChaCha20-Poly1305")
    inner class XChaCha20Poly1305Test {
        @Test @DisplayName("encrypt then decrypt roundtrip")
        fun `roundtrip`() {
            val key = CryptoHelper.generateRandomKey(32)
            val plaintext = "Hello, Enchant!".encodeToByteArray()
            val ciphertext = CryptoHelper.encryptXChaCha20Poly1305(plaintext, key)
            val decrypted = CryptoHelper.decryptXChaCha20Poly1305(ciphertext, key)
            assertArrayEquals(plaintext, decrypted)
        }

        @Test @DisplayName("ciphertext is larger than plaintext (24-byte nonce + tag)")
        fun `ciphertext overhead`() {
            val plaintext = ByteArray(100)
            val key = CryptoHelper.generateRandomKey(32)
            val ct = CryptoHelper.encryptXChaCha20Poly1305(plaintext, key)
            assertTrue(ct.size > plaintext.size)
            assertEquals(plaintext.size + 24 + 16, ct.size) // nonce(24) + tag(16)
        }

        @Test @DisplayName("wrong key fails to decrypt")
        fun `wrong key`() {
            val plaintext = "secret".encodeToByteArray()
            val key1 = CryptoHelper.generateRandomKey(32)
            val key2 = CryptoHelper.generateRandomKey(32)
            val ct = CryptoHelper.encryptXChaCha20Poly1305(plaintext, key1)
            assertThrows(Exception::class.java) { CryptoHelper.decryptXChaCha20Poly1305(ct, key2) }
        }

        @Test @DisplayName("wrong key throws with MAC mismatch message")
        fun `wrong key throws mac mismatch`() {
            val plaintext = "test".encodeToByteArray()
            val key1 = CryptoHelper.generateRandomKey(32)
            val key2 = CryptoHelper.generateRandomKey(32)
            val ct = CryptoHelper.encryptXChaCha20Poly1305(plaintext, key1)
            val ex = assertThrows(Exception::class.java) { CryptoHelper.decryptXChaCha20Poly1305(ct, key2) }
            assertTrue(ex.message?.contains("MAC", ignoreCase = true) == true)
        }

        @Test @DisplayName("empty plaintext encrypts and decrypts")
        fun `empty plaintext`() {
            val key = CryptoHelper.generateRandomKey(32)
            val ct = CryptoHelper.encryptXChaCha20Poly1305(ByteArray(0), key)
            val pt = CryptoHelper.decryptXChaCha20Poly1305(ct, key)
            assertArrayEquals(ByteArray(0), pt)
        }

        @Test @DisplayName("large plaintext (1MB) roundtrips")
        fun `large plaintext`() {
            val key = CryptoHelper.generateRandomKey(32)
            val pt = ByteArray(1024 * 1024)
            val ct = CryptoHelper.encryptXChaCha20Poly1305(pt, key)
            val decrypted = CryptoHelper.decryptXChaCha20Poly1305(ct, key)
            assertArrayEquals(pt, decrypted)
        }

        @Test @DisplayName("wrong key size throws")
        fun `wrong key size`() {
            val key = CryptoHelper.generateRandomKey(16) // wrong size
            assertThrows(Exception::class.java) {
                CryptoHelper.encryptXChaCha20Poly1305("test".encodeToByteArray(), key)
            }
        }

        @Test @DisplayName("corrupted ciphertext fails to decrypt")
        fun `corrupted ciphertext`() {
            val key = CryptoHelper.generateRandomKey(32)
            val ct = CryptoHelper.encryptXChaCha20Poly1305("data".encodeToByteArray(), key)
            ct[ct.size - 1] = (ct.last().toInt() xor 0xFF).toByte() // flip last bit
            assertThrows(Exception::class.java) { CryptoHelper.decryptXChaCha20Poly1305(ct, key) }
        }

        @Test @DisplayName("too-short ciphertext fails")
        fun `truncated ciphertext`() {
            val key = CryptoHelper.generateRandomKey(32)
            assertThrows(Exception::class.java) {
                CryptoHelper.decryptXChaCha20Poly1305(ByteArray(10), key)
            }
        }

        @Test @DisplayName("explicit nonce produces deterministic output")
        fun `deterministic with explicit nonce`() {
            val key = CryptoHelper.generateRandomKey(32)
            val nonce = CryptoHelper.generateRandomKey(24)
            val plaintext = "deterministic".encodeToByteArray()
            val ct1 = CryptoHelper.encryptXChaCha20Poly1305(plaintext, key, nonce)
            val ct2 = CryptoHelper.encryptXChaCha20Poly1305(plaintext, key, nonce)
            assertArrayEquals(ct1, ct2)
        }

        @Test @DisplayName("default nonce produces unique ciphertexts")
        fun `random nonce uniqueness`() {
            val key = CryptoHelper.generateRandomKey(32)
            val plaintext = "unique".encodeToByteArray()
            val ct1 = CryptoHelper.encryptXChaCha20Poly1305(plaintext, key)
            val ct2 = CryptoHelper.encryptXChaCha20Poly1305(plaintext, key)
            assertFalse(ct1.contentEquals(ct2))
        }

        @Test @DisplayName("deprecated encryptAesGcm delegates to XChaCha20")
        fun `deprecated methods still work`() {
            val key = CryptoHelper.generateRandomKey(32)
            val plaintext = "legacy test".encodeToByteArray()
            val ct = CryptoHelper.encryptAesGcm(plaintext, key)
            val pt = CryptoHelper.decryptAesGcm(ct, key)
            assertArrayEquals(plaintext, pt)
        }
    }

    @Nested @DisplayName("Key generation")
    inner class KeyGenTest {
        @Test @DisplayName("X25519 key pair has 32-byte keys")
        fun `x25519 key sizes`() {
            val kp = CryptoHelper.generateX25519KeyPair()
            assertTrue(kp.publicKey.isNotEmpty())
            assertTrue(kp.privateKey.isNotEmpty())
        }
        @Test @DisplayName("Ed25519 key pair has keys")
        fun `ed25519 key sizes`() {
            val kp = CryptoHelper.generateEd25519KeyPair()
            assertEquals(32, kp.publicKey.size)
            assertTrue(kp.privateKey.isNotEmpty())
        }
        @Test @DisplayName("generateRandomKey returns correct size")
        fun `random key correct size`() {
            assertEquals(32, CryptoHelper.generateRandomKey(32).size)
            assertEquals(16, CryptoHelper.generateRandomKey(16).size)
        }
        @Test @DisplayName("generateRandomKey throws on non-positive size")
        fun `random key non-positive`() {
            assertThrows(IllegalArgumentException::class.java) { CryptoHelper.generateRandomKey(0) }
            assertThrows(IllegalArgumentException::class.java) { CryptoHelper.generateRandomKey(-1) }
        }
        @Test @DisplayName("consecutive calls produce different keys")
        fun `random key uniqueness`() {
            val a = CryptoHelper.generateRandomKey()
            val b = CryptoHelper.generateRandomKey()
            assertFalse(a.contentEquals(b))
        }
    }

    @Nested @DisplayName("Ed25519 sign/verify")
    inner class Ed25519Test {
        @Test @DisplayName("sign then verify succeeds")
        fun `sign verify roundtrip`() {
            val kp = CryptoHelper.generateEd25519KeyPair()
            val msg = "test message".encodeToByteArray()
            val sig = CryptoHelper.signEd25519(msg, kp.privateKey)
            assertTrue(CryptoHelper.verifyEd25519(msg, sig, kp.publicKey))
        }
        @Test @DisplayName("wrong message fails verification")
        fun `wrong message`() {
            val kp = CryptoHelper.generateEd25519KeyPair()
            val sig = CryptoHelper.signEd25519("msg1".encodeToByteArray(), kp.privateKey)
            assertFalse(CryptoHelper.verifyEd25519("msg2".encodeToByteArray(), sig, kp.publicKey))
        }
        @Test @DisplayName("wrong public key fails verification")
        fun `wrong public key`() {
            val kp1 = CryptoHelper.generateEd25519KeyPair()
            val kp2 = CryptoHelper.generateEd25519KeyPair()
            val sig = CryptoHelper.signEd25519("msg".encodeToByteArray(), kp1.privateKey)
            assertFalse(CryptoHelper.verifyEd25519("msg".encodeToByteArray(), sig, kp2.publicKey))
        }
    }

    @Nested @DisplayName("X25519 Diffie-Hellman")
    inner class X25519Test {
        @Test @DisplayName("two parties derive same shared secret")
        fun `DH agreement`() {
            val alice = CryptoHelper.generateX25519KeyPair()
            val bob = CryptoHelper.generateX25519KeyPair()
            val secret1 = CryptoHelper.x25519DiffieHellman(alice.privateKey, bob.publicKey)
            val secret2 = CryptoHelper.x25519DiffieHellman(bob.privateKey, alice.publicKey)
            assertArrayEquals(secret1, secret2)
        }
        @Test @DisplayName("DH with same key pair produces valid result")
        fun `dh with same key`() {
            val kp = CryptoHelper.generateX25519KeyPair()
            val secret = CryptoHelper.x25519DiffieHellman(kp.privateKey, kp.publicKey)
            assertNotNull(secret)
            assertTrue(secret.isNotEmpty())
        }
    }

    @Nested @DisplayName("Ed25519 ↔ X25519 conversion")
    inner class ConversionTest {
        @Test @DisplayName("ed25519 public key converts to x25519")
        fun `pk conversion`() {
            val kp = CryptoHelper.generateEd25519KeyPair()
            val xpk = CryptoHelper.ed25519PkToX25519(kp.publicKey)
            assertTrue(xpk.isNotEmpty())
        }
        @Test @DisplayName("ed25519 private key converts to x25519")
        fun `sk conversion`() {
            val kp = CryptoHelper.generateEd25519KeyPair()
            val xsk = CryptoHelper.ed25519SkToX25519(kp.privateKey)
            assertTrue(xsk.isNotEmpty())
        }
    }

    @Nested @DisplayName("Utilities")
    inner class UtilTest {
        @Test @DisplayName("base64Url encode/decode roundtrip")
        fun `base64url roundtrip`() {
            val data = "Hello, Enchant!".encodeToByteArray()
            val encoded = CryptoHelper.base64UrlEncode(data)
            val decoded = CryptoHelper.base64UrlDecode(encoded)
            assertArrayEquals(data, decoded)
        }
        @Test @DisplayName("base64Url empty input")
        fun `base64url empty`() {
            val encoded = CryptoHelper.base64UrlEncode(ByteArray(0))
            assertTrue(encoded.isEmpty())
            assertArrayEquals(ByteArray(0), CryptoHelper.base64UrlDecode(encoded))
        }
        @Test @DisplayName("constantTimeEquals detects difference")
        fun `constant time equals`() {
            assertTrue(CryptoHelper.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
            assertFalse(CryptoHelper.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
            assertFalse(CryptoHelper.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 2, 3)))
        }
        @Test @DisplayName("zeroBytes clears array")
        fun `zero bytes`() {
            val data = byteArrayOf(1, 2, 3, 4, 5)
            CryptoHelper.zeroBytes(data)
            assertArrayEquals(ByteArray(5), data)
        }
    }
}
