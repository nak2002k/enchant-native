package org.enchant.channels.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import org.enchant.channels.ChannelPost

private val BrandPurple = Color(0xFF3A0D6E)
private val BrandPurpleDark = Color(0xFFB388E3)
private val BrandPurpleLight = Color(0xFF7B1FA2)
private val BrandRed = Color(0xFFFF3B30)

@Composable
private fun brandPrimary(): Color = if (isSystemInDarkTheme()) BrandPurpleDark else BrandPurple

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
                title = {
                    Text(channelName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
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
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ChannelHeader(
                    channelId = channelId,
                    channelName = channelName,
                    isSubscribed = isSubscribed,
                    isAdmin = isAdmin,
                    onSubscribe = onSubscribe
                )
            }

            if (pinnedPost != null) {
                item {
                    val primary = brandPrimary()
                    Row(
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PushPin,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Pinned post",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = primary
                        )
                    }
                    PostCard(
                        post = pinnedPost,
                        onEdit = { postToEdit = pinnedPost },
                        onDelete = { postToDelete = pinnedPost },
                        onPin = { postToPin = pinnedPost },
                        onShare = onShare
                    )
                }
            }

            items(posts, key = { it.postId }) { post ->
                PostCard(
                    post = post,
                    onEdit = { postToEdit = post },
                    onDelete = { postToDelete = post },
                    onPin = { postToPin = post },
                    onShare = onShare
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ChannelHeader(
    channelId: String,
    channelName: String,
    isSubscribed: Boolean,
    isAdmin: Boolean,
    onSubscribe: () -> Unit
) {
    val primary = brandPrimary()
    val subscriberCount = remember(channelId) { 1_000 + (abs(channelId.hashCode()) % 990_000) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                channelName.take(2).uppercase(),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = primary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channelName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isAdmin) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = primary.copy(alpha = 0.12f)
                    ) {
                        Text("Admin",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "${formatCount(subscriberCount)} subscribers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isSubscribed) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text("Subscribed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(primary)
                    .clickable(onClick = onSubscribe)
                    .padding(horizontal = 20.dp, vertical = 9.dp)
            ) {
                Text("Subscribe",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White)
            }
        }
    }
}

@Composable
private fun PostCard(
    post: ChannelPost,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onShare: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val primary = brandPrimary()
    val views = remember(post.postId) { 40 + (abs(post.postId.hashCode()) % 12_000) }
    val likes = remember(post.postId) { views / 11 + 2 }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            post.authorId.take(1).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = primary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            post.authorId.take(12),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            post.createdAt,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Post options")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    post.content,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (post.mediaIds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.verticalGradient(listOf(
                                primary.copy(alpha = 0.20f),
                                BrandPurpleLight.copy(alpha = 0.10f)
                            ))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Image,
                            "Image",
                            modifier = Modifier.size(32.dp),
                            tint = primary.copy(alpha = 0.6f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${formatCount(views)} views",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Share,
                            "Share",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FavoriteBorder,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = BrandRed
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            formatCount(likes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
}

private fun formatCount(value: Int): String = when {
    value >= 1_000_000 -> {
        val m = value / 1_000_000f
        if (m % 1f == 0f) "${m.toInt()}M" else String.format("%.1fM", m)
    }
    value >= 1_000 -> {
        val k = value / 1_000f
        if (k % 1f == 0f) "${k.toInt()}K" else String.format("%.1fK", k)
    }
    else -> value.toString()
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
