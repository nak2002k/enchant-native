package org.enchant.core.base

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePreferences {

    @Volatile
    private var prefs: SharedPreferences? = null
    private var _isEncrypted: Boolean = false

    val isEncrypted: Boolean get() = _isEncrypted

    @Synchronized
    fun init(context: Context, allowUnencryptedFallback: Boolean = false) {
        if (prefs != null) return
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            prefs = EncryptedSharedPreferences.create(
                context,
                "enchant_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            _isEncrypted = true
        } catch (e: Exception) {
            if (allowUnencryptedFallback) {
                Log.w("SecurePreferences", "Encrypted init failed, falling back to plaintext: ${e.message}")
                prefs = context.getSharedPreferences("enchant_secure_prefs", Context.MODE_PRIVATE)
                _isEncrypted = false
            } else {
                throw IllegalStateException("Failed to init encrypted preferences: ${e.message}", e)
            }
        }
    }

    private fun getPrefs(): SharedPreferences? = prefs

    fun putString(key: String, value: String) {
        getPrefs()?.edit()?.putString(key, value)?.apply()
    }

    fun putStringSync(key: String, value: String): Boolean {
        return getPrefs()?.edit()?.putString(key, value)?.commit() ?: false
    }

    fun getString(key: String, default: String? = null): String? {
        return getPrefs()?.getString(key, default) ?: default
    }

    fun putStringSet(key: String, value: Set<String>) {
        getPrefs()?.edit()?.putStringSet(key, value)?.apply()
    }

    fun putStringSetSync(key: String, value: Set<String>): Boolean {
        return getPrefs()?.edit()?.putStringSet(key, value)?.commit() ?: false
    }

    fun getStringSet(key: String, default: Set<String>? = null): Set<String>? {
        return getPrefs()?.getStringSet(key, default) ?: default
    }

    fun putInt(key: String, value: Int) {
        getPrefs()?.edit()?.putInt(key, value)?.apply()
    }

    fun putIntSync(key: String, value: Int): Boolean {
        return getPrefs()?.edit()?.putInt(key, value)?.commit() ?: false
    }

    fun getInt(key: String, default: Int = 0): Int {
        return getPrefs()?.getInt(key, default) ?: default
    }

    fun putLong(key: String, value: Long) {
        getPrefs()?.edit()?.putLong(key, value)?.apply()
    }

    fun putLongSync(key: String, value: Long): Boolean {
        return getPrefs()?.edit()?.putLong(key, value)?.commit() ?: false
    }

    fun getLong(key: String, default: Long = 0): Long {
        return getPrefs()?.getLong(key, default) ?: default
    }

    fun putBoolean(key: String, value: Boolean) {
        getPrefs()?.edit()?.putBoolean(key, value)?.apply()
    }

    fun putBooleanSync(key: String, value: Boolean): Boolean {
        return getPrefs()?.edit()?.putBoolean(key, value)?.commit() ?: false
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return getPrefs()?.getBoolean(key, default) ?: default
    }

    fun getBoolean(key: String, default: Boolean?): Boolean? {
        val p = getPrefs() ?: return default
        return if (p.contains(key)) p.getBoolean(key, default ?: false) else default
    }

    fun putFloat(key: String, value: Float) {
        getPrefs()?.edit()?.putFloat(key, value)?.apply()
    }

    fun putFloatSync(key: String, value: Float): Boolean {
        return getPrefs()?.edit()?.putFloat(key, value)?.commit() ?: false
    }

    fun getFloat(key: String, default: Float = 0f): Float {
        return getPrefs()?.getFloat(key, default) ?: default
    }

    fun getFloat(key: String, default: Float?): Float? {
        val p = getPrefs() ?: return default
        return if (p.contains(key)) p.getFloat(key, default ?: 0f) else default
    }

    fun remove(key: String) {
        getPrefs()?.edit()?.remove(key)?.apply()
    }

    fun removeSync(key: String): Boolean {
        return getPrefs()?.edit()?.remove(key)?.commit() ?: false
    }

    fun clearAll() {
        getPrefs()?.edit()?.clear()?.apply()
    }

    fun clearAllSync(): Boolean {
        return getPrefs()?.edit()?.clear()?.commit() ?: false
    }

    fun contains(key: String): Boolean {
        return getPrefs()?.contains(key) ?: false
    }
}
