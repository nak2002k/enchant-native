package org.enchant.core.push

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.enchant.core.network.ApiClient

object HuaweiPushFallback {
    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun isHuaweiDevice(): Boolean {
        return Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true)
    }

    fun startPollingFallback(apiClient: ApiClient) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                delay(30000)
                try {
                    apiClient.get("/v1/messages/pending")
                } catch (_: Exception) {
                }
            }
        }
    }

    fun stopPollingFallback() {
        pollingJob?.cancel()
        pollingJob = null
    }
}
