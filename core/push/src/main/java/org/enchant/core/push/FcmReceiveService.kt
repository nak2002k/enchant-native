package org.enchant.core.push

import android.app.PendingIntent
import android.content.Intent
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
            val isForeground = true
            if (isForeground) {
                FcmFetchManager.scheduleFetch()
            } else {
                val intent = Intent(this@FcmReceiveService, FcmFetchForegroundService::class.java)
                startForegroundService(intent)
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
}
