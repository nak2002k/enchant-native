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

class OutgoingCallActionProcessor(
    private val callLogger: CallLogger?,
    private val observerRegistry: CallObserverRegistry?,
    private val remoteUserId: String,
    private val isVideo: Boolean
) : BaseActionProcessor() {

    override val currentPhase: CallPhase = CallPhase.OUTGOING_CALL
    override val tag: String = "OutgoingCallActionProcessor"

    override fun handleReceiveAnswer(state: CallServiceState, action: CallAction.ReceiveAnswer): CallServiceState {
        Log.d(tag, "handleReceiveAnswer: remoteUserId=$remoteUserId")

        val newState = state.callState.copy(status = CallStatus.CONNECTING)
        val setupData = state.callSetupData?.copy(answerSdp = action.sdp)

        return state.builder()
            .callState(newState)
            .callSetupData(setupData)
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

    override fun handleCancelOutgoingCall(state: CallServiceState, action: CallAction.CancelOutgoingCall): CallServiceState {
        Log.d(tag, "handleCancelOutgoingCall: reason=${action.reason}")

        observerRegistry?.notifyEnded(CallEndReason.HANGUP_LOCAL, null)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.IDLE))
            .callSetupData(null)
            .build()
    }

    override fun handleCallFailedTimeout(state: CallServiceState): CallServiceState {
        Log.e(tag, "handleCallFailedTimeout: signaling timed out")
        observerRegistry?.notifyEnded(CallEndReason.TIMEOUT, null)
        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.ENDED, error = "Signaling timed out"))
            .callSetupData(null)
            .build()
    }

    override fun handleSignalingTimeout(state: CallServiceState): CallServiceState {
        Log.e(tag, "handleSignalingTimeout: signaling timed out")
        observerRegistry?.notifyEnded(CallEndReason.TIMEOUT, null)
        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.ENDED, error = "Signaling timed out"))
            .callSetupData(null)
            .build()
    }

    override fun handleCallFailedBusy(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleCallFailedBusy: remote user is busy")
        observerRegistry?.notifyEnded(CallEndReason.BUSY, null)
        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.ENDED, error = "User is busy"))
            .callSetupData(null)
            .build()
    }

    override fun handleCallFailedIce(state: CallServiceState): CallServiceState {
        Log.e(tag, "handleCallFailedIce: ICE connection failed")
        observerRegistry?.notifyEnded(CallEndReason.ERROR, null)
        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.ENDED, error = "Connection failed"))
            .callSetupData(null)
            .build()
    }

    override fun handleCallFailedDeclinedElsewhere(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleCallFailedDeclinedElsewhere: call accepted on another device")
        observerRegistry?.notifyEnded(CallEndReason.ANSWERED_ELSEWHERE, null)
        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.ENDED, error = "Call answered elsewhere"))
            .callSetupData(null)
            .build()
    }
}