package org.enchant.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pulls undelivered envelopes from the server and hands them to the
 * incoming handler. Invoked on every WS (re)connect so messages that
 * arrived during an offline window or a reconnect are never lost.
 */
object PendingMessageFetcher {
    private var lastFetchAt = 0L

    suspend fun fetchAndProcess() {
        withContext(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            if (now - lastFetchAt < 5_000L) return@withContext
            lastFetchAt = now
            val handler = WebSocketManager.incomingHandler ?: return@withContext
            val client = runCatching { ApiClient.getInstance() }.getOrNull() ?: return@withContext
            try {
                val response = client.get("/v1/messages/pending").getOrNull() ?: return@withContext
                val messages = response["messages"]?.jsonArray ?: return@withContext
                for (raw in messages) {
                    val bytes = raw.jsonArray.mapNotNull { it.jsonPrimitive.content.toIntOrNull() }
                        .map { it.toByte() }.toByteArray()
                    if (bytes.isEmpty()) continue
                    val env = org.enchant.protos.EnvelopeProtos.Envelope.parseFrom(bytes)
                    val envelope = IncomingEnvelope(
                        envelopeId = env.envelopeId.ifEmpty { null },
                        senderUserId = env.senderUserId.ifEmpty { null },
                        senderDeviceId = env.senderDeviceId.ifEmpty { null },
                        messageType = env.messageType.ifEmpty { "ENVELOPE" },
                        payload = env.payload.toByteArray(),
                        serverTimestamp = if (env.hasServerTs()) env.serverTs else null,
                        senderTimestamp = if (env.hasSenderTs()) env.senderTs.toLongOrNull() else null,
                        ephemeral = env.ephemeral,
                        sealed = env.sealed,
                        replyToken = env.replyToken.ifEmpty { null },
                        requestId = null
                    )
                    runCatching { handler.invoke(envelope) }
                }
            } catch (e: Exception) {
                android.util.Log.w("PendingFetcher", "pending fetch failed: ${e.message}")
            }
        }
    }
}
