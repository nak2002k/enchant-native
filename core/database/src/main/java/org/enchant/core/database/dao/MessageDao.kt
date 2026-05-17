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
            INSERT OR IGNORE INTO messages
                (conversation_id, sender_id, sender_device_id, envelope_id, message_type,
                 content, media_key, media_iv, media_mime_type, media_size,
                 media_thumbnail_path, reply_to_envelope_id, forwarded_from_user_id,
                 status, timestamp, server_ts, is_edited, edit_envelope_id,
                 is_starred, is_deleted, disappear_at, gif_url)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
        message.mediaThumbnailPath?.let { stmt.bindString(11, it) } ?: stmt.bindNull(11)
        message.replyToEnvelopeId?.let { stmt.bindString(12, it) } ?: stmt.bindNull(12)
        message.forwardedFromUserId?.let { stmt.bindString(13, it) } ?: stmt.bindNull(13)
        stmt.bindString(14, message.status)
        stmt.bindLong(15, message.timestamp)
        message.serverTs?.let { stmt.bindLong(16, it) } ?: stmt.bindNull(16)
        stmt.bindLong(17, if (message.isEdited) 1 else 0)
        message.editEnvelopeId?.let { stmt.bindString(18, it) } ?: stmt.bindNull(18)
        stmt.bindLong(19, if (message.isStarred) 1 else 0)
        stmt.bindLong(20, if (message.isDeleted) 1 else 0)
        message.disappearAt?.let { stmt.bindLong(21, it) } ?: stmt.bindNull(21)
        message.gifUrl?.let { stmt.bindString(22, it) } ?: stmt.bindNull(22)
        val result = stmt.executeInsert()
        DatabaseNotifier.notify("messages")
        result
    }

    suspend fun insertBatch(messages: List<MessageEntity>) = pool.write { db ->
        db.beginTransaction()
        try {
            messages.forEach { msg ->
                db.execSQL("""
                    INSERT OR IGNORE INTO messages
                        (conversation_id, sender_id, sender_device_id, envelope_id, message_type,
                         content, media_key, media_iv, media_mime_type, media_size,
                         media_thumbnail_path, reply_to_envelope_id, forwarded_from_user_id,
                         status, timestamp, server_ts, is_edited, edit_envelope_id,
                         is_starred, is_deleted, disappear_at, gif_url)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, arrayOf(
                    msg.conversationId,
                    msg.senderId,
                    msg.senderDeviceId,
                    msg.envelopeId,
                    msg.messageType,
                    msg.content,
                    msg.mediaKey,
                    msg.mediaIv,
                    msg.mediaMimeType,
                    msg.mediaSize?.toString(),
                    msg.mediaThumbnailPath,
                    msg.replyToEnvelopeId,
                    msg.forwardedFromUserId,
                    msg.status,
                    msg.timestamp.toString(),
                    msg.serverTs?.toString(),
                    if (msg.isEdited) "1" else "0",
                    msg.editEnvelopeId,
                    if (msg.isStarred) "1" else "0",
                    if (msg.isDeleted) "1" else "0",
                    msg.disappearAt?.toString(),
                    msg.gifUrl
                ))
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
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            DatabaseNotifier.tableChanges.collect { table ->
                if (table == "messages") trySend(query())
            }
        }
        awaitClose { job.cancel() }
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

    suspend fun getUnreadCount(conversationId: String): Int = pool.readWith { db ->
        db.rawQuery("SELECT COUNT(*) FROM messages WHERE conversation_id = ? AND status = 'delivered'", arrayOf(conversationId))
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun searchMessages(query: String): Flow<List<MessageEntity>> = callbackFlow {
        val cursor = pool.readWith { db ->
            db.rawQuery("SELECT * FROM messages WHERE content LIKE ? AND is_deleted = 0 ORDER BY timestamp DESC LIMIT 100", arrayOf("%$query%"))
        }
        val messages = cursor.use { CursorMapper.mapToList<MessageEntity>(it) }
        trySend(messages)
    }

    suspend fun deleteExpired(now: Long) = pool.write { db ->
        db.execSQL("DELETE FROM messages WHERE disappear_at IS NOT NULL AND disappear_at < ? AND is_deleted = 0", arrayOf(now.toString()))
        DatabaseNotifier.notify("messages")
    }

    suspend fun deleteConversation(conversationId: String) = pool.write { db ->
        db.execSQL("DELETE FROM messages WHERE conversation_id = ?", arrayOf(conversationId))
        DatabaseNotifier.notify("messages")
    }
}
