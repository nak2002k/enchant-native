package org.enchant.core.base

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("KeyStoreManager — Full Coverage")
class KeyStoreManagerTest {

    @BeforeEach
    fun setUp() {
        mockkObject(SecurePreferences)
        every { SecurePreferences.getString(any(), any()) } returns null
        every { SecurePreferences.getString(any()) } returns null
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(SecurePreferences)
        unmockkStatic(android.util.Log::class)
    }

    @Nested @DisplayName("Key Aliases")
    inner class KeyAliasesTest {
        @Test @DisplayName("KEY_ALIAS_IDENTITY is correct")
        fun `identity alias`() {
            assertEquals("enchant_identity_key", KeyStoreManager.KEY_ALIAS_IDENTITY)
        }

        @Test @DisplayName("KEY_ALIAS_DB_ENCRYPTION is correct")
        fun `db encryption alias`() {
            assertEquals("enchant_db_key", KeyStoreManager.KEY_ALIAS_DB_ENCRYPTION)
        }
    }

    @Nested @DisplayName("Hardware Backed")
    inner class HardwareBackedTest {
        @Test @DisplayName("isHardwareBacked returns false by default")
        fun `hardware backed default`() {
            assertFalse(KeyStoreManager.isHardwareBacked())
        }
    }

    @Nested @DisplayName("Key Exists")
    inner class KeyExistsTest {
        @Test @DisplayName("keyExists returns false for non-existent key")
        fun `key exists false`() {
            assertFalse(KeyStoreManager.keyExists("nonexistent"))
        }
    }

    @Nested @DisplayName("Delete Key")
    inner class DeleteKeyTest {
        @Test @DisplayName("deleteKey does not throw for non-existent key")
        fun `delete key non existent`() {
            // Should not throw
            kotlinx.coroutines.test.runTest {
                KeyStoreManager.deleteKey("nonexistent")
            }
        }
    }

    @Nested @DisplayName("Sign")
    inner class SignTest {
        @Test @DisplayName("sign returns null for non-existent key")
        fun `sign non existent`() {
            kotlinx.coroutines.test.runTest {
                val result = KeyStoreManager.sign("nonexistent", byteArrayOf(1, 2, 3))
                assertNull(result)
            }
        }
    }

    @Nested @DisplayName("Verify")
    inner class VerifyTest {
        @Test @DisplayName("verify returns false for non-existent key")
        fun `verify non existent`() {
            kotlinx.coroutines.test.runTest {
                val result = KeyStoreManager.verify("nonexistent", byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6))
                assertFalse(result)
            }
        }
    }

    @Nested @DisplayName("Encrypt")
    inner class EncryptTest {
        @Test @DisplayName("encrypt returns null for non-existent key")
        fun `encrypt non existent`() {
            kotlinx.coroutines.test.runTest {
                val result = KeyStoreManager.encrypt("nonexistent", byteArrayOf(1, 2, 3))
                assertNull(result)
            }
        }
    }

    @Nested @DisplayName("Decrypt")
    inner class DecryptTest {
        @Test @DisplayName("decrypt returns null for non-existent key")
        fun `decrypt non existent`() {
            kotlinx.coroutines.test.runTest {
                val result = KeyStoreManager.decrypt("nonexistent", byteArrayOf(1, 2, 3))
                assertNull(result)
            }
        }

        @Test @DisplayName("decrypt returns null for too-short ciphertext")
        fun `decrypt short ciphertext`() {
            kotlinx.coroutines.test.runTest {
                val result = KeyStoreManager.decrypt("nonexistent", byteArrayOf(1, 2))
                assertNull(result)
            }
        }
    }
}
