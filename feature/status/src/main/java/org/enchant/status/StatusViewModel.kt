package org.enchant.status

import android.util.Log
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

sealed class StatusPrivacy {
    data object AllContacts : StatusPrivacy()
    data object Selected : StatusPrivacy()
    data object CloseFriends : StatusPrivacy()
}

data class StatusViewer(
    val userId: String,
    val username: String,
    val viewedAt: String
)

data class StatusFeedEntry(
    val statusId: String = "",
    val userId: String = "",
    val username: String = "",
    val type: String = "text",
    val text: String? = null,
    val mediaId: String? = null,
    val backgroundColor: String? = null,
    val createdAt: String = "",
    val viewedBy: List<StatusViewer> = emptyList(),
    val isViewed: Boolean = false
)

data class StatusUiState(
    val feed: List<StatusFeedEntry> = emptyList(),
    val myStatus: StatusFeedEntry? = null,
    val viewers: List<StatusViewer> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

class StatusViewModel(
    private val apiClient: ApiClient
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatusUiState())
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    fun loadFeed() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiClient.get("/v1/status/feed")
                response.fold(
                    onSuccess = { json ->
                        val entries = json["statuses"]?.jsonArray?.map { item ->
                            val obj = item.jsonObject
                            StatusFeedEntry(
                                statusId = obj["status_id"]?.jsonPrimitive?.content ?: "",
                                userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                                username = obj["username"]?.jsonPrimitive?.content ?: "",
                                type = obj["type"]?.jsonPrimitive?.content ?: "text",
                                text = obj["text"]?.jsonPrimitive?.content,
                                mediaId = obj["media_id"]?.jsonPrimitive?.content,
                                backgroundColor = obj["background_color"]?.jsonPrimitive?.content,
                                createdAt = obj["created_at"]?.jsonPrimitive?.content ?: "",
                                isViewed = obj["is_viewed"]?.jsonPrimitive?.content?.toBoolean() ?: false
                            )
                        } ?: emptyList()
                        val my = entries.find { it.userId == "me" }
                        _uiState.value = _uiState.value.copy(
                            feed = entries,
                            myStatus = my,
                            isLoading = false
                        )
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false, error = it.message
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun createTextStatus(text: String, backgroundColor: String, privacy: StatusPrivacy) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val body = buildJsonObject {
                    put("type", "text")
                    put("text", text)
                    put("background_color", backgroundColor)
                    put("privacy", privacyToStr(privacy))
                }
                val response = apiClient.post("/v1/status", body)
                response.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false, successMessage = "Status created"
                        )
                        loadFeed()
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false, error = it.message
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun createMediaStatus(mediaId: String, privacy: StatusPrivacy) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val body = buildJsonObject {
                    put("type", "media")
                    put("media_id", mediaId)
                    put("privacy", privacyToStr(privacy))
                }
                val response = apiClient.post("/v1/status", body)
                response.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false, successMessage = "Status created"
                        )
                        loadFeed()
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false, error = it.message
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun viewStatus(statusId: String) {
        viewModelScope.launch {
            try {
                apiClient.post("/v1/status/$statusId/view")
            } catch (e: Exception) { Log.w("Status", "Load failed: ${e.message}") }
        }
    }

    fun getViewers(statusId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = apiClient.get("/v1/status/$statusId/views")
                response.fold(
                    onSuccess = { json ->
                        val viewers = json["viewers"]?.jsonArray?.map { item ->
                            val obj = item.jsonObject
                            StatusViewer(
                                userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                                username = obj["username"]?.jsonPrimitive?.content ?: "",
                                viewedAt = obj["viewed_at"]?.jsonPrimitive?.content ?: ""
                            )
                        } ?: emptyList()
                        _uiState.value = _uiState.value.copy(viewers = viewers, isLoading = false)
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

    fun deleteStatus(statusId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = apiClient.del("/v1/status/$statusId")
                response.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false, successMessage = "Status deleted"
                        )
                        loadFeed()
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false, error = it.message
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }

    private fun privacyToStr(privacy: StatusPrivacy): String = when (privacy) {
        StatusPrivacy.AllContacts -> "ALL_CONTACTS"
        StatusPrivacy.Selected -> "SELECTED"
        StatusPrivacy.CloseFriends -> "CLOSE_FRIENDS"
    }
}
