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
import org.enchant.ui.icons.EnchantIcons

private val PurplePrimary = Color(0xFF3A0D6E)
private val PurpleDark = Color(0xFFB388E3)
private val CallRed = Color(0xFFFF3B30)
private val CallBackground = Color(0xFF121212)
private val DarkSurface = Color(0xFF1C1C1E)

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

    Surface(modifier = Modifier.fillMaxSize(), color = CallBackground) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                remoteName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                durationText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(4) {
                    Box(
                        modifier = Modifier.size(6.dp).clip(CircleShape).background(
                            if (it < signalStrength) PurpleDark else Color.White.copy(alpha = 0.15f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1.1f))

            Surface(
                modifier = Modifier
                    .size(112.dp)
                    .shadow(18.dp, CircleShape, spotColor = PurpleDark, ambientColor = PurpleDark)
                    .border(3.dp, PurplePrimary, CircleShape),
                shape = CircleShape,
                color = DarkSurface
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

            Spacer(modifier = Modifier.weight(1.1f))

            Row {
                Row(
                    modifier = Modifier.clickable { onShowKeypad() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        EnchantIcons.grid2x2, null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Keypad",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.width(32.dp))
                Row(
                    modifier = Modifier.clickable { onShowSafetyNumber() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        EnchantIcons.lock, null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Safety number",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                VoiceControlButton(
                    icon = if (isMuted) EnchantIcons.micOff else EnchantIcons.mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    contentDescription = if (isMuted) "Unmute call" else "Mute call",
                    isActive = isMuted,
                    onClick = onToggleMute
                )
                VoiceControlButton(
                    icon = if (isSpeakerOn) EnchantIcons.speakerHigh else EnchantIcons.volume2,
                    label = if (isSpeakerOn) "Speaker" else "Earpiece",
                    contentDescription = "Toggle speaker",
                    isActive = isSpeakerOn,
                    onClick = onToggleSpeaker
                )
                VoiceControlButton(
                    icon = EnchantIcons.video,
                    label = "Video",
                    contentDescription = "Switch to video call",
                    isActive = false,
                    onClick = onSwitchToVideo
                )
                HangupButton(
                    contentDescription = "End call",
                    onClick = onEndCall
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun VoiceControlButton(
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
private fun HangupButton(
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
            EnchantIcons.phoneDisconnect,
            contentDescription,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}
