package org.enchant.core.database.dao

import org.enchant.core.database.DatabasePool

data class GroupEntity(
    val groupId: String, val name: String, val description: String? = null,
    val avatarMediaId: String? = null, val myRole: String = "member", val memberCount: Int = 0
)

class GroupDao(private val pool: DatabasePool) {
    suspend fun insert(group: GroupEntity) = pool.write { db ->
        db.execSQL("INSERT OR REPLACE INTO groups (group_id, name, description, avatar_media_id, my_role, member_count) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf(group.groupId, group.name, group.description, group.avatarMediaId, group.myRole, group.memberCount.toString()))
    }

    suspend fun getById(groupId: String): GroupEntity? = pool.readWith { db ->
        db.rawQuery("SELECT * FROM groups WHERE group_id = ?", arrayOf(groupId)).use { c ->
            if (c.moveToFirst()) GroupEntity(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5)) else null
        }
    }

    suspend fun getAll(): List<GroupEntity> = pool.readWith { db ->
        db.rawQuery("SELECT * FROM groups ORDER BY name ASC", null).use { c ->
            val r = mutableListOf<GroupEntity>(); while (c.moveToNext()) r.add(GroupEntity(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5))); r
        }
    }

    suspend fun update(groupId: String, name: String? = null, description: String? = null, avatarMediaId: String? = null) = pool.write { db ->
        val sets = mutableListOf<String>(); val args = mutableListOf<String>()
        name?.let { sets.add("name = ?"); args.add(it) }
        description?.let { sets.add("description = ?"); args.add(it) }
        avatarMediaId?.let { sets.add("avatar_media_id = ?"); args.add(it) }
        if (sets.isNotEmpty()) { args.add(groupId); db.execSQL("UPDATE groups SET ${sets.joinToString(", ")} WHERE group_id = ?", args.toTypedArray()) }
    }

    suspend fun delete(groupId: String) = pool.write { db ->
        db.execSQL("DELETE FROM groups WHERE group_id = ?", arrayOf(groupId))
    }
}
