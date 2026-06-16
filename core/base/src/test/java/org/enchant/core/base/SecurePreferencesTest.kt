package org.enchant.core.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class SecurePreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetPrefs()
        val plainPrefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        plainPrefs.edit().clear().commit()
        val prefsField = SecurePreferences::class.java.getDeclaredField("prefs")
        prefsField.isAccessible = true
        prefsField.set(SecurePreferences, plainPrefs)
    }

    @After
    fun tearDown() {
        SecurePreferences.clearAll()
    }

    private fun resetPrefs() {
        val field = SecurePreferences::class.java.getDeclaredField("prefs")
        field.isAccessible = true
        field.set(SecurePreferences, null)
    }

    @Test
    fun `init is idempotent`() {
        SecurePreferences.init(context)
        SecurePreferences.init(context)
        SecurePreferences.putString("test", "value")
        assertEquals("value", SecurePreferences.getString("test"))
    }

    @Test
    fun `putString and getString`() {
        SecurePreferences.putString("key", "hello")
        assertEquals("hello", SecurePreferences.getString("key"))
    }

    @Test
    fun `getString returns default when missing`() {
        assertEquals("default", SecurePreferences.getString("missing", "default"))
    }

    @Test
    fun `getString returns null when missing and no default`() {
        assertNull(SecurePreferences.getString("missing"))
    }

    @Test
    fun `putInt and getInt`() {
        SecurePreferences.putInt("count", 42)
        assertEquals(42, SecurePreferences.getInt("count"))
    }

    @Test
    fun `getInt returns default when missing`() {
        assertEquals(99, SecurePreferences.getInt("missing", 99))
    }

    @Test
    fun `getInt returns zero when missing and no default`() {
        assertEquals(0, SecurePreferences.getInt("missing"))
    }

    @Test
    fun `putLong and getLong`() {
        SecurePreferences.putLong("timestamp", 1234567890L)
        assertEquals(1234567890L, SecurePreferences.getLong("timestamp"))
    }

    @Test
    fun `getLong returns default when missing`() {
        assertEquals(999L, SecurePreferences.getLong("missing", 999L))
    }

    @Test
    fun `getLong returns zero when missing and no default`() {
        assertEquals(0L, SecurePreferences.getLong("missing"))
    }

    @Test
    fun `putBoolean and getBoolean`() {
        SecurePreferences.putBoolean("enabled", true)
        assertTrue(SecurePreferences.getBoolean("enabled"))
    }

    @Test
    fun `getBoolean returns false when missing`() {
        SecurePreferences.putBoolean("disabled", false)
        assertFalse(SecurePreferences.getBoolean("disabled"))
    }

    @Test
    fun `getBoolean returns default when missing`() {
        assertTrue(SecurePreferences.getBoolean("missing", true))
    }

    @Test
    fun `getBoolean returns false when missing and no default`() {
        assertFalse(SecurePreferences.getBoolean("missing"))
    }

    @Test
    fun `putFloat and getFloat`() {
        SecurePreferences.putFloat("size", 1.5f)
        assertEquals(1.5f, SecurePreferences.getFloat("size"), 0.001f)
    }

    @Test
    fun `getFloat returns default when missing`() {
        assertEquals(2.0f, SecurePreferences.getFloat("missing", 2.0f), 0.001f)
    }

    @Test
    fun `getFloat returns zero when missing and no default`() {
        assertEquals(0f, SecurePreferences.getFloat("missing"), 0.001f)
    }

    @Test
    fun `remove deletes a key`() {
        SecurePreferences.putString("key", "value")
        SecurePreferences.remove("key")
        assertNull(SecurePreferences.getString("key"))
    }

    @Test
    fun `clearAll removes all keys`() {
        SecurePreferences.putString("a", "1")
        SecurePreferences.putInt("b", 2)
        SecurePreferences.putBoolean("c", true)
        SecurePreferences.clearAll()
        assertNull(SecurePreferences.getString("a"))
        assertEquals(0, SecurePreferences.getInt("b"))
        assertFalse(SecurePreferences.getBoolean("c"))
    }

    @Test
    fun `contains returns true for existing key`() {
        SecurePreferences.putString("key", "value")
        assertTrue(SecurePreferences.contains("key"))
    }

    @Test
    fun `contains returns false for missing key`() {
        assertFalse(SecurePreferences.contains("missing"))
    }

    @Test
    fun `overwrite existing key updates value`() {
        SecurePreferences.putString("key", "first")
        SecurePreferences.putString("key", "second")
        assertEquals("second", SecurePreferences.getString("key"))
    }

    @Test
    fun `stores and retrieves multiple types independently`() {
        SecurePreferences.putString("str", "hello")
        SecurePreferences.putInt("int", 42)
        SecurePreferences.putLong("long", 123L)
        SecurePreferences.putBoolean("bool", true)
        SecurePreferences.putFloat("float", 3.14f)

        assertEquals("hello", SecurePreferences.getString("str"))
        assertEquals(42, SecurePreferences.getInt("int"))
        assertEquals(123L, SecurePreferences.getLong("long"))
        assertTrue(SecurePreferences.getBoolean("bool"))
        assertEquals(3.14f, SecurePreferences.getFloat("float"), 0.001f)
    }
}
