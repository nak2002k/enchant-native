package org.enchant.core.calls.action

import org.enchant.core.calls.model.CallQualityStats

sealed class CallAction {

    // ── Outgoing Call Actions ──
    data class StartOutgoingCall(
        val remoteUserId: String,
        val isVideo: Boolean
    ) : CallAction()

    data class CancelOutgoingCall(
        val reason: String? = null
    ) : CallAction()

    // ── Incoming Call Actions ──
    data class ReceiveIncomingOffer(
        val remoteUserId: String,
        val sdp: String,
        val callId: String,
        val isVideo: Boolean
    ) : CallAction()

    data class AcceptIncomingCall(
        val withVideo: Boolean
    ) : CallAction()

    data class DenyIncomingCall(
        val reason: String? = null
    ) : CallAction()

    // ── Signaling Actions ──
    data class ReceiveAnswer(
        val sdp: String
    ) : CallAction()

    data class ReceiveIceCandidate(
        val candidate: String
    ) : CallAction()

    data class ReceiveHangup(
        val reason: String? = null
    ) : CallAction()

    // ── Call Control Actions ──
    data object ToggleMute : CallAction()
    data object ToggleSpeaker : CallAction()
    data object ToggleVideo : CallAction()
    data object FlipCamera : CallAction()
    data class SetOnHold(val hold: Boolean) : CallAction()
    data class RaiseHand(val raised: Boolean) : CallAction()

    // ── Granular Error Reasons (Signal-Style) ──
    data object CallFailedTimeout : CallAction()
    data object CallFailedIce : CallAction()
    data object CallFailedDeclinedElsewhere : CallAction()
    data object CallFailedBusy : CallAction()
    data object CallFailedEndedElsewhere : CallAction()
    data class CallFailedWithReason(val reason: org.enchant.core.calls.model.CallEndReason) : CallAction()

    // ── System Actions ──
    data object CallConnected : CallAction()
    data object CallReconnecting : CallAction()
    data object CallReconnected : CallAction()
    data object CallEnded : CallAction()
    data class QualityUpdate(val stats: CallQualityStats) : CallAction()

    // ── Timeout Actions ──
    data object IncomingCallTimeout : CallAction()
    data object SignalingTimeout : CallAction()

    // ── Group Call Actions ──
    data class JoinGroupCall(
        val groupId: String,
        val callLinkRoomId: String? = null
    ) : CallAction()

    data object LeaveGroupCall : CallAction()

    // ── Hand Raising ──
    data class GroupCallRaisedHand(
        val userId: String,
        val raised: Boolean
    ) : CallAction()

    // ── Reactions ──
    data class SendReaction(
        val emoji: String
    ) : CallAction()

    data class GroupCallReaction(
        val userId: String,
        val emoji: String
    ) : CallAction()

    // ── Moderation ──
    data class RemoteMute(
        val targetUserId: String
    ) : CallAction()

    data class RemoteUnmute(
        val targetUserId: String
    ) : CallAction()

    data class RemoveParticipant(
        val targetUserId: String
    ) : CallAction()

    data class BlockParticipant(
        val targetUserId: String
    ) : CallAction()

    // ── Ring Group ──
    data class SetRingGroup(
        val enabled: Boolean
    ) : CallAction()

    data class GroupCallRingUpdate(
        val fromUserId: String
    ) : CallAction()

    // ── Group State ──
    data class GroupMembersUpdated(
        val participants: List<org.enchant.core.calls.model.CallParticipant>
    ) : CallAction()

    data object GroupCallEnded : CallAction()
}