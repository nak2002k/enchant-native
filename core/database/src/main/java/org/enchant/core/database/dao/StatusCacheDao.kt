package org.enchant.core.database.dao

import org.enchant.core.database.DatabasePool
import org.enchant.core.database.entity.StatusCacheEntity

class StatusCacheDao(private val pool: DatabasePool) {
    suspend fun insert(status: StatusCacheEntity) = pool.write { db ->
        db.execSQL("INSERT OR REPLACE INTO status_cache (status_id, author_id, status_type, text_content, media_id, background_color, timestamp, viewed) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(status.statusId, status.authorId, status.statusType, status.textContent, status.mediaId, status.backgroundColor, status.timestamp?.toString(), if (status.viewed) "1" else "0"))
    }

    suspend fun getFeed(): Map<String, List<StatusCacheEntity>> = pool.readWith { db ->
        val r = linkedMapOf<String, MutableList<StatusCacheEntity>>()
        db.rawQuery("SELECT * FROM status_cache ORDER BY viewed ASC, timestamp DESC", null).use { c ->
            while (c.moveToNext()) {
                val e = StatusCacheEntity(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getLong(6), c.getInt(7) == 1)
                r.getOrPut(e.authorId) { mutableListOf() }.add(e)
            }
        }
        r
    }

    suspend fun markViewed(statusId: String) = pool.write { db ->
        db.execSQL("UPDATE status_cache SET viewed = 1 WHERE status_id = ?", arrayOf(statusId))
    }

    suspend fun deleteExpired(before: Long) = pool.write { db ->
        db.execSQL("DELETE FROM status_cache WHERE timestamp < ?", arrayOf(before.toString()))
    }

    suspend fun delete(statusId: String) = pool.write { db ->
        db.execSQL("DELETE FROM status_cache WHERE status_id = ?", arrayOf(statusId))
    }
}
