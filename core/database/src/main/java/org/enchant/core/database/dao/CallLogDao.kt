package org.enchant.core.database.dao

import org.enchant.core.database.DatabasePool

data class CallLogEntity(
    val callId: String, val remoteUserId: String, val type: String, val direction: String,
    val durationSeconds: Int = 0, val status: String, val endedAt: Long
)

class CallLogDao(private val pool: DatabasePool) {
    suspend fun insert(entry: CallLogEntity) = pool.write { db ->
        db.execSQL("INSERT OR REPLACE INTO call_logs (call_id, remote_user_id, type, direction, duration_seconds, status, ended_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf(entry.callId, entry.remoteUserId, entry.type, entry.direction, entry.durationSeconds.toString(), entry.status, entry.endedAt.toString()))
    }

    suspend fun getAll(limit: Int = 100): List<CallLogEntity> = pool.readWith { db ->
        db.rawQuery("SELECT * FROM call_logs ORDER BY ended_at DESC LIMIT ?", arrayOf(limit.toString())).use { c ->
            val r = mutableListOf<CallLogEntity>()
            while (c.moveToNext()) r.add(CallLogEntity(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4), c.getString(5), c.getLong(6)))
            r
        }
    }

    suspend fun getFiltered(direction: String?, status: String?, limit: Int = 100): List<CallLogEntity> = pool.readWith { db ->
        val where = mutableListOf<String>(); val args = mutableListOf<String>()
        direction?.let { where.add("direction = ?"); args.add(it) }
        status?.let { where.add("status = ?"); args.add(it) }
        val clause = if (where.isNotEmpty()) "WHERE ${where.joinToString(" AND ")}" else ""
        args.add(limit.toString())
        db.rawQuery("SELECT * FROM call_logs $clause ORDER BY ended_at DESC LIMIT ?", args.toTypedArray()).use { c ->
            val r = mutableListOf<CallLogEntity>()
            while (c.moveToNext()) r.add(CallLogEntity(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4), c.getString(5), c.getLong(6)))
            r
        }
    }

    suspend fun delete(callId: String) = pool.write { db ->
        db.execSQL("DELETE FROM call_logs WHERE call_id = ?", arrayOf(callId))
    }

    suspend fun deleteByIds(callIds: List<String>) = pool.write { db ->
        callIds.forEach { db.execSQL("DELETE FROM call_logs WHERE call_id = ?", arrayOf(it)) }
    }
}
