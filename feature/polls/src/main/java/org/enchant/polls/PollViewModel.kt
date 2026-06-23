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

private const val SECONDS_PER_HOUR = 3600
private const val MIN_CLOSE_SECONDS = 60
private const val MAX_CLOSE_SECONDS = 604800

data class PollOption(
    val id: String,
    val text: String
)

data class Voter(
    val oderId: String,
    val displayName: String = "",
    val username: String = ""
)

data class PollData(
    val pollId: String,
    val conversationId: String = "",
    val question: String,
    val options: List<PollOption> = emptyList(),
    val results: Map<String, Int> = emptyMap(),
    val yourVote: List<String> = emptyList(),
    val totalVotes: Int = 0,
    val isClosed: Boolean = false,
    val allowMultiple: Boolean = false,
    val anonymous: Boolean = false,
    val closeInSeconds: Int? = null,
    val creatorId: String = ""
)

data class PollUiState(
    val currentPoll: PollData? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val voters: Map<String, List<Voter>> = emptyMap(),
    val isLoadingVoters: Boolean = false
)

class PollViewModel(
    private val apiClient: ApiClient = ApiClient.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PollUiState())
    val uiState: StateFlow<PollUiState> = _uiState.asStateFlow()

    fun createPoll(
        conversationId: String,
        question: String,
        options: List<String>,
        allowMultiple: Boolean,
        anonymous: Boolean,
        closeInSeconds: Int?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            val body = buildJsonObject {
                put("conversation_id", conversationId)
                put("question", question)
                put("options", JsonArray(options.map { buildJsonObject { put("text", it) } }))
                put("allow_multiple", allowMultiple)
                put("anonymous", anonymous)
                if (closeInSeconds != null) put("close_in_seconds", closeInSeconds)
            }
            val result = withContext(Dispatchers.IO) {
                apiClient.post("/v1/polls", body)
            }
            result.fold(
                onSuccess = { json ->
                    val serverOptions = json["options"]?.jsonArray?.map { optJson ->
                        PollOption(
                            id = optJson.jsonObject["id"]?.jsonPrimitive?.content ?: "",
                            text = optJson.jsonObject["text"]?.jsonPrimitive?.content ?: ""
                        )
                    } ?: emptyList()
                    val poll = PollData(
                        pollId = json["poll_id"]?.jsonPrimitive?.content ?: "",
                        conversationId = json["conversation_id"]?.jsonPrimitive?.content ?: conversationId,
                        question = json["question"]?.jsonPrimitive?.content ?: question,
                        options = serverOptions,
                        results = serverOptions.associate { it.id to 0 },
                        yourVote = emptyList(),
                        totalVotes = 0,
                        isClosed = false,
                        allowMultiple = allowMultiple,
                        anonymous = anonymous,
                        closeInSeconds = closeInSeconds
                    )
                    _uiState.value = _uiState.value.copy(
                        currentPoll = poll, isSubmitting = false,
                        successMessage = "Poll created"
                    )
                },
                onFailure = { _uiState.value = _uiState.value.copy(
                    isSubmitting = false, error = "Failed to create poll")
                }
            )
        }
    }

    fun vote(pollId: String, selectedOptionIds: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            val body = buildJsonObject {
                put("option_ids", JsonArray(selectedOptionIds.map { JsonPrimitive(it) }))
            }
            val result = withContext(Dispatchers.IO) {
                apiClient.post("/v1/polls/$pollId/vote", body)
            }
            result.fold(
                onSuccess = { loadPoll(pollId) },
                onFailure = { _uiState.value = _uiState.value.copy(
                    isSubmitting = false, error = "Failed to vote")
                }
            )
        }
    }

    fun loadPoll(pollId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            val result = withContext(Dispatchers.IO) {
                apiClient.get("/v1/polls/$pollId")
            }
            result.fold(
                onSuccess = { json ->
                    val serverOptions = json["options"]?.jsonArray?.map { optJson ->
                        PollOption(
                            id = optJson.jsonObject["id"]?.jsonPrimitive?.content ?: "",
                            text = optJson.jsonObject["text"]?.jsonPrimitive?.content ?: ""
                        )
                    } ?: emptyList()
                    val poll = PollData(
                        pollId = json["poll_id"]?.jsonPrimitive?.content ?: pollId,
                        conversationId = json["conversation_id"]?.jsonPrimitive?.content ?: "",
                        question = json["question"]?.jsonPrimitive?.content ?: "",
                        options = serverOptions,
                        results = (json["results"]?.jsonObject)?.let { obj ->
                            obj.keys.associateWith { key ->
                                obj[key]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                            }
                        } ?: emptyMap(),
                        yourVote = json["your_vote"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        totalVotes = json["total_votes"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        isClosed = json["is_closed"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                        allowMultiple = json["allow_multiple"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                        anonymous = json["anonymous"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                        closeInSeconds = json["closes_in_seconds"]?.jsonPrimitive?.content?.toIntOrNull(),
                        creatorId = json["creator_id"]?.jsonPrimitive?.content ?: ""
                    )
                    _uiState.value = _uiState.value.copy(currentPoll = poll, isSubmitting = false)
                },
                onFailure = { _uiState.value = _uiState.value.copy(
                    isSubmitting = false, error = "Failed to load poll")
                }
            )
        }
    }

    fun closePoll(pollId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            val result = withContext(Dispatchers.IO) {
                apiClient.put("/v1/polls/$pollId/close")
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isSubmitting = false,
                        successMessage = "Poll closed")
                    loadPoll(pollId)
                },
                onFailure = { _uiState.value = _uiState.value.copy(
                    isSubmitting = false, error = "Failed to close poll")
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }

    fun loadVoters(pollId: String, optionId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingVoters = true, error = null)
            val result = withContext(Dispatchers.IO) {
                apiClient.get("/v1/polls/$pollId/voters/$optionId")
            }
            result.fold(
                onSuccess = { json ->
                    val votersList = json["voters"]?.jsonArray?.map { voterJson ->
                        Voter(
                            oderId = voterJson.jsonObject["user_id"]?.jsonPrimitive?.content ?: "",
                            displayName = voterJson.jsonObject["display_name"]?.jsonPrimitive?.content ?: "",
                            username = voterJson.jsonObject["username"]?.jsonPrimitive?.content ?: ""
                        )
                    } ?: emptyList()
                    val updatedVoters = _uiState.value.voters.toMutableMap()
                    updatedVoters[optionId] = votersList
                    _uiState.value = _uiState.value.copy(
                        voters = updatedVoters,
                        isLoadingVoters = false
                    )
                },
                onFailure = {
                    val updatedVoters = _uiState.value.voters.toMutableMap()
                    updatedVoters[optionId] = emptyList()
                    _uiState.value = _uiState.value.copy(
                        voters = updatedVoters,
                        isLoadingVoters = false
                    )
                }
            )
        }
    }

    suspend fun getVoters(pollId: String, optionId: String): Result<List<Voter>> {
        return withContext(Dispatchers.IO) {
            apiClient.get("/v1/polls/$pollId/voters/$optionId").map { json ->
                json["voters"]?.jsonArray?.map { voterJson ->
                    Voter(
                        oderId = voterJson.jsonObject["user_id"]?.jsonPrimitive?.content ?: "",
                        displayName = voterJson.jsonObject["display_name"]?.jsonPrimitive?.content ?: "",
                        username = voterJson.jsonObject["username"]?.jsonPrimitive?.content ?: ""
                    )
                } ?: emptyList()
            }
        }
    }
}
