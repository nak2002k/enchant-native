package org.enchant.core.push

import android.app.ActivityManager
import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.enchant.core.base.SecurePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FcmReceiveService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        scope.launch {
            if (isAppInForeground()) {
                FcmFetchManager.scheduleFetch()
            } else {
                val intent = android.content.Intent(this@FcmReceiveService, FcmFetchForegroundService::class.java)
                try {
                    startForegroundService(intent)
                } catch (e: IllegalStateException) {
                    android.util.Log.w("FcmReceive", "Foreground service start failed: ${e.message}")
                    try { startService(intent) } catch (_: Exception) {}
                } catch (e: Exception) {
                    android.util.Log.w("FcmReceive", "Service start failed: ${e.message}")
                }
            }
        }
    }

    override fun onNewToken(token: String) {
        scope.launch {
            SecurePreferences.putString("push.fcm_token", token)
            PushTokenRegistrar.registerWithBackend(token)
        }
    }

    override fun onDeletedMessages() {
        scope.launch {
            FcmFetchManager.scheduleFetch()
        }
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
