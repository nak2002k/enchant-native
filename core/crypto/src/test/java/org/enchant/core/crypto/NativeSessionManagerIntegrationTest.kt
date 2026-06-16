package org.enchant.core.crypto

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("NativeSessionManager — Native Integration Tests")
class NativeSessionManagerIntegrationTest {

    private lateinit var selfIkPair: CryptoPrimitives.KeyPair
    private lateinit var bobIkPair: CryptoPrimitives.KeyPair
    private lateinit var bobSpkPair: CryptoPrimitives.KeyPair
    private lateinit var bobOpkPair: CryptoPrimitives.KeyPair
    private lateinit var bobSig: ByteArray
    private lateinit var mockSessionStore: SessionStore
    private lateinit var mockIdentityStore: IdentityStore

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

    private fun createBobBundle(): KeyManager.KeyBundle {
        return KeyManager.KeyBundle(
            deviceId = "bob-device",
            identityKey = CryptoPrimitives.ed25519PkToX25519(bobIkPair.publicKey),
            signedPrekey = KeyManager.SignedPrekeyData(bobSpkPair.publicKey, bobSig),
            oneTimePrekey = bobOpkPair.publicKey
        )
    }

    private fun setupKeysAndBundle() {
        KeyManager.setTestIdentityKeyPair(selfIkPair)
        KeyManager.setTestKeyBundle("bob", createBobBundle())
    }

    @Nested
    @DisplayName("Initialization")
    inner class InitializationTests {

        @Test
        fun `init creates native handles`() = runTest {
            assertTrue(NativeSessionManager.getLocalIdentityPublicKey() != null)
        }

        @Test
        fun `reset clears all state`() = runTest {
            setupKeysAndBundle()
            NativeSessionManager.encryptMessage("bob", "test".encodeToByteArray())
            assertTrue(NativeSessionManager.hasSession("bob"))

            NativeSessionManager.reset()
            NativeSessionManager.init(selfUserId = "self")
            assertFalse(NativeSessionManager.hasSession("bob"))
        }
    }

    @Nested
    @DisplayName("Identity Operations")
    inner class IdentityTests {

        @Test
        fun `getLocalIdentityPublicKey returns 32 bytes`() = runTest {
            val pk = NativeSessionManager.getLocalIdentityPublicKey()
            assertNotNull(pk)
            assertEquals(32, pk!!.size)
        }

        @Test
        fun `setIdentityKey and getIdentityKey roundtrip`() = runTest {
            val key = ByteArray(32) { 0x42 }
            NativeSessionManager.setIdentityKey("bob", key)
            val retrieved = NativeSessionManager.getIdentityKey("bob")
            assertNotNull(retrieved)
            assertArrayEquals(key, retrieved)
        }

        @Test
        fun `getIdentityKey for unknown user returns null`() = runTest {
            assertNull(NativeSessionManager.getIdentityKey("unknown"))
        }
    }

    @Nested
    @DisplayName("Safety Number")
    inner class SafetyNumberTests {

        @Test
        fun `safety number with peer key returns non-empty hex string`() = runTest {
            val ik = ByteArray(32) { 0x42 }
            NativeSessionManager.setIdentityKey("peer", ik)
            val safetyNumber = NativeSessionManager.getSafetyNumber("peer")
            assertNotEquals("UNVERIFIED", safetyNumber)
            assertTrue(safetyNumber.isNotEmpty())
        }

        @Test
        fun `safety number deterministic for same keys`() = runTest {
            val ik = ByteArray(32) { 0x42 }
            NativeSessionManager.setIdentityKey("peer", ik)
            val s1 = NativeSessionManager.getSafetyNumber("peer")
            val s2 = NativeSessionManager.getSafetyNumber("peer")
            assertEquals(s1, s2)
        }

        @Test
        fun `safety number differs for different peers`() = runTest {
            val ik1 = ByteArray(32) { 0x01 }
            val ik2 = ByteArray(32) { 0x02 }
            NativeSessionManager.setIdentityKey("peer1", ik1)
            NativeSessionManager.setIdentityKey("peer2", ik2)
            val s1 = NativeSessionManager.getSafetyNumber("peer1")
            val s2 = NativeSessionManager.getSafetyNumber("peer2")
            assertNotEquals(s1, s2)
        }

        @Test
        fun `safety number UNVERIFIED for unknown peer`() = runTest {
            assertEquals("UNVERIFIED", NativeSessionManager.getSafetyNumber("unknown"))
        }
    }

    @Nested
    @DisplayName("Encryption")
    inner class EncryptionTests {

        @Test
        fun `encrypt returns non-null payload`() = runTest {
            setupKeysAndBundle()
            val result = NativeSessionManager.encryptMessage("bob", "Hello".encodeToByteArray())
            assertNotNull(result)
            assertTrue(result!!.payload.isNotEmpty())
        }

        @Test
        fun `encrypt produces ENCRYPTED_MESSAGE type`() = runTest {
            setupKeysAndBundle()
            val result = NativeSessionManager.encryptMessage("bob", "Hello".encodeToByteArray())
            assertNotNull(result)
            assertEquals(NativeSessionManager.MessageType.ENCRYPTED_MESSAGE, result!!.messageType)
        }

        @Test
        fun `encrypt handles empty plaintext`() = runTest {
            setupKeysAndBundle()
            val result = NativeSessionManager.encryptMessage("bob", ByteArray(0))
            assertNotNull(result)
        }

        @Test
        fun `encrypt handles large plaintext`() = runTest {
            setupKeysAndBundle()
            val large = ByteArray(10000) { (it % 256).toByte() }
            val result = NativeSessionManager.encryptMessage("bob", large)
            assertNotNull(result)
            assertTrue(result!!.payload.isNotEmpty())
        }

        @Test
        fun `encrypt returns null when bundle missing`() = runTest {
            val result = NativeSessionManager.encryptMessage("nobody", "Hello".encodeToByteArray())
            assertNull(result)
        }

        @Test
        fun `encryptWithSessionKey returns null when no session exists`() = runTest {
            val result = NativeSessionManager.encryptWithSessionKey("nobody", "data".encodeToByteArray())
            assertNull(result)
        }

        @Test
        fun `encryptWithSessionKey works with existing session`() = runTest {
            setupKeysAndBundle()
            NativeSessionManager.encryptMessage("bob", "init".encodeToByteArray())
            val result = NativeSessionManager.encryptWithSessionKey("bob", "data".encodeToByteArray())
            assertNotNull(result)
        }
    }

    @Nested
    @DisplayName("Session Management")
    inner class SessionManagementTests {

        @Test
        fun `hasSession returns false before encryption`() = runTest {
            assertFalse(NativeSessionManager.hasSession("nobody"))
        }

        @Test
        fun `hasSession returns true after successful encryption`() = runTest {
            setupKeysAndBundle()
            NativeSessionManager.encryptMessage("bob", "Hi".encodeToByteArray())
            assertTrue(NativeSessionManager.hasSession("bob"))
        }

        @Test
        fun `hasSession returns true after archiveSession (archive keeps session)`() = runTest {
            setupKeysAndBundle()
            NativeSessionManager.encryptMessage("bob", "Hi".encodeToByteArray())
            assertTrue(NativeSessionManager.hasSession("bob"))
            NativeSessionManager.archiveSession("bob")
            assertTrue(NativeSessionManager.hasSession("bob"))
        }

        @Test
        fun `deleteSession marks session as archived (still has session)`() = runTest {
            setupKeysAndBundle()
            NativeSessionManager.encryptMessage("bob", "Hi".encodeToByteArray())
            assertTrue(NativeSessionManager.hasSession("bob"))
            NativeSessionManager.deleteSession("bob")
            assertTrue(NativeSessionManager.hasSession("bob"))
        }
    }

    @Nested
    @DisplayName("Decryption")
    inner class DecryptionTests {

        @Test
        fun `decryptMessage returns null when no session exists`() = runTest {
            val result = NativeSessionManager.decryptMessage("nobody", ByteArray(100))
            assertNull(result)
        }

        @Test
        fun `decryptMessage with too-short payload returns null`() = runTest {
            setupKeysAndBundle()
            NativeSessionManager.encryptMessage("bob", "test".encodeToByteArray())
            val result = NativeSessionManager.decryptMessage("bob", ByteArray(5))
            assertNull(result)
        }

        @Test
        fun `decryptPreKeyMessage with too-short payload returns null`() = runTest {
            val result = NativeSessionManager.decryptPreKeyMessage("nobody", ByteArray(2))
            assertNull(result)
        }
    }
}
