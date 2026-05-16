package org.enchant.calls

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.enchant.core.calls.CallDirection
import org.enchant.core.calls.CallLogEntry
import org.enchant.core.calls.CallLogFilter
import org.enchant.core.calls.CallStatus
import org.enchant.core.calls.CallType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("CallLogViewModel")
class CallLogViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: CallLogViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CallLogViewModel()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("initial state")
    inner class InitialState {
        @Test
        fun `starts with empty entries`() {
            assert(viewModel.uiState.value.entries.isEmpty())
        }

        @Test
        fun `starts with ALL filter`() {
            assert(viewModel.uiState.value.filter == CallLogFilter.ALL)
        }

        @Test
        fun `starts not in selection mode`() {
            assert(!viewModel.uiState.value.isSelectionMode)
        }

        @Test
        fun `starts with empty search query`() {
            assert(viewModel.uiState.value.searchQuery.isEmpty())
        }
    }

    @Nested
    @DisplayName("filter")
    inner class Filter {
        @Test
        fun `setFilter changes active filter`() {
            viewModel.setFilter(CallLogFilter.MISSED)
            assert(viewModel.uiState.value.filter == CallLogFilter.MISSED)
        }

        @Test
        fun `setFilter cycles through all filters`() {
            viewModel.setFilter(CallLogFilter.ALL)
            assert(viewModel.uiState.value.filter == CallLogFilter.ALL)

            viewModel.setFilter(CallLogFilter.MISSED)
            assert(viewModel.uiState.value.filter == CallLogFilter.MISSED)

            viewModel.setFilter(CallLogFilter.OUTGOING)
            assert(viewModel.uiState.value.filter == CallLogFilter.OUTGOING)

            viewModel.setFilter(CallLogFilter.INCOMING)
            assert(viewModel.uiState.value.filter == CallLogFilter.INCOMING)
        }
    }

    @Nested
    @DisplayName("selection mode")
    inner class Selection {
        @Test
        fun `startSelection enters selection mode`() {
            viewModel.startSelection()
            assert(viewModel.uiState.value.isSelectionMode)
            assert(viewModel.uiState.value.selectedIds.isEmpty())
        }

        @Test
        fun `endSelection exits selection mode and clears selection`() {
            viewModel.startSelection()
            viewModel.endSelection()
            assert(!viewModel.uiState.value.isSelectionMode)
            assert(viewModel.uiState.value.selectedIds.isEmpty())
        }

        @Test
        fun `toggleSelected adds and removes ids`() {
            viewModel.startSelection()
            viewModel.toggleSelected("call_1")
            assert(viewModel.uiState.value.selectedIds.contains("call_1"))

            viewModel.toggleSelected("call_1")
            assert(!viewModel.uiState.value.selectedIds.contains("call_1"))
        }

        @Test
        fun `toggleSelected handles multiple ids`() {
            viewModel.startSelection()
            viewModel.toggleSelected("call_1")
            viewModel.toggleSelected("call_2")
            assert(viewModel.uiState.value.selectedIds.size == 2)
        }

        @Test
        fun `selectAll selects all visible entries`() {
            viewModel.startSelection()
            viewModel.selectAll()
        }
    }

    @Nested
    @DisplayName("search")
    inner class Search {
        @Test
        fun `search with empty query resets filter`() {
            viewModel.setFilter(CallLogFilter.MISSED)
            viewModel.search("")
            assert(viewModel.uiState.value.searchQuery.isEmpty())
        }

        @Test
        fun `search updates query in state`() {
            viewModel.search("alice")
            assert(viewModel.uiState.value.searchQuery == "alice")
        }
    }

    @Nested
    @DisplayName("log entry mapping")
    inner class LogEntryMapping {
        @Test
        fun `CallLogEntry created with correct values`() {
            val entry = CallLogEntry(
                callId = "call_1",
                remoteUserId = "user_1",
                type = CallType.AUDIO,
                direction = CallDirection.INCOMING,
                status = CallStatus.MISSED,
                durationSeconds = 0,
                timestamp = 1000L
            )
            assert(entry.callId == "call_1")
            assert(entry.type == CallType.AUDIO)
            assert(entry.direction == CallDirection.INCOMING)
            assert(entry.status == CallStatus.MISSED)
        }

        @Test
        fun `CallLogEntry with video type`() {
            val entry = CallLogEntry(
                callId = "call_2",
                remoteUserId = "user_2",
                type = CallType.VIDEO,
                direction = CallDirection.OUTGOING,
                status = CallStatus.ANSWERED,
                durationSeconds = 120,
                timestamp = 2000L
            )
            assert(entry.type == CallType.VIDEO)
            assert(entry.direction == CallDirection.OUTGOING)
            assert(entry.status == CallStatus.ANSWERED)
            assert(entry.durationSeconds == 120)
        }
    }
}
