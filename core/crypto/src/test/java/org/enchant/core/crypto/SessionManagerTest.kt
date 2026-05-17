package org.enchant.core.crypto

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.enchant.protos.EnvelopeProtos

@DisplayName("SessionManager")
class SessionManagerTest {

    @BeforeEach
    fun setUp() {
        runTest {
            SessionManager.init()
            KeyManager.init()
            KeyManager.generateAndUploadKeys()
        }
    }

    @Test
    @DisplayName("encryptMessage returns non-null payload for known recipient")
    fun `encryptMessage returns payload`() = runTest {
        SessionManager.setIdentityKey("user1", CryptoHelper.generateEd25519KeyPair().publicKey)

        val result = SessionManager.encryptMessage("user1", "Hello".encodeToByteArray())
        assertNotNull(result)
        assertTrue(result!!.payload.isNotEmpty())
    }

    @Test
    @DisplayName("hasSession returns true after encrypting to recipient")
    fun `hasSession after encrypt`() = runTest {
        SessionManager.setIdentityKey("alice", CryptoHelper.generateEd25519KeyPair().publicKey)
        SessionManager.encryptMessage("alice", "Hi".encodeToByteArray())
        assertTrue(SessionManager.hasSession("alice"))
    }

    @Test
    @DisplayName("deleteSession removes session")
    fun `deleteSession`() = runTest {
        SessionManager.setIdentityKey("temp", CryptoHelper.generateEd25519KeyPair().publicKey)
        SessionManager.encryptMessage("temp", "Hi".encodeToByteArray())
        assertTrue(SessionManager.hasSession("temp"))

        SessionManager.deleteSession("temp")
        assertFalse(SessionManager.hasSession("temp"))
    }

    @Test
    @DisplayName("EncryptedPayload contains properly formatted header+ciphertext")
    fun `encrypted payload has header format`() = runTest {
        SessionManager.setIdentityKey("format-test", CryptoHelper.generateEd25519KeyPair().publicKey)

        SessionManager.encryptMessage("format-test", "first".encodeToByteArray())

        val result = SessionManager.encryptMessage("format-test", "second".encodeToByteArray())
        assertNotNull(result)
        val payload = result!!.payload
        assertTrue(payload.size > 4, "Payload must have at least 4-byte header size prefix")
    }

    @Test
    @DisplayName("archiveSession removes session without cleanup")
    fun `archiveSession`() = runTest {
        SessionManager.setIdentityKey("arch-test", CryptoHelper.generateEd25519KeyPair().publicKey)
        SessionManager.encryptMessage("arch-test", "Hello".encodeToByteArray())
        assertTrue(SessionManager.hasSession("arch-test"))

        SessionManager.archiveSession("arch-test")
        assertFalse(SessionManager.hasSession("arch-test"))
    }

    @Test
    @DisplayName("getSafetyNumber returns formatted string for known identity")
    fun `getSafetyNumber`() = runTest {
        val bobIk = CryptoHelper.generateEd25519KeyPair()
        SessionManager.setIdentityKey("bob-safety", bobIk.publicKey)
        val safetyNum = SessionManager.getSafetyNumber("bob-safety")
        assertNotEquals("UNVERIFIED", safetyNum)
        assertTrue(safetyNum.contains("-"))
    }
}
