package org.enchant.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.enchant.chat.data.ConversationFilter
import org.enchant.chat.data.ConversationRepository
import org.enchant.core.model.Conversation
import org.enchant.core.network.ApiClient

class ConversationListViewModel(
    private val repo: ConversationRepository,
    private val apiClient: ApiClient
) : ViewModel() {
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _filter = MutableStateFlow(ConversationFilter.ALL)
    val filter: StateFlow<ConversationFilter> = _filter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _navigationEvent = MutableStateFlow<String?>(null)
    val navigationEvent: StateFlow<String?> = _navigationEvent.asStateFlow()

    private var collectJob: Job? = null
    private var searchJob: Job? = null

    fun init() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            repo.getConversations(_filter.value).collect { list ->
                _conversations.value = list
            }
        }
        viewModelScope.launch {
            repo.getUnreadCount().collect { count ->
                _unreadCount.value = count
            }
        }
    }

    fun selectFilter(filter: ConversationFilter) {
        _filter.value = filter
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            repo.getConversations(filter).collect { list ->
                _conversations.value = list
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            collectJob?.cancel()
            collectJob = viewModelScope.launch {
                repo.getConversations(_filter.value).collect { list ->
                    _conversations.value = list
                }
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            repo.searchConversations(query).collect { list ->
                _conversations.value = list
            }
        }
    }

    fun archiveConversation(conversationId: String) {
        viewModelScope.launch {
            repo.setArchived(conversationId, true)
            try {
                apiClient.post("/v1/chats/$conversationId/archive", null)
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
        }
    }

    fun unarchiveConversation(conversationId: String) {
        viewModelScope.launch {
            repo.setArchived(conversationId, false)
            try {
                apiClient.del("/v1/chats/$conversationId/archive")
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
        }
    }

    fun pinConversation(conversationId: String) {
        viewModelScope.launch {
            val conv = repo.getConversation(conversationId) ?: return@launch
            repo.setPinned(conversationId, !conv.isPinned)
        }
    }

    fun muteConversation(conversationId: String, until: Long? = null) {
        viewModelScope.launch {
            repo.setMuted(conversationId, until != null, until)
            try {
                apiClient.put("/v1/notifications/preferences/conversations/$conversationId",
                    kotlinx.serialization.json.buildJsonObject {
                        put("muted", kotlinx.serialization.json.JsonPrimitive(until != null))
                        if (until != null) put("mute_duration_seconds", kotlinx.serialization.json.JsonPrimitive((until - System.currentTimeMillis()) / 1000))
                    }
                )
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            repo.deleteConversation(conversationId)
        }
    }

    fun markRead(conversationId: String) {
        viewModelScope.launch {
            repo.markConversationRead(conversationId)
            try {
                apiClient.post("/v1/messages/read", kotlinx.serialization.json.buildJsonObject {
                    put("conversation_id", kotlinx.serialization.json.JsonPrimitive(conversationId))
                })
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val response = apiClient.get("/v1/messages/pending")
                response.fold(
                    onSuccess = {
                        collectJob?.cancel()
                        collectJob = viewModelScope.launch {
                            repo.getConversations(_filter.value).collect { list ->
                                _conversations.value = list
                            }
                        }
                    },
                    onFailure = {}
                )
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
            _isRefreshing.value = false
        }
    }

    fun selectConversation(conversationId: String) {
        _navigationEvent.value = conversationId
    }

    fun clearNavigationEvent() {
        _navigationEvent.value = null
    }

    override fun onCleared() {
        super.onCleared()
        collectJob?.cancel()
        searchJob?.cancel()
    }
}
