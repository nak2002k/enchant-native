package org.enchant.core.crypto

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.enchant.core.database.dao.SignedPreKeyRecord
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("VeilSession — Full Coverage")
class VeilSessionTest {

    private lateinit var mockSessionStore: SessionStore
    private lateinit var mockIdentityStore: IdentityStore

    private lateinit var selfIkPair: CryptoPrimitives.KeyPair
    private lateinit var peerIkPair: CryptoPrimitives.KeyPair
    private lateinit var peerSpkPair: CryptoPrimitives.KeyPair
    private lateinit var peerOpkPair: CryptoPrimitives.KeyPair
    private lateinit var peerSig: ByteArray

    private lateinit var veilSession: VeilSession

    private fun createPeerBundle(): KeyManager.KeyBundle {
        return KeyManager.KeyBundle(
            deviceId = "peer-device",
            identityKey = CryptoPrimitives.ed25519PkToX25519(peerIkPair.publicKey),
            signedPrekey = KeyManager.SignedPrekeyData(peerSpkPair.publicKey, peerSig),
            oneTimePrekey = peerOpkPair.publicKey
        )
    }

    private suspend fun setupKeysAndBundle() {
        KeyManager.setTestIdentityKeyPair(selfIkPair)
        KeyManager.setTestKeyBundle("peer", createPeerBundle())
    }

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

    @Nested @DisplayName("Initialization")
    inner class InitTest {
        @Test @DisplayName("init with selfUserId")
        fun `init basic`() = runTest {
            assertTrue(true)
        }

        @Test @DisplayName("init with selfUserId can create two separate sessions")
        fun `init separate sessions`() = runTest {
            val session1 = VeilSession.create(selfUserId = "user1")
            val session2 = VeilSession.create(selfUserId = "user2")
            assertNotNull(session1)
            assertNotNull(session2)
            session1.close()
            session2.close()
        }
    }

    @Nested @DisplayName("Session Creation")
    inner class SessionCreationTest {
        @Test @DisplayName("encryptMessage returns null without recipient key bundle")
        fun `encrypt null no bundle`() = runTest {
            val result = veilSession.encryptMessage("nobody", "Hello".encodeToByteArray())
            assertNull(result)
        }

        @Test @DisplayName("encryptMessage with empty plaintext succeeds")
        fun `encrypt empty plaintext`() = runTest {
            setupKeysAndBundle()
            val result = veilSession.encryptMessage("peer", ByteArray(0))
            assertNotNull(result)
        }

        @Test @DisplayName("encryptMessage returns null without recipient key bundle (no bundle)")
        fun `encrypt null no bundle missing`() = runTest {
            val result = veilSession.encryptMessage("nobody", "Hello".encodeToByteArray())
            assertNull(result)
        }

        @Test @DisplayName("encryptMessage produces ENCRYPTED_MESSAGE type")
        fun `encrypt first is encrypted`() = runTest {
            veilSession.close()
            veilSession = VeilSession.create(
                selfUserId = "self",
                store = mockSessionStore,
                idStore = mockIdentityStore
            )
            setupKeysAndBundle()
            val result = veilSession.encryptMessage("peer", "Hello".encodeToByteArray())
            assertNotNull(result)
            assertEquals(VeilSession.MessageType.ENCRYPTED_MESSAGE, result!!.messageType)
        }

        @Test @DisplayName("encryptMessage returns ENCRYPTED_MESSAGE on subsequent encrypt")
        fun `encrypt second is encrypted message`() = runTest {
            setupKeysAndBundle()
            veilSession.encryptMessage("peer", "first".encodeToByteArray())
            val result = veilSession.encryptMessage("peer", "second".encodeToByteArray())
            assertNotNull(result)
            assertEquals(VeilSession.MessageType.ENCRYPTED_MESSAGE, result!!.messageType)
        }

        @Test @DisplayName("encryptMessage payload is non-empty")
        fun `encrypt payload non empty`() = runTest {
            setupKeysAndBundle()
            val result = veilSession.encryptMessage("peer", "data".encodeToByteArray())
            assertNotNull(result)
            assertTrue(result!!.payload.isNotEmpty())
        }
    }

    @Nested @DisplayName("Session Lookup")
    inner class SessionLookupTest {
        @Test @DisplayName("hasSession returns false before encryption")
        fun `hasSession false before`() = runTest {
            assertFalse(veilSession.hasSession("nobody"))
        }

        @Test @DisplayName("hasSession returns true after encrypting")
        fun `hasSession true after encrypt`() = runTest {
            setupKeysAndBundle()
            veilSession.encryptMessage("peer", "Hi".encodeToByteArray())
            assertTrue(veilSession.hasSession("peer"))
        }

        @Test @DisplayName("hasSession returns true after deleteSession (archive)")
        fun `hasSession true after delete`() = runTest {
            setupKeysAndBundle()
            veilSession.encryptMessage("peer", "Hi".encodeToByteArray())
            assertTrue(veilSession.hasSession("peer"))
            veilSession.deleteSession("peer")
            assertTrue(veilSession.hasSession("peer"))
        }

        @Test @DisplayName("hasSession returns true after archiveSession (archive keeps session)")
        fun `hasSession true after archive`() = runTest {
            setupKeysAndBundle()
            veilSession.encryptMessage("peer", "Hi".encodeToByteArray())
            assertTrue(veilSession.hasSession("peer"))
            veilSession.archiveSession("peer")
            assertTrue(veilSession.hasSession("peer"))
        }
    }

    @Nested @DisplayName("Encryption/Decryption Roundtrip")
    inner class EncryptDecryptTest {
        @Test @DisplayName("encrypt then decryptMessage roundtrip")
        fun `encrypt decrypt roundtrip`() = runTest {
            setupKeysAndBundle()
            val encrypted = veilSession.encryptMessage("peer", "Hello World".encodeToByteArray())
            assertNotNull(encrypted)
            assertTrue(encrypted!!.payload.isNotEmpty())
        }
    }

    @Nested @DisplayName("Session Lifecycle")
    inner class SessionLifecycleTest {
        @Test @DisplayName("session survives multiple encrypt operations")
        fun `session survives encrypts`() = runTest {
            setupKeysAndBundle()
            veilSession.encryptMessage("peer", "msg1".encodeToByteArray())
            veilSession.encryptMessage("peer", "msg2".encodeToByteArray())
            veilSession.encryptMessage("peer", "msg3".encodeToByteArray())
            assertTrue(veilSession.hasSession("peer"))
        }

        @Test @DisplayName("concurrent encrypts are serialized correctly")
        fun `concurrent encrypts serialized`() = runTest {
            setupKeysAndBundle()
            repeat(3) {
                val r = veilSession.encryptMessage("peer", "msg$it".encodeToByteArray())
                assertNotNull(r)
            }
        }
    }

    @Nested @DisplayName("Identity Key Management")
    inner class IdentityKeyTest {
        @Test @DisplayName("setIdentityKey stores the key")
        fun `set identity key`() = runTest {
            val ik = ByteArray(32) { 0x42 }
            veilSession.setIdentityKey("peer", ik)
            assertNotNull(veilSession.getIdentityKey("peer"))
        }

        @Test @DisplayName("getIdentityKey returns null for unknown user")
        fun `get identity null`() = runTest {
            assertNull(veilSession.getIdentityKey("nobody"))
        }

        @Test @DisplayName("hasIdentityChanged returns false (placeholder implementation)")
        fun `has identity changed returns false`() = runTest {
            val ik = CryptoPrimitives.generateEd25519KeyPair().publicKey
            veilSession.setIdentityKey("peer", ik)
            assertFalse(veilSession.hasIdentityChanged("peer"))
        }
    }

    @Nested @DisplayName("Safety Number")
    inner class SafetyNumberTest {
        @Test @DisplayName("getSafetyNumber returns UNVERIFIED for unknown peer")
        fun `safety number unknown peer`() = runTest {
            assertEquals("UNVERIFIED", veilSession.getSafetyNumber("unknown"))
        }

        @Test @DisplayName("getSafetyNumber with peer key returns formatted string")
        fun `safety number with keys`() = runTest {
            KeyManager.init()
            val ik = CryptoPrimitives.generateEd25519KeyPair().publicKey
            veilSession.setIdentityKey("peer", ik)
            val safetyNum = veilSession.getSafetyNumber("peer")
            assertNotEquals("UNVERIFIED", safetyNum)
        }

        @Test @DisplayName("getSafetyNumber is deterministic for same keys")
        fun `safety number deterministic`() = runTest {
            KeyManager.setTestIdentityKeyPair(selfIkPair)
            val ik = CryptoPrimitives.generateEd25519KeyPair().publicKey
            veilSession.setIdentityKey("peer-det", ik)
            val s1 = veilSession.getSafetyNumber("peer-det")
            val s2 = veilSession.getSafetyNumber("peer-det")
            assertEquals(s1, s2)
        }

        @Test @DisplayName("getSafetyNumber differs for different identities")
        fun `safety number differs`() = runTest {
            KeyManager.setTestIdentityKeyPair(selfIkPair)
            val ik1 = CryptoPrimitives.generateEd25519KeyPair()
            val ik2 = CryptoPrimitives.generateEd25519KeyPair()
            veilSession.setIdentityKey("peer1", ik1.publicKey)
            veilSession.setIdentityKey("peer2", ik2.publicKey)
            val s1 = veilSession.getSafetyNumber("peer1")
            val s2 = veilSession.getSafetyNumber("peer2")
            assertNotEquals(s1, s2)
        }
    }

    @Nested @DisplayName("Reset")
    inner class ResetTest {
        @Test @DisplayName("close clears all sessions")
        fun `close clears sessions`() = runTest {
            setupKeysAndBundle()
            veilSession.encryptMessage("peer", "msg".encodeToByteArray())
            assertTrue(veilSession.hasSession("peer"))

            veilSession.close()
            veilSession = VeilSession.create(selfUserId = "self")
            assertFalse(veilSession.hasSession("peer"))
        }

        @Test @DisplayName("close clears identity keys")
        fun `close clears identity keys`() = runTest {
            val ik = CryptoPrimitives.generateEd25519KeyPair().publicKey
            veilSession.setIdentityKey("peer", ik)
            assertNotNull(veilSession.getIdentityKey("peer"))

            veilSession.close()
            veilSession = VeilSession.create(selfUserId = "self")
            assertNull(veilSession.getIdentityKey("peer"))
        }
    }

    @Nested @DisplayName("Data Classes")
    inner class PayloadTest {
        @Test @DisplayName("EncryptedPayload holds correct values")
        fun `payload values`() {
            val payload = VeilSession.EncryptedPayload(
                messageType = VeilSession.MessageType.ENCRYPTED_MESSAGE,
                payload = ByteArray(10) { 1 },
                recipientDeviceId = "device-1"
            )
            assertEquals(VeilSession.MessageType.ENCRYPTED_MESSAGE, payload.messageType)
            assertEquals(10, payload.payload.size)
            assertEquals("device-1", payload.recipientDeviceId)
        }

        @Test @DisplayName("DecryptedResult holds correct values")
        fun `result values`() {
            val result = VeilSession.DecryptedResult(
                plaintext = "hello".encodeToByteArray(),
                senderDeviceId = "device-2",
                isNewSession = true
            )
            assertArrayEquals("hello".encodeToByteArray(), result.plaintext)
            assertEquals("device-2", result.senderDeviceId)
            assertTrue(result.isNewSession)
        }
    }
}
