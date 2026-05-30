package org.enchant.core.calls.webrtc

import org.webrtc.IceCandidate

class IceCandidateHandler {
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private val maxPendingSize = 100

    fun serialize(candidate: IceCandidate): String {
        return "${candidate.sdpMid}|${candidate.sdpMLineIndex}|${candidate.sdp}"
    }

    fun parse(data: String): IceCandidate? {
        val parts = data.split("|", limit = 3)
        if (parts.size < 3) return null

        val sdpMid = parts[0]
        val sdpMLineIndex = parts[1].toIntOrNull() ?: return null
        val sdp = parts[2]

        return IceCandidate(sdpMid, sdpMLineIndex, sdp)
    }

    fun queue(candidate: IceCandidate) {
        synchronized(pendingCandidates) {
            if (pendingCandidates.size < maxPendingSize) {
                pendingCandidates.add(candidate)
            }
        }
    }

    fun queueRaw(data: String) {
        parse(data)?.let { queue(it) }
    }

    fun drainAndApply(pc: org.webrtc.PeerConnection): Int {
        val candidates = synchronized(pendingCandidates) {
            val list = pendingCandidates.toList()
            pendingCandidates.clear()
            list
        }
        var applied = 0
        for (candidate in candidates) {
            pc.addIceCandidate(candidate)
            applied++
        }
        return applied
    }

    fun clear() {
        synchronized(pendingCandidates) { pendingCandidates.clear() }
    }

    fun pendingCount(): Int = synchronized(pendingCandidates) { pendingCandidates.size }
}