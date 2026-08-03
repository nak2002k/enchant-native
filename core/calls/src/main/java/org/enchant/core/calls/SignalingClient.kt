package org.enchant.core.calls

interface SignalingClient {
    suspend fun sendOffer(remoteUserId: String, sdp: String, callId: String): Boolean
    suspend fun sendAnswer(remoteUserId: String, sdp: String, callId: String): Boolean
    suspend fun sendIceCandidate(remoteUserId: String, candidate: String, callId: String): Boolean
    suspend fun sendHangup(remoteUserId: String, callId: String): Boolean
    suspend fun fetchTurnServers(): Result<List<org.enchant.core.calls.model.IceServer>>
    suspend fun peekGroupCall(groupId: String): Int { return 0 }
}
