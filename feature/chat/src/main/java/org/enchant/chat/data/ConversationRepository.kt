package org.enchant.chat.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.enchant.core.database.DatabasePool
import org.enchant.core.database.dao.ConversationDao
import org.enchant.core.database.dao.MessageDao
import org.enchant.core.database.dao.RecipientDao
import org.enchant.core.database.entity.ConversationEntity
import org.enchant.core.database.entity.MessageEntity
import org.enchant.core.database.entity.RecipientEntity
import org.enchant.core.model.Conversation
import org.enchant.core.model.ConversationType
import org.enchant.core.model.Message
import org.enchant.core.model.MessageStatus

data class MessagePage(
    val messages: List<Message>,
    val nextCursor: Long?,
    val hasMore: Boolean
)

enum class ConversationFilter { ALL, UNREAD, GROUPS, PERSONAL }

class ConversationRepository(
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val recipientDao: RecipientDao,
    private val pool: DatabasePool
) {
    fun getConversations(filter: ConversationFilter = ConversationFilter.ALL): Flow<List<Conversation>> = callbackFlow {
        val allConversations = conversationDao.getAll()
        val allConversationsList = mutableListOf<ConversationEntity>()
        val collectJob = kotlinx.coroutines.launch {
            allConversations.collect { list ->
                allConversationsList.clear()
                allConversationsList.addAll(list)
                val filtered = when (filter) {
                    ConversationFilter.UNREAD -> list.filter { it.unreadCount > 0 }
                    ConversationFilter.GROUPS -> list.filter { it.type == "group" }
                    ConversationFilter.PERSONAL -> list.filter { it.type == "direct" }
                    ConversationFilter.ALL -> list
                }
                trySend(filtered.map { Conversation.fromEntity(it) })
            }
        }
        awaitClose { collectJob.cancel() }
    }

    fun getMessages(conversationId: String, limit: Int = 50, beforeId: Long? = null): Flow<List<Message>> = callbackFlow {
        val flow = messageDao.getConversationMessages(conversationId, limit, beforeId)
        val collectJob = launch {
            flow.collect { entities ->
                trySend(entities.map { Message.fromEntity(it) })
            }
        }
        awaitClose { collectJob.cancel() }
    }

    suspend fun getMessagePage(conversationId: String, cursor: Long? = null, limit: Int = 50): MessagePage = pool.read { db ->
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
        return messageDao.insert(message)
    }

    suspend fun insertMessageAndUpdateConversation(message: MessageEntity, conversationType: String = "direct") {
        pool.write { db ->
            db.beginTransaction()
            try {
                messageDao.insert(message)
                conversationDao.upsert(ConversationEntity(
                    conversationId = message.conversationId,
                    type = conversationType,
                    lastMessage = message.content.take(100),
                    lastMessageEnvelopeId = message.envelopeId,
                    lastMessageTimestamp = message.timestamp,
                    unreadCount = 0
                ))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
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

    suspend fun markMessageDeleted(envelopeId: String) {
        messageDao.markDeleted(envelopeId)
    }

    suspend fun starMessage(envelopeId: String, starred: Boolean) {
        messageDao.starMessage(envelopeId, starred)
    }

    suspend fun addReaction(conversationId: String, messageId: Long, emoji: String, userId: String) = pool.write { db ->
        db.execSQL("""
            INSERT OR REPLACE INTO reactions (message_local_id, emoji, user_id, conversation_id)
            VALUES (?, ?, ?, ?)
        """, arrayOf(messageId.toString(), emoji, userId, conversationId))
    }

    suspend fun removeReaction(messageId: Long, userId: String) = pool.write { db ->
        db.execSQL("DELETE FROM reactions WHERE message_local_id = ? AND user_id = ?", arrayOf(messageId.toString(), userId))
    }

    fun getUnreadCount(): Flow<Int> = callbackFlow {
        val count = conversationDao.getUnreadCount()
        trySend(count)
    }

    suspend fun getConversationUnreadCount(conversationId: String): Int {
        return messageDao.getUnreadCount(conversationId)
    }

    suspend fun markConversationRead(conversationId: String) = pool.write { db ->
        db.execSQL("UPDATE conversations SET unread_count = 0 WHERE conversation_id = ?", arrayOf(conversationId))
        db.execSQL("UPDATE messages SET status = 'read' WHERE conversation_id = ? AND status IN ('delivered', 'sent')", arrayOf(conversationId))
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
        val collectJob = kotlinx.coroutines.launch {
            flow.collect { entities ->
                trySend(entities.map { Conversation.fromEntity(it) })
            }
        }
        awaitClose { collectJob.cancel() }
    }

    fun searchMessages(query: String): Flow<List<Message>> = callbackFlow {
        val flow = messageDao.searchMessages(query)
        val collectJob = kotlinx.coroutines.launch {
            flow.collect { entities ->
                trySend(entities.map { Message.fromEntity(it) })
            }
        }
        awaitClose { collectJob.cancel() }
    }

    suspend fun deleteExpiredMessages() {
        messageDao.deleteExpired(System.currentTimeMillis())
    }

    suspend fun deleteConversation(conversationId: String) {
        messageDao.deleteConversation(conversationId)
    }

    suspend fun setDisappearTimer(conversationId: String, timerSeconds: Int) = pool.write { db ->
        db.execSQL("UPDATE conversations SET disappear_timer_seconds = ? WHERE conversation_id = ?", arrayOf(timerSeconds.toString(), conversationId))
    }

    suspend fun getPinnedMessages(conversationId: String): List<Message> = pool.read { db ->
        db.query("SELECT * FROM messages WHERE conversation_id = ? AND is_starred = 1 AND is_deleted = 0 ORDER BY timestamp DESC LIMIT 10", arrayOf(conversationId))
            .use { org.enchant.core.database.util.CursorMapper.mapToList<MessageEntity>(it) }
            .map { Message.fromEntity(it) }
    }
}
