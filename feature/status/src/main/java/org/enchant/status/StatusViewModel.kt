package org.enchant.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.core.base.logging.Log
import org.enchant.core.network.ApiClient

sealed class StatusPrivacy {
    data object AllContacts : StatusPrivacy()
    data class Selected(val userIds: List<String> = emptyList()) : StatusPrivacy()
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
    val isViewed: Boolean = false,
    val isMine: Boolean = false
)

data class StatusUiState(
    val feed: List<StatusFeedEntry> = emptyList(),
    val myStatus: StatusFeedEntry? = null,
    val singleStatus: StatusFeedEntry? = null,
    val viewers: List<StatusViewer> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

private const val TAG = "StatusViewModel"

class StatusViewModel(
    private val apiClient: ApiClient
) : ViewModel() {
    constructor() : this(ApiClient.getInstance())

    private val _uiState = MutableStateFlow(StatusUiState())
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    private val activeJobs = mutableListOf<Job>()

    private fun launchTracked(block: suspend () -> Unit): Job {
        val job = viewModelScope.launch { block() }
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
        return job
    }

    override fun onCleared() {
        super.onCleared()
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
    }

    fun loadFeed() {
        launchTracked {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiClient.get("/v1/status/feed")
                response.fold(
                    onSuccess = { json ->
                        val entries = json["feed"]?.jsonArray?.mapToFeedEntries() ?: emptyList()
                        val my = entries.find { it.isMine }
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
                Log.e(TAG, "loadFeed failed", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun createTextStatus(text: String, backgroundColor: String, privacy: StatusPrivacy, selectedContacts: List<String>? = null) {
        if (text.length > 700) {
            _uiState.value = _uiState.value.copy(error = "Status text exceeds 700 characters")
            return
        }
        launchTracked {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val body = buildJsonObject {
                    put("type", "text")
                    put("text", text)
                    put("background_color", backgroundColor)
                    put("privacy", privacyToStr(privacy))
                    if (privacy is StatusPrivacy.Selected && selectedContacts != null) {
                        put("selected_contacts", kotlinx.serialization.json.JsonArray(
                            selectedContacts.map { kotlinx.serialization.json.JsonPrimitive(it) }
                        ))
                    }
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
                Log.e(TAG, "createTextStatus failed", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun createMediaStatus(mediaId: String, privacy: StatusPrivacy, selectedContacts: List<String>? = null) {
        launchTracked {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val body = buildJsonObject {
                    put("type", "media")
                    put("media_id", mediaId)
                    put("privacy", privacyToStr(privacy))
                    if (privacy is StatusPrivacy.Selected && selectedContacts != null) {
                        put("selected_contacts", kotlinx.serialization.json.JsonArray(
                            selectedContacts.map { kotlinx.serialization.json.JsonPrimitive(it) }
                        ))
                    }
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
                Log.e(TAG, "createMediaStatus failed", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun viewStatus(statusId: String) {
        launchTracked {
            try {
                val response = apiClient.post("/v1/status/$statusId/view")
                response.onFailure {
                    Log.w(TAG, "viewStatus failed for $statusId: ${it.message}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "viewStatus failed for $statusId: ${e.message}")
            }
        }
    }

    fun loadSingleStatus(statusId: String) {
        launchTracked {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiClient.get("/v1/status/$statusId")
                response.fold(
                    onSuccess = { json ->
                        val entry = (json as? JsonObject)?.toStatusFeedEntry()
                        _uiState.value = _uiState.value.copy(
                            singleStatus = entry,
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
                Log.e(TAG, "loadSingleStatus failed", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun clearSingleStatus() {
        _uiState.value = _uiState.value.copy(singleStatus = null)
    }

    fun getViewers(statusId: String) {
        launchTracked {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = apiClient.get("/v1/status/$statusId/views")
                response.fold(
                    onSuccess = { json ->
                        val viewers = json["views"]?.jsonArray?.mapToViewers() ?: emptyList()
                        _uiState.value = _uiState.value.copy(viewers = viewers, isLoading = false)
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "getViewers failed", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun deleteStatus(statusId: String) {
        launchTracked {
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
                Log.e(TAG, "deleteStatus failed", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }

    private fun JsonArray.mapToFeedEntries(): List<StatusFeedEntry> = map { item ->
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
            isViewed = obj["is_viewed"]?.jsonPrimitive?.content?.toBoolean() ?: false,
            isMine = obj["is_mine"]?.jsonPrimitive?.content?.toBoolean() ?: false
        )
    }

    private fun JsonArray.mapToViewers(): List<StatusViewer> = map { item ->
        val obj = item.jsonObject
        StatusViewer(
            userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
            username = obj["username"]?.jsonPrimitive?.content ?: "",
            viewedAt = obj["viewed_at"]?.jsonPrimitive?.content ?: ""
        )
    }

    private fun JsonObject.toStatusFeedEntry(): StatusFeedEntry {
        return StatusFeedEntry(
            statusId = this["status_id"]?.jsonPrimitive?.content ?: "",
            userId = this["author_user_id"]?.jsonPrimitive?.content ?: "",
            username = "",
            type = this["status_type"]?.jsonPrimitive?.content ?: "text",
            text = this["text_content"]?.jsonPrimitive?.content,
            mediaId = this["media_id"]?.jsonPrimitive?.content,
            backgroundColor = this["text_background"]?.jsonPrimitive?.content,
            createdAt = this["created_ts"]?.jsonPrimitive?.content ?: "",
            isViewed = this["already_viewed"]?.jsonPrimitive?.content?.toBoolean() ?: false,
            isMine = false
        )
    }

    private fun privacyToStr(privacy: StatusPrivacy): String = when (privacy) {
        StatusPrivacy.AllContacts -> "ALL_CONTACTS"
        is StatusPrivacy.Selected -> "SELECTED"
        StatusPrivacy.CloseFriends -> "CLOSE_FRIENDS"
    }
}
