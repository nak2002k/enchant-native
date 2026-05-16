package org.enchant.chat.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import org.enchant.core.model.Message

class ChatPagingSource(
    private val repository: ConversationRepository,
    private val conversationId: String,
    private val pageSize: Int = 50
) : PagingSource<Long, Message>() {

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, Message> {
        return try {
            val cursor = params.key
            val page = repository.getMessagePage(
                conversationId = conversationId,
                cursor = cursor,
                limit = params.loadSize
            )
            LoadResult.Page(
                data = page.messages,
                prevKey = null,
                nextKey = page.nextCursor
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Long, Message>): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestItemToPosition(anchorPosition)?.localId
        }
    }
}
