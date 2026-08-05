package org.enchant.chatlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import org.enchant.chat.data.ConversationFilter
import org.enchant.core.model.Conversation
import org.enchant.core.model.DisappearTimerPresets
import org.enchant.core.network.ConnectivityMonitor
import org.enchant.core.network.OfflineQueue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationListScreen(
    viewModel: ConversationListViewModel,
    onConversationClick: (String) -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val titles by viewModel.titles.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val navigationEvent by viewModel.navigationEvent.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val isOnline by ConnectivityMonitor.isOnline.collectAsState()
    val pendingCount by OfflineQueue.pendingCount.collectAsState()

    LaunchedEffect(Unit) { viewModel.init() }

    LaunchedEffect(conversations) {
        viewModel.resolveTitles(conversations.map { it.id })
    }

    LaunchedEffect(navigationEvent) {
        navigationEvent?.let {
            onConversationClick(it)
            viewModel.clearNavigationEvent()
        }
    }

    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    var showSearch by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
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
                                onClick = { showFabMenu = false; onNewChat() },
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(Icons.Default.Person, "New Chat")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
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
                        // Pencil that rotates into an X when the menu opens.
                        val rotation by animateFloatAsState(
                            targetValue = if (showFabMenu) 180f else 0f,
                            animationSpec = tween(250),
                            label = "fabRotation"
                        )
                        Icon(
                            if (showFabMenu) Icons.Default.Close else Icons.Default.Edit,
                            "New Chat",
                            modifier = Modifier.rotate(rotation)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            AnimatedVisibility(visible = !isOnline) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        "No internet connection",
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }

            AnimatedVisibility(visible = pendingCount > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        "$pendingCount messages pending",
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }

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
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                                animationSpec = tween(300),
                                initialOffsetY = { it / 4 }
                            )
                        ) {
                            ConversationTile(
                                conversation = conversation,
                                title = titles[conversation.id],
                                onClick = { viewModel.selectConversation(conversation.id) },
                        onArchive = {
                            if (conversation.isArchived) viewModel.unarchiveConversation(conversation.id)
                            else viewModel.archiveConversation(conversation.id)
                        },
                        onMute = { viewModel.muteConversation(conversation.id) },
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
        FilterChip(
            selected = currentFilter == ConversationFilter.ARCHIVED,
            onClick = { onFilterSelected(ConversationFilter.ARCHIVED) },
            label = { Text("Archived") }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationTile(
    conversation: Conversation,
    title: String?,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onMute: () -> Unit,
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
                        text = (title?.take(1)?.uppercase() ?: conversation.type.name.take(1)),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { contentDescription = "Avatar" }
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
                        text = title ?: conversation.id.take(16),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (conversation.disappearTimerSeconds > 0) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = "Disappearing messages: ${DisappearTimerPresets.formatDuration(conversation.disappearTimerSeconds)}",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = formatTimestamp(conversation.lastMessageTimestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = run {
                            val draft = conversation.draftContent
                            if (!draft.isNullOrBlank()) "Draft: $draft"
                            else conversation.lastMessage ?: "No messages yet"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (!conversation.draftContent.isNullOrBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (!conversation.draftContent.isNullOrBlank()) FontWeight.Medium else FontWeight.Normal,
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
                                text = if (conversation.unreadCount >= 100) "99+" else conversation.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    .semantics { contentDescription = "${conversation.unreadCount} unread messages" }
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
            text = { Text(if (conversation.isMuted) "Unmute" else "Mute") },
            onClick = { onMute(); showMenu = false },
            leadingIcon = { Icon(if (conversation.isMuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff, null) }
        )
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
        diff < 86400_000 -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            "${cal.get(java.util.Calendar.HOUR_OF_DAY).toString().padStart(2, '0')}:${cal.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')}"
        }
        diff < 604800_000 -> "${diff / 86400_000}d"
        else -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            "${cal.get(java.util.Calendar.DAY_OF_MONTH)}/${cal.get(java.util.Calendar.MONTH) + 1}"
        }
    }
}
