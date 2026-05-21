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
import org.enchant.core.calls.state.LocalDeviceState

class ConnectedCallActionProcessor(
    private val callLogger: CallLogger?,
    private val observerRegistry: CallObserverRegistry?,
    private val remoteUserId: String
) : BaseActionProcessor() {

    override val currentPhase: CallPhase = CallPhase.CONNECTED
    override val tag: String = "ConnectedCallActionProcessor"

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

    override fun handleFlipCamera(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleFlipCamera")
        val newDeviceState = state.localDeviceState.copy(isCameraFlipped = !state.localDeviceState.isCameraFlipped)
        return state.builder()
            .localDeviceState(newDeviceState)
            .build()
    }

    override fun handleSetOnHold(state: CallServiceState, action: CallAction.SetOnHold): CallServiceState {
        Log.d(tag, "handleSetOnHold: hold=${action.hold}")
        val newDeviceState = state.localDeviceState.copy(isOnHold = action.hold)
        return state.builder()
            .localDeviceState(newDeviceState)
            .build()
    }

override fun handleRaiseHand(state: CallServiceState, action: CallAction.RaiseHand): CallServiceState {
        Log.d(tag, "handleRaiseHand: raised=${action.raised}")
        val newDeviceState = state.localDeviceState.copy(
            isHandRaised = action.raised,
            handRaisedTimestamp = if (action.raised) System.currentTimeMillis() else 0
        )
        return state.builder()
            .localDeviceState(newDeviceState)
            .build()
    }

    override fun handleCallEnded(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleCallEnded")

        val summary = if (state.callState.durationSeconds > 0) {
            org.enchant.core.calls.model.CallSummary(
                state.callState.durationSeconds,
                state.callState.isVideoCall,
                state.callState.direction == org.enchant.core.calls.model.CallDirection.OUTGOING
            )
        } else null

        observerRegistry?.notifyEnded(CallEndReason.HANGUP_LOCAL, summary)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.IDLE))
            .build()
    }

    override fun handleReceiveHangup(state: CallServiceState, action: CallAction.ReceiveHangup): CallServiceState {
        Log.d(tag, "handleReceiveHangup: reason=${action.reason}")

        val summary = if (state.callState.durationSeconds > 0) {
            org.enchant.core.calls.model.CallSummary(
                state.callState.durationSeconds,
                state.callState.isVideoCall,
                state.callState.direction == org.enchant.core.calls.model.CallDirection.OUTGOING
            )
        } else null

        observerRegistry?.notifyEnded(CallEndReason.HANGUP_REMOTE, summary)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.IDLE))
            .build()
    }

    override fun handleCallReconnecting(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleReconnecting")
        observerRegistry?.notifyReconnecting()
        return state.builder()
            .callState(state.callState.copy(status = CallStatus.RECONNECTING))
            .build()
    }

    override fun handleCallReconnected(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleReconnected")
        observerRegistry?.notifyReconnected()
        return state.builder()
            .callState(state.callState.copy(status = CallStatus.CONNECTED))
            .build()
    }

    override fun handleQualityUpdate(state: CallServiceState, action: CallAction.QualityUpdate): CallServiceState {
        return state.builder()
            .qualityStats(action.stats)
            .build()
    }

    override fun handleCallFailedIce(state: CallServiceState): CallServiceState {
        Log.e(tag, "handleCallFailedIce: ICE connection failed")
        observerRegistry?.notifyEnded(CallEndReason.ERROR, null)
        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.ENDED, error = "Connection failed"))
            .build()
    }

    override fun handleCallFailedEndedElsewhere(state: CallServiceState): CallServiceState {
        Log.d(tag, "handleCallFailedEndedElsewhere: call ended on another device")
        observerRegistry?.notifyEnded(CallEndReason.ANSWERED_ELSEWHERE, null)
        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.ENDED, error = "Call ended elsewhere"))
            .build()
    }
}