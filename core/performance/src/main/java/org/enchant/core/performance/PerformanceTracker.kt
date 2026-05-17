package org.enchant.core.performance

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

object PerformanceTracker {
    private val metrics = ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>>()
    private const val MAX_ENTRIES_PER_METRIC = 1000

    fun startTrace(name: String): Long {
        val start = System.currentTimeMillis()
        metrics.computeIfAbsent(name) { ConcurrentLinkedQueue() }.apply {
            add(start)
            while (size > MAX_ENTRIES_PER_METRIC) poll()
        }
        return start
    }

    fun endTrace(name: String, startTime: Long) {
        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed > 100) {
            android.util.Log.d("Perf", "$name: ${elapsed}ms")
        }
    }

    fun getAverage(name: String): Double {
        val times = metrics[name] ?: return 0.0
        return times.average()
    }

    fun reset() {
        metrics.clear()
    }
}
