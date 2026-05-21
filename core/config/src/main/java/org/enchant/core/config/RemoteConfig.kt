package org.enchant.core.config

import org.enchant.core.store.EnchantStore

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
        val pending = getPendingConfig()
        if (!pending.isNullOrEmpty()) {
            return parseConfigValue(pending, key) ?: default
        }
        return default
    }

    fun getInt(key: String, default: Int = 0): Int {
        val pending = getPendingConfig()
        if (!pending.isNullOrEmpty()) {
            return parseConfigInt(pending, key) ?: default
        }
        return defaults[key]?.toIntOrNull() ?: default
    }

    fun getLong(key: String, default: Long = 0L): Long {
        val pending = getPendingConfig()
        if (!pending.isNullOrEmpty()) {
            return parseConfigLong(pending, key) ?: default
        }
        return defaults[key]?.toLongOrNull() ?: default
    }

    fun getCurrentConfig(): String? = try { EnchantStore.RemoteConfig.values } catch (_: Exception) { null }
    fun setCurrentConfig(value: String) { try { EnchantStore.RemoteConfig.setValues(value) } catch (_: Exception) {} }
    fun getPendingConfig(): String? = getCurrentConfig()
    fun setPendingConfig(value: String) { setCurrentConfig(value) }
    fun getLastFetchTime(): Long = try { EnchantStore.RemoteConfig.lastFetchTs } catch (_: Exception) { 0L }
    fun setLastFetchTime(time: Long) { try { EnchantStore.RemoteConfig.setLastFetchTs(time) } catch (_: Exception) {} }
    fun getETag(): String? = try { EnchantStore.RemoteConfig.eTag } catch (_: Exception) { null }
    fun setETag(etag: String) { try { EnchantStore.RemoteConfig.setETag(etag) } catch (_: Exception) {} }

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