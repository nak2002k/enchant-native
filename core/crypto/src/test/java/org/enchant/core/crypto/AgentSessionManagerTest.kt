package org.enchant.core.crypto

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("AgentSessionManager — Native Integration Tests")
class AgentSessionManagerTest {

    private lateinit var agent: AgentSessionManager

    @BeforeEach
    fun setUp() {
        agent = AgentSessionManager.create()
    }

    @AfterEach
    fun tearDown() {
        agent.close()
    }

    @Nested
    @DisplayName("Identity Generation")
    inner class IdentityGeneration {

        @Test
        fun `generateIdentity returns 32-byte keypair`() {
            val (pub, priv) = agent.generateIdentity()
            assertEquals(32, pub.size)
            assertEquals(32, priv.size)
        }

        @Test
        fun `generateIdentity produces different keys each time`() {
            val (pub1, _) = agent.generateIdentity()
            val (pub2, _) = agent.generateIdentity()
            assertArrayEquals(pub1, pub1)
            assertFalse(pub1.contentEquals(pub2))
        }

        @Test
        fun `public key is valid X25519 point`() {
            val (pub, _) = agent.generateIdentity()
            // X25519 public key must be 32 bytes and not all zeros
            assertFalse(pub.all { it == 0.toByte() })
        }
    }

    @Nested
    @DisplayName("DH Key Exchange")
    inner class KeyExchange {

        private lateinit var alicePriv: ByteArray
        private lateinit var alicePub: ByteArray
        private lateinit var bobPriv: ByteArray
        private lateinit var bobPub: ByteArray

        @BeforeEach
        fun generateKeys() {
            val alice = agent.generateIdentity()
            alicePub = alice.first
            alicePriv = alice.second

            val bob = agent.generateIdentity()
            bobPub = bob.first
            bobPriv = bob.second
        }

        @Test
        fun `initiateSession returns 32-byte shared secret and ephemeral`() {
            val (sharedSecret, ephemeral) = agent.initiateSession(alicePriv, bobPub)
            assertEquals(32, sharedSecret.size)
            assertEquals(32, ephemeral.size)
        }

        @Test
        fun `respondSession returns 32-byte shared secret`() {
            val (_, ephemeral) = agent.initiateSession(alicePriv, bobPub)
            val sharedSecret = agent.respondSession(bobPriv, alicePub, ephemeral)
            assertEquals(32, sharedSecret.size)
        }

        @Test
        fun `DH exchange produces matching shared secrets`() {
            val (sharedSecret1, ephemeral) = agent.initiateSession(alicePriv, bobPub)
            val sharedSecret2 = agent.respondSession(bobPriv, alicePub, ephemeral)
            assertArrayEquals(sharedSecret1, sharedSecret2)
        }

        @Test
        fun `initiateSession rejects wrong key sizes`() {
            assertThrows(IllegalArgumentException::class.java) {
                agent.initiateSession(ByteArray(16), bobPub)
            }
            assertThrows(IllegalArgumentException::class.java) {
                agent.initiateSession(alicePriv, ByteArray(16))
            }
        }

        @Test
        fun `respondSession rejects wrong key sizes`() {
            val (_, ephemeral) = agent.initiateSession(alicePriv, bobPub)
            assertThrows(IllegalArgumentException::class.java) {
                agent.respondSession(ByteArray(16), alicePub, ephemeral)
            }
            assertThrows(IllegalArgumentException::class.java) {
                agent.respondSession(bobPriv, ByteArray(16), ephemeral)
            }
            assertThrows(IllegalArgumentException::class.java) {
                agent.respondSession(bobPriv, alicePub, ByteArray(16))
            }
        }

        @Test
        fun `ephemeral key is different from identity key`() {
            val (_, ephemeral) = agent.initiateSession(alicePriv, bobPub)
            assertFalse(alicePriv.contentEquals(ephemeral))
            assertFalse(alicePub.contentEquals(ephemeral))
        }
    }

    @Nested
    @DisplayName("Encrypt / Decrypt")
    inner class EncryptDecrypt {

        private lateinit var sharedSecret: ByteArray

        @BeforeEach
        fun establishSession() {
            val alice = agent.generateIdentity()
            val bob = agent.generateIdentity()
            val (ss, ephemeral) = agent.initiateSession(alice.first, bob.first)
            sharedSecret = agent.respondSession(bob.second, alice.first, ephemeral)
        }

        @Test
        fun `encrypt produces ciphertext with nonce and tag overhead`() {
            val plaintext = "Hello, agent!".toByteArray()
            val ciphertext = agent.encrypt(sharedSecret, plaintext)
            // 24 nonce + plaintext + 16 tag
            assertEquals(plaintext.size + 24 + 16, ciphertext.size)
        }

        @Test
        fun `encrypt then decrypt roundtrip`() {
            val plaintext = "Hello, agent!".toByteArray()
            val ciphertext = agent.encrypt(sharedSecret, plaintext)
            val decrypted = agent.decrypt(sharedSecret, ciphertext)
            assertArrayEquals(plaintext, decrypted)
        }

        @Test
        fun `encrypt then decrypt with binary data`() {
            val plaintext = ByteArray(1024) { (it * 37).toByte() }
            val ciphertext = agent.encrypt(sharedSecret, plaintext)
            val decrypted = agent.decrypt(sharedSecret, ciphertext)
            assertArrayEquals(plaintext, decrypted)
        }

        @Test
        fun `encrypt then decrypt empty plaintext`() {
            val plaintext = ByteArray(0)
            val ciphertext = agent.encrypt(sharedSecret, plaintext)
            val decrypted = agent.decrypt(sharedSecret, ciphertext)
            assertArrayEquals(plaintext, decrypted)
        }

        @Test
        fun `different encryptions produce different ciphertexts`() {
            val plaintext = "same message".toByteArray()
            val ct1 = agent.encrypt(sharedSecret, plaintext)
            val ct2 = agent.encrypt(sharedSecret, plaintext)
            assertFalse(ct1.contentEquals(ct2))
        }

        @Test
        fun `decrypt fails with wrong shared secret`() {
            val alice = agent.generateIdentity()
            val bob = agent.generateIdentity()
            val (wrongSecret, _) = agent.initiateSession(alice.first, bob.first)

            val plaintext = "secret".toByteArray()
            val ciphertext = agent.encrypt(sharedSecret, plaintext)

            assertThrows(IllegalStateException::class.java) {
                agent.decrypt(wrongSecret, ciphertext)
            }
        }

        @Test
        fun `encrypt rejects wrong shared secret size`() {
            assertThrows(IllegalArgumentException::class.java) {
                agent.encrypt(ByteArray(16), "test".toByteArray())
            }
        }

        @Test
        fun `decrypt rejects wrong shared secret size`() {
            val plaintext = "test".toByteArray()
            val ciphertext = agent.encrypt(sharedSecret, plaintext)
            assertThrows(IllegalArgumentException::class.java) {
                agent.decrypt(ByteArray(16), ciphertext)
            }
        }
    }

    @Nested
    @DisplayName("Resource Management")
    inner class ResourceManagement {

        @Test
        fun `close is idempotent`() {
            agent.close()
            agent.close()
        }

        @Test
        fun `operations after close throw IllegalStateException`() {
            agent.close()
            assertThrows(IllegalStateException::class.java) {
                agent.generateIdentity()
            }
        }
    }

    @Nested
    @DisplayName("Full Agent Roundtrip")
    inner class FullRoundtrip {

        @Test
        fun `complete agent session lifecycle`() {
            // 1. Generate identities
            val (agentPub, agentPriv) = agent.generateIdentity()
            val (serverPub, serverPriv) = agent.generateIdentity()

            // 2. DH key exchange
            val (sharedSecret1, ephemeral) = agent.initiateSession(agentPriv, serverPub)
            val sharedSecret2 = agent.respondSession(serverPriv, agentPub, ephemeral)
            assertArrayEquals(sharedSecret1, sharedSecret2)

            // 3. Agent encrypts, server decrypts
            val message1 = "Hello from agent!".toByteArray()
            val ciphertext1 = agent.encrypt(sharedSecret1, message1)
            val decrypted1 = agent.decrypt(sharedSecret2, ciphertext1)
            assertArrayEquals(message1, decrypted1)

            // 4. Server encrypts, agent decrypts
            val message2 = "Hello from server!".toByteArray()
            val ciphertext2 = agent.encrypt(sharedSecret2, message2)
            val decrypted2 = agent.decrypt(sharedSecret1, ciphertext2)
            assertArrayEquals(message2, decrypted2)

            // 5. Multiple messages
            for (i in 0..9) {
                val msg = "Message $i".toByteArray()
                val ct = agent.encrypt(sharedSecret1, msg)
                val pt = agent.decrypt(sharedSecret2, ct)
                assertArrayEquals(msg, pt, "Roundtrip failed for message $i")
            }
        }
    }
}
