package org.enchant.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_MESSAGES_SILENT = "messages_silent"
    const val CHANNEL_CALLS = "calls"
    const val CHANNEL_VOICE = "voice"
    const val CHANNEL_OTHER = "other"

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Message notifications"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES_SILENT, "Messages (Silent)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Silent message notifications"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CALLS, "Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming call notifications"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_VOICE, "Voice Messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Voice message playback"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_OTHER, "Other", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Background service and other notifications"
            }
        )
    }
}
