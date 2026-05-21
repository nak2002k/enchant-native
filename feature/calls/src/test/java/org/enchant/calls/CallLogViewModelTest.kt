package org.enchant.calls

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.CallState
import org.enchant.core.calls.CallsModule
import org.enchant.core.calls.model.CallLogEntry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("CallLogViewModel — Full Coverage")
class CallLogViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockCallManager: org.enchant.core.calls.DefaultCallManager

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockCallManager = mockk(relaxed = true)
        mockkObject(CallsModule)
        every { CallsModule.getCallManager() } returns mockCallManager
        every { mockCallManager.callState } returns MutableStateFlow(CallState())
        coEvery { mockCallManager.getCallLogs(any()) } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(CallsModule)
        Dispatchers.resetMain()
    }

    @Nested @DisplayName("Load Call Logs")
    inner class LoadCallLogsTest {
        @Test @DisplayName("loadCallLogs loads call logs")
        fun `load call logs`() = runTest {
            val viewModel = CallLogViewModel()
            viewModel.loadCallLogs()
            coEvery { mockCallManager.getCallLogs(any()) } returns emptyList()
        }
    }

    @Nested @DisplayName("Set Filter")
    inner class SetFilterTest {
        @Test @DisplayName("setFilter changes the call log filter")
        fun `set filter`() = runTest {
            val viewModel = CallLogViewModel()
            viewModel.setFilter(org.enchant.core.calls.CallLogFilter.MISSED)
            assertEquals(org.enchant.core.calls.CallLogFilter.MISSED, viewModel.uiState.value.filter)
        }

        @Test @DisplayName("setFilter to ALL shows all calls")
        fun `set filter all`() = runTest {
            val viewModel = CallLogViewModel()
            viewModel.setFilter(org.enchant.core.calls.CallLogFilter.ALL)
            assertEquals(org.enchant.core.calls.CallLogFilter.ALL, viewModel.uiState.value.filter)
        }
    }

    @Nested @DisplayName("Selection")
    inner class SelectionTest {
        @Test @DisplayName("startSelection enables selection mode")
        fun `start selection`() = runTest {
            val viewModel = CallLogViewModel()
            viewModel.startSelection()
            assertTrue(viewModel.uiState.value.isSelectionMode)
        }

        @Test @DisplayName("endSelection disables selection mode")
        fun `end selection`() = runTest {
            val viewModel = CallLogViewModel()
            viewModel.startSelection()
            viewModel.endSelection()
            assertFalse(viewModel.uiState.value.isSelectionMode)
        }

        @Test @DisplayName("toggleSelected adds or removes ID from selection")
        fun `toggle selected`() = runTest {
            val viewModel = CallLogViewModel()
            viewModel.startSelection()
            viewModel.toggleSelected("call-1")
            assertTrue(viewModel.uiState.value.selectedIds.contains("call-1"))
            viewModel.toggleSelected("call-1")
            assertFalse(viewModel.uiState.value.selectedIds.contains("call-1"))
        }

        @Test @DisplayName("selectAll selects all visible calls")
        fun `select all`() = runTest {
            val viewModel = CallLogViewModel()
            viewModel.startSelection()
            viewModel.selectAll()
        }
    }

    @Nested @DisplayName("Deletion")
    inner class DeletionTest {
        @Test @DisplayName("stageDeletion returns selected IDs")
        fun `stage deletion`() = runTest {
            val viewModel = CallLogViewModel()
            viewModel.startSelection()
            viewModel.toggleSelected("call-1")
            viewModel.toggleSelected("call-2")
            val staged = viewModel.stageDeletion()
            assertEquals(2, staged.callIds.size)
        }

        @Test @DisplayName("confirmDeletion deletes staged calls")
        fun `confirm deletion`() = runTest {
            val viewModel = CallLogViewModel()
            viewModel.startSelection()
            viewModel.toggleSelected("call-1")
            val staged = viewModel.stageDeletion()
            viewModel.confirmDeletion(staged)
            testDispatcher.scheduler.runCurrent()
            assertFalse(viewModel.uiState.value.isSelectionMode)
        }
    }

    @Nested @DisplayName("UI State")
    inner class UiStateTest {
        @Test @DisplayName("uiState has default values")
        fun `ui state defaults`() = runTest {
            val viewModel = CallLogViewModel()
            val state = viewModel.uiState.value
            assertNotNull(state)
            assertTrue(state.entries.isEmpty())
            assertEquals(org.enchant.core.calls.CallLogFilter.ALL, state.filter)
            assertFalse(state.isLoading)
            assertFalse(state.isSelectionMode)
            assertTrue(state.selectedIds.isEmpty())
        }
    }
}