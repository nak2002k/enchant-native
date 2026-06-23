package org.enchant.core.calls

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.enchant.core.calls.model.CallState

object CallManagerProvider {
    fun getInstance(): DefaultCallManager = CallsModule.getCallManager()
}

object CallManager {
    private val _fallbackState = MutableStateFlow(CallState())

    val callState: StateFlow<CallState>
        get() = try {
            CallsModule.getCallManager().callState
        } catch (e: IllegalStateException) {
            Log.w("CallManager", "CallManager not initialized, returning idle state")
            _fallbackState
        }

    private fun <T> safeCall(default: T, block: DefaultCallManager.() -> T): T {
        return try {
            CallsModule.getCallManager().let(block)
        } catch (e: IllegalStateException) {
            Log.w("CallManager", "CallManager not initialized")
            default
        }
    }

    fun registerObserver(observer: org.enchant.core.calls.observer.CallObserver) =
        safeCall(Unit) { registerObserver(observer) }

    fun unregisterObserver(observer: org.enchant.core.calls.observer.CallObserver) =
        safeCall(Unit) { unregisterObserver(observer) }

    fun toggleMute() = safeCall(Unit) { toggleMute() }
    fun toggleSpeaker() = safeCall(Unit) { toggleSpeaker() }
    fun flipCamera() = safeCall(Unit) { flipCamera() }
    fun endCall() = safeCall(Unit) { endCall() }

    fun denyCall() = safeCall(Unit) { denyCall() }
    suspend fun acceptCall(withVideo: Boolean) {
        try {
            CallsModule.getCallManager().acceptCall(withVideo)
        } catch (e: IllegalStateException) {
            Log.w("CallManager", "CallManager not initialized")
        }
    }

    suspend fun startOutgoingCall(remoteUserId: String, isVideo: Boolean) {
        try {
            CallsModule.getCallManager().startOutgoingCall(remoteUserId, isVideo)
        } catch (e: IllegalStateException) {
            Log.w("CallManager", "CallManager not initialized")
        }
    }

    fun handleReceivedOffer(senderUserId: String, sdp: String, callId: String, isVideo: Boolean) {
        safeCall(Unit) { handleReceivedOffer(senderUserId, sdp, callId, isVideo) }
    }

    fun handleReceivedHangup() {
        safeCall(Unit) { handleReceivedHangup() }
    }

    fun toggleVideo() {
        safeCall(Unit) { toggleVideo() }
    }

    suspend fun getCallLogs(limit: Int = 100): List<org.enchant.core.calls.model.CallLogEntry> =
        try {
            CallsModule.getCallManager().getCallLogs(limit)
        } catch (e: IllegalStateException) {
            Log.w("CallManager", "CallManager not initialized")
            emptyList()
        }

    fun setOnHold(hold: Boolean) = safeCall(Unit) { setOnHold(hold) }
    fun raiseHand(raised: Boolean) = safeCall(Unit) { raiseHand(raised) }
}