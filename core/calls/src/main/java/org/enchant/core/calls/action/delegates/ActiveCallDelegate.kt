package org.enchant.core.calls.action.delegates

import android.util.Log
import org.enchant.core.calls.CallLogger
import org.enchant.core.calls.action.processors.IdleActionProcessor
import org.enchant.core.calls.model.CallDirection
import org.enchant.core.calls.model.CallEndReason
import org.enchant.core.calls.model.CallStatus
import org.enchant.core.calls.model.CallSummary
import org.enchant.core.calls.observer.CallObserverRegistry
import org.enchant.core.calls.state.CallServiceState

class ActiveCallDelegate(
    private val callLogger: CallLogger?,
    private val observerRegistry: CallObserverRegistry?
) {
    fun performHangup(state: CallServiceState): CallServiceState {
        Log.d("ActiveCallDelegate", "performHangup")

        val summary = buildSummary(state)
        observerRegistry?.notifyEnded(CallEndReason.HANGUP_LOCAL, summary)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.IDLE))
            .build()
    }

    fun performRemoteHangup(state: CallServiceState, reason: String?): CallServiceState {
        Log.d("ActiveCallDelegate", "performRemoteHangup: reason=$reason")

        val summary = buildSummary(state)
        val endReason = when (reason) {
            "busy" -> CallEndReason.BUSY
            "timeout" -> CallEndReason.TIMEOUT
            else -> CallEndReason.HANGUP_REMOTE
        }

        observerRegistry?.notifyEnded(endReason, summary)

        return state.builder()
            .actionProcessor(IdleActionProcessor(callLogger, observerRegistry))
            .callState(state.callState.copy(status = CallStatus.IDLE))
            .build()
    }

    private fun buildSummary(state: CallServiceState): CallSummary? {
        val callState = state.callState
        return if (callState.durationSeconds > 0) {
            CallSummary(
                callState.durationSeconds,
                callState.isVideoCall,
                callState.direction == CallDirection.OUTGOING
            )
        } else null
    }
}