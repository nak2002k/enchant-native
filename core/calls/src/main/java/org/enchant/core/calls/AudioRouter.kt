package org.enchant.core.calls

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.enchant.core.base.AppConfig

object AudioRouter {
    private var audioManager: AudioManager? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: Any? = null
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        initialized = true
    }

    suspend fun startAudio(context: Context) {
        withContext(Dispatchers.Default) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                    .setAcceptsDelayedFocusGain(true)
                    .build()
                audioFocusRequest = request
                audioManager?.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN)
            }
        }
    }

    fun stopAudio(playDisconnect: Boolean = false) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                (audioFocusRequest as? AudioFocusRequest)?.let {
                    audioManager?.abandonAudioFocusRequest(it)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
        if (playDisconnect) playDisconnectTone()
    }

    fun selectAudioDevice(device: AudioDevice) {
        when (device) {
            AudioDevice.SPEAKER -> audioManager?.isSpeakerphoneOn = true
            AudioDevice.EARPIECE -> audioManager?.isSpeakerphoneOn = false
            AudioDevice.BLUETOOTH -> {
                audioManager?.isSpeakerphoneOn = false
                audioManager?.startBluetoothSco()
                audioManager?.isBluetoothScoOn = true
            }
            AudioDevice.WIRED_HEADSET -> {
                audioManager?.isSpeakerphoneOn = false
                audioManager?.isWiredHeadsetOn = true
            }
        }
    }

    fun setSpeakerphoneOn(on: Boolean) {
        audioManager?.isSpeakerphoneOn = on
    }

    suspend fun startIncomingRinger(ringtoneUri: Uri? = null) {
        withContext(Dispatchers.Default) {
            try {
                val ctx = AppConfig.applicationContext ?: return@withContext
                stopRinger()
                val uri = ringtoneUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(ctx, uri)
                    isLooping = true
                    setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    prepare()
                    start()
                }
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
        }
    }

    suspend fun startOutgoingRinger() {
        withContext(Dispatchers.Default) {
            try {
                stopRinger()
                val ctx = AppConfig.applicationContext ?: return@withContext
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(ctx, uri)
                    isLooping = true
                    setVolume(0.3f, 0.3f)
                    setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                        .build())
                    prepare()
                    start()
                }
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
        }
    }

    fun stopRinger() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
    }

    fun vibrate(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager ?: return
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                    longArrayOf(0, 1000, 500, 1000, 500, 1000), 2))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000), 2)
            }
        } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
    }

    private fun playDisconnectTone() {
        try {
            val ctx = AppConfig.applicationContext ?: return
            mediaPlayer = MediaPlayer().apply {
                setDataSource(ctx, Settings.System.DEFAULT_NOTIFICATION_URI)
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build())
                prepare()
                start()
                setOnCompletionListener { release() }
            }
        } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
    }
}
