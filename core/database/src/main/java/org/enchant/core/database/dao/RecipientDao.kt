package org.enchant.core.database.dao

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.enchant.core.database.DatabasePool
import org.enchant.core.database.entity.RecipientEntity
import org.enchant.core.database.util.CursorMapper
import org.enchant.core.database.util.DatabaseNotifier

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
            recipients.forEach { r ->
                db.execSQL("""
                    INSERT OR REPLACE INTO recipients
                        (recipient_id, username, display_name, phone_number, avatar_media_id, avatar_local_path, is_blocked)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """, arrayOf(
                    r.recipientId, r.username, r.displayName,
                    r.phoneNumber, r.avatarMediaId, r.avatarLocalPath,
                    if (r.isBlocked) "1" else "0"
                ))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun getByUserId(userId: String): RecipientEntity? = pool.readWith { db ->
        db.rawQuery("SELECT * FROM recipients WHERE recipient_id = ?", arrayOf(userId))
            .use { CursorMapper.mapTo<RecipientEntity>(it) }
    }

    suspend fun getByUsername(username: String): RecipientEntity? = pool.readWith { db ->
        db.rawQuery("SELECT * FROM recipients WHERE username = ?", arrayOf(username))
            .use { CursorMapper.mapTo<RecipientEntity>(it) }
    }

    fun getAll(): Flow<List<RecipientEntity>> = callbackFlow {
        fun queryDb(): List<RecipientEntity> = pool.readWith { db ->
            db.rawQuery("SELECT * FROM recipients ORDER BY display_name ASC", null)
                .use { CursorMapper.mapToList<RecipientEntity>(it) }
        }
        trySend(queryDb())
        val job = launch {
            DatabaseNotifier.tableChanges.collect { table ->
                if (table == "recipients") {
                    trySend(queryDb())
                }
            }
        }
        awaitClose { job.cancel() }
    }

    suspend fun getBlocked(): List<RecipientEntity> = pool.readWith { db ->
        db.rawQuery("SELECT * FROM recipients WHERE is_blocked = 1", null)
            .use { CursorMapper.mapToList<RecipientEntity>(it) }
    }

    fun search(query: String): Flow<List<RecipientEntity>> = callbackFlow {
        fun queryDb(): List<RecipientEntity> = pool.readWith { db ->
            db.rawQuery("SELECT * FROM recipients WHERE display_name LIKE ? OR username LIKE ? ORDER BY display_name ASC LIMIT 50", arrayOf("%$query%", "%$query%"))
                .use { CursorMapper.mapToList<RecipientEntity>(it) }
        }
        trySend(queryDb())
        val job = launch {
            DatabaseNotifier.tableChanges.collect { table ->
                if (table == "recipients") {
                    trySend(queryDb())
                }
            }
        }
        awaitClose { job.cancel() }
    }
}
