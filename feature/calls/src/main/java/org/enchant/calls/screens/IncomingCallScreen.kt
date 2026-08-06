package org.enchant.calls.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
private val PurpleLight = Color(0xFF7B1FA2)
private val CallRed = Color(0xFFFF3B30)
private val CallGreen = Color(0xFF34C759)
private val DarkSurface = Color(0xFF1C1C1E)
private val PressSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

@Composable
fun IncomingCallScreen(
    callerName: String,
    callerId: String,
    isVideoCall: Boolean,
    onAcceptAudio: () -> Unit,
    onAcceptVideo: () -> Unit,
    onDecline: () -> Unit,
    callStatus: String = "RINGING"
) {
    var timeLeft by remember { mutableIntStateOf(30) }

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
        if (timeLeft == 0) onDecline()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(PurplePrimary, DarkSurface))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                callerName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                if (isVideoCall) "Incoming video call" else "Incoming voice call",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.weight(1f))

            CallerAvatar(callerName)

            Spacer(modifier = Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallActionButton(
                    icon = Icons.Default.CallEnd,
                    label = "Decline",
                    contentDescription = "Decline incoming call",
                    color = CallRed,
                    onClick = onDecline
                )

                if (isVideoCall) {
                    CallActionButton(
                        icon = Icons.Default.Videocam,
                        label = "Video",
                        contentDescription = "Accept video call",
                        color = CallGreen,
                        onClick = onAcceptVideo
                    )
                }

                CallActionButton(
                    icon = Icons.Default.Call,
                    label = "Accept",
                    contentDescription = "Accept audio call",
                    color = CallGreen,
                    onClick = onAcceptAudio
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Auto-decline in ${timeLeft}s",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CallerAvatar(name: String) {
    Box(modifier = Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.matchParentSize().drawBehind {
                drawCircle(
                    color = PurpleLight.copy(alpha = 0.3f),
                    radius = size.minDimension / 2f + 8.dp.toPx(),
                    style = Stroke(10.dp.toPx())
                )
                drawCircle(
                    color = PurpleDark.copy(alpha = 0.55f),
                    radius = size.minDimension / 2f + 2.dp.toPx(),
                    style = Stroke(3.dp.toPx())
                )
            }
        )
        Surface(
            modifier = Modifier
                .size(112.dp)
                .shadow(18.dp, CircleShape, spotColor = PurpleDark, ambientColor = PurpleDark)
                .border(3.dp, Color.White, CircleShape),
            shape = CircleShape,
            color = DarkSurface
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    name.take(2).uppercase(),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun CallActionButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    color: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = PressSpring
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(color)
                .semantics { this.contentDescription = contentDescription }
                .clickable(interactionSource = interactionSource, indication = null) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}
