package org.enchant.core.jobmanager

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

object DisappearingMessagesWorker {
    private const val TAG = "DisappearingMsg"
    private val lastRunMs = AtomicLong(0L)

    fun reset() { lastRunMs.set(0) }
    private val intervalMs = 60_000L
    private var onTick: (() -> Unit)? = null

    fun setOnTick(handler: () -> Unit) {
        onTick = handler
    }

    fun tick() {
        val now = System.currentTimeMillis()
        val prev = lastRunMs.get()
        if (now - prev < intervalMs) return
        if (!lastRunMs.compareAndSet(prev, now)) return
        try {
            onTick?.invoke()
        } catch (e: Exception) {
            try { Log.w(TAG, "Cleanup failed: ${e.message}") } catch (_: Exception) {}
        }
    }
}
