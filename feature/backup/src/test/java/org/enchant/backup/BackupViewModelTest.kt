package org.enchant.backup

import org.junit.jupiter.api.Test

class BackupViewModelTest {

    @Test
    fun `BackupUiState has initial defaults`() {
        val state = BackupUiState()
        assert(state.latestBackup == null)
        assert(state.backups.isEmpty())
        assert(state.uploadProgress == 0f)
        assert(state.downloadProgress == 0f)
        assert(!state.isProcessing)
        assert(state.error == null)
        assert(state.successMessage == null)
    }

    @Test
    fun `BackupViewModel constructor does not crash`() {
        val viewModel = BackupViewModel()
        assert(viewModel.uiState.value == BackupUiState())
    }
}
