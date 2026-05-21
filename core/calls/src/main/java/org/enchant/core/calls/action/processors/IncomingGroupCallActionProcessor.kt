package org.enchant.core.calls.action.processors

import android.util.Log
import org.enchant.core.calls.CallLogger
import org.enchant.core.calls.action.BaseActionProcessor
import org.enchant.core.calls.action.CallAction
import org.enchant.core.calls.action.CallPhase
import org.enchant.core.calls.model.CallEndReason
import org.enchant.core.calls.model.CallStatus
import org.enchant.core.calls.model.GroupCallState
import org.enchant.core.calls.observer.CallObserverRegistry
import org.enchant.core.calls.state.CallServiceState

class IncomingGroupCallActionProcessor(
    private val callLogger: CallLogger?,
    private val observerRegistry: CallObserverRegistry?,
    private val groupId: String,
    private val ringId: Long = 0,
    private val ringerUserId: String? = null
) : BaseActionProcessor() {

    override val currentPhase: CallPhase = CallPhase.INCOMING_CALL
    override val tag: String = "IncomingGroupCallActionProcessor"

    override fun handleAcceptIncomingCall(state: CallServiceState, action: CallAction.AcceptIncomingCall): CallServiceState {
        Log.d(tag, "handleAccept: groupId=$groupId, withVideo=${action.withVideo}")

        return state.builder()
            .actionProcessor(GroupCallActionProcessor(callLogger, observerRegistry, groupId))
            .callState(state.callState.copy(
                status = CallStatus.CONNECTING,
                isVideoCall = action.withVideo
            ))
            .groupCallState(GroupCallState.CONNECTING)
            .build()
    }

    override fun handleDenyIncomingCall(state: CallServiceState, action: CallAction.DenyIncomingCall): CallServiceState {
        Log.d(tag, "handleDeny: groupId=$groupId, reason=${action.reason}")

        observerRegistry?.notifyEnded(CallEndReason.HANGUP_LOCAL, null)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.IDLE))
            .groupCallState(GroupCallState.IDLE)
            .build()
    }

    override fun handleGroupCallRingUpdate(state: CallServiceState, action: CallAction.GroupCallRingUpdate): CallServiceState {
        Log.d(tag, "handleGroupCallRingUpdate: fromUserId=${action.fromUserId}")

        if (state.groupCallState == GroupCallState.IDLE) {
            return state.builder()
                .groupCallState(GroupCallState.RINGING)
                .build()
        }
        return state
    }

    override fun handleJoinGroupCall(state: CallServiceState, action: CallAction.JoinGroupCall): CallServiceState {
        Log.d(tag, "handleJoinGroupCall: groupId=${action.groupId}")

        return state.builder()
            .actionProcessor(GroupCallActionProcessor(callLogger, observerRegistry, action.groupId))
            .groupCallState(GroupCallState.CONNECTING)
            .build()
    }

    override fun handleIncomingCallTimeout(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleTimeout: incoming group call timed out")

        observerRegistry?.notifyEnded(CallEndReason.TIMEOUT, null)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.IDLE))
            .groupCallState(GroupCallState.IDLE)
            .build()
    }

    override fun handleCallConnected(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleCallConnected: groupId=$groupId")

        observerRegistry?.notifyConnected()

        return state.builder()
            .actionProcessor(GroupCallActionProcessor(callLogger, observerRegistry, groupId))
            .callState(state.callState.copy(status = CallStatus.CONNECTED))
            .groupCallState(GroupCallState.CONNECTED_AND_JOINED)
            .build()
    }

    override fun handleReceiveHangup(state: CallServiceState, action: CallAction.ReceiveHangup): CallServiceState {
        Log.d(tag, "handleReceiveHangup: reason=${action.reason}")

        observerRegistry?.notifyEnded(CallEndReason.HANGUP_REMOTE, null)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.IDLE))
            .groupCallState(GroupCallState.IDLE)
            .build()
    }
}