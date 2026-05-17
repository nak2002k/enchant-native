package org.enchant.core.crash

import android.util.Log

object CrashReporter {
    private const val TAG = "EnchantCrash"
    @Volatile
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
    }

    fun log(message: String) {
        val scrubbed = scrub(message)
        Log.d(TAG, scrubbed)
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().log(scrubbed)
        } catch (e: Exception) { Log.w(TAG, "Crashlytics unavailable: ${e.message}") }
    }

    fun logEvent(name: String, data: Map<String, String>? = null) {
        val scrubbedName = scrub(name)
        Log.d(TAG, "event: $scrubbedName")
        try {
            val instance = com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
            data?.forEach { (key, value) ->
                instance.setCustomKey(scrub(key), scrub(value))
            }
            instance.log("event: $scrubbedName")
        } catch (e: Exception) { Log.w(TAG, "Crashlytics unavailable: ${e.message}") }
    }

    fun logError(message: String, throwable: Throwable? = null) {
        val scrubbed = scrub(message)
        Log.e(TAG, scrubbed, throwable)
        try {
            val instance = com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
            instance.log("error: $scrubbed")
            if (throwable != null) instance.recordException(throwable)
        } catch (e: Exception) { Log.w(TAG, "Crashlytics unavailable: ${e.message}") }
    }

    fun logDecryptionFailure() {
        Log.w(TAG, "Decryption failure")
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().log("decryption_failure")
        } catch (e: Exception) { Log.w(TAG, "Crashlytics unavailable: ${e.message}") }
    }

    fun setUserId(userId: String?) {
        if (userId != null) Log.d(TAG, "user set")
        else Log.d(TAG, "user cleared")
        try {
            val instance = com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
            if (userId != null) instance.setUserId(userId)
            else instance.setUserId("")
        } catch (e: Exception) { Log.w(TAG, "Crashlytics unavailable: ${e.message}") }
    }

    fun recordException(t: Throwable) {
        Log.e(TAG, "Exception", t)
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(t)
        } catch (e: Exception) { Log.w(TAG, "Crashlytics unavailable: ${e.message}") }
    }

    fun setCustomKey(key: String, value: String) {
        val scrubbed = scrub(value)
        Log.d(TAG, "meta: $key=$scrubbed")
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setCustomKey(
                scrub(key), scrubbed
            )
        } catch (e: Exception) { Log.w(TAG, "Crashlytics unavailable: ${e.message}") }
    }

    fun scrub(input: String): String {
        return input
            .replace(Regex("[A-Za-z0-9+/]{40,}={0,3}"), "[REDACTED_KEY]")
            .replace(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"), "[REDACTED_UUID]")
            .replace(Regex("\\+[1-9]\\d{6,14}"), "[REDACTED_PHONE]")
            .replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), "[REDACTED_EMAIL]")
    }
}
