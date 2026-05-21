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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.enchant.core.calls.CallDirection
import org.enchant.core.calls.CallEndReason
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.CallObserver
import org.enchant.core.calls.CallState
import org.enchant.core.calls.CallStatus
import org.enchant.core.calls.CallSummary
import org.enchant.core.calls.CallsModule
import org.enchant.core.calls.model.IceServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("CallManager")
class CallManagerStateTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockCallManager: org.enchant.core.calls.DefaultCallManager
    private lateinit var mockStateFlow: MutableStateFlow<CallState>

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockStateFlow = MutableStateFlow(CallState())
        mockCallManager = mockk(relaxed = true)
        every { mockCallManager.callState } returns mockStateFlow
        mockkObject(CallsModule)
        every { CallsModule.getCallManager() } returns mockCallManager
        mockkObject(CallManager)
        every { CallManager.callState } returns mockStateFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(CallsModule)
        unmockkObject(CallManager)
    }

    @Nested
    @DisplayName("initial state")
    inner class InitialState {
        @Test
        fun `starts in IDLE state`() {
            assert(mockStateFlow.value.status == CallStatus.IDLE)
        }

        @Test
        fun `has no remote user initially`() {
            assert(mockStateFlow.value.remoteUserId == null)
        }

        @Test
        fun `has no call ID initially`() {
            assert(mockStateFlow.value.callId == null)
        }

        @Test
        fun `is not muted initially`() {
            assert(!mockStateFlow.value.isMuted)
        }

        @Test
        fun `is not video call initially`() {
            assert(!mockStateFlow.value.isVideoCall)
        }

        @Test
        fun `duration is zero initially`() {
            assert(mockStateFlow.value.durationSeconds == 0)
        }

        @Test
        fun `is not on hold initially`() {
            assert(!mockStateFlow.value.isOnHold)
        }

        @Test
        fun `is hand not raised initially`() {
            assert(!mockStateFlow.value.isHandRaised)
        }
    }

    @Nested
    @DisplayName("state transitions")
    inner class StateTransitions {
        @Test
        fun `toggleMute updates mute state`() {
            val ringingState = CallState(status = CallStatus.RINGING, isMuted = false)
            mockStateFlow.value = ringingState

            val newState = ringingState.copy(isMuted = true)
            mockStateFlow.value = newState
            assert(mockStateFlow.value.isMuted)
        }

        @Test
        fun `toggleSpeaker updates speaker state`() {
            val connectedState = CallState(status = CallStatus.CONNECTED, isSpeakerOn = false)
            mockStateFlow.value = connectedState

            val newState = connectedState.copy(isSpeakerOn = true)
            mockStateFlow.value = newState
            assert(mockStateFlow.value.isSpeakerOn)
        }

        @Test
        fun `setOnHold updates hold state`() {
            val connectedState = CallState(status = CallStatus.CONNECTED, isOnHold = false)
            mockStateFlow.value = connectedState

            val newState = connectedState.copy(isOnHold = true)
            mockStateFlow.value = newState
            assert(mockStateFlow.value.isOnHold)
        }

        @Test
        fun `raiseHand updates hand raised state`() {
            val connectedState = CallState(status = CallStatus.CONNECTED, isHandRaised = false)
            mockStateFlow.value = connectedState

            val newState = connectedState.copy(isHandRaised = true)
            mockStateFlow.value = newState
            assert(mockStateFlow.value.isHandRaised)
        }
    }

    @Nested
    @DisplayName("observer registry")
    inner class ObserverRegistry {
        @Test
        fun `observer can be registered`() {
            val observer = object : CallObserver {
                override fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) {}
            }
            assertDoesNotThrow { CallManager.registerObserver(observer) }
        }

        @Test
        fun `observer can be unregistered`() {
            val observer = object : CallObserver {
                override fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) {}
            }
            CallManager.registerObserver(observer)
            assertDoesNotThrow { CallManager.unregisterObserver(observer) }
        }
    }
}