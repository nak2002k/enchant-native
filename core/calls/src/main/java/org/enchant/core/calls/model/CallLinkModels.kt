package org.enchant.core.calls.model

enum class CallLinkRestrictions {
    ANYONE,
    APPROVAL_REQUIRED,
    CONTACTS_ONLY
}

data class CallLinkData(
    val roomId: String,
    val name: String,
    val creatorId: String,
    val restrictions: CallLinkRestrictions,
    val isActive: Boolean
)

data class CallLinkCredentials(
    val roomId: String,
    val authToken: String,
    val iceServers: List<IceServer>
)

data class CallParticipant(
    val userId: String,
    val displayName: String,
    val isMuted: Boolean,
    val isVideoOn: Boolean,
    val hasRaisedHand: Boolean,
    val isAdmin: Boolean = false
)

data class GroupCallState(
    val callId: String,
    val eraId: String,
    val groupId: String,
    val isJoined: Boolean = false,
    val participants: List<CallParticipant> = emptyList(),
    val localHandRaised: Boolean = false,
    val localReaction: String? = null,
    val isRinging: Boolean = false,
    val isCallFull: Boolean = false
) {
    val activeParticipantCount: Int get() = participants.count { !it.isMuted }
}