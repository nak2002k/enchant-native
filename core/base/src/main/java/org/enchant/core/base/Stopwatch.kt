package org.enchant.core.base

import org.enchant.core.base.logging.Log
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

class Stopwatch @JvmOverloads constructor(
    private val title: String,
    private val decimalPlaces: Int = 0
) {
    private val startTimeNanos: Long = System.nanoTime()
    private val splits: MutableList<Split> = mutableListOf()

    fun split(label: String) {
        val now = System.nanoTime()
        val previousTime = if (splits.isEmpty()) startTimeNanos else splits.last().nanoTime
        splits += Split(nanoTime = now, durationNanos = now - previousTime, label = label)
    }

    fun stop(tag: String) {
        Log.d(tag, stopAndGetLogString())
    }

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

inline fun <T> logTime(tag: String, label: String, decimalPlaces: Int = 0, block: () -> T): T {
    val result = measureTimedValue(block)
    val timeMs = result.duration.toDouble(DurationUnit.MILLISECONDS)
        .let { if (decimalPlaces == 0) it.toLong().toDouble() else it }
        .let { "%.${decimalPlaces}f".format(it) }
    Log.d(tag, "$label: ${timeMs}ms")
    return result.value
}
