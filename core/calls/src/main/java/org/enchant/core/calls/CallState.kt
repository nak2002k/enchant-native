package org.enchant.core.calls

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CallStatusEnum { IDLE, PRE_JOIN, CALLING, RINGING, CONNECTING, CONNECTED, RECONNECTING, ENDED }

enum class CallDirection { INCOMING, OUTGOING }

enum class CallType { AUDIO, VIDEO, GROUP_AUDIO, GROUP_VIDEO }

enum class CallStatus { MISSED, ANSWERED, CANCELLED, OUTGOING }

enum class CallEndReason { HANGUP_LOCAL, HANGUP_REMOTE, ANSWERED_ELSEWHERE, BUSY, TIMEOUT, ERROR }

enum class CallLogFilter { ALL, MISSED, OUTGOING, INCOMING }

enum class AudioDevice { EARPIECE, SPEAKER, BLUETOOTH, WIRED_HEADSET }

enum class SignalStrength { GOOD, FAIR, POOR, NONE }

data class CallState(
    val status: CallStatusEnum = CallStatusEnum.IDLE,
    val remoteUserId: String? = null,
    val remoteName: String? = null,
    val callId: String? = null,
    val isVideoCall: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isOnHold: Boolean = false,
    val isHandRaised: Boolean = false,
    val durationSeconds: Int = 0,
    val signalStrength: SignalStrength? = null,
    val error: String? = null,
    val direction: CallDirection = CallDirection.OUTGOING
)

data class CallLogEntry(
    val callId: String,
    val remoteUserId: String,
    val remoteName: String? = null,
    val type: CallType,
    val direction: CallDirection,
    val status: CallStatus,
    val durationSeconds: Int,
    val timestamp: Long
)

data class StagedDeletion(val count: Int, val callIds: List<String>)

data class PeekInfo(
    val activeParticipants: Int,
    val maxParticipants: Int,
    val isActive: Boolean
)

data class CallLinkData(
    val roomId: String,
    val name: String,
    val creatorId: String,
    val restrictions: CallLinkRestrictions,
    val isActive: Boolean
)

enum class CallLinkRestrictions { ANYONE, APPROVAL_REQUIRED, CONTACTS_ONLY }

data class CallLinkCredentials(
    val roomId: String,
    val authToken: String,
    val iceServers: List<IceServer>
)

data class IceServer(val urls: List<String>, val username: String? = null, val credential: String? = null)

data class CallSummary(val durationSeconds: Int, val wasVideoCall: Boolean, val wasOutgoing: Boolean)
