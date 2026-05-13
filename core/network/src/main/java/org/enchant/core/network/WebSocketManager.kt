package org.enchant.core.network

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
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
    val ephemeral: Boolean
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
    private var initialized = false
    private var scope: CoroutineScope? = null
    private var webSocket: WebSocket? = null
    private var requestIdCounter = 0L
    private var consecutive401s = 0
    private var retryCount = 0
    private val pendingRequests = ConcurrentHashMap<Long, CompletableDeferred<WebSocketResources.WebSocketResponseMessage>>()
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _incomingMessages = MutableSharedFlow<IncomingEnvelope>(extraBufferCapacity = 100)
    private val _connectionErrors = MutableSharedFlow<ConnectionError>(extraBufferCapacity = 10)
    private var keepAliveJob: Job? = null
    private var apiClient: ApiClient? = null

    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    val incomingMessages: SharedFlow<IncomingEnvelope> = _incomingMessages.asSharedFlow()
    val connectionErrors: SharedFlow<ConnectionError> = _connectionErrors.asSharedFlow()

    fun init() {
        if (initialized) return
        apiClient = ApiClient()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        initialized = true
    }

    suspend fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTING || _connectionState.value == ConnectionState.CONNECTED) return
        _connectionState.value = ConnectionState.CONNECTING

        val jwt = SecurePreferences.getString("auth.jwt")
        if (jwt == null) {
            _connectionState.value = ConnectionState.AUTH_FAILED
            return
        }

        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(AppConfig.wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
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

            override fun onMessage(ws: WebSocket, bytes: ByteByteString) {
                scope?.launch {
                    handleFrame(bytes.toByteArray())
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.RECONNECTING
                scope?.launch {
                    scheduleReconnect()
                }
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
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
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
                messageType = "SIGNAL_MESSAGE",
                payload = payload,
                senderTs = senderTs ?: System.currentTimeMillis()
            )).getOrNull()?.toString()
        }

        val envelope = EnvelopeProtos.Envelope.newBuilder()
            .setType(EnvelopeProtos.Envelope.Type.DOUBLE_RATCHET)
            .setDestinationServiceId(recipientUserId)
            .setContent(ByteString.copyFrom(payload))
            .setClientTimestamp(senderTs ?: System.currentTimeMillis())
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
        webSocket?.send(ByteString.of(*frame.toByteArray()))

        return withTimeoutOrNull(10000L) {
            val response = deferred.await()
            if (response.status == 200) {
                response.body?.toStringUtf8()
            } else null
        }
    }

    suspend fun sendTypingStart(recipientUserId: String) {
        sendEphemeral(recipientUserId, "TYPING_START")
    }

    suspend fun sendTypingStop(recipientUserId: String) {
        sendEphemeral(recipientUserId, "TYPING_STOP")
    }

    suspend fun sendDeliveryReceipt(envelopeId: String, senderUserId: String) {
        sendEphemeral(senderUserId, "DELIVERY_RECEIPT")
    }

    suspend fun sendReadReceipt(envelopeId: String, senderUserId: String) {
        sendEphemeral(senderUserId, "READ_RECEIPT")
    }

    suspend fun sendCallOffer(recipientUserId: String, sdp: String): Boolean {
        return sendCallSignal(recipientUserId, "CALL_OFFER", sdp.toByteArray())
    }

    suspend fun sendCallAnswer(recipientUserId: String, sdp: String): Boolean {
        return sendCallSignal(recipientUserId, "CALL_ANSWER", sdp.toByteArray())
    }

    suspend fun sendCallIce(recipientUserId: String, candidate: String): Boolean {
        return sendCallSignal(recipientUserId, "CALL_ICE", candidate.toByteArray())
    }

    suspend fun sendCallEnd(recipientUserId: String): Boolean {
        return sendCallSignal(recipientUserId, "CALL_END", ByteArray(0))
    }

    suspend fun requestRESTFallback(message: OutgoingMessage): Result<Any> {
        return apiClient?.post("/v1/messages/send", kotlinx.serialization.json.JsonObject(
            mapOf(
                "recipientUserId" to kotlinx.serialization.json.JsonPrimitive(message.recipientUserId),
                "messageType" to kotlinx.serialization.json.JsonPrimitive(message.messageType),
                "payload" to kotlinx.serialization.json.JsonPrimitive(
                    java.util.Base64.getUrlEncoder().encodeToString(message.payload)
                )
            )
        )) ?: Result.failure(Exception("ApiClient not initialized"))
    }

    private suspend fun authenticate(ws: WebSocket, jwt: String): Boolean {
        val id = nextRequestId()
        val frame = WebSocketResources.WebSocketMessage.newBuilder()
            .setType(WebSocketResources.WebSocketMessage.Type.REQUEST)
            .setRequest(WebSocketResources.WebSocketRequestMessage.newBuilder()
                .setVerb("POST")
                .setPath("/v1/auth")
                .setBody(ByteString.copyFrom(jwt.toByteArray()))
                .setId(id)
                .build())
            .build()

        val deferred = CompletableDeferred<WebSocketResources.WebSocketResponseMessage>()
        pendingRequests[id] = deferred
        ws.send(ByteString.of(*frame.toByteArray()))

        return withTimeoutOrNull(10000L) {
            val response = deferred.await()
            response.status == 200
        } ?: false
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
                        val envelope = EnvelopeProtos.Envelope.parseFrom(request.body)
                        _incomingMessages.tryEmit(IncomingEnvelope(
                            envelopeId = envelope.serverGuid,
                            senderUserId = envelope.sourceServiceId.ifEmpty { null },
                            senderDeviceId = if (envelope.hasSourceDeviceId()) envelope.sourceDeviceId.toString() else null,
                            messageType = envelope.type.name,
                            payload = envelope.content.toByteArray(),
                            serverTimestamp = if (envelope.hasServerTimestamp()) envelope.serverTimestamp else null,
                            ephemeral = envelope.ephemeral
                        ))
                    }
                }
                else -> {}
            }
        } catch (_: Exception) {}
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
                ws.send(ByteString.of(*frame.toByteArray()))
            }
        }
    }

    private suspend fun scheduleReconnect() {
        val baseDelay = minOf(1000L * (1 shl retryCount), 30000L)
        val jitter = (baseDelay * 0.25 * Random.nextDouble()).toLong()
        val delay = baseDelay + if (Random.nextBoolean()) jitter else -jitter
        retryCount++
        delay(delay.coerceAtLeast(1000L))
        connect()
    }

    private suspend fun sendEphemeral(recipientUserId: String, messageType: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        val envelope = EnvelopeProtos.Envelope.newBuilder()
            .setType(EnvelopeProtos.Envelope.Type.DOUBLE_RATCHET)
            .setDestinationServiceId(recipientUserId)
            .setEphemeral(true)
            .setClientTimestamp(System.currentTimeMillis())
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
        webSocket?.send(ByteString.of(*frame.toByteArray()))
    }

    private suspend fun sendCallSignal(recipientUserId: String, type: String, data: ByteArray): Boolean {
        if (_connectionState.value != ConnectionState.CONNECTED) return false
        val envelope = EnvelopeProtos.Envelope.newBuilder()
            .setType(EnvelopeProtos.Envelope.Type.DOUBLE_RATCHET)
            .setDestinationServiceId(recipientUserId)
            .setContent(ByteString.copyFrom(data))
            .setClientTimestamp(System.currentTimeMillis())
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
        webSocket?.send(ByteString.of(*frame.toByteArray()))
        return true
    }

    private fun nextRequestId(): Long = ++requestIdCounter
}
