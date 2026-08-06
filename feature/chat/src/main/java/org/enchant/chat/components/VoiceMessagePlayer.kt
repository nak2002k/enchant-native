package org.enchant.chat.components

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.enchant.ui.icons.EnchantIcons

@Composable
fun VoiceMessagePlayer(
    audioUrl: String,
    duration: Int,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(audioUrl) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (_: Exception) {}
            mediaPlayer = null
        }
    }

    val barHeights = remember {
        List(20) { (4 + (it * 7 % 17)).dp }
    }

    val bg = if (isOutgoing) MaterialTheme.colorScheme.primaryContainer
             else MaterialTheme.colorScheme.surfaceVariant

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = bg,
        modifier = modifier.widthIn(max = 250.dp).clickable {
            if (isPlaying) {
                mediaPlayer?.pause()
                isPlaying = false
            } else {
                try {
                    if (mediaPlayer == null) {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(audioUrl)
                            prepare()
                            setOnCompletionListener {
                                isPlaying = false
                                progress = 0f
                                currentPosition = 0
                            }
                        }
                    }
                    mediaPlayer?.start()
                    isPlaying = true
                } catch (e: Exception) {
                    Log.w("VoicePlayer", "Playback failed: ${e.message}")
                }
            }
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp, 8.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) EnchantIcons.pause else EnchantIcons.play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.height(24.dp).weight(1f).background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                    RoundedCornerShape(2.dp)
                )
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    barHeights.forEach { h ->
                        Box(
                            modifier = Modifier.width(2.dp).height(h)
                                .padding(end = 1.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            val displayTime = if (isPlaying) currentPosition else duration
            Text(
                "${displayTime / 60}:${(displayTime % 60).toString().padStart(2, '0')}",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp
            )
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(200)
            try {
                val pos = mediaPlayer?.currentPosition ?: 0
                val dur = mediaPlayer?.duration?.takeIf { it > 0 } ?: (duration * 1000)
                currentPosition = pos / 1000
                progress = pos.toFloat() / dur.toFloat()
            } catch (_: Exception) {
                isPlaying = false
            }
        }
    }
}
