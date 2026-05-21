package org.enchant.core.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Plaintext SharedPreferences for values that MUST exist before the encrypted store is available.
 *
 * WARNING: Values stored here are NOT encrypted.
 * Rule of thumb: If you're not comfortable logging it, don't put it here.
 *
 * Use cases:
 * - Database encryption key existence flag (not the key itself)
 * - First-launch detection before DB is initialized
 * - Migration version tracking
 * - SMS/MMS migration offsets
 * - Values needed during the boot sequence before SQLCipher is unlocked
 *
 * All other values should go through [EnchantStore].
 */
class PlainTextSharedPrefsDataStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "enchant_plaintext",
        Context.MODE_PRIVATE
    )

    fun getString(key: String, defaultValue: String? = null): String? {
        return prefs.getString(key, defaultValue)
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return prefs.getInt(key, defaultValue)
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return prefs.getLong(key, defaultValue)
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    fun getFloat(key: String, defaultValue: Float = 0f): Float {
        return prefs.getFloat(key, defaultValue)
    }

    fun contains(key: String): Boolean {
        return prefs.contains(key)
    }

    fun putString(key: String, value: String?) {
        prefs.edit { putString(key, value) }
    }

    fun putInt(key: String, value: Int) {
        prefs.edit { putInt(key, value) }
    }

    fun putLong(key: String, value: Long) {
        prefs.edit { putLong(key, value) }
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }

    fun putFloat(key: String, value: Float) {
        prefs.edit { putFloat(key, value) }
    }

    fun remove(key: String) {
        prefs.edit { remove(key) }
    }

    fun clearAll() {
        prefs.edit { clear() }
    }

    fun getAll(): Map<String, *> = prefs.all
}
