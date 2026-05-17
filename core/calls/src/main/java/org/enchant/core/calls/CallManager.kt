package org.enchant.core.calls

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.core.base.AppConfig
import org.enchant.core.base.SecurePreferences
import org.enchant.core.database.DatabasePool
import org.enchant.core.network.ApiClient
import org.enchant.core.network.WebSocketManager
import org.enchant.protos.CallMessageProtos
import org.webrtc.*
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

object CallManager {
    private val _callState = MutableStateFlow(CallState())
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val observerRegistry = CallObserverRegistry()
    private var peerConnection: PeerConnection? = null
    private var localStream: MediaStream? = null
    private var remoteStream: MediaStream? = null
    private var iceCandidateBuffer = mutableListOf<IceCandidate>()
    private var turnServers: List<IceServer> = emptyList()
    private var turnServersFetchedAt: Long = 0
    private var durationJob: Job? = null
    @Volatile
    private var initialized = false
    private var mutedBeforeReconnect = false
    private var videoBeforeReconnect = false
    private var offerReceivedAt: Long = 0
    private val incomingIceCandidates = ConcurrentLinkedQueue<String>()
    private var ringGroupEnabled = true
    private var capturer: CameraVideoCapturer? = null

    private var _apiClient: ApiClient? = null
    private val webSocket get() = WebSocketManager
    private val pool get() = DatabasePool.instance
    private val callScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun setApiClient(client: ApiClient) { _apiClient = client }
    private val apiClient get() = _apiClient ?: error("ApiClient not set. Call setApiClient() first.")

    suspend fun init() {
        if (initialized) return
        WebRtcService.init(AppConfig.applicationContext ?: return)
        AudioRouter.init(AppConfig.applicationContext ?: return)
        initialized = true
    }

    fun registerObserver(observer: CallObserver) = observerRegistry.registerObserver(observer)
    fun unregisterObserver(observer: CallObserver) = observerRegistry.unregisterObserver(observer)

    suspend fun startOutgoingCall(remoteUserId: String, isVideo: Boolean) {
        if (_callState.value.status != CallStatusEnum.IDLE) {
            _callState.value = _callState.value.copy(error = "Already in a call")
            return
        }
        val selfId = SecurePreferences.getString("auth.user_id") ?: return
        _callState.value = CallState(
            status = CallStatusEnum.CALLING,
            remoteUserId = remoteUserId,
            isVideoCall = isVideo,
            callId = UUID.randomUUID().toString()
        )
        observerRegistry.notifyCallStarted(remoteUserId, isVideo)

        retrieveTurnServers()
        localStream = WebRtcService.getLocalStream(isVideo)
        peerConnection = WebRtcService.createPeerConnection(turnServers, pcObserver)
        if (peerConnection == null) { endCall(); return }

        localStream?.audioTracks?.firstOrNull()?.let { peerConnection?.addTrack(it, listOf("stream")) }
        localStream?.videoTracks?.firstOrNull()?.let { peerConnection?.addTrack(it, listOf("stream")) }

        val pc = peerConnection ?: return
        val sdp = WebRtcService.createOffer(pc)
        if (sdp != null) {
            sendCallSignaling(remoteUserId, CallMessage.Offer(sdp))
            observerRegistry.notifyOfferSent(remoteUserId, sdp)
        }
        startDurationTimer()
    }

    suspend fun acceptCall(callId: String, withVideo: Boolean) {
        if (_callState.value.status != CallStatusEnum.RINGING) return
        _callState.value = _callState.value.copy(status = CallStatusEnum.CONNECTING)

        retrieveTurnServers()
        localStream = WebRtcService.getLocalStream(withVideo)
        peerConnection = WebRtcService.createPeerConnection(turnServers, pcObserver)
        if (peerConnection == null) { endCall(); return }

        localStream?.audioTracks?.firstOrNull()?.let { peerConnection?.addTrack(it, listOf("stream")) }
        localStream?.videoTracks?.firstOrNull()?.let { peerConnection?.addTrack(it, listOf("stream")) }

        val pc = peerConnection ?: return
        val sdp = WebRtcService.createAnswer(pc)
        if (sdp != null) {
            val remoteId = _callState.value.remoteUserId ?: return
            sendCallSignaling(remoteId, CallMessage.Answer(sdp))
            observerRegistry.notifyAnswerSent(remoteId, sdp)
        }

        incomingIceCandidates.forEach { WebRtcService.addIceCandidate(pc, it) }
        incomingIceCandidates.clear()
        startDurationTimer()
    }

    fun denyCall() {
        val remoteId = _callState.value.remoteUserId ?: return
        callScope.launch(Dispatchers.IO) { sendCallSignaling(remoteId, CallMessage.End) }
        observerRegistry.notifyHangupSent(remoteId)
        cleanup()
    }

    fun endCall() {
        val state = _callState.value
        if (state.status == CallStatusEnum.IDLE) return
        val remoteId = state.remoteUserId
        if (remoteId != null) {
            callScope.launch(Dispatchers.IO) { sendCallSignaling(remoteId, CallMessage.End) }
            observerRegistry.notifyHangupSent(remoteId)
        }
        AudioRouter.stopRinger()
        val summary = if (state.durationSeconds > 0) {
            CallSummary(state.durationSeconds, state.isVideoCall, state.status == CallStatusEnum.CALLING)
        } else null
        observerRegistry.notifyCallEnded(CallEndReason.HANGUP_LOCAL, summary)
        insertCallLog(state)
        cleanup()
    }

    fun toggleMute() {
        val newMuted = !_callState.value.isMuted
        _callState.value = _callState.value.copy(isMuted = newMuted)
        WebRtcService.toggleAudioTrack(localStream, !newMuted)
    }

    fun toggleVideo() {
        val newVideo = !_callState.value.isVideoCall
        _callState.value = _callState.value.copy(isVideoCall = newVideo)
        callScope.launch(Dispatchers.Default) {
            if (newVideo) {
                val videoStream = WebRtcService.getLocalStream(true)
                videoStream?.videoTracks?.firstOrNull()?.let { track ->
                    localStream?.addTrack(track)
                    peerConnection?.addTrack(track, listOf("stream"))
                }
                localStream = videoStream
            } else {
                localStream?.videoTracks?.firstOrNull()?.let { track ->
                    val sender = peerConnection?.senders?.firstOrNull {
                        it.track()?.kind() == "video"
                    }
                    if (sender != null) {
                        peerConnection?.removeTrack(sender)
                    }
                    localStream?.removeTrack(track)
                }
            }
        }
    }

    fun flipCamera() {
        WebRtcService.switchCamera(localStream?.videoTracks?.firstOrNull())
    }

    fun toggleSpeaker() {
        val newSpeaker = !_callState.value.isSpeakerOn
        _callState.value = _callState.value.copy(isSpeakerOn = newSpeaker)
        WebRtcService.setSpeakerphoneOn(newSpeaker)
        AudioRouter.setSpeakerphoneOn(newSpeaker)
    }

    fun setOnHold(hold: Boolean) {
        _callState.value = _callState.value.copy(isOnHold = hold)
        WebRtcService.toggleAudioTrack(localStream, !hold)
    }

    fun handleReceivedOffer(senderUserId: String, sdp: String, callId: String) {
        if (_callState.value.status != CallStatusEnum.IDLE) {
            callScope.launch(Dispatchers.IO) { sendCallSignaling(senderUserId, CallMessage.End) }
            return
        }
        offerReceivedAt = System.currentTimeMillis()
        _callState.value = CallState(
            status = CallStatusEnum.RINGING,
            remoteUserId = senderUserId,
            callId = callId,
            isVideoCall = true
        )
        callScope.launch(Dispatchers.Default) {
            AudioRouter.vibrate(AppConfig.applicationContext ?: return@launch)
            AudioRouter.startIncomingRinger()
        }
        observerRegistry.notifyCallStarted(senderUserId, true)
        callScope.launch(Dispatchers.Default) {
            delay(30000)
            if (_callState.value.status == CallStatusEnum.RINGING) {
                handleReceivedOfferExpired()
            }
        }
    }

    fun handleReceivedOfferExpired() {
        if (_callState.value.status != CallStatusEnum.RINGING) return
        AudioRouter.stopRinger()
        observerRegistry.notifyCallEnded(CallEndReason.TIMEOUT, null)
        insertCallLog(_callState.value)
        cleanup()
    }

    fun handleReceivedAnswer(sdp: String) {
        val pc = peerConnection ?: return
        WebRtcService.setRemoteDescription(pc, sdp, SessionDescription.Type.ANSWER)
        _callState.value = _callState.value.copy(status = CallStatusEnum.CONNECTED)
        AudioRouter.stopRinger()
    }

    fun handleReceivedIce(candidate: String) {
        if (_callState.value.status != CallStatusEnum.CONNECTING &&
            _callState.value.status != CallStatusEnum.CONNECTED) {
            incomingIceCandidates.add(candidate)
            return
        }
        val pc = peerConnection ?: return
        WebRtcService.addIceCandidate(pc, candidate)
    }

    fun handleReceivedHangup() {
        val prevStatus = _callState.value.status
        if (prevStatus == CallStatusEnum.IDLE) return
        val summary = if (_callState.value.durationSeconds > 0) {
            CallSummary(_callState.value.durationSeconds, _callState.value.isVideoCall, false)
        } else null
        AudioRouter.stopRinger()
        observerRegistry.notifyCallEnded(CallEndReason.HANGUP_REMOTE, summary)
        insertCallLog(_callState.value)
        cleanup()
    }

    suspend fun handleCallReconnect(newSession: String) {
        val prevMuted = _callState.value.isMuted
        val prevVideo = _callState.value.isVideoCall
        val prevSpeaker = _callState.value.isSpeakerOn
        _callState.value = _callState.value.copy(status = CallStatusEnum.RECONNECTING)
        cleanup()
        retrieveTurnServers()
        localStream = WebRtcService.getLocalStream(prevVideo)
        peerConnection = WebRtcService.createPeerConnection(turnServers, pcObserver)
        localStream?.audioTracks?.firstOrNull()?.let { peerConnection?.addTrack(it, listOf("stream")) }
        localStream?.videoTracks?.firstOrNull()?.let { peerConnection?.addTrack(it, listOf("stream")) }
        WebRtcService.toggleAudioTrack(localStream, !prevMuted)
        WebRtcService.setSpeakerphoneOn(prevSpeaker)
        _callState.value = _callState.value.copy(
            status = CallStatusEnum.CONNECTING,
            isMuted = prevMuted,
            isVideoCall = prevVideo,
            isSpeakerOn = prevSpeaker
        )
        startDurationTimer()
    }

    fun setRingGroup(shouldRing: Boolean) {
        ringGroupEnabled = shouldRing
    }

    suspend fun sendGroupCallUpdateMessage(groupId: String, eraId: String, isCallFull: Boolean) {
        try {
            val content = buildJsonObject {
                put("groupId", groupId)
                put("eraId", eraId)
                put("isCallFull", isCallFull.toString())
            }
            apiClient.post("/v1/groups/$groupId/messages", content)
        } catch (e: Exception) { Log.w("Calls", "Group call update failed: ${e.message}") }
    }

    suspend fun retrieveTurnServers() {
        if (System.currentTimeMillis() - turnServersFetchedAt < 3_600_000 && turnServers.isNotEmpty()) return
        try {
            val response = apiClient.get("/v1/calls/turn-credentials")
            response.onSuccess { json ->
                turnServers = listOf(IceServer(
                    urls = listOf("stun:stun.l.google.com:19302"),
                    username = json["username"]?.jsonPrimitive?.content,
                    credential = json["credential"]?.jsonPrimitive?.content
                ))
                turnServersFetchedAt = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.w("Calls", "TURN fetch failed: ${e.message}")
            turnServers = listOf(IceServer(urls = listOf("stun:stun.l.google.com:19302")))
        }
    }

    fun getCallLogs(): Flow<List<CallLogEntry>> = flow {
        val db = pool?.writer ?: return@flow emit(emptyList())
        val cursor = db.rawQuery("SELECT * FROM call_logs ORDER BY ended_at DESC LIMIT 100", null)
        val logs = mutableListOf<CallLogEntry>()
        cursor.use { c ->
            while (c.moveToNext()) {
                logs.add(CallLogEntry(
                    callId = c.getString(c.getColumnIndexOrThrow("call_id")),
                    remoteUserId = c.getString(c.getColumnIndexOrThrow("remote_user_id")),
                    type = when (c.getString(c.getColumnIndexOrThrow("type"))) {
                        "video" -> CallType.VIDEO
                        "group_audio" -> CallType.GROUP_AUDIO
                        "group_video" -> CallType.GROUP_VIDEO
                        else -> CallType.AUDIO
                    },
                    direction = if (c.getString(c.getColumnIndexOrThrow("direction")) == "incoming") CallDirection.INCOMING else CallDirection.OUTGOING,
                    status = when (c.getString(c.getColumnIndexOrThrow("status"))) {
                        "missed" -> CallStatus.MISSED
                        "answered" -> CallStatus.ANSWERED
                        "cancelled" -> CallStatus.CANCELLED
                        else -> CallStatus.OUTGOING
                    },
                    durationSeconds = c.getInt(c.getColumnIndexOrThrow("duration_seconds")),
                    timestamp = c.getLong(c.getColumnIndexOrThrow("ended_at"))
                ))
            }
        }
        emit(logs)
    }

    suspend fun insertMissedCall(peerUserId: String, isVideo: Boolean, timestamp: Long = System.currentTimeMillis()) {
        val callId = UUID.randomUUID().toString()
        val db = pool?.writer ?: return
        db.execSQL("""
            INSERT INTO call_logs (call_id, remote_user_id, type, direction, status, ended_at)
            VALUES (?, ?, ?, 'incoming', 'missed', ?)
        """, arrayOf(callId, peerUserId, if (isVideo) "video" else "audio", timestamp.toString()))
    }

    suspend fun sendCallSignaling(remoteUserId: String, message: CallMessage): Boolean {
        return when (message) {
            is CallMessage.Offer -> webSocket.sendCallOffer(remoteUserId, message.sdp)
            is CallMessage.Answer -> webSocket.sendCallAnswer(remoteUserId, message.sdp)
            is CallMessage.Ice -> webSocket.sendCallIce(remoteUserId, message.candidate)
            is CallMessage.End -> webSocket.sendCallEnd(remoteUserId)
        }
    }

    suspend fun sendCallMessage(remoteUserId: String, callMessage: CallMessageProtos.CallMessage) {
        val payload = callMessage.toByteArray()
        webSocket.sendMessage(recipientUserId = remoteUserId, payload = payload, ephemeral = true)
    }

    fun selectAudioDevice(device: AudioDevice) = AudioRouter.selectAudioDevice(device)

    suspend fun peekGroupCall(groupId: String): PeekInfo? {
        return try {
            val response = apiClient.get("/v1/groups/$groupId/peek")
            response.getOrNull()?.let { json ->
                PeekInfo(
                    activeParticipants = json["active_participants"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    maxParticipants = json["max_participants"]?.jsonPrimitive?.content?.toIntOrNull() ?: 500,
                    isActive = json["is_active"]?.jsonPrimitive?.content?.toBoolean() ?: false
                )
            }
        } catch (e: Exception) { Log.w("Calls", "Peek group failed: ${e.message}"); null }
    }

    suspend fun peekCallLink(roomId: String): PeekInfo? {
        return try {
            val response = apiClient.get("/v1/calls/links/$roomId/peek")
            response.getOrNull()?.let { json ->
                PeekInfo(
                    activeParticipants = json["active_participants"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    maxParticipants = json["max_participants"]?.jsonPrimitive?.content?.toIntOrNull() ?: 50,
                    isActive = json["is_active"]?.jsonPrimitive?.content?.toBoolean() ?: false
                )
            }
        } catch (e: Exception) { Log.w("Calls", "Peek link failed: ${e.message}"); null }
    }

    fun raiseHand(raised: Boolean) {
        _callState.value = _callState.value.copy(isHandRaised = raised)
    }

    fun react(emoji: String) {
        val remoteId = _callState.value.remoteUserId ?: return
        callScope.launch(Dispatchers.IO) {
            try {
                val callMessage = CallMessageProtos.CallMessage.newBuilder().build()
                sendCallMessage(remoteId, callMessage)
            } catch (e: Exception) { Log.w("Calls", "React failed: ${e.message}") }
        }
    }

    fun requestRemoteMute(participantId: String) {
        callScope.launch(Dispatchers.IO) {
            try {
                val callMessage = CallMessageProtos.CallMessage.newBuilder().build()
                webSocket.sendMessage(recipientUserId = participantId, payload = callMessage.toByteArray(), ephemeral = true)
            } catch (e: Exception) { Log.w("Calls", "Remote mute failed: ${e.message}") }
        }
    }

    fun removeParticipant(participantId: String) {
        val groupId = _callState.value.remoteUserId ?: return
        callScope.launch(Dispatchers.IO) {
            try {
                apiClient.del("/v1/groups/$groupId/members/$participantId")
            } catch (e: Exception) { Log.w("Calls", "Remove participant failed: ${e.message}") }
        }
    }

    private val pcObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            incomingIceCandidates.add("${candidate.sdpMid}|${candidate.sdpMLineIndex}|${candidate.sdp}")
        }
        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) { android.util.Log.v("Calls", "onIceCandidatesRemoved called") }
        override fun onSignalingChange(state: PeerConnection.SignalingState) { android.util.Log.v("Calls", "onSignalingChange called") }
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            if (state == PeerConnection.IceConnectionState.CONNECTED) {
                _callState.value = _callState.value.copy(status = CallStatusEnum.CONNECTED)
            }
        }
        override fun onIceConnectionReceivingChange(p0: Boolean) { android.util.Log.v("Calls", "onIceConnectionReceivingChange called") }
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) { android.util.Log.v("Calls", "onIceGatheringChange called") }
        override fun onAddStream(stream: MediaStream) { remoteStream = stream }
        override fun onRemoveStream(stream: MediaStream) { remoteStream = null }
        override fun onDataChannel(channel: DataChannel) { android.util.Log.v("Calls", "onDataChannel called") }
        override fun onRenegotiationNeeded() { android.util.Log.v("Calls", "onRenegotiationNeeded called") }
        override fun onAddTrack(receiver: RtpReceiver, tracks: Array<MediaStream>) {
            tracks.firstOrNull()?.let { remoteStream = it }
        }
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = callScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                val current = _callState.value
                if (current.status == CallStatusEnum.CONNECTED || current.status == CallStatusEnum.CALLING) {
                    _callState.value = current.copy(durationSeconds = current.durationSeconds + 1)
                }
            }
        }
    }

    private fun insertCallLog(state: CallState) {
        val callId = state.callId ?: UUID.randomUUID().toString()
        val remoteId = state.remoteUserId ?: return
        callScope.launch(Dispatchers.IO) {
            try {
                val db = pool?.writer ?: return@launch
                db.execSQL("""
                    INSERT OR REPLACE INTO call_logs (call_id, remote_user_id, type, direction, duration_seconds, status, ended_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """, arrayOf(
                    callId, remoteId,
                    if (state.isVideoCall) "video" else "audio",
                    "outgoing",
                    state.durationSeconds.toString(),
                    if (state.durationSeconds > 0) "answered" else "cancelled",
                    System.currentTimeMillis().toString()
                ))
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
        }
    }

    fun resetForTest() {
        cleanup()
        turnServers = emptyList()
        turnServersFetchedAt = 0
        offerReceivedAt = 0
        initialized = false
    }

    private fun cleanup() {
        durationJob?.cancel()
        callScope.coroutineContext.cancelChildren()
        AudioRouter.stopRinger()
        AudioRouter.stopAudio(playDisconnect = true)
        peerConnection?.let { WebRtcService.dispose(it) }
        peerConnection = null
        localStream = null
        remoteStream = null
        iceCandidateBuffer.clear()
        incomingIceCandidates.clear()
        _callState.value = CallState()
    }
}

sealed class CallMessage {
    data class Offer(val sdp: String) : CallMessage()
    data class Answer(val sdp: String) : CallMessage()
    data class Ice(val candidate: String) : CallMessage()
    data object End : CallMessage()
}
