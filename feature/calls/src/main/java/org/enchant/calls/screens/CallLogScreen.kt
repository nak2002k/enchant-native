package org.enchant.calls.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.enchant.core.calls.CallDirection
import org.enchant.core.calls.CallEndReason
import org.enchant.core.calls.CallLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CallGreen = Color(0xFF34C759)
private val CallRed = Color(0xFFFF3B30)
private val CallGray = Color(0xFF8E8E93)

@Composable
fun CallLogScreen(
    entries: List<CallLogEntry>,
    filter: org.enchant.core.calls.CallLogFilter,
    isLoading: Boolean,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onFilterChange: (org.enchant.core.calls.CallLogFilter) -> Unit,
    onEntryClick: (String) -> Unit,
    onStartSelection: () -> Unit,
    onEndSelection: () -> Unit,
    onToggleSelected: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = CallLogSpacing.Lg, end = CallLogSpacing.Sm, top = CallLogSpacing.Sm, bottom = CallLogSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isSelectionMode) "${selectedIds.size} selected" else "Calls",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isSelectionMode) {
                TextButton(onClick = onSelectAll) {
                    Text("Select All", color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onEndSelection) {
                    Text("Done", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                TextButton(onClick = onStartSelection) {
                    Text("Edit", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        FilterChipsRow(filter = filter, onFilterChange = onFilterChange)

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (entries.isEmpty() && !isLoading) {
            CallEmptyState()
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(entries, key = { it.callId }) { entry ->
                    CallLogRow(
                        entry = entry,
                        isSelected = entry.callId in selectedIds,
                        isSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) onToggleSelected(entry.callId)
                            else onEntryClick(entry.callId)
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                onStartSelection()
                                onToggleSelected(entry.callId)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.CallEmptyState() {
    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Phone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "No calls yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Your call history will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilterChipsRow(filter: org.enchant.core.calls.CallLogFilter, onFilterChange: (org.enchant.core.calls.CallLogFilter) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        org.enchant.core.calls.CallLogFilter.entries.forEach { f ->
            FilterChip(
                selected = filter == f,
                onClick = { onFilterChange(f) },
                label = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallLogRow(
    entry: CallLogEntry,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isMissed = entry.status == CallEndReason.BUSY || entry.status == CallEndReason.TIMEOUT
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else Color.Transparent
    val statusText = when (entry.status) {
        CallEndReason.BUSY -> "Missed call"
        CallEndReason.HANGUP_LOCAL, CallEndReason.HANGUP_REMOTE -> if (entry.durationSeconds > 0) "Answered" else "Cancelled"
        CallEndReason.TIMEOUT -> "No answer"
        CallEndReason.ERROR -> "Failed"
        CallEndReason.NETWORK_LOST -> "Connection lost"
        CallEndReason.ANSWERED_ELSEWHERE -> "Answered elsewhere"
    }
    val subtitle = buildString {
        append(statusText)
        if (entry.durationSeconds > 0) {
            append(" · ")
            append(formatDuration(entry.durationSeconds))
        }
        append(" · ")
        append(formTimestamp(entry.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (entry.remoteName ?: entry.remoteUserId).take(2).uppercase().ifBlank { "?" },
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.remoteName ?: entry.remoteUserId.take(16),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isMissed) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        CallTypeBadge(entry = entry)
    }
}

@Composable
private fun CallTypeBadge(entry: CallLogEntry) {
    val isMissed = entry.status == CallEndReason.BUSY || entry.status == CallEndReason.TIMEOUT
    val icon = when {
        isMissed -> Icons.Rounded.CallEnd
        entry.direction == CallDirection.OUTGOING -> Icons.Rounded.CallMade
        else -> Icons.Rounded.CallReceived
    }
    val tint = when {
        isMissed -> CallRed
        entry.direction == CallDirection.OUTGOING -> CallGray
        else -> CallGreen
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

// ─── Spacing tokens (iOS grouped) ───
private object CallLogSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Lg = 16.dp
}

private fun formatDuration(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private fun formTimestamp(ts: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ts
    return when {
        diff < 86_400_000 ->
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))
        diff < 172_800_000 -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}
