package org.enchant.core.notifications

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.core.network.ApiClient

data class NotificationPreferences(
    val masterNotificationsOn: Boolean = true,
    val messageNotificationsOn: Boolean = true,
    val callNotificationsOn: Boolean = true,
    val statusNotificationsOn: Boolean = true,
    val channelNotificationsOn: Boolean = true,
    val mentionNotificationsOn: Boolean = true,
    val showPreview: Boolean = true,
    val dndEnabled: Boolean = false,
    val dndStartTime: String = "",
    val dndEndTime: String = "",
    val dndTimezone: String = ""
)

data class ConversationNotificationPrefs(
    val conversationId: String,
    val conversationType: String,
    val muted: Boolean = false,
    val muteExpiresTs: String? = null,
    val mentionsOnly: Boolean = false,
    val customSound: String = "default"
)

class NotificationPreferencesManager(
    private val apiClient: ApiClient
) {
    suspend fun getGlobalPreferences(): NotificationPreferences = withContext(Dispatchers.Default) {
        try {
            val response = apiClient.get("/v1/notifications/preferences")
            response.fold(
                onSuccess = { json ->
                    NotificationPreferences(
                        masterNotificationsOn = json["master_notifications_on"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                        messageNotificationsOn = json["message_notifications_on"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                        callNotificationsOn = json["call_notifications_on"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                        statusNotificationsOn = json["status_notifications_on"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                        channelNotificationsOn = json["channel_notifications_on"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                        mentionNotificationsOn = json["mention_notifications_on"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                        showPreview = json["show_preview"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                        dndEnabled = json["dnd_enabled"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                        dndStartTime = json["dnd_start_time"]?.jsonPrimitive?.content ?: "",
                        dndEndTime = json["dnd_end_time"]?.jsonPrimitive?.content ?: "",
                        dndTimezone = json["dnd_timezone"]?.jsonPrimitive?.content ?: ""
                    )
                },
                onFailure = { NotificationPreferences() }
            )
        } catch (_: Exception) {
            NotificationPreferences()
        }
    }

    suspend fun updateGlobalPreferences(prefs: NotificationPreferences): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val body = buildJsonObject {
                    put("master_notifications_on", prefs.masterNotificationsOn)
                    put("message_notifications_on", prefs.messageNotificationsOn)
                    put("call_notifications_on", prefs.callNotificationsOn)
                    put("status_notifications_on", prefs.statusNotificationsOn)
                    put("channel_notifications_on", prefs.channelNotificationsOn)
                    put("mention_notifications_on", prefs.mentionNotificationsOn)
                    put("show_preview", prefs.showPreview)
                    put("dnd_enabled", prefs.dndEnabled)
                    put("dnd_start_time", prefs.dndStartTime)
                    put("dnd_end_time", prefs.dndEndTime)
                    put("dnd_timezone", prefs.dndTimezone)
                }
                apiClient.put("/v1/notifications/preferences", body).map { }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getConversationPrefs(page: Int = 0, limit: Int = 200): List<ConversationNotificationPrefs> =
        withContext(Dispatchers.Default) {
            try {
                val response = apiClient.get("/v1/notifications/preferences/conversations",
                    mapOf("page" to page.toString(), "limit" to limit.toString()))
                response.fold(
                    onSuccess = { json ->
                        json["conversations"]?.jsonArray?.map { item ->
                            val obj = item.jsonObject
                            ConversationNotificationPrefs(
                                conversationId = obj["conversation_id"]?.jsonPrimitive?.content ?: "",
                                conversationType = obj["conversation_type"]?.jsonPrimitive?.content ?: "DIRECT",
                                muted = obj["muted"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                                muteExpiresTs = obj["mute_expires_ts"]?.jsonPrimitive?.content,
                                mentionsOnly = obj["mentions_only"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                                customSound = obj["custom_sound"]?.jsonPrimitive?.content ?: "default"
                            )
                        } ?: emptyList()
                    },
                    onFailure = { emptyList() }
                )
            } catch (_: Exception) {
                emptyList()
            }
        }

    suspend fun updateConversationPrefs(
        conversationId: String,
        muted: Boolean? = null,
        muteExpiresTs: String? = null,
        mentionsOnly: Boolean? = null,
        customSound: String? = null
    ): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            val body = buildJsonObject {
                muted?.let { put("muted", it) }
                muteExpiresTs?.let { put("mute_expires_ts", it) }
                mentionsOnly?.let { put("mentions_only", it) }
                customSound?.let { put("custom_sound", it) }
            }
            apiClient.put("/v1/notifications/preferences/conversations/$conversationId", body).map { }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}