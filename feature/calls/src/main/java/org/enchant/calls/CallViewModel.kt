package org.enchant.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.CallState
import org.enchant.core.calls.CallStatus

data class CallUiState(
    val callState: CallState = CallState(),
    val isCallScreenVisible: Boolean = false,
    val navigateToConversation: String? = null
)

class CallViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            CallManager.callState.collect { state ->
                _uiState.value = _uiState.value.copy(callState = state)
                when (state.status) {
                    CallStatus.IDLE -> {
                        _uiState.value = _uiState.value.copy(isCallScreenVisible = false)
                    }
                    CallStatus.RINGING, CallStatus.CALLING,
                    CallStatus.CONNECTING, CallStatus.CONNECTED,
                    CallStatus.RECONNECTING -> {
                        _uiState.value = _uiState.value.copy(isCallScreenVisible = true)
                    }
                    CallStatus.ENDED -> {
                        _uiState.value = _uiState.value.copy(isCallScreenVisible = false)
                    }
                }
            }
        }
    }

    fun startCall(remoteUserId: String, isVideo: Boolean) {
        viewModelScope.launch {
            CallManager.startOutgoingCall(remoteUserId, isVideo)
        }
    }

    fun acceptCall(withVideo: Boolean) {
        val currentState = _uiState.value.callState.status
        if (currentState != CallStatus.RINGING) {
            android.util.Log.w("CallViewModel", "acceptCall called in state $currentState, expected RINGING")
            return
        }
        viewModelScope.launch {
            CallManager.acceptCall(withVideo)
        }
    }

    fun denyCall() {
        CallManager.denyCall()
    }

    fun endCall() {
        CallManager.endCall()
    }

    fun toggleMute() {
        CallManager.toggleMute()
    }

    fun toggleSpeaker() {
        CallManager.toggleSpeaker()
    }

    fun toggleVideo() {
        CallManager.toggleVideo()
    }

    fun flipCamera() {
        CallManager.flipCamera()
    }

    fun setOnHold(hold: Boolean) {
        CallManager.setOnHold(hold)
    }

    fun raiseHand(raised: Boolean) {
        CallManager.raiseHand(raised)
    }

    fun navigateToConversation(conversationId: String) {
        _uiState.value = _uiState.value.copy(navigateToConversation = conversationId)
    }

    fun onNavigatedToConversation() {
        _uiState.value = _uiState.value.copy(navigateToConversation = null)
    }
}