package org.enchant.core.calls

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.enchant.core.calls.audio.AudioRouter
import org.enchant.core.calls.audio.AudioFocusManager
import org.enchant.core.calls.audio.RingtonePlayer
import org.enchant.core.calls.model.*
import org.enchant.core.calls.notification.CallNotificationManager
import org.enchant.core.calls.observer.CallObserverRegistry
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
    val callState: StateFlow<CallState> = stateMachine.state

    private val callScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var peerConnection: PeerConnection? = null
    private var durationJob: Job? = null
    private var turnServers: List<IceServer> = emptyList()
    private var turnServersFetchedAt: Long = 0
    private var statsCollector: StatsCollector? = null

    init {
        webRtcEngine.initialize()
    }

    fun registerObserver(observer: org.enchant.core.calls.observer.CallObserver) =
        observerRegistry.register(observer)

    fun unregisterObserver(observer: org.enchant.core.calls.observer.CallObserver) =
        observerRegistry.unregister(observer)

    suspend fun startOutgoingCall(remoteUserId: String, isVideo: Boolean) {
        val callId = UUID.randomUUID().toString()
        if (!stateMachine.startOutgoing(remoteUserId, isVideo, callId)) {
            stateMachine.setError("Already in a call")
            return
        }

        if (!audioFocusManager.requestFocus()) {
            Log.w("CallManager", "Audio focus not granted")
        }

        observerRegistry.notifyStarted(remoteUserId, isVideo)

        val iceServers = fetchTurnServers()
        peerConnection = webRtcEngine.createPeerConnection(iceServers, createPeerConnectionObserver())
            ?: run { endCall(); return }

        val stream = mediaStreamManager.createLocalStream(isVideo)
            ?: run { endCall(); return }

        addTracks(stream)

        val sdp = sdpHandler.createOffer(peerConnection!!)
        if (sdp != null) {
            signalingClient.sendOffer(remoteUserId, sdp)
            observerRegistry.notifyOfferSent(remoteUserId, sdp)
        }

        stateMachine.setConnecting()
        startDurationTimer()
    }

    fun handleReceivedOffer(senderUserId: String, sdp: String, callId: String, isVideo: Boolean) {
        if (!stateMachine.receiveIncoming(senderUserId, isVideo, callId)) {
            callScope.launch(Dispatchers.IO) {
                signalingClient.sendHangup(senderUserId)
            }
            return
        }

        callScope.launch(Dispatchers.Default) {
            ringtonePlayer.startIncomingRingtone()
            ringtonePlayer.vibrate()
        }
        observerRegistry.notifyStarted(senderUserId, isVideo)
        notificationManager.showIncomingCall(senderUserId, isVideo, callId)

        callScope.launch(Dispatchers.Default) {
            delay(30_000)
            if (callState.value.status == CallStatus.RINGING) {
                handleCallTimeout()
            }
        }
    }

    suspend fun acceptCall(withVideo: Boolean) {
        if (!stateMachine.acceptCall()) return

        if (!audioFocusManager.requestFocus()) {
            Log.w("CallManager", "Audio focus not granted")
        }

        notificationManager.cancelIncoming()

        val iceServers = fetchTurnServers()
        peerConnection = webRtcEngine.createPeerConnection(iceServers, createPeerConnectionObserver())
            ?: run { endCall(); return }

        val stream = mediaStreamManager.createLocalStream(withVideo)
            ?: run { endCall(); return }

        addTracks(stream)

        val sdp = sdpHandler.createAnswer(peerConnection!!)
        if (sdp != null) {
            val remoteId = callState.value.remoteUserId
            if (remoteId != null) {
                signalingClient.sendAnswer(remoteId, sdp)
                observerRegistry.notifyAnswerSent(remoteId, sdp)
            }
        }

        startDurationTimer()
    }

    fun denyCall() {
        val remoteId = callState.value.remoteUserId ?: return
        callScope.launch(Dispatchers.IO) {
            signalingClient.sendHangup(remoteId)
            observerRegistry.notifyHangup(remoteId)
        }
        ringtonePlayer.stopRingtone()
        ringtonePlayer.cancelVibration()
        notificationManager.cancelIncoming()
        stateMachine.denyCall()
    }

    fun endCall() {
        val previousState = stateMachine.endCall()
        if (previousState.status == CallStatus.IDLE) return

        val remoteId = previousState.remoteUserId
        if (remoteId != null) {
            callScope.launch(Dispatchers.IO) {
                signalingClient.sendHangup(remoteId)
                observerRegistry.notifyHangup(remoteId)
            }
        }

        ringtonePlayer.stopRingtone()
        ringtonePlayer.cancelVibration()
        notificationManager.cancelAll()

        val summary = if (previousState.durationSeconds > 0) {
            CallSummary(
                previousState.durationSeconds,
                previousState.isVideoCall,
                previousState.direction == CallDirection.OUTGOING
            )
        } else null

        observerRegistry.notifyEnded(CallEndReason.HANGUP_LOCAL, summary)
        callScope.launch(Dispatchers.IO) { callLogger.insertCallLog(previousState) }

        cleanup()
    }

    suspend fun handleReceivedAnswer(sdp: String) {
        val pc = peerConnection ?: return
        val success = sdpHandler.setRemoteDescription(pc, sdp, SessionDescription.Type.ANSWER)
        if (success) {
            stateMachine.setConnected()
            observerRegistry.notifyConnected()
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
        val previousState = stateMachine.endCall()
        if (previousState.status == CallStatus.IDLE) return

        ringtonePlayer.stopRingtone()
        ringtonePlayer.cancelVibration()
        notificationManager.cancelAll()

        val summary = if (previousState.durationSeconds > 0) {
            CallSummary(
                previousState.durationSeconds,
                previousState.isVideoCall,
                previousState.direction == CallDirection.OUTGOING
            )
        } else null

        observerRegistry.notifyEnded(CallEndReason.HANGUP_REMOTE, summary)
        callScope.launch(Dispatchers.IO) { callLogger.insertCallLog(previousState) }

        cleanup()
    }

    fun toggleMute() {
        stateMachine.toggleMute()
        mediaStreamManager.setAudioEnabled(!callState.value.isMuted)
    }

    fun toggleVideo() {
        stateMachine.toggleVideo()
        if (callState.value.isVideoEnabled) {
            mediaStreamManager.addVideo()
        } else {
            mediaStreamManager.removeVideo()
        }
    }

    fun toggleSpeaker() {
        stateMachine.toggleSpeaker()
        audioRouter.setSpeakerphoneOn(callState.value.isSpeakerOn)
    }

    fun flipCamera() {
        mediaStreamManager.switchCamera()
    }

    fun setOnHold(hold: Boolean) {
        stateMachine.setOnHold(hold)
        mediaStreamManager.setAudioEnabled(!hold)
    }

    fun raiseHand(raised: Boolean) {
        stateMachine.setHandRaised(raised)
    }

    suspend fun getCallLogs(limit: Int = 100): List<CallLogEntry> =
        callLogger.getCallLogs(limit)

    suspend fun insertMissedCall(peerUserId: String, isVideo: Boolean, timestamp: Long = System.currentTimeMillis()) {
        callLogger.insertMissedCall(peerUserId, isVideo, timestamp)
    }

    suspend fun peekGroupCall(groupId: String): PeekInfo? {
        return null
    }

    fun shutdown() {
        callScope.cancel()
        cleanup()
        webRtcEngine.release()
        mediaStreamManager.release()
        observerRegistry.clear()
    }

    private fun handleCallTimeout() {
        ringtonePlayer.stopRingtone()
        ringtonePlayer.cancelVibration()
        notificationManager.cancelIncoming()
        val previousState = stateMachine.endCall()
        observerRegistry.notifyEnded(CallEndReason.TIMEOUT, null)
        callScope.launch(Dispatchers.IO) { callLogger.insertCallLog(previousState) }
        cleanup()
    }

    private suspend fun fetchTurnServers(): List<IceServer> {
        if (System.currentTimeMillis() - turnServersFetchedAt < 3_600_000 && turnServers.isNotEmpty()) {
            return turnServers
        }
        return signalingClient.fetchTurnServers().getOrElse {
            listOf(IceServer(urls = listOf("stun:stun.l.google.com:19302")))
        }
    }

    private fun addTracks(stream: MediaStream) {
        val pc = peerConnection ?: return
        stream.audioTracks.firstOrNull()?.let { pc.addTrack(it, listOf("stream")) }
        stream.videoTracks.firstOrNull()?.let { pc.addTrack(it, listOf("stream")) }
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = callScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                val current = callState.value
                if (current.status == CallStatus.CONNECTED || current.status == CallStatus.CALLING) {
                    stateMachine.updateDuration(current.durationSeconds + 1)
                }
            }
        }
    }

    private fun cleanup() {
        durationJob?.cancel()
        durationJob = null
        statsCollector?.stopCollecting()
        statsCollector = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        iceHandler.clear()
        mediaStreamManager.release()
        audioFocusManager.abandonFocus()
    }

    private fun createPeerConnectionObserver(): PeerConnection.Observer {
        return object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val remoteId = callState.value.remoteUserId ?: return
                val serialized = iceHandler.serialize(candidate)
                callScope.launch(Dispatchers.IO) {
                    signalingClient.sendIceCandidate(remoteId, serialized)
                    observerRegistry.notifyIceSent(remoteId, serialized)
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        stateMachine.setConnected()
                        observerRegistry.notifyConnected()
                        iceHandler.drainAndApply(peerConnection!!)
                        startStatsCollection()
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        stateMachine.setReconnecting()
                        observerRegistry.notifyReconnecting()
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        endCall()
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
            collector.stats
                .onEach { stats -> observerRegistry.notifyQuality(stats) }
                .launchIn(callScope)
            callScope.launch {
                collector.startCollecting()
            }
        }
    }
}