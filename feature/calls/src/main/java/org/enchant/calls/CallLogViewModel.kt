package org.enchant.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.enchant.core.calls.CallDirection
import org.enchant.core.calls.CallLogEntry
import org.enchant.core.calls.CallLogFilter
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.CallStatus
import org.enchant.core.calls.CallType
import org.enchant.core.calls.StagedDeletion

data class CallLogUiState(
    val entries: List<CallLogEntry> = emptyList(),
    val filteredEntries: List<CallLogEntry> = emptyList(),
    val filter: CallLogFilter = CallLogFilter.ALL,
    val searchQuery: String = "",
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val stagedDeletion: StagedDeletion? = null,
    val isLoading: Boolean = false
)

class CallLogViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CallLogUiState())
    val uiState: StateFlow<CallLogUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun loadCallLogs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            CallManager.getCallLogs().collect { logs ->
                val entries = logs.map { entity ->
                    CallLogEntry(
                        callId = entity.callId,
                        remoteUserId = entity.remoteUserId,
                        type = when (entity.type) {
                            "audio" -> CallType.AUDIO
                            "video" -> CallType.VIDEO
                            "group_audio" -> CallType.GROUP_AUDIO
                            "group_video" -> CallType.GROUP_VIDEO
                            else -> CallType.AUDIO
                        },
                        direction = if (entity.direction == "incoming") CallDirection.INCOMING else CallDirection.OUTGOING,
                        status = when (entity.status) {
                            "missed" -> CallStatus.MISSED
                            "answered" -> CallStatus.ANSWERED
                            "cancelled" -> CallStatus.CANCELLED
                            "outgoing" -> CallStatus.OUTGOING
                            else -> CallStatus.MISSED
                        },
                        durationSeconds = entity.durationSeconds,
                        timestamp = entity.endedAt
                    )
                }
                _uiState.value = _uiState.value.copy(entries = entries, isLoading = false)
                applyFilter(_uiState.value.filter)
            }
        }
    }

    fun setFilter(filter: CallLogFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
        applyFilter(filter)
    }

    fun search(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            applyFilter(_uiState.value.filter)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            val q = query.lowercase()
            val all = _uiState.value.entries
            val filtered = all.filter { it.remoteUserId.lowercase().contains(q) }
            _uiState.value = _uiState.value.copy(filteredEntries = filtered)
        }
    }

    fun startSelection() {
        _uiState.value = _uiState.value.copy(isSelectionMode = true, selectedIds = emptySet())
    }

    fun endSelection() {
        _uiState.value = _uiState.value.copy(isSelectionMode = false, selectedIds = emptySet())
    }

    fun toggleSelected(callId: String) {
        val current = _uiState.value.selectedIds
        _uiState.value = _uiState.value.copy(
            selectedIds = if (callId in current) current - callId else current + callId
        )
    }

    fun selectAll() {
        val allIds = _uiState.value.filteredEntries.map { it.callId }.toSet()
        _uiState.value = _uiState.value.copy(selectedIds = allIds)
    }

    fun stageDeletion(): StagedDeletion {
        val ids = _uiState.value.selectedIds.ifEmpty {
            _uiState.value.filteredEntries.firstOrNull()?.let { setOf(it.callId) } ?: emptySet()
        }
        val staged = StagedDeletion(count = ids.size, callIds = ids.toList())
        _uiState.value = _uiState.value.copy(stagedDeletion = staged)
        return staged
    }

    fun confirmDeletion(staged: StagedDeletion) {
        viewModelScope.launch {
            val pool = org.enchant.core.base.DI.databasePool
            pool.write { db ->
                staged.callIds.forEach { id ->
                    db.execSQL("DELETE FROM call_logs WHERE call_id = ?", arrayOf(id))
                }
            }
            _uiState.value = _uiState.value.copy(stagedDeletion = null, isSelectionMode = false)
            loadCallLogs()
        }
    }

    private fun applyFilter(filter: CallLogFilter) {
        val all = _uiState.value.entries
        val filtered = when (filter) {
            CallLogFilter.ALL -> all
            CallLogFilter.MISSED -> all.filter { it.status == CallStatus.MISSED }
            CallLogFilter.OUTGOING -> all.filter { it.direction == CallDirection.OUTGOING }
            CallLogFilter.INCOMING -> all.filter { it.direction == CallDirection.INCOMING }
        }
        _uiState.value = _uiState.value.copy(filteredEntries = filtered)
    }
}
