package org.enchant.core.performance

object PerformanceTracker {
    private val metrics = mutableMapOf<String, MutableList<Long>>()

    fun startTrace(name: String): Long {
        val start = System.currentTimeMillis()
        metrics.getOrPut(name) { mutableListOf() }.add(start)
        return start
    }

    fun endTrace(name: String, startTime: Long) {
        val elapsed = System.currentTimeMillis() - startTime
        android.util.Log.d("Perf", "$name: ${elapsed}ms")
    }

    fun getAverage(name: String): Double {
        val times = metrics[name] ?: return 0.0
        return times.average()
    }

    fun reset() {
        metrics.clear()
    }
}
