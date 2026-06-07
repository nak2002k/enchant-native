package org.enchant.core.database.dao

import org.enchant.core.database.DatabasePool
import org.enchant.core.database.entity.DraftEntity
import org.enchant.core.database.util.DatabaseNotifier

class DraftDao(private val pool: DatabasePool) {
    suspend fun save(draft: DraftEntity) = pool.write { db ->
        db.execSQL(
            "INSERT OR REPLACE INTO drafts (conversation_id, content, timestamp) VALUES (?, ?, ?)",
            arrayOf(draft.conversationId, draft.content, draft.timestamp.toString())
        )
        DatabaseNotifier.notify("drafts")
    }

    suspend fun get(conversationId: String): DraftEntity? = pool.readWith { db ->
        db.rawQuery("SELECT * FROM drafts WHERE conversation_id = ?", arrayOf(conversationId))
            .use {
                if (it.moveToFirst()) DraftEntity(
                    conversationId = it.getString(0),
                    content = it.getString(1),
                    timestamp = it.getLong(2)
                ) else null
            }
    }

    suspend fun delete(conversationId: String) = pool.write { db ->
        db.execSQL("DELETE FROM drafts WHERE conversation_id = ?", arrayOf(conversationId))
        DatabaseNotifier.notify("drafts")
    }

    suspend fun getAll(): List<DraftEntity> = pool.readWith { db ->
        db.rawQuery("SELECT * FROM drafts ORDER BY timestamp DESC", null).use {
            val list = mutableListOf<DraftEntity>()
            while (it.moveToNext()) {
                list.add(DraftEntity(
                    conversationId = it.getString(0),
                    content = it.getString(1),
                    timestamp = it.getLong(2)
                ))
            }
            list
        }
    }
}
