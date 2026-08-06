package org.enchant.chat.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.enchant.core.base.AppConfig
import org.enchant.core.database.DatabasePool
import org.enchant.core.database.dao.ConversationDao
import org.enchant.core.database.dao.MediaCacheDao
import org.enchant.core.database.dao.MessageDao
import org.enchant.core.database.dao.RecipientDao
import org.enchant.core.database.entity.ConversationEntity
import org.enchant.core.database.entity.MessageEntity
import org.enchant.core.database.util.DatabaseNotifier
import org.enchant.core.model.Conversation
import org.enchant.core.model.ConversationType
import org.enchant.core.model.Message
import org.enchant.core.model.MessageStatus
import org.enchant.core.network.ApiClient
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive

data class MessagePage(
    val messages: List<Message>,
    val nextCursor: Long?,
    val hasMore: Boolean
)

enum class ConversationFilter { ALL, UNREAD, GROUPS, PERSONAL, ARCHIVED }

class ConversationRepository(
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val recipientDao: RecipientDao,
    private val pool: DatabasePool,
    private val mediaCacheDao: MediaCacheDao = MediaCacheDao(pool),
    private val apiClient: ApiClient? = null
) {
    fun getConversations(filter: ConversationFilter = ConversationFilter.ALL): Flow<List<Conversation>> = callbackFlow {
        val allConversations = conversationDao.getAll()
        val allConversationsList = mutableListOf<ConversationEntity>()
        val collectJob = launch {
            allConversations.collect { list ->
                allConversationsList.clear()
                allConversationsList.addAll(list)
                val filtered = when (filter) {
                    ConversationFilter.UNREAD -> list.filter { it.unreadCount > 0 }
                    ConversationFilter.GROUPS -> list.filter { it.type == "group" }
                    ConversationFilter.PERSONAL -> list.filter { it.type == "direct" }
                    ConversationFilter.ARCHIVED -> list.filter { it.isArchived }
                    ConversationFilter.ALL -> list
                }
                val drafts = try {
                    pool.readWith { db ->
                        val draftMap = mutableMapOf<String, String>()
                        db.rawQuery("SELECT conversation_id, content FROM drafts", null).use { cursor ->
                            while (cursor.moveToNext()) {
                                draftMap[cursor.getString(0)] = cursor.getString(1)
                            }
                        }
                        draftMap
                    }
                } catch (_: Exception) { emptyMap() }
                trySend(filtered.map { Conversation.fromEntity(it).copy(draftContent = drafts[it.conversationId]) })
            }
        }
        awaitClose { collectJob.cancel() }
    }

    fun getMessages(conversationId: String, limit: Int = 50, beforeId: Long? = null): Flow<List<Message>> = callbackFlow {
        val flow = messageDao.getConversationMessages(conversationId, limit, beforeId)
        val collectJob = launch {
            flow.collect { entities ->
                val messages = entities.map { Message.fromEntity(it) }
                trySend(attachReactions(messages))
            }
        }
        awaitClose { collectJob.cancel() }
    }

    suspend fun getMessagePage(conversationId: String, cursor: Long? = null, limit: Int = 50): MessagePage = pool.readWith { db ->
        val cursorClause = if (cursor != null) "AND local_id < ?" else ""
        val args = mutableListOf(conversationId)
        cursor?.let { args.add(it.toString()) }
        args.add((limit + 1).toString())
        val raw = db.rawQuery("""
            SELECT * FROM messages
            WHERE conversation_id = ? AND is_deleted = 0 $cursorClause
            ORDER BY timestamp DESC
            LIMIT ?
        """, args.toTypedArray())
        val entities = raw.use { org.enchant.core.database.util.CursorMapper.mapToList<MessageEntity>(it) }
        val hasMore = entities.size > limit
        val pageItems = if (hasMore) entities.dropLast(1) else entities
        val nextCursor = pageItems.lastOrNull()?.localId
        MessagePage(
            messages = pageItems.map { Message.fromEntity(it) },
            nextCursor = nextCursor,
            hasMore = hasMore
        )
    }

    suspend fun insertMessage(message: MessageEntity): Long {
        val entity = resolveDisappearAt(message)
        return messageDao.insert(entity)
    }

    /**
     * Resolve a human-readable name for a direct-conversation peer:
     * cached recipient (contact) first, then the user profile, cached for
     * next time. Returns null when the peer is unknown and the profile
     * cannot be fetched.
     */
    suspend fun resolveDisplayName(userId: String): String? {
        recipientDao.getByUserId(userId)?.let { cached ->
            val name = cached.displayName ?: cached.username
            if (name != null) return name
        }
        // Group conversations resolve their title from the groups service.
        if (userId.length <= 36 && !userId.contains("@")) {
            // Local cache first: the groups feature stores the name in
            // groups_table (group_id = the full UUID).
            val cached = pool.readWith { db ->
                db.rawQuery("SELECT name FROM groups_table WHERE group_id = ?", arrayOf(userId))
                    .use { if (it.moveToFirst()) it.getString(0) else null }
            }
            if (!cached.isNullOrBlank()) return cached
            val groupResult = runCatching {
                apiClient?.get("/v1/groups/$userId")
            }
            val name = groupResult.getOrNull()?.getOrNull()?.get("name")?.jsonPrimitive?.content
            if (name != null) return name
        }
        val client = apiClient ?: org.enchant.core.network.ApiClient.getInstance()
        return try {
            val json = client.get("/v1/profile/$userId").getOrNull() ?: return null
            val displayName = json["display_name"]?.jsonPrimitive?.content
            val username = json["username"]?.jsonPrimitive?.content
            val name = displayName ?: username ?: return null
            recipientDao.upsert(
                org.enchant.core.database.entity.RecipientEntity(
                    recipientId = userId, displayName = displayName, username = username
                )
            )
            name
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun resolveDisappearAt(message: MessageEntity): MessageEntity {
        if (message.disappearAt != null) return message
        val conv = conversationDao.getById(message.conversationId) ?: return message
        val timer = conv.disappearTimerSeconds
        if (timer <= 0) return message
        val baseTs = message.serverTs ?: message.timestamp
        return message.copy(disappearAt = baseTs + timer * 1000L)
    }

    suspend fun insertMessageAndUpdateConversation(message: MessageEntity, conversationType: String = "direct") {
        val entity = resolveDisappearAt(message)
        pool.write { db ->
            db.beginTransaction()
            try {
                val cursor = db.rawQuery(
                    "SELECT unread_count FROM conversations WHERE conversation_id = ?",
                    arrayOf(entity.conversationId)
                )
                val currentUnread = if (cursor.moveToFirst()) cursor.getInt(0) else 0
                val selfId = org.enchant.core.base.SecurePreferences.getString("auth.user_id") ?: ""
                val newUnread = if (entity.senderId == selfId) currentUnread else currentUnread + 1
                cursor.close()

                db.execSQL("""
                    INSERT OR REPLACE INTO messages
                        (conversation_id, sender_id, envelope_id, message_type,
                         content, status, timestamp, server_ts, disappear_at,
                         media_key, media_mime_type, media_size, media_id, is_view_once)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, arrayOf(
                    entity.conversationId, entity.senderId, entity.envelopeId,
                    entity.messageType, entity.content, entity.status,
                    entity.timestamp.toString(), entity.serverTs,
                    entity.disappearAt, entity.mediaKey, entity.mediaMimeType,
                    entity.mediaSize?.toString(), entity.mediaId,
                    if (entity.isViewOnce) 1 else 0
                ))
                db.execSQL("""
                    INSERT OR REPLACE INTO conversations
                        (conversation_id, type, last_message, last_message_envelope_id,
                         last_message_timestamp, unread_count)
                    VALUES (?, ?, ?, ?, ?, ?)
                """, arrayOf(
                    message.conversationId, conversationType,
                    message.content.take(100), message.envelopeId,
                    message.timestamp.toString(), newUnread
                ))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        // This path writes both tables directly, bypassing the DAO methods
        // that normally publish table-change notifications. Without these
        // events, an open conversation and the conversation list keep stale
        // StateFlow snapshots even though the message is in SQLite.
        DatabaseNotifier.notify("messages")
        DatabaseNotifier.notify("conversations")
    }

    suspend fun getMessage(envelopeId: String): Message? {
        return messageDao.getByEnvelopeId(envelopeId)?.let { Message.fromEntity(it) }
    }

    suspend fun getMessageByLocalId(localId: Long): Message? {
        return messageDao.getById(localId)?.let { Message.fromEntity(it) }
    }

    suspend fun updateMessageStatus(envelopeId: String, status: MessageStatus) {
        messageDao.updateStatus(envelopeId, status.name.lowercase())
    }

    suspend fun updateMessageContent(envelopeId: String, content: String) = pool.write { db ->
        db.execSQL("UPDATE messages SET content = ?, is_edited = 1 WHERE envelope_id = ?", arrayOf(content, envelopeId))
    }

    suspend fun updateEditEnvelopeId(envelopeId: String, editEnvelopeId: String) = pool.write { db ->
        db.execSQL("UPDATE messages SET edit_envelope_id = ? WHERE envelope_id = ?", arrayOf(editEnvelopeId, envelopeId))
    }

    suspend fun updateEditedAt(envelopeId: String, editedAt: Long) = pool.write { db ->
        db.execSQL("UPDATE messages SET edited_at = ? WHERE envelope_id = ?", arrayOf(editedAt.toString(), envelopeId))
    }

    suspend fun markMessageDeleted(envelopeId: String) {
        messageDao.markDeleted(envelopeId)
    }

    suspend fun deleteLocalMedia(envelopeId: String) {
        val cached = mediaCacheDao.get(envelopeId)
        if (cached != null) {
            cached.localPath?.let { path ->
                val file = java.io.File(path)
                if (file.exists()) file.delete()
            }
            mediaCacheDao.delete(envelopeId)
        }
        markMessageDeleted(envelopeId)
    }

    suspend fun starMessage(envelopeId: String, starred: Boolean) {
        messageDao.starMessage(envelopeId, starred)
    }

    suspend fun pinMessage(envelopeId: String, pinned: Boolean) {
        messageDao.pinMessage(envelopeId, pinned)
    }

    suspend fun addReaction(conversationId: String, messageId: Long, emoji: String, userId: String) = pool.write { db ->
        db.execSQL("""
            INSERT OR REPLACE INTO reactions (message_local_id, emoji, user_id, conversation_id)
            VALUES (?, ?, ?, ?)
        """, arrayOf(messageId.toString(), emoji, userId, conversationId))
        DatabaseNotifier.notify("messages")
    }

    suspend fun removeReaction(messageId: Long, userId: String) = pool.write { db ->
        db.execSQL("DELETE FROM reactions WHERE message_local_id = ? AND user_id = ?", arrayOf(messageId.toString(), userId))
    }

    fun getUnreadCount(): Flow<Int> = callbackFlow {
        val count = conversationDao.getUnreadCount()
        trySend(count)
        awaitClose { }
    }

    suspend fun getConversationUnreadCount(conversationId: String): Int {
        return messageDao.getUnreadCount(conversationId)
    }

    suspend fun markConversationRead(conversationId: String) = pool.write { db ->
        db.execSQL("UPDATE conversations SET unread_count = 0 WHERE conversation_id = ?", arrayOf(conversationId))
        db.execSQL("UPDATE messages SET status = 'read' WHERE conversation_id = ? AND status IN ('delivered', 'sent')", arrayOf(conversationId))
        DatabaseNotifier.notify("conversations")
        DatabaseNotifier.notify("messages")
    }

    suspend fun setArchived(conversationId: String, archived: Boolean) {
        conversationDao.setArchived(conversationId, archived)
    }

    suspend fun setPinned(conversationId: String, pinned: Boolean) {
        conversationDao.setPinned(conversationId, pinned)
    }

    suspend fun setMuted(conversationId: String, muted: Boolean, until: Long? = null) {
        conversationDao.setMuted(conversationId, muted, until)
    }

    suspend fun getConversation(conversationId: String): Conversation? {
        return conversationDao.getById(conversationId)?.let { Conversation.fromEntity(it) }
    }

    suspend fun getOrCreateConversation(userId: String): Conversation {
        val existing = conversationDao.getById(userId)
        if (existing != null) return Conversation.fromEntity(existing)
        val entity = ConversationEntity(
            conversationId = userId,
            type = "direct",
            lastMessageTimestamp = System.currentTimeMillis()
        )
        conversationDao.upsert(entity)
        return Conversation.fromEntity(entity)
    }

    fun searchConversations(query: String): Flow<List<Conversation>> = callbackFlow {
        val flow = conversationDao.search(query)
        val collectJob = launch {
            flow.collect { entities ->
                trySend(entities.map { Conversation.fromEntity(it) })
            }
        }
        awaitClose { collectJob.cancel() }
    }

    fun searchMessages(query: String): Flow<List<Message>> = callbackFlow {
        val flow = messageDao.searchMessages(query)
        val collectJob = launch {
            flow.collect { entities ->
                trySend(entities.map { Message.fromEntity(it) })
            }
        }
        awaitClose { collectJob.cancel() }
    }

    suspend fun deleteExpiredMessages() {
        val now = System.currentTimeMillis()
        val expired = pool.readWith { db ->
            db.rawQuery("SELECT local_id, envelope_id, media_thumbnail_path FROM messages WHERE disappear_at IS NOT NULL AND disappear_at < ? AND is_deleted = 0", arrayOf(now.toString()))
                .use { org.enchant.core.database.util.CursorMapper.mapToList<MessageEntity>(it) }
        }
        if (expired.isEmpty()) {
            messageDao.deleteExpired(now)
            return
        }
        for (msg in expired) {
            msg.envelopeId?.let { eid ->
                val cached = mediaCacheDao.get(eid)
                if (cached != null) {
                    cached.localPath?.let { path ->
                        val file = java.io.File(path)
                        if (file.exists()) file.delete()
                    }
                    mediaCacheDao.delete(eid)
                }
            }
            msg.mediaThumbnailPath?.let { path ->
                val thumb = java.io.File(path)
                if (thumb.exists()) thumb.delete()
            }
        }
        messageDao.deleteExpired(now)
    }

    suspend fun deleteConversation(conversationId: String) {
        messageDao.deleteConversation(conversationId)
    }

    suspend fun setDisappearTimer(conversationId: String, timerSeconds: Int) {
        pool.write { db ->
            db.execSQL("UPDATE conversations SET disappear_timer_seconds = ? WHERE conversation_id = ?", arrayOf(timerSeconds.toString(), conversationId))
        }
        apiClient?.let { client ->
            try {
                client.put("/v1/disappear/$conversationId", buildJsonObject {
                    put("timer_seconds", JsonPrimitive(timerSeconds))
                    put("timer_mode", JsonPrimitive("FROM_SEND"))
                    put("conversation_type", JsonPrimitive("direct"))
                })
            } catch (e: Exception) {
                android.util.Log.w("ConversationRepo", "Failed to sync disappear timer to server: ${e.message}")
            }
        }
    }

    suspend fun getPinnedMessages(conversationId: String): List<Message> = pool.readWith { db ->
        db.query("SELECT * FROM messages WHERE conversation_id = ? AND is_pinned = 1 AND is_deleted = 0 ORDER BY timestamp DESC LIMIT 10", arrayOf(conversationId))
            .use { org.enchant.core.database.util.CursorMapper.mapToList<MessageEntity>(it) }
            .map { Message.fromEntity(it) }
    }

    private suspend fun attachReactions(messages: List<Message>): List<Message> {
        if (messages.isEmpty()) return messages
        val ids = messages.map { it.localId }
        val reactionsMap = loadReactionsForMessages(ids)
        return messages.map { msg ->
            msg.copy(reactions = reactionsMap[msg.localId] ?: emptyList())
        }
    }

    private suspend fun loadReactionsForMessages(messageIds: List<Long>): Map<Long, List<org.enchant.core.model.Reaction>> {
        if (messageIds.isEmpty()) return emptyMap()
        return pool.readWith { db ->
            val placeholders = messageIds.joinToString(",") { "?" }
            val args = messageIds.map { it.toString() }.toTypedArray()
            val cursor = db.rawQuery("SELECT message_local_id, emoji, user_id FROM reactions WHERE message_local_id IN ($placeholders)", args)
            cursor.use {
                val map = mutableMapOf<Long, MutableList<org.enchant.core.model.Reaction>>()
                while (it.moveToNext()) {
                    val msgId = it.getLong(0)
                    val emoji = it.getString(1)
                    val userId = it.getString(2)
                    map.getOrPut(msgId) { mutableListOf() }.add(org.enchant.core.model.Reaction(msgId.toString(), emoji, userId))
                }
                map
            }
        }
    }

    suspend fun getReplyPreview(envelopeId: String): Result<Message> {
        val client = apiClient ?: return Result.failure(Exception("ApiClient not available"))
        return try {
            val response = client.get("/v1/messages/$envelopeId/reply")
            response.map { json ->
                Message(
                    localId = 0,
                    envelopeId = json["envelope_id"]?.jsonPrimitive?.content,
                    conversationId = "",
                    senderId = json["sender_id"]?.jsonPrimitive?.content ?: "",
                    content = "",
                    status = MessageStatus.SENT,
                    timestamp = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun translateMessage(envelopeId: String, targetLanguage: String): Result<String> {
        val client = apiClient ?: return Result.failure(Exception("ApiClient not available"))
        return try {
            val response = client.post("/v1/messages/$envelopeId/translate", buildJsonObject {
                put("target_language", JsonPrimitive(targetLanguage))
            })
            response.map { json ->
                json["translated_text"]?.jsonPrimitive?.content ?: ""
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveDraft(conversationId: String, content: String) = pool.write { db ->
        db.execSQL(
            "INSERT OR REPLACE INTO drafts (conversation_id, content, timestamp) VALUES (?, ?, ?)",
            arrayOf(conversationId, content, System.currentTimeMillis().toString())
        )
    }

    suspend fun getDraft(conversationId: String): String? = pool.readWith { db ->
        db.rawQuery("SELECT content FROM drafts WHERE conversation_id = ?", arrayOf(conversationId))
            .use { if (it.moveToFirst()) it.getString(0) else null }
    }

    suspend fun deleteDraft(conversationId: String) = pool.write { db ->
        db.execSQL("DELETE FROM drafts WHERE conversation_id = ?", arrayOf(conversationId))
    }

    suspend fun scheduleMessage(conversationId: String, content: String, scheduledAt: Long) = pool.write { db ->
        db.execSQL(
            "INSERT INTO scheduled_messages (conversation_id, content, scheduled_at, is_sent, created_at) VALUES (?, ?, ?, 0, ?)",
            arrayOf(conversationId, content, scheduledAt.toString(), System.currentTimeMillis().toString())
        )
    }

    suspend fun getScheduledMessages(conversationId: String): List<org.enchant.core.database.entity.ScheduledMessageEntity> = pool.readWith { db ->
        db.rawQuery(
            "SELECT * FROM scheduled_messages WHERE conversation_id = ? ORDER BY scheduled_at ASC",
            arrayOf(conversationId)
        ).use {
            val list = mutableListOf<org.enchant.core.database.entity.ScheduledMessageEntity>()
            while (it.moveToNext()) {
                list.add(org.enchant.core.database.entity.ScheduledMessageEntity(
                    id = it.getLong(0),
                    conversationId = it.getString(1),
                    content = it.getString(2),
                    scheduledAt = it.getLong(3),
                    isSent = it.getInt(4) == 1,
                    createdAt = it.getLong(5)
                ))
            }
            list
        }
    }

    suspend fun deleteScheduledMessage(id: Long) = pool.write { db ->
        db.execSQL("DELETE FROM scheduled_messages WHERE id = ?", arrayOf(id.toString()))
    }

    fun getStarredMessages(): Flow<List<Message>> = callbackFlow {
        val flow = messageDao.getStarredMessages()
        val collectJob = launch {
            flow.collect { entities ->
                trySend(entities.map { Message.fromEntity(it) })
            }
        }
        awaitClose { collectJob.cancel() }
    }
}
