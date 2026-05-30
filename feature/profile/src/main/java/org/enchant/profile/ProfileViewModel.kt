package org.enchant.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.core.network.ApiClient

data class ProfileData(
    val userId: String,
    val displayName: String?,
    val username: String?,
    val about: String?,
    val avatarMediaId: String?
)

data class BlockedUser(
    val userId: String,
    val username: String?
)

data class ProfileUiState(
    val profile: ProfileData? = null,
    val searchResults: List<ProfileData> = emptyList(),
    val blockedUsers: List<BlockedUser> = emptyList(),
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

class ProfileViewModel(
    private val apiClient: ApiClient
) : ViewModel() {
    constructor() : this(org.enchant.core.network.ApiClient.getInstance())

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    private val profileRefreshMutex = Mutex()

    fun loadProfile(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiClient.get("/v1/profile/$userId")
                response.fold(
                    onSuccess = { json ->
                        val profile = ProfileData(
                            userId = json["user_id"]?.jsonPrimitive?.content ?: userId,
                            displayName = json["display_name"]?.jsonPrimitive?.content,
                            username = json["username"]?.jsonPrimitive?.content,
                            about = json["about"]?.jsonPrimitive?.content,
                            avatarMediaId = json["avatar_media_id"]?.jsonPrimitive?.content
                        )
                        _uiState.value = _uiState.value.copy(
                            profile = profile, isLoading = false
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

    fun loadMyProfile() {
        loadProfile("me")
    }

    fun updateProfile(displayName: String?, about: String?) {
        val trimmedName = displayName?.trim()
        val clampedAbout = about?.take(MAX_ABOUT_LENGTH)

        if (trimmedName != null && trimmedName.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Display name cannot be empty")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val body = buildJsonObject {
                    if (trimmedName != null) put("display_name", trimmedName)
                    if (clampedAbout != null) put("about", clampedAbout)
                }
                val response = apiClient.put("/v1/profile", body)
                response.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false, successMessage = "Profile updated"
                        )
                        refreshProfileSafely()
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

    fun updateAvatar(mediaId: String) {
        if (mediaId.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Invalid avatar media ID")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val body = buildJsonObject {
                    put("avatar_media_id", mediaId)
                }
                val response = apiClient.post("/v1/profile/avatar", body)
                response.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false, successMessage = "Avatar updated"
                        )
                        refreshProfileSafely()
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

    fun searchByUsername(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
            return
        }
        viewModelScope.launch {
            try {
                val response = apiClient.get("/v1/profile/search", mapOf("username" to query))
                response.fold(
                    onSuccess = { json ->
                        val results = json["results"]?.jsonArray?.map { item ->
                            val obj = item.jsonObject
                            ProfileData(
                                userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                                displayName = obj["display_name"]?.jsonPrimitive?.content,
                                username = obj["username"]?.jsonPrimitive?.content,
                                about = obj["about"]?.jsonPrimitive?.content,
                                avatarMediaId = obj["avatar_media_id"]?.jsonPrimitive?.content
                            )
                        } ?: emptyList()
                        _uiState.value = _uiState.value.copy(searchResults = results)
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(
                            searchResults = emptyList(),
                            error = it.message
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    searchResults = emptyList(),
                    error = e.message
                )
            }
        }
    }

    fun getBlockedUsers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = apiClient.get("/v1/blocks")
                response.fold(
                    onSuccess = { json ->
                        val users = json["blocks"]?.jsonArray?.map { item ->
                            val obj = item.jsonObject
                            BlockedUser(
                                userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                                username = obj["username"]?.jsonPrimitive?.content
                            )
                        } ?: emptyList()
                        _uiState.value = _uiState.value.copy(
                            blockedUsers = users, isLoading = false
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

    fun blockUser(userId: String) {
        viewModelScope.launch {
            try {
                apiClient.post("/v1/blocks/$userId")
                _uiState.value = _uiState.value.copy(successMessage = "User blocked")
                getBlockedUsers()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun unblockUser(userId: String) {
        viewModelScope.launch {
            try {
                apiClient.del("/v1/blocks/$userId")
                _uiState.value = _uiState.value.copy(successMessage = "User unblocked")
                getBlockedUsers()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun setEditing(editing: Boolean) {
        _uiState.value = _uiState.value.copy(isEditing = editing)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null, isEditing = false)
    }

    private suspend fun refreshProfileSafely() {
        profileRefreshMutex.withLock {
            loadMyProfile()
        }
    }

    companion object {
        private const val MAX_ABOUT_LENGTH = 500
    }
}
