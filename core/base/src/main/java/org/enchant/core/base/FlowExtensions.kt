package org.enchant.core.base

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration

/**
 * Throttles a flow to emit at most once per [timeout], but emits immediately
 * for values where [emitImmediately] returns true.
 *
 * The first value is always emitted. Subsequent values within the timeout
 * window are conflated (only the latest is kept) and emitted after the
 * timeout expires.
 */
fun <T> Flow<T>.throttleLatest(timeout: Duration, emitImmediately: (T) -> Boolean = { false }): Flow<T> = channelFlow {
        var lastEmitTime = 0L
        var pendingValue: T? = null
        var pendingScheduled = false
        collect { value ->
            val now = System.currentTimeMillis()
            if (now - lastEmitTime >= timeout.inWholeMilliseconds || emitImmediately(value)) {
                send(value)
                lastEmitTime = now
                pendingValue = null
            } else {
                pendingValue = value
                if (!pendingScheduled) {
                    pendingScheduled = true
                    launch {
                        delay(timeout)
                        pendingValue?.let { send(it); lastEmitTime = System.currentTimeMillis() }
                        pendingValue = null
                        pendingScheduled = false
                    }
                }
            }
        }
    }

/**
 * Maps non-null values, filtering out nulls.
 */
fun <T : Any, R : Any> Flow<T?>.mapNotNull(transform: suspend (T) -> R): Flow<R> {
    return filterNotNull().map(transform)
}

/**
 * Retries the flow up to [maxRetries] times when an exception occurs.
 */
fun <T> Flow<T>.retry(maxRetries: Int, delayMs: Long): Flow<T> {
    return channelFlow {
        var retries = 0
        while (retries <= maxRetries) {
            try {
                collect { send(it) }
                break
            } catch (e: Exception) {
                retries++
                if (retries > maxRetries) throw e
                delay(delayMs)
            }
        }
    }
}
