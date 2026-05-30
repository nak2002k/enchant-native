package org.enchant.core.push

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.enchant.core.network.ApiClient

object HuaweiPushFallback {
    private const val TAG = "HuaweiPush"
    private const val BASE_POLL_INTERVAL_MS = 30_000L
    private const val MAX_POLL_INTERVAL_MS = 300_000L
    private var pollingJob: Job? = null
    private var scope: CoroutineScope? = null
    private var onMessagesReceived: ((List<JsonObject>) -> Unit)? = null
    private var consecutiveFailures = 0

    fun setOnMessagesReceived(callback: (List<JsonObject>) -> Unit) {
        onMessagesReceived = callback
    }

    fun isHuaweiDevice(): Boolean {
        return Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true)
    }

    fun startPollingFallback(apiClient: ApiClient) {
        stopPollingFallback()
        consecutiveFailures = 0
        scope = CoroutineScope(Dispatchers.IO)
        pollingJob = scope?.launch {
            while (isActive) {
                val backoff = BASE_POLL_INTERVAL_MS * (1L shl minOf(consecutiveFailures, 3))
                val interval = minOf(backoff, MAX_POLL_INTERVAL_MS)
                delay(interval)
                try {
                    val result = apiClient.get("/v1/messages/pending")
                    result.fold(
                        onSuccess = { response ->
                            consecutiveFailures = 0
                            val messages = response["messages"]?.let { arr ->
                                @Suppress("UNCHECKED_CAST")
                                (arr as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject }
                            } ?: emptyList()
                            if (messages.isNotEmpty()) {
                                Log.d(TAG, "Received ${messages.size} pending messages")
                                onMessagesReceived?.invoke(messages)
                            }
                        },
                        onFailure = { e ->
                            consecutiveFailures++
                            Log.w(TAG, "Poll failed: ${e.message}")
                        }
                    )
                } catch (e: Exception) {
                    consecutiveFailures++
                    Log.w(TAG, "Poll exception: ${e.message}")
                }
            }
        }
    }

    fun stopPollingFallback() {
        pollingJob?.cancel()
        pollingJob = null
        scope?.cancel()
        scope = null
        consecutiveFailures = 0
    }
}
