package org.enchant.core.calls

interface SignalingClient {
    suspend fun sendOffer(remoteUserId: String, sdp: String): Boolean
    suspend fun sendAnswer(remoteUserId: String, sdp: String): Boolean
    suspend fun sendIceCandidate(remoteUserId: String, candidate: String): Boolean
    suspend fun sendHangup(remoteUserId: String): Boolean
    suspend fun fetchTurnServers(): Result<List<org.enchant.core.calls.model.IceServer>>
}