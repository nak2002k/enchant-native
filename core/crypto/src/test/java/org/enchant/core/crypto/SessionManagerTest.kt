package org.enchant.core.crypto

import io.mockk.every
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import org.enchant.core.base.SecurePreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("SessionManager")
class SessionManagerTest {
    private val testUserId = "test_user_123"

    @BeforeEach
    fun setUp() = runTest {
        mockkObject(SecurePreferences)
        every { SecurePreferences.putString(any(), any()) } returns Unit
        every { SecurePreferences.getString(any(), any()) } answers { null }
        every { SecurePreferences.getString(any()) } answers { null }

        KeyManager.init()
        KeyManager.generateAndUploadKeys()
        SessionManager.init()
        val remoteIk = CryptoHelper.generateEd25519KeyPair()
        SessionManager.setIdentityKey(testUserId, remoteIk.publicKey)
    }

    @AfterEach
    fun tearDown() = runTest {
        SessionManager.deleteSession(testUserId)
    }

    @Test @DisplayName("first encrypt establishes session")
    fun `encrypt establishes session`() = runTest {
        val result = SessionManager.encryptMessage(testUserId, "Hello".encodeToByteArray())
        assertNotNull(result)
        assertTrue(SessionManager.hasSession(testUserId))
    }

    @Test @DisplayName("hasSession returns false initially")
    fun `hasSession initially false`() = runTest {
        assertFalse(SessionManager.hasSession(testUserId))
    }

    @Test @DisplayName("deleteSession removes session")
    fun `deleteSession removes session`() = runTest {
        SessionManager.encryptMessage(testUserId, "test".encodeToByteArray())
        assertTrue(SessionManager.hasSession(testUserId))
        SessionManager.deleteSession(testUserId)
        assertFalse(SessionManager.hasSession(testUserId))
    }

    @Test @DisplayName("decrypt without session returns null")
    fun `decrypt without session`() = runTest {
        val result = SessionManager.decryptMessage("unknown",
            EncryptedPayload(org.enchant.protos.EnvelopeProtos.Envelope.Type.DOUBLE_RATCHET, ByteArray(16)))
        assertNull(result)
    }
}
