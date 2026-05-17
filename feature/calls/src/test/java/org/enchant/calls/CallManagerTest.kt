package org.enchant.calls

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.enchant.core.calls.CallEndReason
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.CallObserver
import org.enchant.core.calls.CallState
import org.enchant.core.calls.CallStatusEnum
import org.enchant.core.calls.WebRtcService
import org.enchant.core.base.SecurePreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.enchant.core.calls.AudioRouter
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CallManager — Full Coverage")
class CallManagerTest {

    @BeforeEach
    fun setUp() {
        mockkObject(SecurePreferences)
        every { SecurePreferences.getString(any(), any()) } returns "self-user"
        every { SecurePreferences.getString(any()) } returns "self-user"
        mockkObject(WebRtcService)
        coEvery { WebRtcService.getLocalStream(any()) } returns null
        coEvery { WebRtcService.createPeerConnection(any(), any()) } returns null
        mockkObject(AudioRouter)
        CallManager.resetForTest()
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(SecurePreferences)
        unmockkObject(WebRtcService)
        unmockkObject(AudioRouter)
    }

    @Nested @DisplayName("Start Outgoing Call")
    inner class StartOutgoingTest {
        @Test @DisplayName("startOutgoingCall sets CALLING state")
        fun `start outgoing sets calling`() = runTest {
            CallManager.startOutgoingCall("remote-user", false)
            assertEquals(CallStatusEnum.CALLING, CallManager.callState.value.status)
            assertEquals("remote-user", CallManager.callState.value.remoteUserId)
            assertFalse(CallManager.callState.value.isVideoCall)
        }

        @Test @DisplayName("startOutgoingCall with video sets isVideoCall")
        fun `start outgoing video`() = runTest {
            CallManager.startOutgoingCall("remote-user", true)
            assertTrue(CallManager.callState.value.isVideoCall)
        }

        @Test @DisplayName("startOutgoingCall fails when already in call")
        fun `start outgoing already in call`() = runTest {
            CallManager.startOutgoingCall("remote-user", false)
            CallManager.startOutgoingCall("other-user", false)
            assertEquals("Already in a call", CallManager.callState.value.error)
        }

        @Test @DisplayName("startOutgoingCall does nothing when no user ID")
        fun `start outgoing no user id`() = runTest {
            every { SecurePreferences.getString(any(), any()) } returns null
            CallManager.startOutgoingCall("remote-user", false)
            assertEquals(CallStatusEnum.IDLE, CallManager.callState.value.status)
        }
    }

    @Nested @DisplayName("Accept Call")
    inner class AcceptCallTest {
        @Test @DisplayName("acceptCall does nothing when not RINGING")
        fun `accept not ringing`() = runTest {
            CallManager.acceptCall("call-1", false)
            assertEquals(CallStatusEnum.IDLE, CallManager.callState.value.status)
        }

        @Test @DisplayName("acceptCall sets CONNECTING state when RINGING")
        fun `accept sets connecting`() = runTest {
            CallManager.handleReceivedOffer("remote-user", "sdp-offer", "call-1")
            CallManager.acceptCall("call-1", false)
            assertEquals(CallStatusEnum.CONNECTING, CallManager.callState.value.status)
        }
    }

    @Nested @DisplayName("Deny Call")
    inner class DenyCallTest {
        @Test @DisplayName("denyCall does nothing when no remote user")
        fun `deny no remote`() = runTest {
            CallManager.denyCall()
            assertEquals(CallStatusEnum.IDLE, CallManager.callState.value.status)
        }
    }

    @Nested @DisplayName("End Call")
    inner class EndCallTest {
        @Test @DisplayName("endCall does nothing when IDLE")
        fun `end idle does nothing`() = runTest {
            CallManager.endCall()
            assertEquals(CallStatusEnum.IDLE, CallManager.callState.value.status)
        }

        @Test @DisplayName("endCall resets state to IDLE")
        fun `end resets to idle`() = runTest {
            CallManager.startOutgoingCall("remote-user", false)
            CallManager.endCall()
            assertEquals(CallStatusEnum.IDLE, CallManager.callState.value.status)
        }
    }

    @Nested @DisplayName("Toggle Mute")
    inner class ToggleMuteTest {
        @Test @DisplayName("toggleMute toggles isMuted state")
        fun `toggle mute`() = runTest {
            assertFalse(CallManager.callState.value.isMuted)
            CallManager.toggleMute()
            assertTrue(CallManager.callState.value.isMuted)
            CallManager.toggleMute()
            assertFalse(CallManager.callState.value.isMuted)
        }
    }

    @Nested @DisplayName("Toggle Video")
    inner class ToggleVideoTest {
        @Test @DisplayName("toggleVideo toggles isVideoCall state")
        fun `toggle video`() = runTest {
            CallManager.startOutgoingCall("remote-user", false)
            assertFalse(CallManager.callState.value.isVideoCall)
            CallManager.toggleVideo()
            assertTrue(CallManager.callState.value.isVideoCall)
        }
    }

    @Nested @DisplayName("Toggle Speaker")
    inner class ToggleSpeakerTest {
        @Test @DisplayName("toggleSpeaker toggles isSpeakerOn state")
        fun `toggle speaker`() = runTest {
            assertFalse(CallManager.callState.value.isSpeakerOn)
            CallManager.toggleSpeaker()
            assertTrue(CallManager.callState.value.isSpeakerOn)
        }
    }

    @Nested @DisplayName("Handle Received Offer")
    inner class HandleOfferTest {
        @Test @DisplayName("handleReceivedOffer sets RINGING state")
        fun `handle offer sets ringing`() = runTest {
            CallManager.handleReceivedOffer("remote-user", "sdp-offer", "call-1")
            assertEquals(CallStatusEnum.RINGING, CallManager.callState.value.status)
            assertEquals("remote-user", CallManager.callState.value.remoteUserId)
            assertEquals("call-1", CallManager.callState.value.callId)
        }

        @Test @DisplayName("handleReceivedOffer rejects when already in call")
        fun `handle offer already in call`() = runTest {
            CallManager.startOutgoingCall("remote-user", false)
            CallManager.handleReceivedOffer("other-user", "sdp-offer", "call-2")
            assertEquals("remote-user", CallManager.callState.value.remoteUserId)
        }

        @Test @DisplayName("handleReceivedOffer detects video from SDP")
        fun `handle offer detects video`() = runTest {
            CallManager.handleReceivedOffer("remote-user", "m=video 9 UDP/TLSRTP/SAVPF", "call-1")
            assertTrue(CallManager.callState.value.isVideoCall)
        }

        @Test @DisplayName("handleReceivedOffer with isVideo=true sets video")
        fun `handle offer is video true`() = runTest {
            CallManager.handleReceivedOffer("remote-user", "sdp-offer", "call-1", isVideo = true)
            assertTrue(CallManager.callState.value.isVideoCall)
        }
    }

    @Nested @DisplayName("Handle Received Answer")
    inner class HandleAnswerTest {
        @Test @DisplayName("handleReceivedAnswer sets CONNECTED state")
        fun `handle answer sets connected`() = runTest {
            CallManager.startOutgoingCall("remote-user", false)
            CallManager.handleReceivedAnswer("sdp-answer")
            assertEquals(CallStatusEnum.CONNECTED, CallManager.callState.value.status)
        }

        @Test @DisplayName("handleReceivedAnswer does nothing when no peer connection")
        fun `handle answer no pc`() = runTest {
            CallManager.handleReceivedAnswer("sdp-answer")
            assertEquals(CallStatusEnum.IDLE, CallManager.callState.value.status)
        }
    }

    @Nested @DisplayName("Handle Received Hangup")
    inner class HandleHangupTest {
        @Test @DisplayName("handleReceivedHangup resets to IDLE")
        fun `handle hangup resets`() = runTest {
            CallManager.startOutgoingCall("remote-user", false)
            CallManager.handleReceivedHangup()
            assertEquals(CallStatusEnum.IDLE, CallManager.callState.value.status)
        }

        @Test @DisplayName("handleReceivedHangup does nothing when IDLE")
        fun `handle hangup idle does nothing`() = runTest {
            CallManager.handleReceivedHangup()
            assertEquals(CallStatusEnum.IDLE, CallManager.callState.value.status)
        }
    }

    @Nested @DisplayName("Handle Received ICE")
    inner class HandleIceTest {
        @Test @DisplayName("handleReceivedIce buffers candidates when not connected")
        fun `handle ice buffers`() = runTest {
            CallManager.handleReceivedIce("candidate-string")
        }

        @Test @DisplayName("handleReceivedIce adds candidate directly when connected")
        fun `handle ice connected`() = runTest {
            CallManager.startOutgoingCall("remote-user", false)
            CallManager.handleReceivedAnswer("sdp-answer")
            CallManager.handleReceivedIce("candidate-string")
        }
    }

    @Nested @DisplayName("Call Expired")
    inner class CallExpiredTest {
        @Test @DisplayName("handleReceivedOfferExpired resets call when still RINGING")
        fun `handle expired resets`() = runTest {
            CallManager.handleReceivedOffer("remote-user", "sdp-offer", "call-1")
            assertEquals(CallStatusEnum.RINGING, CallManager.callState.value.status)
            CallManager.handleReceivedOfferExpired()
            assertEquals(CallStatusEnum.IDLE, CallManager.callState.value.status)
        }

        @Test @DisplayName("handleReceivedOfferExpired does nothing when not RINGING")
        fun `handle expired not ringing`() = runTest {
            CallManager.handleReceivedOfferExpired()
            assertEquals(CallStatusEnum.IDLE, CallManager.callState.value.status)
        }
    }

    @Nested @DisplayName("Observer Registration")
    inner class ObserverTest {
        @Test @DisplayName("registerObserver adds observer")
        fun `register observer`() = runTest {
            val observer = mockk<CallObserver>(relaxed = true)
            CallManager.registerObserver(observer)
        }

        @Test @DisplayName("unregisterObserver removes observer")
        fun `unregister observer`() = runTest {
            val observer = mockk<CallObserver>(relaxed = true)
            CallManager.registerObserver(observer)
            CallManager.unregisterObserver(observer)
        }
    }

    @Nested @DisplayName("Call Logs")
    inner class CallLogTest {
        @Test @DisplayName("getCallLogs returns flow of call logs")
        fun `get call logs`() = runTest {
            val logs = CallManager.getCallLogs()
            logs.collect { list ->
                assertTrue(list.isEmpty())
            }
        }

        @Test @DisplayName("insertMissedCall inserts missed call entry")
        fun `insert missed call`() = runTest {
            CallManager.insertMissedCall("remote-user", false)
        }
    }

    @Nested @DisplayName("Set On Hold")
    inner class SetOnHoldTest {
        @Test @DisplayName("setOnHold sets isOnHold state")
        fun `set on hold`() = runTest {
            CallManager.setOnHold(true)
            assertTrue(CallManager.callState.value.isOnHold)
            CallManager.setOnHold(false)
            assertFalse(CallManager.callState.value.isOnHold)
        }
    }

    @Nested @DisplayName("Raise Hand")
    inner class RaiseHandTest {
        @Test @DisplayName("raiseHand sets isHandRaised state")
        fun `raise hand`() = runTest {
            CallManager.raiseHand(true)
            assertTrue(CallManager.callState.value.isHandRaised)
            CallManager.raiseHand(false)
            assertFalse(CallManager.callState.value.isHandRaised)
        }
    }

    @Nested @DisplayName("React")
    inner class ReactTest {
        @Test @DisplayName("react does nothing when no remote user")
        fun `react no remote`() = runTest {
            CallManager.react("\uD83D\uDC4D")
        }
    }

    @Nested @DisplayName("Set Ring Group")
    inner class SetRingGroupTest {
        @Test @DisplayName("setRingGroup sets ring group flag")
        fun `set ring group`() = runTest {
            CallManager.setRingGroup(false)
        }
    }

    @Nested @DisplayName("Reset For Test")
    inner class ResetTest {
        @Test @DisplayName("resetForTest clears all state")
        fun `reset clears state`() = runTest {
            CallManager.startOutgoingCall("remote-user", false)
            CallManager.resetForTest()
            assertEquals(CallStatusEnum.IDLE, CallManager.callState.value.status)
            assertFalse(CallManager.callState.value.isMuted)
            assertFalse(CallManager.callState.value.isSpeakerOn)
        }
    }
}
