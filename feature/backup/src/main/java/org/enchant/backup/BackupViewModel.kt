package org.enchant.backup

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
import org.enchant.core.network.ApiClient
import org.enchant.core.database.DatabasePool

data class ExportInfo(
    val exportId: String,
    val status: String,
    val downloadToken: String? = null
)

data class BackupInfo(
    val backupId: String,
    val sizeBytes: Long = 0,
    val sectionCount: Int = 0,
    val createdAt: String? = null,
    val sections: List<String> = emptyList()
)

data class BackupUiState(
    val latestBackup: BackupInfo? = null,
    val backups: List<BackupInfo> = emptyList(),
    val uploadProgress: Float = 0f,
    val downloadProgress: Float = 0f,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val exportInfo: ExportInfo? = null,
    val isExporting: Boolean = false
)

class BackupViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun initiateBackup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null)
            val result = withContext(Dispatchers.Default) {
                ApiClient.getInstance().post("/v1/backup", buildJsonObject {
                    put("action", "initiate")
                })
            }
            result.fold(
                onSuccess = { json ->
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        successMessage = "Backup initiated: ${json["backup_id"]?.jsonPrimitive?.content}"
                    )
                },
                onFailure = { _uiState.value = _uiState.value.copy(
                    isProcessing = false, error = it.message)
                }
            )
        }
    }

    fun uploadChunk(backupId: String, chunkIndex: Int, totalChunks: Int, data: ByteArray) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null)
            val result = withContext(Dispatchers.Default) {
                ApiClient.getInstance().postRaw("/v1/backup/$backupId/chunks/$chunkIndex", data)
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isProcessing = false,
                        uploadProgress = (chunkIndex + 1).toFloat() / totalChunks.toFloat())
                },
                onFailure = { _uiState.value = _uiState.value.copy(
                    isProcessing = false, error = it.message)
                }
            )
        }
    }

    fun finalizeBackup(backupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null)
            val result = withContext(Dispatchers.Default) {
                ApiClient.getInstance().put("/v1/backup/$backupId/finalize")
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isProcessing = false,
                        successMessage = "Backup finalized")
                    getLatestBackup()
                },
                onFailure = { _uiState.value = _uiState.value.copy(
                    isProcessing = false, error = it.message)
                }
            )
        }
    }

    fun getLatestBackup() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                ApiClient.getInstance().get("/v1/backup/latest")
            }
            result.fold(
                onSuccess = { json ->
                    val info = BackupInfo(
                        backupId = json["backup_id"]?.jsonPrimitive?.content ?: "",
                        sizeBytes = json["size_bytes"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        sectionCount = json["section_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        createdAt = json["created_at"]?.jsonPrimitive?.content,
                        sections = json["sections"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    )
                    _uiState.value = _uiState.value.copy(latestBackup = info)
                },
                onFailure = {}
            )
        }
    }

    fun downloadBackup(backupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null)
            val result = withContext(Dispatchers.Default) {
                ApiClient.getInstance().getBinary("/v1/backup/$backupId/download")
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isProcessing = false,
                        successMessage = "Backup downloaded")
                },
                onFailure = { _uiState.value = _uiState.value.copy(
                    isProcessing = false, error = it.message)
                }
            )
        }
    }

    fun deleteBackup(backupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null)
            val result = withContext(Dispatchers.Default) {
                ApiClient.getInstance().del("/v1/backup/$backupId")
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isProcessing = false,
                        successMessage = "Backup deleted")
                },
                onFailure = { _uiState.value = _uiState.value.copy(
                    isProcessing = false, error = it.message)
                }
            )
        }
    }

    fun restoreBackup(backupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null)
            val result = withContext(Dispatchers.Default) {
                ApiClient.getInstance().post("/v1/backup/$backupId/restore")
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isProcessing = false,
                        successMessage = "Backup restored")
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

    fun requestExport(includeMedia: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, error = null)
            val result = withContext(Dispatchers.Default) {
                ApiClient.getInstance().post("/v1/export/request", buildJsonObject {
                    put("include_media", includeMedia)
                })
            }
            result.fold(
                onSuccess = { json ->
                    val exportInfo = ExportInfo(
                        exportId = json["export_id"]?.jsonPrimitive?.content ?: "",
                        status = "PENDING"
                    )
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        exportInfo = exportInfo,
                        successMessage = "Export started: ${exportInfo.exportId}"
                    )
                    pollExportStatus(exportInfo.exportId)
                },
                onFailure = { _uiState.value = _uiState.value.copy(
                    isExporting = false, error = it.message)
                }
            )
        }
    }

    private fun pollExportStatus(exportId: String) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            val result = withContext(Dispatchers.Default) {
                ApiClient.getInstance().get("/v1/export/$exportId")
            }
            result.fold(
                onSuccess = { json ->
                    val status = json["status"]?.jsonPrimitive?.content ?: "PENDING"
                    val downloadToken = json["download_token"]?.jsonPrimitive?.content
                    val currentInfo = _uiState.value.exportInfo
                    val updated = currentInfo?.copy(status = status, downloadToken = downloadToken)
                    _uiState.value = _uiState.value.copy(exportInfo = updated)
                    if (status == "PENDING" || status == "PROCESSING") {
                        pollExportStatus(exportId)
                    } else if (status == "COMPLETED" && downloadToken != null) {
                        _uiState.value = _uiState.value.copy(
                            successMessage = "Export ready for download"
                        )
                    } else if (status == "FAILED") {
                        _uiState.value = _uiState.value.copy(
                            error = "Export failed"
                        )
                    }
                },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message) }
            )
        }
    }

    fun downloadExport(exportId: String, downloadToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null)
            val result = withContext(Dispatchers.Default) {
                ApiClient.getInstance().getBinary("/v1/export/$exportId/download?token=$downloadToken")
            }
            result.fold(
                onSuccess = { bytes ->
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        successMessage = "Export downloaded: ${bytes.size} bytes"
                    )
                },
                onFailure = { _uiState.value = _uiState.value.copy(
                    isProcessing = false, error = it.message)
                }
            )
        }
    }
}
