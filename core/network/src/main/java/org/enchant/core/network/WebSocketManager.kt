package org.enchant.core.network

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.CertificatePinner
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.ConnectionSpec.Builder as ConnectionSpecBuilder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.TlsVersion
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.enchant.protos.WebSocketResources
import org.enchant.protos.EnvelopeProtos
import org.enchant.core.base.AppConfig
import org.enchant.core.base.SecurePreferences
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, AUTH_FAILED
}

data class IncomingEnvelope(
    val envelopeId: String?,
    val senderUserId: String?,
    val senderDeviceId: String?,
    val messageType: String,
    val payload: ByteArray,
    val serverTimestamp: Long?,
    val ephemeral: Boolean,
    val replyToken: String? = null,
    val requestId: Long? = null
)

data class ConnectionError(val code: Int, val message: String)
data class OutgoingMessage(
    val id: String,
    val recipientUserId: String,
    val recipientDeviceId: String?,
    val messageType: String,
    val payload: ByteArray,
    val senderTs: Long
)

object WebSocketManager {
    @Volatile
    private var initialized = false
    private var scope: CoroutineScope? = null
    private var webSocket: WebSocket? = null
    private val requestIdCounter = java.util.concurrent.atomic.AtomicLong(0)
    private fun nextRequestId() = requestIdCounter.incrementAndGet()
    @Volatile
    private var consecutive401s = 0
    @Volatile
    private var retryCount = 0
    private val maxRetryCount = 10
    private val pendingRequests = ConcurrentHashMap<Long, CompletableDeferred<WebSocketResources.WebSocketResponseMessage>>()
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _incomingMessages = MutableSharedFlow<IncomingEnvelope>(extraBufferCapacity = 100)
    private val _connectionErrors = MutableSharedFlow<ConnectionError>(extraBufferCapacity = 10)
    @Volatile
    private var keepAliveJob: Job? = null

    private var apiClient: ApiClient? = null
    private var applicationContext: Context? = null

    // Certificate pinner: trusts system CAs for alpha.
    // Call updatePins() with real SHA-256 hashes before production.
    private val certificatePinner: CertificatePinner by lazy {
        CertificatePinner.Builder().build()
    }

    fun updatePins(host: String, pins: List<String>) {
        val builder = CertificatePinner.Builder()
        pins.forEach { pin -> builder.add(host, pin) }
        _certificatePinner = builder.build()
    }
    @Volatile
    private var _certificatePinner: CertificatePinner? = null
    private fun getPinner(): CertificatePinner = _certificatePinner ?: certificatePinner

    private fun buildSecureClient(): OkHttpClient {
        val spec = ConnectionSpecBuilder(ConnectionSpec.RESTRICTED_TLS)
            .tlsVersions(TlsVersion.TLS_1_3)
            .cipherSuites(
                CipherSuite.TLS_AES_256_GCM_SHA384,
                CipherSuite.TLS_CHACHA20_POLY1305_SHA256
            )
            .build()
        val builder = OkHttpClient.Builder()
            .connectionSpecs(listOf(spec))
            .certificatePinner(getPinner())
        return DomainFronting.applyToClient(builder).build()
    }

    private val wsClient = buildSecureClient()
        .newBuilder()
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val refreshClient = buildSecureClient()
        .newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    val incomingMessages: SharedFlow<IncomingEnvelope> = _incomingMessages.asSharedFlow()
    val connectionErrors: SharedFlow<ConnectionError> = _connectionErrors.asSharedFlow()

    /**
     * Hook invoked for every incoming envelope. Set by the app layer (which has access to
     * the message processor) so this network module stays decoupled from feature modules.
     *
     * Return true to ack the envelope (message processed/persisted), false to NACK it
     * (server will redeliver). The ack must only be sent AFTER processing completes so
     * failures trigger redelivery instead of silent message loss.
     */
    @Volatile
    var incomingHandler: (suspend (IncomingEnvelope) -> Boolean)? = null

    fun init(context: Context) {
        if (initialized) return
        applicationContext = context.applicationContext
        apiClient = ApiClient.getInstance()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        initialized = true
    }

    suspend fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTING || _connectionState.value == ConnectionState.CONNECTED) return
        _connectionState.value = ConnectionState.CONNECTING
        retryCount = 0

        startForegroundService()

        var jwt = SecurePreferences.getString("auth.jwt")
        if (jwt != null && isJwtExpired(jwt)) {
            val newJwt = tryRefreshJwt()
            if (newJwt != null) {
                jwt = newJwt
            } else {
                _connectionState.value = ConnectionState.AUTH_FAILED
                return
            }
        }
        if (jwt == null) {
            _connectionState.value = ConnectionState.AUTH_FAILED
            return
        }

        val wsUrl = AppConfig.wsUrl.trimEnd('/') + "/v1/connect"
        val request = Request.Builder().url(wsUrl).build()
        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                scope?.launch {
                    val authResult = authenticate(ws, jwt)
                    if (authResult) {
                        _connectionState.value = ConnectionState.CONNECTED
                        consecutive401s = 0
                        retryCount = 0
                        startKeepAlive(ws)
                    } else {
                        _connectionState.value = ConnectionState.AUTH_FAILED
                        _connectionErrors.tryEmit(ConnectionError(4001, "Auth failure"))
                    }
                }
            }

            override fun onMessage(ws: WebSocket, bytes: okio.ByteString) {
                scope?.launch {
                    handleFrame(bytes.toByteArray())
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                pendingRequests.values.forEach { it.completeExceptionally(Exception("WebSocket connection failed")) }
                pendingRequests.clear()
                _connectionState.value = ConnectionState.RECONNECTING
                scope?.launch { scheduleReconnect() }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.DISCONNECTED
                if (code == 4001) {
                    consecutive401s++
                    if (consecutive401s >= 5) {
                        _connectionState.value = ConnectionState.AUTH_FAILED
                        return
                    }
                }
                scope?.launch { scheduleReconnect() }
            }
        })
    }

    fun disconnect() {
        keepAliveJob?.cancel()
        val pending = ArrayList(pendingRequests.values)
        pendingRequests.clear()
        pending.forEach { it.completeExceptionally(Exception("WebSocket disconnected")) }
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        stopForegroundService()
    }

    suspend fun sendMessage(
        recipientUserId: String,
        recipientDeviceId: String? = null,
        payload: ByteArray,
        senderTs: Long? = null,
        ephemeral: Boolean = false
    ): String? {
        if (payload.size > 2 * 1024 * 1024) return null
        if (_connectionState.value != ConnectionState.CONNECTED) {
            return requestRESTFallback(OutgoingMessage(
                id = java.util.UUID.randomUUID().toString(),
                recipientUserId = recipientUserId,
                recipientDeviceId = recipientDeviceId,
                messageType = "ENCRYPTED_MESSAGE",
                payload = payload,
                senderTs = senderTs ?: System.currentTimeMillis()
            )).getOrNull()?.toString()
        }

        val content = com.google.protobuf.ByteString.copyFrom(payload)
        val envelope = EnvelopeProtos.Envelope.newBuilder()
            .setMessageType("ENCRYPTED_MESSAGE")
            .setRecipientUserId(recipientUserId)
            .setPayload(content)
            .setSenderTs((senderTs ?: System.currentTimeMillis()).toString())
            .setEphemeral(ephemeral)
            .build()

        val id = nextRequestId()
        val frame = WebSocketResources.WebSocketMessage.newBuilder()
            .setType(WebSocketResources.WebSocketMessage.Type.REQUEST)
            .setRequest(WebSocketResources.WebSocketRequestMessage.newBuilder()
                .setVerb("POST")
                .setPath("/api/v1/message")
                .setBody(envelope.toByteString())
                .setId(id)
                .build())
            .build()

        val deferred = CompletableDeferred<WebSocketResources.WebSocketResponseMessage>()
        pendingRequests[id] = deferred
        webSocket?.send(frame.toByteArray().toByteString())

        try {
            return withTimeoutOrNull(10000L) {
                val response = deferred.await()
                if (response.status == 200) {
                    response.body?.toStringUtf8()
                } else null
            }
        } finally {
            pendingRequests.remove(id)
        }
    }

    suspend fun sendTypingStart(recipientUserId: String) {
        sendEnchantMessage(recipientUserId, ByteArray(0), "TYPING_START")
    }

    suspend fun sendTypingStop(recipientUserId: String) {
        sendEnchantMessage(recipientUserId, ByteArray(0), "TYPING_STOP")
    }

    suspend fun sendDeliveryReceipt(envelopeId: String, senderUserId: String) {
        val receiptPayload = envelopeId.toByteArray()
        sendEnchantMessage(senderUserId, receiptPayload, "DELIVERY_RECEIPT")
    }

    suspend fun sendReadReceipt(envelopeId: String, senderUserId: String) {
        val receiptPayload = envelopeId.toByteArray()
        sendEnchantMessage(senderUserId, receiptPayload, "READ_RECEIPT")
    }

    suspend fun sendCallOffer(recipientUserId: String, sdp: String): Boolean {
        return sendCallMessage(recipientUserId, sdp.toByteArray(), "CALL_OFFER")
    }

    suspend fun sendCallAnswer(recipientUserId: String, sdp: String): Boolean {
        return sendCallMessage(recipientUserId, sdp.toByteArray(), "CALL_ANSWER")
    }

    suspend fun sendCallIce(recipientUserId: String, candidate: String): Boolean {
        return sendCallMessage(recipientUserId, candidate.toByteArray(), "CALL_ICE")
    }

    suspend fun sendCallEnd(recipientUserId: String): Boolean {
        return sendCallMessage(recipientUserId, ByteArray(0), "CALL_END")
    }

    suspend fun requestRESTFallback(message: OutgoingMessage): Result<Any> {
        val jsonMap = mutableMapOf(
            "recipient_user_id" to kotlinx.serialization.json.JsonPrimitive(message.recipientUserId),
            "message_type" to kotlinx.serialization.json.JsonPrimitive(message.messageType),
            "payload" to kotlinx.serialization.json.JsonPrimitive(
                java.util.Base64.getUrlEncoder().encodeToString(message.payload)
            ),
            "sender_ts" to kotlinx.serialization.json.JsonPrimitive(message.senderTs)
        )
        message.recipientDeviceId?.let { deviceId ->
            jsonMap["recipient_device_id"] = kotlinx.serialization.json.JsonPrimitive(deviceId)
        }
        return apiClient?.post("/v1/messages/send", kotlinx.serialization.json.JsonObject(jsonMap))
            ?: Result.failure(Exception("ApiClient not initialized"))
    }

    private suspend fun authenticate(ws: WebSocket, jwt: String): Boolean {
        val id = nextRequestId()
        val body = com.google.protobuf.ByteString.copyFrom(jwt.toByteArray())
        val frame = WebSocketResources.WebSocketMessage.newBuilder()
            .setType(WebSocketResources.WebSocketMessage.Type.REQUEST)
            .setRequest(WebSocketResources.WebSocketRequestMessage.newBuilder()
                .setVerb("POST")
                .setPath("/v1/auth")
                .setBody(body)
                .setId(id)
                .build())
            .build()

        val deferred = CompletableDeferred<WebSocketResources.WebSocketResponseMessage>()
        pendingRequests[id] = deferred
        ws.send(frame.toByteArray().toByteString())

        try {
            return withTimeoutOrNull(10000L) {
                val response = deferred.await()
                response.status == 200
            } ?: run {
                deferred.completeExceptionally(java.util.concurrent.TimeoutException("Auth timed out"))
                false
            }
        } finally {
            pendingRequests.remove(id)
        }
    }

    private fun handleFrame(data: ByteArray) {
        try {
            val message = WebSocketResources.WebSocketMessage.parseFrom(data)
            when (message.type) {
                WebSocketResources.WebSocketMessage.Type.RESPONSE -> {
                    val response = message.response
                    pendingRequests[response.id]?.complete(response)
                }
                WebSocketResources.WebSocketMessage.Type.REQUEST -> {
                    val request = message.request
                    if (request.verb == "PUT" && request.path == "/api/v1/message") {
                        try {
                            val envelope = EnvelopeProtos.Envelope.parseFrom(request.body)
                            val replyToken = request.headersList.firstOrNull { it.startsWith("X-Reply-Token:") }
                                ?.substringAfter("X-Reply-Token:")?.trim()?.ifEmpty { null }
                            val isSealed = envelope.sealed
                            val messageType = envelope.messageType
                            _incomingMessages.tryEmit(IncomingEnvelope(
                                envelopeId = envelope.envelopeId.ifEmpty { null },
                                senderUserId = if (isSealed) null else envelope.senderUserId.ifEmpty { null },
                                senderDeviceId = envelope.senderDeviceId.ifEmpty { null },
                                messageType = messageType,
                                payload = envelope.payload.toByteArray(),
                                serverTimestamp = if (envelope.hasServerTs()) envelope.serverTs else null,
                                ephemeral = if (isSealed) true else envelope.ephemeral,
                                replyToken = if (replyToken != null) replyToken else envelope.replyToken.ifEmpty { null },
                                requestId = request.id
                            ))
                        } catch (e: Exception) {
                            Log.w("WS", "Envelope parse failed, sending NACK")
                            sendResponse(request.id, 400, "Parse error: ${e.message}")
                        }
                    }
                }
                else -> {}
            }
        } catch (e: Exception) {
            Log.e("Enchant", "handleFrame error", e)
            _connectionErrors.tryEmit(ConnectionError(5000, "Frame processing failed: ${e.message}"))
        }
    }

    /**
     * Send an ack (200) or NACK (400) for an incoming message envelope.
     * Must only be called after the envelope has been processed (decrypted + persisted),
     * so a processing failure causes the server to redeliver instead of losing the message.
     */
    fun sendEnvelopeAck(requestId: Long?, success: Boolean) {
        if (requestId == null) return
        sendResponse(requestId, if (success) 200 else 400, if (success) "OK" else "Processing failed")
    }

    private fun sendResponse(requestId: Long, status: Int, message: String) {
        try {
            val response = WebSocketResources.WebSocketMessage.newBuilder()
                .setType(WebSocketResources.WebSocketMessage.Type.RESPONSE)
                .setResponse(WebSocketResources.WebSocketResponseMessage.newBuilder()
                    .setId(requestId)
                    .setStatus(status)
                    .setMessage(message)
                    .build())
                .build()
            webSocket?.send(response.toByteArray().toByteString())
        } catch (e: Exception) {
            Log.w("WS", "Failed to send response", e)
        }
    }

    private fun startKeepAlive(ws: WebSocket) {
        keepAliveJob?.cancel()
        keepAliveJob = scope?.launch {
            while (isActive) {
                delay(30000)
                if (_connectionState.value != ConnectionState.CONNECTED) break
                val id = nextRequestId()
                val frame = WebSocketResources.WebSocketMessage.newBuilder()
                    .setType(WebSocketResources.WebSocketMessage.Type.REQUEST)
                    .setRequest(WebSocketResources.WebSocketRequestMessage.newBuilder()
                        .setVerb("GET")
                        .setPath("/v1/keepalive")
                        .setId(id)
                        .build())
                    .build()
                ws.send(frame.toByteArray().toByteString())
            }
        }
    }

    private suspend fun scheduleReconnect() {
        if (retryCount >= maxRetryCount) {
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }
        val baseDelay = minOf(1000L * (1 shl retryCount), 30000L)
        val jitter = (baseDelay * 0.25 * Random.nextDouble()).toLong()
        val delay = baseDelay + if (Random.nextBoolean()) jitter else -jitter
        retryCount++
        delay(delay.coerceAtLeast(1000L))
        connect()
    }

    private suspend fun sendEnchantMessage(recipientUserId: String, payload: ByteArray, messageType: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        val content = com.google.protobuf.ByteString.copyFrom(payload)
        val envelope = EnvelopeProtos.Envelope.newBuilder()
            .setMessageType(messageType)
            .setRecipientUserId(recipientUserId)
            .setPayload(content)
            .setEphemeral(messageType.startsWith("TYPING_"))
            .setSenderTs(System.currentTimeMillis().toString())
            .build()

        val id = nextRequestId()
        val frame = WebSocketResources.WebSocketMessage.newBuilder()
            .setType(WebSocketResources.WebSocketMessage.Type.REQUEST)
            .setRequest(WebSocketResources.WebSocketRequestMessage.newBuilder()
                .setVerb("POST")
                .setPath("/api/v1/message")
                .setBody(envelope.toByteString())
                .setId(id)
                .build())
            .build()
        webSocket?.send(frame.toByteArray().toByteString())
    }

    private suspend fun sendCallMessage(recipientUserId: String, data: ByteArray, messageType: String): Boolean {
        if (_connectionState.value != ConnectionState.CONNECTED) return false
        val content = com.google.protobuf.ByteString.copyFrom(data)
        val envelope = EnvelopeProtos.Envelope.newBuilder()
            .setMessageType(messageType)
            .setRecipientUserId(recipientUserId)
            .setPayload(content)
            .setSenderTs(System.currentTimeMillis().toString())
            .setUrgent(true)
            .build()

        val id = nextRequestId()
        val frame = WebSocketResources.WebSocketMessage.newBuilder()
            .setType(WebSocketResources.WebSocketMessage.Type.REQUEST)
            .setRequest(WebSocketResources.WebSocketRequestMessage.newBuilder()
                .setVerb("POST")
                .setPath("/api/v1/message")
                .setBody(envelope.toByteString())
                .setId(id)
                .build())
            .build()
        webSocket?.send(frame.toByteArray().toByteString())
        return true
    }

    private fun isJwtExpired(jwt: String): Boolean {
        return try {
            val parts = jwt.split(".")
            if (parts.size == 3 && parts.none { it.isEmpty() }) {
                val payload = java.util.Base64.getUrlDecoder().decode(parts[1])
                val payloadStr = payload.decodeToString()
                val json = kotlinx.serialization.json.Json.parseToJsonElement(payloadStr).jsonObject
                val exp = json["exp"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                System.currentTimeMillis() / 1000 >= exp
            } else {
                Log.w("WS", "Malformed JWT in isJwtExpired")
                true
            }
        } catch (e: Exception) { Log.w("WS", "JWT check failed"); true }
    }

    private suspend fun tryRefreshJwt(): String? {
        val refreshToken = SecurePreferences.getString("auth.refresh_token") ?: return null
        return try {
            val body = kotlinx.serialization.json.buildJsonObject {
                put("refresh_token", kotlinx.serialization.json.JsonPrimitive(refreshToken))
            }
            val jsonBody = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(), body
            )
            val request = Request.Builder()
                .url("${AppConfig.gatewayUrl}/v1/auth/refresh")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            val response = refreshClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    val parsed = json.parseToJsonElement(responseBody).jsonObject
                    val newJwt = parsed["access_token"]?.jsonPrimitive?.content
                    val newRefresh = parsed["refresh_token"]?.jsonPrimitive?.content
                    if (newJwt != null) {
                        SecurePreferences.putString("auth.jwt", newJwt)
                        if (newRefresh != null) {
                            SecurePreferences.putString("auth.refresh_token", newRefresh)
                        }
                        newJwt
                    } else null
                } else null
            } else null
        } catch (e: Exception) { Log.e("WS", "JWT refresh failed"); null }
    }

    private fun startForegroundService() {
        val ctx = applicationContext ?: return
        try {
            val intent = Intent(ctx, WebSocketForegroundService::class.java).apply {
                action = WebSocketForegroundService.ACTION_CONNECT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("WS", "Failed to start foreground service", e)
        }
    }

    private fun stopForegroundService() {
        val ctx = applicationContext ?: return
        try {
            val intent = Intent(ctx, WebSocketForegroundService::class.java).apply {
                action = WebSocketForegroundService.ACTION_DISCONNECT
            }
            ctx.startService(intent)
        } catch (e: Exception) {
            Log.e("WS", "Failed to stop foreground service", e)
        }
    }

    @VisibleForTesting
    fun resetForTesting() {
        initialized = false
        scope?.cancel()
        scope = null
        webSocket = null
        requestIdCounter.set(0)
        consecutive401s = 0
        retryCount = 0
        pendingRequests.clear()
        keepAliveJob?.cancel()
        keepAliveJob = null
        _connectionState.value = ConnectionState.DISCONNECTED
        apiClient = null
        applicationContext = null
    }
}
