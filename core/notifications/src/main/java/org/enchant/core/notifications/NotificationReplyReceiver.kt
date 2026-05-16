package org.enchant.core.notifications

import android.app.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationReplyReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = NotificationBuilder.getConversationId(intent) ?: return

        when (intent.action) {
            ACTION_REPLY -> handleReply(context, intent, conversationId)
            ACTION_MARK_READ -> handleMarkRead(context, conversationId)
        }
    }

    private fun handleReply(context: Context, intent: Intent, conversationId: String) {
        val replyText = NotificationBuilder.getReplyText(intent)
        if (replyText.isNullOrBlank()) return

        scope.launch {
            try {
                val apiClient = org.enchant.core.base.DI.apiClient
                val selfId = org.enchant.core.base.SecurePreferences.getString("auth.user_id") ?: return@launch
                apiClient.post("/v1/messages/send", kotlinx.serialization.json.buildJsonObject {
                    put("recipient_user_id", conversationId)
                    put("message_type", "SIGNAL_MESSAGE")
                    put("payload", java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(replyText.encodeToByteArray()))
                })
            } catch (_: Exception) {}
        }
    }

    private fun handleMarkRead(context: Context, conversationId: String) {
        scope.launch {
            try {
                val apiClient = org.enchant.core.base.DI.apiClient
                apiClient.post("/v1/messages/read", kotlinx.serialization.json.buildJsonObject {
                    put("conversation_id", conversationId)
                })
            } catch (_: Exception) {}
        }
    }

    companion object {
        const val ACTION_REPLY = "org.enchant.action.NOTIFICATION_REPLY"
        const val ACTION_MARK_READ = "org.enchant.action.NOTIFICATION_MARK_READ"
    }
}
