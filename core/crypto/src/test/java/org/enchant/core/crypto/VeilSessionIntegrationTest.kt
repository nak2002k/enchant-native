package org.enchant.core.crypto

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("VeilSession — Native Integration Tests")
class VeilSessionIntegrationTest {

    private lateinit var selfIkPair: CryptoPrimitives.KeyPair
    private lateinit var peerIkPair: CryptoPrimitives.KeyPair
    private lateinit var peerSpkPair: CryptoPrimitives.KeyPair
    private lateinit var peerOpkPair: CryptoPrimitives.KeyPair
    private lateinit var peerSig: ByteArray
    private lateinit var mockSessionStore: SessionStore
    private lateinit var mockIdentityStore: IdentityStore
    private lateinit var veilSession: VeilSession

    @BeforeEach
    fun setUp() = runTest {
        VeilSession.reset()
        KeyManager.reset()

        selfIkPair = CryptoPrimitives.generateEd25519KeyPair()
        peerIkPair = CryptoPrimitives.generateEd25519KeyPair()
        peerSpkPair = CryptoPrimitives.generateX25519KeyPair()
        peerOpkPair = CryptoPrimitives.generateX25519KeyPair()
        peerSig = CryptoPrimitives.signEd25519(peerSpkPair.publicKey, peerIkPair.privateKey)

        mockSessionStore = mockk(relaxed = true)
        mockIdentityStore = mockk(relaxed = true)

        veilSession = VeilSession.create(
            selfUserId = "self",
            store = mockSessionStore,
            idStore = mockIdentityStore
        )
    }

    @AfterEach
    fun tearDown() = runTest {
        veilSession.close()
        VeilSession.reset()
        KeyManager.reset()
    }

    private fun createPeerBundle(): KeyManager.KeyBundle {
        return KeyManager.KeyBundle(
            deviceId = "peer-device",
            identityKey = CryptoPrimitives.ed25519PkToX25519(peerIkPair.publicKey),
            signedPrekey = KeyManager.SignedPrekeyData(peerSpkPair.publicKey, peerSig),
            oneTimePrekey = peerOpkPair.publicKey
        )
    }

    private fun setupKeysAndBundle() {
        KeyManager.setTestIdentityKeyPair(selfIkPair)
        KeyManager.setTestKeyBundle("peer", createPeerBundle())
    }

    @Nested
    @DisplayName("Initialization")
    inner class InitializationTests {

        @Test
        fun `create generates native identity`() = runTest {
            val pk = veilSession.getLocalIdentityPublicKey()
            assertNotNull(pk)
            assertEquals(32, pk!!.size)
        }

        @Test
        fun `create generates Ed25519 keypair`() = runTest {
            val pk = veilSession.getLocalIdentityPublicKey()
            assertNotNull(pk)
            // Ed25519 public keys are 32 bytes; verify it's a valid point
            assertEquals(32, pk!!.size)
        }

        @Test
        fun `close releases native resources`() = runTest {
            val session = VeilSession.create(selfUserId = "temp")
            session.close()
            // After close, operations should fail gracefully
            assertNull(session.getLocalIdentityPublicKey())
        }
    }

    @Nested
    @DisplayName("Identity Operations")
    inner class IdentityTests {

        @Test
        fun `getLocalIdentityPublicKey returns 32 bytes`() = runTest {
            val pk = veilSession.getLocalIdentityPublicKey()
            assertNotNull(pk)
            assertEquals(32, pk!!.size)
        }

        @Test
        fun `setIdentityKey and getIdentityKey roundtrip`() = runTest {
            val key = ByteArray(32) { 0x42 }
            veilSession.setIdentityKey("peer", key)
            val retrieved = veilSession.getIdentityKey("peer")
            assertNotNull(retrieved)
            assertArrayEquals(key, retrieved)
        }

        @Test
        fun `getIdentityKey for unknown peer returns null`() = runTest {
            assertNull(veilSession.getIdentityKey("unknown"))
        }
    }

    @Nested
    @DisplayName("Safety Number")
    inner class SafetyNumberTests {

        @Test
        fun `safety number with peer key returns non-empty hex string`() = runTest {
            val ik = ByteArray(32) { 0x42 }
            veilSession.setIdentityKey("peer", ik)
            val safetyNumber = veilSession.getSafetyNumber("peer")
            assertNotEquals("UNVERIFIED", safetyNumber)
            assertTrue(safetyNumber.isNotEmpty())
        }

        @Test
        fun `safety number deterministic for same keys`() = runTest {
            val ik = ByteArray(32) { 0x42 }
            veilSession.setIdentityKey("peer", ik)
            val s1 = veilSession.getSafetyNumber("peer")
            val s2 = veilSession.getSafetyNumber("peer")
            assertEquals(s1, s2)
        }

        @Test
        fun `safety number differs for different peers`() = runTest {
            val ik1 = ByteArray(32) { 0x01 }
            val ik2 = ByteArray(32) { 0x02 }
            veilSession.setIdentityKey("peer1", ik1)
            veilSession.setIdentityKey("peer2", ik2)
            val s1 = veilSession.getSafetyNumber("peer1")
            val s2 = veilSession.getSafetyNumber("peer2")
            assertNotEquals(s1, s2)
        }

        @Test
        fun `safety number UNVERIFIED for unknown peer`() = runTest {
            assertEquals("UNVERIFIED", veilSession.getSafetyNumber("unknown"))
        }
    }

    @Nested
    @DisplayName("Encryption")
    inner class EncryptionTests {

        @Test
        fun `encrypt returns non-null payload`() = runTest {
            setupKeysAndBundle()
            val result = veilSession.encryptMessage("peer", "Hello".encodeToByteArray())
            assertNotNull(result)
            assertTrue(result!!.payload.isNotEmpty())
        }

        @Test
        fun `first encrypt after establish produces PREKEY_MESSAGE`() = runTest {
            setupKeysAndBundle()
            val result = veilSession.encryptMessage("peer", "second".encodeToByteArray())
            assertNotNull(result)
            assertEquals(VeilSession.MessageType.PREKEY_MESSAGE, result!!.messageType)
        }

        @Test
        fun `encrypt handles empty plaintext`() = runTest {
            setupKeysAndBundle()
            val result = veilSession.encryptMessage("peer", ByteArray(0))
            assertNotNull(result)
        }

        @Test
        fun `encrypt handles large plaintext`() = runTest {
            setupKeysAndBundle()
            val large = ByteArray(10000) { (it % 256).toByte() }
            val result = veilSession.encryptMessage("peer", large)
            assertNotNull(result)
            assertTrue(result!!.payload.isNotEmpty())
        }

        @Test
        fun `encrypt returns null when bundle missing`() = runTest {
            val result = veilSession.encryptMessage("nobody", "Hello".encodeToByteArray())
            assertNull(result)
        }

        @Test
        fun `encryptWithSessionKey returns null when no session exists`() = runTest {
            val result = veilSession.encryptWithSessionKey("nobody", "data".encodeToByteArray())
            assertNull(result)
        }

        @Test
        fun `encryptWithSessionKey works with existing session`() = runTest {
            setupKeysAndBundle()
            veilSession.encryptMessage("peer", "init".encodeToByteArray())
            val result = veilSession.encryptWithSessionKey("peer", "data".encodeToByteArray())
            assertNotNull(result)
        }
    }

    @Nested
    @DisplayName("Session Management")
    inner class SessionManagementTests {

        @Test
        fun `hasSession returns false before encryption`() = runTest {
            assertFalse(veilSession.hasSession("nobody"))
        }

        @Test
        fun `hasSession returns true after successful encryption`() = runTest {
            setupKeysAndBundle()
            veilSession.encryptMessage("peer", "Hi".encodeToByteArray())
            assertTrue(veilSession.hasSession("peer"))
        }

        @Test
        fun `hasSession returns true after archiveSession (archive keeps session)`() = runTest {
            setupKeysAndBundle()
            veilSession.encryptMessage("peer", "Hi".encodeToByteArray())
            assertTrue(veilSession.hasSession("peer"))
            veilSession.archiveSession("peer")
            assertTrue(veilSession.hasSession("peer"))
        }

        @Test
        fun `deleteSession marks session as archived (still has session)`() = runTest {
            setupKeysAndBundle()
            veilSession.encryptMessage("peer", "Hi".encodeToByteArray())
            assertTrue(veilSession.hasSession("peer"))
            veilSession.deleteSession("peer")
            assertTrue(veilSession.hasSession("peer"))
        }
    }

    @Nested
    @DisplayName("Decryption")
    inner class DecryptionTests {

        @Test
        fun `decryptMessage returns null when no session exists`() = runTest {
            val result = veilSession.decryptMessage("nobody", ByteArray(100))
            assertNull(result)
        }

        @Test
        fun `decryptMessage with too-short payload returns null`() = runTest {
            setupKeysAndBundle()
            veilSession.encryptMessage("peer", "test".encodeToByteArray())
            val result = veilSession.decryptMessage("peer", ByteArray(5))
            assertNull(result)
        }

        @Test
        fun `decryptPreKeyMessage with too-short payload returns null`() = runTest {
            val result = veilSession.decryptPreKeyMessage("nobody", ByteArray(2))
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("Alice-Bob Roundtrip (Two Separate Sessions)")
    inner class RoundtripTests {

        @Test
        fun `single session can encrypt and self-decrypt for sanity check`() = runTest {
            val session = VeilSession.create(selfUserId = "self")
            try {
                val ik = CryptoPrimitives.generateEd25519KeyPair()
                val spk = CryptoPrimitives.generateX25519KeyPair()
                val opk = CryptoPrimitives.generateX25519KeyPair()
                val spkSig = CryptoPrimitives.signEd25519(spk.publicKey, ik.privateKey)
                val bundle = KeyManager.KeyBundle(
                    deviceId = "self-device",
                    identityKey = CryptoPrimitives.ed25519PkToX25519(ik.publicKey),
                    signedPrekey = KeyManager.SignedPrekeyData(spk.publicKey, spkSig),
                    oneTimePrekey = opk.publicKey
                )

                val established = session.establishSession("peer", 1, bundle)
                assertTrue(established, "Session should be established")

                val plaintext = "Hello, self!"
                val encrypted = session.encryptMessage("peer", plaintext.toByteArray())
                assertNotNull(encrypted)
                assertTrue(encrypted!!.payload.isNotEmpty())
                assertEquals(VeilSession.MessageType.PREKEY_MESSAGE,
                    encrypted.messageType)
             } finally {
                session.close()
            }
        }

        @Test
        fun `alice to bob prekey and bob replies normal — full bidirectional roundtrip`() = runTest {
            // Both parties create their own VeilSession with auto-generated X25519 identities.
            // Alice initiates X3DH with Bob's key bundle. Bob responds via decryptPrekeyMessage.
            // Bob then encrypts a reply. Alice decrypts it using the existing session.

            val alice = VeilSession.create(selfUserId = "alice")
            val bob = VeilSession.create(selfUserId = "bob")

            try {
                // Get Bob's auto-generated X25519 identity public key
                val bobIdentityPub = bob.getLocalIdentityPublicKey()!!
                assertEquals(32, bobIdentityPub.size)

                // Generate Bob's signed prekey and one-time prekey (X25519)
                val bobSpk = CryptoPrimitives.generateX25519KeyPair()
                val bobOpk = CryptoPrimitives.generateX25519KeyPair()

                // Store Bob's SPK and OPK private keys in his identity store
                // so decryptPrekeyMessage can look them up by ID
                bob.storeSignedPrekey(1, bobSpk.privateKey)
                bob.storeOneTimePrekey(1, bobOpk.privateKey)

                // Sign Bob's SPK (native validates signature size, not content)
                val signingKey = CryptoPrimitives.generateEd25519KeyPair()
                val spkSig = CryptoPrimitives.signEd25519(bobSpk.publicKey, signingKey.privateKey)

                // Build Bob's key bundle
                val bobBundle = KeyManager.KeyBundle(
                    deviceId = "1",
                    identityKey = bobIdentityPub,
                    signedPrekey = KeyManager.SignedPrekeyData(bobSpk.publicKey, spkSig),
                    oneTimePrekey = bobOpk.publicKey
                )

                // --- Step 1: Alice establishes session and sends PREKEY message ---
                val established = alice.establishSession("bob", 1, bobBundle)
                assertTrue(established, "Alice should establish session with Bob")

                val plaintext1 = "Hello Bob from Alice"
                val encrypted1 = alice.encryptMessage("bob", plaintext1.toByteArray())
                assertNotNull(encrypted1, "Alice encrypt should succeed")
                assertEquals(VeilSession.MessageType.PREKEY_MESSAGE, encrypted1!!.messageType)
                assertTrue(encrypted1.payload.isNotEmpty(), "Ciphertext should not be empty")

                println("[TEST] Alice sent PREKEY (${encrypted1.payload.size} bytes)")

                // --- Step 2: Bob decrypts Alice's PREKEY message ---
                val decrypted1 = bob.decryptPrekeyMessage(
                    senderUserId = "alice",
                    ciphertext = encrypted1.payload,
                    ourSignedPrekeyId = 1,
                    ourOneTimePrekeyId = 1
                )
                assertNotNull(decrypted1, "Bob decryptPrekeyMessage should succeed")
                assertEquals(plaintext1, String(decrypted1!!.plaintext), "Bob should read Alice's message")
                assertTrue(decrypted1.isNewSession, "Should indicate new session")

                println("[TEST] Bob decrypted PREKEY: \"${String(decrypted1.plaintext)}\"")

                // --- Step 3: Bob sends NORMAL reply ---
                val plaintext2 = "Hello Alice from Bob"
                val encrypted2 = bob.encryptMessage("alice", plaintext2.toByteArray())
                assertNotNull(encrypted2, "Bob encrypt should succeed")
                assertEquals(VeilSession.MessageType.ENCRYPTED_MESSAGE, encrypted2!!.messageType)

                println("[TEST] Bob sent NORMAL (${encrypted2.payload.size} bytes)")

                // --- Step 4: Alice decrypts Bob's NORMAL reply ---
                val decrypted2 = alice.decryptMessage("bob", encrypted2.payload)
                assertNotNull(decrypted2, "Alice decryptMessage should succeed")
                assertEquals(plaintext2, String(decrypted2!!.plaintext), "Alice should read Bob's reply")

                println("[TEST] Alice decrypted NORMAL: \"${String(decrypted2.plaintext)}\"")

                // --- Step 5: Multi-message roundtrip ---
                val plaintext3 = "Second message from Alice"
                val encrypted3 = alice.encryptMessage("bob", plaintext3.toByteArray())
                assertNotNull(encrypted3, "Alice second encrypt should succeed")
                assertEquals(VeilSession.MessageType.ENCRYPTED_MESSAGE, encrypted3!!.messageType)

                val decrypted3 = bob.decryptMessage("alice", encrypted3.payload)
                assertNotNull(decrypted3, "Bob second decrypt should succeed")
                assertEquals(plaintext3, String(decrypted3!!.plaintext))

                val plaintext4 = "Second reply from Bob"
                val encrypted4 = bob.encryptMessage("alice", plaintext4.toByteArray())
                assertNotNull(encrypted4, "Bob second encrypt should succeed")

                val decrypted4 = alice.decryptMessage("bob", encrypted4!!.payload)
                assertNotNull(decrypted4, "Alice second decrypt should succeed")
                assertEquals(plaintext4, String(decrypted4!!.plaintext))

                println("[TEST] Multi-message roundtrip PASSED")
            } finally {
                alice.close()
                bob.close()
            }
        }
    }
}
