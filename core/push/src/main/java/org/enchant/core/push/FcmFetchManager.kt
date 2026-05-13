package org.enchant.core.push

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

object FcmFetchManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fetchJob: Job? = null
    private val _isFetchScheduled = MutableStateFlow(false)
    private val _backoffCounter = java.util.concurrent.atomic.AtomicInteger(0)

    val isFetchScheduled: StateFlow<Boolean> = _isFetchScheduled.asStateFlow()

    suspend fun onFcmReceived() {
        scheduleFetch()
    }

    fun scheduleFetch() {
        fetchJob?.cancel()
        fetchJob = scope.launch {
            _isFetchScheduled.value = true
            delay(1000)
            _isFetchScheduled.value = false
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
}
