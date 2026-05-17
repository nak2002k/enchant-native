package org.enchant.core.jobmanager

import android.util.Log

object DisappearingMessagesWorker {
    private const val TAG = "DisappearingMsg"
    @Volatile private var lastRunMs = 0L

    fun reset() { lastRunMs = 0 }
    private val intervalMs = 60_000L
    private var onTick: (() -> Unit)? = null

    fun setOnTick(handler: () -> Unit) {
        onTick = handler
    }

    fun tick() {
        val now = System.currentTimeMillis()
        if (now - lastRunMs < intervalMs) return
        lastRunMs = now
        try {
            onTick?.invoke()
        } catch (e: Exception) {
            try { Log.w(TAG, "Cleanup failed: ${e.message}") } catch (_: Exception) {}
        }
    }
}
