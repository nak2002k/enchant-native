package org.enchant.core.calls

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object ActiveCallManager {
    private const val CHANNEL_ID = "active_call"
    private const val NOTIFICATION_ID = 2001

    fun showCallNotification(context: Context, remoteUserId: String, isVideoCall: Boolean) {
        createChannel(context)
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val endIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(CallNotificationReceiver.ACTION_HANGUP).setClass(context, CallNotificationReceiver::class.java),
            pendingFlags
        )
        val muteIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(CallNotificationReceiver.ACTION_MUTE).setClass(context, CallNotificationReceiver::class.java),
            pendingFlags
        )
        val speakerIntent = PendingIntent.getBroadcast(
            context, 2,
            Intent(CallNotificationReceiver.ACTION_SPEAKER).setClass(context, CallNotificationReceiver::class.java),
            pendingFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(if (isVideoCall) "Video call" else "Voice call")
            .setContentText("Call with $remoteUserId")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End", endIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, "Mute", muteIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, "Speaker", speakerIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun updateCallNotification(context: Context, durationSeconds: Int) {
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val endIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(CallNotificationReceiver.ACTION_HANGUP).setClass(context, CallNotificationReceiver::class.java),
            pendingFlags
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Call in progress")
            .setContentText(formatDuration(durationSeconds))
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End", endIntent)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancelCallNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun startCallScreen(context: Context, callId: String, isVideoCall: Boolean) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.putExtra("navigate_to", if (isVideoCall) "video_call" else "active_call")
        intent?.putExtra("call_id", callId)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (intent != null) context.startActivity(intent)
    }

    fun stopCallScreen(context: Context) {
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Active Call")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun formatDuration(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return "${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
    }
}
