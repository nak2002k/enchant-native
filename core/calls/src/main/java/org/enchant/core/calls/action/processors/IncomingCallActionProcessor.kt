package org.enchant.core.calls.action.processors

import android.util.Log
import org.enchant.core.calls.CallLogger
import org.enchant.core.calls.action.BaseActionProcessor
import org.enchant.core.calls.action.CallAction
import org.enchant.core.calls.action.CallPhase
import org.enchant.core.calls.model.CallEndReason
import org.enchant.core.calls.model.CallStatus
import org.enchant.core.calls.observer.CallObserverRegistry
import org.enchant.core.calls.state.CallServiceState

class IncomingCallActionProcessor(
    private val callLogger: CallLogger?,
    private val observerRegistry: CallObserverRegistry?,
    private val remoteUserId: String,
    private val isVideo: Boolean
) : BaseActionProcessor() {

    override val currentPhase: CallPhase = CallPhase.INCOMING_CALL
    override val tag: String = "IncomingCallActionProcessor"

    override fun handleAcceptIncomingCall(state: CallServiceState, action: CallAction.AcceptIncomingCall): CallServiceState {
        Log.d(tag, "handleAccept: remoteUserId=$remoteUserId, withVideo=${action.withVideo}")

        val newState = state.callState.copy(
            status = CallStatus.CONNECTING,
            isVideoCall = action.withVideo
        )

        return state.builder()
            .callState(newState)
            .build()
    }

    override fun handleDenyIncomingCall(state: CallServiceState, action: CallAction.DenyIncomingCall): CallServiceState {
        Log.d(tag, "handleDeny: reason=${action.reason}")

        observerRegistry?.notifyEnded(CallEndReason.HANGUP_LOCAL, null)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.IDLE))
            .callSetupData(null)
            .build()
    }

    override fun handleIncomingCallTimeout(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleTimeout: incoming call timed out")

        observerRegistry?.notifyEnded(CallEndReason.TIMEOUT, null)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.IDLE))
            .callSetupData(null)
            .build()
    }

    override fun handleCallConnected(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleCallConnected: remoteUserId=$remoteUserId")

        val newState = state.callState.copy(status = CallStatus.CONNECTED)

        observerRegistry?.notifyConnected()

        return state.builder()
            .actionProcessor(ConnectedCallActionProcessor(callLogger, observerRegistry, remoteUserId))
            .callState(newState)
            .callSetupData(null)
            .build()
    }

    override fun handleReceiveHangup(state: CallServiceState, action: CallAction.ReceiveHangup): CallServiceState {
        Log.d(tag, "handleReceiveHangup: reason=${action.reason}")

        observerRegistry?.notifyEnded(CallEndReason.HANGUP_REMOTE, null)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.IDLE))
            .callSetupData(null)
            .build()
    }
}