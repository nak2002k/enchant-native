package org.enchant.core.database.dao

import org.enchant.core.database.DatabasePool

data class InstalledStickerEntity(val packId: String, val stickerId: String, val emoji: String? = null, val position: Int = 0)

class InstalledStickerDao(private val pool: DatabasePool) {
    suspend fun addSticker(sticker: InstalledStickerEntity) = pool.write { db ->
        db.execSQL("INSERT OR REPLACE INTO installed_stickers (pack_id, sticker_id, emoji, position) VALUES (?, ?, ?, ?)",
            arrayOf(sticker.packId, sticker.stickerId, sticker.emoji, sticker.position.toString()))
    }

    suspend fun removeSticker(packId: String, stickerId: String) = pool.write { db ->
        db.execSQL("DELETE FROM installed_stickers WHERE pack_id = ? AND sticker_id = ?", arrayOf(packId, stickerId))
    }

    suspend fun getStickersForPack(packId: String): List<InstalledStickerEntity> = pool.readWith { db ->
        db.rawQuery("SELECT * FROM installed_stickers WHERE pack_id = ? ORDER BY position ASC", arrayOf(packId)).use { c ->
            val r = mutableListOf<InstalledStickerEntity>()
            while (c.moveToNext()) r.add(InstalledStickerEntity(c.getString(0), c.getString(1), c.getString(2), c.getInt(3)))
            r
        }
    }

    suspend fun removeAllForPack(packId: String) = pool.write { db ->
        db.execSQL("DELETE FROM installed_stickers WHERE pack_id = ?", arrayOf(packId))
    }
}
