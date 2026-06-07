package org.enchant.core.database.dao

import org.enchant.core.database.DatabasePool
import org.enchant.core.database.entity.ScheduledMessageEntity
import org.enchant.core.database.util.CursorMapper
import org.enchant.core.database.util.DatabaseNotifier

class ScheduledMessageDao(private val pool: DatabasePool) {
    suspend fun insert(entity: ScheduledMessageEntity): Long = pool.write { db ->
        val stmt = db.compileStatement("""
            INSERT INTO scheduled_messages (conversation_id, content, scheduled_at, is_sent, created_at)
            VALUES (?, ?, ?, ?, ?)
        """)
        stmt.bindString(1, entity.conversationId)
        stmt.bindString(2, entity.content)
        stmt.bindLong(3, entity.scheduledAt)
        stmt.bindLong(4, if (entity.isSent) 1 else 0)
        stmt.bindLong(5, entity.createdAt)
        val result = stmt.executeInsert()
        DatabaseNotifier.notify("scheduled_messages")
        result
    }

    suspend fun markSent(id: Long) = pool.write { db ->
        db.execSQL("UPDATE scheduled_messages SET is_sent = 1 WHERE id = ?", arrayOf(id.toString()))
        DatabaseNotifier.notify("scheduled_messages")
    }

    suspend fun getPending(): List<ScheduledMessageEntity> = pool.readWith { db ->
        db.rawQuery(
            "SELECT * FROM scheduled_messages WHERE is_sent = 0 AND scheduled_at <= ? ORDER BY scheduled_at ASC",
            arrayOf(System.currentTimeMillis().toString())
        ).use { CursorMapper.mapToList<ScheduledMessageEntity>(it) }
    }

    suspend fun getAll(): List<ScheduledMessageEntity> = pool.readWith { db ->
        db.rawQuery("SELECT * FROM scheduled_messages ORDER BY scheduled_at DESC LIMIT 100", null)
            .use { CursorMapper.mapToList<ScheduledMessageEntity>(it) }
    }

    suspend fun delete(id: Long) = pool.write { db ->
        db.execSQL("DELETE FROM scheduled_messages WHERE id = ?", arrayOf(id.toString()))
        DatabaseNotifier.notify("scheduled_messages")
    }

    suspend fun deleteSent() = pool.write { db ->
        db.execSQL("DELETE FROM scheduled_messages WHERE is_sent = 1")
        DatabaseNotifier.notify("scheduled_messages")
    }
}
