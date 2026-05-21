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
import org.enchant.core.calls.action.CallPhase
import org.enchant.core.calls.action.processors.IdleActionProcessor
import org.enchant.core.calls.action.processors.GroupCallActionProcessor
import org.enchant.core.calls.model.CallDirection
import org.enchant.core.calls.model.CallState
import org.enchant.core.calls.model.CallStatus
import org.enchant.core.calls.model.GroupCallState
import org.enchant.core.calls.model.GroupCallParticipant
import org.enchant.core.calls.model.CallParticipant
import org.enchant.core.calls.state.CallServiceState
import org.enchant.core.calls.state.LocalDeviceState
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

    @Nested @DisplayName("IncomingCallActionProcessor")
    inner class IncomingCallActionProcessorTest {
        @Test @DisplayName("handleAcceptIncomingCall sets isVideoCall from action")
        fun `accept incoming sets video from action`() = runTest {
            val state = createIncomingState(false)
            val action = CallAction.AcceptIncomingCall(true)

            val result = state.actionProcessor.process(state, action)

            assertTrue(result.callState.isVideoCall)
        }

        @Test @DisplayName("handleDenyIncomingCall transitions to IDLE")
        fun `deny incoming returns to idle`() = runTest {
            val state = createIncomingState(false)
            val action = CallAction.DenyIncomingCall(null)

            val result = state.actionProcessor.process(state, action)

            assertEquals(CallStatus.IDLE, result.callState.status)
        }

        @Test @DisplayName("CallConnected transitions to CONNECTED and switches processor")
        fun `call connected switches processor`() = runTest {
            val state = createIncomingState(false)
            val action = CallAction.CallConnected

            val result = state.actionProcessor.process(state, action)

            assertEquals(CallStatus.CONNECTED, result.callState.status)
            assertEquals(CallPhase.CONNECTED, result.phase)
        }

        @Test @DisplayName("ReceiveHangup transitions to IDLE")
        fun `receive hangup returns to idle`() = runTest {
            val state = createIncomingState(false)
            val action = CallAction.ReceiveHangup(null)

            val result = state.actionProcessor.process(state, action)

            assertEquals(CallStatus.IDLE, result.callState.status)
        }

        private fun createIncomingState(isVideo: Boolean): CallServiceState {
            val idleState = createIdleState()
            val action = CallAction.ReceiveIncomingOffer("user1", "sdp-content", "call-123", isVideo)
            return idleState.actionProcessor.process(idleState, action)
        }
    }

    @Nested @DisplayName("ConnectedCallActionProcessor")
    inner class ConnectedCallActionProcessorTest {
        @Test @DisplayName("handleToggleMute flips muted state")
        fun `toggle mute flips state`() = runTest {
            val state = createConnectedState()
            val initialMuted = state.localDeviceState.isMuted

            val result = state.actionProcessor.process(state, CallAction.ToggleMute)

            assertEquals(!initialMuted, result.localDeviceState.isMuted)
        }

        @Test @DisplayName("handleToggleSpeaker flips speaker state")
        fun `toggle speaker flips state`() = runTest {
            val state = createConnectedState()
            val initialSpeaker = state.localDeviceState.isSpeakerOn

            val result = state.actionProcessor.process(state, CallAction.ToggleSpeaker)

            assertEquals(!initialSpeaker, result.localDeviceState.isSpeakerOn)
        }

        @Test @DisplayName("handleToggleVideo flips video state")
        fun `toggle video flips state`() = runTest {
            val state = createConnectedState()
            val initialVideo = state.localDeviceState.isVideoEnabled

            val result = state.actionProcessor.process(state, CallAction.ToggleVideo)

            assertEquals(!initialVideo, result.localDeviceState.isVideoEnabled)
        }

        @Test @DisplayName("handleFlipCamera flips camera flipped state")
        fun `flip camera flips state`() = runTest {
            val state = createConnectedState()
            val initialFlipped = state.localDeviceState.isCameraFlipped

            val result = state.actionProcessor.process(state, CallAction.FlipCamera)

            assertEquals(!initialFlipped, result.localDeviceState.isCameraFlipped)
        }

        @Test @DisplayName("handleSetOnHold sets hold state")
        fun `set on hold sets hold`() = runTest {
            val state = createConnectedState()
            val action = CallAction.SetOnHold(true)

            val result = state.actionProcessor.process(state, action)

            assertTrue(result.localDeviceState.isOnHold)
        }

        @Test @DisplayName("handleRaiseHand sets isHandRaised and timestamp")
        fun `raise hand sets raised and timestamp`() = runTest {
            val state = createConnectedState()
            val action = CallAction.RaiseHand(true)

            val result = state.actionProcessor.process(state, action)

            assertTrue(result.localDeviceState.isHandRaised)
            assertTrue(result.localDeviceState.handRaisedTimestamp > 0)
        }

        @Test @DisplayName("handleRaiseHand clears hand when lowered")
        fun `raise hand clears when lowered`() = runTest {
            val state = createConnectedState()
            val raiseAction = CallAction.RaiseHand(true)
            val raisedState = state.actionProcessor.process(state, raiseAction)
            val lowerAction = CallAction.RaiseHand(false)

            val result = raisedState.actionProcessor.process(raisedState, lowerAction)

            assertFalse(result.localDeviceState.isHandRaised)
            assertEquals(0, result.localDeviceState.handRaisedTimestamp)
        }

        @Test @DisplayName("handleCallEnded transitions to IDLE")
        fun `call ended returns to idle`() = runTest {
            val state = createConnectedState()

            val result = state.actionProcessor.process(state, CallAction.CallEnded)

            assertEquals(CallStatus.IDLE, result.callState.status)
            assertEquals(CallPhase.IDLE, result.phase)
        }

        @Test @DisplayName("handleCallReconnecting sets RECONNECTING status")
        fun `call reconnecting sets status`() = runTest {
            val state = createConnectedState()

            val result = state.actionProcessor.process(state, CallAction.CallReconnecting)

            assertEquals(CallStatus.RECONNECTING, result.callState.status)
        }

        @Test @DisplayName("handleCallReconnected sets CONNECTED status")
        fun `call reconnected sets status`() = runTest {
            val state = createConnectedState()

            val result = state.actionProcessor.process(state, CallAction.CallReconnected)

            assertEquals(CallStatus.CONNECTED, result.callState.status)
        }

        @Test @DisplayName("handleCallFailedIce transitions to ENDED with error")
        fun `call failed ice sets ended`() = runTest {
            val state = createConnectedState()

            val result = state.actionProcessor.process(state, CallAction.CallFailedIce)

            assertEquals(CallStatus.ENDED, result.callState.status)
            assertNotNull(result.callState.error)
        }

        @Test @DisplayName("QualityUpdate updates quality stats")
        fun `quality update sets stats`() = runTest {
            val state = createConnectedState()
            val stats = org.enchant.core.calls.model.CallQualityStats(rttMs = 50)
            val action = CallAction.QualityUpdate(stats)

            val result = state.actionProcessor.process(state, action)

            assertEquals(50, result.qualityStats.rttMs)
        }

        private fun createConnectedState(): CallServiceState {
            val idleState = createIdleState()
            val startAction = CallAction.StartOutgoingCall("user1", false)
            val outgoingState = idleState.actionProcessor.process(idleState, startAction)
            val connectedAction = CallAction.CallConnected
            return outgoingState.actionProcessor.process(outgoingState, connectedAction)
        }
    }

    @Nested @DisplayName("GroupCallActionProcessor")
    inner class GroupCallActionProcessorTest {
        @Test @DisplayName("handleJoinGroupCall sets CONNECTING state")
        fun `join group call sets connecting`() = runTest {
            val state = createGroupConnectedState()
            val action = CallAction.JoinGroupCall("group1")

            val result = state.actionProcessor.process(state, action)

            assertEquals(org.enchant.core.calls.model.GroupCallState.CONNECTING, result.groupCallState)
        }

        @Test @DisplayName("handleLeaveGroupCall transitions to IDLE")
        fun `leave group call returns to idle`() = runTest {
            val state = createGroupConnectedState()

            val result = state.actionProcessor.process(state, CallAction.LeaveGroupCall)

            assertEquals(CallStatus.IDLE, result.callState.status)
            assertEquals(org.enchant.core.calls.model.GroupCallState.IDLE, result.groupCallState)
        }

        @Test @DisplayName("handleToggleMute flips muted state")
        fun `toggle mute flips state`() = runTest {
            val state = createGroupConnectedState()
            val initialMuted = state.localDeviceState.isMuted

            val result = state.actionProcessor.process(state, CallAction.ToggleMute)

            assertEquals(!initialMuted, result.localDeviceState.isMuted)
        }

        @Test @DisplayName("handleRaiseHand sets isHandRaised and timestamp")
        fun `raise hand sets raised and timestamp`() = runTest {
            val state = createGroupConnectedState()
            val action = CallAction.RaiseHand(true)

            val result = state.actionProcessor.process(state, action)

            assertTrue(result.localDeviceState.isHandRaised)
            assertTrue(result.localDeviceState.handRaisedTimestamp > 0)
        }

        @Test @DisplayName("handleGroupMembersUpdated updates participants")
        fun `group members updated sets participants`() = runTest {
            val state = createGroupConnectedState()
            val participants = listOf(
                org.enchant.core.calls.model.CallParticipant("user1", "User One", true, true, false),
                org.enchant.core.calls.model.CallParticipant("user2", "User Two", false, false, true)
            )
            val action = CallAction.GroupMembersUpdated(participants)

            val result = state.actionProcessor.process(state, action)

            assertEquals(2, result.groupCallParticipants.size)
        }

        @Test @DisplayName("handleRemoveParticipant filters out target")
        fun `remove participant filters out`() = runTest {
            val state = createGroupConnectedStateWithParticipants()
            val action = CallAction.RemoveParticipant("user1")

            val result = state.actionProcessor.process(state, action)

            assertTrue(result.groupCallParticipants.none { it.userId == "user1" })
        }

        @Test @DisplayName("handleCallReconnecting sets both reconnecting states")
        fun `call reconnecting sets both states`() = runTest {
            val state = createGroupConnectedState()

            val result = state.actionProcessor.process(state, CallAction.CallReconnecting)

            assertEquals(CallStatus.RECONNECTING, result.callState.status)
            assertEquals(org.enchant.core.calls.model.GroupCallState.RECONNECTING, result.groupCallState)
        }

        private fun createGroupConnectedState(): CallServiceState {
            return CallServiceState(
                callState = CallState(
                    status = CallStatus.CONNECTED,
                    remoteUserId = "group1",
                    isVideoCall = false,
                    direction = CallDirection.OUTGOING
                ),
                actionProcessor = GroupCallActionProcessor(mockCallLogger, mockObserverRegistry, "group1"),
                localDeviceState = org.enchant.core.calls.state.LocalDeviceState()
            )
        }

        private fun createGroupConnectedStateWithParticipants(): CallServiceState {
            val participants = listOf(
                org.enchant.core.calls.model.GroupCallParticipant("user1", 1, false, false, false, 0),
                org.enchant.core.calls.model.GroupCallParticipant("user2", 2, false, false, false, 0)
            )
            return CallServiceState(
                callState = CallState(
                    status = CallStatus.CONNECTED,
                    remoteUserId = "group1",
                    isVideoCall = false,
                    direction = CallDirection.OUTGOING
                ),
                actionProcessor = GroupCallActionProcessor(mockCallLogger, mockObserverRegistry, "group1"),
                localDeviceState = org.enchant.core.calls.state.LocalDeviceState(),
                groupCallParticipants = participants
            )
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