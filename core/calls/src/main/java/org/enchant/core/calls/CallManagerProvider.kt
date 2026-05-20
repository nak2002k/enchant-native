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
}