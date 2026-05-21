package org.enchant.calls

import org.enchant.core.calls.CallDirection
import org.enchant.core.calls.CallEndReason
import org.enchant.core.calls.CallLinkCredentials
import org.enchant.core.calls.CallLinkData
import org.enchant.core.calls.CallLinkRestrictions
import org.enchant.core.calls.CallLogEntry
import org.enchant.core.calls.CallLogFilter
import org.enchant.core.calls.CallState
import org.enchant.core.calls.CallStatus
import org.enchant.core.calls.CallSummary
import org.enchant.core.calls.CallType
import org.enchant.core.calls.IceServer
import org.enchant.core.calls.PeekInfo
import org.enchant.core.calls.SignalStrength
import org.enchant.core.calls.StagedDeletion
import org.enchant.core.calls.AudioDevice
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CallState")
class CallStateTest {

    @Nested
    @DisplayName("CallState")
    inner class CallStateTests {
        @Test
        fun `default state is IDLE`() {
            val state = CallState()
            assert(state.status == CallStatus.IDLE)
        }

        @Test
        fun `default fields are false or null`() {
            val state = CallState()
            assert(!state.isVideoCall)
            assert(!state.isMuted)
            assert(!state.isSpeakerOn)
            assert(!state.isOnHold)
            assert(!state.isHandRaised)
            assert(state.durationSeconds == 0)
            assert(state.remoteUserId == null)
            assert(state.callId == null)
            assert(state.error == null)
            assert(state.signalStrength == null)
        }

        @Test
        fun `copy with modifications works`() {
            val original = CallState()
            val modified = original.copy(
                status = CallStatus.CALLING,
                remoteUserId = "user_1",
                isVideoCall = true,
                isMuted = true
            )
            assert(modified.status == CallStatus.CALLING)
            assert(modified.remoteUserId == "user_1")
            assert(modified.isVideoCall)
            assert(modified.isMuted)
            assert(!modified.isSpeakerOn)
        }

        @Test
        fun `all CallStatus values are distinct`() {
            val values = CallStatus.entries.toTypedArray()
            assert(values.size == 7)
            assert(values.contains(CallStatus.IDLE))
            assert(values.contains(CallStatus.CALLING))
            assert(values.contains(CallStatus.RINGING))
            assert(values.contains(CallStatus.CONNECTING))
            assert(values.contains(CallStatus.CONNECTED))
            assert(values.contains(CallStatus.RECONNECTING))
            assert(values.contains(CallStatus.ENDED))
        }

        @Test
        fun `CallEndReason has all expected values`() {
            val values = CallEndReason.entries.toTypedArray()
            assert(values.contains(CallEndReason.HANGUP_LOCAL))
            assert(values.contains(CallEndReason.HANGUP_REMOTE))
            assert(values.contains(CallEndReason.ANSWERED_ELSEWHERE))
            assert(values.contains(CallEndReason.BUSY))
            assert(values.contains(CallEndReason.TIMEOUT))
            assert(values.contains(CallEndReason.ERROR))
            assert(values.contains(CallEndReason.NETWORK_LOST))
        }
    }

    @Nested
    @DisplayName("CallLogEntry")
    inner class CallLogEntryTests {
        @Test
        fun `constructor sets all fields`() {
            val entry = CallLogEntry(
                callId = "call_1",
                remoteUserId = "user_1",
                type = CallType.AUDIO,
                direction = CallDirection.INCOMING,
                status = CallEndReason.BUSY,
                durationSeconds = 0,
                timestamp = 1000L
            )
            assert(entry.callId == "call_1")
            assert(entry.remoteUserId == "user_1")
            assert(entry.status == CallEndReason.BUSY)
        }

        @Test
        fun `CallLogEntry with video call type`() {
            val entry = CallLogEntry(
                callId = "call_2",
                remoteUserId = "user_2",
                type = CallType.VIDEO,
                direction = CallDirection.OUTGOING,
                status = CallEndReason.HANGUP_LOCAL,
                durationSeconds = 60,
                timestamp = 2000L
            )
            assert(entry.type == CallType.VIDEO)
            assert(entry.direction == CallDirection.OUTGOING)
            assert(entry.durationSeconds == 60)
        }
    }

    @Nested
    @DisplayName("CallLogFilter")
    inner class CallLogFilterTests {
        @Test
        fun `all filter values are present`() {
            val values = CallLogFilter.entries.toTypedArray()
            assert(values.size == 4)
            assert(values.contains(CallLogFilter.ALL))
            assert(values.contains(CallLogFilter.MISSED))
            assert(values.contains(CallLogFilter.OUTGOING))
            assert(values.contains(CallLogFilter.INCOMING))
        }
    }

    @Nested
    @DisplayName("ICustomData")
    inner class CustomDataTests {
        @Test
        fun `StagedDeletion holds correct values`() {
            val staged = StagedDeletion(count = 3, callIds = listOf("id1", "id2", "id3"))
            assert(staged.count == 3)
            assert(staged.callIds.size == 3)
            assert(staged.callIds[0] == "id1")
        }

        @Test
        fun `StagedDeletion with empty list`() {
            val staged = StagedDeletion(count = 0, callIds = emptyList())
            assert(staged.count == 0)
            assert(staged.callIds.isEmpty())
        }

        @Test
        fun `CallSummary holds correct values`() {
            val summary = CallSummary(durationSeconds = 120, wasVideoCall = true, wasOutgoing = false)
            assert(summary.durationSeconds == 120)
            assert(summary.wasVideoCall)
            assert(!summary.wasOutgoing)
        }

        @Test
        fun `PeekInfo holds correct values`() {
            val peek = PeekInfo(activeParticipants = 5, maxParticipants = 500, isActive = true)
            assert(peek.activeParticipants == 5)
            assert(peek.maxParticipants == 500)
            assert(peek.isActive)
        }

        @Test
        fun `PeekInfo inactive call`() {
            val peek = PeekInfo(activeParticipants = 0, maxParticipants = 500, isActive = false)
            assert(!peek.isActive)
            assert(peek.activeParticipants == 0)
        }

        @Test
        fun `IceServer holds correct values`() {
            val server = IceServer(urls = listOf("stun:stun.l.google.com:19302"), username = "user", credential = "pass")
            assert(server.urls[0] == "stun:stun.l.google.com:19302")
            assert(server.username == "user")
            assert(server.credential == "pass")
        }

        @Test
        fun `IceServer without credentials`() {
            val server = IceServer(urls = listOf("stun:stun.example.com:3478"))
            assert(server.username == null)
            assert(server.credential == null)
            assert(server.urls.size == 1)
        }
    }

    @Nested
    @DisplayName("CallLinkRestrictions")
    inner class CallLinkRestrictionsTests {
        @Test
        fun `ANYONE restriction allows anyone`() {
            assert(CallLinkRestrictions.ANYONE.name == "ANYONE")
        }

        @Test
        fun `APPROVAL_REQUIRED needs approval`() {
            assert(CallLinkRestrictions.APPROVAL_REQUIRED.name == "APPROVAL_REQUIRED")
        }

        @Test
        fun `CONTACTS_ONLY limits to contacts`() {
            assert(CallLinkRestrictions.CONTACTS_ONLY.name == "CONTACTS_ONLY")
        }

        @Test
        fun `all restriction values present`() {
            val values = CallLinkRestrictions.entries.toTypedArray()
            assert(values.size == 3)
        }
    }

    @Nested
    @DisplayName("CallLinkData")
    inner class CallLinkDataTests {
        @Test
        fun `CallLinkData with ANYONE restrictions`() {
            val data = CallLinkData("room1", "Test", "creator", CallLinkRestrictions.ANYONE, true)
            assert(data.restrictions == CallLinkRestrictions.ANYONE)
            assert(data.isActive)
            assert(data.roomId == "room1")
            assert(data.name == "Test")
        }

        @Test
        fun `CallLinkData with inActive state`() {
            val data = CallLinkData("room2", "Test", "creator", CallLinkRestrictions.APPROVAL_REQUIRED, false)
            assert(!data.isActive)
            assert(data.restrictions == CallLinkRestrictions.APPROVAL_REQUIRED)
        }
    }

    @Nested
    @DisplayName("CallLinkCredentials")
    inner class CallLinkCredentialsTests {
        @Test
        fun `CallLinkCredentials holds correct values`() {
            val credentials = CallLinkCredentials(
                roomId = "room_1",
                authToken = "token_abc",
                iceServers = listOf(IceServer(urls = listOf("stun:stun.example.com:3478")))
            )
            assert(credentials.roomId == "room_1")
            assert(credentials.authToken == "token_abc")
            assert(credentials.iceServers.size == 1)
        }

        @Test
        fun `CallLinkCredentials with empty token`() {
            val credentials = CallLinkCredentials("room_2", "", emptyList())
            assert(credentials.authToken.isEmpty())
            assert(credentials.iceServers.isEmpty())
        }
    }

    @Nested
    @DisplayName("AudioDevice")
    inner class AudioDeviceTests {
        @Test
        fun `all audio device types present`() {
            val values = AudioDevice.entries.toTypedArray()
            assert(values.size == 4)
            assert(values.contains(AudioDevice.EARPIECE))
            assert(values.contains(AudioDevice.SPEAKER))
            assert(values.contains(AudioDevice.BLUETOOTH))
            assert(values.contains(AudioDevice.WIRED_HEADSET))
        }
    }

    @Nested
    @DisplayName("SignalStrength")
    inner class SignalStrengthTests {
        @Test
        fun `all signal strengths present`() {
            val values = SignalStrength.entries.toTypedArray()
            assert(values.size == 4)
            assert(values.contains(SignalStrength.GOOD))
            assert(values.contains(SignalStrength.FAIR))
            assert(values.contains(SignalStrength.POOR))
            assert(values.contains(SignalStrength.NONE))
        }
    }
}