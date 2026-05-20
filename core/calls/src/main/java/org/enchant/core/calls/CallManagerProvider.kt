package org.enchant.core.calls

object CallManagerProvider {
    private var _instance: DefaultCallManager? = null

    fun setInstance(instance: DefaultCallManager) {
        _instance = instance
    }

    fun getInstance(): DefaultCallManager = _instance
        ?: throw IllegalStateException("CallManager not initialized. Call setInstance() first.")
}

object CallManager {
    val callState = CallManagerProvider.getInstance().callState

    fun registerObserver(observer: org.enchant.core.calls.observer.CallObserver) =
        CallManagerProvider.getInstance().registerObserver(observer)

    fun unregisterObserver(observer: org.enchant.core.calls.observer.CallObserver) =
        CallManagerProvider.getInstance().unregisterObserver(observer)

    fun toggleMute() = CallManagerProvider.getInstance().toggleMute()
    fun toggleSpeaker() = CallManagerProvider.getInstance().toggleSpeaker()
    fun flipCamera() = CallManagerProvider.getInstance().flipCamera()
    fun endCall() = CallManagerProvider.getInstance().endCall()

    fun denyCall() = CallManagerProvider.getInstance().denyCall()
    suspend fun acceptCall(withVideo: Boolean) {
        CallManagerProvider.getInstance().acceptCall(withVideo)
    }
}