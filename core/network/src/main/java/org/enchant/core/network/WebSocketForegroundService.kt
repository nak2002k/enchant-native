package org.enchant.core.network

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.enchant.core.base.AppConfig

class WebSocketForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var isConnected = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> connect()
            ACTION_DISCONNECT -> disconnect()
            else -> {
                if (!isConnected) connect()
            }
        }
        return START_STICKY
    }

    private fun connect() {
        startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))

        scope.launch {
            try {
                WebSocketManager.connectionState.collect { state ->
                    when (state) {
                        ConnectionState.CONNECTED -> {
                            isConnected = true
                            updateNotification("Connected")
                        }
                        ConnectionState.DISCONNECTED, ConnectionState.AUTH_FAILED -> {
                            isConnected = false
                            updateNotification("Reconnecting...")
                        }
                        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> {
                            updateNotification("Connecting...")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("WebSocketService", "State collection failed", e)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        isConnected = false
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannelCompat.Builder(
                CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW
            ).setName("WebSocket Service").build()
            NotificationManagerCompat.from(this).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Enchant")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = NotificationManagerCompat.from(this)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "websocket_service"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_CONNECT = "org.enchant.action.WS_CONNECT"
        const val ACTION_DISCONNECT = "org.enchant.action.WS_DISCONNECT"
    }
}
