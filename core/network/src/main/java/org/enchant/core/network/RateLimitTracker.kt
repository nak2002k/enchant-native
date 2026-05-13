package org.enchant.core.network

import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

data class RateLimit(val maxCalls: Int, val windowSeconds: Int, val scope: String)

object RateLimitTracker {
    private val callLogs = ConcurrentHashMap<String, MutableList<Long>>()
    private val serverLimits = ConcurrentHashMap<String, RateLimit>()
    private val resetTimes = ConcurrentHashMap<String, Long>()

    fun recordCall(endpoint: String) {
        val now = System.currentTimeMillis()
        callLogs.getOrPut(endpoint) { mutableListOf() }.add(now)
    }

    fun canCall(endpoint: String): Boolean {
        val remaining = getRemaining(endpoint)
        return remaining > 0
    }

    fun getRemaining(endpoint: String): Int {
        val limit = serverLimits[endpoint] ?: return Int.MAX_VALUE
        val windowMs = limit.windowSeconds * 1000L
        val now = System.currentTimeMillis()
        val logs = callLogs[endpoint] ?: return limit.maxCalls
        logs.removeAll { now - it > windowMs }
        return limit.maxCalls - logs.size
    }

    fun getResetTime(endpoint: String): Long {
        return resetTimes[endpoint] ?: 0L
    }

    fun updateFromHeaders(endpoint: String, headers: Map<String, String>) {
        val limit = headers["X-RateLimit-Limit"]?.toIntOrNull()
        val remaining = headers["X-RateLimit-Remaining"]?.toIntOrNull()
        val reset = headers["X-RateLimit-Reset"]?.toLongOrNull()
        val retryAfter = headers["Retry-After"]?.toLongOrNull()

        if (limit != null) {
            serverLimits[endpoint] = RateLimit(limit, 60, "device")
        }
        if (reset != null) {
            resetTimes[endpoint] = reset * 1000
        }
        if (retryAfter != null) {
            resetTimes[endpoint] = System.currentTimeMillis() + (retryAfter * 1000)
        }
    }

    suspend fun waitIfNeeded(endpoint: String) {
        val resetTime = resetTimes[endpoint] ?: return
        val now = System.currentTimeMillis()
        val waitMs = resetTime - now
        if (waitMs > 0 && waitMs < 60_000) {
            delay(waitMs)
        }
    }
}
