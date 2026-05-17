package org.enchant.core.config

import java.util.concurrent.ConcurrentHashMap

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

    private val overrides = ConcurrentHashMap<String, String>()

    fun getString(key: String, default: String = defaults[key] ?: ""): String =
        overrides[key] ?: defaults[key] ?: default

    fun getInt(key: String, default: Int = 0): Int =
        (overrides[key] ?: defaults[key])?.toIntOrNull() ?: default

    fun getLong(key: String, default: Long = 0L): Long =
        (overrides[key] ?: defaults[key])?.toLongOrNull() ?: default

    fun setOverride(key: String, value: String) {
        overrides[key] = value
    }

    fun clearOverrides() {
        overrides.clear()
    }
}
