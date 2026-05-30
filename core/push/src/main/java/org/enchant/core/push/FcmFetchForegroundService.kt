package org.enchant.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class FcmFetchForegroundService : android.app.Service() {
    companion object {
        private const val CHANNEL_ID = "fcm_fetch"
        private const val NOTIFICATION_ID = 1001
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var fetchObserver: Job? = null
    private var fetchStarted = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Enchant")
            .setContentText("Checking for new messages")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        fetchObserver = scope.launch {
            FcmFetchManager.isFetchScheduled.collect { scheduled ->
                if (fetchStarted.value && !scheduled) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }

        fetchStarted.value = true
        FcmFetchManager.scheduleFetch()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        fetchObserver?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Message Fetch",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            manager.createNotificationChannel(channel)
        }
    }
}
