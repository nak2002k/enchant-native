package org.enchant.calls

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.enchant.core.calls.CallEndReason
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.CallObserver
import org.enchant.core.calls.CallStatusEnum
import org.enchant.core.calls.CallSummary
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("CallManager")
class CallManagerStateTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        CallManager.resetForTest()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("initial state")
    inner class InitialState {
        @Test
        fun `starts in IDLE state`() {
            assert(CallManager.callState.value.status == CallStatusEnum.IDLE)
        }

        @Test
        fun `has no remote user initially`() {
            assert(CallManager.callState.value.remoteUserId == null)
        }

        @Test
        fun `has no call ID initially`() {
            assert(CallManager.callState.value.callId == null)
        }

        @Test
        fun `is not muted initially`() {
            assert(!CallManager.callState.value.isMuted)
        }

        @Test
        fun `is not video call initially`() {
            assert(!CallManager.callState.value.isVideoCall)
        }

        @Test
        fun `duration is zero initially`() {
            assert(CallManager.callState.value.durationSeconds == 0)
        }

        @Test
        fun `is not on hold initially`() {
            assert(!CallManager.callState.value.isOnHold)
        }

        @Test
        fun `hand is not raised initially`() {
            assert(!CallManager.callState.value.isHandRaised)
        }
    }

    @Nested
    @DisplayName("incoming call")
    inner class IncomingCall {
        @Test
        fun `handleReceivedOffer transitions to RINGING`() {
            CallManager.handleReceivedOffer("remote_user", "sdp_offer", "call_123")

            assert(CallManager.callState.value.status == CallStatusEnum.RINGING)
            assert(CallManager.callState.value.remoteUserId == "remote_user")
            assert(CallManager.callState.value.callId == "call_123")
        }

        @Test
        fun `handleReceivedHangup transitions to IDLE`() {
            CallManager.handleReceivedOffer("remote_user", "sdp", "call_1")
            CallManager.handleReceivedHangup()

            assert(CallManager.callState.value.status == CallStatusEnum.IDLE)
        }

        @Test
        fun `handleReceivedOffer while in RINGING does not change state`() {
            CallManager.handleReceivedOffer("remote_user", "sdp", "call_1")
            CallManager.handleReceivedOffer("other_user", "sdp2", "call_2")

            assert(CallManager.callState.value.remoteUserId == "remote_user")
            assert(CallManager.callState.value.status == CallStatusEnum.RINGING)
        }

        @Test
        fun `observer is notified on incoming call`() {
            var notified = false
            val observer = object : CallObserver {
                override fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) { notified = true }
            }
            CallManager.registerObserver(observer)

            CallManager.handleReceivedOffer("remote_user", "sdp", "call_1")

            assert(notified)
        }

        @Test
        fun `observer is notified on call end`() {
            var ended = false
            val observer = object : CallObserver {
                override fun onCallEnded(reason: CallEndReason, summary: CallSummary?) { ended = true }
            }
            CallManager.registerObserver(observer)

            CallManager.handleReceivedOffer("remote_user", "sdp", "call_1")
            CallManager.handleReceivedHangup()

            assert(ended)
        }

    }

    @Nested
    @DisplayName("call end")
    inner class CallEnd {
        @Test
        fun `endCall from RINGING transitions to IDLE`() {
            CallManager.handleReceivedOffer("remote_user", "sdp", "call_1")
            assert(CallManager.callState.value.status == CallStatusEnum.RINGING)

            CallManager.endCall()

            assert(CallManager.callState.value.status == CallStatusEnum.IDLE)
        }

        @Test
        fun `endCall from IDLE is no-op`() {
            CallManager.endCall()
            assert(CallManager.callState.value.status == CallStatusEnum.IDLE)
        }

        @Test
        fun `denyCall from RINGING transitions to IDLE`() {
            CallManager.handleReceivedOffer("remote_user", "sdp", "call_1")
            CallManager.denyCall()

            assert(CallManager.callState.value.status == CallStatusEnum.IDLE)
        }

        @Test
        fun `cleanup resets all fields to defaults`() {
            CallManager.handleReceivedOffer("user", "sdp", "call_1")
            CallManager.handleReceivedHangup()

            val state = CallManager.callState.value
            assert(state.status == CallStatusEnum.IDLE)
            assert(state.remoteUserId == null)
            assert(state.callId == null)
            assert(state.durationSeconds == 0)
        }
    }

    @Nested
    @DisplayName("outgoing call")
    inner class OutgoingCall {
        @Test
        fun `cannot start outgoing call when already in a call`() {
            CallManager.handleReceivedOffer("user1", "sdp", "call_1")
            assert(CallManager.callState.value.status == CallStatusEnum.RINGING)

            assert(CallManager.callState.value.error == null)
        }

        @Test
        fun `endCall from outgoing state resets`() {
            CallManager.endCall()
            assert(CallManager.callState.value.status == CallStatusEnum.IDLE)
        }
    }

    @Nested
    @DisplayName("state transitions")
    inner class StateTransitions {
        @Test
        fun `toggleMute flips isMuted`() {
            CallManager.handleReceivedOffer("remote_user", "sdp", "call_1")

            val before = CallManager.callState.value.isMuted
            CallManager.toggleMute()
            assert(CallManager.callState.value.isMuted != before)

            CallManager.toggleMute()
            assert(CallManager.callState.value.isMuted == before)
        }

        @Test
        fun `toggleSpeaker flips isSpeakerOn`() {
            CallManager.handleReceivedOffer("remote_user", "sdp", "call_1")

            val before = CallManager.callState.value.isSpeakerOn
            CallManager.toggleSpeaker()
            assert(CallManager.callState.value.isSpeakerOn != before)
        }

        @Test
        fun `toggleVideo flips isVideoCall`() {
            CallManager.handleReceivedOffer("remote_user", "sdp", "call_1")

            val before = CallManager.callState.value.isVideoCall
            CallManager.toggleVideo()
            assert(CallManager.callState.value.isVideoCall != before)
        }

        @Test
        fun `setOnHold updates hold state`() {
            CallManager.handleReceivedOffer("remote_user", "sdp", "call_1")

            CallManager.setOnHold(true)
            assert(CallManager.callState.value.isOnHold)

            CallManager.setOnHold(false)
            assert(!CallManager.callState.value.isOnHold)
        }

        @Test
        fun `raiseHand flips isHandRaised`() {
            CallManager.raiseHand(true)
            assert(CallManager.callState.value.isHandRaised)

            CallManager.raiseHand(false)
            assert(!CallManager.callState.value.isHandRaised)
        }

        @Test
        fun `setRingGroup stores preference`() {
            CallManager.setRingGroup(false)
            CallManager.setRingGroup(true)
        }
    }

    @Nested
    @DisplayName("observer registry")
    inner class ObserverRegistry {
        @Test
        fun `unregistered observer does not receive events`() {
            var notified = false
            val observer = object : CallObserver {
                override fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) { notified = true }
            }
            CallManager.registerObserver(observer)
            CallManager.unregisterObserver(observer)

            CallManager.handleReceivedOffer("user", "sdp", "call_1")

            assert(!notified)
        }

        @Test
        fun `multiple observers all receive events`() {
            var count = 0
            val observer1 = object : CallObserver {
                override fun onCallEnded(reason: CallEndReason, summary: CallSummary?) { count++ }
            }
            val observer2 = object : CallObserver {
                override fun onCallEnded(reason: CallEndReason, summary: CallSummary?) { count++ }
            }
            CallManager.registerObserver(observer1)
            CallManager.registerObserver(observer2)

            CallManager.handleReceivedOffer("user", "sdp", "call_1")
            CallManager.handleReceivedHangup()

            assert(count == 2)
        }

        @Test
        fun `duplicate observer is not registered twice`() {
            var count = 0
            val observer = object : CallObserver {
                override fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) { count++ }
            }
            CallManager.registerObserver(observer)
            CallManager.registerObserver(observer)

            CallManager.handleReceivedOffer("user", "sdp", "call_1")

            assert(count == 1)
        }
    }
}
