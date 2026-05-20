package org.enchant.core.calls.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RingtonePlayer @Inject constructor(
    private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null
    private val vibrator: Vibrator? = getVibrator()

    suspend fun startIncomingRingtone(ringtoneUri: Uri? = null) {
        withContext(Dispatchers.Default) {
            stopRingtone()
            try {
                val uri = ringtoneUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, uri)
                    isLooping = true
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e("RingtonePlayer", "Ringtone failed: ${e.message}")
            }
        }
    }

    suspend fun startOutgoingRingback() {
        withContext(Dispatchers.Default) {
            stopRingtone()
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, uri)
                    isLooping = true
                    setVolume(0.3f, 0.3f)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                            .build()
                    )
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e("RingtonePlayer", "Ringback failed: ${e.message}")
            }
        }
    }

    fun stopRingtone() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.w("RingtonePlayer", "Stop ringtone failed: ${e.message}")
        }
    }

    fun vibrate() {
        vibrator?.let { v ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 1000, 500, 1000, 500, 1000), 2
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000), 2)
                }
            } catch (e: Exception) {
                Log.w("RingtonePlayer", "Vibrate failed: ${e.message}")
            }
        }
    }

    fun cancelVibration() {
        vibrator?.cancel()
    }

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}