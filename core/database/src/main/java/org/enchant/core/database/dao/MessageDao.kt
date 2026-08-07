package org.enchant.core.database.dao

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.enchant.core.database.DatabasePool
import org.enchant.core.database.entity.MessageEntity
import org.enchant.core.database.util.CursorMapper
import org.enchant.core.database.util.DatabaseNotifier

class MessageDao(private val pool: DatabasePool) {
    suspend fun insert(message: MessageEntity): Long = pool.write { db ->
        val stmt = db.compileStatement("""
            INSERT OR REPLACE INTO messages
                (conversation_id, sender_id, sender_device_id, envelope_id, message_type,
                 content, media_key, media_iv, media_mime_type, media_size,
                 media_id, media_thumbnail_path, reply_to_envelope_id, forwarded_from_user_id,
                 status, timestamp, server_ts, is_edited, edit_envelope_id,
                 is_starred, is_deleted, disappear_at, gif_url, is_view_once, edited_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """)
        stmt.bindString(1, message.conversationId)
        stmt.bindString(2, message.senderId)
        message.senderDeviceId?.let { stmt.bindString(3, it) } ?: stmt.bindNull(3)
        message.envelopeId?.let { stmt.bindString(4, it) } ?: stmt.bindNull(4)
        stmt.bindString(5, message.messageType)
        stmt.bindString(6, message.content)
        message.mediaKey?.let { stmt.bindString(7, it) } ?: stmt.bindNull(7)
        message.mediaIv?.let { stmt.bindString(8, it) } ?: stmt.bindNull(8)
        message.mediaMimeType?.let { stmt.bindString(9, it) } ?: stmt.bindNull(9)
        message.mediaSize?.let { stmt.bindLong(10, it) } ?: stmt.bindNull(10)
        message.mediaId?.let { stmt.bindString(11, it) } ?: stmt.bindNull(11)
        message.mediaThumbnailPath?.let { stmt.bindString(12, it) } ?: stmt.bindNull(12)
        message.replyToEnvelopeId?.let { stmt.bindString(13, it) } ?: stmt.bindNull(13)
        message.forwardedFromUserId?.let { stmt.bindString(14, it) } ?: stmt.bindNull(14)
        stmt.bindString(15, message.status)
        stmt.bindLong(16, message.timestamp)
        message.serverTs?.let { stmt.bindLong(17, it) } ?: stmt.bindNull(17)
        stmt.bindLong(18, if (message.isEdited) 1 else 0)
        message.editEnvelopeId?.let { stmt.bindString(19, it) } ?: stmt.bindNull(19)
        stmt.bindLong(20, if (message.isStarred) 1 else 0)
        stmt.bindLong(21, if (message.isDeleted) 1 else 0)
        message.disappearAt?.let { stmt.bindLong(22, it) } ?: stmt.bindNull(22)
        message.gifUrl?.let { stmt.bindString(23, it) } ?: stmt.bindNull(23)
        stmt.bindLong(24, if (message.isViewOnce) 1 else 0)
        message.editedAt?.let { stmt.bindLong(25, it) } ?: stmt.bindNull(25)
        val result = stmt.executeInsert()
        DatabaseNotifier.notify("messages")
        result
    }

    suspend fun insertBatch(messages: List<MessageEntity>) = pool.write { db ->
        db.beginTransaction()
        try {
            val stmt = db.compileStatement("""
                INSERT OR REPLACE INTO messages
                    (conversation_id, sender_id, sender_device_id, envelope_id, message_type,
                     content, media_key, media_iv, media_mime_type, media_size,
                     media_id, media_thumbnail_path, reply_to_envelope_id, forwarded_from_user_id,
                     status, timestamp, server_ts, is_edited, edit_envelope_id,
                     is_starred, is_deleted, disappear_at, gif_url, is_view_once, edited_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)
            messages.forEach { msg ->
                stmt.bindString(1, msg.conversationId)
                stmt.bindString(2, msg.senderId)
                msg.senderDeviceId?.let { stmt.bindString(3, it) } ?: stmt.bindNull(3)
                msg.envelopeId?.let { stmt.bindString(4, it) } ?: stmt.bindNull(4)
                stmt.bindString(5, msg.messageType)
                stmt.bindString(6, msg.content)
                msg.mediaKey?.let { stmt.bindString(7, it) } ?: stmt.bindNull(7)
                msg.mediaIv?.let { stmt.bindString(8, it) } ?: stmt.bindNull(8)
                msg.mediaMimeType?.let { stmt.bindString(9, it) } ?: stmt.bindNull(9)
                msg.mediaSize?.let { stmt.bindLong(10, it) } ?: stmt.bindNull(10)
                msg.mediaId?.let { stmt.bindString(11, it) } ?: stmt.bindNull(11)
                msg.mediaThumbnailPath?.let { stmt.bindString(12, it) } ?: stmt.bindNull(12)
                msg.replyToEnvelopeId?.let { stmt.bindString(13, it) } ?: stmt.bindNull(13)
                msg.forwardedFromUserId?.let { stmt.bindString(14, it) } ?: stmt.bindNull(14)
                stmt.bindString(15, msg.status)
                stmt.bindLong(16, msg.timestamp)
                msg.serverTs?.let { stmt.bindLong(17, it) } ?: stmt.bindNull(17)
                stmt.bindLong(18, if (msg.isEdited) 1 else 0)
                msg.editEnvelopeId?.let { stmt.bindString(19, it) } ?: stmt.bindNull(19)
                stmt.bindLong(20, if (msg.isStarred) 1 else 0)
                stmt.bindLong(21, if (msg.isDeleted) 1 else 0)
                msg.disappearAt?.let { stmt.bindLong(22, it) } ?: stmt.bindNull(22)
                msg.gifUrl?.let { stmt.bindString(23, it) } ?: stmt.bindNull(23)
                stmt.bindLong(24, if (msg.isViewOnce) 1 else 0)
                msg.editedAt?.let { stmt.bindLong(25, it) } ?: stmt.bindNull(25)
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun getById(localId: Long): MessageEntity? = pool.readWith { db ->
        db.rawQuery("SELECT * FROM messages WHERE local_id = ?", arrayOf(localId.toString()))
            .use { CursorMapper.mapTo<MessageEntity>(it) }
    }

    suspend fun getByEnvelopeId(envelopeId: String): MessageEntity? = pool.readWith { db ->
        db.rawQuery("SELECT * FROM messages WHERE envelope_id = ?", arrayOf(envelopeId))
            .use { CursorMapper.mapTo<MessageEntity>(it) }
    }

    fun getConversationMessages(conversationId: String, limit: Int = 50, beforeId: Long? = null): Flow<List<MessageEntity>> = callbackFlow {
        val sql = """
            SELECT * FROM messages
            WHERE conversation_id = ? AND is_deleted = 0
            ${if (beforeId != null) "AND local_id < ?" else ""}
            ORDER BY timestamp DESC
            LIMIT ?
        """
        fun query(): List<MessageEntity> = pool.readWith { db ->
            val args = mutableListOf(conversationId)
            beforeId?.let { args.add(it.toString()) }
            args.add(limit.toString())
            db.rawQuery(sql, args.toTypedArray()).use { CursorMapper.mapToList<MessageEntity>(it) }
        }
        trySend(query())
        val job = launch {
            DatabaseNotifier.tableChanges.collect { table ->
                if (table == "messages") trySend(query())
            }
        }
        awaitClose { job.cancel() }
    }

    suspend fun getConversationMessagesSnapshot(conversationId: String, limit: Int = 50): List<MessageEntity> = pool.readWith { db ->
        db.rawQuery("""
            SELECT * FROM messages
            WHERE conversation_id = ? AND is_deleted = 0
            ORDER BY timestamp DESC
            LIMIT ?
        """, arrayOf(conversationId, limit.toString())).use { CursorMapper.mapToList<MessageEntity>(it) }
    }

    suspend fun updateStatus(envelopeId: String, status: String) = pool.write { db ->
        db.execSQL("UPDATE messages SET status = ? WHERE envelope_id = ?", arrayOf(status, envelopeId))
        DatabaseNotifier.notify("messages")
    }

    suspend fun markDeleted(envelopeId: String) = pool.write { db ->
        db.execSQL("UPDATE messages SET is_deleted = 1 WHERE envelope_id = ?", arrayOf(envelopeId))
        DatabaseNotifier.notify("messages")
    }

    suspend fun starMessage(envelopeId: String, starred: Boolean) = pool.write { db ->
        db.execSQL("UPDATE messages SET is_starred = ? WHERE envelope_id = ?", arrayOf(if (starred) 1 else 0, envelopeId))
        DatabaseNotifier.notify("messages")
    }

    suspend fun pinMessage(envelopeId: String, pinned: Boolean) = pool.write { db ->
        db.execSQL("UPDATE messages SET is_pinned = ? WHERE envelope_id = ?", arrayOf(if (pinned) 1 else 0, envelopeId))
        DatabaseNotifier.notify("messages")
    }

    suspend fun getUnreadCount(conversationId: String): Int = pool.readWith { db ->
        db.rawQuery("SELECT COUNT(*) FROM messages WHERE conversation_id = ? AND status = 'delivered'", arrayOf(conversationId))
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun searchMessages(query: String): Flow<List<MessageEntity>> = callbackFlow {
        fun query(): List<MessageEntity> = pool.readWith { db ->
            val sanitized = query.trim()
                .replace(Regex("[^a-zA-Z0-9 ]"), "")
                .take(200)
            val words = sanitized.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val ftsQuery = words.joinToString(" ") { "\"$it\"" }
            val cursor = db.rawQuery("""
                SELECT m.* FROM messages m
                INNER JOIN messages_fts fts ON m.local_id = fts.rowid
                WHERE messages_fts MATCH ? AND m.is_deleted = 0
                ORDER BY m.timestamp DESC LIMIT 100
            """, arrayOf(ftsQuery))
            cursor.use { CursorMapper.mapToList<MessageEntity>(it) }
        }
        try { trySend(query()) } catch (_: Exception) { trySend(emptyList()) }
        val job = launch {
            DatabaseNotifier.tableChanges.collect { table ->
                if (table == "messages") try { trySend(query()) } catch (_: Exception) {}
            }
        }
        awaitClose { job.cancel() }
    }

    suspend fun deleteExpired(now: Long) = pool.write { db ->
        db.execSQL("DELETE FROM messages WHERE disappear_at IS NOT NULL AND disappear_at < ? AND is_deleted = 0", arrayOf(now.toString()))
        DatabaseNotifier.notify("messages")
    }

    suspend fun getEnvelopeIdByServerTs(serverTs: Long): String? = pool.readWith { db ->
        db.rawQuery("SELECT envelope_id FROM messages WHERE server_ts = ? OR timestamp = ? LIMIT 1", arrayOf(serverTs.toString(), serverTs.toString()))
            .use { if (it.moveToFirst()) it.getString(0) else null }
    }

    suspend fun updateDisappearAt(envelopeId: String, disappearAt: Long) = pool.write { db ->
        db.execSQL("UPDATE messages SET disappear_at = ? WHERE envelope_id = ?", arrayOf(disappearAt.toString(), envelopeId))
        DatabaseNotifier.notify("messages")
    }

    suspend fun deleteConversation(conversationId: String) = pool.write { db ->
        db.execSQL("DELETE FROM messages WHERE conversation_id = ?", arrayOf(conversationId))
        DatabaseNotifier.notify("messages")
    }

    suspend fun updateEditEnvelopeId(envelopeId: String, editEnvelopeId: String) = pool.write { db ->
        db.execSQL("UPDATE messages SET edit_envelope_id = ? WHERE envelope_id = ?", arrayOf(editEnvelopeId, envelopeId))
        DatabaseNotifier.notify("messages")
    }

    suspend fun updateEditedAt(envelopeId: String, editedAt: Long) = pool.write { db ->
        db.execSQL("UPDATE messages SET edited_at = ? WHERE envelope_id = ?", arrayOf(editedAt.toString(), envelopeId))
        DatabaseNotifier.notify("messages")
    }

    suspend fun getStarredMessages(): Flow<List<MessageEntity>> = callbackFlow {
        fun query(): List<MessageEntity> = pool.readWith { db ->
            db.rawQuery("SELECT * FROM messages WHERE is_starred = 1 AND is_deleted = 0 ORDER BY timestamp DESC LIMIT 200", null)
                .use { CursorMapper.mapToList<MessageEntity>(it) }
        }
        trySend(query())
        val job = launch {
            DatabaseNotifier.tableChanges.collect { table ->
                if (table == "messages") trySend(query())
            }
        }
        awaitClose { job.cancel() }
    }

    suspend fun getPinnedMessages(conversationId: String): List<MessageEntity> = pool.readWith { db ->
        db.rawQuery("SELECT * FROM messages WHERE conversation_id = ? AND is_pinned = 1 AND is_deleted = 0 ORDER BY timestamp DESC", arrayOf(conversationId))
            .use { CursorMapper.mapToList<MessageEntity>(it) }
    }

    suspend fun getDeletedMessages(): List<MessageEntity> = pool.readWith { db ->
        db.rawQuery("SELECT * FROM messages WHERE is_deleted = 1 AND envelope_id IS NOT NULL ORDER BY timestamp DESC LIMIT 50", null)
            .use { CursorMapper.mapToList<MessageEntity>(it) }
    }
}
