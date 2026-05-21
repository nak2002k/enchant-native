package org.enchant.core.jobmanager

internal object JobLogger {
    private const val TAG = "EnchantJobManager"

    fun d(message: String) {
        try {
            android.util.Log.d(TAG, message)
        } catch (_: RuntimeException) {
        }
    }

    fun i(message: String) {
        try {
            android.util.Log.i(TAG, message)
        } catch (_: RuntimeException) {
        }
    }

    fun w(message: String) {
        try {
            android.util.Log.w(TAG, message)
        } catch (_: RuntimeException) {
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) {
                android.util.Log.e(TAG, message, throwable)
            } else {
                android.util.Log.e(TAG, message)
            }
        } catch (_: RuntimeException) {
        }
    }

    fun jobEvent(jobId: String, event: String) {
        d("[$jobId] $event")
    }
}
