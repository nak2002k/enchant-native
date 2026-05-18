package org.enchant.stickers

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

data class StickerPack(
    val packId: String,
    val title: String,
    val cover: String? = null,
    val stickerCount: Int = 0,
    val author: String? = null,
    val stickers: List<String> = emptyList(),
    val isInstalled: Boolean = false
)

data class StickerUiState(
    val featured: List<StickerPack> = emptyList(),
    val searchResults: List<StickerPack> = emptyList(),
    val library: List<StickerPack> = emptyList(),
    val recent: List<Pair<String, String>> = emptyList(),
    val selectedPack: StickerPack? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class StickerViewModel(
    private val apiClient: ApiClient
) : ViewModel() {
    constructor() : this(org.enchant.core.network.ApiClient.getInstance())
    private val _uiState = MutableStateFlow(StickerUiState())
    val uiState: StateFlow<StickerUiState> = _uiState.asStateFlow()

    fun loadFeatured() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = withContext(Dispatchers.Default) {
                apiClient.get("/v1/stickers/packs/featured")
            }
            result.fold(
                onSuccess = { json ->
                    val packs = json["packs"]?.jsonArray?.map { item ->
                        val obj = item.jsonObject
                        StickerPack(
                            packId = obj["pack_id"]?.jsonPrimitive?.content ?: "",
                            title = obj["title"]?.jsonPrimitive?.content ?: "",
                            cover = obj["cover"]?.jsonPrimitive?.content,
                            stickerCount = obj["sticker_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            author = obj["author"]?.jsonPrimitive?.content,
                            isInstalled = obj["is_installed"]?.jsonPrimitive?.content?.toBoolean() ?: false
                        )
                    } ?: emptyList()
                    _uiState.value = _uiState.value.copy(featured = packs, isLoading = false)
                },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message, isLoading = false) }
            )
        }
    }

    fun searchPacks(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = withContext(Dispatchers.Default) {
                apiClient.get("/v1/stickers/packs/search", mapOf("q" to query))
            }
            result.fold(
                onSuccess = { json ->
                    val packs = json["packs"]?.jsonArray?.map { item ->
                        val obj = item.jsonObject
                        StickerPack(
                            packId = obj["pack_id"]?.jsonPrimitive?.content ?: "",
                            title = obj["title"]?.jsonPrimitive?.content ?: "",
                            cover = obj["cover"]?.jsonPrimitive?.content,
                            stickerCount = obj["sticker_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            author = obj["author"]?.jsonPrimitive?.content
                        )
                    } ?: emptyList()
                    _uiState.value = _uiState.value.copy(searchResults = packs, isLoading = false)
                },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message, isLoading = false) }
            )
        }
    }

    fun loadPackDetail(packId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = withContext(Dispatchers.Default) {
                apiClient.get("/v1/stickers/packs/$packId")
            }
            result.fold(
                onSuccess = { json ->
                    val pack = StickerPack(
                        packId = json["pack_id"]?.jsonPrimitive?.content ?: packId,
                        title = json["title"]?.jsonPrimitive?.content ?: "",
                        cover = json["cover"]?.jsonPrimitive?.content,
                        stickerCount = json["sticker_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        author = json["author"]?.jsonPrimitive?.content,
                        stickers = json["stickers"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        isInstalled = json["is_installed"]?.jsonPrimitive?.content?.toBoolean() ?: false
                    )
                    _uiState.value = _uiState.value.copy(selectedPack = pack, isLoading = false)
                },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message, isLoading = false) }
            )
        }
    }

    fun installPack(packId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = withContext(Dispatchers.Default) {
                apiClient.post("/v1/stickers/library/$packId")
            }
            result.fold(
                onSuccess = {
                    loadLibrary()
                    val current = _uiState.value.selectedPack
                    if (current?.packId == packId) {
                        _uiState.value = _uiState.value.copy(selectedPack = current.copy(isInstalled = true))
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false)
                },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message, isLoading = false) }
            )
        }
    }

    fun uninstallPack(packId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = withContext(Dispatchers.Default) {
                apiClient.del("/v1/stickers/library/$packId")
            }
            result.fold(
                onSuccess = {
                    loadLibrary()
                    val current = _uiState.value.selectedPack
                    if (current?.packId == packId) {
                        _uiState.value = _uiState.value.copy(selectedPack = current.copy(isInstalled = false))
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false)
                },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message, isLoading = false) }
            )
        }
    }

    fun loadLibrary() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                apiClient.get("/v1/stickers/library")
            }
            result.fold(
                onSuccess = { json ->
                    val packs = json["packs"]?.jsonArray?.map { item ->
                        val obj = item.jsonObject
                        StickerPack(
                            packId = obj["pack_id"]?.jsonPrimitive?.content ?: "",
                            title = obj["title"]?.jsonPrimitive?.content ?: "",
                            cover = obj["cover"]?.jsonPrimitive?.content,
                            stickerCount = obj["sticker_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            author = obj["author"]?.jsonPrimitive?.content,
                            isInstalled = true
                        )
                    } ?: emptyList()
                    _uiState.value = _uiState.value.copy(library = packs)
                },
                onFailure = { android.util.Log.w("StickerVM", "loadLibrary failed: ${it.message}") }
            )
        }
    }

    fun loadRecent() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                apiClient.get("/v1/stickers/recent")
            }
            result.fold(
                onSuccess = { json ->
                    val recent = json["recent"]?.jsonArray?.map { item ->
                        val obj = item.jsonObject
                        (obj["pack_id"]?.jsonPrimitive?.content ?: "") to
                            (obj["sticker_id"]?.jsonPrimitive?.content ?: "")
                    } ?: emptyList()
                    _uiState.value = _uiState.value.copy(recent = recent)
                },
                onFailure = { android.util.Log.w("StickerVM", "loadRecent failed: ${it.message}") }
            )
        }
    }

    fun recordStickerUse(packId: String, stickerId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                apiClient.post("/v1/stickers/recent/$stickerId", buildJsonObject {
                    put("pack_id", packId)
                })
            }
        }
    }

    fun sendSticker(packId: String, stickerId: String, onSend: ((packId: String, stickerId: String) -> Unit)? = null) {
        recordStickerUse(packId, stickerId)
        loadRecent()
        onSend?.invoke(packId, stickerId)
    }
}
