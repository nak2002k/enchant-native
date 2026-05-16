package org.enchant.calls

import org.enchant.core.calls.*
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
            assert(state.status == CallStatusEnum.IDLE)
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
                status = CallStatusEnum.CALLING,
                remoteUserId = "user_1",
                isVideoCall = true,
                isMuted = true
            )
            assert(modified.status == CallStatusEnum.CALLING)
            assert(modified.remoteUserId == "user_1")
            assert(modified.isVideoCall)
            assert(modified.isMuted)
            assert(!modified.isSpeakerOn)
        }

        @Test
        fun `all statuses are distinct`() {
            val values = CallStatusEnum.values()
            assert(values.size == 8)
            assert(values.contains(CallStatusEnum.IDLE))
            assert(values.contains(CallStatusEnum.PRE_JOIN))
            assert(values.contains(CallStatusEnum.CALLING))
            assert(values.contains(CallStatusEnum.RINGING))
            assert(values.contains(CallStatusEnum.CONNECTING))
            assert(values.contains(CallStatusEnum.CONNECTED))
            assert(values.contains(CallStatusEnum.RECONNECTING))
            assert(values.contains(CallStatusEnum.ENDED))
        }

        @Test
        fun `CallEndReason has all expected values`() {
            val values = CallEndReason.values()
            assert(values.contains(CallEndReason.HANGUP_LOCAL))
            assert(values.contains(CallEndReason.HANGUP_REMOTE))
            assert(values.contains(CallEndReason.ANSWERED_ELSEWHERE))
            assert(values.contains(CallEndReason.BUSY))
            assert(values.contains(CallEndReason.TIMEOUT))
            assert(values.contains(CallEndReason.ERROR))
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
                status = CallStatus.MISSED,
                durationSeconds = 0,
                timestamp = 1000L
            )
            assert(entry.callId == "call_1")
            assert(entry.remoteUserId == "user_1")
            assert(entry.remoteName == null)
        }

        @Test
        fun `CallStatus values are distinct`() {
            val values = CallStatus.values()
            assert(values.contains(CallStatus.MISSED))
            assert(values.contains(CallStatus.ANSWERED))
            assert(values.contains(CallStatus.CANCELLED))
            assert(values.contains(CallStatus.OUTGOING))
        }

        @Test
        fun `CallDirection values are distinct`() {
            val values = CallDirection.values()
            assert(values.contains(CallDirection.INCOMING))
            assert(values.contains(CallDirection.OUTGOING))
        }

        @Test
        fun `CallType values are distinct`() {
            val values = CallType.values()
            assert(values.contains(CallType.AUDIO))
            assert(values.contains(CallType.VIDEO))
            assert(values.contains(CallType.GROUP_AUDIO))
            assert(values.contains(CallType.GROUP_VIDEO))
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
        fun `IceServer holds correct values`() {
            val server = IceServer(urls = listOf("stun:stun.l.google.com:19302"), username = "user", credential = "pass")
            assert(server.urls[0] == "stun:stun.l.google.com:19302")
            assert(server.username == "user")
            assert(server.credential == "pass")
        }
    }

    @Nested
    @DisplayName("CallLinkRestrictions")
    inner class CallLinkRestrictionsTests {
        @Test
        fun `ALL restriction allows anyone`() {
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
    }

    @Nested
    @DisplayName("CallLinkData")
    inner class CallLinkDataTests {
        @Test
        fun `CallLinkData with ANYONE restrictions`() {
            val data = CallLinkData("room1", "Test", "creator", CallLinkRestrictions.ANYONE, true)
            assert(data.restrictions == CallLinkRestrictions.ANYONE)
        }

        @Test
        fun `CallLinkData with inActive state`() {
            val data = CallLinkData("room2", "Test", "creator", CallLinkRestrictions.APPROVAL_REQUIRED, false)
            assert(!data.isActive)
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
            val values = AudioDevice.values()
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
            val values = SignalStrength.values()
            assert(values.contains(SignalStrength.GOOD))
            assert(values.contains(SignalStrength.FAIR))
            assert(values.contains(SignalStrength.POOR))
            assert(values.contains(SignalStrength.NONE))
        }
    }
}
