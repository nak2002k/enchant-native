package org.enchant.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.ui.theme.AppThemeManager
import org.enchant.core.network.ApiClient

data class DeviceInfo(
    val deviceId: String,
    val name: String,
    val lastSeen: String? = null,
    val isCurrent: Boolean = false
)

data class StorageInfo(
    val totalBytes: Long = 0,
    val messagesBytes: Long = 0,
    val mediaBytes: Long = 0,
    val cacheBytes: Long = 0
)

data class SettingsUiState(
    val theme: String = "system",
    val fontSize: Float = 1f,
    val notificationEnabled: Boolean = true,
    val messageNotifications: Boolean = true,
    val showPreview: Boolean = true,
    val lastSeenVisibility: String = "contacts",
    val onlineVisibility: Boolean = true,
    val avatarVisibility: String = "contacts",
    val aboutVisibility: String = "contacts",
    val readReceipts: Boolean = true,
    val blockedUsers: List<String> = emptyList(),
    val devices: List<DeviceInfo> = emptyList(),
    val storageInfo: StorageInfo? = null,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val displayName: String = "",
    val username: String? = null,
    val about: String? = null
)

class SettingsViewModel(
    private val apiClient: ApiClient
) : ViewModel() {
    constructor() : this(
        ApiClient().also { it.init() }
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        AppThemeManager.loadTheme()
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                apiClient.get("/v1/settings")
            }
            result.fold(
                onSuccess = { json ->
                    _uiState.value = _uiState.value.copy(
                        theme = json["theme"]?.jsonPrimitive?.content ?: "system",
                        fontSize = json["font_size"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1f,
                        notificationEnabled = json["notifications_enabled"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                        messageNotifications = json["message_notifications"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                        showPreview = json["show_preview"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                        lastSeenVisibility = json["last_seen_visibility"]?.jsonPrimitive?.content ?: "contacts",
                        onlineVisibility = json["online_visibility"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                        avatarVisibility = json["avatar_visibility"]?.jsonPrimitive?.content ?: "contacts",
                        aboutVisibility = json["about_visibility"]?.jsonPrimitive?.content ?: "contacts",
                        readReceipts = json["read_receipts"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                        blockedUsers = json["blocked_users"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    )
                },
                onFailure = {}
            )
        }
    }

    fun updateTheme(theme: String) {
        AppThemeManager.setTheme(theme)
        _uiState.value = _uiState.value.copy(theme = theme)
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                apiClient.put("/v1/settings/theme", buildJsonObject { put("theme", theme) })
            }
        }
    }

    fun updateFontSize(size: Float) {
        _uiState.value = _uiState.value.copy(fontSize = size)
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                apiClient.put("/v1/settings/font-size", buildJsonObject { put("font_size", size) })
            }
        }
    }

    fun updateNotificationPrefs(enabled: Boolean, messageNotif: Boolean, preview: Boolean) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                apiClient.put("/v1/settings/notifications", buildJsonObject {
                    put("notifications_enabled", enabled)
                    put("message_notifications", messageNotif)
                    put("show_preview", preview)
                })
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        notificationEnabled = enabled,
                        messageNotifications = messageNotif,
                        showPreview = preview
                    )
                },
                onFailure = {}
            )
        }
    }

    fun updatePrivacy(
        lastSeen: String, online: Boolean, avatar: String,
        about: String, readReceipts: Boolean
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                apiClient.put("/v1/settings/privacy", buildJsonObject {
                    put("last_seen_visibility", lastSeen)
                    put("online_visibility", online)
                    put("avatar_visibility", avatar)
                    put("about_visibility", about)
                    put("read_receipts", readReceipts)
                })
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        lastSeenVisibility = lastSeen, onlineVisibility = online,
                        avatarVisibility = avatar, aboutVisibility = about,
                        readReceipts = readReceipts
                    )
                },
                onFailure = {}
            )
        }
    }

    fun muteConversation(conversationId: String, until: Long?) {
        viewModelScope.launch {
            val body = buildJsonObject {
                put("mute_until_ms", until ?: -1L)
            }
            withContext(Dispatchers.Default) {
                apiClient.put("/v1/conversations/$conversationId/mute", body)
            }
        }
    }

    fun loadDevices() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                apiClient.get("/v1/devices")
            }
            result.fold(
                onSuccess = { json ->
                    val devices = json["devices"]?.jsonArray?.map { item ->
                        val obj = item.jsonObject
                        DeviceInfo(
                            deviceId = obj["device_id"]?.jsonPrimitive?.content ?: "",
                            name = obj["name"]?.jsonPrimitive?.content ?: "",
                            lastSeen = obj["last_seen"]?.jsonPrimitive?.content,
                            isCurrent = obj["is_current"]?.jsonPrimitive?.content?.toBoolean() ?: false
                        )
                    } ?: emptyList()
                    _uiState.value = _uiState.value.copy(devices = devices)
                },
                onFailure = {}
            )
        }
    }

    fun revokeDevice(deviceId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null)
            val result = withContext(Dispatchers.Default) {
                apiClient.del("/v1/devices/$deviceId")
            }
            result.fold(
                onSuccess = {
                    loadDevices()
                    _uiState.value = _uiState.value.copy(isProcessing = false,
                        successMessage = "Device revoked")
                },
                onFailure = { _uiState.value = _uiState.value.copy(
                    isProcessing = false, error = it.message)
                }
            )
        }
    }

    fun getStorageUsage() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                apiClient.get("/v1/settings/storage")
            }
            result.fold(
                onSuccess = { json ->
                    val info = StorageInfo(
                        totalBytes = json["total_bytes"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        messagesBytes = json["messages_bytes"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        mediaBytes = json["media_bytes"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        cacheBytes = json["cache_bytes"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0
                    )
                    _uiState.value = _uiState.value.copy(storageInfo = info)
                },
                onFailure = {}
            )
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null)
            val result = withContext(Dispatchers.Default) {
                apiClient.del("/v1/settings/cache")
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isProcessing = false,
                        successMessage = "Cache cleared")
                },
                onFailure = { _uiState.value = _uiState.value.copy(
                    isProcessing = false, error = it.message)
                }
            )
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null)
            val result = withContext(Dispatchers.Default) {
                apiClient.del("/v1/account")
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isProcessing = false,
                        successMessage = "Account deleted")
                },
                onFailure = { _uiState.value = _uiState.value.copy(
                    isProcessing = false, error = it.message)
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
