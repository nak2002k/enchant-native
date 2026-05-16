package org.enchant.core.calls

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.enchant.core.base.DI
import org.enchant.core.base.SecurePreferences
import org.enchant.core.database.entity.CallLogEntity
import org.enchant.core.database.util.CursorMapper
import org.enchant.core.network.ApiClient
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
    private var initialized = false
    private val incomingIceCandidates = ConcurrentLinkedQueue<String>()

    private val apiClient: ApiClient get() = DI.apiClient
    private val pool get() = DI.databasePool

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

        val sdp = WebRtcService.createOffer(peerConnection!!)
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

        val sdp = WebRtcService.createAnswer(peerConnection!!)
        if (sdp != null) {
            val remoteId = _callState.value.remoteUserId ?: return
            sendCallSignaling(remoteId, CallMessage.Answer(sdp))
            observerRegistry.notifyAnswerSent(remoteId, sdp)
        }

        incomingIceCandidates.forEach { WebRtcService.addIceCandidate(peerConnection!!, it) }
        incomingIceCandidates.clear()
        startDurationTimer()
    }

    fun denyCall() {
        val remoteId = _callState.value.remoteUserId ?: return
        sendCallSignaling(remoteId, CallMessage.End)
        observerRegistry.notifyHangupSent(remoteId)
        cleanup()
    }

    fun endCall() {
        val state = _callState.value
        if (state.status == CallStatusEnum.IDLE) return
        val remoteId = state.remoteUserId
        if (remoteId != null) {
            sendCallSignaling(remoteId, CallMessage.End)
            observerRegistry.notifyHangupSent(remoteId)
        }
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
        if (newVideo) {
            localStream = localStream
        } else {
            WebRtcService.toggleVideoTrack(localStream, false)
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
            sendCallSignaling(senderUserId, CallMessage.End)
            return
        }
        _callState.value = CallState(
            status = CallStatusEnum.RINGING,
            remoteUserId = senderUserId,
            callId = callId,
            isVideoCall = true
        )
        CoroutineScope(Dispatchers.Default).launch {
            AudioRouter.vibrate(AppConfig.applicationContext ?: return@launch)
            AudioRouter.startIncomingRinger()
        }
        observerRegistry.notifyCallStarted(senderUserId, true)
    }

    fun handleReceivedAnswer(sdp: String) {
        val pc = peerConnection ?: return
        WebRtcService.setRemoteDescription(pc, sdp, SessionDescription.Type.ANSWER)
        _callState.value = _callState.value.copy(status = CallStatusEnum.CONNECTED)
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

    suspend fun retrieveTurnServers() {
        if (System.currentTimeMillis() - turnServersFetchedAt < 3_600_000 && turnServers.isNotEmpty()) return
        try {
            val response = apiClient.get("/v1/calls/turn-credentials")
            response.onSuccess { json ->
                turnServers = listOf(IceServer(
                    urls = listOf("stun:stun.l.google.com:19302"),
                    username = json["username"]?.kotlinx.serialization.json.jsonPrimitive?.content,
                    credential = json["credential"]?.kotlinx.serialization.json.jsonPrimitive?.content
                ))
                turnServersFetchedAt = System.currentTimeMillis()
            }
        } catch (_: Exception) {
            turnServers = listOf(IceServer(urls = listOf("stun:stun.l.google.com:19302")))
        }
    }

    fun getCallLogs(): Flow<List<CallLogEntity>> = kotlinx.coroutines.flow.flow {
        val logs = pool.read {
            CursorMapper.mapToList<CallLogEntity>(
                it.rawQuery("SELECT * FROM call_logs ORDER BY ended_at DESC LIMIT 100", null)
            )
        }
        emit(logs)
    }

    suspend fun insertMissedCall(peerUserId: String, isVideo: Boolean, timestamp: Long = System.currentTimeMillis()) {
        val callId = UUID.randomUUID().toString()
        pool.write { db ->
            db.execSQL("""
                INSERT INTO call_logs (call_id, remote_user_id, type, direction, status, ended_at)
                VALUES (?, ?, ?, 'incoming', 'missed', ?)
            """, arrayOf(callId, peerUserId, if (isVideo) "video" else "audio", timestamp.toString()))
        }
    }

    fun sendCallSignaling(remoteUserId: String, message: CallMessage) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = when (message) {
                    is CallMessage.Offer -> "offer:${message.sdp}"
                    is CallMessage.Answer -> "answer:${message.sdp}"
                    is CallMessage.Ice -> "ice:${message.candidate}"
                    is CallMessage.End -> "hangup"
                }
                apiClient.post("/v1/messages/send", kotlinx.serialization.json.buildJsonObject {
                    put("recipient_user_id", remoteUserId)
                    put("message_type", "SIGNAL_MESSAGE")
                    put("payload", java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.encodeToByteArray()))
                })
            } catch (_: Exception) {}
        }
    }

    fun selectAudioDevice(device: AudioDevice) = AudioRouter.selectAudioDevice(device)

    suspend fun peekGroupCall(groupId: String): PeekInfo? {
        return try {
            val response = apiClient.get("/v1/groups/$groupId")
            response.getOrNull()?.let {
                PeekInfo(activeParticipants = 0, maxParticipants = 500, isActive = false)
            }
        } catch (_: Exception) { null }
    }

    suspend fun peekCallLink(roomId: String): PeekInfo? {
        return try {
            PeekInfo(activeParticipants = 0, maxParticipants = 50, isActive = false)
        } catch (_: Exception) { null }
    }

    fun raiseHand(raised: Boolean) {}
    fun react(emoji: String) {}
    fun requestRemoteMute(participantId: String) {}
    fun removeParticipant(participantId: String) {}

    private val pcObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) { incomingIceCandidates.add(candidate.sdp) }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
        override fun onSignalingChange(state: SignalingState?) {}
        override fun onIceConnectionChange(state: IceConnectionState?) {
            if (state == IceConnectionState.CONNECTED) {
                _callState.value = _callState.value.copy(status = CallStatusEnum.CONNECTED)
            }
        }
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(state: IceGatheringState?) {}
        override fun onAddStream(stream: MediaStream?) { remoteStream = stream }
        override fun onRemoveStream(stream: MediaStream?) { remoteStream = null }
        override fun onDataChannel(channel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, tracks: Array<out MediaStream>?) {}
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = CoroutineScope(Dispatchers.Default).launch {
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                pool.write { db ->
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
                }
            } catch (_: Exception) {}
        }
    }

    private fun cleanup() {
        durationJob?.cancel()
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
