package org.enchant.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.enchant.chat.data.ConversationFilter
import org.enchant.chat.data.ConversationRepository
import org.enchant.core.model.Conversation
import org.enchant.core.network.ApiClient
import org.enchant.core.network.IncomingEnvelope
import org.enchant.core.network.WebSocketManager
import org.enchant.protos.EnvelopeProtos

class ConversationListViewModel(
    private val repo: ConversationRepository = ConversationListViewModel.defaultRepo(),
    private val apiClient: ApiClient = ApiClient.getInstance()
) : ViewModel() {

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L

        internal fun defaultRepo(): ConversationRepository {
            val pool = org.enchant.core.database.DatabasePool.instance
                ?: error("DatabasePool not initialized")
            return ConversationRepository(
                messageDao = org.enchant.core.database.dao.MessageDao(pool),
                conversationDao = org.enchant.core.database.dao.ConversationDao(pool),
                recipientDao = org.enchant.core.database.dao.RecipientDao(pool),
                pool = pool
            )
        }
    }

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _titles = MutableStateFlow<Map<String, String>>(emptyMap())
    val titles: StateFlow<Map<String, String>> = _titles.asStateFlow()

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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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

    /** Resolve display names for direct conversations (falls back to profile). */
    fun resolveTitles(ids: List<String>) {
        viewModelScope.launch {
            val need = ids.distinct().filter { it !in _titles.value }
            if (need.isEmpty()) return@launch
            need.forEach { id ->
                val name = repo.resolveDisplayName(id)
                if (name != null) {
                    _titles.update { it + (id to name) }
                }
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
            delay(SEARCH_DEBOUNCE_MS)
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
            } catch (e: Exception) {
                android.util.Log.e("Enchant", "archive failed for $conversationId", e)
                _errorMessage.value = "Archive failed"
            }
        }
    }

    fun unarchiveConversation(conversationId: String) {
        viewModelScope.launch {
            repo.setArchived(conversationId, false)
            try {
                apiClient.del("/v1/chats/$conversationId/archive")
            } catch (e: Exception) {
                android.util.Log.e("Enchant", "unarchive failed for $conversationId", e)
                _errorMessage.value = "Unarchive failed"
            }
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
            val conv = repo.getConversation(conversationId) ?: return@launch
            // Toggle when no explicit duration is given (Signal default: 1h).
            val muted = if (until == null) !conv.isMuted else true
            val muteUntil = if (muted) (until ?: System.currentTimeMillis() + 3600_000L) else null
            repo.setMuted(conversationId, muted, muteUntil)
            try {
                apiClient.put("/v1/notifications/preferences/conversations/$conversationId",
                    kotlinx.serialization.json.buildJsonObject {
                        put("muted", kotlinx.serialization.json.JsonPrimitive(muted))
                        if (muteUntil != null) put("mute_duration_seconds", kotlinx.serialization.json.JsonPrimitive((muteUntil - System.currentTimeMillis()) / 1000))
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("Enchant", "mute failed for $conversationId", e)
                _errorMessage.value = "Mute failed"
            }
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
            } catch (e: Exception) {
                android.util.Log.e("Enchant", "markRead failed for $conversationId", e)
                _errorMessage.value = "Mark read failed"
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                apiClient.get("/v1/messages/pending").onSuccess { json ->
                    val messages = json["messages"]?.jsonArray ?: return@onSuccess
                    for (raw in messages) {
                        val bytes = raw.jsonArray.mapNotNull { it.jsonPrimitive.content.toIntOrNull() }
                            .map { it.toByte() }.toByteArray()
                        if (bytes.isEmpty()) continue
                        try {
                            val env = EnvelopeProtos.Envelope.parseFrom(bytes)
                            val envelope = IncomingEnvelope(
                                envelopeId = env.envelopeId.ifEmpty { null },
                                senderUserId = env.senderUserId.ifEmpty { null },
                                senderDeviceId = env.senderDeviceId.ifEmpty { null },
                                messageType = env.messageType.ifEmpty { "ENVELOPE" },
                                payload = env.payload.toByteArray(),
                                serverTimestamp = if (env.hasServerTs()) env.serverTs else null,
                                ephemeral = env.ephemeral,
                                sealed = env.sealed,
                                replyToken = env.replyToken.ifEmpty { null },
                                requestId = null
                            )
                            WebSocketManager.incomingHandler?.invoke(envelope)
                        } catch (e: Exception) {
                            android.util.Log.e("Enchant", "Pending envelope parse failed", e)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Enchant", "refresh failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun selectConversation(conversationId: String) {
        _navigationEvent.value = conversationId
    }

    fun clearNavigationEvent() {
        _navigationEvent.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        collectJob?.cancel()
        searchJob?.cancel()
    }
}
