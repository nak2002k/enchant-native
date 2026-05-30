package org.enchant.core.database.dao

import org.enchant.core.database.DatabasePool
import org.enchant.core.database.entity.MediaCacheEntity

class MediaCacheDao(private val pool: DatabasePool) {
    suspend fun put(mediaId: String, localPath: String, fileSize: Long? = null) = pool.write { db ->
        db.execSQL("INSERT OR REPLACE INTO media_cache (media_id, local_path, file_size, last_accessed_at) VALUES (?, ?, ?, ?)",
            arrayOf(mediaId, localPath, fileSize?.toString(), System.currentTimeMillis().toString()))
    }

    suspend fun get(mediaId: String): MediaCacheEntity? = pool.readWith { db ->
        db.rawQuery("SELECT * FROM media_cache WHERE media_id = ?", arrayOf(mediaId)).use { c ->
            if (c.moveToFirst()) MediaCacheEntity(c.getString(0), c.getString(1), c.getLong(2), c.getLong(3)) else null
        }
    }

    suspend fun delete(mediaId: String) = pool.write { db ->
        db.execSQL("DELETE FROM media_cache WHERE media_id = ?", arrayOf(mediaId))
    }

    suspend fun prune(keepCount: Int = 100) = pool.write { db ->
        db.execSQL("DELETE FROM media_cache WHERE media_id NOT IN (SELECT media_id FROM media_cache ORDER BY last_accessed_at DESC LIMIT ?)", arrayOf(keepCount.toString()))
    }
}
