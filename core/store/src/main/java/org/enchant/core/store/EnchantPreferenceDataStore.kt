package org.enchant.core.store

import androidx.preference.PreferenceDataStore

/**
 * Bridge between AndroidX Preference library and [KeyValueStorage].
 *
 * Allows using [EnchantStore] as the backing store for AndroidX Preference screens:
 * ```
 * preferenceManager.preferenceDataStore = EnchantStore.getPreferenceDataStore()
 * ```
 */
class EnchantPreferenceDataStore(
    private val store: KeyValueStorage
) : PreferenceDataStore() {

    override fun getString(key: String, defValue: String?): String? {
        return store.getString(key, defValue)
    }

    override fun putString(key: String, value: String?) {
        store.putString(key, value)
    }

    override fun getInt(key: String, defValue: Int): Int {
        return store.getInt(key, defValue)
    }

    override fun putInt(key: String, value: Int) {
        store.putInt(key, value)
    }

    override fun getLong(key: String, defValue: Long): Long {
        return store.getLong(key, defValue)
    }

    override fun putLong(key: String, value: Long) {
        store.putLong(key, value)
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return store.getFloat(key, defValue)
    }

    override fun putFloat(key: String, value: Float) {
        store.putFloat(key, value)
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return store.getBoolean(key, defValue)
    }

    override fun putBoolean(key: String, value: Boolean) {
        store.putBoolean(key, value)
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        val raw = store.getString(key)
        return if (raw != null) {
            raw.split("|").toMutableSet()
        } else {
            defValues
        }
    }

    override fun putStringSet(key: String, values: MutableSet<String>?) {
        store.putString(key, values?.joinToString("|"))
    }
}
