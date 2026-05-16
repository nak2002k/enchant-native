package org.enchant.calls.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ActiveVoiceCallScreen(
    remoteName: String,
    durationSeconds: Int,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    signalStrength: Int,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
    onShowKeypad: () -> Unit,
    onSwitchToVideo: () -> Unit,
    onShowSafetyNumber: () -> Unit
) {
    val durationText = remember(durationSeconds) {
        val min = durationSeconds / 60
        val sec = durationSeconds % 60
        "${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Surface(modifier = Modifier.size(100.dp), shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Text(remoteName.take(2).uppercase(), fontSize = 36.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(remoteName, style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(8.dp))
            Text(durationText, style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(4) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = if (it < signalStrength) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                    ) {}
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    isActive = isMuted,
                    onClick = onToggleMute
                )
                ActionButton(
                    icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                    label = if (isSpeakerOn) "Speaker" else "Earpiece",
                    isActive = isSpeakerOn,
                    onClick = onToggleSpeaker
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(icon = Icons.Default.Dialpad, label = "Keypad", isActive = false, onClick = onShowKeypad)
                ActionButton(icon = Icons.Default.Videocam, label = "Video", isActive = false, onClick = onSwitchToVideo)
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onShowSafetyNumber) {
                Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Safety number", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(32.dp))

            FilledIconButton(
                onClick = onEndCall,
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935))
            ) {
                Icon(Icons.Default.CallEnd, "End Call", tint = Color.White, modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Surface(
                shape = CircleShape,
                color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(icon, label, modifier = Modifier.padding(12.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
