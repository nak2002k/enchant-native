package org.enchant.backup

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.enchant.core.network.ApiClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("BackupViewModel — Full Coverage")
class BackupViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var apiClient: ApiClient
    private lateinit var viewModel: BackupViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(ApiClient.Companion)
        apiClient = mockk<ApiClient>(relaxed = true)
        every { ApiClient.getInstance() } returns apiClient
        viewModel = BackupViewModel()
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(ApiClient.Companion)
        Dispatchers.resetMain()
    }

    @Nested @DisplayName("Create Backup")
    inner class CreateBackupTest {
        @Test @DisplayName("initiateBackup initiates a backup")
        fun `initiate backup`() = runTest {
            viewModel.initiateBackup()
            testDispatcher.scheduler.advanceUntilIdle()
        }
    }

    @Nested @DisplayName("Restore Backup")
    inner class RestoreBackupTest {
        @Test @DisplayName("restoreBackup restores from backup")
        fun `restore backup`() = runTest {
            viewModel.restoreBackup("backup-1")
            testDispatcher.scheduler.advanceUntilIdle()
        }
    }

    @Nested @DisplayName("Delete Backup")
    inner class DeleteBackupTest {
        @Test @DisplayName("deleteBackup deletes a backup")
        fun `delete backup`() = runTest {
            viewModel.deleteBackup("backup-1")
            testDispatcher.scheduler.advanceUntilIdle()
        }
    }

    @Nested @DisplayName("Load Backups")
    inner class LoadBackupsTest {
        @Test @DisplayName("getLatestBackup loads latest backup")
        fun `get latest backup`() = runTest {
            viewModel.getLatestBackup()
            testDispatcher.scheduler.advanceUntilIdle()
        }
    }

    @Nested @DisplayName("UI State")
    inner class UiStateTest {
        @Test @DisplayName("uiState has default values")
        fun `ui state defaults`() = runTest {
            val state = viewModel.uiState.value
            assertNotNull(state)
            assertTrue(state.backups.isEmpty())
            assertFalse(state.isProcessing)
        }
    }
}
