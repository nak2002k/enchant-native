package org.enchant.core.calls.action

import android.util.Log
import org.enchant.core.calls.state.CallServiceState

interface ActionProcessor {
    fun process(state: CallServiceState, action: CallAction): CallServiceState
    val currentPhase: CallPhase
    val tag: String
}

abstract class BaseActionProcessor : ActionProcessor {

    override fun process(state: CallServiceState, action: CallAction): CallServiceState {
        return when (action) {
            is CallAction.StartOutgoingCall -> handleStartOutgoingCall(state, action)
            is CallAction.CancelOutgoingCall -> handleCancelOutgoingCall(state, action)
            is CallAction.ReceiveIncomingOffer -> handleReceiveIncomingOffer(state, action)
            is CallAction.AcceptIncomingCall -> handleAcceptIncomingCall(state, action)
            is CallAction.DenyIncomingCall -> handleDenyIncomingCall(state, action)
            is CallAction.ReceiveAnswer -> handleReceiveAnswer(state, action)
            is CallAction.ReceiveIceCandidate -> handleReceiveIceCandidate(state, action)
            is CallAction.ReceiveHangup -> handleReceiveHangup(state, action)
            is CallAction.ToggleMute -> handleToggleMute(state)
            is CallAction.ToggleSpeaker -> handleToggleSpeaker(state)
            is CallAction.ToggleVideo -> handleToggleVideo(state)
            is CallAction.FlipCamera -> handleFlipCamera(state)
            is CallAction.SetOnHold -> handleSetOnHold(state, action)
            is CallAction.RaiseHand -> handleRaiseHand(state, action)
            is CallAction.CallConnected -> handleCallConnected(state)
            is CallAction.CallReconnecting -> handleCallReconnecting(state)
            is CallAction.CallReconnected -> handleCallReconnected(state)
            is CallAction.CallEnded -> handleCallEnded(state)
            is CallAction.CallFailedTimeout -> handleCallFailedTimeout(state)
            is CallAction.CallFailedIce -> handleCallFailedIce(state)
            is CallAction.CallFailedDeclinedElsewhere -> handleCallFailedDeclinedElsewhere(state)
            is CallAction.CallFailedBusy -> handleCallFailedBusy(state)
            is CallAction.CallFailedEndedElsewhere -> handleCallFailedEndedElsewhere(state)
            is CallAction.CallFailedWithReason -> handleCallFailedWithReason(state, action)
            is CallAction.QualityUpdate -> handleQualityUpdate(state, action)
            is CallAction.IncomingCallTimeout -> handleIncomingCallTimeout(state)
            is CallAction.SignalingTimeout -> handleSignalingTimeout(state)
            is CallAction.JoinGroupCall -> handleJoinGroupCall(state, action)
            is CallAction.LeaveGroupCall -> handleLeaveGroupCall(state)
            is CallAction.GroupCallRaisedHand -> handleGroupCallRaisedHand(state, action)
            is CallAction.SendReaction -> handleSendReaction(state, action)
            is CallAction.GroupCallReaction -> handleGroupCallReaction(state, action)
            is CallAction.RemoteMute -> handleRemoteMute(state, action)
            is CallAction.RemoteUnmute -> handleRemoteUnmute(state, action)
            is CallAction.RemoveParticipant -> handleRemoveParticipant(state, action)
            is CallAction.BlockParticipant -> handleBlockParticipant(state, action)
            is CallAction.SetRingGroup -> handleSetRingGroup(state, action)
            is CallAction.GroupCallRingUpdate -> handleGroupCallRingUpdate(state, action)
            is CallAction.GroupMembersUpdated -> handleGroupMembersUpdated(state, action)
            is CallAction.GroupCallEnded -> handleGroupCallEnded(state)
            else -> {
                Log.w(tag, "Unhandled action: ${action::class.simpleName}")
                state
            }
        }
    }

    protected open fun handleStartOutgoingCall(state: CallServiceState, action: CallAction.StartOutgoingCall): CallServiceState {
        Log.w(tag, "handleStartOutgoingCall not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleCancelOutgoingCall(state: CallServiceState, action: CallAction.CancelOutgoingCall): CallServiceState {
        Log.w(tag, "handleCancelOutgoingCall not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleReceiveIncomingOffer(state: CallServiceState, action: CallAction.ReceiveIncomingOffer): CallServiceState {
        Log.w(tag, "handleReceiveIncomingOffer not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleAcceptIncomingCall(state: CallServiceState, action: CallAction.AcceptIncomingCall): CallServiceState {
        Log.w(tag, "handleAcceptIncomingCall not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleDenyIncomingCall(state: CallServiceState, action: CallAction.DenyIncomingCall): CallServiceState {
        Log.w(tag, "handleDenyIncomingCall not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleReceiveAnswer(state: CallServiceState, action: CallAction.ReceiveAnswer): CallServiceState {
        Log.w(tag, "handleReceiveAnswer not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleReceiveIceCandidate(state: CallServiceState, action: CallAction.ReceiveIceCandidate): CallServiceState {
        Log.w(tag, "handleReceiveIceCandidate not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleReceiveHangup(state: CallServiceState, action: CallAction.ReceiveHangup): CallServiceState {
        Log.w(tag, "handleReceiveHangup not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleToggleMute(state: CallServiceState): CallServiceState {
        Log.w(tag, "handleToggleMute not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleToggleSpeaker(state: CallServiceState): CallServiceState {
        Log.w(tag, "handleToggleSpeaker not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleToggleVideo(state: CallServiceState): CallServiceState {
        Log.w(tag, "handleToggleVideo not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleFlipCamera(state: CallServiceState): CallServiceState {
        Log.w(tag, "handleFlipCamera not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleSetOnHold(state: CallServiceState, action: CallAction.SetOnHold): CallServiceState {
        Log.w(tag, "handleSetOnHold not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleRaiseHand(state: CallServiceState, action: CallAction.RaiseHand): CallServiceState {
        Log.w(tag, "handleRaiseHand not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleCallConnected(state: CallServiceState): CallServiceState {
        Log.w(tag, "handleCallConnected not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleCallReconnecting(state: CallServiceState): CallServiceState {
        Log.w(tag, "handleCallReconnecting not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleCallReconnected(state: CallServiceState): CallServiceState {
        Log.w(tag, "handleCallReconnected not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleCallEnded(state: CallServiceState): CallServiceState {
        Log.w(tag, "handleCallEnded not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleCallFailedTimeout(state: CallServiceState): CallServiceState {
        Log.w(tag, "handleCallFailedTimeout not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleCallFailedIce(state: CallServiceState): CallServiceState {
        Log.w(tag, "handleCallFailedIce not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleCallFailedDeclinedElsewhere(state: CallServiceState): CallServiceState {
        Log.w(tag, "handleCallFailedDeclinedElsewhere not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleCallFailedBusy(state: CallServiceState): CallServiceState {
        Log.w(tag, "handleCallFailedBusy not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleCallFailedEndedElsewhere(state: CallServiceState): CallServiceState {
        Log.w(tag, "handleCallFailedEndedElsewhere not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleCallFailedWithReason(state: CallServiceState, action: CallAction.CallFailedWithReason): CallServiceState {
        Log.w(tag, "handleCallFailedWithReason not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleQualityUpdate(state: CallServiceState, action: CallAction.QualityUpdate): CallServiceState {
        Log.w(tag, "handleQualityUpdate not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleIncomingCallTimeout(state: CallServiceState): CallServiceState {
        Log.w(tag, "handleIncomingCallTimeout not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleSignalingTimeout(state: CallServiceState): CallServiceState {
        Log.w(tag, "handleSignalingTimeout not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleJoinGroupCall(state: CallServiceState, action: CallAction.JoinGroupCall): CallServiceState {
        Log.w(tag, "handleJoinGroupCall not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleLeaveGroupCall(state: CallServiceState): CallServiceState {
        Log.w(tag, "handleLeaveGroupCall not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleGroupCallRaisedHand(state: CallServiceState, action: CallAction.GroupCallRaisedHand): CallServiceState {
        Log.w(tag, "handleGroupCallRaisedHand not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleSendReaction(state: CallServiceState, action: CallAction.SendReaction): CallServiceState {
        Log.w(tag, "handleSendReaction not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleGroupCallReaction(state: CallServiceState, action: CallAction.GroupCallReaction): CallServiceState {
        Log.w(tag, "handleGroupCallReaction not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleRemoteMute(state: CallServiceState, action: CallAction.RemoteMute): CallServiceState {
        Log.w(tag, "handleRemoteMute not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleRemoteUnmute(state: CallServiceState, action: CallAction.RemoteUnmute): CallServiceState {
        Log.w(tag, "handleRemoteUnmute not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleRemoveParticipant(state: CallServiceState, action: CallAction.RemoveParticipant): CallServiceState {
        Log.w(tag, "handleRemoveParticipant not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleBlockParticipant(state: CallServiceState, action: CallAction.BlockParticipant): CallServiceState {
        Log.w(tag, "handleBlockParticipant not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleSetRingGroup(state: CallServiceState, action: CallAction.SetRingGroup): CallServiceState {
        Log.w(tag, "handleSetRingGroup not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleGroupCallRingUpdate(state: CallServiceState, action: CallAction.GroupCallRingUpdate): CallServiceState {
        Log.w(tag, "handleGroupCallRingUpdate not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleGroupMembersUpdated(state: CallServiceState, action: CallAction.GroupMembersUpdated): CallServiceState {
        Log.w(tag, "handleGroupMembersUpdated not handled in ${this::class.simpleName}")
        return state
    }

    protected open fun handleGroupCallEnded(state: CallServiceState): CallServiceState {
        Log.w(tag, "handleGroupCallEnded not handled in ${this::class.simpleName}")
        return state
    }
}