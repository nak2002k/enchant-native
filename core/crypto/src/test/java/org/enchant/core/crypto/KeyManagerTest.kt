package org.enchant.core.crypto

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.enchant.core.base.SecurePreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("KeyManager")
class KeyManagerTest {

    @BeforeEach
    fun setUp() = runTest {
        mockkObject(SecurePreferences)
        every { SecurePreferences.getString(any(), any()) } returns null
        every { SecurePreferences.getString(any()) } returns null
        every { SecurePreferences.putString(any(), any()) } returns Unit
        every { SecurePreferences.getLong(any(), any()) } returns 0L
        every { SecurePreferences.putLong(any(), any()) } returns Unit
        every { SecurePreferences.putBoolean(any(), any()) } returns Unit
        every { SecurePreferences.getBoolean(any(), any()) } returns false
        KeyManager.init()
        KeyManager.generateAndUploadKeys()
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(SecurePreferences)
    }

    @Nested @DisplayName("key generation")
    inner class KeyGenerationTest {
        @Test @DisplayName("hasKeys returns true after generation")
        fun `has keys after gen`() = runTest {
            assertTrue(KeyManager.hasKeys())
        }

        @Test @DisplayName("getIdentityKeyPair returns non-null after generation")
        fun `identity key pair exists`() = runTest {
            val pair = KeyManager.getIdentityKeyPair()
            assertNotNull(pair)
            assertEquals(32, pair!!.publicKey.size)
        }

        @Test @DisplayName("getIdentityPublicKeyBase64 returns non-empty string")
        fun `identity key base64`() = runTest {
            val b64 = KeyManager.getIdentityPublicKeyBase64()
            assertNotNull(b64)
            assertTrue(b64!!.isNotEmpty())
        }

        @Test @DisplayName("identity key is deterministic for same session")
        fun `identity key stable`() = runTest {
            val first = KeyManager.getIdentityPublicKeyBase64()
            val second = KeyManager.getIdentityPublicKeyBase64()
            assertEquals(first, second)
        }
    }

    @Nested @DisplayName("key bundle")
    inner class KeyBundleTest {
        @Test @DisplayName("fetchKeyBundle returns null without network")
        fun `fetch bundle null`() = runTest {
            val bundle = KeyManager.fetchKeyBundle("nonexistent-user")
            assertNull(bundle)
        }
    }

    @Nested @DisplayName("init with stored keys")
    inner class InitWithStoredKeysTest {
        @Test @DisplayName("init does not throw when no stored keys")
        fun `init clean`() = runTest {
            KeyManager.init()
            assertTrue(true)
        }

        @Test @DisplayName("double init is safe")
        fun `double init`() = runTest {
            KeyManager.init()
            KeyManager.init()
            assertTrue(true)
        }
    }
}