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
import org.enchant.core.store.EnchantStore
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
    val about: String? = null,
    val defaultDisappearingTimer: Int = 0,
    val autoDownloadWifi: Boolean = false,
    val autoDownloadCellular: Boolean = false
)

class SettingsViewModel(
    private val apiClient: ApiClient
) : ViewModel() {
    constructor() : this(ApiClient.getInstance())

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
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
                        blockedUsers = json["blocked_users"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        defaultDisappearingTimer = json["disappearing_timer_seconds"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        autoDownloadWifi = json["auto_download_wifi"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                        autoDownloadCellular = json["auto_download_cellular"]?.jsonPrimitive?.content?.toBoolean() ?: false
                    )
                },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message) }
            )
        }
    }

    fun updateTheme(theme: String) {
        try { EnchantStore.settings.theme = theme } catch (_: Exception) { }
        _uiState.value = _uiState.value.copy(theme = theme)
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                apiClient.put("/v1/settings/theme", buildJsonObject { put("theme", theme) })
            }
            result.onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun updateFontSize(size: Float) {
        _uiState.value = _uiState.value.copy(fontSize = size)
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                apiClient.put("/v1/settings/font-size", buildJsonObject { put("font_size", size) })
            }
            result.onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun updateNotificationPrefs(notificationEnabled: Boolean, messageNotifications: Boolean, showPreview: Boolean) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                apiClient.put("/v1/settings/notifications", buildJsonObject {
                    put("notifications_enabled", notificationEnabled)
                    put("message_notifications", messageNotifications)
                    put("show_preview", showPreview)
                })
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        notificationEnabled = notificationEnabled,
                        messageNotifications = messageNotifications,
                        showPreview = showPreview
                    )
                },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message) }
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
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message) }
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
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message) }
            )
        }
    }

    fun revokeDevice(deviceId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true, error = null,
                devices = _uiState.value.devices.filter { it.deviceId != deviceId }
            )
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
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message) }
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
        if (_uiState.value.isProcessing) return
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

    fun updateDisappearingTimer(seconds: Int) {
        _uiState.value = _uiState.value.copy(defaultDisappearingTimer = seconds)
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                apiClient.put("/v1/settings/chats", buildJsonObject { put("disappearing_timer_seconds", seconds) })
            }
            result.onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun updateAutoDownload(autoDownloadWifi: Boolean, autoDownloadCellular: Boolean) {
        _uiState.value = _uiState.value.copy(autoDownloadWifi = autoDownloadWifi, autoDownloadCellular = autoDownloadCellular)
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                apiClient.put("/v1/settings/chats", buildJsonObject {
                    put("auto_download_wifi", autoDownloadWifi)
                    put("auto_download_cellular", autoDownloadCellular)
                })
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
