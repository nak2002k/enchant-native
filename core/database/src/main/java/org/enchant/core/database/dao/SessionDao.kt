package org.enchant.core.database.dao

import org.enchant.core.database.DatabasePool

class SessionDao(private val pool: DatabasePool) {
    suspend fun store(userId: String, deviceId: String, session: ByteArray) = pool.write { db ->
        db.execSQL("""
            INSERT OR REPLACE INTO signal_sessions (user_id, device_id, serialized_session, created_at, last_used_at)
            VALUES (?, ?, ?, ?, ?)
        """, arrayOf(userId, deviceId, session, System.currentTimeMillis().toString(), System.currentTimeMillis().toString()))
    }

    suspend fun load(userId: String, deviceId: String): ByteArray? = pool.readWith { db ->
        db.rawQuery("SELECT serialized_session FROM signal_sessions WHERE user_id = ? AND device_id = ?", arrayOf(userId, deviceId))
            .use { if (it.moveToFirst()) it.getBlob(0) else null }
    }

    suspend fun delete(userId: String, deviceId: String) = pool.write { db ->
        db.execSQL("DELETE FROM signal_sessions WHERE user_id = ? AND device_id = ?", arrayOf(userId, deviceId))
    }

    suspend fun hasSession(userId: String, deviceId: String): Boolean = pool.readWith { db ->
        db.rawQuery("SELECT 1 FROM signal_sessions WHERE user_id = ? AND device_id = ? LIMIT 1", arrayOf(userId, deviceId))
            .use { it.moveToFirst() }
    }

    suspend fun deleteAllForUser(userId: String) = pool.write { db ->
        db.execSQL("DELETE FROM signal_sessions WHERE user_id = ?", arrayOf(userId))
    }
}
