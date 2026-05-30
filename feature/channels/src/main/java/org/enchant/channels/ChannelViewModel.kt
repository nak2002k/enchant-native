package org.enchant.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.core.network.ApiClient
import org.enchant.core.base.SecurePreferences

data class ChannelPost(
    val postId: String = "",
    val channelId: String = "",
    val authorId: String = "",
    val content: String = "",
    val mediaIds: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val createdAt: String = ""
) {
    fun isOwn(): Boolean = authorId == SecurePreferences.getString("auth.user_id") ?: ""
}

data class Channel(
    val channelId: String = "",
    val name: String = "",
    val description: String? = null,
    val avatarMediaId: String? = null,
    val subscriberCount: Int = 0,
    val isSubscribed: Boolean = false,
    val isAdmin: Boolean = false
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
    private var feedJob: Job? = null

    private fun parsePosts(json: JsonObject): List<ChannelPost> {
        return json["posts"]?.jsonArray?.map { item ->
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
    }

    fun loadFeed(channelId: String) {
        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, cursor = null)
            try {
                val response = apiClient.get("/v1/channels/$channelId/feed", mapOf(
                    "limit" to "20"
                ))
                response.fold(
                    onSuccess = { json ->
                        val posts = parsePosts(json)
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
                        val posts = parsePosts(json)
                        val newCursor = json["cursor"]?.jsonPrimitive?.content
                        val existingIds = _uiState.value.feed.map { it.postId }.toSet()
                        val newPosts = posts.filter { !it.isPinned && it.postId !in existingIds }
                        _uiState.value = _uiState.value.copy(
                            feed = _uiState.value.feed + newPosts,
                            cursor = newCursor,
                            isLoadingMore = false
                        )
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(isLoadingMore = false, error = it.message)
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false, error = e.message)
            }
        }
    }

    private fun updateChannelSubscription(channelId: String, subscribed: Boolean) {
        _uiState.value = _uiState.value.copy(
            channels = _uiState.value.channels.map {
                if (it.channelId == channelId) it.copy(isSubscribed = subscribed) else it
            },
            discoverResults = _uiState.value.discoverResults.map {
                if (it.channelId == channelId) it.copy(isSubscribed = subscribed) else it
            },
            searchResults = _uiState.value.searchResults.map {
                if (it.channelId == channelId) it.copy(isSubscribed = subscribed) else it
            }
        )
    }

    fun subscribe(channelId: String) {
        viewModelScope.launch {
            try {
                apiClient.post("/v1/channels/$channelId/subscribe")
                updateChannelSubscription(channelId, true)
                _uiState.value = _uiState.value.copy(successMessage = "Subscribed")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun unsubscribe(channelId: String) {
        viewModelScope.launch {
            try {
                apiClient.del("/v1/channels/$channelId/subscribe")
                updateChannelSubscription(channelId, false)
                _uiState.value = _uiState.value.copy(successMessage = "Unsubscribed")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun editPost(channelId: String, postId: String, content: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val body = buildJsonObject { put("text_content", content) }
                val response = apiClient.post("/v1/channels/$channelId/posts/$postId", body)
                response.fold(
                    onSuccess = {
                        val updatedPosts = _uiState.value.feed.map {
                            if (it.postId == postId) it.copy(content = content) else it
                        }
                        val updatedPinned = _uiState.value.pinnedPost?.let {
                            if (it.postId == postId) it.copy(content = content) else it
                        }
                        _uiState.value = _uiState.value.copy(
                            feed = updatedPosts,
                            pinnedPost = updatedPinned,
                            isLoading = false,
                            successMessage = "Post updated"
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

    fun deletePost(channelId: String, postId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiClient.del("/v1/channels/$channelId/posts/$postId")
                response.fold(
                    onSuccess = {
                        val updatedPosts = _uiState.value.feed.filter { it.postId != postId }
                        val updatedPinned = if (_uiState.value.pinnedPost?.postId == postId) null else _uiState.value.pinnedPost
                        _uiState.value = _uiState.value.copy(
                            feed = updatedPosts,
                            pinnedPost = updatedPinned,
                            isLoading = false,
                            successMessage = "Post deleted"
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

    fun pinPost(channelId: String, postId: String, pinned: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)
            try {
                val response = apiClient.put("/v1/channels/$channelId/posts/$postId/pin")
                response.fold(
                    onSuccess = {
                        val currentPinned = _uiState.value.pinnedPost
                        val targetPost = (_uiState.value.feed + listOfNotNull(currentPinned)).find { it.postId == postId }
                        val updatedPosts = if (pinned && targetPost != null) {
                            _uiState.value.feed.filter { it.postId != postId }
                        } else {
                            _uiState.value.feed
                        }
                        val newPinned = if (pinned && targetPost != null) {
                            targetPost.copy(isPinned = true)
                        } else if (!pinned && currentPinned?.postId == postId) {
                            null
                        } else {
                            currentPinned
                        }
                        _uiState.value = _uiState.value.copy(
                            feed = updatedPosts,
                            pinnedPost = newPinned,
                            successMessage = if (pinned) "Post pinned" else "Post unpinned"
                        )
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(error = it.message)
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
                        _uiState.value = _uiState.value.copy(searchResults = emptyList(), error = it.message)
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(searchResults = emptyList(), error = e.message)
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
