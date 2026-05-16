package org.enchant.chatlist

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.enchant.chat.data.ConversationFilter
import org.enchant.core.model.Conversation

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationListScreen(
    viewModel: ConversationListViewModel,
    onConversationClick: (String) -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val navigationEvent by viewModel.navigationEvent.collectAsState()

    LaunchedEffect(Unit) { viewModel.init() }

    LaunchedEffect(navigationEvent) {
        navigationEvent?.let {
            onConversationClick(it)
            viewModel.clearNavigationEvent()
        }
    }

    var showSearch by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.search(it) },
                            placeholder = { Text("Search conversations") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent
                            )
                        )
                    } else {
                        Text("Enchant")
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch; if (!showSearch) viewModel.search("") }) {
                        Icon(if (showSearch) Icons.Default.Close else Icons.Default.Search, "Search")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (!showSearch) {
                Box {
                    if (showFabMenu) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(y = (-80).dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            SmallFloatingActionButton(
                                onClick = { showFabMenu = false; onNewGroup() },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(Icons.Default.PersonAdd, "New Group")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    FloatingActionButton(
                        onClick = { showFabMenu = !showFabMenu }
                    ) {
                        Icon(if (showFabMenu) Icons.Default.Close else Icons.Default.Edit, "New Chat")
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            FilterChipsRow(
                currentFilter = filter,
                onFilterSelected = { viewModel.selectFilter(it) }
            )

            if (isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (conversations.isEmpty() && !isRefreshing) {
                EmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(conversations, key = { it.id }) { conversation ->
                        ConversationTile(
                            conversation = conversation,
                            onClick = { viewModel.selectConversation(conversation.id) },
                            onArchive = { viewModel.archiveConversation(conversation.id) },
                            onPin = { viewModel.pinConversation(conversation.id) },
                            onMarkRead = { viewModel.markRead(conversation.id) },
                            onDelete = { viewModel.deleteConversation(conversation.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    currentFilter: ConversationFilter,
    onFilterSelected: (ConversationFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = currentFilter == ConversationFilter.ALL,
            onClick = { onFilterSelected(ConversationFilter.ALL) },
            label = { Text("All") }
        )
        FilterChip(
            selected = currentFilter == ConversationFilter.UNREAD,
            onClick = { onFilterSelected(ConversationFilter.UNREAD) },
            label = { Text("Unread") }
        )
        FilterChip(
            selected = currentFilter == ConversationFilter.GROUPS,
            onClick = { onFilterSelected(ConversationFilter.GROUPS) },
            label = { Text("Groups") }
        )
        FilterChip(
            selected = currentFilter == ConversationFilter.PERSONAL,
            onClick = { onFilterSelected(ConversationFilter.PERSONAL) },
            label = { Text("Personal") }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationTile(
    conversation: Conversation,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onPin: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { showMenu = true }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = conversation.id.take(2).uppercase(),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                if (conversation.isPinned) {
                    Surface(
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.TopEnd),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiary
                    ) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.padding(2.dp),
                            tint = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.id.take(16),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatTimestamp(conversation.lastMessageTimestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.lastMessage ?: "No messages yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (conversation.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (conversation.isMuted) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Muted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
        DropdownMenuItem(
            text = { Text(if (conversation.isArchived) "Unarchive" else "Archive") },
            onClick = { onArchive(); showMenu = false },
            leadingIcon = { Icon(Icons.Default.Archive, null) }
        )
        DropdownMenuItem(
            text = { Text(if (conversation.isPinned) "Unpin" else "Pin") },
            onClick = { onPin(); showMenu = false },
            leadingIcon = { Icon(Icons.Default.PushPin, null) }
        )
        if (conversation.unreadCount > 0) {
            DropdownMenuItem(
                text = { Text("Mark read") },
                onClick = { onMarkRead(); showMenu = false },
                leadingIcon = { Icon(Icons.Default.DoneAll, null) }
            )
        }
        DropdownMenuItem(
            text = { Text("Delete") },
            onClick = { onDelete(); showMenu = false },
            leadingIcon = { Icon(Icons.Default.Delete, null) }
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Chat,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No conversations yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Start a new chat",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long?): String {
    if (timestamp == null) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "now"
        diff < 3600_000 -> "${diff / 60_000}m"
        diff < 86400_000 -> "${diff / 3600_000}h"
        diff < 604800_000 -> "${diff / 86400_000}d"
        else -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            "${cal.get(java.util.Calendar.DAY_OF_MONTH)}/${cal.get(java.util.Calendar.MONTH) + 1}"
        }
    }
}
