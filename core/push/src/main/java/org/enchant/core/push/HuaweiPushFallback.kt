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
    private var pollingJob: Job? = null
    private var scope: CoroutineScope? = null
    private var onMessagesReceived: ((List<JsonObject>) -> Unit)? = null

    fun setOnMessagesReceived(callback: (List<JsonObject>) -> Unit) {
        onMessagesReceived = callback
    }

    fun isHuaweiDevice(): Boolean {
        return Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true)
    }

    fun startPollingFallback(apiClient: ApiClient) {
        stopPollingFallback()
        scope = CoroutineScope(Dispatchers.IO)
        pollingJob = scope?.launch {
            while (isActive) {
                delay(30000)
                try {
                    val result = apiClient.get("/v1/messages/pending")
                    result.fold(
                        onSuccess = { response ->
                            val messages = response["messages"]?.let { arr ->
                                @Suppress("UNCHECKED_CAST")
                                (arr as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject }
                            } ?: emptyList()
                            if (messages.isNotEmpty()) {
                                Log.d("HuaweiPush", "Received ${messages.size} pending messages")
                                onMessagesReceived?.invoke(messages)
                            }
                        },
                        onFailure = { e ->
                            Log.w("HuaweiPush", "Poll failed: ${e.message}")
                        }
                    )
                } catch (e: Exception) {
                    Log.w("HuaweiPush", "Poll exception: ${e.message}")
                }
            }
        }
    }

    fun stopPollingFallback() {
        pollingJob?.cancel()
        pollingJob = null
        scope?.cancel()
        scope = null
    }
}
