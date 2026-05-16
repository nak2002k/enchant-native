package org.enchant.core.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.enchant.core.base.AppConfig
import org.webrtc.*
import java.util.UUID
import kotlin.coroutines.resume

object WebRtcService {
    private var initialized = false
    private var rootEglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var audioManager: AudioManager? = null
    private var currentCapturer: CameraVideoCapturer? = null
    private var currentVideoSource: VideoSource? = null

    suspend fun init(context: Context) {
        if (initialized) return
        withContext(Dispatchers.Default) {
            try {
                val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                    .setFieldTrials("")
                    .createInitializationOptions()
                PeerConnectionFactory.initialize(initOptions)

                rootEglBase = EglBase.create()
                peerConnectionFactory = PeerConnectionFactory.builder()
                    .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase?.eglBaseContext))
                    .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase?.eglBaseContext, true, true))
                    .createPeerConnectionFactory()

                audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return@withContext
                initialized = true
            } catch (e: Exception) {
                android.util.Log.e("WebRtcService", "Init failed", e)
            }
        }
    }

    suspend fun createPeerConnection(
        iceServers: List<IceServer>,
        observer: PeerConnection.Observer
    ): PeerConnection? = withContext(Dispatchers.Default) {
        try {
            val config = PeerConnection.RTCConfiguration(
                iceServers.map { s ->
                    PeerConnection.IceServer.builder(s.urls)
                        .setUsername(s.username ?: "")
                        .setPassword(s.credential ?: "")
                        .createIceServer()
                }
            ).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                iceCandidatePoolSize = 5
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }
            peerConnectionFactory?.createPeerConnection(config, observer)
        } catch (e: Exception) {
            android.util.Log.e("WebRtcService", "Create PC failed", e)
            null
        }
    }

    suspend fun createOffer(pc: PeerConnection): String? = suspendCancellableCoroutine { cont ->
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    pc.setLocalDescription(SdpObserverWrapper(), it)
                    cont.resume(it.description)
                } ?: cont.resume(null)
            }
            override fun onSetFailure(error: String?) { cont.resume(null) }
            override fun onCreateFailure(error: String?) { cont.resume(null) }
            override fun onSetSuccess() {}
        }, MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            optional.add(MediaConstraints.KeyValuePair("internalSctpDataChannels", "true"))
        })
    }

    suspend fun createAnswer(pc: PeerConnection): String? = suspendCancellableCoroutine { cont ->
        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    pc.setLocalDescription(SdpObserverWrapper(), it)
                    cont.resume(it.description)
                } ?: cont.resume(null)
            }
            override fun onSetFailure(error: String?) { cont.resume(null) }
            override fun onCreateFailure(error: String?) { cont.resume(null) }
            override fun onSetSuccess() {}
        }, MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        })
    }

    fun setRemoteDescription(pc: PeerConnection, sdp: String, type: SessionDescription.Type) {
        pc.setRemoteDescription(SdpObserverWrapper(), SessionDescription(type, sdp))
    }

    fun addIceCandidate(pc: PeerConnection, candidate: String) {
        try {
            val parts = candidate.split("|")
            val sdpMid = parts.getOrElse(0) { "" }
            val sdpMLineIndex = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
            val sdp = parts.getOrElse(2) { candidate }
            val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
            pc.addIceCandidate(iceCandidate)
        } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
    }

    suspend fun getLocalStream(isVideo: Boolean): MediaStream? = withContext(Dispatchers.Default) {
        try {
            val ctx = AppConfig.applicationContext ?: return@withContext null
            val factory = peerConnectionFactory ?: return@withContext null

            val audioSource = factory.createAudioSource(MediaConstraints())
            val audioTrack = factory.createAudioTrack("audio_${UUID.randomUUID()}", audioSource)
            val stream = factory.createLocalMediaStream("stream_${UUID.randomUUID()}")
            stream.addTrack(audioTrack)

            if (isVideo && ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val cameraName = Camera2Enumerator(ctx).run {
                            deviceNames.firstOrNull { isFrontFacing(it) || isBackFacing(it) }
                        }
                        if (cameraName != null) {
                            val capturer = Camera2Enumerator(ctx).createCapturer(cameraName, null)
                            currentCapturer = capturer
                        val videoSource = factory.createVideoSource(capturer?.isScreencast ?: false)
                        currentVideoSource = videoSource
                        capturer?.startCapture(1280, 720, 30)
                        val videoTrack = factory.createVideoTrack("video_${UUID.randomUUID()}", videoSource)
                        stream.addTrack(videoTrack)
                    }
                } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
            }
            stream
        } catch (e: Exception) {
            android.util.Log.e("WebRtcService", "getLocalStream failed", e)
            null
        }
    }

    fun toggleAudioTrack(stream: MediaStream?, enabled: Boolean) {
        stream?.audioTracks?.firstOrNull()?.setEnabled(enabled)
    }

    fun toggleVideoTrack(stream: MediaStream?, enabled: Boolean) {
        stream?.videoTracks?.firstOrNull()?.setEnabled(enabled)
    }

    fun switchCamera(videoTrack: VideoTrack?) {
        currentCapturer?.let {
            try { it.switchCamera(null) } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
        }
    }

    fun setSpeakerphoneOn(on: Boolean) {
        audioManager?.isSpeakerphoneOn = on
    }

    fun getLocalFingerprint(pc: PeerConnection?): String? {
        return try {
            pc?.localDescription?.description ?: return null
        } catch (_: Exception) { null }
    }

    fun getRemoteFingerprint(pc: PeerConnection?): String? {
        return try {
            pc?.remoteDescription?.description ?: return null
        } catch (_: Exception) { null }
    }

    fun dispose(pc: PeerConnection) {
        try {
            pc.close()
            pc.dispose()
        } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
    }

    fun cleanup() {
        try {
            currentCapturer?.stopCapture()
            currentCapturer?.dispose()
        } catch (_: Exception) {}
        currentCapturer = null
        currentVideoSource = null
    }

    fun getFactory(): PeerConnectionFactory? = peerConnectionFactory
    fun getEglBase(): EglBase? = rootEglBase
    fun isInitialized(): Boolean = initialized

    private class SdpObserverWrapper : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetFailure(p0: String?) {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetSuccess() {}
    }
}
