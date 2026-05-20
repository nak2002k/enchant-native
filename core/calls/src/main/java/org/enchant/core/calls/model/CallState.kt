package org.enchant.core.calls.model

enum class CallStatus {
    IDLE, CALLING, RINGING, CONNECTING, CONNECTED, RECONNECTING, ENDED
}

enum class CallDirection { INCOMING, OUTGOING }

enum class CallType { AUDIO, VIDEO, GROUP_AUDIO, GROUP_VIDEO }

enum class CallEndReason {
    HANGUP_LOCAL, HANGUP_REMOTE, ANSWERED_ELSEWHERE, BUSY, TIMEOUT, ERROR, NETWORK_LOST
}

enum class AudioDevice { EARPIECE, SPEAKER, BLUETOOTH, WIRED_HEADSET }

enum class SignalStrength { GOOD, FAIR, POOR, NONE }

data class CallState(
    val status: CallStatus = CallStatus.IDLE,
    val remoteUserId: String? = null,
    val remoteName: String? = null,
    val callId: String? = null,
    val isVideoCall: Boolean = false,
    val isMuted: Boolean = false,
    val isVideoEnabled: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isOnHold: Boolean = false,
    val isHandRaised: Boolean = false,
    val durationSeconds: Int = 0,
    val signalStrength: SignalStrength? = null,
    val error: String? = null,
    val direction: CallDirection = CallDirection.OUTGOING
) {
    companion object {
        fun idle() = CallState()
    }
}

data class CallLogEntry(
    val callId: String,
    val remoteUserId: String,
    val remoteName: String? = null,
    val type: CallType,
    val direction: CallDirection,
    val status: CallEndReason,
    val durationSeconds: Int,
    val timestamp: Long
)

data class CallSummary(
    val durationSeconds: Int,
    val wasVideoCall: Boolean,
    val wasOutgoing: Boolean
)

data class PeekInfo(
    val activeParticipants: Int,
    val maxParticipants: Int,
    val isActive: Boolean
)

data class IceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

data class CallQualityStats(
    val rttMs: Long = 0,
    val packetsLost: Int = 0,
    val jitterMs: Long = 0,
    val bytesReceived: Long = 0,
    val bytesSent: Long = 0
)