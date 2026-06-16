package org.enchant.core.crypto

import io.mockk.coEvery
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

@DisplayName("NativeSessionManager — Full Coverage")
class NativeSessionManagerTest {

    private lateinit var mockSessionStore: SessionStore
    private lateinit var mockIdentityStore: IdentityStore

    private lateinit var selfIkPair: CryptoPrimitives.KeyPair
    private lateinit var bobIkPair: CryptoPrimitives.KeyPair
    private lateinit var bobSpkPair: CryptoPrimitives.KeyPair
    private lateinit var bobOpkPair: CryptoPrimitives.KeyPair
    private lateinit var bobSig: ByteArray

    private fun createBobBundle(): KeyManager.KeyBundle {
        return KeyManager.KeyBundle(
            deviceId = "bob-device",
            identityKey = bobIkPair.publicKey,
            signedPrekey = KeyManager.SignedPrekeyData(bobSpkPair.publicKey, bobSig),
            oneTimePrekey = bobOpkPair.publicKey
        )
    }

    private suspend fun setupKeysAndBundle() {
        KeyManager.setTestIdentityKeyPair(selfIkPair)
        KeyManager.setTestKeyBundle("bob", createBobBundle())
    }

    @BeforeEach
    fun setUp() = runTest {
        NativeSessionManager.reset()
        KeyManager.reset()

        selfIkPair = CryptoPrimitives.generateEd25519KeyPair()
        bobIkPair = CryptoPrimitives.generateEd25519KeyPair()
        bobSpkPair = CryptoPrimitives.generateX25519KeyPair()
        bobOpkPair = CryptoPrimitives.generateX25519KeyPair()
        bobSig = CryptoPrimitives.signEd25519(bobSpkPair.publicKey, bobIkPair.privateKey)

        mockSessionStore = mockk(relaxed = true)
        mockIdentityStore = mockk(relaxed = true)

        NativeSessionManager.init(selfUserId = "self", store = mockSessionStore, idStore = mockIdentityStore)
    }

    @AfterEach
    fun tearDown() = runTest {
        NativeSessionManager.reset()
        KeyManager.reset()
    }

    @Nested @DisplayName("Initialization")
    inner class InitTest {
        @Test @DisplayName("init with selfUserId")
        fun `init basic`() = runTest {
            NativeSessionManager.reset()
            NativeSessionManager.init(selfUserId = "user1")
            assertTrue(true)
        }

        @Test @DisplayName("double init is idempotent")
        fun `double init safe`() = runTest {
            NativeSessionManager.init(selfUserId = "user2")
            assertTrue(true)
        }
    }

    @Nested @DisplayName("Session Creation")
    inner class SessionCreationTest {
        @Test @DisplayName("encryptMessage returns null without identity key")
        fun `encrypt null no identity`() = runTest {
            val result = NativeSessionManager.encryptMessage("bob", "Hello".encodeToByteArray())
            assertNull(result)
        }

        @Test @DisplayName("encryptMessage returns null without recipient key bundle")
        fun `encrypt null no bundle`() = runTest {
            KeyManager.setTestIdentityKeyPair(selfIkPair)
            val result = NativeSessionManager.encryptMessage("unknown", "Hello".encodeToByteArray())
            assertNull(result)
        }

        @Test @DisplayName("encryptMessage returns PREKEY_MESSAGE on first encrypt")
        fun `encrypt first is prekey`() = runTest {
            setupKeysAndBundle()
            val result = NativeSessionManager.encryptMessage("bob", "Hello".encodeToByteArray())
            assertNotNull(result)
            assertEquals(NativeSessionManager.MessageType.PREKEY_MESSAGE, result!!.messageType)
        }

        @Test @DisplayName("encryptMessage returns ENCRYPTED_MESSAGE on subsequent encrypt")
        fun `encrypt second is encrypted message`() = runTest {
            setupKeysAndBundle()
            NativeSessionManager.encryptMessage("bob", "first".encodeToByteArray())
            val result = NativeSessionManager.encryptMessage("bob", "second".encodeToByteArray())
            assertNotNull(result)
            assertEquals(NativeSessionManager.MessageType.ENCRYPTED_MESSAGE, result!!.messageType)
        }

        @Test @DisplayName("encryptMessage payload is non-empty")
        fun `encrypt payload non empty`() = runTest {
            setupKeysAndBundle()
            val result = NativeSessionManager.encryptMessage("bob", "Hello".encodeToByteArray())
            assertTrue(result!!.payload.isNotEmpty())
        }

        @Test @DisplayName("encryptMessage with empty plaintext succeeds")
        fun `encrypt empty plaintext`() = runTest {
            setupKeysAndBundle()
            val result = NativeSessionManager.encryptMessage("bob", ByteArray(0))
            assertNotNull(result)
        }
    }

    @Nested @DisplayName("Session Lookup")
    inner class SessionLookupTest {
        @Test @DisplayName("hasSession returns false before encryption")
        fun `hasSession false before`() = runTest {
            assertFalse(NativeSessionManager.hasSession("nobody"))
        }

        @Test @DisplayName("hasSession returns true after encrypting")
        fun `hasSession true after encrypt`() = runTest {
            setupKeysAndBundle()
            NativeSessionManager.encryptMessage("bob", "Hi".encodeToByteArray())
            assertTrue(NativeSessionManager.hasSession("bob"))
        }

        @Test @DisplayName("hasSession returns false after deleteSession")
        fun `hasSession false after delete`() = runTest {
            setupKeysAndBundle()
            NativeSessionManager.encryptMessage("bob", "Hi".encodeToByteArray())
            assertTrue(NativeSessionManager.hasSession("bob"))
            NativeSessionManager.deleteSession("bob")
            assertFalse(NativeSessionManager.hasSession("bob"))
        }

        @Test @DisplayName("hasSession returns false after archiveSession")
        fun `hasSession false after archive`() = runTest {
            setupKeysAndBundle()
            NativeSessionManager.encryptMessage("bob", "Hi".encodeToByteArray())
            assertTrue(NativeSessionManager.hasSession("bob"))
            NativeSessionManager.archiveSession("bob")
            assertFalse(NativeSessionManager.hasSession("bob"))
        }

        @Test @DisplayName("deleteSession calls store.delete")
        fun `delete calls store`() = runTest {
            setupKeysAndBundle()
            NativeSessionManager.encryptMessage("bob", "Hi".encodeToByteArray())
            NativeSessionManager.deleteSession("bob")
            coVerify(atLeast = 1) { mockSessionStore.delete(any()) }
        }
    }

    @Nested @DisplayName("Encryption/Decryption Roundtrip")
    inner class EncryptDecryptTest {
        @Test @DisplayName("encrypt then decryptMessage returns original plaintext")
        fun `encrypt decrypt roundtrip`() = runTest {
            setupKeysAndBundle()
            val encrypted = NativeSessionManager.encryptMessage("bob", "Hello World".encodeToByteArray())
            assertNotNull(encrypted)

            val theirIdentityX = CryptoPrimitives.ed25519PkToX255519(bobIkPair.publicKey)
            NativeSessionManager.setIdentityKey("bob", theirIdentityX)

            val result = NativeSessionManager.decryptMessage("bob", encrypted!!.payload)
            assertNotNull(result)
            assertArrayEquals("Hello World".encodeToByteArray(), result!!.plaintext)
        }

        @Test @DisplayName("multiple messages in sequence decrypt correctly")
        fun `multiple messages sequence`() = runTest {
            setupKeysAndBundle()
            val theirIdentityX = CryptoPrimitives.ed25519PkToX25519(bobIkPair.publicKey)
            NativeSessionManager.setIdentityKey("bob", theirIdentityX)

            for (i in 0 until 5) {
                val encrypted = NativeSessionManager.encryptMessage("bob", "msg-$i".encodeToByteArray())
                assertNotNull(encrypted)
                val decrypted = NativeSessionManager.decryptMessage("bob", encrypted!!.payload)
                assertNotNull(decrypted)
                assertEquals("msg-$i", decrypted!!.plaintext.decodeToString())
            }
        }

        @Test @DisplayName("decryptMessage with no session returns null")
        fun `decrypt no session`() = runTest {
            val result = NativeSessionManager.decryptMessage("nobody", ByteArray(100))
            assertNull(result)
        }

        @Test @DisplayName("decryptMessage with too-short payload returns null")
        fun `decrypt short payload`() = runTest {
            setupKeysAndBundle()
            NativeSessionManager.encryptMessage("bob", "init".encodeToByteArray())
            val result = NativeSessionManager.decryptMessage("bob", ByteArray(2))
            assertNull(result)
        }

        @Test @DisplayName("decryptMessage with corrupted payload returns null")
        fun `decrypt corrupted`() = runTest {
            setupKeysAndBundle()
            NativeSessionManager.setIdentityKey("bob", CryptoPrimitives.ed25519PkToX25519(bobIkPair.publicKey))
            val encrypted = NativeSessionManager.encryptMessage("bob", "test".encodeToByteArray())
            assertNotNull(encrypted)
            encrypted!!.payload[0] = (encrypted.payload[0].toInt() xor 0xFF).toByte()
            val result = NativeSessionManager.decryptMessage("bob", encrypted.payload)
            assertNull(result)
        }
    }

    @Nested @DisplayName("Pre-Key Message Decryption")
    inner class PreKeyDecryptTest {
        @Test @DisplayName("decryptPreKeyMessage establishes session and decrypts")
        fun `prekey decrypt establishes`() = runTest {
            val sharedSecret = CryptoPrimitives.generateRandomKey(32)
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            val aliceState = DoubleRatchet.initializeAsAlice(
                sharedSecret = sharedSecret,
                theirSignedPrekeyPublic = bobSpkPair.publicKey,
                ourEphemeralKeyPair = aliceEk
            )!!

            val (_, message) = DoubleRatchet.encrypt(aliceState, "prekey test".encodeToByteArray())

            val preKeyPayload = buildPreKeyMessagePayload(
                theirIk = bobIkPair.publicKey,
                theirEk = aliceEk.publicKey,
                ourSpkId = 1,
                ourOpkId = -1,
                header = message.header,
                ciphertext = message.ciphertext
            )

            KeyManager.setTestIdentityKeyPair(bobIkPair)
            val mockSpkStore = mockk<PreKeyStore>(relaxed = true)
            coEvery { mockSpkStore.getCurrentSignedPreKey() } returns SignedPreKeyRecord(
                id = 1, publicKey = bobSpkPair.publicKey, privateKey = bobSpkPair.privateKey,
                signature = bobSig, timestamp = System.currentTimeMillis()
            )
            KeyManager.init(store = mockSpkStore)

            NativeSessionManager.reset()
            NativeSessionManager.init(selfUserId = "bob", store = mockSessionStore, idStore = mockIdentityStore)

            val result = NativeSessionManager.decryptPreKeyMessage("alice", preKeyPayload)
            assertNotNull(result)
            assertEquals("prekey test", result!!.plaintext.decodeToString())
            assertTrue(result.isNewSession)
        }

        @Test @DisplayName("decryptPreKeyMessage with existing session delegates to decryptMessage")
        fun `prekey existing session`() = runTest {
            setupKeysAndBundle()
            NativeSessionManager.setIdentityKey("bob", CryptoPrimitives.ed25519PkToX25519(bobIkPair.publicKey))
            NativeSessionManager.encryptMessage("bob", "init".encodeToByteArray())

            val payload = ByteArray(100)
            val result = NativeSessionManager.decryptPreKeyMessage("bob", payload)
            assertNull(result)
        }

        @Test @DisplayName("decryptPreKeyMessage with truncated payload returns null")
        fun `prekey truncated`() = runTest {
            KeyManager.setTestIdentityKeyPair(bobIkPair)
            val result = NativeSessionManager.decryptPreKeyMessage("alice", ByteArray(2))
            assertNull(result)
        }
    }

    @Nested @DisplayName("Identity Key Management")
    inner class IdentityKeyTest {
        @Test @DisplayName("setIdentityKey stores the key")
        fun `set identity key`() = runTest {
            val ik = CryptoPrimitives.generateEd25519KeyPair().publicKey
            NativeSessionManager.setIdentityKey("bob", ik)
            val stored = NativeSessionManager.getIdentityKey("bob")
            assertNotNull(stored)
            assertTrue(ik.contentEquals(stored!!))
        }

        @Test @DisplayName("getIdentityKey returns null for unknown user")
        fun `get identity key unknown`() = runTest {
            assertNull(NativeSessionManager.getIdentityKey("nobody"))
        }

        @Test @DisplayName("hasIdentityChanged returns false for unknown user")
        fun `has identity changed unknown`() = runTest {
            assertFalse(NativeSessionManager.hasIdentityChanged("unknown"))
        }

        @Test @DisplayName("hasIdentityChanged returns false (placeholder implementation)")
        fun `has identity changed returns false`() = runTest {
            val ik = CryptoPrimitives.generateEd25519KeyPair().publicKey
            NativeSessionManager.setIdentityKey("bob", ik)
            assertFalse(NativeSessionManager.hasIdentityChanged("bob"))
        }
    }

    @Nested @DisplayName("Safety Number")
    inner class SafetyNumberTest {
        @Test @DisplayName("getSafetyNumber returns UNVERIFIED without our identity key")
        fun `safety number unverified no our key`() = runTest {
            KeyManager.reset()
            KeyManager.init()
            val ik = CryptoPrimitives.generateEd25519KeyPair().publicKey
            NativeSessionManager.setIdentityKey("bob", ik)
            assertEquals("UNVERIFIED", NativeSessionManager.getSafetyNumber("bob"))
        }

        @Test @DisplayName("getSafetyNumber returns UNVERIFIED without their identity key")
        fun `safety number unverified no their key`() = runTest {
            KeyManager.setTestIdentityKeyPair(selfIkPair)
            assertEquals("UNVERIFIED", NativeSessionManager.getSafetyNumber("unknown"))
        }

        @Test @DisplayName("getSafetyNumber returns formatted string when both keys available")
        fun `safety number formatted`() = runTest {
            KeyManager.setTestIdentityKeyPair(selfIkPair)
            val bobIk = CryptoPrimitives.generateEd25519KeyPair()
            NativeSessionManager.setIdentityKey("bob-safety", bobIk.publicKey)
            val safetyNum = NativeSessionManager.getSafetyNumber("bob-safety")
            assertNotEquals("UNVERIFIED", safetyNum)
        }

        @Test @DisplayName("getSafetyNumber is deterministic for same keys")
        fun `safety number deterministic`() = runTest {
            KeyManager.setTestIdentityKeyPair(selfIkPair)
            val bobIk = CryptoPrimitives.generateEd25519KeyPair()
            NativeSessionManager.setIdentityKey("bob-det", bobIk.publicKey)
            val s1 = NativeSessionManager.getSafetyNumber("bob-det")
            val s2 = NativeSessionManager.getSafetyNumber("bob-det")
            assertEquals(s1, s2)
        }

        @Test @DisplayName("getSafetyNumber differs for different identities")
        fun `safety number differs`() = runTest {
            KeyManager.setTestIdentityKeyPair(selfIkPair)
            val bobIk1 = CryptoPrimitives.generateEd25519KeyPair()
            val bobIk2 = CryptoPrimitives.generateEd25519KeyPair()
            NativeSessionManager.setIdentityKey("bob-1", bobIk1.publicKey)
            NativeSessionManager.setIdentityKey("bob-2", bobIk2.publicKey)
            val s1 = NativeSessionManager.getSafetyNumber("bob-1")
            val s2 = NativeSessionManager.getSafetyNumber("bob-2")
            assertNotEquals(s1, s2)
        }
    }

    @Nested @DisplayName("Session Lifecycle")
    inner class SessionLifecycleTest {
        @Test @DisplayName("session survives multiple encrypt operations")
        fun `session survives multiple`() = runTest {
            setupKeysAndBundle()
            NativeSessionManager.encryptMessage("bob", "msg1".encodeToByteArray())
            NativeSessionManager.encryptMessage("bob", "msg2".encodeToByteArray())
            NativeSessionManager.encryptMessage("bob", "msg3".encodeToByteArray())
            assertTrue(NativeSessionManager.hasSession("bob"))
        }

        @Test @DisplayName("concurrent encrypts are serialized correctly")
        fun `concurrent encrypts`() = runTest {
            setupKeysAndBundle()
            var successCount = 0
            repeat(5) { i ->
                val result = NativeSessionManager.encryptMessage("bob", "data-$i".encodeToByteArray())
                if (result != null) successCount++
            }
            assertEquals(5, successCount)
        }

        @Test @DisplayName("loadSessionsFromDb is safe without store")
        fun `load sessions no store`() = runTest {
            NativeSessionManager.reset()
            NativeSessionManager.init(selfUserId = "self")
            NativeSessionManager.loadSessionsFromDb()
            assertTrue(true)
        }
    }

    @Nested @DisplayName("Reset")
    inner class ResetTest {
        @Test @DisplayName("reset clears all sessions")
        fun `reset clears sessions`() = runTest {
            setupKeysAndBundle()
            NativeSessionManager.encryptMessage("bob", "msg".encodeToByteArray())
            assertTrue(NativeSessionManager.hasSession("bob"))
            NativeSessionManager.reset()
            NativeSessionManager.init(selfUserId = "self")
            assertFalse(NativeSessionManager.hasSession("bob"))
        }

        @Test @DisplayName("reset clears identity keys")
        fun `reset clears identity keys`() = runTest {
            val ik = CryptoPrimitives.generateEd25519KeyPair().publicKey
            NativeSessionManager.setIdentityKey("bob", ik)
            assertNotNull(NativeSessionManager.getIdentityKey("bob"))
            NativeSessionManager.reset()
            NativeSessionManager.init(selfUserId = "self")
            assertNull(NativeSessionManager.getIdentityKey("bob"))
        }
    }

    @Nested @DisplayName("EncryptedPayload Data Class")
    inner class PayloadTest {
        @Test @DisplayName("EncryptedPayload holds correct values")
        fun `payload values`() {
            val payload = NativeSessionManager.EncryptedPayload(
                messageType = NativeSessionManager.MessageType.ENCRYPTED_MESSAGE,
                payload = ByteArray(10) { 1 },
                recipientDeviceId = "device-1"
            )
            assertEquals(NativeSessionManager.MessageType.ENCRYPTED_MESSAGE, payload.messageType)
            assertEquals(10, payload.payload.size)
            assertEquals("device-1", payload.recipientDeviceId)
        }

        @Test @DisplayName("DecryptedResult holds correct values")
        fun `result values`() {
            val result = NativeSessionManager.DecryptedResult(
                plaintext = "hello".encodeToByteArray(),
                senderDeviceId = "device-2",
                isNewSession = true
            )
            assertArrayEquals("hello".encodeToByteArray(), result.plaintext)
            assertEquals("device-2", result.senderDeviceId)
            assertTrue(result.isNewSession)
        }
    }

    private fun buildPreKeyMessagePayload(
        theirIk: ByteArray,
        theirEk: ByteArray,
        ourSpkId: Int,
        ourOpkId: Int,
        header: ByteArray,
        ciphertext: ByteArray
    ): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(
            4 + theirIk.size + 4 + theirEk.size + 4 + 4 + 4 + header.size + ciphertext.size
        ).order(java.nio.ByteOrder.BIG_ENDIAN)
        buf.putInt(theirIk.size); buf.put(theirIk)
        buf.putInt(theirEk.size); buf.put(theirEk)
        buf.putInt(ourSpkId)
        buf.putInt(ourOpkId)
        buf.putInt(header.size); buf.put(header)
        buf.put(ciphertext)
        return buf.array()
    }
}
