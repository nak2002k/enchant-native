package org.enchant.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.core.network.ApiClient

data class ChannelPost(
    val postId: String = "",
    val channelId: String = "",
    val authorId: String = "",
    val content: String = "",
    val mediaIds: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val createdAt: String = ""
)

data class Channel(
    val channelId: String = "",
    val name: String = "",
    val description: String? = null,
    val avatarMediaId: String? = null,
    val subscriberCount: Int = 0,
    val isSubscribed: Boolean = false
)

data class ChannelUiState(
    val feed: List<ChannelPost> = emptyList(),
    val pinnedPost: ChannelPost? = null,
    val channels: List<Channel> = emptyList(),
    val myChannels: List<Channel> = emptyList(),
    val discoverResults: List<Channel> = emptyList(),
    val searchResults: List<Channel> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val cursor: String? = null
)

class ChannelViewModel(
    private val apiClient: ApiClient
) : ViewModel() {
    constructor() : this(org.enchant.core.network.ApiClient.getInstance())
    private val _uiState = MutableStateFlow(ChannelUiState())
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    fun loadFeed(channelId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiClient.get("/v1/channels/$channelId/feed", mapOf(
                    "limit" to "20"
                ))
                response.fold(
                    onSuccess = { json ->
                        val posts = json["posts"]?.jsonArray?.map { item ->
                            val obj = item.jsonObject
                            ChannelPost(
                                postId = obj["post_id"]?.jsonPrimitive?.content ?: "",
                                channelId = obj["channel_id"]?.jsonPrimitive?.content ?: "",
                                authorId = obj["author_id"]?.jsonPrimitive?.content ?: "",
                                content = obj["content"]?.jsonPrimitive?.content ?: "",
                                isPinned = obj["is_pinned"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                                createdAt = obj["created_at"]?.jsonPrimitive?.content ?: ""
                            )
                        } ?: emptyList()
                        val pinned = posts.find { it.isPinned }
                        val cursor = json["cursor"]?.jsonPrimitive?.content
                        _uiState.value = _uiState.value.copy(
                            feed = posts.filter { !it.isPinned },
                            pinnedPost = pinned,
                            cursor = cursor,
                            isLoading = false
                        )
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun loadMore(channelId: String) {
        val cursor = _uiState.value.cursor ?: return
        if (_uiState.value.isLoadingMore) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val response = apiClient.get("/v1/channels/$channelId/feed", mapOf(
                    "limit" to "20",
                    "before" to cursor
                ))
                response.fold(
                    onSuccess = { json ->
                        val posts = json["posts"]?.jsonArray?.map { item ->
                            val obj = item.jsonObject
                            ChannelPost(
                                postId = obj["post_id"]?.jsonPrimitive?.content ?: "",
                                channelId = obj["channel_id"]?.jsonPrimitive?.content ?: "",
                                authorId = obj["author_id"]?.jsonPrimitive?.content ?: "",
                                content = obj["content"]?.jsonPrimitive?.content ?: "",
                                isPinned = obj["is_pinned"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                                createdAt = obj["created_at"]?.jsonPrimitive?.content ?: ""
                            )
                        } ?: emptyList()
                        val newCursor = json["cursor"]?.jsonPrimitive?.content
                        _uiState.value = _uiState.value.copy(
                            feed = _uiState.value.feed + posts.filter { !it.isPinned },
                            cursor = newCursor,
                            isLoadingMore = false
                        )
                    },
                    onFailure = {
                        android.util.Log.w("ChannelVM", "loadMore failed: ${it.message}")
                        _uiState.value = _uiState.value.copy(isLoadingMore = false, error = it.message)
                    }
                )
            } catch (e: Exception) {
                android.util.Log.w("ChannelVM", "loadMore exception: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoadingMore = false, error = e.message)
            }
        }
    }

    fun subscribe(channelId: String) {
        viewModelScope.launch {
            try {
                apiClient.post("/v1/channels/$channelId/subscribe")
                _uiState.value = _uiState.value.copy(
                    successMessage = "Subscribed",
                    channels = _uiState.value.channels.map {
                        if (it.channelId == channelId) it.copy(isSubscribed = true) else it
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun unsubscribe(channelId: String) {
        viewModelScope.launch {
            try {
                apiClient.del("/v1/channels/$channelId/subscribe")
                _uiState.value = _uiState.value.copy(
                    successMessage = "Unsubscribed",
                    channels = _uiState.value.channels.map {
                        if (it.channelId == channelId) it.copy(isSubscribed = false) else it
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun discoverChannels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiClient.get("/v1/channels/discover")
                response.fold(
                    onSuccess = { json ->
                        val results = json["channels"]?.jsonArray?.map { item ->
                            val obj = item.jsonObject
                            Channel(
                                channelId = obj["channel_id"]?.jsonPrimitive?.content ?: "",
                                name = obj["name"]?.jsonPrimitive?.content ?: "",
                                description = obj["description"]?.jsonPrimitive?.content,
                                subscriberCount = obj["subscriber_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                                isSubscribed = obj["is_subscribed"]?.jsonPrimitive?.content?.toBoolean() ?: false
                            )
                        } ?: emptyList()
                        _uiState.value = _uiState.value.copy(
                            discoverResults = results, isLoading = false
                        )
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun createChannel(name: String, description: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val body = buildJsonObject {
                    put("name", name)
                    if (description != null) put("description", description)
                }
                val response = apiClient.post("/v1/channels", body)
                response.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false, successMessage = "Channel created"
                        )
                        loadMyChannels()
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun loadMyChannels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiClient.get("/v1/channels/my")
                response.fold(
                    onSuccess = { json ->
                        val channels = json["channels"]?.jsonArray?.map { item ->
                            val obj = item.jsonObject
                            Channel(
                                channelId = obj["channel_id"]?.jsonPrimitive?.content ?: "",
                                name = obj["name"]?.jsonPrimitive?.content ?: "",
                                description = obj["description"]?.jsonPrimitive?.content,
                                subscriberCount = obj["subscriber_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                                isSubscribed = true
                            )
                        } ?: emptyList()
                        _uiState.value = _uiState.value.copy(
                            myChannels = channels, isLoading = false
                        )
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun searchChannels(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
            return
        }
        viewModelScope.launch {
            try {
                val response = apiClient.get("/v1/channels/search", mapOf("q" to query))
                response.fold(
                    onSuccess = { json ->
                        val results = json["channels"]?.jsonArray?.map { item ->
                            val obj = item.jsonObject
                            Channel(
                                channelId = obj["channel_id"]?.jsonPrimitive?.content ?: "",
                                name = obj["name"]?.jsonPrimitive?.content ?: "",
                                description = obj["description"]?.jsonPrimitive?.content,
                                subscriberCount = obj["subscriber_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                                isSubscribed = obj["is_subscribed"]?.jsonPrimitive?.content?.toBoolean() ?: false
                            )
                        } ?: emptyList()
                        _uiState.value = _uiState.value.copy(searchResults = results)
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(searchResults = emptyList())
                    }
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(searchResults = emptyList())
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
