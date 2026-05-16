package org.enchant.chat.data

import org.enchant.core.model.Message

class ChatPagingSource(
    private val repository: ConversationRepository,
    private val conversationId: String,
    private val pageSize: Int = 50
) {
    private var currentCursor: Long? = null
    private var hasMore = true

    suspend fun loadNext(): List<Message> {
        if (!hasMore) return emptyList()
        val page = repository.getMessagePage(conversationId, currentCursor, pageSize)
        currentCursor = page.nextCursor
        hasMore = page.hasMore
        return page.messages
    }

    suspend fun loadPrevious(): List<Message> {
        return loadNext()
    }

    suspend fun refresh(): List<Message> {
        currentCursor = null
        hasMore = true
        return loadNext()
    }

    fun hasMorePages(): Boolean = hasMore

    fun reset() {
        currentCursor = null
        hasMore = true
    }
}
