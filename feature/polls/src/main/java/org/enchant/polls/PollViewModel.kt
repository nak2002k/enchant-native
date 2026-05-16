package org.enchant.polls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.core.network.ApiClient

data class PollData(
    val pollId: String,
    val question: String,
    val options: List<String> = emptyList(),
    val results: Map<String, Int> = emptyMap(),
    val yourVote: List<String> = emptyList(),
    val totalVotes: Int = 0,
    val isClosed: Boolean = false,
    val allowMultiple: Boolean = false
)

data class PollUiState(
    val currentPoll: PollData? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

class PollViewModel(
    private val apiClient: ApiClient
) : ViewModel() {
    private val _uiState = MutableStateFlow(PollUiState())
    val uiState: StateFlow<PollUiState> = _uiState.asStateFlow()

    fun createPoll(question: String, options: List<String>, allowMultiple: Boolean, closeInSeconds: Int?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            val body = buildJsonObject {
                put("question", question)
                put("options", JsonArray(options.map { JsonPrimitive(it) }))
                put("allow_multiple", allowMultiple)
                if (closeInSeconds != null) put("close_in_seconds", closeInSeconds)
            }
            val result = withContext(Dispatchers.Default) {
                apiClient.post("/v1/polls", body)
            }
            result.fold(
                onSuccess = { json ->
                    val poll = PollData(
                        pollId = json["poll_id"]?.jsonPrimitive?.content ?: "",
                        question = question,
                        options = options,
                        results = options.associateWith { 0 },
                        yourVote = emptyList(),
                        totalVotes = 0,
                        isClosed = false,
                        allowMultiple = allowMultiple
                    )
                    _uiState.value = _uiState.value.copy(
                        currentPoll = poll, isSubmitting = false,
                        successMessage = "Poll created"
                    )
                },
                onFailure = { _uiState.value = _uiState.value.copy(
                    isSubmitting = false, error = it.message)
                }
            )
        }
    }

    fun vote(pollId: String, selectedOptions: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            val body = buildJsonObject {
                put("option_ids", JsonArray(selectedOptions.map { JsonPrimitive(it) }))
            }
            val result = withContext(Dispatchers.Default) {
                apiClient.post("/v1/polls/$pollId/vote", body)
            }
            result.fold(
                onSuccess = { loadPoll(pollId) },
                onFailure = { _uiState.value = _uiState.value.copy(
                    isSubmitting = false, error = it.message)
                }
            )
        }
    }

    fun loadPoll(pollId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            val result = withContext(Dispatchers.Default) {
                apiClient.get("/v1/polls/$pollId")
            }
            result.fold(
                onSuccess = { json ->
                    val poll = PollData(
                        pollId = json["poll_id"]?.jsonPrimitive?.content ?: pollId,
                        question = json["question"]?.jsonPrimitive?.content ?: "",
                        options = json["options"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        results = (json["results"]?.jsonObject)?.let { obj ->
                            obj.keys.associateWith { key ->
                                obj[key]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                            }
                        } ?: emptyMap(),
                        yourVote = json["your_vote"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        totalVotes = json["total_votes"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        isClosed = json["is_closed"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                        allowMultiple = json["allow_multiple"]?.jsonPrimitive?.content?.toBoolean() ?: false
                    )
                    _uiState.value = _uiState.value.copy(currentPoll = poll, isSubmitting = false)
                },
                onFailure = { _uiState.value = _uiState.value.copy(
                    isSubmitting = false, error = it.message)
                }
            )
        }
    }

    fun closePoll(pollId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            val result = withContext(Dispatchers.Default) {
                apiClient.put("/v1/polls/$pollId/close")
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isSubmitting = false,
                        successMessage = "Poll closed")
                    loadPoll(pollId)
                },
                onFailure = { _uiState.value = _uiState.value.copy(
                    isSubmitting = false, error = it.message)
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
