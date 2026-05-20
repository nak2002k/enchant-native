package org.enchant.core.calls.observer

import org.enchant.core.calls.model.CallEndReason
import org.enchant.core.calls.model.CallSummary

interface CallObserver {
    fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) {}
    fun onCallEnded(reason: CallEndReason, summary: CallSummary?) {}
    fun onCallConnected() {}
    fun onCallReconnecting() {}
    fun onCallReconnected() {}
    fun onOfferSent(remoteUserId: String, sdp: String) {}
    fun onAnswerSent(remoteUserId: String, sdp: String) {}
    fun onIceCandidateSent(remoteUserId: String, candidate: String) {}
    fun onHangupSent(remoteUserId: String) {}
    fun onError(error: String) {}
    fun onQualityUpdate(stats: org.enchant.core.calls.model.CallQualityStats) {}
}

class CallObserverRegistry {
    private val observers = mutableListOf<CallObserver>()

    fun register(observer: CallObserver) {
        synchronized(observers) {
            if (!observers.contains(observer)) observers.add(observer)
        }
    }

    fun unregister(observer: CallObserver) {
        synchronized(observers) { observers.remove(observer) }
    }

    fun clear() {
        synchronized(observers) { observers.clear() }
    }

    fun notifyStarted(remoteUserId: String, isVideo: Boolean) {
        synchronized(observers) { observers.forEach { it.onCallStarted(remoteUserId, isVideo) } }
    }

    fun notifyEnded(reason: CallEndReason, summary: CallSummary?) {
        synchronized(observers) { observers.forEach { it.onCallEnded(reason, summary) } }
    }

    fun notifyConnected() {
        synchronized(observers) { observers.forEach { it.onCallConnected() } }
    }

    fun notifyReconnecting() {
        synchronized(observers) { observers.forEach { it.onCallReconnecting() } }
    }

    fun notifyReconnected() {
        synchronized(observers) { observers.forEach { it.onCallReconnected() } }
    }

    fun notifyOfferSent(remoteUserId: String, sdp: String) {
        synchronized(observers) { observers.forEach { it.onOfferSent(remoteUserId, sdp) } }
    }

    fun notifyAnswerSent(remoteUserId: String, sdp: String) {
        synchronized(observers) { observers.forEach { it.onAnswerSent(remoteUserId, sdp) } }
    }

    fun notifyIceSent(remoteUserId: String, candidate: String) {
        synchronized(observers) { observers.forEach { it.onIceCandidateSent(remoteUserId, candidate) } }
    }

    fun notifyHangup(remoteUserId: String) {
        synchronized(observers) { observers.forEach { it.onHangupSent(remoteUserId) } }
    }

    fun notifyError(error: String) {
        synchronized(observers) { observers.forEach { it.onError(error) } }
    }

    fun notifyQuality(stats: org.enchant.core.calls.model.CallQualityStats) {
        synchronized(observers) { observers.forEach { it.onQualityUpdate(stats) } }
    }
}