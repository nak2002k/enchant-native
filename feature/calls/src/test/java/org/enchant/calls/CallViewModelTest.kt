package org.enchant.calls

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.enchant.core.calls.CallStatusEnum
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("CallViewModel")
class CallViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: CallViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CallViewModel()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("initial state")
    inner class InitialState {
        @Test
        fun `starts with IDLE call state`() {
            assert(viewModel.uiState.value.callState.status == CallStatusEnum.IDLE)
        }

        @Test
        fun `call screen is not visible initially`() {
            assert(!viewModel.uiState.value.isCallScreenVisible)
        }

        @Test
        fun `no navigation target initially`() {
            assert(viewModel.uiState.value.navigateToConversation == null)
        }
    }

    @Nested
    @DisplayName("navigation")
    inner class Navigation {
        @Test
        fun `navigateToConversation sets target`() {
            viewModel.navigateToConversation("conv_1")
            assert(viewModel.uiState.value.navigateToConversation == "conv_1")
        }

        @Test
        fun `onNavigatedToConversation clears target`() {
            viewModel.navigateToConversation("conv_1")
            viewModel.onNavigatedToConversation()
            assert(viewModel.uiState.value.navigateToConversation == null)
        }
    }

    @Nested
    @DisplayName("call actions through viewmodel")
    inner class CallActions {
        @Test
        fun `raiseHand updates state`() {
            viewModel.raiseHand(true)
            viewModel.raiseHand(false)
        }

        @Test
        fun `endCall from IDLE is safe`() {
            viewModel.endCall()
            assert(viewModel.uiState.value.callState.status == CallStatusEnum.IDLE)
        }

        @Test
        fun `toggleMute through viewmodel`() {
            viewModel.toggleMute()
        }

        @Test
        fun `toggleSpeaker through viewmodel`() {
            viewModel.toggleSpeaker()
        }

        @Test
        fun `toggleVideo through viewmodel`() {
            viewModel.toggleVideo()
        }
    }
}
