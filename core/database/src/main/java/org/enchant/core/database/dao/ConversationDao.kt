package org.enchant.core.database.dao

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.enchant.core.database.DatabasePool
import org.enchant.core.database.entity.ConversationEntity
import org.enchant.core.database.util.CursorMapper
import org.enchant.core.database.util.DatabaseNotifier

class ConversationDao(private val pool: DatabasePool) {
    suspend fun upsert(conversation: ConversationEntity) = pool.write { db ->
        db.execSQL("""
            INSERT OR REPLACE INTO conversations
                (conversation_id, type, last_message, last_message_envelope_id, last_message_timestamp,
                 unread_count, is_pinned, is_archived, is_muted, mute_until, disappear_timer_seconds)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, arrayOf(
            conversation.conversationId,
            conversation.type,
            conversation.lastMessage,
            conversation.lastMessageEnvelopeId,
            conversation.lastMessageTimestamp?.toString(),
            conversation.unreadCount.toString(),
            if (conversation.isPinned) "1" else "0",
            if (conversation.isArchived) "1" else "0",
            if (conversation.isMuted) "1" else "0",
            conversation.muteUntil?.toString(),
            conversation.disappearTimerSeconds.toString()
        ))
        DatabaseNotifier.notify("conversations")
    }

    fun getAll(): Flow<List<ConversationEntity>> = callbackFlow {
        val cursor = pool.readWith { db ->
            db.rawQuery("SELECT * FROM conversations ORDER BY last_message_timestamp DESC", null)
        }
        val items = cursor.use { CursorMapper.mapToList<ConversationEntity>(it) }
        trySend(items)

        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            DatabaseNotifier.tableChanges.collect { table ->
                if (table == "conversations") {
                    val c = pool.readWith { db ->
                        db.rawQuery("SELECT * FROM conversations ORDER BY last_message_timestamp DESC", null)
                    }
                    val updated = c.use { CursorMapper.mapToList<ConversationEntity>(it) }
                    trySend(updated)
                }
            }
        }
        awaitClose { job.cancel() }
    }

    suspend fun getById(conversationId: String): ConversationEntity? = pool.readWith { db ->
        db.rawQuery("SELECT * FROM conversations WHERE conversation_id = ?", arrayOf(conversationId))
            .use { CursorMapper.mapTo<ConversationEntity>(it) }
    }

    suspend fun setArchived(conversationId: String, archived: Boolean) = pool.write { db ->
        db.execSQL("UPDATE conversations SET is_archived = ? WHERE conversation_id = ?", arrayOf(if (archived) 1 else 0, conversationId))
    }

    suspend fun setPinned(conversationId: String, pinned: Boolean) = pool.write { db ->
        db.execSQL("UPDATE conversations SET is_pinned = ? WHERE conversation_id = ?", arrayOf(if (pinned) 1 else 0, conversationId))
    }

    suspend fun setMuted(conversationId: String, muted: Boolean, until: Long?) = pool.write { db ->
        db.execSQL("UPDATE conversations SET is_muted = ?, mute_until = ? WHERE conversation_id = ?", arrayOf(if (muted) 1 else 0, until?.toString(), conversationId))
    }

    suspend fun incrementUnread(conversationId: String, amount: Int = 1) = pool.write { db ->
        db.execSQL("UPDATE conversations SET unread_count = unread_count + ? WHERE conversation_id = ?", arrayOf(amount.toString(), conversationId))
    }

    suspend fun getUnreadCount(): Int = pool.readWith { db ->
        db.rawQuery("SELECT COALESCE(SUM(unread_count), 0) FROM conversations", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun search(query: String): Flow<List<ConversationEntity>> = callbackFlow {
        fun queryDb(): List<ConversationEntity> = pool.readWith { db ->
            db.rawQuery("""
                SELECT c.* FROM conversations c
                LEFT JOIN messages m ON c.last_message_envelope_id = m.envelope_id
                WHERE m.content LIKE ? OR c.conversation_id IN (
                    SELECT conversation_id FROM messages WHERE content LIKE ?
                )
                ORDER BY c.last_message_timestamp DESC
            """, arrayOf("%$query%", "%$query%"))
                .use { CursorMapper.mapToList<ConversationEntity>(it) }
        }
        try { trySend(queryDb()) } catch (_: Exception) { trySend(emptyList()) }
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            DatabaseNotifier.tableChanges.collect { table ->
                if (table == "conversations" || table == "messages") {
                    try { trySend(queryDb()) } catch (_: Exception) {}
                }
            }
        }
        awaitClose { job.cancel() }
    }
}
