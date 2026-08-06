package org.enchant.calls.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val PurplePrimary = Color(0xFF3A0D6E)
private val PurpleDark = Color(0xFF8E24AA)
private val CallRed = Color(0xFFFF3B30)
private val DarkBackground = Color(0xFF1C1C1E)

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
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Calling",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                repeat(3) {
                    Text(
                        ".",
                        fontSize = 20.sp,
                        color = Color.White.copy(alpha = 0.7f * dotAlpha),
                        modifier = Modifier.offset(y = (it * -2).dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                remoteName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.weight(1f))

            Box(modifier = Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { scaleX = ringScale; scaleY = ringScale }
                        .background(PurpleDark.copy(alpha = 0.3f), CircleShape)
                )
                Surface(
                    modifier = Modifier
                        .size(112.dp)
                        .shadow(18.dp, CircleShape, spotColor = PurpleDark, ambientColor = PurpleDark)
                        .border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                    shape = CircleShape,
                    color = Color(0xFF121212)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            remoteName.take(2).uppercase(),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                OutgoingControlButton(
                    icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                    contentDescription = if (isSpeakerOn) "Turn off speaker" else "Turn on speaker",
                    onClick = {
                        isSpeakerOn = !isSpeakerOn
                        onToggleSpeaker()
                    }
                )
                OutgoingControlButton(
                    icon = Icons.Default.Videocam,
                    contentDescription = "Switch to video call",
                    onClick = onSwitchToVideo
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            HangupButton(
                size = 76.dp,
                iconSize = 32.dp,
                contentDescription = "End call",
                onClick = onEndCall
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "End call",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Auto-cancels in ${timeLeft}s",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun OutgoingControlButton(
    icon: ImageVector,
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
            .background(Color.White.copy(alpha = 0.12f))
            .semantics { this.contentDescription = contentDescription }
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun HangupButton(
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
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
            .size(size)
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
            modifier = Modifier.size(iconSize)
        )
    }
}
