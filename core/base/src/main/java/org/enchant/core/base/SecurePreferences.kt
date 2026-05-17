package org.enchant.core.base

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.nio.ByteBuffer

object SecurePreferences {
    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
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
    }

    fun putString(key: String, value: String) {
        if (prefs == null) return
        prefs!!.edit().putString(key, value).apply()
    }

    fun getString(key: String, default: String? = null): String? {
        if (prefs == null) return default
        return prefs!!.getString(key, default)
    }

    fun putInt(key: String, value: Int) {
        if (prefs == null) return
        prefs!!.edit().putInt(key, value).apply()
    }

    fun getInt(key: String, default: Int = 0): Int {
        if (prefs == null) return default
        return prefs!!.getInt(key, default)
    }

    fun putLong(key: String, value: Long) {
        if (prefs == null) return
        prefs!!.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, default: Long = 0): Long {
        if (prefs == null) return default
        return prefs!!.getLong(key, default)
    }

    fun putBoolean(key: String, value: Boolean) {
        if (prefs == null) return
        prefs!!.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        if (prefs == null) return default
        return prefs!!.getBoolean(key, default)
    }

    fun remove(key: String) {
        if (prefs == null) return
        prefs!!.edit().remove(key).apply()
    }

    fun clearAll() {
        if (prefs == null) return
        prefs!!.edit().clear().apply()
    }

    fun contains(key: String): Boolean {
        if (prefs == null) return false
        return prefs!!.contains(key)
    }
}
