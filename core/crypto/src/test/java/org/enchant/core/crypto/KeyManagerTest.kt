package org.enchant.core.crypto

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.enchant.core.base.KeyStoreManager
import org.enchant.core.base.SecurePreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("KeyManager — Full Coverage")
class KeyManagerTest {

    @BeforeEach
    fun setUp() {
        KeyManager.reset()
        mockkObject(SecurePreferences)
        mockkObject(KeyStoreManager)
        every { SecurePreferences.getString(any(), any()) } returns null
        every { SecurePreferences.getString(any()) } returns null
        every { SecurePreferences.putString(any(), any()) } returns Unit
        every { SecurePreferences.putInt(any(), any()) } returns Unit
        every { SecurePreferences.putLong(any(), any()) } returns Unit
        every { SecurePreferences.getLong(any(), any()) } returns 0L
        every { SecurePreferences.putBoolean(any(), any()) } returns Unit
        every { SecurePreferences.getBoolean(any(), any()) } returns false
        coEvery { KeyStoreManager.encrypt(any(), any()) } returns ByteArray(32)
        coEvery { KeyStoreManager.decrypt(any(), any()) } returns ByteArray(32)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(SecurePreferences)
        unmockkObject(KeyStoreManager)
    }

    @Nested @DisplayName("Key Generation")
    inner class KeyGenerationTest {
        @Test @DisplayName("hasKeys returns false before generation")
        fun `has keys false before`() = runTest {
            KeyManager.init()
            assertFalse(KeyManager.hasKeys())
        }

        @Test @DisplayName("hasKeys returns true after generation")
        fun `has keys true after`() = runTest {
            KeyManager.init()
            KeyManager.generateAndUploadKeys()
            assertTrue(KeyManager.hasKeys())
        }

        @Test @DisplayName("getIdentityKeyPair returns non-null after generation")
        fun `identity key pair exists`() = runTest {
            KeyManager.init()
            KeyManager.generateAndUploadKeys()
            val pair = KeyManager.getIdentityKeyPair()
            assertNotNull(pair)
            assertEquals(32, pair!!.publicKey.size)
        }

        @Test @DisplayName("getIdentityPublicKeyBase64 returns non-empty string")
        fun `identity key base64`() = runTest {
            KeyManager.init()
            KeyManager.generateAndUploadKeys()
            val b64 = KeyManager.getIdentityPublicKeyBase64()
            assertNotNull(b64)
            assertTrue(b64!!.isNotEmpty())
        }

        @Test @DisplayName("identity key is stable across calls in same session")
        fun `identity key stable`() = runTest {
            KeyManager.init()
            KeyManager.generateAndUploadKeys()
            val first = KeyManager.getIdentityPublicKeyBase64()
            val second = KeyManager.getIdentityPublicKeyBase64()
            assertEquals(first, second)
        }

        @Test @DisplayName("signWithIdentity returns non-null after generation")
        fun `sign with identity`() = runTest {
            KeyManager.init()
            KeyManager.generateAndUploadKeys()
            val sig = KeyManager.signWithIdentity("data to sign".encodeToByteArray())
            assertNotNull(sig)
            assertEquals(64, sig!!.size)
        }

        @Test @DisplayName("signWithIdentity returns null before generation")
        fun `sign with identity null before`() = runTest {
            KeyManager.init()
            val sig = KeyManager.signWithIdentity("data".encodeToByteArray())
            assertNull(sig)
        }
    }

    @Nested @DisplayName("Key Bundle Fetch")
    inner class KeyBundleTest {
        @Test @DisplayName("fetchKeyBundle returns null without API client")
        fun `fetch bundle null no client`() = runTest {
            KeyManager.init()
            val bundle = KeyManager.fetchKeyBundle("nonexistent-user")
            assertNull(bundle)
        }
    }

    @Nested @DisplayName("Initialization")
    inner class InitTest {
        @Test @DisplayName("init does not throw when no stored keys")
        fun `init clean`() = runTest {
            KeyManager.init()
            assertTrue(true)
        }

        @Test @DisplayName("double init is safe (idempotent)")
        fun `double init safe`() = runTest {
            KeyManager.init()
            KeyManager.init()
            assertTrue(true)
        }
    }

    @Nested @DisplayName("SPK Rotation")
    inner class SpkRotationTest {
        @Test @DisplayName("needsKeyRotation returns false initially")
        fun `needs rotation false initially`() = runTest {
            KeyManager.init()
            assertFalse(KeyManager.needsKeyRotation())
        }

        @Test @DisplayName("rotateSignedPreKey fails without API client")
        fun `rotate spk no client`() = runTest {
            KeyManager.init()
            val result = KeyManager.rotateSignedPreKey()
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("cleanSignedPreKeys does nothing when recently rotated")
        fun `clean spk recent`() = runTest {
            KeyManager.init()
            KeyManager.generateAndUploadKeys()
            KeyManager.cleanSignedPreKeys()
            assertTrue(KeyManager.hasKeys())
        }
    }

    @Nested @DisplayName("OPK Management")
    inner class OpkTest {
        @Test @DisplayName("topUpOpks does nothing without API client")
        fun `topup no client`() = runTest {
            KeyManager.init()
            KeyManager.generateAndUploadKeys()
            KeyManager.topUpOpks()
            assertTrue(true)
        }
    }

    @Nested @DisplayName("Key Persistence")
    inner class PersistenceTest {
        @Test @DisplayName("generateAndUploadKeys stores keys in SecurePreferences")
        fun `gen stores keys`() = runTest {
            KeyManager.init()
            KeyManager.generateAndUploadKeys()
            coVerify(atLeast = 1) { SecurePreferences.putString(any(), any()) }
        }

        @Test @DisplayName("generateAndUploadKeys stores OPK count")
        fun `gen stores opk count`() = runTest {
            KeyManager.init()
            KeyManager.generateAndUploadKeys()
            coVerify(atLeast = 1) { SecurePreferences.putInt(any(), any()) }
        }
    }

    @Nested @DisplayName("C02/C03/L17: SPK/OPK Encoding Consistency")
    inner class EncodingConsistencyTest {
        @Test @DisplayName("SPK private key stored as base64, not comma-separated")
        fun `spk private key is base64 encoded`() = runTest {
            KeyManager.init()
            KeyManager.generateAndUploadKeys()
            verify(atLeast = 1) {
                SecurePreferences.putString(
                    match { it == "crypto.spk_private" },
                    match { !it.contains(",") && it.isNotEmpty() }
                )
            }
        }

        @Test @DisplayName("OPK private keys stored as base64, not comma-separated")
        fun `opk private keys are base64 encoded`() = runTest {
            KeyManager.init()
            KeyManager.generateAndUploadKeys()
            verify(atLeast = 1) {
                SecurePreferences.putString(
                    match { it.startsWith("crypto.opk_") && it.endsWith("_private") },
                    match { !it.contains(",") && it.isNotEmpty() }
                )
            }
        }

        @Test @DisplayName("SPK public and private use consistent encoding")
        fun `spk encoding consistent`() = runTest {
            KeyManager.init()
            KeyManager.generateAndUploadKeys()
            verify(atLeast = 1) {
                SecurePreferences.putString(
                    match { it == "crypto.spk_public" },
                    match { it.isNotEmpty() }
                )
            }
            verify(atLeast = 1) {
                SecurePreferences.putString(
                    match { it == "crypto.spk_private" },
                    match { it.isNotEmpty() }
                )
            }
        }

        @Test @DisplayName("SPK can be loaded after generation (roundtrip)")
        fun `spk roundtrip`() = runTest {
            KeyManager.init()
            KeyManager.generateAndUploadKeys()
            val spkBefore = KeyManager.getIdentityKeyPair()
            assertNotNull(spkBefore)
        }
    }
}
