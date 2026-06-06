package org.enchant.core.database.dao

import org.enchant.core.database.DatabasePool

class SessionDao(private val pool: DatabasePool) {
    suspend fun store(userId: String, deviceId: String, session: ByteArray) = pool.write { db ->
        val stmt = db.compileStatement("""
            INSERT OR REPLACE INTO enchant_sessions (user_id, device_id, serialized_session, created_at, last_used_at)
            VALUES (?, ?, ?, ?, ?)
        """)
        stmt.bindString(1, userId)
        stmt.bindString(2, deviceId)
        stmt.bindBlob(3, session)
        stmt.bindLong(4, System.currentTimeMillis())
        stmt.bindLong(5, System.currentTimeMillis())
        stmt.executeInsert()
    }

    suspend fun load(userId: String, deviceId: String): ByteArray? = pool.readWith { db ->
        db.rawQuery("SELECT serialized_session FROM enchant_sessions WHERE user_id = ? AND device_id = ?", arrayOf(userId, deviceId))
            .use { if (it.moveToFirst()) it.getBlob(0) else null }
    }

    suspend fun delete(userId: String, deviceId: String) = pool.write { db ->
        db.execSQL("DELETE FROM enchant_sessions WHERE user_id = ? AND device_id = ?", arrayOf(userId, deviceId))
    }

    suspend fun hasSession(userId: String, deviceId: String): Boolean = pool.readWith { db ->
        db.rawQuery("SELECT 1 FROM enchant_sessions WHERE user_id = ? AND device_id = ? LIMIT 1", arrayOf(userId, deviceId))
            .use { it.moveToFirst() }
    }

    suspend fun deleteAllForUser(userId: String) = pool.write { db ->
        db.execSQL("DELETE FROM enchant_sessions WHERE user_id = ?", arrayOf(userId))
    }

    suspend fun loadAll(): List<Triple<String, String, ByteArray>> = pool.readWith { db ->
        db.rawQuery("SELECT user_id, device_id, serialized_session FROM enchant_sessions", null).use { cursor ->
            val sessions = mutableListOf<Triple<String, String, ByteArray>>()
            while (cursor.moveToNext()) {
                val userId = cursor.getString(0)
                val deviceId = cursor.getString(1)
                val session = cursor.getBlob(2)
                sessions.add(Triple(userId, deviceId, session))
            }
            sessions
        }
    }
}
