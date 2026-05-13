package org.enchant.core.database.dao

import org.enchant.core.database.DatabasePool
import org.enchant.core.database.entity.RecipientEntity
import org.enchant.core.database.util.CursorMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class RecipientDao(private val pool: DatabasePool) {
    suspend fun upsert(recipient: RecipientEntity) = pool.write { db ->
        db.execSQL("""
            INSERT OR REPLACE INTO recipients
                (recipient_id, username, display_name, phone_number, avatar_media_id, avatar_local_path, is_blocked)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, arrayOf(
            recipient.recipientId,
            recipient.username,
            recipient.displayName,
            recipient.phoneNumber,
            recipient.avatarMediaId,
            recipient.avatarLocalPath,
            if (recipient.isBlocked) "1" else "0"
        ))
    }

    suspend fun upsertAll(recipients: List<RecipientEntity>) = pool.write { db ->
        db.beginTransaction()
        try {
            recipients.forEach { upsert(it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun getByUserId(userId: String): RecipientEntity? = pool.read { db ->
        db.query("SELECT * FROM recipients WHERE recipient_id = ?", arrayOf(userId))
            .use { CursorMapper.mapTo<RecipientEntity>(it) }
    }

    suspend fun getByUsername(username: String): RecipientEntity? = pool.read { db ->
        db.query("SELECT * FROM recipients WHERE username = ?", arrayOf(username))
            .use { CursorMapper.mapTo<RecipientEntity>(it) }
    }

    fun getAll(): Flow<List<RecipientEntity>> = callbackFlow {
        val cursor = pool.read { db ->
            db.query("SELECT * FROM recipients ORDER BY display_name ASC", null)
        }
        val items = cursor.use { CursorMapper.mapToList<RecipientEntity>(it) }
        trySend(items)
    }

    suspend fun getBlocked(): List<RecipientEntity> = pool.read { db ->
        db.query("SELECT * FROM recipients WHERE is_blocked = 1", null)
            .use { CursorMapper.mapToList<RecipientEntity>(it) }
    }

    fun search(query: String): Flow<List<RecipientEntity>> = callbackFlow {
        val cursor = pool.read { db ->
            db.query("SELECT * FROM recipients WHERE display_name LIKE ? OR username LIKE ? ORDER BY display_name ASC LIMIT 50", arrayOf("%$query%", "%$query%"))
        }
        val items = cursor.use { CursorMapper.mapToList<RecipientEntity>(it) }
        trySend(items)
    }
}
