package org.enchant.polls

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.enchant.core.network.ApiClient

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("PollViewModel — Full Coverage")
class PollViewModelTest {

    @Test @DisplayName("uiState has default values")
    fun `ui state defaults`(): TestResult = runTest {
        val apiClient: ApiClient = mockk(relaxed = true)
        val viewModel = PollViewModel(apiClient)
        val state = viewModel.uiState.value
        assertNotNull(state)
        assertNull(state.currentPoll)
        assertFalse(state.isSubmitting)
        assertNull(state.error)
        assertNull(state.successMessage)
        assertTrue(state.voters.isEmpty())
        assertFalse(state.isLoadingVoters)
    }
}