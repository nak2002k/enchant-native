package org.enchant.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.enchant.core.calls.CallDirection
import org.enchant.core.calls.CallEndReason
import org.enchant.core.calls.CallLogEntry
import org.enchant.core.calls.CallLogFilter
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.StagedDeletion

data class CallLogUiState(
    val entries: List<CallLogEntry> = emptyList(),
    val filteredEntries: List<CallLogEntry> = emptyList(),
    val clusteredEntries: List<CallEventCluster> = emptyList(),
    val filter: CallLogFilter = CallLogFilter.ALL,
    val searchQuery: String = "",
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val selectionState: CallLogSelectionState = CallLogSelectionState.All,
    val stagedDeletion: StagedDeletion? = null,
    val isLoading: Boolean = false,
    val currentPage: Int = 0,
    val hasMorePages: Boolean = false,
    val error: String? = null
)

sealed class CallLogSelectionState {
    data class Includes(val ids: Set<String>) : CallLogSelectionState()
    data class Excludes(val ids: Set<String>) : CallLogSelectionState()
    object All : CallLogSelectionState()

    fun isExclusionary(): Boolean = this is Excludes || this is All
}

data class CallEventCluster(
    val parentCallId: String,
    val childCallIds: Set<String>,
    val remoteUserId: String,
    val direction: CallDirection,
    val callCount: Int,
    val latestTimestamp: Long,
    val latestStatus: CallEndReason,
    val totalDuration: Int
) {
    fun isWithinTimeout(otherTimestamp: Long): Boolean {
        val fourHours = 4 * 60 * 60 * 1000L
        return (latestTimestamp - otherTimestamp) < fourHours
    }

    val callIds: List<String>
        get() = childCallIds.toList()
}

class CallLogViewModel : ViewModel() {
    companion object {
        private const val PAGE_SIZE = 20
        private const val CLUSTER_TIMEOUT_MS = 4 * 60 * 60 * 1000L
    }

    private val _uiState = MutableStateFlow(CallLogUiState())
    val uiState: StateFlow<CallLogUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadCallLogs()
    }

    fun loadCallLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val allLogs = CallManager.getCallLogs(PAGE_SIZE * 10)
                val filteredLogs = applyFilter(allLogs, _uiState.value.filter)
                val clusteredLogs = clusterCallLogs(filteredLogs)
                _uiState.update {
                    it.copy(
                        entries = allLogs,
                        filteredEntries = filteredLogs,
                        clusteredEntries = clusteredLogs,
                        isLoading = false,
                        currentPage = 0,
                        hasMorePages = allLogs.size >= PAGE_SIZE * 10
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadMoreLogs() {
        if (!_uiState.value.hasMorePages || _uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val nextPage = _uiState.value.currentPage + 1
                val allLogs = CallManager.getCallLogs(PAGE_SIZE * (nextPage + 1))
                val filteredLogs = applyFilter(allLogs, _uiState.value.filter)
                val clusteredLogs = clusterCallLogs(filteredLogs)
                _uiState.update {
                    it.copy(
                        entries = allLogs,
                        filteredEntries = filteredLogs,
                        clusteredEntries = clusteredLogs,
                        isLoading = false,
                        currentPage = nextPage,
                        hasMorePages = allLogs.size >= PAGE_SIZE * (nextPage + 1)
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setFilter(filter: CallLogFilter) {
        _uiState.update { it.copy(filter = filter) }
        viewModelScope.launch {
            val filteredLogs = applyFilter(_uiState.value.entries, filter)
            val clusteredLogs = clusterCallLogs(filteredLogs)
            _uiState.update {
                it.copy(
                    filteredEntries = filteredLogs,
                    clusteredEntries = clusteredLogs,
                    selectedIds = emptySet(),
                    selectionState = CallLogSelectionState.All
                )
            }
        }
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            val filteredLogs = applyFilter(_uiState.value.entries, _uiState.value.filter)
            val clusteredLogs = clusterCallLogs(filteredLogs)
            _uiState.update {
                it.copy(filteredEntries = filteredLogs, clusteredEntries = clusteredLogs)
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            val q = query.lowercase()
            val all = _uiState.value.entries
            val filtered = all.filter {
                it.remoteUserId.lowercase().contains(q) ||
                (it.remoteName?.lowercase()?.contains(q) == true)
            }
            val clusteredLogs = clusterCallLogs(filtered)
            _uiState.update {
                it.copy(filteredEntries = filtered, clusteredEntries = clusteredLogs)
            }
        }
    }

    fun startSelection() {
        _uiState.update {
            it.copy(
                isSelectionMode = true,
                selectedIds = emptySet(),
                selectionState = CallLogSelectionState.Includes(emptySet())
            )
        }
    }

    fun endSelection() {
        _uiState.update {
            it.copy(
                isSelectionMode = false,
                selectedIds = emptySet(),
                selectionState = CallLogSelectionState.All
            )
        }
    }

    fun toggleSelected(callId: String) {
        val current = _uiState.value.selectedIds
        val newSelection = if (callId in current) {
            current - callId
        } else {
            current + callId
        }
        _uiState.update {
            it.copy(
                selectedIds = newSelection,
                selectionState = CallLogSelectionState.Includes(newSelection)
            )
        }
    }

    fun selectAll() {
        val allIds = _uiState.value.filteredEntries.map { it.callId }.toSet()
        _uiState.update {
            it.copy(
                selectedIds = allIds,
                selectionState = CallLogSelectionState.All
            )
        }
    }

    fun stageDeletion(): StagedDeletion {
        val selectedIds = _uiState.value.selectedIds.toList()
        if (selectedIds.isEmpty()) {
            return StagedDeletion(count = 0, callIds = emptyList())
        }
        val staged = StagedDeletion(count = selectedIds.size, callIds = selectedIds)
        _uiState.update { it.copy(stagedDeletion = staged) }
        return staged
    }

    fun confirmDeletion(staged: StagedDeletion) {
        if (staged.callIds.isEmpty()) {
            _uiState.update {
                it.copy(stagedDeletion = null, isSelectionMode = false, selectedIds = emptySet())
            }
            return
        }
        viewModelScope.launch {
            try {
                val pool = org.enchant.core.database.DatabasePool.instance
                pool?.writer?.let { db ->
                    if (_uiState.value.selectionState.isExclusionary()) {
                        val filter = _uiState.value.filter
                        when {
                            _uiState.value.selectionState is CallLogSelectionState.All -> {
                                db.execSQL("DELETE FROM call_logs WHERE filter = ?", arrayOf(filter.name))
                            }
                            _uiState.value.selectionState is CallLogSelectionState.Excludes -> {
                                val excludes = (_uiState.value.selectionState as CallLogSelectionState.Excludes).ids
                                db.execSQL("DELETE FROM call_logs WHERE call_id NOT IN (${excludes.joinToString(",")}) AND filter = ?", arrayOf(filter.name))
                            }
                        }
                    } else {
                        staged.callIds.forEach { id ->
                            db.execSQL("DELETE FROM call_logs WHERE call_id = ?", arrayOf(id))
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete: ${e.message}") }
            }
            _uiState.update {
                it.copy(
                    stagedDeletion = null,
                    isSelectionMode = false,
                    selectedIds = emptySet(),
                    selectionState = CallLogSelectionState.All
                )
            }
            loadCallLogs()
        }
    }

    private fun applyFilter(logs: List<CallLogEntry>, filter: CallLogFilter): List<CallLogEntry> {
        return when (filter) {
            CallLogFilter.ALL -> logs
            CallLogFilter.MISSED -> logs.filter { isMissedCall(it) }
            CallLogFilter.OUTGOING -> logs.filter { it.direction == CallDirection.OUTGOING }
            CallLogFilter.INCOMING -> logs.filter { it.direction == CallDirection.INCOMING }
        }
    }

    private fun isMissedCall(entry: CallLogEntry): Boolean {
        return entry.direction == CallDirection.INCOMING &&
               entry.status in listOf(CallEndReason.BUSY, CallEndReason.TIMEOUT)
    }

    private fun clusterCallLogs(entries: List<CallLogEntry>): List<CallEventCluster> {
        if (entries.isEmpty()) return emptyList()

        val sorted = entries.sortedByDescending { it.timestamp }
        val clusters = mutableListOf<CallEventCluster>()
        var currentCluster: MutableList<CallLogEntry>? = null

        for (entry in sorted) {
            if (currentCluster == null) {
                currentCluster = mutableListOf(entry)
            } else {
                val first = currentCluster.first()
                val samePeer = entry.remoteUserId == first.remoteUserId
                val sameDirection = entry.direction == first.direction
                val withinTimeout = (first.timestamp - entry.timestamp) < CLUSTER_TIMEOUT_MS

                if (samePeer && sameDirection && withinTimeout) {
                    currentCluster.add(entry)
                } else {
                    clusters.add(buildCluster(currentCluster))
                    currentCluster = mutableListOf(entry)
                }
            }
        }

        if (currentCluster != null && currentCluster.isNotEmpty()) {
            clusters.add(buildCluster(currentCluster))
        }

        return clusters
    }

    private fun buildCluster(entries: List<CallLogEntry>): CallEventCluster {
        val first = entries.first()
        return CallEventCluster(
            parentCallId = first.callId,
            childCallIds = entries.map { it.callId }.toSet(),
            remoteUserId = first.remoteUserId,
            direction = first.direction,
            callCount = entries.size,
            latestTimestamp = first.timestamp,
            latestStatus = first.status,
            totalDuration = entries.sumOf { it.durationSeconds }
        )
    }

    private fun buildDeletionQuery(state: CallLogSelectionState, filter: CallLogFilter): String {
        return when {
            state is CallLogSelectionState.All -> "DELETE FROM call_logs WHERE filter = ?"
            state is CallLogSelectionState.Excludes -> "DELETE FROM call_logs WHERE call_id NOT IN (?) AND filter = ?"
            state is CallLogSelectionState.Includes -> "DELETE FROM call_logs WHERE call_id IN (?)"
            else -> "DELETE FROM call_logs"
        }
    }
}