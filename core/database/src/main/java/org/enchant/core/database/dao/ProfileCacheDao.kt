package org.enchant.core.database.dao

import org.enchant.core.database.DatabasePool
import org.enchant.core.database.entity.ProfileCacheEntity

class ProfileCacheDao(private val pool: DatabasePool) {
    suspend fun upsert(profile: ProfileCacheEntity) = pool.write { db ->
        db.execSQL("INSERT OR REPLACE INTO profile_cache (user_id, display_name, username, about, avatar_media_id) VALUES (?, ?, ?, ?, ?)",
            arrayOf(profile.userId, profile.displayName, profile.username, profile.about, profile.avatarMediaId))
    }

    suspend fun getByUserId(userId: String): ProfileCacheEntity? = pool.readWith { db ->
        db.rawQuery("SELECT * FROM profile_cache WHERE user_id = ?", arrayOf(userId)).use { c ->
            if (c.moveToFirst()) ProfileCacheEntity(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5)) else null
        }
    }

    suspend fun delete(userId: String) = pool.write { db ->
        db.execSQL("DELETE FROM profile_cache WHERE user_id = ?", arrayOf(userId))
    }

    suspend fun search(query: String): List<ProfileCacheEntity> = pool.readWith { db ->
        db.rawQuery("SELECT * FROM profile_cache WHERE username LIKE ? OR display_name LIKE ? LIMIT 20", arrayOf("%$query%", "%$query%")).use { c ->
            val r = mutableListOf<ProfileCacheEntity>()
            while (c.moveToNext()) r.add(ProfileCacheEntity(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5)))
            r
        }
    }
}
