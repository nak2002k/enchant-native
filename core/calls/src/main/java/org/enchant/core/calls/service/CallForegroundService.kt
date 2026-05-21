package org.enchant.core.calls.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import org.enchant.core.calls.notification.CallNotificationManager

class CallForegroundService : Service() {

    companion object {
        private const val TAG = "CallForegroundService"
        private const val ACTION_START = "org.enchant.core.calls.service.START"
        private const val ACTION_UPDATE = "org.enchant.core.calls.service.UPDATE"
        private const val ACTION_STOP = "org.enchant.core.calls.service.STOP"

        private const val EXTRA_REMOTE_USER_ID = "remote_user_id"
        private const val EXTRA_IS_VIDEO = "is_video"
        private const val EXTRA_CALL_DURATION = "call_duration"
        private const val EXTRA_NOTIFICATION_TYPE = "notification_type"

        const val NOTIFICATION_ID = 2001

        fun start(context: Context, remoteUserId: String, isVideo: Boolean) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_REMOTE_USER_ID, remoteUserId)
                putExtra(EXTRA_IS_VIDEO, isVideo)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun update(context: Context, remoteUserId: String, isVideo: Boolean, durationSeconds: Int) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_REMOTE_USER_ID, remoteUserId)
                putExtra(EXTRA_IS_VIDEO, isVideo)
                putExtra(EXTRA_CALL_DURATION, durationSeconds)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val notificationManager by lazy {
        CallNotificationManager(this)
    }

    private var remoteUserId: String = ""
    private var isVideoCall: Boolean = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                remoteUserId = intent.getStringExtra(EXTRA_REMOTE_USER_ID) ?: ""
                isVideoCall = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
                startForeground()
            }
            ACTION_UPDATE -> {
                val newDuration = intent.getIntExtra(EXTRA_CALL_DURATION, 0)
                updateNotification(newDuration)
            }
            ACTION_STOP -> {
                stopForeground(ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    private fun startForeground() {
        val notification = notificationManager.buildForegroundNotification(remoteUserId, isVideoCall)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = calculateServiceType()
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(durationSeconds: Int) {
        val text = "$remoteUserId • ${formatDuration(durationSeconds)}"
        val notification = notificationManager.buildForegroundNotification(text, isVideoCall)
        val notificationManagerCompat = androidx.core.app.NotificationManagerCompat.from(this)
        notificationManagerCompat.notify(NOTIFICATION_ID, notification)
    }

    private fun calculateServiceType(): Int {
        var serviceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (isVideoCall) {
                serviceType = serviceType or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
        }
        return serviceType
    }

    private fun formatDuration(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return "${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
    }
}