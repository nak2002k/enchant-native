package org.enchant.core.calls

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.core.calls.model.IceServer
import org.enchant.core.network.ApiClient

class WebSocketSignalingClient(
    private val apiClient: ApiClient
) : SignalingClient {

    override suspend fun sendOffer(remoteUserId: String, sdp: String): Boolean =
        post("/v1/calls/offer") {
            put("recipient_user_id", remoteUserId)
            put("sdp", sdp)
        }

    override suspend fun sendAnswer(remoteUserId: String, sdp: String): Boolean =
        post("/v1/calls/answer") {
            put("recipient_user_id", remoteUserId)
            put("sdp", sdp)
        }

    override suspend fun sendIceCandidate(remoteUserId: String, candidate: String): Boolean =
        post("/v1/calls/ice") {
            put("recipient_user_id", remoteUserId)
            put("candidate", candidate)
        }

    override suspend fun sendHangup(remoteUserId: String): Boolean =
        post("/v1/calls/end") {
            put("recipient_user_id", remoteUserId)
        }

    override suspend fun fetchTurnServers(): Result<List<IceServer>> =
        apiClient.get("/v1/calls/turn-credentials").map { json ->
            val uris = json["uris"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
            val username = json["username"]?.jsonPrimitive?.content
            val credential = json["credential"]?.jsonPrimitive?.content
            if (uris.isEmpty()) {
                emptyList()
            } else {
                listOf(IceServer(urls = uris, username = username, credential = credential))
            }
        }

    private suspend fun post(path: String, body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): Boolean =
        apiClient.post(path, buildJsonObject(body)).isSuccess
}
