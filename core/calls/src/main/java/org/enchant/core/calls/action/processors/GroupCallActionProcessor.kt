package org.enchant.core.calls.action.processors

import android.util.Log
import org.enchant.core.calls.CallLogger
import org.enchant.core.calls.action.BaseActionProcessor
import org.enchant.core.calls.action.CallAction
import org.enchant.core.calls.action.CallPhase
import org.enchant.core.calls.model.CallEndReason
import org.enchant.core.calls.observer.CallObserverRegistry
import org.enchant.core.calls.state.CallServiceState

class GroupCallActionProcessor(
    private val callLogger: CallLogger?,
    private val observerRegistry: CallObserverRegistry?,
    private val groupId: String,
    private val isCallLink: Boolean = false
) : BaseActionProcessor() {

    override val currentPhase: CallPhase = CallPhase.GROUP_CONNECTED
    override val tag: String = "GroupCallActionProcessor"

    override fun handleJoinGroupCall(state: CallServiceState, action: CallAction.JoinGroupCall): CallServiceState {
        Log.d(tag, "handleJoinGroupCall: groupId=${action.groupId}, callLinkRoomId=${action.callLinkRoomId}")

        return state.builder()
            .groupCallState(org.enchant.core.calls.model.GroupCallState.CONNECTING)
            .build()
    }

    override fun handleLeaveGroupCall(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleLeaveGroupCall")

        observerRegistry?.notifyEnded(CallEndReason.HANGUP_LOCAL, null)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = org.enchant.core.calls.model.CallStatus.IDLE))
            .groupCallState(org.enchant.core.calls.model.GroupCallState.IDLE)
            .groupCallParticipants(emptyList())
            .build()
    }

    override fun handleGroupCallRaisedHand(state: CallServiceState, action: CallAction.GroupCallRaisedHand): CallServiceState {
        Log.d(tag, "handleGroupCallRaisedHand: userId=${action.userId}, raised=${action.raised}")

        val updatedParticipants = if (action.raised) {
            state.groupCallParticipants.map { participant ->
                if (participant.userId == action.userId) {
                    participant.copy(isHandRaised = true, handRaisedTimestamp = System.currentTimeMillis())
                } else participant
            }
        } else {
            state.groupCallParticipants.map { participant ->
                if (participant.userId == action.userId) {
                    participant.copy(isHandRaised = false, handRaisedTimestamp = 0)
                } else participant
            }
        }

        return state.builder()
            .groupCallParticipants(updatedParticipants)
            .build()
    }

    override fun handleSendReaction(state: CallServiceState, action: CallAction.SendReaction): CallServiceState {
        Log.d(tag, "handleSendReaction: emoji=${action.emoji}")
        return state
    }

    override fun handleGroupCallReaction(state: CallServiceState, action: CallAction.GroupCallReaction): CallServiceState {
        Log.d(tag, "handleGroupCallReaction: userId=${action.userId}, emoji=${action.emoji}")
        return state
    }

    override fun handleRemoteMute(state: CallServiceState, action: CallAction.RemoteMute): CallServiceState {
        Log.d(tag, "handleRemoteMute: targetUserId=${action.targetUserId}")

        if (action.targetUserId == state.callState.remoteUserId) {
            val newDeviceState = state.localDeviceState.copy(isMuted = true)
            return state.builder()
                .localDeviceState(newDeviceState)
                .build()
        }
        return state
    }

    override fun handleRemoteUnmute(state: CallServiceState, action: CallAction.RemoteUnmute): CallServiceState {
        Log.d(tag, "handleRemoteUnmute: targetUserId=${action.targetUserId}")

        if (action.targetUserId == state.callState.remoteUserId) {
            val newDeviceState = state.localDeviceState.copy(isMuted = false)
            return state.builder()
                .localDeviceState(newDeviceState)
                .build()
        }
        return state
    }

    override fun handleRemoveParticipant(state: CallServiceState, action: CallAction.RemoveParticipant): CallServiceState {
        Log.d(tag, "handleRemoveParticipant: targetUserId=${action.targetUserId}")

        val updatedParticipants = state.groupCallParticipants.filter { it.userId != action.targetUserId }

        return state.builder()
            .groupCallParticipants(updatedParticipants)
            .build()
    }

    override fun handleBlockParticipant(state: CallServiceState, action: CallAction.BlockParticipant): CallServiceState {
        Log.d(tag, "handleBlockParticipant: targetUserId=${action.targetUserId}")

        val updatedParticipants = state.groupCallParticipants.filter { it.userId != action.targetUserId }

        return state.builder()
            .groupCallParticipants(updatedParticipants)
            .build()
    }

    override fun handleSetRingGroup(state: CallServiceState, action: CallAction.SetRingGroup): CallServiceState {
        Log.d(tag, "handleSetRingGroup: enabled=${action.enabled}")
        return state
    }

    override fun handleGroupCallRingUpdate(state: CallServiceState, action: CallAction.GroupCallRingUpdate): CallServiceState {
        Log.d(tag, "handleGroupCallRingUpdate: fromUserId=${action.fromUserId}")
        return state
    }

    override fun handleGroupMembersUpdated(state: CallServiceState, action: CallAction.GroupMembersUpdated): CallServiceState {
        Log.d(tag, "handleGroupMembersUpdated: ${action.participants.size} members")

        val newParticipants = action.participants.map { participant ->
            org.enchant.core.calls.model.GroupCallParticipant(
                userId = participant.userId,
                demuxId = 0,
                isAudioMuted = participant.isMuted,
                isVideoMuted = !participant.isVideoOn,
                isHandRaised = participant.hasRaisedHand,
                handRaisedTimestamp = if (participant.hasRaisedHand) System.currentTimeMillis() else 0
            )
        }

        return state.builder()
            .groupCallParticipants(newParticipants)
            .build()
    }

    override fun handleGroupCallEnded(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleGroupCallEnded")

        observerRegistry?.notifyEnded(CallEndReason.HANGUP_LOCAL, null)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = org.enchant.core.calls.model.CallStatus.IDLE))
            .groupCallState(org.enchant.core.calls.model.GroupCallState.DISCONNECTED)
            .build()
    }

    override fun handleToggleMute(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleToggleMute")
        val newDeviceState = state.localDeviceState.copy(isMuted = !state.localDeviceState.isMuted)
        return state.builder()
            .localDeviceState(newDeviceState)
            .build()
    }

    override fun handleToggleSpeaker(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleToggleSpeaker")
        val newDeviceState = state.localDeviceState.copy(isSpeakerOn = !state.localDeviceState.isSpeakerOn)
        return state.builder()
            .localDeviceState(newDeviceState)
            .build()
    }

    override fun handleToggleVideo(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleToggleVideo")
        val newDeviceState = state.localDeviceState.copy(isVideoEnabled = !state.localDeviceState.isVideoEnabled)
        return state.builder()
            .localDeviceState(newDeviceState)
            .build()
    }

    override fun handleRaiseHand(state: CallServiceState, action: CallAction.RaiseHand): CallServiceState {
        Log.d(tag, "handleRaiseHand: raised=${action.raised}")
        val newDeviceState = state.localDeviceState.copy(
            handRaisedTimestamp = if (action.raised) System.currentTimeMillis() else 0
        )
        return state.builder()
            .localDeviceState(newDeviceState)
            .build()
    }

    override fun handleCallEnded(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleCallEnded")

        observerRegistry?.notifyEnded(CallEndReason.HANGUP_LOCAL, null)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = org.enchant.core.calls.model.CallStatus.IDLE))
            .groupCallState(org.enchant.core.calls.model.GroupCallState.IDLE)
            .build()
    }

    override fun handleCallReconnecting(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleCallReconnecting")
        return state.builder()
            .callState(state.callState.copy(status = org.enchant.core.calls.model.CallStatus.RECONNECTING))
            .groupCallState(org.enchant.core.calls.model.GroupCallState.RECONNECTING)
            .build()
    }

    override fun handleCallReconnected(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleCallReconnected")
        return state.builder()
            .callState(state.callState.copy(status = org.enchant.core.calls.model.CallStatus.CONNECTED))
            .groupCallState(org.enchant.core.calls.model.GroupCallState.CONNECTED_AND_JOINED)
            .build()
    }

    override fun handleQualityUpdate(state: CallServiceState, action: CallAction.QualityUpdate): CallServiceState {
        return state.builder()
            .qualityStats(action.stats)
            .build()
    }
}