package org.enchant.core.base.concurrent

import android.os.Handler
import android.os.Looper
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
        private var lastAckTime = System.currentTimeMillis()

        override fun run() {
            val now = System.currentTimeMillis()
            if (now - lastAckTime > thresholdMs) {
                val stackTrace = getMainThreadStackTrace()
                Log.w(TAG, "ANR detected: main thread blocked for ${now - lastAckTime}ms")
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
