package org.enchant.calls

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.enchant.core.calls.CallLogEntry
import org.enchant.core.calls.CallLogFilter
import org.enchant.core.calls.CallType
import org.enchant.core.calls.CallDirection
import org.enchant.core.calls.CallStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CallLogViewModel — Full Coverage")
class CallLogViewModelTest {

    private lateinit var viewModel: CallLogViewModel

    @BeforeEach
    fun setUp() {
        viewModel = CallLogViewModel()
    }

    @Nested @DisplayName("Load Call Logs")
    inner class LoadCallLogsTest {
        @Test @DisplayName("loadCallLogs loads call logs")
        fun `load call logs`() = runTest {
            viewModel.loadCallLogs()
        }
    }

    @Nested @DisplayName("Set Filter")
    inner class SetFilterTest {
        @Test @DisplayName("setFilter changes the call log filter")
        fun `set filter`() = runTest {
            viewModel.setFilter(CallLogFilter.MISSED)
            assertEquals(CallLogFilter.MISSED, viewModel.uiState.value.filter)
        }

        @Test @DisplayName("setFilter to ALL shows all calls")
        fun `set filter all`() = runTest {
            viewModel.setFilter(CallLogFilter.ALL)
            assertEquals(CallLogFilter.ALL, viewModel.uiState.value.filter)
        }
    }

    @Nested @DisplayName("Selection")
    inner class SelectionTest {
        @Test @DisplayName("startSelection enables selection mode")
        fun `start selection`() = runTest {
            viewModel.startSelection()
            assertTrue(viewModel.uiState.value.isSelectionMode)
        }

        @Test @DisplayName("endSelection disables selection mode")
        fun `end selection`() = runTest {
            viewModel.startSelection()
            viewModel.endSelection()
            assertFalse(viewModel.uiState.value.isSelectionMode)
        }

        @Test @DisplayName("toggleSelected adds or removes ID from selection")
        fun `toggle selected`() = runTest {
            viewModel.startSelection()
            viewModel.toggleSelected("call-1")
            assertTrue(viewModel.uiState.value.selectedIds.contains("call-1"))
            viewModel.toggleSelected("call-1")
            assertFalse(viewModel.uiState.value.selectedIds.contains("call-1"))
        }

        @Test @DisplayName("selectAll selects all visible calls")
        fun `select all`() = runTest {
            viewModel.startSelection()
            viewModel.selectAll()
        }
    }

    @Nested @DisplayName("Deletion")
    inner class DeletionTest {
        @Test @DisplayName("stageDeletion returns selected IDs")
        fun `stage deletion`() = runTest {
            viewModel.startSelection()
            viewModel.toggleSelected("call-1")
            viewModel.toggleSelected("call-2")
            val staged = viewModel.stageDeletion()
            assertEquals(2, staged.size)
        }

        @Test @DisplayName("confirmDeletion deletes staged calls")
        fun `confirm deletion`() = runTest {
            viewModel.startSelection()
            viewModel.toggleSelected("call-1")
            val staged = viewModel.stageDeletion()
            viewModel.confirmDeletion(staged)
            assertFalse(viewModel.uiState.value.isSelectionMode)
        }
    }

    @Nested @DisplayName("UI State")
    inner class UiStateTest {
        @Test @DisplayName("uiState has default values")
        fun `ui state defaults`() = runTest {
            val state = viewModel.uiState.value
            assertNotNull(state)
            assertTrue(state.entries.isEmpty())
            assertEquals(CallLogFilter.ALL, state.filter)
            assertFalse(state.isLoading)
            assertFalse(state.isSelectionMode)
            assertTrue(state.selectedIds.isEmpty())
        }
    }
}
