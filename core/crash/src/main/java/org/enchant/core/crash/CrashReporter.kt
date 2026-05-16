package org.enchant.core.crash

import android.util.Log

object CrashReporter {
    private const val TAG = "CrashReporter"
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
    }

    fun log(message: String) {
        Log.d(TAG, message)
    }

    fun recordException(t: Throwable) {
        Log.e(TAG, "Exception", t)
    }

    fun setCustomKey(key: String, value: String) {
        Log.d(TAG, "$key=$value")
    }
}
