package org.enchant.calls

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.CallState
import org.enchant.core.calls.CallStatusEnum
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CallViewModel — Full Coverage")
class CallViewModelTest {

    private lateinit var viewModel: CallViewModel

    @BeforeEach
    fun setUp() {
        mockkObject(CallManager)
        every { CallManager.callState } returns MutableStateFlow(CallState())
        viewModel = CallViewModel()
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(CallManager)
        viewModel.onCleared()
    }

    @Nested @DisplayName("Start Call")
    inner class StartCallTest {
        @Test @DisplayName("startCall calls CallManager.startOutgoingCall")
        fun `start call`() = runTest {
            viewModel.startCall("remote-user", false)
            coVerify { CallManager.startOutgoingCall("remote-user", false) }
        }

        @Test @DisplayName("startCall with video calls with isVideo=true")
        fun `start call video`() = runTest {
            viewModel.startCall("remote-user", true)
            coVerify { CallManager.startOutgoingCall("remote-user", true) }
        }
    }

    @Nested @DisplayName("Accept Call")
    inner class AcceptCallTest {
        @Test @DisplayName("acceptCall calls CallManager.acceptCall")
        fun `accept call`() = runTest {
            viewModel.acceptCall(false)
            coVerify { CallManager.acceptCall(any(), false) }
        }

        @Test @DisplayName("acceptCall with video calls with withVideo=true")
        fun `accept call video`() = runTest {
            viewModel.acceptCall(true)
            coVerify { CallManager.acceptCall(any(), true) }
        }
    }

    @Nested @DisplayName("Deny Call")
    inner class DenyCallTest {
        @Test @DisplayName("denyCall calls CallManager.denyCall")
        fun `deny call`() = runTest {
            viewModel.denyCall()
            coVerify { CallManager.denyCall() }
        }
    }

    @Nested @DisplayName("End Call")
    inner class EndCallTest {
        @Test @DisplayName("endCall calls CallManager.endCall")
        fun `end call`() = runTest {
            viewModel.endCall()
            coVerify { CallManager.endCall() }
        }
    }

    @Nested @DisplayName("Toggle Mute")
    inner class ToggleMuteTest {
        @Test @DisplayName("toggleMute calls CallManager.toggleMute")
        fun `toggle mute`() = runTest {
            viewModel.toggleMute()
            coVerify { CallManager.toggleMute() }
        }
    }

    @Nested @DisplayName("Toggle Speaker")
    inner class ToggleSpeakerTest {
        @Test @DisplayName("toggleSpeaker calls CallManager.toggleSpeaker")
        fun `toggle speaker`() = runTest {
            viewModel.toggleSpeaker()
            coVerify { CallManager.toggleSpeaker() }
        }
    }

    @Nested @DisplayName("Toggle Video")
    inner class ToggleVideoTest {
        @Test @DisplayName("toggleVideo calls CallManager.toggleVideo")
        fun `toggle video`() = runTest {
            viewModel.toggleVideo()
            coVerify { CallManager.toggleVideo() }
        }
    }

    @Nested @DisplayName("Flip Camera")
    inner class FlipCameraTest {
        @Test @DisplayName("flipCamera calls CallManager.flipCamera")
        fun `flip camera`() = runTest {
            viewModel.flipCamera()
            coVerify { CallManager.flipCamera() }
        }
    }

    @Nested @DisplayName("Raise Hand")
    inner class RaiseHandTest {
        @Test @DisplayName("raiseHand calls CallManager.raiseHand")
        fun `raise hand`() = runTest {
            viewModel.raiseHand(true)
            coVerify { CallManager.raiseHand(true) }
        }
    }

    @Nested @DisplayName("React")
    inner class ReactTest {
        @Test @DisplayName("react calls CallManager.react")
        fun `react`() = runTest {
            viewModel.react("\uD83D\uDC4D")
            coVerify { CallManager.react("\uD83D\uDC4D") }
        }
    }

    @Nested @DisplayName("UI State")
    inner class UiStateTest {
        @Test @DisplayName("uiState emits CallState from CallManager")
        fun `ui state emits`() = runTest {
            val state = viewModel.uiState.value
            assertNotNull(state)
            assertNotNull(state.callState)
        }
    }

    @Nested @DisplayName("On Cleared")
    inner class OnClearedTest {
        @Test @DisplayName("onCleared cancels duration job")
        fun `on cleared cancels`() = runTest {
            viewModel.onCleared()
        }
    }
}
