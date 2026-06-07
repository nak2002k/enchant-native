package org.enchant.core.database.dao

import org.enchant.core.database.DatabasePool
import org.enchant.core.database.entity.ChatFolderEntity
import org.enchant.core.database.util.DatabaseNotifier

class ChatFolderDao(private val pool: DatabasePool) {
    suspend fun insert(folder: ChatFolderEntity) = pool.write { db ->
        db.execSQL(
            "INSERT OR REPLACE INTO chat_folders (folder_id, name, position, conversation_ids) VALUES (?, ?, ?, ?)",
            arrayOf(folder.folderId, folder.name, folder.position.toString(), folder.conversationIds)
        )
        DatabaseNotifier.notify("chat_folders")
    }

    suspend fun getAll(): List<ChatFolderEntity> = pool.readWith { db ->
        db.rawQuery("SELECT * FROM chat_folders ORDER BY position ASC", null).use {
            val list = mutableListOf<ChatFolderEntity>()
            while (it.moveToNext()) {
                list.add(ChatFolderEntity(
                    folderId = it.getString(0),
                    name = it.getString(1),
                    position = it.getInt(2),
                    conversationIds = it.getString(3)
                ))
            }
            list
        }
    }

    suspend fun delete(folderId: String) = pool.write { db ->
        db.execSQL("DELETE FROM chat_folders WHERE folder_id = ?", arrayOf(folderId))
        DatabaseNotifier.notify("chat_folders")
    }

    suspend fun updateConversations(folderId: String, conversationIds: String) = pool.write { db ->
        db.execSQL("UPDATE chat_folders SET conversation_ids = ? WHERE folder_id = ?", arrayOf(conversationIds, folderId))
        DatabaseNotifier.notify("chat_folders")
    }
}
