package org.enchant.core.push

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FcmReceiveService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        scope.launch {
            val data = message.data
            if (data.isNotEmpty()) {
                FcmFetchManager.notifyFcmRetryReceived()
            }
            if (isAppInForeground()) {
                FcmFetchManager.scheduleFetch()
            } else {
                val intent = android.content.Intent(this@FcmReceiveService, FcmFetchForegroundService::class.java)
                try {
                    startForegroundService(intent)
                } catch (e: IllegalStateException) {
                    Log.w("FcmReceive", "Foreground service start failed: ${e.message}")
                    try {
                        startService(intent)
                    } catch (e2: Exception) {
                        Log.e("FcmReceive", "Fallback service start failed: ${e2.message}")
                    }
                } catch (e: Exception) {
                    Log.w("FcmReceive", "Service start failed: ${e.message}")
                }
            }
        }
    }

    override fun onNewToken(token: String) {
        scope.launch {
            PushTokenRegistrar.registerWithBackend(token)
        }
    }

    override fun onDeletedMessages() {
        scope.launch {
            FcmFetchManager.scheduleFetch()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val runningProcesses = activityManager.runningAppProcesses ?: return false
        return runningProcesses.any { process ->
            process.processName == packageName &&
            process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }
}
