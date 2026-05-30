package org.enchant.status.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.enchant.status.StatusFeedEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusFeedScreen(
    myStatus: StatusFeedEntry?,
    feed: List<StatusFeedEntry>,
    onStatusTap: (String) -> Unit,
    onCreateStatus: () -> Unit
) {
    val groupedFeed = remember(feed) {
        feed.groupBy { it.userId }
    }
    val sortedUsers = remember(groupedFeed) {
        groupedFeed.entries.sortedBy { (_, statuses) ->
            statuses.any { !it.isViewed }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Status") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateStatus) {
                Icon(Icons.Default.Edit, "Create status")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Surface(
                            modifier = Modifier
                                .size(56.dp)
                                .then(
                                    if (myStatus != null) Modifier.border(
                                        2.dp, MaterialTheme.colorScheme.primary, CircleShape
                                    ) else Modifier
                                ),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            onClick = {
                                if (myStatus != null) onStatusTap(myStatus.statusId)
                                else onCreateStatus()
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (myStatus != null) {
                                    Text(
                                        myStatus.text?.take(2) ?: "S",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Add,
                                        "Add status",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        if (myStatus == null) {
                            Icon(
                                Icons.Default.AddCircle,
                                "Add",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.BottomEnd)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("My Status", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (myStatus != null) "Tap to view" else "Tap to add status",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (feed.isNotEmpty()) {
                    Text(
                        "Recent updates",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            if (feed.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No recent statuses",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            sortedUsers.forEach { (userId, statuses) ->
                val anyUnviewed = statuses.any { !it.isViewed }
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStatusTap(userId) }
                            .then(if (anyUnviewed) Modifier else Modifier.alpha(0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        statuses.firstOrNull()?.username?.take(2)?.uppercase() ?: "?",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    statuses.firstOrNull()?.username ?: "Unknown",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "${statuses.size} update(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (anyUnviewed) {
                                Surface(
                                    modifier = Modifier.size(8.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary
                                ) {}
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}
