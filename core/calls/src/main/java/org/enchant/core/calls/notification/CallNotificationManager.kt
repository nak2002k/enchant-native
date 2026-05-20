package org.enchant.core.calls.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import javax.inject.Inject

class CallNotificationManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val CHANNEL_ID = "call_channel"
        private const val INCOMING_CALL_ID = 2000
        private const val ACTIVE_CALL_ID = 2001
    }

    init {
        createChannel()
    }

    fun showIncomingCall(remoteUserId: String, isVideo: Boolean, callId: String) {
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val answerIntent = PendingIntent.getBroadcast(
            context, 100,
            Intent(CallNotificationReceiver.ACTION_ANSWER).apply {
                setClass(context, CallNotificationReceiver::class.java)
                putExtra("call_id", callId)
                putExtra("is_video", isVideo)
            },
            pendingFlags
        )

        val denyIntent = PendingIntent.getBroadcast(
            context, 101,
            Intent(CallNotificationReceiver.ACTION_DENY).apply {
                setClass(context, CallNotificationReceiver::class.java)
                putExtra("call_id", callId)
            },
            pendingFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(if (isVideo) "Video call" else "Voice call")
            .setContentText("Incoming call from $remoteUserId")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(answerIntent, true)
            .addAction(android.R.drawable.ic_menu_call, "Answer", answerIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", denyIntent)
            .setOngoing(true)
            .build()

        NotificationManagerCompat.from(context).notify(INCOMING_CALL_ID, notification)
    }

    fun showActiveCall(remoteUserId: String, isVideo: Boolean, durationSeconds: Int) {
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val endIntent = PendingIntent.getBroadcast(
            context, 200,
            Intent(CallNotificationReceiver.ACTION_HANGUP).setClass(context, CallNotificationReceiver::class.java),
            pendingFlags
        )
        val muteIntent = PendingIntent.getBroadcast(
            context, 201,
            Intent(CallNotificationReceiver.ACTION_MUTE).setClass(context, CallNotificationReceiver::class.java),
            pendingFlags
        )
        val speakerIntent = PendingIntent.getBroadcast(
            context, 202,
            Intent(CallNotificationReceiver.ACTION_SPEAKER).setClass(context, CallNotificationReceiver::class.java),
            pendingFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(if (isVideo) "Video call" else "Voice call")
            .setContentText("$remoteUserId • ${formatDuration(durationSeconds)}")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End", endIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, "Mute", muteIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, "Speaker", speakerIntent)
            .build()

        NotificationManagerCompat.from(context).notify(ACTIVE_CALL_ID, notification)
    }

    fun cancelAll() {
        NotificationManagerCompat.from(context).cancel(INCOMING_CALL_ID)
        NotificationManagerCompat.from(context).cancel(ACTIVE_CALL_ID)
    }

    fun cancelIncoming() {
        NotificationManagerCompat.from(context).cancel(INCOMING_CALL_ID)
    }

    fun buildForegroundNotification(remoteUserId: String, isVideo: Boolean): Notification {
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val endIntent = PendingIntent.getBroadcast(
            context, 300,
            Intent(CallNotificationReceiver.ACTION_HANGUP).setClass(context, CallNotificationReceiver::class.java),
            pendingFlags
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(if (isVideo) "Video call" else "Voice call")
            .setContentText(remoteUserId)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End", endIntent)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Calls")
            .setDescription("Call notifications")
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun formatDuration(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return "${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
    }
}