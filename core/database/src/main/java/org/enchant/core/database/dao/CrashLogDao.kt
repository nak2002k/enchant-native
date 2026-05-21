package org.enchant.core.database.dao

import org.enchant.core.database.DatabasePool

data class CrashEntity(
    val id: Long,
    val timestamp: Long,
    val exceptionName: String,
    val message: String?,
    val stackTrace: String,
    val isFatal: Boolean,
    val remoteReported: Boolean
)

class CrashLogDao(private val pool: DatabasePool) {
    fun insert(timestamp: Long, exceptionName: String, message: String?, stackTrace: String, isFatal: Boolean): Long {
        return pool.write { db ->
            db.execSQL("""
                INSERT INTO crashes (timestamp, exception_name, message, stack_trace, is_fatal, remote_reported)
                VALUES (?, ?, ?, ?, ?, 0)
            """, arrayOf(timestamp.toString(), exceptionName, message ?: "", stackTrace, if (isFatal) "1" else "0"))
            db.rawQuery("SELECT last_insert_rowid()", null).use { if (it.moveToFirst()) it.getLong(0) else -1L }
        }
    }

    fun getAll(limit: Int = 100): List<CrashEntity> = pool.readWith { db ->
        db.rawQuery(
            "SELECT id, timestamp, exception_name, message, stack_trace, is_fatal, remote_reported FROM crashes ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            val r = mutableListOf<CrashEntity>()
            while (c.moveToNext()) {
                r.add(
                    CrashEntity(
                        id = c.getLong(0),
                        timestamp = c.getLong(1),
                        exceptionName = c.getString(2),
                        message = c.getString(3),
                        stackTrace = c.getString(4),
                        isFatal = c.getInt(5) == 1,
                        remoteReported = c.getInt(6) == 1
                    )
                )
            }
            r
        }
    }

    fun getUnreported(limit: Int = 50): List<CrashEntity> = pool.readWith { db ->
        db.rawQuery(
            "SELECT id, timestamp, exception_name, message, stack_trace, is_fatal, remote_reported FROM crashes WHERE remote_reported = 0 ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            val r = mutableListOf<CrashEntity>()
            while (c.moveToNext()) {
                r.add(
                    CrashEntity(
                        id = c.getLong(0),
                        timestamp = c.getLong(1),
                        exceptionName = c.getString(2),
                        message = c.getString(3),
                        stackTrace = c.getString(4),
                        isFatal = c.getInt(5) == 1,
                        remoteReported = c.getInt(6) == 1
                    )
                )
            }
            r
        }
    }

    fun markReported(ids: List<Long>) = pool.write { db ->
        if (ids.isEmpty()) return@write
        val placeholders = ids.joinToString(",") { "?" }
        db.execSQL("UPDATE crashes SET remote_reported = 1 WHERE id IN ($placeholders)", ids.map { it.toString() }.toTypedArray())
    }

    fun delete(id: Long) = pool.write { db ->
        db.execSQL("DELETE FROM crashes WHERE id = ?", arrayOf(id.toString()))
    }

    fun deleteOlderThan(timestamp: Long) = pool.write { db ->
        db.execSQL("DELETE FROM crashes WHERE timestamp < ?", arrayOf(timestamp.toString()))
    }

    fun getCount(): Int = pool.readWith { db ->
        db.rawQuery("SELECT COUNT(*) FROM crashes", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun getUnreportedCount(): Int = pool.readWith { db ->
        db.rawQuery("SELECT COUNT(*) FROM crashes WHERE remote_reported = 0", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }
}