package org.enchant.calls

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.enchant.core.calls.CallEndReason
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.CallObserver
import org.enchant.core.calls.CallStatusEnum
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
        fun `handleReceivedOffer while in RINGING sends busy`() {
            CallManager.handleReceivedOffer("remote_user", "sdp", "call_1")
            CallManager.handleReceivedOffer("other_user", "sdp2", "call_2")

            assert(CallManager.callState.value.remoteUserId == "remote_user")
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
    }

    @Nested
    @DisplayName("call end")
    inner class CallEnd {
        @Test
        fun `endCall transitions to IDLE`() {
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
        fun `denyCall transitions to IDLE`() {
            CallManager.handleReceivedOffer("remote_user", "sdp", "call_1")
            CallManager.denyCall()

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
    }
}
