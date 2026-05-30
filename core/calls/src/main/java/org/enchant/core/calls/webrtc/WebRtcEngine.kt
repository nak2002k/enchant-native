package org.enchant.core.calls.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton

class WebRtcEngine(
    private val context: Context
) {
    private var rootEglBase: EglBase? = null
    var peerConnectionFactory: PeerConnectionFactory? = null
        private set
    private var audioManager: android.media.AudioManager? = null

    @Volatile
    var isInitialized: Boolean = false
        private set

    fun initialize() {
        if (isInitialized) return

        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setFieldTrials("")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        rootEglBase = EglBase.create()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase!!.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase!!.eglBaseContext, true, true))
            .createPeerConnectionFactory()

        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        isInitialized = true
    }

    fun createPeerConnection(
        iceServers: List<org.enchant.core.calls.model.IceServer>,
        observer: PeerConnection.Observer
    ): PeerConnection? {
        val factory = peerConnectionFactory ?: run {
            Log.e("WebRtcEngine", "Factory not initialized")
            return null
        }

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

        val pc = factory.createPeerConnection(config, observer)

        iceServers.forEach { s ->
            s.username?.let { it.toByteArray().fill(0) }
            s.credential?.let { it.toByteArray().fill(0) }
        }

        return pc
    }

    fun getEglBaseContext(): EglBase.Context? = rootEglBase?.eglBaseContext

    fun getAudioManager(): android.media.AudioManager? = audioManager

    fun release() {
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        rootEglBase?.release()
        rootEglBase = null
        audioManager = null
        isInitialized = false
    }
}