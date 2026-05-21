package org.enchant.core.calls.state

import org.enchant.core.calls.CallLogger
import org.enchant.core.calls.action.ActionProcessor
import org.enchant.core.calls.model.CallQualityStats
import org.enchant.core.calls.model.CallState
import org.enchant.core.calls.observer.CallObserverRegistry

data class CallServiceState(
    val actionProcessor: ActionProcessor,
    val callState: CallState = CallState.idle(),
    val callSetupData: CallSetupData? = null,
    val localDeviceState: LocalDeviceState = LocalDeviceState(),
    val qualityStats: CallQualityStats = CallQualityStats(),
    val callLogger: CallLogger? = null,
    val observerRegistry: CallObserverRegistry? = null
) {
    val phase: org.enchant.core.calls.action.CallPhase get() = actionProcessor.currentPhase

    fun builder(): CallServiceStateBuilder = CallServiceStateBuilder(this)
}

data class CallSetupData(
    val remoteUserId: String,
    val callId: String,
    val isVideo: Boolean,
    val offerSdp: String? = null,
    val answerSdp: String? = null,
    val receivedAt: Long = System.currentTimeMillis()
)

data class LocalDeviceState(
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isVideoEnabled: Boolean = false,
    val isCameraFlipped: Boolean = false,
    val isOnHold: Boolean = false,
    val isHandRaised: Boolean = false,
    val isAdmin: Boolean = false
)

class CallServiceStateBuilder(private val current: CallServiceState) {
    private var processor: ActionProcessor? = current.actionProcessor
    private var state: CallState = current.callState
    private var setupData: CallSetupData? = current.callSetupData
    private var deviceState: LocalDeviceState = current.localDeviceState
    private var quality: CallQualityStats = current.qualityStats

    fun actionProcessor(p: ActionProcessor): CallServiceStateBuilder = apply { processor = p }
    fun callState(s: CallState): CallServiceStateBuilder = apply { state = s }
    fun callSetupData(d: CallSetupData?): CallServiceStateBuilder = apply { setupData = d }
    fun localDeviceState(d: LocalDeviceState): CallServiceStateBuilder = apply { deviceState = d }
    fun qualityStats(q: CallQualityStats): CallServiceStateBuilder = apply { quality = q }

    fun build(): CallServiceState = CallServiceState(
        actionProcessor = processor!!,
        callState = state,
        callSetupData = setupData,
        localDeviceState = deviceState,
        qualityStats = quality,
        callLogger = current.callLogger,
        observerRegistry = current.observerRegistry
    )
}