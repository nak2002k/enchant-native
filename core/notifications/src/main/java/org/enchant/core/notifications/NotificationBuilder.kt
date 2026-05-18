package org.enchant.core.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat

data class ConversationSummary(
    val conversationId: String,
    val displayName: String,
    val snippet: String,
    val unreadCount: Int,
    val timestamp: Long,
    val avatarBitmap: Bitmap? = null,
    val isMuted: Boolean = false
)

object NotificationBuilder {
    private const val REPLY_KEY = "inline_reply"
    private const val MARK_READ_KEY = "mark_read"

    fun buildMessageNotification(
        context: Context,
        conversationDisplayName: String,
        messagePreview: String,
        senderName: String?,
        conversationId: String,
        messageCount: Int,
        avatarBitmap: Bitmap? = null,
        channelId: String = NotificationChannels.CHANNEL_MESSAGES
    ): Notification {
        val openIntent = createOpenConversationIntent(context, conversationId)
        val replyAction = createReplyAction(context, conversationId)
        val markReadAction = createMarkAsReadAction(context, conversationId)

        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(if (messageCount > 1) "$messageCount messages" else conversationDisplayName)
            .setSummaryText(conversationDisplayName)
            .addLine("${senderName ?: ""}: $messagePreview")

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(conversationDisplayName)
            .setContentText(if (senderName != null) "$senderName: $messagePreview" else messagePreview)
            .setStyle(style)
            .setContentIntent(openIntent)
            .addAction(replyAction)
            .addAction(markReadAction)
            .setAutoCancel(true)
            .setNumber(messageCount)
            .setGroup(conversationId)
            .setGroupSummary(messageCount > 1)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    fun buildSummaryNotification(
        context: Context,
        conversationList: List<ConversationSummary>,
        channelId: String = NotificationChannels.CHANNEL_MESSAGES
    ): Notification {
        val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("${conversationList.size} conversations")
            .setSummaryText("Tap to open")

        conversationList.take(10).forEach { conv ->
            val line = if (conv.displayName.length > 20) {
                "${conv.displayName.take(20)}… ${conv.snippet.take(40)}"
            } else {
                "${conv.displayName} ${conv.snippet.take(50)}"
            }
            inboxStyle.addLine(line)
        }

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${conversationList.size} conversations")
            .setContentText(openIntent?.let { "Tap to open" } ?: "New messages")
            .setStyle(inboxStyle)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroupSummary(true)
            .setGroup(SUMMARY_GROUP)
            .build()
    }

    fun createReplyAction(context: Context, conversationId: String): NotificationCompat.Action {
        val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra("conversation_id", conversationId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, conversationId.hashCode() * 3 + 1, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val remoteInput = RemoteInput.Builder(REPLY_KEY)
            .setLabel("Reply")
            .build()

        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, "Reply", pendingIntent
        ).addRemoteInput(remoteInput).build()
    }

    fun createMarkAsReadAction(context: Context, conversationId: String): NotificationCompat.Action {
        val readIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
            action = ACTION_MARK_READ
            putExtra("conversation_id", conversationId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, conversationId.hashCode() * 3 + 2, readIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, "Mark read", pendingIntent
        ).build()
    }

    fun buildCallNotification(
        context: Context,
        callerName: String,
        answerIntent: PendingIntent?,
        declineIntent: PendingIntent?
    ): Notification {
        return NotificationCompat.Builder(context, NotificationChannels.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming call")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(answerIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    fun getReplyText(intent: Intent): String? {
        return RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REPLY_KEY)?.toString()
    }

    fun getConversationId(intent: Intent): String? {
        return intent.getStringExtra("conversation_id")
    }

    private fun createOpenConversationIntent(context: Context, conversationId: String): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra("conversation_id", conversationId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: Intent()
        return PendingIntent.getActivity(
            context, conversationId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val ACTION_REPLY = "org.enchant.action.NOTIFICATION_REPLY"
    private const val ACTION_MARK_READ = "org.enchant.action.NOTIFICATION_MARK_READ"
    private const val SUMMARY_GROUP = "enchant_summary"
}
