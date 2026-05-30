package org.enchant.calls.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

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
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().background(Color.Black)) {
        Text(
            formatTimer(durationSeconds),
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(participants) { p ->
                ParticipantTile(p, isAdmin, onMuteParticipant, onRemoveParticipant)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.8f)).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier.semantics { this.contentDescription = if (isMuted) "Unmute call" else "Mute call" }
            ) {
                Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, "Mute", tint = Color.White)
            }
            IconButton(
                onClick = onRaiseHand,
                modifier = Modifier.semantics { this.contentDescription = "Raise hand" }
            ) {
                Icon(Icons.Default.PanTool, "Raise hand", tint = Color.White)
            }
            IconButton(
                onClick = { onSendReaction("\uD83D\uDC4D") },
                modifier = Modifier.semantics { this.contentDescription = "Send thumbs up reaction" }
            ) {
                Icon(Icons.Default.ThumbUp, "React", tint = Color.White)
            }
            FilledIconButton(
                onClick = onEndCall,
                modifier = Modifier.semantics { contentDescription = "End call" },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935))
            ) {
                Icon(Icons.Default.CallEnd, "End", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ParticipantTile(participant: CallParticipant, isAdmin: Boolean, onMute: (String) -> Unit, onRemove: (String) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val bg = if (participant.hasRaisedHand) Color(0xFF4CAF50).copy(alpha = 0.3f) else Color.DarkGray.copy(alpha = 0.7f)

    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(bg, MaterialTheme.shapes.medium)
            .pointerInput(isAdmin) {
                if (isAdmin) {
                    detectTapGestures(
                        onLongPress = { showMenu = true }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Text(participant.displayName.take(2).uppercase(), color = Color.White)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(participant.displayName, color = Color.White, style = MaterialTheme.typography.labelSmall)
            if (participant.hasRaisedHand) {
                Icon(Icons.Default.PanTool, "Hand raised", tint = Color.Yellow, modifier = Modifier.size(16.dp))
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (participant.isMuted.not()) {
                DropdownMenuItem(text = { Text("Mute") }, onClick = { onMute(participant.userId); showMenu = false })
            }
            DropdownMenuItem(text = { Text("Remove") }, onClick = { onRemove(participant.userId); showMenu = false })
        }
    }
}

private fun formatTimer(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
