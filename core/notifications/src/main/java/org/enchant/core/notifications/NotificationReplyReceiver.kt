package org.enchant.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.enchant.core.crypto.SessionManager

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
                val apiClient = org.enchant.core.network.ApiClient.getInstance()
                val selfId = org.enchant.core.base.SecurePreferences.getString("auth.user_id") ?: return@launch
                val encrypted = SessionManager.encryptMessage(conversationId, replyText.encodeToByteArray())
                val payload = encrypted?.payload ?: return@launch
                apiClient.post("/v1/messages/send", kotlinx.serialization.json.JsonObject(
                    mapOf(
                        "recipient_user_id" to kotlinx.serialization.json.JsonPrimitive(conversationId),
                        "message_type" to kotlinx.serialization.json.JsonPrimitive("SIGNAL_MESSAGE"),
                        "payload" to kotlinx.serialization.json.JsonPrimitive(
                            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
                        )
                    )
                ))
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
        }
    }

    private fun handleMarkRead(context: Context, conversationId: String) {
        scope.launch {
            try {
                val apiClient = org.enchant.core.network.ApiClient.getInstance()
                apiClient.post("/v1/messages/read", kotlinx.serialization.json.JsonObject(
                    mapOf("conversation_id" to kotlinx.serialization.json.JsonPrimitive(conversationId))
                ))
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
        }
    }

    companion object {
        const val ACTION_REPLY = "org.enchant.action.NOTIFICATION_REPLY"
        const val ACTION_MARK_READ = "org.enchant.action.NOTIFICATION_MARK_READ"
    }
}
