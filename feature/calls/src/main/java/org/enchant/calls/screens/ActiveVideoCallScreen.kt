package org.enchant.calls.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun ActiveVideoCallScreen(
    remoteUserId: String,
    durationSeconds: Int,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onToggleMute: () -> Unit,
    onFlipCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit
) {
    var showControls by remember { mutableStateOf(true) }
    var pipOffsetX by remember { mutableFloatStateOf(0f) }
    var pipOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            if (showControls) showControls = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
        detectTapGestures { showControls = !showControls }
    }) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Remote Video", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }

        if (showControls) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    VideoControlButton(Icons.Default.Mic, if (isMuted) "Unmute" else "Mute", isMuted, onToggleMute)
                    VideoControlButton(Icons.Default.FlipCamera, "Flip", false, onFlipCamera)
                    VideoControlButton(
                        if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                        if (isSpeakerOn) "Speaker" else "Earpiece", false, onToggleSpeaker
                    )
                    FilledIconButton(
                        onClick = onEndCall,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935))
                    ) {
                        Icon(Icons.Default.CallEnd, "End", tint = Color.White)
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.size(120.dp, 180.dp)
                .offset(x = (20 + pipOffsetX).dp, y = (100 + pipOffsetY).dp)
                .align(Alignment.TopEnd)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        pipOffsetX += dragAmount.x
                        pipOffsetY += dragAmount.y
                    }
                },
            shape = RoundedCornerShape(8.dp),
            color = Color.DarkGray
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("You", color = Color.White)
            }
        }

        Text(
            formatTimer(durationSeconds),
            color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
        )
    }
}

@Composable
private fun VideoControlButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isActive: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatTimer(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
