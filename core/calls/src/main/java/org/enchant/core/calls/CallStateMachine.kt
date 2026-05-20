package org.enchant.core.calls

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.enchant.core.calls.model.CallState
import org.enchant.core.calls.model.CallStatus
import org.enchant.core.calls.model.CallDirection

class CallStateMachine {
    private val _state = MutableStateFlow(CallState.idle())
    val state: StateFlow<CallState> = _state.asStateFlow()

    fun startOutgoing(remoteUserId: String, isVideo: Boolean, callId: String): Boolean {
        if (_state.value.status != CallStatus.IDLE) return false
        _state.value = CallState(
            status = CallStatus.CALLING,
            remoteUserId = remoteUserId,
            isVideoCall = isVideo,
            callId = callId,
            direction = CallDirection.OUTGOING
        )
        return true
    }

    fun receiveIncoming(remoteUserId: String, isVideo: Boolean, callId: String): Boolean {
        if (_state.value.status != CallStatus.IDLE) return false
        _state.value = CallState(
            status = CallStatus.RINGING,
            remoteUserId = remoteUserId,
            isVideoCall = isVideo,
            callId = callId,
            direction = CallDirection.INCOMING
        )
        return true
    }

    fun acceptCall(): Boolean {
        if (_state.value.status != CallStatus.RINGING) return false
        _state.value = _state.value.copy(status = CallStatus.CONNECTING)
        return true
    }

    fun setConnecting(): Boolean {
        if (_state.value.status != CallStatus.CALLING) return false
        _state.value = _state.value.copy(status = CallStatus.CONNECTING)
        return true
    }

    fun setConnected(): Boolean {
        val current = _state.value.status
        if (current != CallStatus.CONNECTING && current != CallStatus.CALLING) return false
        _state.value = _state.value.copy(status = CallStatus.CONNECTED)
        return true
    }

    fun setReconnecting(): Boolean {
        val current = _state.value.status
        if (current != CallStatus.CONNECTED && current != CallStatus.CONNECTING) return false
        _state.value = _state.value.copy(status = CallStatus.RECONNECTING)
        return true
    }

    fun setReconnected(): Boolean {
        if (_state.value.status != CallStatus.RECONNECTING) return false
        _state.value = _state.value.copy(status = CallStatus.CONNECTED)
        return true
    }

    fun endCall(): CallState {
        val previous = _state.value
        _state.value = CallState.idle()
        return previous
    }

    fun cancelCall(): Boolean {
        if (_state.value.status != CallStatus.CALLING) return false
        _state.value = CallState.idle()
        return true
    }

    fun denyCall(): Boolean {
        if (_state.value.status != CallStatus.RINGING) return false
        _state.value = CallState.idle()
        return true
    }

    fun toggleMute() {
        _state.value = _state.value.copy(isMuted = !_state.value.isMuted)
    }

    fun toggleVideo() {
        _state.value = _state.value.copy(isVideoEnabled = !_state.value.isVideoEnabled)
    }

    fun toggleSpeaker() {
        _state.value = _state.value.copy(isSpeakerOn = !_state.value.isSpeakerOn)
    }

    fun setOnHold(hold: Boolean) {
        _state.value = _state.value.copy(isOnHold = hold)
    }

    fun setHandRaised(raised: Boolean) {
        _state.value = _state.value.copy(isHandRaised = raised)
    }

    fun updateDuration(seconds: Int) {
        _state.value = _state.value.copy(durationSeconds = seconds)
    }

    fun setError(error: String) {
        _state.value = _state.value.copy(error = error)
    }

    fun reset() {
        _state.value = CallState.idle()
    }
}