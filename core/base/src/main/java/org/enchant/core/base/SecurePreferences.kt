package org.enchant.core.base

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.nio.ByteBuffer

object SecurePreferences {
    @Volatile
    private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
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
        } catch (e: Exception) {
            prefs = context.getSharedPreferences("enchant_secure_prefs", Context.MODE_PRIVATE)
        }
    }

    private fun getPrefs(): SharedPreferences? {
        val current = prefs
        return current
    }

    fun putString(key: String, value: String) {
        getPrefs()?.edit()?.putString(key, value)?.apply()
    }

    fun getString(key: String, default: String? = null): String? {
        return getPrefs()?.getString(key, default) ?: default
    }

    fun putInt(key: String, value: Int) {
        getPrefs()?.edit()?.putInt(key, value)?.apply()
    }

    fun getInt(key: String, default: Int = 0): Int {
        return getPrefs()?.getInt(key, default) ?: default
    }

    fun putLong(key: String, value: Long) {
        getPrefs()?.edit()?.putLong(key, value)?.apply()
    }

    fun getLong(key: String, default: Long = 0): Long {
        return getPrefs()?.getLong(key, default) ?: default
    }

    fun putBoolean(key: String, value: Boolean) {
        getPrefs()?.edit()?.putBoolean(key, value)?.apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return getPrefs()?.getBoolean(key, default) ?: default
    }

    fun putFloat(key: String, value: Float) {
        getPrefs()?.edit()?.putFloat(key, value)?.apply()
    }

    fun getFloat(key: String, default: Float = 0f): Float {
        return getPrefs()?.getFloat(key, default) ?: default
    }

    fun remove(key: String) {
        getPrefs()?.edit()?.remove(key)?.apply()
    }

    fun clearAll() {
        getPrefs()?.edit()?.clear()?.apply()
    }

    fun contains(key: String): Boolean {
        return getPrefs()?.contains(key) ?: false
    }
}
