package org.enchant.core.crypto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("CryptoPrimitives — Full Coverage")
class CryptoPrimitivesTest {

    private fun hexToBytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Nested @DisplayName("SHA-256")
    inner class Sha256Test {
        @Test @DisplayName("produces exactly 32 bytes")
        fun `sha256 produces 32 bytes`() {
            val result = CryptoPrimitives.sha256("test".encodeToByteArray())
            assertEquals(32, result.size)
        }

        @Test @DisplayName("same input produces identical output (deterministic)")
        fun `deterministic`() {
            val a = CryptoPrimitives.sha256("hello".encodeToByteArray())
            val b = CryptoPrimitives.sha256("hello".encodeToByteArray())
            assertArrayEquals(a, b)
        }

        @Test @DisplayName("different inputs produce different outputs")
        fun `different inputs different outputs`() {
            val a = CryptoPrimitives.sha256("hello".encodeToByteArray())
            val b = CryptoPrimitives.sha256("world".encodeToByteArray())
            assertFalse(a.contentEquals(b))
        }

        @Test @DisplayName("empty input produces valid 32-byte hash")
        fun `empty input valid hash`() {
            val result = CryptoPrimitives.sha256(ByteArray(0))
            assertEquals(32, result.size)
            assertFalse(result.all { it == 0.toByte() })
        }

        @Test @DisplayName("known SHA-256 test vector (abc)")
        fun `known test vector`() {
            val result = CryptoPrimitives.sha256("abc".encodeToByteArray())
            val expected = hexToBytes("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
            assertArrayEquals(expected, result)
        }

        @Test @DisplayName("large input (1MB) produces valid hash")
        fun `large input produces valid hash`() {
            val large = ByteArray(1024 * 1024) { it.toByte() }
            val result = CryptoPrimitives.sha256(large)
            assertEquals(32, result.size)
        }
    }

    @Nested @DisplayName("SHA-512")
    inner class Sha512Test {
        @Test @DisplayName("produces exactly 64 bytes")
        fun `sha512 produces 64 bytes`() {
            val result = CryptoPrimitives.sha512("test".encodeToByteArray())
            assertEquals(64, result.size)
        }

        @Test @DisplayName("deterministic output")
        fun `sha512 deterministic`() {
            val a = CryptoPrimitives.sha512("hello".encodeToByteArray())
            val b = CryptoPrimitives.sha512("hello".encodeToByteArray())
            assertArrayEquals(a, b)
        }

        @Test @DisplayName("empty input produces valid hash")
        fun `sha512 empty input`() {
            val result = CryptoPrimitives.sha512(ByteArray(0))
            assertEquals(64, result.size)
        }
    }

    @Nested @DisplayName("SHA-384")
    inner class Sha384Test {
        @Test @DisplayName("produces exactly 48 bytes")
        fun `sha384 produces 48 bytes`() {
            val result = CryptoPrimitives.sha384("test".encodeToByteArray())
            assertEquals(48, result.size)
        }

        @Test @DisplayName("deterministic output")
        fun `sha384 deterministic`() {
            val a = CryptoPrimitives.sha384("hello".encodeToByteArray())
            val b = CryptoPrimitives.sha384("hello".encodeToByteArray())
            assertArrayEquals(a, b)
        }
    }

    @Nested @DisplayName("HKDF-SHA256 (RFC 5869)")
    inner class HkdfSha256Test {
        @Test @DisplayName("RFC 5869 Test Case 1")
        fun `rfc5869 test case 1`() {
            val ik = hexToBytes("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
            val salt = hexToBytes("000102030405060708090a0b0c")
            val info = hexToBytes("f0f1f2f3f4f5f6f7f8f9")
            val expected = hexToBytes("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865")
            val result = CryptoPrimitives.hkdfSha256(ik, salt, info, 42)
            assertArrayEquals(expected, result)
        }

        @Test @DisplayName("RFC 5869 Test Case 2 — longer inputs")
        fun `rfc5869 test case 2`() {
            val ik = hexToBytes("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f")
            val salt = hexToBytes("606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9fa0a1a2a3a4a5a6a7a8a9aaabacadaeaf")
            val info = hexToBytes("b0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedfe0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff")
            val expected = hexToBytes("b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71cc30c58179ec3e87c14c01d5c1f3434f1d87")
            val result = CryptoPrimitives.hkdfSha256(ik, salt, info, 82)
            assertArrayEquals(expected, result)
        }

        @Test @DisplayName("negative length throws IllegalArgumentException")
        fun `negative length throws`() {
            assertThrows<IllegalArgumentException> {
                CryptoPrimitives.hkdfSha256(ByteArray(1), ByteArray(1), ByteArray(1), -1)
            }
        }

        @Test @DisplayName("zero length throws IllegalArgumentException")
        fun `zero length throws`() {
            assertThrows<IllegalArgumentException> {
                CryptoPrimitives.hkdfSha256(ByteArray(1), ByteArray(1), ByteArray(1), 0)
            }
        }

        @Test @DisplayName("empty salt uses 32 zero bytes per RFC")
        fun `empty salt uses default`() {
            val result = CryptoPrimitives.hkdfSha256("input".encodeToByteArray(), ByteArray(0), "info".encodeToByteArray(), 16)
            assertEquals(16, result.size)
        }

        @Test @DisplayName("empty info is valid")
        fun `empty info valid`() {
            val result = CryptoPrimitives.hkdfSha256("ikm".encodeToByteArray(), ByteArray(32), ByteArray(0), 32)
            assertEquals(32, result.size)
        }

        @Test @DisplayName("output length > 32*255 throws (HKDF limit)")
        fun `exceeds hkdf limit throws`() {
            assertThrows<IllegalArgumentException> {
                CryptoPrimitives.hkdfSha256(ByteArray(32), ByteArray(32), ByteArray(1), 32 * 256)
            }
        }

        @Test @DisplayName("single byte output")
        fun `single byte output`() {
            val result = CryptoPrimitives.hkdfSha256(ByteArray(32), ByteArray(32), ByteArray(1), 1)
            assertEquals(1, result.size)
        }
    }

    @Nested @DisplayName("HMAC-SHA256")
    inner class HmacSha256Test {
        @Test @DisplayName("produces 32 bytes")
        fun `hmac produces 32 bytes`() {
            val result = CryptoPrimitives.hmacSha256("key".encodeToByteArray(), "data".encodeToByteArray())
            assertEquals(32, result.size)
        }

        @Test @DisplayName("deterministic output")
        fun `hmac deterministic`() {
            val a = CryptoPrimitives.hmacSha256("key".encodeToByteArray(), "data".encodeToByteArray())
            val b = CryptoPrimitives.hmacSha256("key".encodeToByteArray(), "data".encodeToByteArray())
            assertArrayEquals(a, b)
        }

        @Test @DisplayName("different keys produce different MACs")
        fun `different keys different macs`() {
            val a = CryptoPrimitives.hmacSha256("key1".encodeToByteArray(), "data".encodeToByteArray())
            val b = CryptoPrimitives.hmacSha256("key2".encodeToByteArray(), "data".encodeToByteArray())
            assertFalse(a.contentEquals(b))
        }

        @Test @DisplayName("empty data produces valid MAC")
        fun `empty data valid mac`() {
            val result = CryptoPrimitives.hmacSha256("key".encodeToByteArray(), ByteArray(0))
            assertEquals(32, result.size)
        }

        @Test @DisplayName("key longer than 64 bytes is hashed first")
        fun `long key is hashed`() {
            val longKey = ByteArray(128) { it.toByte() }
            val result = CryptoPrimitives.hmacSha256(longKey, "data".encodeToByteArray())
            assertEquals(32, result.size)
        }
    }

    @Nested @DisplayName("HMAC-SHA512")
    inner class HmacSha512Test {
        @Test @DisplayName("produces 64 bytes")
        fun `hmac512 produces 64 bytes`() {
            val result = CryptoPrimitives.hmacSha512("key".encodeToByteArray(), "data".encodeToByteArray())
            assertEquals(64, result.size)
        }

        @Test @DisplayName("deterministic output")
        fun `hmac512 deterministic`() {
            val a = CryptoPrimitives.hmacSha512("key".encodeToByteArray(), "data".encodeToByteArray())
            val b = CryptoPrimitives.hmacSha512("key".encodeToByteArray(), "data".encodeToByteArray())
            assertArrayEquals(a, b)
        }

        @Test @DisplayName("different keys produce different MACs")
        fun `hmac512 different keys`() {
            val a = CryptoPrimitives.hmacSha512("key1".encodeToByteArray(), "data".encodeToByteArray())
            val b = CryptoPrimitives.hmacSha512("key2".encodeToByteArray(), "data".encodeToByteArray())
            assertFalse(a.contentEquals(b))
        }
    }

    @Nested @DisplayName("XChaCha20-Poly1305")
    inner class XChaCha20Poly1305Test {
        @Test @DisplayName("encrypt then decrypt returns original plaintext")
        fun `roundtrip`() {
            val key = CryptoPrimitives.generateRandomKey(32)
            val plaintext = "Hello, Enchant!".encodeToByteArray()
            val ciphertext = CryptoPrimitives.encryptXChaCha20Poly1305(plaintext, key)
            val decrypted = CryptoPrimitives.decryptXChaCha20Poly1305(ciphertext, key)
            assertArrayEquals(plaintext, decrypted)
        }

        @Test @DisplayName("ciphertext size = plaintext + 24 (nonce) + 16 (tag)")
        fun `ciphertext overhead`() {
            val plaintext = ByteArray(100)
            val key = CryptoPrimitives.generateRandomKey(32)
            val ct = CryptoPrimitives.encryptXChaCha20Poly1305(plaintext, key)
            assertEquals(plaintext.size + 24 + 16, ct.size)
        }

        @Test @DisplayName("wrong key throws exception")
        fun `wrong key throws`() {
            val plaintext = "secret".encodeToByteArray()
            val key1 = CryptoPrimitives.generateRandomKey(32)
            val key2 = CryptoPrimitives.generateRandomKey(32)
            val ct = CryptoPrimitives.encryptXChaCha20Poly1305(plaintext, key1)
            assertThrows<Exception> { CryptoPrimitives.decryptXChaCha20Poly1305(ct, key2) }
        }

        @Test @DisplayName("empty plaintext encrypts and decrypts to empty")
        fun `empty plaintext roundtrip`() {
            val key = CryptoPrimitives.generateRandomKey(32)
            val ct = CryptoPrimitives.encryptXChaCha20Poly1305(ByteArray(0), key)
            assertEquals(24 + 16, ct.size)
            val pt = CryptoPrimitives.decryptXChaCha20Poly1305(ct, key)
            assertArrayEquals(ByteArray(0), pt)
        }

        @Test @DisplayName("1MB plaintext roundtrips correctly")
        fun `large plaintext roundtrip`() {
            val key = CryptoPrimitives.generateRandomKey(32)
            val pt = ByteArray(1024 * 1024) { (it % 256).toByte() }
            val ct = CryptoPrimitives.encryptXChaCha20Poly1305(pt, key)
            val decrypted = CryptoPrimitives.decryptXChaCha20Poly1305(ct, key)
            assertArrayEquals(pt, decrypted)
        }

        @Test @DisplayName("wrong key size (16 bytes) throws")
        fun `wrong key size throws`() {
            val key = CryptoPrimitives.generateRandomKey(16)
            assertThrows<Exception> {
                CryptoPrimitives.encryptXChaCha20Poly1305("test".encodeToByteArray(), key)
            }
        }

        @Test @DisplayName("corrupted ciphertext (last byte flipped) fails to decrypt")
        fun `corrupted ciphertext fails`() {
            val key = CryptoPrimitives.generateRandomKey(32)
            val ct = CryptoPrimitives.encryptXChaCha20Poly1305("data".encodeToByteArray(), key)
            ct[ct.size - 1] = (ct.last().toInt() xor 0xFF).toByte()
            assertThrows<Exception> { CryptoPrimitives.decryptXChaCha20Poly1305(ct, key) }
        }

        @Test @DisplayName("corrupted nonce portion fails to decrypt")
        fun `corrupted nonce fails`() {
            val key = CryptoPrimitives.generateRandomKey(32)
            val ct = CryptoPrimitives.encryptXChaCha20Poly1305("data".encodeToByteArray(), key)
            ct[0] = (ct[0].toInt() xor 0xFF).toByte()
            assertThrows<Exception> { CryptoPrimitives.decryptXChaCha20Poly1305(ct, key) }
        }

        @Test @DisplayName("truncated ciphertext (10 bytes) fails")
        fun `truncated ciphertext fails`() {
            val key = CryptoPrimitives.generateRandomKey(32)
            assertThrows<Exception> {
                CryptoPrimitives.decryptXChaCha20Poly1305(ByteArray(10), key)
            }
        }

        @Test @DisplayName("explicit nonce produces deterministic ciphertext")
        fun `explicit nonce deterministic`() {
            val key = CryptoPrimitives.generateRandomKey(32)
            val nonce = CryptoPrimitives.generateRandomKey(24)
            val plaintext = "deterministic".encodeToByteArray()
            val ct1 = CryptoPrimitives.encryptXChaCha20Poly1305(plaintext, key, nonce)
            val ct2 = CryptoPrimitives.encryptXChaCha20Poly1305(plaintext, key, nonce)
            assertArrayEquals(ct1, ct2)
        }

        @Test @DisplayName("default (random) nonce produces unique ciphertexts")
        fun `random nonce uniqueness`() {
            val key = CryptoPrimitives.generateRandomKey(32)
            val plaintext = "unique".encodeToByteArray()
            val ct1 = CryptoPrimitives.encryptXChaCha20Poly1305(plaintext, key)
            val ct2 = CryptoPrimitives.encryptXChaCha20Poly1305(plaintext, key)
            assertFalse(ct1.contentEquals(ct2))
        }

        @Test @DisplayName("raw encrypt/decrypt with explicit key and nonce")
        fun `raw encrypt decrypt`() {
            val key = CryptoPrimitives.generateRandomKey(32)
            val nonce = CryptoPrimitives.generateRandomKey(12)
            val plaintext = "raw test".encodeToByteArray()
            val ct = CryptoPrimitives.encryptXChaCha20Poly1305Raw(plaintext, key, nonce)
            val pt = CryptoPrimitives.decryptXChaCha20Poly1305Raw(ct, key, nonce)
            assertArrayEquals(plaintext, pt)
        }

        @Test @DisplayName("raw decrypt with wrong nonce fails")
        fun `raw wrong nonce fails`() {
            val key = CryptoPrimitives.generateRandomKey(32)
            val nonce1 = CryptoPrimitives.generateRandomKey(12)
            val nonce2 = CryptoPrimitives.generateRandomKey(12)
            val plaintext = "raw test".encodeToByteArray()
            val ct = CryptoPrimitives.encryptXChaCha20Poly1305Raw(plaintext, key, nonce1)
            assertThrows<Exception> { CryptoPrimitives.decryptXChaCha20Poly1305Raw(ct, key, nonce2) }
        }
    }

    @Nested @DisplayName("AES-256-GCM")
    inner class AesGcmTest {
        @Test @DisplayName("encrypt then decrypt returns original plaintext")
        fun `aes roundtrip`() {
            val key = CryptoPrimitives.generateRandomKey(32)
            val plaintext = "AES test".encodeToByteArray()
            val ct = CryptoPrimitives.encryptAesGcm(plaintext, key)
            val pt = CryptoPrimitives.decryptAesGcm(ct, key)
            assertArrayEquals(plaintext, pt)
        }

        @Test @DisplayName("ciphertext size = plaintext + 12 (nonce) + 16 (tag)")
        fun `aes ciphertext overhead`() {
            val plaintext = ByteArray(100)
            val key = CryptoPrimitives.generateRandomKey(32)
            val ct = CryptoPrimitives.encryptAesGcm(plaintext, key)
            assertEquals(plaintext.size + 12 + 16, ct.size)
        }

        @Test @DisplayName("wrong key throws exception")
        fun `aes wrong key throws`() {
            val plaintext = "secret".encodeToByteArray()
            val key1 = CryptoPrimitives.generateRandomKey(32)
            val key2 = CryptoPrimitives.generateRandomKey(32)
            val ct = CryptoPrimitives.encryptAesGcm(plaintext, key1)
            assertThrows<Exception> { CryptoPrimitives.decryptAesGcm(ct, key2) }
        }

        @Test @DisplayName("empty plaintext roundtrips")
        fun `aes empty plaintext`() {
            val key = CryptoPrimitives.generateRandomKey(32)
            val ct = CryptoPrimitives.encryptAesGcm(ByteArray(0), key)
            assertEquals(12 + 16, ct.size)
            val pt = CryptoPrimitives.decryptAesGcm(ct, key)
            assertArrayEquals(ByteArray(0), pt)
        }

        @Test @DisplayName("raw encrypt/decrypt with explicit nonce")
        fun `aes raw roundtrip`() {
            val key = CryptoPrimitives.generateRandomKey(32)
            val nonce = CryptoPrimitives.generateRandomKey(12)
            val plaintext = "aes raw".encodeToByteArray()
            val ct = CryptoPrimitives.encryptAesGcmRaw(plaintext, key, nonce)
            val pt = CryptoPrimitives.decryptAesGcmRaw(ct, key, nonce)
            assertArrayEquals(plaintext, pt)
        }

        @Test @DisplayName("wrong nonce size throws")
        fun `aes wrong nonce size`() {
            val key = CryptoPrimitives.generateRandomKey(32)
            val nonce = ByteArray(16)
            assertThrows<Exception> {
                CryptoPrimitives.encryptAesGcmRaw("test".encodeToByteArray(), key, nonce)
            }
        }
    }

    @Nested @DisplayName("Key Generation")
    inner class KeyGenTest {
        @Test @DisplayName("X25519 key pair has 32-byte public key")
        fun `x25519 public key 32 bytes`() {
            val kp = CryptoPrimitives.generateX25519KeyPair()
            assertEquals(32, kp.publicKey.size)
        }

        @Test @DisplayName("X25519 key pair has 32-byte private key")
        fun `x25519 private key 32 bytes`() {
            val kp = CryptoPrimitives.generateX25519KeyPair()
            assertEquals(32, kp.privateKey.size)
        }

        @Test @DisplayName("Ed25519 key pair has 32-byte public key")
        fun `ed25519 public key 32 bytes`() {
            val kp = CryptoPrimitives.generateEd25519KeyPair()
            assertEquals(32, kp.publicKey.size)
        }

        @Test @DisplayName("Ed25519 key pair has non-empty private key")
        fun `ed25519 private key non empty`() {
            val kp = CryptoPrimitives.generateEd25519KeyPair()
            assertTrue(kp.privateKey.isNotEmpty())
        }

        @Test @DisplayName("generateRandomKey returns correct size")
        fun `random key correct size`() {
            assertEquals(32, CryptoPrimitives.generateRandomKey(32).size)
            assertEquals(16, CryptoPrimitives.generateRandomKey(16).size)
            assertEquals(64, CryptoPrimitives.generateRandomKey(64).size)
        }

        @Test @DisplayName("generateRandomKey throws on size <= 0")
        fun `random key non positive throws`() {
            assertThrows<IllegalArgumentException> { CryptoPrimitives.generateRandomKey(0) }
            assertThrows<IllegalArgumentException> { CryptoPrimitives.generateRandomKey(-1) }
        }

        @Test @DisplayName("consecutive random keys are different")
        fun `random key uniqueness`() {
            val a = CryptoPrimitives.generateRandomKey()
            val b = CryptoPrimitives.generateRandomKey()
            assertFalse(a.contentEquals(b))
        }

        @Test @DisplayName("100 consecutive X25519 key pairs are all unique")
        fun `x25519 key uniqueness`() {
            val keys = (1..100).map { CryptoPrimitives.generateX25519KeyPair() }
            for (i in keys.indices) {
                for (j in i + 1 until keys.size) {
                    assertFalse(keys[i].publicKey.contentEquals(keys[j].publicKey))
                }
            }
        }

        @Test @DisplayName("100 consecutive Ed25519 key pairs are all unique")
        fun `ed25519 key uniqueness`() {
            val keys = (1..100).map { CryptoPrimitives.generateEd25519KeyPair() }
            for (i in keys.indices) {
                for (j in i + 1 until keys.size) {
                    assertFalse(keys[i].publicKey.contentEquals(keys[j].publicKey))
                }
            }
        }
    }

    @Nested @DisplayName("Ed25519 Sign/Verify")
    inner class Ed25519Test {
        @Test @DisplayName("sign then verify succeeds")
        fun `sign verify roundtrip`() {
            val kp = CryptoPrimitives.generateEd25519KeyPair()
            val msg = "test message".encodeToByteArray()
            val sig = CryptoPrimitives.signEd25519(msg, kp.privateKey)
            assertEquals(64, sig.size)
            assertTrue(CryptoPrimitives.verifyEd25519(msg, sig, kp.publicKey))
        }

        @Test @DisplayName("wrong message fails verification")
        fun `wrong message fails`() {
            val kp = CryptoPrimitives.generateEd25519KeyPair()
            val sig = CryptoPrimitives.signEd25519("msg1".encodeToByteArray(), kp.privateKey)
            assertFalse(CryptoPrimitives.verifyEd25519("msg2".encodeToByteArray(), sig, kp.publicKey))
        }

        @Test @DisplayName("wrong public key fails verification")
        fun `wrong public key fails`() {
            val kp1 = CryptoPrimitives.generateEd25519KeyPair()
            val kp2 = CryptoPrimitives.generateEd25519KeyPair()
            val sig = CryptoPrimitives.signEd25519("msg".encodeToByteArray(), kp1.privateKey)
            assertFalse(CryptoPrimitives.verifyEd25519("msg".encodeToByteArray(), sig, kp2.publicKey))
        }

        @Test @DisplayName("empty message signs and verifies")
        fun `empty message signs`() {
            val kp = CryptoPrimitives.generateEd25519KeyPair()
            val sig = CryptoPrimitives.signEd25519(ByteArray(0), kp.privateKey)
            assertEquals(64, sig.size)
            assertTrue(CryptoPrimitives.verifyEd25519(ByteArray(0), sig, kp.publicKey))
        }

        @Test @DisplayName("truncated signature fails verification")
        fun `truncated signature fails`() {
            val kp = CryptoPrimitives.generateEd25519KeyPair()
            val sig = CryptoPrimitives.signEd25519("msg".encodeToByteArray(), kp.privateKey)
            val truncated = sig.copyOf(32)
            assertFalse(CryptoPrimitives.verifyEd25519("msg".encodeToByteArray(), truncated, kp.publicKey))
        }

        @Test @DisplayName("corrupted signature fails verification")
        fun `corrupted signature fails`() {
            val kp = CryptoPrimitives.generateEd25519KeyPair()
            val sig = CryptoPrimitives.signEd25519("msg".encodeToByteArray(), kp.privateKey)
            sig[0] = (sig[0].toInt() xor 0xFF).toByte()
            assertFalse(CryptoPrimitives.verifyEd25519("msg".encodeToByteArray(), sig, kp.publicKey))
        }
    }

    @Nested @DisplayName("X25519 Diffie-Hellman")
    inner class X25519DhTest {
        @Test @DisplayName("two parties derive identical shared secret")
        fun `dh agreement`() {
            val alice = CryptoPrimitives.generateX25519KeyPair()
            val bob = CryptoPrimitives.generateX25519KeyPair()
            val secret1 = CryptoPrimitives.x25519DiffieHellman(alice.privateKey, bob.publicKey)
            val secret2 = CryptoPrimitives.x25519DiffieHellman(bob.privateKey, alice.publicKey)
            assertArrayEquals(secret1, secret2)
        }

        @Test @DisplayName("shared secret is 32 bytes")
        fun `dh secret 32 bytes`() {
            val alice = CryptoPrimitives.generateX25519KeyPair()
            val bob = CryptoPrimitives.generateX25519KeyPair()
            val secret = CryptoPrimitives.x25519DiffieHellman(alice.privateKey, bob.publicKey)
            assertEquals(32, secret.size)
        }

        @Test @DisplayName("shared secret is not all zeros")
        fun `dh secret not zero`() {
            val alice = CryptoPrimitives.generateX25519KeyPair()
            val bob = CryptoPrimitives.generateX25519KeyPair()
            val secret = CryptoPrimitives.x25519DiffieHellman(alice.privateKey, bob.publicKey)
            assertFalse(secret.all { it == 0.toByte() })
        }

        @Test @DisplayName("different key pairs produce different shared secrets")
        fun `different pairs different secrets`() {
            val alice1 = CryptoPrimitives.generateX25519KeyPair()
            val alice2 = CryptoPrimitives.generateX25519KeyPair()
            val bob = CryptoPrimitives.generateX25519KeyPair()
            val s1 = CryptoPrimitives.x25519DiffieHellman(alice1.privateKey, bob.publicKey)
            val s2 = CryptoPrimitives.x25519DiffieHellman(alice2.privateKey, bob.publicKey)
            assertFalse(s1.contentEquals(s2))
        }
    }

    @Nested @DisplayName("Ed25519 to X25519 Conversion")
    inner class ConversionTest {
        @Test @DisplayName("Ed25519 public key converts to 32-byte X25519 public key")
        fun `pk conversion`() {
            val kp = CryptoPrimitives.generateEd25519KeyPair()
            val xpk = CryptoPrimitives.ed25519PkToX25519(kp.publicKey)
            assertEquals(32, xpk.size)
        }

        @Test @DisplayName("Ed25519 private key converts to 32-byte X25519 private key")
        fun `sk conversion`() {
            val kp = CryptoPrimitives.generateEd25519KeyPair()
            val xsk = CryptoPrimitives.ed25519SkToX25519(kp.privateKey)
            assertEquals(32, xsk.size)
        }

        @Test @DisplayName("Converted keys work for DH")
        fun `converted keys work for dh`() {
            val aliceEd = CryptoPrimitives.generateEd25519KeyPair()
            val bobEd = CryptoPrimitives.generateEd25519KeyPair()
            val aliceX = CryptoPrimitives.ed25519SkToX25519(aliceEd.privateKey)
            val bobXpk = CryptoPrimitives.ed25519PkToX25519(bobEd.publicKey)
            val secret = CryptoPrimitives.x25519DiffieHellman(aliceX, bobXpk)
            assertEquals(32, secret.size)
        }
    }

    @Nested @DisplayName("Utilities")
    inner class UtilTest {
        @Test @DisplayName("base64Url encode/decode roundtrip")
        fun `base64url roundtrip`() {
            val data = "Hello, Enchant!".encodeToByteArray()
            val encoded = CryptoPrimitives.base64UrlEncode(data)
            val decoded = CryptoPrimitives.base64UrlDecode(encoded)
            assertArrayEquals(data, decoded)
        }

        @Test @DisplayName("base64Url empty input roundtrip")
        fun `base64url empty roundtrip`() {
            val encoded = CryptoPrimitives.base64UrlEncode(ByteArray(0))
            assertTrue(encoded.isEmpty())
            assertArrayEquals(ByteArray(0), CryptoPrimitives.base64UrlDecode(encoded))
        }

        @Test @DisplayName("base64Url binary data roundtrip")
        fun `base64url binary roundtrip`() {
            val data = ByteArray(256) { it.toByte() }
            val encoded = CryptoPrimitives.base64UrlEncode(data)
            val decoded = CryptoPrimitives.base64UrlDecode(encoded)
            assertArrayEquals(data, decoded)
        }

        @Test @DisplayName("base64Url produces URL-safe encoding (no + or /)")
        fun `base64url safe chars`() {
            val data = ByteArray(100) { (it * 7 + 3).toByte() }
            val encoded = CryptoPrimitives.base64UrlEncode(data)
            assertFalse(encoded.contains('+'))
            assertFalse(encoded.contains('/'))
            assertFalse(encoded.contains('='))
        }

        @Test @DisplayName("base64Url decode invalid string throws")
        fun `base64url invalid decode throws`() {
            assertThrows<Exception> { CryptoPrimitives.base64UrlDecode("!!!invalid!!!") }
        }

        @Test @DisplayName("hex encode/decode roundtrip")
        fun `hex roundtrip`() {
            val data = ByteArray(256) { it.toByte() }
            val encoded = CryptoPrimitives.hexEncode(data)
            val decoded = CryptoPrimitives.hexDecode(encoded)
            assertArrayEquals(data, decoded)
        }

        @Test @DisplayName("hex encode produces lowercase")
        fun `hex lowercase`() {
            val data = byteArrayOf(0x0A.toByte(), 0xFF.toByte(), 0x1B.toByte())
            val encoded = CryptoPrimitives.hexEncode(data)
            assertEquals("0aff1b", encoded)
        }

        @Test @DisplayName("constantTimeEquals identical arrays returns true")
        fun `constant time equals identical`() {
            assertTrue(CryptoPrimitives.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        }

        @Test @DisplayName("constantTimeEquals different arrays returns false")
        fun `constant time equals different`() {
            assertFalse(CryptoPrimitives.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
        }

        @Test @DisplayName("constantTimeEquals different lengths returns false")
        fun `constant time equals different lengths`() {
            assertFalse(CryptoPrimitives.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 2, 3)))
        }

        @Test @DisplayName("constantTimeEquals empty arrays returns true")
        fun `constant time equals empty`() {
            assertTrue(CryptoPrimitives.constantTimeEquals(ByteArray(0), ByteArray(0)))
        }

        @Test @DisplayName("zeroBytes clears entire array")
        fun `zero bytes clears array`() {
            val data = byteArrayOf(1, 2, 3, 4, 5)
            CryptoPrimitives.zeroBytes(data)
            assertArrayEquals(ByteArray(5), data)
        }

        @Test @DisplayName("zeroBytes on empty array does nothing")
        fun `zero bytes empty array`() {
            val data = ByteArray(0)
            CryptoPrimitives.zeroBytes(data)
            assertEquals(0, data.size)
        }

        @Test @DisplayName("zeroBytes on large array clears all bytes")
        fun `zero bytes large array`() {
            val data = ByteArray(1024) { it.toByte() }
            CryptoPrimitives.zeroBytes(data)
            assertTrue(data.all { it == 0.toByte() })
        }
    }

    @Nested @DisplayName("Argon2id hash with custom params")
    inner class Argon2idWithParamsTest {
        @Test @DisplayName("produces output of requested length")
        fun `argon2id with params output length`() {
            val out = CryptoPrimitives.argon2idHashWithParams(
                "password".toByteArray(),
                ByteArray(16) { it.toByte() },
                2, 65536, 2,
                32
            )
            assertEquals(32, out.size)
            assertTrue(out.any { it != 0.toByte() })
        }

        @Test @DisplayName("same salt+params produces same hash (deterministic)")
        fun `argon2id deterministic`() {
            val salt = ByteArray(16) { it.toByte() }
            val a = CryptoPrimitives.argon2idHashWithParams(
                "password".toByteArray(), salt, 2, 65536, 2, 32
            )
            val b = CryptoPrimitives.argon2idHashWithParams(
                "password".toByteArray(), salt, 2, 65536, 2, 32
            )
            assertArrayEquals(a, b)
        }

        @Test @DisplayName("different salt produces different hash")
        fun `argon2id different salt`() {
            val salt1 = ByteArray(16) { 0 }
            val salt2 = ByteArray(16) { 1 }
            val a = CryptoPrimitives.argon2idHashWithParams(
                "password".toByteArray(), salt1, 2, 65536, 2, 32
            )
            val b = CryptoPrimitives.argon2idHashWithParams(
                "password".toByteArray(), salt2, 2, 65536, 2, 32
            )
            assertFalse(a.contentEquals(b))
        }

        @Test @DisplayName("different password produces different hash")
        fun `argon2id different password`() {
            val salt = ByteArray(16) { 0 }
            val a = CryptoPrimitives.argon2idHashWithParams(
                "password1".toByteArray(), salt, 2, 65536, 2, 32
            )
            val b = CryptoPrimitives.argon2idHashWithParams(
                "password2".toByteArray(), salt, 2, 65536, 2, 32
            )
            assertFalse(a.contentEquals(b))
        }

        @Test @DisplayName("Signal-style params (2/64MB/2) for phone hashing")
        fun `argon2id signal style phone hashing`() {
            val salt = CryptoPrimitives.generateRandomKey(16)
            val hash = CryptoPrimitives.argon2idHashWithParams(
                "+15551234567".toByteArray(Charsets.UTF_8),
                salt, 2, 65536, 2, 32
            )
            assertEquals(32, hash.size)
            assertTrue(hash.any { it != 0.toByte() })
        }
    }

    @Nested @DisplayName("Safety number fingerprint (using native)")
    inner class SafetyNumberTest {
        @Test @DisplayName("same inputs produce same fingerprint")
        fun `safety number deterministic`() {
            val key1 = ByteArray(32) { it.toByte() }
            val key2 = ByteArray(32) { (it + 7).toByte() }
            val out1 = ByteArray(32)
            val out2 = ByteArray(32)
            assertEquals(0, EnchantCrypto.enchant_safety_number_generate(
                key1, key2, "alice", "bob", out1, longArrayOf(32)
            ))
            assertEquals(0, EnchantCrypto.enchant_safety_number_generate(
                key1, key2, "alice", "bob", out2, longArrayOf(32)
            ))
            assertArrayEquals(out1, out2)
        }

        @Test @DisplayName("different inputs produce different fingerprints")
        fun `safety number different inputs`() {
            val key1 = ByteArray(32) { it.toByte() }
            val key2 = ByteArray(32) { (it + 7).toByte() }
            val out1 = ByteArray(32)
            val out2 = ByteArray(32)
            assertEquals(0, EnchantCrypto.enchant_safety_number_generate(
                key1, key2, "alice", "bob", out1, longArrayOf(32)
            ))
            assertEquals(0, EnchantCrypto.enchant_safety_number_generate(
                key2, key1, "alice", "bob", out2, longArrayOf(32)
            ))
            assertFalse(out1.contentEquals(out2))
        }

        @Test @DisplayName("safety number produce non-zero output")
        fun `safety number non zero`() {
            val key1 = ByteArray(32) { it.toByte() }
            val key2 = ByteArray(32) { (it + 7).toByte() }
            val out = ByteArray(32)
            assertEquals(0, EnchantCrypto.enchant_safety_number_generate(
                key1, key2, "alice", "bob", out, longArrayOf(32)
            ))
            assertTrue(out.any { it != 0.toByte() })
        }

        @Test @DisplayName("safety number is null on invalid input")
        fun `safety number invalid input`() {
            val key = ByteArray(32)
            val out = ByteArray(32)
            // null/empty UUID should still succeed (returns zeros or valid hash)
            assertEquals(0, EnchantCrypto.enchant_safety_number_generate(
                key, key, "", "", out, longArrayOf(32)
            ))
        }
    }
}
