package org.enchant.core.calls

import android.content.Context
import org.enchant.core.calls.audio.AudioFocusManager
import org.enchant.core.calls.audio.AudioRouter
import org.enchant.core.calls.audio.RingtonePlayer
import org.enchant.core.calls.dao.DatabaseCallLogDao
import org.enchant.core.calls.notification.CallNotificationManager
import org.enchant.core.calls.webrtc.IceCandidateHandler
import org.enchant.core.calls.webrtc.MediaStreamManager
import org.enchant.core.calls.webrtc.SdpHandler
import org.enchant.core.calls.webrtc.WebRtcEngine

object CallsModule {
    private var context: Context? = null
    private var _callManager: DefaultCallManager? = null
    private val lock = Any()

    fun initialize(appContext: Context) {
        context = appContext
    }

    fun provideCallManager(
        signalingClient: SignalingClient,
        databasePool: org.enchant.core.database.DatabasePool
    ): DefaultCallManager {
        val ctx = context ?: throw IllegalStateException("CallsModule not initialized")

        val webRtcEngine = WebRtcEngine(ctx)
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val audioRouter = AudioRouter(audioManager)
        val audioFocusManager = AudioFocusManager(audioManager)
        val ringtonePlayer = RingtonePlayer(ctx)
        val notificationManager = CallNotificationManager(ctx)
        val callLogger = CallLogger(DatabaseCallLogDao(databasePool))
        val observerRegistry = org.enchant.core.calls.observer.CallObserverRegistry()
        val stateMachine = CallStateMachine()
        val sdpHandler = SdpHandler()
        val iceHandler = IceCandidateHandler()
        val mediaStreamManager = MediaStreamManager(ctx, webRtcEngine)

        return DefaultCallManager(
            stateMachine = stateMachine,
            webRtcEngine = webRtcEngine,
            mediaStreamManager = mediaStreamManager,
            sdpHandler = sdpHandler,
            iceHandler = iceHandler,
            signalingClient = signalingClient,
            audioRouter = audioRouter,
            audioFocusManager = audioFocusManager,
            ringtonePlayer = ringtonePlayer,
            notificationManager = notificationManager,
            callLogger = callLogger,
            observerRegistry = observerRegistry
        )
    }

    fun getCallManager(): DefaultCallManager = synchronized(lock) {
        _callManager ?: throw IllegalStateException("CallManager not set. Call setCallManager() first.")
    }

    fun setCallManager(manager: DefaultCallManager) {
        synchronized(lock) {
            _callManager = manager
        }
    }
}