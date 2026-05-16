package org.enchant.core.database.dao

import org.enchant.core.database.DatabasePool

data class StickerPackEntity(val packId: String, val title: String? = null, val cover: String? = null, val author: String? = null, val installedAt: Long = System.currentTimeMillis())

class StickerPackDao(private val pool: DatabasePool) {
    suspend fun install(pack: StickerPackEntity) = pool.write { db ->
        db.execSQL("INSERT OR REPLACE INTO sticker_packs (pack_id, title, cover, author, installed_at) VALUES (?, ?, ?, ?, ?)",
            arrayOf(pack.packId, pack.title, pack.cover, pack.author, pack.installedAt.toString()))
    }

    suspend fun uninstall(packId: String) = pool.write { db ->
        db.execSQL("DELETE FROM sticker_packs WHERE pack_id = ?", arrayOf(packId))
    }

    suspend fun getInstalled(): List<StickerPackEntity> = pool.readWith { db ->
        db.rawQuery("SELECT * FROM sticker_packs ORDER BY installed_at DESC", null).use { c ->
            val r = mutableListOf<StickerPackEntity>()
            while (c.moveToNext()) r.add(StickerPackEntity(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4)))
            r
        }
    }

    suspend fun getById(packId: String): StickerPackEntity? = pool.readWith { db ->
        db.rawQuery("SELECT * FROM sticker_packs WHERE pack_id = ?", arrayOf(packId)).use { c ->
            if (c.moveToFirst()) StickerPackEntity(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4)) else null
        }
    }

    suspend fun search(query: String): List<StickerPackEntity> = pool.readWith { db ->
        db.rawQuery("SELECT * FROM sticker_packs WHERE title LIKE ? LIMIT 20", arrayOf("%$query%")).use { c ->
            val r = mutableListOf<StickerPackEntity>()
            while (c.moveToNext()) r.add(StickerPackEntity(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4)))
            r
        }
    }
}
