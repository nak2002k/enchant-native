package org.enchant.backup.archive

import android.content.ContentValues
import org.enchant.core.database.DatabasePool

data class GroupArchive(
    val groupId: String,
    val name: String,
    val description: String? = null,
    val memberIds: List<String> = emptyList(),
    val settings: Map<String, String> = emptyMap()
)

class GroupArchiveExporter(private val pool: DatabasePool) {

    suspend fun exportGroups(): List<GroupArchive> {
        val db = pool.readWith { db -> db }
        val groups = mutableListOf<GroupArchive>()
        val cursor = db.rawQuery("SELECT group_id, name, description FROM groups_table", null)
        while (cursor.moveToNext()) {
            val groupId = cursor.getString(0) ?: ""
            val members = mutableListOf<String>()
            val memberCursor = db.rawQuery(
                "SELECT user_id FROM group_members WHERE group_id = ?",
                arrayOf(groupId)
            )
            while (memberCursor.moveToNext()) {
                members.add(memberCursor.getString(0))
            }
            memberCursor.close()
            groups.add(
                GroupArchive(
                    groupId = groupId,
                    name = cursor.getString(1) ?: "",
                    description = cursor.getString(2),
                    memberIds = members
                )
            )
        }
        cursor.close()
        return groups
    }

    suspend fun importGroups(archives: List<GroupArchive>) {
        val db = pool.write { db -> db }
        val existingIds = mutableSetOf<String>()
        val cursor = db.rawQuery("SELECT group_id FROM groups_table", null)
        while (cursor.moveToNext()) {
            existingIds.add(cursor.getString(0))
        }
        cursor.close()

        db.beginTransaction()
        try {
            archives.forEach { group ->
                if (group.groupId !in existingIds) {
                    val values = ContentValues().apply {
                        put("group_id", group.groupId)
                        put("name", group.name)
                        put("description", group.description)
                    }
                    db.insert("groups_table", null, values)
                    group.memberIds.forEach { userId ->
                        val memberValues = ContentValues().apply {
                            put("group_id", group.groupId)
                            put("user_id", userId)
                            put("role", "member")
                        }
                        db.insert("group_members", null, memberValues)
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
