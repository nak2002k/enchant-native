package org.enchant.channels.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.enchant.channels.ChannelPost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelFeedScreen(
    channelId: String,
    channelName: String,
    isSubscribed: Boolean,
    isAdmin: Boolean = false,
    posts: List<ChannelPost>,
    pinnedPost: ChannelPost?,
    onSubscribe: () -> Unit,
    onShare: () -> Unit,
    onLoadMore: () -> Unit,
    onEditPost: (String, String, String) -> Unit = { _, _, _ -> },
    onDeletePost: (String, String) -> Unit = { _, _ -> },
    onPinPost: (String, String, Boolean) -> Unit = { _, _, _ -> }
) {
    var postToEdit by remember { mutableStateOf<ChannelPost?>(null) }
    var postToDelete by remember { mutableStateOf<ChannelPost?>(null) }
    var postToPin by remember { mutableStateOf<ChannelPost?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val totalItems = listState.layoutInfo.totalItemsCount
        if (lastVisible >= totalItems - 3) {
            onLoadMore()
        }
    }

    if (postToEdit != null) {
        EditPostDialog(
            post = postToEdit!!,
            onDismiss = { postToEdit = null },
            onConfirm = { newContent ->
                onEditPost(channelId, postToEdit!!.postId, newContent)
                postToEdit = null
            }
        )
    }

    if (postToDelete != null) {
        DeletePostDialog(
            onDismiss = { postToDelete = null },
            onConfirm = {
                onDeletePost(channelId, postToDelete!!.postId)
                postToDelete = null
            }
        )
    }

    if (postToPin != null) {
        PinPostDialog(
            shouldPin = !postToPin!!.isPinned,
            onDismiss = { postToPin = null },
            onConfirm = {
                onPinPost(channelId, postToPin!!.postId, !postToPin!!.isPinned)
                postToPin = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(channelName) },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, "Share")
                    }
                }
            )
        }
    ) { padding ->
        if (posts.isEmpty() && pinnedPost == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Article,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No posts yet", style = MaterialTheme.typography.titleMedium)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "$channelId subscribers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onSubscribe,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text(if (isSubscribed) "Subscribed" else "Subscribe")
                        }
                    }
                }
            }

            if (pinnedPost != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Pinned post",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    PostCard(
                        post = pinnedPost,
                        onEdit = { postToEdit = pinnedPost },
                        onDelete = { postToDelete = pinnedPost },
                        onPin = { postToPin = pinnedPost }
                    )
                }
            }

            items(posts, key = { it.postId }) { post ->
                PostCard(
                    post = post,
                    onEdit = { postToEdit = post },
                    onDelete = { postToDelete = post },
                    onPin = { postToPin = post }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun PostCard(
    post: ChannelPost,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxWidth()) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        post.authorId.take(12),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        post.createdAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Post options")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    post.content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (post.isOwn()) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (post.isPinned) "Unpin" else "Pin") },
                    onClick = {
                        showMenu = false
                        onPin()
                    },
                    leadingIcon = { Icon(if (post.isPinned) Icons.Default.PushPin else Icons.Default.PushPin, null) }
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun EditPostDialog(
    post: ChannelPost,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var content by remember { mutableStateOf(post.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Post") },
        text = {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(content) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeletePostDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Post") },
        text = { Text("Are you sure you want to delete this post? This action cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PinPostDialog(
    shouldPin: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (shouldPin) "Pin Post" else "Unpin Post") },
        text = { Text(if (shouldPin) "Pin this post to the top of the channel?" else "Remove this post from pinned?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(if (shouldPin) "Pin" else "Unpin")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}