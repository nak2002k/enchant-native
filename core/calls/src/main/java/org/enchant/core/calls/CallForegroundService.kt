package org.enchant.core.calls

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CallForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var callId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_CALL -> {
                callId = intent.getStringExtra("call_id")
                val isVideo = intent.getBooleanExtra("is_video", false)
                val remoteUserId = intent.getStringExtra("remote_user_id") ?: ""
                try {
                    startForeground(NOTIFICATION_ID, buildCallNotification(remoteUserId, isVideo))
                } catch (e: SecurityException) {
                    android.util.Log.w("CallFGService", "Notification permission not granted: ${e.message}")
                }

                scope.launch {
                    CallManager.init()
                    CallManager.registerObserver(callObserver)
                }
            }
            ACTION_END_CALL -> {
                scope.launch { CallManager.endCall() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        scope.launch { CallManager.unregisterObserver(callObserver) }
        super.onDestroy()
    }

    private val callObserver = object : CallObserver {
        override fun onCallEnded(reason: CallEndReason, summary: CallSummary?) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildCallNotification(remoteUserId: String, isVideo: Boolean): Notification {
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val endIntent = PendingIntent.getBroadcast(
            this, 0,
            Intent(CallNotificationReceiver.ACTION_HANGUP).setClass(this, CallNotificationReceiver::class.java),
            pendingFlags
        )
        val muteIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(CallNotificationReceiver.ACTION_MUTE).setClass(this, CallNotificationReceiver::class.java),
            pendingFlags
        )
        val speakerIntent = PendingIntent.getBroadcast(
            this, 2,
            Intent(CallNotificationReceiver.ACTION_SPEAKER).setClass(this, CallNotificationReceiver::class.java),
            pendingFlags
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isVideo) "Video call" else "Voice call")
            .setContentText(remoteUserId)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End", endIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, "Mute", muteIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, "Speaker", speakerIntent)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Active Call")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "call_foreground"
        private const val NOTIFICATION_ID = 2002
        const val ACTION_START_CALL = "org.enchant.action.START_CALL"
        const val ACTION_END_CALL = "org.enchant.action.END_CALL"
    }
}
