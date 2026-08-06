package org.enchant.chatlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.enchant.chatlist.components.ConversationRow
import org.enchant.chatlist.components.EnchantAvatar
import org.enchant.chatlist.components.EnchantBrand
import org.enchant.chatlist.components.EnchantEmptyState
import org.enchant.chatlist.components.EnchantFab
import org.enchant.chatlist.components.EnchantMotion
import org.enchant.chatlist.components.EnchantSpacing
import org.enchant.chatlist.components.FilterPillsRow
import org.enchant.chatlist.components.SearchPill
import org.enchant.core.network.ConnectivityMonitor
import org.enchant.core.network.OfflineQueue
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    viewModel: ConversationListViewModel,
    onConversationClick: (String) -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val titles by viewModel.titles.collectAsState()
    val senderNames by viewModel.senderNames.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val navigationEvent by viewModel.navigationEvent.collectAsState()
    val ownInitial = org.enchant.core.base.SecurePreferences.getString("profile.display_name")
        ?.take(1)?.uppercase()
        ?: org.enchant.core.base.SecurePreferences.getString("profile.displayName")?.take(1)?.uppercase()
        ?: "E"
    val ownUserId = org.enchant.core.base.SecurePreferences.getString("auth.user_id")

    val snackbarHostState = remember { SnackbarHostState() }
    val isOnline by ConnectivityMonitor.isOnline.collectAsState()
    val pendingCount by OfflineQueue.pendingCount.collectAsState()
    val listState = rememberLazyListState()
    // Rows animate in once (fade + 8dp slide) on first composition only.
    val rowEnteredIds = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(Unit) { viewModel.init() }

    LaunchedEffect(conversations) {
        viewModel.resolveTitles(conversations.map { it.id })
        viewModel.resolveSenderNames(conversations.mapNotNull { it.lastMessageSenderId })
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

    var fabComposed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { fabComposed = true }
    val fabHidden by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200
        }
    }
    val fabScale by animateFloatAsState(
        targetValue = if (fabComposed && !fabHidden) 1f else 0f,
        animationSpec = EnchantMotion.springBouncy,
        label = "fabScale"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EnchantAvatar(
                            text = ownInitial,
                            size = 34.dp,
                            background = MaterialTheme.colorScheme.primaryContainer,
                            textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .clickable(onClick = onProfileClick)
                                .semantics { contentDescription = "Profile" }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Enchant",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.search("") }) {
                        Icon(
                            Icons.Default.Search,
                            "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = fabScale
                    scaleY = fabScale
                    alpha = fabScale
                }
            ) {
                EnchantFab(
                    onClick = onNewChat,
                    icon = Icons.Rounded.Edit,
                    containerColor = EnchantBrand.SignalBlue,
                    contentColor = Color.White
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            AnimatedVisibility(
                visible = !isOnline,
                enter = expandVertically(animationSpec = tween(240)) + fadeIn(animationSpec = tween(240)),
                exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180)),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = EnchantSpacing.md, vertical = EnchantSpacing.xs),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = EnchantSpacing.lg, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Outlined.WifiOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(EnchantSpacing.sm))
                        Text(
                            "No internet connection",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
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

            SearchPill(
                query = searchQuery,
                onQueryChange = { viewModel.search(it) },
                modifier = Modifier.padding(
                    start = EnchantSpacing.lg,
                    end = EnchantSpacing.lg,
                    top = EnchantSpacing.md,
                    bottom = EnchantSpacing.xs
                )
            )

            Spacer(modifier = Modifier.height(EnchantSpacing.sm))

            FilterPillsRow(
                currentFilter = filter,
                onFilterSelected = { viewModel.selectFilter(it) }
            )

            Spacer(modifier = Modifier.height(EnchantSpacing.sm))

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.weight(1f)
            ) {
                if (conversations.isEmpty() && !isRefreshing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        EnchantEmptyState(
                            icon = Icons.Rounded.ChatBubbleOutline,
                            title = "No chats yet",
                            subtitle = "Start a conversation with a friend or create a group"
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = EnchantSpacing.xs,
                            bottom = 96.dp
                        )
                    ) {
                        itemsIndexed(conversations, key = { _, conversation -> conversation.id }) { index, conversation ->
                            val alreadyEntered = rowEnteredIds[conversation.id] == true
                            val entrance by animateFloatAsState(
                                targetValue = if (alreadyEntered) 1f else 0f,
                                animationSpec = EnchantMotion.spring,
                                label = "rowEntrance",
                            )
                            LaunchedEffect(conversation.id, alreadyEntered) {
                                if (!alreadyEntered) {
                                    delay(index * 30L)
                                    rowEnteredIds[conversation.id] = true
                                }
                            }
                            ConversationRow(
                                conversation = conversation,
                                title = titles[conversation.id],
                                lastSenderName = conversation.lastMessageSenderId?.let { senderNames[it] },
                                ownUserId = ownUserId,
                                onClick = { viewModel.selectConversation(conversation.id) },
                                onArchive = {
                                    if (conversation.isArchived) viewModel.unarchiveConversation(conversation.id)
                                    else viewModel.archiveConversation(conversation.id)
                                },
                                onMute = { viewModel.muteConversation(conversation.id) },
                                onPin = { viewModel.pinConversation(conversation.id) },
                                onMarkRead = { viewModel.markRead(conversation.id) },
                                onDelete = { viewModel.deleteConversation(conversation.id) },
                                modifier = Modifier
                                    .animateItem()
                                    .graphicsLayer {
                                        alpha = entrance
                                        translationY = (1f - entrance) * 8.dp.toPx()
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
