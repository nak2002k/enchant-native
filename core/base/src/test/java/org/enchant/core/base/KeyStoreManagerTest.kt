package org.enchant.core.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(AndroidJUnit4::class)
class KeyStoreManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetKeyStoreManager()
    }

    @After
    fun tearDown() {
        resetKeyStoreManager()
    }

    private fun resetKeyStoreManager() {
        val initField = KeyStoreManager::class.java.getDeclaredField("initialized")
        initField.isAccessible = true
        initField.set(KeyStoreManager, false)

        val hwField = KeyStoreManager::class.java.getDeclaredField("_isHardwareBacked")
        hwField.isAccessible = true
        hwField.set(KeyStoreManager, false)
    }

    @Test
    fun `key aliases are correct constants`() {
        assertEquals("enchant_identity_key", KeyStoreManager.KEY_ALIAS_IDENTITY)
        assertEquals("enchant_db_key", KeyStoreManager.KEY_ALIAS_DB_ENCRYPTION)
    }

    @Test
    fun `isHardwareBacked throws before init`() {
        assertThrows(IllegalStateException::class.java) {
            KeyStoreManager.isHardwareBacked()
        }
    }

    @Test
    fun `keyExists returns false for non-existent key`() {
        assertFalse(KeyStoreManager.keyExists("nonexistent-key"))
    }

    @Test
    fun `deleteKey does not throw for non-existent key`() = runTest {
        KeyStoreManager.deleteKey("nonexistent-key")
    }

    @Test
    fun `sign returns null for non-existent key`() = runTest {
        val result = KeyStoreManager.sign("nonexistent-key", byteArrayOf(1, 2, 3))
        assertNull(result)
    }

    @Test
    fun `verify returns false for non-existent key`() = runTest {
        val result = KeyStoreManager.verify("nonexistent-key", byteArrayOf(1), byteArrayOf(2))
        assertFalse(result)
    }

    @Test
    fun `encrypt returns null for non-existent key`() = runTest {
        val result = KeyStoreManager.encrypt("nonexistent-key", byteArrayOf(1, 2, 3))
        assertNull(result)
    }

    @Test
    fun `decrypt returns null for non-existent key`() = runTest {
        val result = KeyStoreManager.decrypt("nonexistent-key", byteArrayOf(1, 2, 3))
        assertNull(result)
    }

    @Test
    fun `decrypt returns null for ciphertext shorter than 13 bytes`() = runTest {
        val result = KeyStoreManager.decrypt("any-key", byteArrayOf(1, 2))
        assertNull(result)
    }
}
