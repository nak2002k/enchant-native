package org.enchant.calls.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.enchant.core.calls.CallLogEntry
import org.enchant.core.calls.model.CallDirection
import org.enchant.core.calls.model.CallEndReason
import org.enchant.core.calls.model.CallType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailScreen(
    entry: CallLogEntry?,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (entry == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val dateStr = remember(entry.timestamp) {
            try {
                SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
            } catch (_: Exception) {
                entry.timestamp.toString()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (entry.type) {
                            CallType.VIDEO, CallType.GROUP_VIDEO -> Icons.Default.Videocam
                            CallType.GROUP_AUDIO -> Icons.Default.Group
                            CallType.AUDIO -> Icons.Default.Phone
                        },
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(entry.remoteName ?: entry.remoteUserId, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))

            val directionText = when (entry.direction) {
                CallDirection.INCOMING -> "Incoming"
                CallDirection.OUTGOING -> "Outgoing"
            }
            val typeText = when (entry.type) {
                CallType.VIDEO, CallType.GROUP_VIDEO -> "Video"
                CallType.GROUP_AUDIO -> "Group"
                CallType.AUDIO -> "Audio"
            }
            Text("$directionText $typeText call", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(8.dp))

            val statusText = when (entry.status) {
                CallEndReason.HANGUP_LOCAL -> "Completed"
                CallEndReason.HANGUP_REMOTE -> "Declined"
                CallEndReason.BUSY -> "Busy"
                CallEndReason.TIMEOUT -> "No answer"
                CallEndReason.ANSWERED_ELSEWHERE -> "Answered elsewhere"
                CallEndReason.ERROR -> "Error"
                CallEndReason.NETWORK_LOST -> "Network lost"
            }
            val statusColor = when (entry.status) {
                CallEndReason.HANGUP_LOCAL -> MaterialTheme.colorScheme.primary
                CallEndReason.TIMEOUT, CallEndReason.BUSY -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(statusText, style = MaterialTheme.typography.bodyLarge, color = statusColor)

            if (entry.durationSeconds > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                val mins = entry.durationSeconds / 60
                val secs = entry.durationSeconds % 60
                Text("${mins}:${String.format("%02d", secs)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

            Spacer(modifier = Modifier.height(48.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(onClick = onCall) {
                        Icon(Icons.Default.Phone, "Call")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Call", style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(onClick = onMessage) {
                        Icon(Icons.Default.Chat, "Message")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Message", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
