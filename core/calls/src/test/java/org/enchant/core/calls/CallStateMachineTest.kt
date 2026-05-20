package org.enchant.core.calls

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.enchant.core.calls.model.CallStatus
import org.enchant.core.calls.model.CallDirection

@DisplayName("CallStateMachine")
class CallStateMachineTest {

    private lateinit var sm: CallStateMachine

    @BeforeEach
    fun setUp() {
        sm = CallStateMachine()
    }

    @Nested @DisplayName("initial state")
    inner class InitialState {
        @Test fun `starts as IDLE`() {
            assertEquals(CallStatus.IDLE, sm.state.value.status)
        }

        @Test fun `remoteUserId is null`() {
            assertNull(sm.state.value.remoteUserId)
        }

        @Test fun `duration is 0`() {
            assertEquals(0, sm.state.value.durationSeconds)
        }
    }

    @Nested @DisplayName("outgoing call flow")
    inner class OutgoingCall {
        @Test fun `IDLE to CALLING`() {
            assertTrue(sm.startOutgoing("user1", false, "call-1"))
            assertEquals(CallStatus.CALLING, sm.state.value.status)
            assertEquals("user1", sm.state.value.remoteUserId)
            assertEquals(CallDirection.OUTGOING, sm.state.value.direction)
        }

        @Test fun `CALLING to CONNECTING`() {
            sm.startOutgoing("user1", false, "call-1")
            assertTrue(sm.setConnecting())
            assertEquals(CallStatus.CONNECTING, sm.state.value.status)
        }

        @Test fun `CONNECTING to CONNECTED`() {
            sm.startOutgoing("user1", false, "call-1")
            sm.setConnecting()
            assertTrue(sm.setConnected())
            assertEquals(CallStatus.CONNECTED, sm.state.value.status)
        }

        @Test fun `cannot start call when already in call`() {
            sm.startOutgoing("user1", false, "call-1")
            assertFalse(sm.startOutgoing("user2", false, "call-2"))
        }
    }

    @Nested @DisplayName("incoming call flow")
    inner class IncomingCall {
        @Test fun `IDLE to RINGING`() {
            assertTrue(sm.receiveIncoming("user1", true, "call-1"))
            assertEquals(CallStatus.RINGING, sm.state.value.status)
            assertEquals(CallDirection.INCOMING, sm.state.value.direction)
        }

        @Test fun `RINGING to CONNECTING (accept)`() {
            sm.receiveIncoming("user1", true, "call-1")
            assertTrue(sm.acceptCall())
            assertEquals(CallStatus.CONNECTING, sm.state.value.status)
        }

        @Test fun `RINGING to IDLE (deny)`() {
            sm.receiveIncoming("user1", true, "call-1")
            assertTrue(sm.denyCall())
            assertEquals(CallStatus.IDLE, sm.state.value.status)
        }

        @Test fun `cannot receive call when busy`() {
            sm.startOutgoing("user1", false, "call-1")
            assertFalse(sm.receiveIncoming("user2", false, "call-2"))
        }
    }

    @Nested @DisplayName("call controls")
    inner class Controls {
        @Test fun `toggleMute flips state`() {
            assertFalse(sm.state.value.isMuted)
            sm.toggleMute()
            assertTrue(sm.state.value.isMuted)
            sm.toggleMute()
            assertFalse(sm.state.value.isMuted)
        }

        @Test fun `toggleSpeaker flips state`() {
            assertFalse(sm.state.value.isSpeakerOn)
            sm.toggleSpeaker()
            assertTrue(sm.state.value.isSpeakerOn)
        }

        @Test fun `updateDuration increments`() {
            sm.updateDuration(5)
            assertEquals(5, sm.state.value.durationSeconds)
            sm.updateDuration(10)
            assertEquals(10, sm.state.value.durationSeconds)
        }
    }

    @Nested @DisplayName("end call")
    inner class EndCall {
        @Test fun `endCall returns previous state`() {
            sm.startOutgoing("user1", false, "call-1")
            val previous = sm.endCall()
            assertEquals(CallStatus.CALLING, previous.status)
            assertEquals(CallStatus.IDLE, sm.state.value.status)
        }

        @Test fun `endCall from IDLE returns IDLE`() {
            val previous = sm.endCall()
            assertEquals(CallStatus.IDLE, previous.status)
        }
    }

    @Nested @DisplayName("reconnect flow")
    inner class Reconnect {
        @Test fun `CONNECTED to RECONNECTING to CONNECTED`() {
            sm.startOutgoing("user1", false, "call-1")
            sm.setConnecting()
            sm.setConnected()
            assertTrue(sm.setReconnecting())
            assertEquals(CallStatus.RECONNECTING, sm.state.value.status)
            assertTrue(sm.setReconnected())
            assertEquals(CallStatus.CONNECTED, sm.state.value.status)
        }

        @Test fun `cannot reconnect from IDLE`() {
            assertFalse(sm.setReconnecting())
        }
    }
}