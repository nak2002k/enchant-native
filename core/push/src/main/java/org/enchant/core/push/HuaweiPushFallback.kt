package org.enchant.core.push

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.enchant.core.network.ApiClient

object HuaweiPushFallback {
    private var pollingJob: Job? = null
    private var scope: CoroutineScope? = null

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
                    result.getOrNull()?.let { response ->
                        android.util.Log.d("HuaweiPush", "Pending messages: ${response.toString().take(100)}")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("HuaweiPush", "Poll failed: ${e.message}")
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
