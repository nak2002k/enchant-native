package org.enchant.calls.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.enchant.core.calls.CallLogEntry
import org.enchant.core.calls.model.CallDirection
import org.enchant.core.calls.model.CallEndReason
import org.enchant.core.calls.model.CallType
import org.enchant.ui.icons.EnchantIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallDetailScreen(
    entry: CallLogEntry?,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    EnchantIcons.arrowLeft,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = "Call Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (entry == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (entry.remoteName ?: entry.remoteUserId).take(2).uppercase().ifBlank { "?" },
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = entry.remoteName ?: entry.remoteUserId,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))

            val directionText = when (entry.direction) {
                CallDirection.INCOMING -> "Incoming"
                CallDirection.OUTGOING -> "Outgoing"
            }
            val typeText = when (entry.type) {
                CallType.VIDEO, CallType.GROUP_VIDEO -> "Video"
                CallType.GROUP_AUDIO -> "Group"
                CallType.AUDIO -> "Audio"
            }
            Text(
                text = "$directionText $typeText call",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

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
                Spacer(Modifier.height(4.dp))
                val mins = entry.durationSeconds / 60
                val secs = entry.durationSeconds % 60
                Text(
                    "${mins}:${String.format("%02d", secs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = dateStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            Spacer(Modifier.height(32.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = "CALL DETAILS",
                    modifier = Modifier.padding(start = CallDetailSpacing.Lg, bottom = CallDetailSpacing.Sm),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp,
                )
                EnchantGroupedCardLocal {
                    DetailRow(label = "Time", value = dateStr)
                    DetailDivider(inset = 16.dp)
                    DetailRow(
                        label = "Duration",
                        value = if (entry.durationSeconds > 0) {
                            val mins = entry.durationSeconds / 60
                            val secs = entry.durationSeconds % 60
                            "${mins}:${String.format("%02d", secs)}"
                        } else {
                            "—"
                        },
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(onClick = onCall) {
                        Icon(EnchantIcons.phone, "Call")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Call", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(onClick = onMessage) {
                        Icon(EnchantIcons.messageCircle, "Message")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Message", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EnchantGroupedCardLocal(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        content()
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CallDetailSpacing.Lg, vertical = CallDetailSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailDivider(inset: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .padding(start = inset)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

// ─── Spacing tokens (iOS grouped) ───
private object CallDetailSpacing {
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
}
