package org.enchant.core.base.concurrent

import org.enchant.core.base.logging.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Detects thread deadlocks by periodically checking for threads in BLOCKED
 * state. On Android, uses thread state inspection since the full
 * ThreadMXBean is not available.
 *
 * Usage:
 * ```
 * val detector = DeadlockDetector(
 *     intervalMs = 30000,
 *     onDeadlockDetected = { threads -> reportDeadlock(threads) }
 * )
 * detector.start()
 * // ... app runs ...
 * detector.stop()
 * ```
 */
class DeadlockDetector(
    private val intervalMs: Long = 30000,
    private val onDeadlockDetected: (List<ThreadInfo>) -> Unit
) {

    private val TAG = Log.tag(DeadlockDetector::class)
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "deadlock-detector").apply { isDaemon = true }
    }

    private var running = false

    /**
     * Starts deadlock detection.
     */
    fun start() {
        if (running) return
        running = true
        executor.scheduleAtFixedRate(
            { checkForDeadlocks() },
            intervalMs, intervalMs, TimeUnit.MILLISECONDS
        )
    }

    /**
     * Stops deadlock detection.
     */
    fun stop() {
        running = false
        executor.shutdown()
    }

    private fun checkForDeadlocks() {
        val threadMap = Thread.getAllStackTraces()
        val blockedThreads = threadMap.entries.filter { (thread, _) ->
            thread.state == Thread.State.BLOCKED
        }

        if (blockedThreads.size >= 3) {
            val infos = blockedThreads.map { (thread, stackTrace) ->
                ThreadInfo(
                    name = thread.name,
                    id = thread.id,
                    state = thread.state.name,
                    lockName = null,
                    lockOwnerName = null,
                    stackTrace = stackTrace.joinToString("\n") { "  at $it" }
                )
            }
            Log.w(TAG, "Potential deadlock: ${infos.size} threads blocked")
            onDeadlockDetected(infos)
        }
    }

    data class ThreadInfo(
        val name: String,
        val id: Long,
        val state: String,
        val lockName: String?,
        val lockOwnerName: String?,
        val stackTrace: String
    )
}
