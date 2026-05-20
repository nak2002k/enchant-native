package org.enchant.core.base

import org.enchant.core.base.logging.Log
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

/**
 * Measures elapsed time with split points for performance profiling.
 *
 * Usage:
 * ```
 * val sw = Stopwatch("message-decrypt")
 * // ... decrypt step 1
 * sw.split("cipher-init")
 * // ... decrypt step 2
 * sw.split("decrypt")
 * sw.stop(TAG)
 * // Output: [message-decrypt] cipher-init: 5ms, decrypt: 12ms, total: 17ms
 * ```
 */
class Stopwatch @JvmOverloads constructor(
    private val title: String,
    private val decimalPlaces: Int = 0
) {
    private val startTimeNanos: Long = System.nanoTime()
    private val splits: MutableList<Split> = mutableListOf()

    /**
     * Records a split point with the given label.
     */
    fun split(label: String) {
        val now = System.nanoTime()
        val previousTime = if (splits.isEmpty()) startTimeNanos else splits.last().nanoTime
        splits += Split(nanoTime = now, durationNanos = now - previousTime, label = label)
    }

    /**
     * Stops the stopwatch and logs the result with the given tag.
     */
    fun stop(tag: String) {
        Log.d(tag, stopAndGetLogString())
    }

    /**
     * Stops the stopwatch and returns the formatted log string.
     */
    fun stopAndGetLogString(): String {
        val now = System.nanoTime()
        splits += Split(nanoTime = now, durationNanos = now - startTimeNanos, label = "total")
        val splitString = splits.joinToString(", ") { it.displayString(decimalPlaces) }
        return "[$title] $splitString"
    }

    private data class Split(val nanoTime: Long, val durationNanos: Long, val label: String) {
        fun displayString(decimalPlaces: Int): String {
            val timeMs = durationNanos.nanoseconds.toDouble(DurationUnit.MILLISECONDS)
                .let { if (decimalPlaces == 0) it.toLong().toDouble() else it }
                .let { "%.${decimalPlaces}f".format(it) }
            return "$label: ${timeMs}ms"
        }
    }
}

/**
 * Measures the execution time of [block] and logs it with the given tag and label.
 *
 * @return the result of [block]
 */
inline fun <T> logTime(tag: String, label: String, decimalPlaces: Int = 0, block: () -> T): T {
    val result = measureTimedValue(block)
    val timeMs = result.duration.toDouble(DurationUnit.MILLISECONDS)
        .let { if (decimalPlaces == 0) it.toLong().toDouble() else it }
        .let { "%.${decimalPlaces}f".format(it) }
    Log.d(tag, "$label: ${timeMs}ms")
    return result.value
}
