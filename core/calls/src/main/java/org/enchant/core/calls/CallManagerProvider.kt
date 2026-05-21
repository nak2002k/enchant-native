package org.enchant.core.calls

object CallManagerProvider {
    fun getInstance(): DefaultCallManager = CallsModule.getCallManager()
}

object CallManager {
    val callState get() = CallsModule.getCallManager().callState

    fun registerObserver(observer: org.enchant.core.calls.observer.CallObserver) =
        CallsModule.getCallManager().registerObserver(observer)

    fun unregisterObserver(observer: org.enchant.core.calls.observer.CallObserver) =
        CallsModule.getCallManager().unregisterObserver(observer)

    fun toggleMute() = CallsModule.getCallManager().toggleMute()
    fun toggleSpeaker() = CallsModule.getCallManager().toggleSpeaker()
    fun flipCamera() = CallsModule.getCallManager().flipCamera()
    fun endCall() = CallsModule.getCallManager().endCall()

    fun denyCall() = CallsModule.getCallManager().denyCall()
    suspend fun acceptCall(withVideo: Boolean) {
        CallsModule.getCallManager().acceptCall(withVideo)
    }

    suspend fun startOutgoingCall(remoteUserId: String, isVideo: Boolean) {
        CallsModule.getCallManager().startOutgoingCall(remoteUserId, isVideo)
    }

    fun handleReceivedOffer(senderUserId: String, sdp: String, callId: String, isVideo: Boolean) {
        CallsModule.getCallManager().handleReceivedOffer(senderUserId, sdp, callId, isVideo)
    }

    fun handleReceivedHangup() {
        CallsModule.getCallManager().handleReceivedHangup()
    }

    fun toggleVideo() {
        CallsModule.getCallManager().toggleVideo()
    }

    suspend fun getCallLogs(limit: Int = 100): List<org.enchant.core.calls.model.CallLogEntry> =
        CallsModule.getCallManager().getCallLogs(limit)

    fun setOnHold(hold: Boolean) = CallsModule.getCallManager().setOnHold(hold)
    fun raiseHand(raised: Boolean) = CallsModule.getCallManager().raiseHand(raised)
}