package org.enchant.core.crash

import android.util.Log

object CrashReporter {
    private const val TAG = "EnchantCrash"
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
    }

    fun log(message: String) {
        val scrubbed = scrubSensitive(message)
        Log.d(TAG, scrubbed)
    }

    fun recordException(t: Throwable) {
        Log.e(TAG, "Exception", t)
    }

    fun setCustomKey(key: String, value: String) {
        val scrubbed = scrubSensitive(value)
        Log.d(TAG, "meta: $key=$scrubbed")
    }

    private fun scrubSensitive(input: String): String {
        return input
            .replace(Regex("[A-Za-z0-9+/]{40,}={0,3}"), "[REDACTED_KEY]")
            .replace(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"), "[REDACTED_UUID]")
            .replace(Regex("\\+?[1-9]\\d{1,14}"), "[REDACTED_PHONE]")
    }
}
