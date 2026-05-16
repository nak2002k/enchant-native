package org.enchant.core.database.dao

import org.enchant.core.database.DatabasePool

data class GroupMemberEntity(val groupId: String, val userId: String, val role: String, val joinedAt: Long? = null)

class GroupMemberDao(private val pool: DatabasePool) {
    suspend fun addMember(groupId: String, userId: String, role: String = "member") = pool.write { db ->
        db.execSQL("INSERT OR REPLACE INTO group_members (group_id, user_id, role, joined_at) VALUES (?, ?, ?, ?)",
            arrayOf(groupId, userId, role, System.currentTimeMillis().toString()))
    }

    suspend fun removeMember(groupId: String, userId: String) = pool.write { db ->
        db.execSQL("DELETE FROM group_members WHERE group_id = ? AND user_id = ?", arrayOf(groupId, userId))
    }

    suspend fun getMembers(groupId: String): List<GroupMemberEntity> = pool.readWith { db ->
        db.rawQuery("SELECT * FROM group_members WHERE group_id = ?", arrayOf(groupId)).use { c ->
            val r = mutableListOf<GroupMemberEntity>()
            while (c.moveToNext()) r.add(GroupMemberEntity(c.getString(0), c.getString(1), c.getString(2), c.getLong(3)))
            r
        }
    }

    suspend fun getGroupsForUser(userId: String): List<String> = pool.readWith { db ->
        db.rawQuery("SELECT group_id FROM group_members WHERE user_id = ?", arrayOf(userId)).use { c ->
            val r = mutableListOf<String>(); while (c.moveToNext()) r.add(c.getString(0)); r
        }
    }

    suspend fun updateRole(groupId: String, userId: String, role: String) = pool.write { db ->
        db.execSQL("UPDATE group_members SET role = ? WHERE group_id = ? AND user_id = ?", arrayOf(role, groupId, userId))
    }
}
