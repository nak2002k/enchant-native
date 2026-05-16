package org.enchant.calls.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.enchant.core.calls.CallDirection
import org.enchant.core.calls.CallLogEntry
import org.enchant.core.calls.CallLogFilter
import org.enchant.core.calls.CallStatus
import org.enchant.core.calls.CallType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogScreen(
    entries: List<CallLogEntry>,
    filter: CallLogFilter,
    isLoading: Boolean,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onFilterChange: (CallLogFilter) -> Unit,
    onEntryClick: (String) -> Unit,
    onStartSelection: () -> Unit,
    onEndSelection: () -> Unit,
    onToggleSelected: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSelectionMode) "${selectedIds.size} selected" else "Call Log") },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, "Select all") }
                        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
                        IconButton(onClick = onEndSelection) { Icon(Icons.Default.Close, "Cancel") }
                    } else {
                        IconButton(onClick = onStartSelection) { Icon(Icons.Default.Checklist, "Select") }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            FilterChipsRow(filter = filter, onFilterChange = onFilterChange)

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (entries.isEmpty() && !isLoading) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Phone, null, modifier = Modifier.size(64.dp).padding(bottom = 8.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Text("No calls yet", style = MaterialTheme.typography.titleMedium)
                    }
                }
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
}

@Composable
private fun FilterChipsRow(filter: CallLogFilter, onFilterChange: (CallLogFilter) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CallLogFilter.entries.forEach { f ->
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
    val isMissed = entry.status == CallStatus.MISSED
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else Color.Transparent

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = bgColor
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        when (entry.type) {
                            CallType.VIDEO, CallType.GROUP_VIDEO -> Icons.Default.Videocam
                            else -> Icons.Default.Phone
                        },
                        null,
                        tint = if (isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.remoteName ?: entry.remoteUserId.take(16), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (entry.direction == CallDirection.INCOMING) Icons.Default.ArrowBack else Icons.Default.ArrowForward,
                        null, modifier = Modifier.size(14.dp),
                        tint = if (isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when (entry.status) {
                            CallStatus.MISSED -> "Missed call"
                            CallStatus.ANSWERED -> "Answered"
                            CallStatus.CANCELLED -> "Cancelled"
                            CallStatus.OUTGOING -> "Outgoing"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (entry.durationSeconds > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(formatDuration(entry.durationSeconds), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(formTimestamp(entry.timestamp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatDuration(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private fun formTimestamp(ts: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ts
    return when {
        diff < 86_400_000 -> "Today"
        diff < 172_800_000 -> "Yesterday"
        else -> "${java.util.Calendar.getInstance().apply { timeInMillis = ts }
            .get(java.util.Calendar.DAY_OF_MONTH)}/${java.util.Calendar.getInstance().apply { timeInMillis = ts }
            .get(java.util.Calendar.MONTH) + 1}"
    }
}
