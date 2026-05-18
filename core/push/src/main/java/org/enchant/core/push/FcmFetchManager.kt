package org.enchant.core.push

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object FcmFetchManager {
    private const val TAG = "FcmFetchManager"
    private var scope: CoroutineScope? = null
    private var fetchJob: Job? = null
    private val _isFetchScheduled = MutableStateFlow(false)
    private val _backoffCounter = AtomicInteger(0)
    private var onFetchTriggered: (suspend () -> Unit)? = null

    val isFetchScheduled: StateFlow<Boolean> = _isFetchScheduled.asStateFlow()

    fun init(onFetch: suspend () -> Unit) {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        onFetchTriggered = onFetch
    }

    suspend fun onFcmReceived() {
        scheduleFetch()
    }

    fun scheduleFetch() {
        fetchJob?.cancel()
        val currentScope = scope ?: return
        fetchJob = currentScope.launch {
            _isFetchScheduled.value = true
            val backoff = minOf(1000L * (1 shl _backoffCounter.get()), 30000L)
            delay(backoff)
            try {
                onFetchTriggered?.invoke()
                _backoffCounter.set(0)
            } catch (e: Exception) {
                Log.w(TAG, "FCM fetch failed: ${e.message}")
                _backoffCounter.incrementAndGet()
            } finally {
                _isFetchScheduled.value = false
            }
        }
    }

    fun cancelFetch() {
        fetchJob?.cancel()
        fetchJob = null
        _isFetchScheduled.value = false
    }

    fun notifyFcmRetryReceived() {
        _backoffCounter.set(0)
    }

    fun shutdown() {
        fetchJob?.cancel()
        scope?.cancel()
        scope = null
    }
}
