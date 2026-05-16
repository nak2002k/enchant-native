package org.enchant.core.calls

interface CallObserver {
    fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) {}
    fun onCallEnded(reason: CallEndReason, summary: CallSummary?) {}
    fun onOfferSent(remoteUserId: String, sdp: String) {}
    fun onAnswerSent(remoteUserId: String, sdp: String) {}
    fun onIceCandidatesSent(remoteUserId: String, candidates: List<String>) {}
    fun onHangupSent(remoteUserId: String) {}
    fun onGroupCallRingUpdate(groupId: String, ringUpdate: RingUpdate) {}
    fun onMessageSentError(exception: Exception) {}
}

enum class RingUpdate { REQUESTED, JOINED, LEFT, DECLINED, BUSY }

class CallObserverRegistry {
    private val observers = mutableListOf<CallObserver>()

    fun registerObserver(observer: CallObserver) {
        synchronized(observers) {
            if (!observers.contains(observer)) observers.add(observer)
        }
    }

    fun unregisterObserver(observer: CallObserver) {
        synchronized(observers) { observers.remove(observer) }
    }

    fun notifyCallStarted(remoteUserId: String, isVideoCall: Boolean) {
        synchronized(observers) { observers.forEach { it.onCallStarted(remoteUserId, isVideoCall) } }
    }

    fun notifyCallEnded(reason: CallEndReason, summary: CallSummary?) {
        synchronized(observers) { observers.forEach { it.onCallEnded(reason, summary) } }
    }

    fun notifyOfferSent(remoteUserId: String, sdp: String) {
        synchronized(observers) { observers.forEach { it.onOfferSent(remoteUserId, sdp) } }
    }

    fun notifyAnswerSent(remoteUserId: String, sdp: String) {
        synchronized(observers) { observers.forEach { it.onAnswerSent(remoteUserId, sdp) } }
    }

    fun notifyIceCandidatesSent(remoteUserId: String, candidates: List<String>) {
        synchronized(observers) { observers.forEach { it.onIceCandidatesSent(remoteUserId, candidates) } }
    }

    fun notifyHangupSent(remoteUserId: String) {
        synchronized(observers) { observers.forEach { it.onHangupSent(remoteUserId) } }
    }

    fun notifyGroupCallRingUpdate(groupId: String, ringUpdate: RingUpdate) {
        synchronized(observers) { observers.forEach { it.onGroupCallRingUpdate(groupId, ringUpdate) } }
    }

    fun notifyMessageSentError(exception: Exception) {
        synchronized(observers) { observers.forEach { it.onMessageSentError(exception) } }
    }
}
