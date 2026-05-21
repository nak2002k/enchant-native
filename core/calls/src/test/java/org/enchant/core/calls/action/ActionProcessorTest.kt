package org.enchant.core.calls.action

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.enchant.core.calls.CallLogger
import org.enchant.core.calls.CallObserverRegistry
import org.enchant.core.calls.action.processors.IdleActionProcessor
import org.enchant.core.calls.model.CallDirection
import org.enchant.core.calls.model.CallState
import org.enchant.core.calls.model.CallStatus
import org.enchant.core.calls.state.CallServiceState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("ActionProcessor — Full Coverage")
class ActionProcessorTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockCallLogger: CallLogger
    private lateinit var mockObserverRegistry: CallObserverRegistry

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockCallLogger = mockk(relaxed = true)
        mockObserverRegistry = mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    private fun createIdleState(): CallServiceState {
        return CallServiceState(
            callState = CallState.idle(),
            actionProcessor = IdleActionProcessor(mockCallLogger, mockObserverRegistry)
        )
    }

    @Nested @DisplayName("IdleActionProcessor")
    inner class IdleActionProcessorTest {
        @Test @DisplayName("StartOutgoingCall transitions to CALLING state")
        fun `start outgoing call transitions state`() = runTest {
            val state = createIdleState()
            val action = CallAction.StartOutgoingCall("user1", false)

            val result = state.actionProcessor.process(state, action)

            assertNotNull(result.callState)
            assertEquals(CallStatus.CALLING, result.callState.status)
            assertEquals("user1", result.callState.remoteUserId)
            assertEquals(CallDirection.OUTGOING, result.callState.direction)
            assertFalse(result.callState.isVideoCall)
        }

        @Test @DisplayName("StartOutgoingCall generates callId")
        fun `start outgoing call generates call id`() = runTest {
            val state = createIdleState()
            val action = CallAction.StartOutgoingCall("user1", false)

            val result = state.actionProcessor.process(state, action)

            assertNotNull(result.callState.callId)
            assertTrue(result.callState.callId!!.isNotBlank())
        }

        @Test @DisplayName("StartOutgoingCall video call sets isVideoCall")
        fun `start outgoing video call`() = runTest {
            val state = createIdleState()
            val action = CallAction.StartOutgoingCall("user1", true)

            val result = state.actionProcessor.process(state, action)

            assertTrue(result.callState.isVideoCall)
        }

        @Test @DisplayName("StartOutgoingCall sets CallSetupData")
        fun `start outgoing call sets setup data`() = runTest {
            val state = createIdleState()
            val action = CallAction.StartOutgoingCall("user1", false)

            val result = state.actionProcessor.process(state, action)

            assertNotNull(result.callSetupData)
            assertEquals("user1", result.callSetupData!!.remoteUserId)
            assertFalse(result.callSetupData!!.isVideo)
        }

        @Test @DisplayName("ReceiveIncomingOffer transitions to RINGING state")
        fun `receive incoming offer transitions state`() = runTest {
            val state = createIdleState()
            val action = CallAction.ReceiveIncomingOffer("user1", "sdp-content", "call-123", false)

            val result = state.actionProcessor.process(state, action)

            assertEquals(CallStatus.RINGING, result.callState.status)
            assertEquals("user1", result.callState.remoteUserId)
            assertEquals(CallDirection.INCOMING, result.callState.direction)
            assertEquals("call-123", result.callState.callId)
        }

        @Test @DisplayName("ReceiveIncomingOffer sets offerSdp in setupData")
        fun `receive incoming offer sets sdp`() = runTest {
            val state = createIdleState()
            val action = CallAction.ReceiveIncomingOffer("user1", "sdp-content", "call-123", true)

            val result = state.actionProcessor.process(state, action)

            assertNotNull(result.callSetupData)
            assertEquals("sdp-content", result.callSetupData!!.offerSdp)
        }

        @Test @DisplayName("ReceiveIncomingOffer with blank callId generates UUID")
        fun `receive incoming offer generates call id`() = runTest {
            val state = createIdleState()
            val action = CallAction.ReceiveIncomingOffer("user1", "sdp", "", false)

            val result = state.actionProcessor.process(state, action)

            assertNotNull(result.callState.callId)
            assertTrue(result.callState.callId!!.isNotBlank())
        }

        @Test @DisplayName("unhandled action returns same state")
        fun `unhandled action returns same state`() = runTest {
            val state = createIdleState()
            val action = CallAction.CallConnected

            val result = state.actionProcessor.process(state, action)

            assertEquals(state.callState.status, result.callState.status)
        }
    }

    @Nested @DisplayName("OutgoingCallActionProcessor")
    inner class OutgoingCallActionProcessorTest {
        @Test @DisplayName("CallConnected transitions to CONNECTED state")
        fun `call connected transitions state`() = runTest {
            val state = createOutgoingState()
            val action = CallAction.CallConnected

            val result = state.actionProcessor.process(state, action)

            assertEquals(CallStatus.CONNECTED, result.callState.status)
        }

        @Test @DisplayName("CancelOutgoingCall transitions to IDLE status")
        fun `cancel outgoing call returns to idle`() = runTest {
            val state = createOutgoingState()
            val action = CallAction.CancelOutgoingCall()

            val result = state.actionProcessor.process(state, action)

            assertEquals(CallStatus.IDLE, result.callState.status)
        }

        @Test @DisplayName("CancelOutgoingCall with reason logs reason")
        fun `cancel outgoing call with reason`() = runTest {
            val state = createOutgoingState()
            val action = CallAction.CancelOutgoingCall("user cancelled")

            val result = state.actionProcessor.process(state, action)

            assertEquals(CallStatus.IDLE, result.callState.status)
        }

        @Test @DisplayName("CallFailedTimeout transitions to ENDED status")
        fun `call failed timeout`() = runTest {
            val state = createOutgoingState()
            val action = CallAction.CallFailedTimeout

            val result = state.actionProcessor.process(state, action)

            assertEquals(CallStatus.ENDED, result.callState.status)
            assertNotNull(result.callState.error)
        }

        @Test @DisplayName("CallFailedBusy transitions to ENDED status")
        fun `call failed busy`() = runTest {
            val state = createOutgoingState()
            val action = CallAction.CallFailedBusy

            val result = state.actionProcessor.process(state, action)

            assertEquals(CallStatus.ENDED, result.callState.status)
            assertNotNull(result.callState.error)
        }

        @Test @DisplayName("ReceiveAnswer transitions to CONNECTING")
        fun `receive answer transitions to connecting`() = runTest {
            val state = createOutgoingState()
            val action = CallAction.ReceiveAnswer("answer-sdp")

            val result = state.actionProcessor.process(state, action)

            assertEquals(CallStatus.CONNECTING, result.callState.status)
        }

        @Test @DisplayName("ToggleMute returns same state (unhandled in outgoing)")
        fun `toggle mute unhandled in outgoing`() = runTest {
            val state = createOutgoingState()

            val result = state.actionProcessor.process(state, CallAction.ToggleMute)

            assertEquals(state.callState, result.callState)
        }

        @Test @DisplayName("unhandled action returns same state")
        fun `unhandled action returns same state`() = runTest {
            val state = createOutgoingState()
            val action = CallAction.GroupCallEnded

            val result = state.actionProcessor.process(state, action)

            assertEquals(state.callState, result.callState)
        }

        private fun createOutgoingState(): CallServiceState {
            val idleState = createIdleState()
            val action = CallAction.StartOutgoingCall("user1", false)
            return idleState.actionProcessor.process(idleState, action)
        }
    }

    @Nested @DisplayName("BaseActionProcessor")
    inner class BaseActionProcessorTest {
        @Test @DisplayName("process handles all CallAction types without throwing")
        fun `process handles all action types`() = runTest {
            val state = createIdleState()
            val actions = listOf(
                CallAction.StartOutgoingCall("user1", false),
                CallAction.CancelOutgoingCall(),
                CallAction.ReceiveIncomingOffer("user2", "sdp", "call-1", false),
                CallAction.AcceptIncomingCall(false),
                CallAction.DenyIncomingCall(),
                CallAction.ReceiveAnswer("sdp"),
                CallAction.ReceiveIceCandidate("candidate"),
                CallAction.ReceiveHangup(),
                CallAction.ToggleMute,
                CallAction.ToggleSpeaker,
                CallAction.ToggleVideo,
                CallAction.FlipCamera,
                CallAction.SetOnHold(true),
                CallAction.RaiseHand(true),
                CallAction.CallConnected,
                CallAction.CallReconnecting,
                CallAction.CallReconnected,
                CallAction.CallEnded,
                CallAction.QualityUpdate(mockk(relaxed = true)),
                CallAction.IncomingCallTimeout,
                CallAction.SignalingTimeout,
                CallAction.JoinGroupCall("group1"),
                CallAction.LeaveGroupCall,
                CallAction.GroupCallRaisedHand("user1", true),
                CallAction.SendReaction("👍"),
                CallAction.GroupCallReaction("user1", "👍"),
                CallAction.RemoteMute("user2"),
                CallAction.RemoteUnmute("user2"),
                CallAction.RemoveParticipant("user2"),
                CallAction.BlockParticipant("user2"),
                CallAction.SetRingGroup(true),
                CallAction.GroupCallRingUpdate("user1"),
                CallAction.GroupMembersUpdated(emptyList()),
                CallAction.GroupCallEnded
            )

            for (action in actions) {
                val result = state.actionProcessor.process(state, action)
                assertNotNull(result)
            }
        }
    }

    @Nested @DisplayName("CallPhase")
    inner class CallPhaseTest {
        @Test @DisplayName("CallPhase enum has all expected values")
        fun `call phase enum values`() {
            assertEquals(CallPhase.IDLE, CallPhase.valueOf("IDLE"))
            assertEquals(CallPhase.OUTGOING_CALL, CallPhase.valueOf("OUTGOING_CALL"))
            assertEquals(CallPhase.INCOMING_CALL, CallPhase.valueOf("INCOMING_CALL"))
            assertEquals(CallPhase.CONNECTED, CallPhase.valueOf("CONNECTED"))
            assertEquals(CallPhase.RECONNECTING, CallPhase.valueOf("RECONNECTING"))
            assertEquals(CallPhase.GROUP_CONNECTED, CallPhase.valueOf("GROUP_CONNECTED"))
            assertEquals(CallPhase.CALL_LINK, CallPhase.valueOf("CALL_LINK"))
        }
    }
}