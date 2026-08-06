package org.enchant.calls.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val PurplePrimary = Color(0xFF3A0D6E)
private val PurpleDark = Color(0xFFB388E3)
private val CallRed = Color(0xFFFF3B30)

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
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isSelfFocused by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            if (System.currentTimeMillis() - lastInteraction >= 3000) {
                showControls = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
        detectTapGestures {
            showControls = !showControls
            lastInteraction = System.currentTimeMillis()
        }
    }) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Remote Video", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (remoteUserId.isBlank()) "Video call" else remoteUserId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 14.dp, start = 72.dp, end = 72.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    formatTimer(durationSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 72.dp, end = 72.dp, bottom = 14.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VideoControlButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    contentDescription = if (isMuted) "Unmute call" else "Mute call",
                    isActive = isMuted,
                    onClick = onToggleMute
                )
                VideoControlButton(
                    icon = Icons.Default.FlipCameraAndroid,
                    label = "Flip",
                    contentDescription = "Flip camera",
                    isActive = false,
                    onClick = onFlipCamera
                )
                VideoControlButton(
                    icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                    label = if (isSpeakerOn) "Speaker" else "Earpiece",
                    contentDescription = "Toggle speaker",
                    isActive = isSpeakerOn,
                    onClick = onToggleSpeaker
                )
                VideoHangupButton(
                    contentDescription = "End",
                    onClick = onEndCall
                )
            }
        }

        Surface(
            modifier = Modifier.size(120.dp, 180.dp)
                .offset(x = pipOffsetX.dp, y = pipOffsetY.dp)
                .align(Alignment.TopEnd)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        pipOffsetX += dragAmount.x
                        pipOffsetY += dragAmount.y
                        lastInteraction = System.currentTimeMillis()
                    }
                }
                .clickable {
                    isSelfFocused = !isSelfFocused
                    lastInteraction = System.currentTimeMillis()
                },
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1C1C1E)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().border(
                    width = if (isSelfFocused) 2.dp else 1.dp,
                    color = if (isSelfFocused) PurpleDark else Color.White.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Text("You", color = Color.White)
            }
        }
    }
}

@Composable
private fun VideoControlButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(if (isActive) PurplePrimary else Color.White.copy(alpha = 0.12f))
                .semantics { this.contentDescription = contentDescription }
                .clickable(interactionSource = interactionSource, indication = null) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun VideoHangupButton(
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(CallRed)
            .semantics { this.contentDescription = contentDescription }
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.CallEnd,
            contentDescription,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

private fun formatTimer(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
