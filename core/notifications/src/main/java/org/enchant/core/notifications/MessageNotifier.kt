package org.enchant.core.notifications

import android.content.Context
import android.graphics.Bitmap
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.ConcurrentHashMap

object MessageNotifier {
    @Volatile
    private var initialized = false
    private val conversationNotifications = ConcurrentHashMap<String, PendingNotification>()

    private data class PendingNotification(
        val displayName: String,
        val snippet: String,
        val senderName: String?,
        val count: Int,
        val timestamp: Long,
        val avatarBitmap: Bitmap? = null
    )

    fun init(context: Context) {
        if (initialized) return
        NotificationChannels.createAll(context)
        initialized = true
    }

    fun onMessageReceived(
        context: Context,
        conversationId: String,
        displayName: String,
        senderName: String?,
        snippet: String,
        avatarBitmap: Bitmap? = null,
        isMuted: Boolean = false
    ) {
        val existing = conversationNotifications[conversationId]
        val newCount = (existing?.count ?: 0) + 1
        val pending = PendingNotification(
            displayName = displayName,
            snippet = snippet,
            senderName = if (newCount == 1) senderName else existing?.senderName,
            count = newCount,
            timestamp = System.currentTimeMillis(),
            avatarBitmap = avatarBitmap ?: existing?.avatarBitmap
        )
        conversationNotifications[conversationId] = pending

        val channelId = if (isMuted) NotificationChannels.CHANNEL_MESSAGES_SILENT
            else NotificationChannels.CHANNEL_MESSAGES

        val notification = NotificationBuilder.buildMessageNotification(
            context = context,
            conversationDisplayName = displayName,
            messagePreview = snippet,
            senderName = if (newCount == 1) senderName else null,
            conversationId = conversationId,
            messageCount = newCount,
            avatarBitmap = avatarBitmap,
            channelId = channelId
        )

        NotificationManagerCompat.from(context).notify(
            conversationId.hashCode(), notification
        )

        updateSummaryNotification(context)
    }

    fun removeConversation(context: Context, conversationId: String) {
        conversationNotifications.remove(conversationId)
        NotificationManagerCompat.from(context).cancel(conversationId.hashCode())
        updateSummaryNotification(context)
    }

    fun cancelAll(context: Context) {
        conversationNotifications.keys.forEach { id ->
            NotificationManagerCompat.from(context).cancel(id.hashCode())
        }
        conversationNotifications.clear()
        NotificationManagerCompat.from(context).cancelAll()
    }

    private fun updateSummaryNotification(context: Context) {
        val activeConversations = conversationNotifications.entries
            .map { (id, pending) ->
                ConversationSummary(
                    conversationId = id,
                    displayName = pending.displayName,
                    snippet = pending.snippet,
                    unreadCount = pending.count,
                    timestamp = pending.timestamp,
                    avatarBitmap = pending.avatarBitmap
                )
            }
            .sortedByDescending { it.timestamp }

        if (activeConversations.size > 1) {
            val summary = NotificationBuilder.buildSummaryNotification(
                context = context,
                conversationList = activeConversations
            )
            NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, summary)
        } else {
            NotificationManagerCompat.from(context).cancel(SUMMARY_NOTIFICATION_ID)
        }
    }

    private const val SUMMARY_NOTIFICATION_ID = Int.MAX_VALUE
}
