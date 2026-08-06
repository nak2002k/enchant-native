package org.enchant.core.calls

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.enchant.core.calls.action.CallAction
import org.enchant.core.calls.action.processors.IdleActionProcessor
import org.enchant.core.calls.audio.AudioFocusManager
import org.enchant.core.calls.audio.AudioRouter
import org.enchant.core.calls.audio.RingtonePlayer
import org.enchant.core.calls.model.*
import org.enchant.core.calls.notification.CallNotificationManager
import org.enchant.core.calls.observer.CallObserverRegistry
import org.enchant.core.calls.service.CallForegroundService
import org.enchant.core.calls.state.CallServiceState
import org.enchant.core.calls.webrtc.IceCandidateHandler
import org.enchant.core.calls.webrtc.MediaStreamManager
import org.enchant.core.calls.webrtc.SdpHandler
import org.enchant.core.calls.webrtc.StatsCollector
import org.enchant.core.calls.webrtc.WebRtcEngine
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.util.UUID

class DefaultCallManager(
    private val stateMachine: CallStateMachine,
    private val webRtcEngine: WebRtcEngine,
    private val mediaStreamManager: MediaStreamManager,
    private val sdpHandler: SdpHandler,
    private val iceHandler: IceCandidateHandler,
    private val signalingClient: SignalingClient,
    private val audioRouter: AudioRouter,
    private val audioFocusManager: AudioFocusManager,
    private val ringtonePlayer: RingtonePlayer,
    private val notificationManager: CallNotificationManager,
    private val callLogger: CallLogger,
    private val observerRegistry: CallObserverRegistry
) {
    private val _callState = MutableStateFlow(
        CallServiceState(
            actionProcessor = IdleActionProcessor(callLogger, observerRegistry),
            callLogger = callLogger,
            observerRegistry = observerRegistry
        ).callState
    )

    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _serviceState = MutableStateFlow(
        CallServiceState(
            actionProcessor = IdleActionProcessor(callLogger, observerRegistry),
            callLogger = callLogger,
            observerRegistry = observerRegistry
        )
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var peerConnection: PeerConnection? = null
    private var durationJob: Job? = null
    private var incomingTimeoutJob: Job? = null
    private var signalingTimeoutJob: Job? = null
    private var turnServers: List<IceServer> = emptyList()
    private var turnServersFetchedAt: Long = 0

    private var turnUsername: ByteArray? = null
    private var turnCredential: ByteArray? = null
    private var statsCollector: StatsCollector? = null

    init {
        webRtcEngine.initialize()
        serviceScope.launch {
            _serviceState.collect {
                _callState.value = it.callState
            }
        }
    }

    fun registerObserver(observer: org.enchant.core.calls.observer.CallObserver) =
        observerRegistry.register(observer)

    fun unregisterObserver(observer: org.enchant.core.calls.observer.CallObserver) =
        observerRegistry.unregister(observer)

    private suspend fun processAction(action: CallAction) {
        _serviceState.value = _serviceState.value.actionProcessor.process(_serviceState.value, action)
        updateCallState()
    }

    private fun updateCallState() {
        _callState.value = _serviceState.value.callState
    }

    suspend fun startOutgoingCall(remoteUserId: String, isVideo: Boolean) {
        incomingTimeoutJob?.cancel()
        signalingTimeoutJob?.cancel()
        processAction(CallAction.StartOutgoingCall(remoteUserId, isVideo))
        val state = _serviceState.value.callState
        Log.w("CallManager", "OUT: after processAction status=${state.status}")
        if (state.status != CallStatus.CALLING) return

        Log.w("CallManager", "OUT: requesting audio focus...")
        if (!audioFocusManager.requestFocus()) {
            Log.w("CallManager", "Audio focus not granted")
        }
        Log.w("CallManager", "OUT: audio focus done")

        Log.w("CallManager", "OUT: fetching TURN...")
        val iceServers = fetchTurnServers()
        Log.w("CallManager", "OUT: creating peer connection (servers=${iceServers.size})")
        peerConnection = webRtcEngine.createPeerConnection(iceServers, createPeerConnectionObserver())
            ?: run { Log.w("CallManager", "OUT: peer connection FAILED"); endCall(); return }

        iceHandler.drainAndApply(peerConnection!!)

        Log.w("CallManager", "OUT: creating local stream...")
        val stream = mediaStreamManager.createLocalStream(isVideo)
            ?: run { Log.w("CallManager", "OUT: local stream FAILED"); endCall(); return }

        addTracks(stream)

        Log.w("CallManager", "OUT: creating offer...")
        val sdp = sdpHandler.createOffer(peerConnection!!)
        Log.w("CallManager", "OUT: offer=${if (sdp != null) "OK ${sdp.length}b" else "NULL"}")
        if (sdp != null) {
            val callId = _serviceState.value.callState.callId ?: UUID.randomUUID().toString()
            val sent = signalingClient.sendOffer(remoteUserId, sdp, callId)
            Log.w("CallManager", "OUT: sendOffer result=$sent callId=$callId")
            observerRegistry.notifyOfferSent(remoteUserId, sdp)
        }

        startDurationTimer()

        signalingTimeoutJob = serviceScope.launch {
            delay(30_000)
            val current = _serviceState.value.callState
            if (current.status == CallStatus.CALLING || current.status == CallStatus.CONNECTING || current.status == CallStatus.RINGING) {
                processAction(CallAction.SignalingTimeout)
            }
        }
    }

    fun handleReceivedOffer(senderUserId: String, sdp: String, callId: String, isVideo: Boolean) {
        incomingTimeoutJob?.cancel()
        serviceScope.launch {
            processAction(CallAction.ReceiveIncomingOffer(senderUserId, sdp, callId, isVideo))
        }

        serviceScope.launch {
            ringtonePlayer.startIncomingRingtone()
            ringtonePlayer.vibrate()
        }
        notificationManager.showIncomingCall(senderUserId, isVideo, callId)

        incomingTimeoutJob = serviceScope.launch {
            delay(30_000)
            if (_serviceState.value.callState.status == CallStatus.RINGING) {
                processAction(CallAction.IncomingCallTimeout)
            }
        }
    }

    suspend fun acceptCall(withVideo: Boolean) {
        incomingTimeoutJob?.cancel()
        signalingTimeoutJob?.cancel()
        processAction(CallAction.AcceptIncomingCall(withVideo))

        if (!audioFocusManager.requestFocus()) {
            Log.w("CallManager", "Audio focus not granted")
        }

        notificationManager.cancelIncoming()

        val iceServers = fetchTurnServers()
        peerConnection = webRtcEngine.createPeerConnection(iceServers, createPeerConnectionObserver())
            ?: run { endCall(); return }

        // The answer needs the caller's offer as the remote description
        // first, or createAnswer fails and the call never negotiates.
        val offerSdp = _serviceState.value.callSetupData?.offerSdp
        if (offerSdp != null) {
            val remoteSet = sdpHandler.setRemoteDescription(
                peerConnection!!,
                offerSdp,
                SessionDescription.Type.OFFER
            )
            if (!remoteSet) {
                Log.w("CallManager", "ACCEPT: setRemoteDescription failed")
            }
        } else {
            Log.w("CallManager", "ACCEPT: no offer sdp in state, answer will fail")
        }

        val stream = mediaStreamManager.createLocalStream(withVideo)
            ?: run { endCall(); return }

        addTracks(stream)

        val sdp = sdpHandler.createAnswer(peerConnection!!)
        if (sdp != null) {
            val remoteId = _serviceState.value.callState.remoteUserId
            if (remoteId != null) {
                val callId = _serviceState.value.callState.callId ?: UUID.randomUUID().toString()
                signalingClient.sendAnswer(remoteId, sdp, callId)
                observerRegistry.notifyAnswerSent(remoteId, sdp)
            }
        }

        startDurationTimer()
    }

    fun denyCall() {
        val remoteId = _serviceState.value.callState.remoteUserId ?: return
        incomingTimeoutJob?.cancel()
        serviceScope.launch(Dispatchers.IO) {
            val callId = _serviceState.value.callState.callId ?: UUID.randomUUID().toString()
            signalingClient.sendHangup(remoteId, callId)
            observerRegistry.notifyHangup(remoteId)
        }
        ringtonePlayer.stopRingtone()
        ringtonePlayer.cancelVibration()
        notificationManager.cancelIncoming()
        serviceScope.launch {
            processAction(CallAction.DenyIncomingCall(null))
        }
    }

    fun endCall() {
        serviceScope.launch {
            processAction(CallAction.CallEnded)
        }
        cleanup()
    }

    suspend fun handleReceivedAnswer(sdp: String) {
        processAction(CallAction.ReceiveAnswer(sdp))

        val pc = peerConnection ?: return
        val success = sdpHandler.setRemoteDescription(pc, sdp, SessionDescription.Type.ANSWER)
        if (success) {
            processAction(CallAction.CallConnected)
        }
    }

    fun handleReceivedIce(candidate: String) {
        val pc = peerConnection
        if (pc == null) {
            iceHandler.queueRaw(candidate)
            return
        }
        iceHandler.parse(candidate)?.let { pc.addIceCandidate(it) }
    }

    fun handleReceivedHangup() {
        serviceScope.launch {
            processAction(CallAction.ReceiveHangup(null))
        }
        cleanup()
    }

    fun toggleMute() {
        serviceScope.launch {
            processAction(CallAction.ToggleMute)
        }
    }

    fun toggleVideo() {
        serviceScope.launch {
            processAction(CallAction.ToggleVideo)
        }
    }

    fun toggleSpeaker() {
        serviceScope.launch {
            processAction(CallAction.ToggleSpeaker)
        }
    }

    fun flipCamera() {
        serviceScope.launch {
            processAction(CallAction.FlipCamera)
        }
    }

    fun setOnHold(hold: Boolean) {
        serviceScope.launch {
            processAction(CallAction.SetOnHold(hold))
        }
    }

    fun raiseHand(raised: Boolean) {
        serviceScope.launch {
            processAction(CallAction.RaiseHand(raised))
        }
    }

    suspend fun getCallLogs(limit: Int = 100): List<CallLogEntry> =
        callLogger.getCallLogs(limit)

    suspend fun insertMissedCall(peerUserId: String, isVideo: Boolean, timestamp: Long = System.currentTimeMillis()) {
        callLogger.insertMissedCall(peerUserId, isVideo, timestamp)
    }

    suspend fun peekGroupCall(groupId: String): PeekInfo? {
        try {
            val activeParticipants = signalingClient.peekGroupCall(groupId)
            return PeekInfo(
                activeParticipants = activeParticipants,
                maxParticipants = 32,
                isActive = activeParticipants > 0
            )
        } catch (_: Exception) {
            return PeekInfo(activeParticipants = 0, maxParticipants = 32, isActive = false)
        }
    }

    fun shutdown() {
        serviceScope.cancel()
        cleanup()
        webRtcEngine.release()
        mediaStreamManager.release()
        observerRegistry.clear()
    }

    private suspend fun fetchTurnServers(): List<IceServer> {
        if (System.currentTimeMillis() - turnServersFetchedAt < 300_000 && turnServers.isNotEmpty()) {
            return turnServers
        }
        val servers = signalingClient.fetchTurnServers().getOrElse {
            listOf(IceServer(urls = listOf("stun:stun.l.google.com:19302")))
        }
        turnServers = servers
        turnServersFetchedAt = System.currentTimeMillis()
        servers.forEach { server ->
            server.username?.let { u ->
                if (turnUsername == null) turnUsername = u.toByteArray()
            }
            server.credential?.let { c ->
                if (turnCredential == null) turnCredential = c.toByteArray()
            }
        }
        return servers
    }

    private fun addTracks(stream: MediaStream) {
        val pc = peerConnection ?: return
        stream.audioTracks.firstOrNull()?.let { pc.addTrack(it, listOf("stream")) }
        stream.videoTracks.firstOrNull()?.let { pc.addTrack(it, listOf("stream")) }
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                val current = _serviceState.value.callState
                if (current.status == CallStatus.CONNECTED) {
                    stateMachine.updateDuration(current.durationSeconds + 1)
                }
            }
        }
    }

    private fun cleanup() {
        durationJob?.cancel()
        durationJob = null
        incomingTimeoutJob?.cancel()
        incomingTimeoutJob = null
        signalingTimeoutJob?.cancel()
        signalingTimeoutJob = null
        statsCollector?.stopCollecting()
        statsCollector = null
        // close() + dispose() on a torn-down connection can segfault the
        // WebRTC native layer; run them guarded and detached.
        val pc = peerConnection
        peerConnection = null
        if (pc != null) {
            runCatching { pc.close() }
            runCatching { pc.dispose() }
        }
        iceHandler.clear()
        mediaStreamManager.release()
        audioFocusManager.abandonFocus()
        turnUsername?.fill(0)
        turnUsername = null
        turnCredential?.fill(0)
        turnCredential = null
    }

    private fun createPeerConnectionObserver(): PeerConnection.Observer {
        return object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val remoteId = _serviceState.value.callState.remoteUserId ?: return
                val serialized = iceHandler.serialize(candidate)
                serviceScope.launch(Dispatchers.IO) {
                    val callId = _serviceState.value.callState.callId ?: UUID.randomUUID().toString()
                    signalingClient.sendIceCandidate(remoteId, serialized, callId)
                    observerRegistry.notifyIceSent(remoteId, serialized)
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        serviceScope.launch {
                            processAction(CallAction.CallConnected)
                        }
                        iceHandler.drainAndApply(peerConnection!!)
                        startStatsCollection()
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        serviceScope.launch {
                            processAction(CallAction.CallReconnecting)
                        }
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        serviceScope.launch {
                            processAction(CallAction.CallFailedIce)
                        }
                        cleanup()
                    }
                    else -> {}
                }
            }

            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onDataChannel(channel: org.webrtc.DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver, tracks: Array<MediaStream>) {}
        }
    }

    private fun startStatsCollection() {
        val pc = peerConnection ?: return
        statsCollector = StatsCollector(pc).also { collector ->
            serviceScope.launch {
                collector.startCollecting { stats ->
                    serviceScope.launch {
                        processAction(CallAction.QualityUpdate(stats))
                        observerRegistry.notifyQuality(stats)
                    }
                }
            }
        }
    }
}