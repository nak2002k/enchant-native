package org.enchant.calls.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PurplePrimary = Color(0xFF3A0D6E)
private val CallRed = Color(0xFFFF3B30)
private val CallBackground = Color(0xFF121212)
private val TileBackground = Color(0xFF26262E)
private val TileBorder = Color.White.copy(alpha = 0.08f)

data class CallParticipant(val userId: String, val displayName: String, val isMuted: Boolean, val isVideoOn: Boolean, val hasRaisedHand: Boolean)

@Composable
fun GroupCallScreen(
    participants: List<CallParticipant>,
    isAdmin: Boolean,
    durationSeconds: Int,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onRaiseHand: () -> Unit,
    onSendReaction: (String) -> Unit,
    onMuteParticipant: (String) -> Unit,
    onRemoveParticipant: (String) -> Unit,
    onEndCall: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().background(CallBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Group Call",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "${participants.size} members  ·  ${formatTimer(durationSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            AvatarStack(participants)

            Spacer(modifier = Modifier.height(16.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(participants) { p ->
                ParticipantTile(p, isAdmin, onMuteParticipant, onRemoveParticipant)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.8f)).padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GroupControlButton(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isMuted) "Unmute call" else "Mute call",
                isActive = isMuted,
                onClick = onToggleMute
            )
            GroupControlButton(
                icon = Icons.Default.PanTool,
                contentDescription = "Raise hand",
                isActive = false,
                onClick = onRaiseHand
            )
            GroupControlButton(
                icon = Icons.Default.ThumbUp,
                contentDescription = "Send thumbs up reaction",
                isActive = false,
                onClick = { onSendReaction("\uD83D\uDC4D") }
            )
            GroupHangupButton(
                contentDescription = "End call",
                onClick = onEndCall
            )
        }
    }
}

@Composable
private fun AvatarStack(participants: List<CallParticipant>) {
    val shown = participants.take(5)
    if (shown.isEmpty()) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
            shown.forEach { p ->
                Surface(
                    modifier = Modifier.size(40.dp).border(2.dp, CallBackground, CircleShape),
                    shape = CircleShape,
                    color = PurplePrimary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            p.displayName.take(2).uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
        if (participants.size > shown.size) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "+${participants.size - shown.size}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun GroupControlButton(
    icon: ImageVector,
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
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun GroupHangupButton(
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

@Composable
private fun ParticipantTile(participant: CallParticipant, isAdmin: Boolean, onMute: (String) -> Unit, onRemove: (String) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val tileShape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(tileShape)
                .background(TileBackground)
                .border(1.dp, TileBorder, tileShape)
                .pointerInput(isAdmin) {
                    if (isAdmin) {
                        detectTapGestures(
                            onLongPress = { showMenu = true }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(shape = CircleShape, color = PurplePrimary) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Text(
                        participant.displayName.take(2).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (participant.hasRaisedHand) {
                Icon(
                    Icons.Default.PanTool,
                    "Hand raised",
                    tint = Color.Yellow,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            participant.displayName,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (participant.isMuted.not()) {
                DropdownMenuItem(text = { Text("Mute") }, onClick = { onMute(participant.userId); showMenu = false })
            }
            DropdownMenuItem(text = { Text("Remove") }, onClick = { onRemove(participant.userId); showMenu = false })
        }
    }
}

private fun formatTimer(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
