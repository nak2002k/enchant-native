package org.enchant.core.calls.action.processors

import android.util.Log
import org.enchant.core.calls.action.BaseActionProcessor
import org.enchant.core.calls.action.CallAction
import org.enchant.core.calls.action.CallPhase
import org.enchant.core.calls.model.CallDirection
import org.enchant.core.calls.model.CallStatus
import org.enchant.core.calls.state.CallServiceState
import org.enchant.core.calls.state.CallSetupData
import java.util.UUID

class IdleActionProcessor(
    private val callLogger: org.enchant.core.calls.CallLogger?,
    private val observerRegistry: org.enchant.core.calls.observer.CallObserverRegistry?
) : BaseActionProcessor() {

    override val currentPhase: CallPhase = CallPhase.IDLE
    override val tag: String = "IdleActionProcessor"

    override fun handleStartOutgoingCall(state: CallServiceState, action: CallAction.StartOutgoingCall): CallServiceState {
        Log.d(tag, "handleStartOutgoingCall: remoteUserId=${action.remoteUserId}, isVideo=${action.isVideo}")

        val callId = UUID.randomUUID().toString()
        val newCallState = state.callState.copy(
            status = CallStatus.CALLING,
            remoteUserId = action.remoteUserId,
            callId = callId,
            isVideoCall = action.isVideo,
            direction = CallDirection.OUTGOING
        )

        val setupData = CallSetupData(
            remoteUserId = action.remoteUserId,
            callId = callId,
            isVideo = action.isVideo
        )

        observerRegistry?.notifyStarted(action.remoteUserId, action.isVideo)

        return state.builder()
            .actionProcessor(OutgoingCallActionProcessor(callLogger, observerRegistry, action.remoteUserId, action.isVideo))
            .callState(newCallState)
            .callSetupData(setupData)
            .build()
    }

    override fun handleReceiveIncomingOffer(state: CallServiceState, action: CallAction.ReceiveIncomingOffer): CallServiceState {
        Log.d(tag, "handleReceiveIncomingOffer: remoteUserId=${action.remoteUserId}, isVideo=${action.isVideo}")

        val callId = action.callId.ifBlank { UUID.randomUUID().toString() }
        val newCallState = state.callState.copy(
            status = CallStatus.RINGING,
            remoteUserId = action.remoteUserId,
            callId = callId,
            isVideoCall = action.isVideo,
            direction = CallDirection.INCOMING
        )

        val setupData = CallSetupData(
            remoteUserId = action.remoteUserId,
            callId = callId,
            isVideo = action.isVideo,
            offerSdp = action.sdp,
            receivedAt = System.currentTimeMillis()
        )

        observerRegistry?.notifyStarted(action.remoteUserId, action.isVideo)

        return state.builder()
            .actionProcessor(IncomingCallActionProcessor(callLogger, observerRegistry, action.remoteUserId, action.isVideo))
            .callState(newCallState)
            .callSetupData(setupData)
            .build()
    }
}