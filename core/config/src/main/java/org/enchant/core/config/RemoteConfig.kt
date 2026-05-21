package org.enchant.core.config

object ConfigValues {
    private const val KEY_REMOTE_CONFIG = "remote_config"
    private const val KEY_PENDING_CONFIG = "pending_remote_config"
    private const val KEY_LAST_FETCH_TIME = "remote_config_last_fetch_time"
    private const val KEY_ETAG = "etag"

    private var store: KeyValueStore? = null

    fun setStore(keyValueStore: KeyValueStore) {
        store = keyValueStore
    }

    fun getCurrentConfig(): String? = store?.getString(KEY_REMOTE_CONFIG)

    fun setCurrentConfig(value: String) {
        store?.beginWrite()?.putString(KEY_REMOTE_CONFIG, value)?.apply()
    }

    fun getPendingConfig(): String? = store?.getString(KEY_PENDING_CONFIG) ?: getCurrentConfig()

    fun setPendingConfig(value: String) {
        store?.beginWrite()?.putString(KEY_PENDING_CONFIG, value)?.apply()
    }

    fun getLastFetchTime(): Long = store?.getLong(KEY_LAST_FETCH_TIME, 0) ?: 0L

    fun setLastFetchTime(time: Long) {
        store?.beginWrite()?.putLong(KEY_LAST_FETCH_TIME, time)?.apply()
    }

    fun getETag(): String? = store?.getString(KEY_ETAG) ?: ""

    fun setETag(etag: String) {
        store?.beginWrite()?.putString(KEY_ETAG, etag)?.apply()
    }
}

object RemoteConfig {
    private val defaults = mapOf(
        "message_retention_days" to "30",
        "max_group_size" to "500",
        "max_media_size_mb" to "128",
        "prekey_rotation_days" to "30",
        "opk_batch_size" to "100",
        "opk_min_count" to "10",
        "opk_cleanup_days" to "90",
        "max_edits_per_message" to "2",
        "disappear_timer_max" to "7776000"
    )

    fun getString(key: String, default: String = defaults[key] ?: ""): String {
        val pending = ConfigValues.getPendingConfig()
        if (pending != null && pending.isNotEmpty()) {
            return parseConfigValue(pending, key) ?: defaults[key] ?: default
        }
        return defaults[key] ?: default
    }

    fun getInt(key: String, default: Int = 0): Int {
        val pending = ConfigValues.getPendingConfig()
        if (pending != null && pending.isNotEmpty()) {
            return parseConfigInt(pending, key) ?: default
        }
        return defaults[key]?.toIntOrNull() ?: default
    }

    fun getLong(key: String, default: Long = 0L): Long {
        val pending = ConfigValues.getPendingConfig()
        if (pending != null && pending.isNotEmpty()) {
            return parseConfigLong(pending, key) ?: default
        }
        return defaults[key]?.toLongOrNull() ?: default
    }

    private fun parseConfigValue(config: String, key: String): String? {
        return try {
            val pairs = config.split(";")
            pairs.find { it.startsWith("$key=") }?.substringAfter("=")
        } catch (_: Exception) { null }
    }

    private fun parseConfigInt(config: String, key: String): Int? {
        return parseConfigValue(config, key)?.toIntOrNull()
    }

    private fun parseConfigLong(config: String, key: String): Long? {
        return parseConfigValue(config, key)?.toLongOrNull()
    }
}