package org.enchant.core.notifications

import android.app.NotificationManager
import android.content.Context

object MessageNotifier {
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        NotificationChannels.createAll(context)
        initialized = true
    }

    fun onMessageReceived(context: Context, conversationId: String, senderName: String, preview: String) {
    }

    fun removeConversation(context: Context, conversationId: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(conversationId.hashCode())
    }

    fun cancelAll(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()
    }
}
