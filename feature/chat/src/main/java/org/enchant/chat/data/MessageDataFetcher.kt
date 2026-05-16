package org.enchant.chat.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.enchant.core.database.DatabasePool

data class ExtraMessageData(
    val reactions: List<Reaction> = emptyList(),
    val mentions: List<Mention> = emptyList(),
    val isPinned: Boolean = false
)
data class Reaction(val emoji: String, val userId: String, val timestamp: Long)
data class Mention(val userId: String, val start: Int, val length: Int)

class MessageDataFetcher(private val pool: DatabasePool) {
    suspend fun fetchExtraData(messageId: Long): ExtraMessageData = coroutineScope {
        val reactionsDeferred = async { loadReactions(messageId) }
        val mentionsDeferred = async { loadMentions(messageId) }
        val pinnedDeferred = async { isPinned(messageId) }
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
            val pinned = async { isPinned(id) }
            ExtraMessageData(reactions = reactions.await(), mentions = mentions.await(), isPinned = pinned.await())
        }
    }

    private fun loadReactions(messageId: Long): List<Reaction> = emptyList()
    private fun loadMentions(messageId: Long): List<Mention> = emptyList()
    private fun isPinned(messageId: Long): Boolean = false
}
