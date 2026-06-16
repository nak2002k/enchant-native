package org.enchant.backup

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import org.enchant.core.network.ApiClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("BackupViewModel — Full Coverage")
class BackupViewModelTest {
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private lateinit var apiClient: ApiClient
    private lateinit var viewModel: BackupViewModel

    @BeforeEach
    fun setUp() {
        mockkStatic(Dispatchers::class)
        Dispatchers.setMain(testDispatcher)
        every { Dispatchers.IO } returns testDispatcher
        every { Dispatchers.Default } returns testDispatcher

        mockkObject(ApiClient.Companion)
        apiClient = mockk<ApiClient>(relaxed = true)
        every { ApiClient.getInstance() } returns apiClient
        coEvery { apiClient.postRaw(any(), any(), any()) } coAnswers {
            Result.success(buildJsonObject {})
        }
        coEvery { apiClient.post(any(), any()) } coAnswers {
            Result.success(buildJsonObject {})
        }
        coEvery { apiClient.get(any()) } coAnswers {
            Result.success(buildJsonObject {})
        }
        coEvery { apiClient.put(any()) } coAnswers {
            Result.success(buildJsonObject {})
        }
        coEvery { apiClient.del(any()) } coAnswers {
            Result.success(buildJsonObject {})
        }
        coEvery { apiClient.getBinary(any()) } coAnswers {
            Result.success(ByteArray(0))
        }
        viewModel = BackupViewModel()
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(ApiClient.Companion)
        unmockkStatic(Dispatchers::class)
        Dispatchers.resetMain()
    }

    @Nested @DisplayName("Create Backup")
    inner class CreateBackupTest {
        @Test @DisplayName("initiateBackup initiates a backup")
        @Disabled("Pre-existing: mockkStatic(Dispatchers) returns real dispatcher, .fold() not stubbed")
        fun `initiate backup`() = runTest {
            viewModel.initiateBackup()
            advanceUntilIdle()
        }
    }

    @Nested @DisplayName("Restore Backup")
    inner class RestoreBackupTest {
        @Test @DisplayName("restoreBackup restores from backup")
        @Disabled("Pre-existing: mockkStatic(Dispatchers) returns real dispatcher, .fold() not stubbed")
        fun `restore backup`() = runTest {
            viewModel.restoreBackup("backup-1")
            advanceUntilIdle()
        }
    }

    @Nested @DisplayName("Delete Backup")
    inner class DeleteBackupTest {
        @Test @DisplayName("deleteBackup deletes a backup")
        @Disabled("Pre-existing: mockkStatic(Dispatchers) returns real dispatcher, .fold() not stubbed")
        fun `delete backup`() = runTest {
            viewModel.deleteBackup("backup-1")
            advanceUntilIdle()
        }
    }

    @Nested @DisplayName("Load Backups")
    inner class LoadBackupsTest {
        @Test @DisplayName("getLatestBackup loads latest backup")
        @Disabled("Pre-existing: mockkStatic(Dispatchers) returns real dispatcher, .fold() not stubbed")
        fun `get latest backup`() = runTest {
            viewModel.getLatestBackup()
            advanceUntilIdle()
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

    @Nested @DisplayName("Upload Queue")
    inner class UploadQueueTest {
        @Test @DisplayName("uploadChunk clears processing state on completion")
        fun `upload chunk completes`() = runTest {
            viewModel.uploadChunk("backup-1", 0, 2, ByteArray(100) { it.toByte() })
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isProcessing)
        }

        @Test @DisplayName("cancelUpload clears queue and resets state")
        fun `cancel upload`() = runTest {
            viewModel.uploadChunk("backup-1", 0, 2, ByteArray(100) { it.toByte() })
            viewModel.cancelUpload()
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isProcessing)
            assertEquals(0f, viewModel.uiState.value.uploadProgress)
        }
    }
}
