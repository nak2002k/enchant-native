package org.enchant.core.calls

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.core.calls.model.IceServer
import org.enchant.core.crypto.CryptoPrimitives
import org.enchant.core.crypto.NativeSessionManager
import org.enchant.core.network.ApiClient

class WebSocketSignalingClient(
    private val apiClient: ApiClient
) : SignalingClient {

    override suspend fun sendOffer(remoteUserId: String, sdp: String, callId: String): Boolean {
        val encSdp = encryptSignal(remoteUserId, sdp) ?: return false
        return post("/v1/calls/offer") {
            put("recipient_user_id", remoteUserId)
            put("call_id", callId)
            put("sdp", encSdp)
        }
    }

    override suspend fun sendAnswer(remoteUserId: String, sdp: String, callId: String): Boolean {
        val encSdp = encryptSignal(remoteUserId, sdp) ?: return false
        return post("/v1/calls/answer") {
            put("recipient_user_id", remoteUserId)
            put("call_id", callId)
            put("sdp", encSdp)
        }
    }

    override suspend fun sendIceCandidate(remoteUserId: String, candidate: String, callId: String): Boolean {
        val encCandidate = encryptSignal(remoteUserId, candidate) ?: return false
        return post("/v1/calls/ice") {
            put("recipient_user_id", remoteUserId)
            put("call_id", callId)
            put("candidate", encCandidate)
        }
    }

    override suspend fun sendHangup(remoteUserId: String, callId: String): Boolean =
        post("/v1/calls/end") {
            put("recipient_user_id", remoteUserId)
            put("call_id", callId)
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

    /**
     * Encrypts a signaling payload (SDP / ICE candidate) with the session key.
     *
     * Returns a JSON wrapper `{"c":1,"mt":"E"|"P","d":"<base64url>"}` where
     * `mt` records whether the ciphertext is a pre-key message (new session)
     * or a regular encrypted message, or null if no session/key bundle could
     * be established. The server only sees opaque routing metadata plus this
     * opaque payload; the media content stays end-to-end encrypted.
     */
    private suspend fun encryptSignal(remoteUserId: String, plaintext: String): String? {
        // Call signaling rides the Veil seal (the same proven path as
        // messaging) instead of the 1:1 session ratchet.
        val recipientPublicKey = NativeSessionManager.getIdentityKey(remoteUserId)
            ?: return null
        val identity = org.enchant.core.crypto.KeyManager.getIdentityKeyPair()
            ?: return null
        val veiled = org.enchant.core.crypto.VeilSender.encryptVeiled(
            recipientPublicKey = recipientPublicKey,
            senderIdentityPrivate = identity.privateKey,
            senderIdentityPublic = identity.publicKey,
            message = plaintext.toByteArray(Charsets.UTF_8)
        )
        return CryptoPrimitives.base64UrlEncode(veiled)
    }

    private suspend fun post(path: String, body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): Boolean =
        apiClient.post(path, buildJsonObject(body)).isSuccess
}
