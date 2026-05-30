package org.enchant.calls.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun OutgoingCallScreen(
    remoteName: String,
    isVideoCall: Boolean,
    onEndCall: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSwitchToVideo: () -> Unit,
    callStatus: String = "CALLING"
) {
    var timeLeft by remember { mutableIntStateOf(45) }
    var isSpeakerOn by remember { mutableStateOf(false) }

    LaunchedEffect(callStatus) {
        if (callStatus == "CONNECTED" || callStatus == "RECONNECTING" || callStatus == "ENDED") {
            timeLeft = 0
        }
    }

    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
        if (timeLeft == 0) onEndCall()
    }

    val infiniteTransition = rememberInfiniteTransition()
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Calling", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                repeat(3) {
                    Text(".", fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dotAlpha),
                        modifier = Modifier.offset(y = (it * -2).dp))
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { isSpeakerOn = !isSpeakerOn; onToggleSpeaker() },
                        modifier = Modifier.semantics { this.contentDescription = if (isSpeakerOn) "Turn off speaker" else "Turn on speaker" }
                    ) {
                        Surface(shape = CircleShape, color = if (isSpeakerOn) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant) {
                            Icon(if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                "Speaker", modifier = Modifier.padding(12.dp))
                        }
                    }
                    Text("Speaker", style = MaterialTheme.typography.labelSmall)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onSwitchToVideo,
                        modifier = Modifier.semantics { this.contentDescription = "Switch to video call" }
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                            Icon(Icons.Default.Videocam, "Video", modifier = Modifier.padding(12.dp))
                        }
                    }
                    Text("Video", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            FilledIconButton(
                onClick = onEndCall,
                modifier = Modifier.size(64.dp).semantics { contentDescription = "End call" },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935))
            ) {
                Icon(Icons.Default.CallEnd, "End Call", tint = Color.White, modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("End call", style = MaterialTheme.typography.labelSmall)

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
