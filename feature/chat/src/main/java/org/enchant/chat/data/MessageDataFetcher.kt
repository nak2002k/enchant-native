package org.enchant.chat.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.enchant.core.database.DatabasePool

data class ExtraMessageData(
    val reactions: List<Reaction> = emptyList(),
    val mentions: List<Mention> = emptyList(),
    val isPinned: Boolean = false
)
data class Reaction(val emoji: String, val userId: String, val timestamp: Long = 0L)
data class Mention(val userId: String, val start: Int, val length: Int)

class MessageDataFetcher(private val pool: DatabasePool) {
    suspend fun fetchExtraData(messageId: Long): ExtraMessageData = coroutineScope {
        val reactionsDeferred = async { loadReactions(messageId) }
        val mentionsDeferred = async { loadMentions(messageId) }
        val pinnedDeferred = async { loadPinned(messageId) }
        ExtraMessageData(
            reactions = reactionsDeferred.await(),
            mentions = mentionsDeferred.await(),
            isPinned = pinnedDeferred.await()
        )
    }

    suspend fun fetchExtraDataBatch(messageIds: List<Long>): Map<Long, ExtraMessageData> = coroutineScope {
        messageIds.associateWith { id ->
            val reactions = async { loadReactions(id) }
            val mentions = async { loadMentions(id) }
            val pinned = async { loadPinned(id) }
            ExtraMessageData(reactions = reactions.await(), mentions = mentions.await(), isPinned = pinned.await())
        }
    }

    private fun loadReactions(messageId: Long): List<Reaction> = pool.readWith { db ->
        val c = db.rawQuery(
            "SELECT emoji, user_id, timestamp FROM reactions WHERE message_local_id = ? ORDER BY timestamp",
            arrayOf(messageId.toString())
        )
        c.use {
            val r = mutableListOf<Reaction>()
            while (it.moveToNext()) {
                r.add(Reaction(it.getString(0), it.getString(1), it.getLong(2)))
            }
            r
        }
    }

    private fun loadMentions(messageId: Long): List<Mention> = pool.readWith { db ->
        val c = db.rawQuery(
            "SELECT user_id, start_pos, length FROM message_mentions WHERE message_local_id = ?",
            arrayOf(messageId.toString())
        )
        c.use {
            val r = mutableListOf<Mention>()
            while (it.moveToNext()) {
                r.add(Mention(it.getString(0), it.getInt(1), it.getInt(2)))
            }
            r
        }
    }

    private fun loadPinned(messageId: Long): Boolean = false
}
