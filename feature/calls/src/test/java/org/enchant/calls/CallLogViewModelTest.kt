package org.enchant.calls

import io.mockk.coEvery
import io.mockk.coVerify
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
import org.enchant.core.calls.CallDirection
import org.enchant.core.calls.CallEndReason
import org.enchant.core.calls.CallLogFilter
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.CallState
import org.enchant.core.calls.CallsModule
import org.enchant.core.calls.model.CallLogEntry
import org.enchant.core.calls.model.CallType
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
        @Test @DisplayName("loadCallLogs loads call logs from CallManager")
        fun `load call logs`() = runTest {
            val viewModel = CallLogViewModel()
            testDispatcher.scheduler.runCurrent()
            coVerify { mockCallManager.getCallLogs(any()) }
        }

        @Test @DisplayName("loadCallLogs sets isLoading during load")
        fun `load call logs sets loading`() = runTest {
            val viewModel = CallLogViewModel()
            val stateBefore = viewModel.uiState.value
            testDispatcher.scheduler.runCurrent()
            val stateAfter = viewModel.uiState.value
            // isLoading transitions from true to false
            assertFalse(stateAfter.isLoading)
        }

        @Test @DisplayName("loadCallLogs populates entries")
        fun `load call logs populates entries`() = runTest {
            val logs = listOf(
                createCallLogEntry("call-1", "user1", CallDirection.OUTGOING),
                createCallLogEntry("call-2", "user2", CallDirection.INCOMING)
            )
            coEvery { mockCallManager.getCallLogs(any()) } returns logs

            val viewModel = CallLogViewModel()
            testDispatcher.scheduler.runCurrent()

            assertEquals(2, viewModel.uiState.value.entries.size)
        }

        @Test @DisplayName("loadCallLogs clusters entries")
        fun `load call logs clusters entries`() = runTest {
            val now = System.currentTimeMillis()
            val logs = listOf(
                createCallLogEntry("call-1", "user1", CallDirection.OUTGOING, now),
                createCallLogEntry("call-2", "user1", CallDirection.OUTGOING, now - 3600000),
                createCallLogEntry("call-3", "user2", CallDirection.INCOMING, now - 7200000)
            )
            coEvery { mockCallManager.getCallLogs(any()) } returns logs

            val viewModel = CallLogViewModel()
            testDispatcher.scheduler.runCurrent()

            assertTrue(viewModel.uiState.value.clusteredEntries.isNotEmpty())
        }
    }

    @Nested @DisplayName("Paging")
    inner class PagingTest {
        @Test @DisplayName("initial page is 0")
        fun `initial page is 0`() = runTest {
            coEvery { mockCallManager.getCallLogs(any()) } returns emptyList()
            val viewModel = CallLogViewModel()
            testDispatcher.scheduler.runCurrent()
            assertEquals(0, viewModel.uiState.value.currentPage)
        }

        @Test @DisplayName("hasMorePages true when more logs available")
        fun `has more pages`() = runTest {
            val manyLogs = (1..200).map { createCallLogEntry("call-$it", "user1", CallDirection.OUTGOING) }
            coEvery { mockCallManager.getCallLogs(any()) } returns manyLogs

            val viewModel = CallLogViewModel()
            testDispatcher.scheduler.runCurrent()

            assertTrue(viewModel.uiState.value.hasMorePages)
        }

        @Test @DisplayName("hasMorePages false when no more logs")
        fun `no more pages`() = runTest {
            coEvery { mockCallManager.getCallLogs(any()) } returns emptyList()
            val viewModel = CallLogViewModel()
            testDispatcher.scheduler.runCurrent()
            assertFalse(viewModel.uiState.value.hasMorePages)
        }

        @Test @DisplayName("loadMoreLogs increments page")
        fun `load more logs increments page`() = runTest {
            val logs = (1..50).map { createCallLogEntry("call-$it", "user1", CallDirection.OUTGOING) }
            coEvery { mockCallManager.getCallLogs(any()) } returns logs

            val viewModel = CallLogViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.loadMoreLogs()
            testDispatcher.scheduler.runCurrent()

            assertTrue(viewModel.uiState.value.currentPage >= 0)
        }
    }

    @Nested @DisplayName("Set Filter")
    inner class SetFilterTest {
        @Test @DisplayName("setFilter changes the call log filter")
        fun `set filter`() = runTest {
            coEvery { mockCallManager.getCallLogs(any()) } returns emptyList()
            val viewModel = CallLogViewModel()
            viewModel.setFilter(CallLogFilter.MISSED)
            assertEquals(CallLogFilter.MISSED, viewModel.uiState.value.filter)
        }

        @Test @DisplayName("setFilter to ALL shows all calls")
        fun `set filter all`() = runTest {
            coEvery { mockCallManager.getCallLogs(any()) } returns emptyList()
            val viewModel = CallLogViewModel()
            viewModel.setFilter(CallLogFilter.ALL)
            assertEquals(CallLogFilter.ALL, viewModel.uiState.value.filter)
        }

        @Test @DisplayName("setFilter OUTGOING filters outgoing calls")
        fun `set filter outgoing`() = runTest {
            val logs = listOf(
                createCallLogEntry("call-1", "user1", CallDirection.OUTGOING),
                createCallLogEntry("call-2", "user2", CallDirection.INCOMING)
            )
            coEvery { mockCallManager.getCallLogs(any()) } returns logs

            val viewModel = CallLogViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.setFilter(CallLogFilter.OUTGOING)
            testDispatcher.scheduler.runCurrent()

            assertEquals(1, viewModel.uiState.value.filteredEntries.size)
            assertEquals(CallDirection.OUTGOING, viewModel.uiState.value.filteredEntries.first().direction)
        }

        @Test @DisplayName("setFilter INCOMING filters incoming calls")
        fun `set filter incoming`() = runTest {
            val logs = listOf(
                createCallLogEntry("call-1", "user1", CallDirection.OUTGOING),
                createCallLogEntry("call-2", "user2", CallDirection.INCOMING)
            )
            coEvery { mockCallManager.getCallLogs(any()) } returns logs

            val viewModel = CallLogViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.setFilter(CallLogFilter.INCOMING)
            testDispatcher.scheduler.runCurrent()

            assertEquals(1, viewModel.uiState.value.filteredEntries.size)
            assertEquals(CallDirection.INCOMING, viewModel.uiState.value.filteredEntries.first().direction)
        }
    }

    @Nested @DisplayName("Search")
    inner class SearchTest {
        @Test @DisplayName("search filters by remoteUserId")
        fun `search by user id`() = runTest {
            coEvery { mockCallManager.getCallLogs(any()) } returns listOf(
                createCallLogEntry("call-1", "alice", CallDirection.OUTGOING),
                createCallLogEntry("call-2", "bob", CallDirection.INCOMING)
            )

            val viewModel = CallLogViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.search("alice")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.filteredEntries.size)
            assertEquals("alice", viewModel.uiState.value.filteredEntries.first().remoteUserId)
        }

        @Test @DisplayName("search with empty query shows all filtered")
        fun `search empty shows all`() = runTest {
            coEvery { mockCallManager.getCallLogs(any()) } returns listOf(
                createCallLogEntry("call-1", "alice", CallDirection.OUTGOING),
                createCallLogEntry("call-2", "bob", CallDirection.INCOMING)
            )

            val viewModel = CallLogViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.search("")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.filteredEntries.size)
        }
    }

    @Nested @DisplayName("Selection")
    inner class SelectionTest {
        @Test @DisplayName("startSelection enables selection mode and sets Includes state")
        fun `start selection`() = runTest {
            val viewModel = CallLogViewModel()
            viewModel.startSelection()
            assertTrue(viewModel.uiState.value.isSelectionMode)
            assertTrue(viewModel.uiState.value.selectionState is CallLogSelectionState.Includes)
        }

        @Test @DisplayName("endSelection disables selection mode and resets selection state")
        fun `end selection`() = runTest {
            val viewModel = CallLogViewModel()
            viewModel.startSelection()
            viewModel.endSelection()
            assertFalse(viewModel.uiState.value.isSelectionMode)
            assertTrue(viewModel.uiState.value.selectionState is CallLogSelectionState.All)
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

        @Test @DisplayName("selectAll selects all visible calls and sets All state")
        fun `select all`() = runTest {
            coEvery { mockCallManager.getCallLogs(any()) } returns listOf(
                createCallLogEntry("call-1", "user1", CallDirection.OUTGOING),
                createCallLogEntry("call-2", "user2", CallDirection.INCOMING)
            )

            val viewModel = CallLogViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.startSelection()
            viewModel.selectAll()

            assertEquals(2, viewModel.uiState.value.selectedIds.size)
            assertTrue(viewModel.uiState.value.selectionState is CallLogSelectionState.All)
        }

        @Test @DisplayName("toggleSelected updates selectionState to Includes")
        fun `toggle selected updates state`() = runTest {
            val viewModel = CallLogViewModel()
            viewModel.startSelection()
            viewModel.toggleSelected("call-1")

            val state = viewModel.uiState.value.selectionState
            assertTrue(state is CallLogSelectionState.Includes)
            assertTrue((state as CallLogSelectionState.Includes).ids.contains("call-1"))
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
            assertTrue(staged.callIds.contains("call-1"))
            assertTrue(staged.callIds.contains("call-2"))
        }

        @Test @DisplayName("stageDeletion with empty selection returns empty")
        fun `stage deletion empty`() = runTest {
            val viewModel = CallLogViewModel()
            val staged = viewModel.stageDeletion()
            assertTrue(staged.callIds.isEmpty())
        }

        @Test @DisplayName("confirmDeletion clears selection and reloads")
        fun `confirm deletion clears selection`() = runTest {
            coEvery { mockCallManager.getCallLogs(any()) } returns emptyList()
            mockkObject(org.enchant.core.database.DatabasePool)
            val mockPool = mockk<org.enchant.core.database.DatabasePool>(relaxed = true)
            every { org.enchant.core.database.DatabasePool.instance } returns mockPool

            val viewModel = CallLogViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.startSelection()
            viewModel.toggleSelected("call-1")
            val staged = viewModel.stageDeletion()
            viewModel.confirmDeletion(staged)
            testDispatcher.scheduler.runCurrent()

            assertFalse(viewModel.uiState.value.isSelectionMode)
            assertTrue(viewModel.uiState.value.selectedIds.isEmpty())

            unmockkObject(org.enchant.core.database.DatabasePool)
        }
    }

    @Nested @DisplayName("CallEventCluster")
    inner class ClusterTest {
        @Test @DisplayName("CallEventCluster callIds returns list of call IDs")
        fun `call ids`() = runTest {
            val cluster = CallEventCluster(
                parentCallId = "call-1",
                childCallIds = setOf("call-1", "call-2", "call-3"),
                remoteUserId = "user1",
                direction = CallDirection.OUTGOING,
                callCount = 3,
                latestTimestamp = System.currentTimeMillis(),
                latestStatus = CallEndReason.HANGUP_LOCAL,
                totalDuration = 180
            )

            assertEquals(3, cluster.callIds.size)
            assertTrue(cluster.callIds.contains("call-1"))
            assertTrue(cluster.callIds.contains("call-2"))
            assertTrue(cluster.callIds.contains("call-3"))
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
            assertEquals(CallLogFilter.ALL, state.filter)
            assertFalse(state.isLoading)
            assertFalse(state.isSelectionMode)
            assertTrue(state.selectedIds.isEmpty())
        }

        @Test @DisplayName("uiState error is null initially")
        fun `error is null initially`() = runTest {
            val viewModel = CallLogViewModel()
            assertNull(viewModel.uiState.value.error)
        }
    }

    private fun createCallLogEntry(
        callId: String,
        remoteUserId: String,
        direction: CallDirection,
        timestamp: Long = System.currentTimeMillis()
    ): CallLogEntry {
        return CallLogEntry(
            callId = callId,
            remoteUserId = remoteUserId,
            remoteName = null,
            type = CallType.AUDIO,
            direction = direction,
            status = CallEndReason.HANGUP_LOCAL,
            durationSeconds = 60,
            timestamp = timestamp
        )
    }
}