package org.enchant.calls

import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.CallState
import org.enchant.core.calls.CallStatus
import org.enchant.core.calls.CallsModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("CallViewModel — Full Coverage")
class CallViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockCallManager: org.enchant.core.calls.DefaultCallManager

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        Dispatchers.setMain(testDispatcher)
        mockCallManager = mockk(relaxed = true)
        mockkObject(CallsModule)
        every { CallsModule.getCallManager() } returns mockCallManager
        every { mockCallManager.callState } returns MutableStateFlow(CallState())
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(CallsModule)
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    @Nested @DisplayName("Start Call")
    inner class StartCallTest {
        @Test @DisplayName("startCall calls CallManager.startOutgoingCall")
        fun `start call`() = runTest {
            coEvery { mockCallManager.startOutgoingCall(any(), any()) } returns Unit
            val viewModel = CallViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.startCall("remote-user", false)
            testDispatcher.scheduler.runCurrent()
            coVerify { mockCallManager.startOutgoingCall("remote-user", false) }
        }

        @Test @DisplayName("startCall with video calls with isVideo=true")
        fun `start call video`() = runTest {
            coEvery { mockCallManager.startOutgoingCall(any(), any()) } returns Unit
            val viewModel = CallViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.startCall("remote-user", true)
            testDispatcher.scheduler.runCurrent()
            coVerify { mockCallManager.startOutgoingCall("remote-user", true) }
        }
    }

    @Nested @DisplayName("Accept Call")
    inner class AcceptCallTest {
        @Test @DisplayName("acceptCall calls CallManager.acceptCall when RINGING")
        fun `accept call`() = runTest {
            coEvery { mockCallManager.acceptCall(any()) } returns Unit
            every { mockCallManager.callState } returns MutableStateFlow(CallState(status = CallStatus.RINGING))
            val viewModel = CallViewModel()
            testDispatcher.scheduler.runCurrent()
            testDispatcher.scheduler.runCurrent()
            viewModel.acceptCall(false)
            testDispatcher.scheduler.runCurrent()
            coVerify { mockCallManager.acceptCall(false) }
        }

        @Test @DisplayName("acceptCall does nothing when not RINGING")
        fun `accept call ignored when not ringing`() = runTest {
            every { mockCallManager.callState } returns MutableStateFlow(CallState(status = CallStatus.IDLE))
            val viewModel = CallViewModel()
            testDispatcher.scheduler.runCurrent()
            testDispatcher.scheduler.runCurrent()
            viewModel.acceptCall(false)
            testDispatcher.scheduler.runCurrent()
            coVerify(inverse = true) { mockCallManager.acceptCall(any()) }
        }
    }

    @Nested @DisplayName("Deny Call")
    inner class DenyCallTest {
        @Test @DisplayName("denyCall calls CallManager.denyCall")
        fun `deny call`() = runTest {
            every { mockCallManager.denyCall() } returns Unit
            val viewModel = CallViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.denyCall()
            verify { mockCallManager.denyCall() }
        }
    }

    @Nested @DisplayName("End Call")
    inner class EndCallTest {
        @Test @DisplayName("endCall calls CallManager.endCall")
        fun `end call`() = runTest {
            every { mockCallManager.endCall() } returns Unit
            val viewModel = CallViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.endCall()
            verify { mockCallManager.endCall() }
        }
    }

    @Nested @DisplayName("Toggle Mute")
    inner class ToggleMuteTest {
        @Test @DisplayName("toggleMute calls CallManager.toggleMute")
        fun `toggle mute`() = runTest {
            every { mockCallManager.toggleMute() } returns Unit
            val viewModel = CallViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.toggleMute()
            verify { mockCallManager.toggleMute() }
        }
    }

    @Nested @DisplayName("Toggle Speaker")
    inner class ToggleSpeakerTest {
        @Test @DisplayName("toggleSpeaker calls CallManager.toggleSpeaker")
        fun `toggle speaker`() = runTest {
            every { mockCallManager.toggleSpeaker() } returns Unit
            val viewModel = CallViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.toggleSpeaker()
            verify { mockCallManager.toggleSpeaker() }
        }
    }

    @Nested @DisplayName("Toggle Video")
    inner class ToggleVideoTest {
        @Test @DisplayName("toggleVideo calls DefaultCallManager.toggleVideo")
        fun `toggle video`() = runTest {
            every { mockCallManager.toggleVideo() } returns Unit
            val viewModel = CallViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.toggleVideo()
            verify { mockCallManager.toggleVideo() }
        }
    }

    @Nested @DisplayName("Flip Camera")
    inner class FlipCameraTest {
        @Test @DisplayName("flipCamera calls CallManager.flipCamera")
        fun `flip camera`() = runTest {
            every { mockCallManager.flipCamera() } returns Unit
            val viewModel = CallViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.flipCamera()
            verify { mockCallManager.flipCamera() }
        }
    }

    @Nested @DisplayName("Raise Hand")
    inner class RaiseHandTest {
        @Test @DisplayName("raiseHand calls CallManager.raiseHand")
        fun `raise hand`() = runTest {
            every { mockCallManager.raiseHand(any()) } returns Unit
            val viewModel = CallViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.raiseHand(true)
            verify { mockCallManager.raiseHand(true) }
        }
    }

    @Nested @DisplayName("UI State")
    inner class UiStateTest {
        @Test @DisplayName("uiState emits CallState from CallManager")
        fun `ui state emits`() = runTest {
            val viewModel = CallViewModel()
            testDispatcher.scheduler.runCurrent()
            val state = viewModel.uiState.value
            assertNotNull(state)
            assertNotNull(state.callState)
        }

        @Test @DisplayName("uiState isCallScreenVisible true when CONNECTED")
        fun `call screen visible when connected`() = runTest {
            every { mockCallManager.callState } returns MutableStateFlow(CallState(status = CallStatus.CONNECTED))
            val viewModel = CallViewModel()
            testDispatcher.scheduler.runCurrent()
            assertTrue(viewModel.uiState.value.isCallScreenVisible)
        }

        @Test @DisplayName("uiState isCallScreenVisible false when IDLE")
        fun `call screen hidden when idle`() = runTest {
            every { mockCallManager.callState } returns MutableStateFlow(CallState(status = CallStatus.IDLE))
            val viewModel = CallViewModel()
            testDispatcher.scheduler.runCurrent()
            assertFalse(viewModel.uiState.value.isCallScreenVisible)
        }
    }

    @Nested @DisplayName("Navigation")
    inner class NavigationTest {
        @Test @DisplayName("navigateToConversation sets navigateToConversation")
        fun `navigate to conversation`() {
            val viewModel = CallViewModel()
            viewModel.navigateToConversation("conv-123")
            assertEquals("conv-123", viewModel.uiState.value.navigateToConversation)
        }

        @Test @DisplayName("onNavigatedToConversation clears navigateToConversation")
        fun `navigated clears`() {
            val viewModel = CallViewModel()
            viewModel.navigateToConversation("conv-123")
            viewModel.onNavigatedToConversation()
            assertNull(viewModel.uiState.value.navigateToConversation)
        }
    }
}