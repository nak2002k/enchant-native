package org.enchant.core.base.concurrent

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.enchant.core.base.logging.Log

/**
 * Detects Application Not Responding (ANR) conditions by monitoring the
 * main thread's responsiveness.
 *
 * Posts a heartbeat to the main thread at regular intervals. If the heartbeat
 * is not acknowledged within the timeout, an ANR is detected and the callback
 * is invoked.
 *
 * Usage:
 * ```
 * val detector = AnrDetector(
 *     thresholdMs = 5000,
 *     onAnrDetected = { stackTrace -> reportAnr(stackTrace) }
 * )
 * detector.start()
 * // ... app runs ...
 * detector.stop()
 * ```
 */
class AnrDetector(
    private val thresholdMs: Long = 5000,
    private val onAnrDetected: (String) -> Unit
) {

    private val TAG = Log.tag(AnrDetector::class)
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var running = false

    private val heartbeat = object : Runnable {
        private var lastAckTime = SystemClock.uptimeMillis()
        private val GC_THRESHOLD = 0.8f

        override fun run() {
            val now = SystemClock.uptimeMillis()
            val elapsed = now - lastAckTime
            if (elapsed > thresholdMs) {
                val runtime = Runtime.getRuntime()
                val freeMemory = runtime.freeMemory()
                val totalMemory = runtime.totalMemory()
                if (totalMemory > 0 && (totalMemory - freeMemory).toFloat() / totalMemory > GC_THRESHOLD) {
                    lastAckTime = now
                    if (running) handler.postDelayed(this, thresholdMs / 2)
                    return
                }
                val stackTrace = getMainThreadStackTrace()
                Log.w(TAG, "ANR detected: main thread blocked for ${elapsed}ms")
                onAnrDetected(stackTrace)
            }
            lastAckTime = now
            if (running) {
                handler.postDelayed(this, thresholdMs / 2)
            }
        }
    }

    /**
     * Starts ANR detection. Must be called on the main thread.
     */
    fun start() {
        if (running) return
        running = true
        handler.post(heartbeat)
    }

    /**
     * Stops ANR detection.
     */
    fun stop() {
        running = false
        handler.removeCallbacks(heartbeat)
    }

    private fun getMainThreadStackTrace(): String {
        return Looper.getMainLooper().thread.stackTrace
            .joinToString("\n") { "  at $it" }
    }
}
