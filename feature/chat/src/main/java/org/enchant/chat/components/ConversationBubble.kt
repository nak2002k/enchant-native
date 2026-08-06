package org.enchant.chat.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forward
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.enchant.core.model.DisappearTimerPresets
import org.enchant.core.model.Message
import org.enchant.core.model.MessageStatus
import java.io.File

// ─── Shared bubble time/format helpers ───

internal fun formatTime(timestamp: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val min = cal.get(java.util.Calendar.MINUTE)
    return "${hour.toString().padStart(2, '0')}:${min.toString().padStart(2, '0')}"
}

internal fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}

internal fun formatDuration(durationMs: Int): String {
    val totalSec = durationMs / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

// ─── Message bubble (Signal-grade) ───

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageBubble(
    message: Message,
    isOutgoing: Boolean,
    senderId: String? = null,
    senderName: String? = null,
    showSenderName: Boolean = false,
    hasTail: Boolean = false,
    verticalGap: Dp = EnchantSpacing.md,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongPress: () -> Unit = {},
    onToggleSelection: () -> Unit = {},
    onReply: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onDeleteEveryone: (String) -> Unit = {},
    onEdit: (String) -> Unit = {},
    onForward: (String) -> Unit = {},
    onCopy: (String) -> Unit = {},
    onReact: (String) -> Unit = {},
    onReport: (String) -> Unit = {},
    onTranslate: (String) -> Unit = {},
    onStar: (Long) -> Unit = {},
    onPin: (Long) -> Unit = {},
    onInfo: (Message) -> Unit = {},
    onViewOnceViewed: (String) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val bubbleScope = rememberCoroutineScope()
    var viewOnceRevealed by remember { mutableStateOf(false) }
    var viewOnceCountdown by remember { mutableIntStateOf(0) }

    val bubbleColor = if (isOutgoing) EnchantBrand.SignalBlue else MaterialTheme.colorScheme.surface
    val bodyColor = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryColor = if (isOutgoing) Color.White.copy(alpha = 0.65f)
        else MaterialTheme.colorScheme.onSurfaceVariant

    // View-once: after the reveal, count down and expire the message.
    LaunchedEffect(viewOnceCountdown) {
        if (viewOnceCountdown > 0) {
            delay(1000)
            viewOnceCountdown--
        }
    }
    if (viewOnceRevealed && viewOnceCountdown <= 0 && message.isViewOnce) {
        viewOnceRevealed = false
    }

    // Entrance: new messages spring in (alpha 0→1, scale 0.98→1).
    var entered by remember { mutableStateOf(false) }
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = EnchantMotion.spring,
        label = "bubbleEntrance",
    )
    LaunchedEffect(Unit) { entered = true }

    // Shape: 18dp radius; the sender-side bottom corner is tight (4dp) and,
    // only on the first bubble of a run, carries the tail nub.
    val tailCorner = if (hasTail) 18.dp else 4.dp
    val shape = if (isOutgoing) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = tailCorner, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = tailCorner)
    }
    val border = when {
        isSelectionMode && isSelected -> BorderStroke(2.dp, EnchantBrand.SignalBlue)
        isSelectionMode -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        !isOutgoing -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = EnchantSpacing.md,
                end = EnchantSpacing.md,
                top = verticalGap,
                bottom = 2.dp,
            )
            .graphicsLayer {
                alpha = entrance
                val s = 0.98f + 0.02f * entrance
                scaleX = s
                scaleY = s
            },
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
    ) {
        if (showSenderName && senderName != null) {
            Text(
                text = senderName,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, end = 4.dp),
                color = senderId?.let { senderNameColor(it) } ?: MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            Surface(
                shape = shape,
                color = bubbleColor,
                border = border,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(
                        onClick = { if (isSelectionMode) onToggleSelection() },
                        onLongClick = {
                            onLongPress()
                            if (!isSelectionMode) showMenu = true
                        },
                    ),
            ) {
                val isImage = message.mediaMimeType?.startsWith("image/") == true &&
                    message.mediaId != null && message.mediaKey != null && !message.isViewOnce
                Column(
                    modifier = Modifier.padding(
                        if (isImage) PaddingValues(4.dp)
                        else PaddingValues(horizontal = EnchantSpacing.md, vertical = EnchantSpacing.sm)
                    )
                ) {
                    if (message.isDeleted) {
                        Text(
                            "This message was deleted",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryColor,
                            fontStyle = FontStyle.Italic,
                        )
                    } else if (message.mediaMimeType != null) {
                        val mimeType = message.mediaMimeType
                        val mid = message.mediaId
                        val mkey = message.mediaKey
                        if (mimeType != null && mimeType.startsWith("audio/")) {
                            VoiceMessageContent(
                                mediaMimeType = mimeType,
                                mediaSize = message.mediaSize,
                                content = message.content,
                                mediaId = message.mediaId,
                                mediaKey = message.mediaKey,
                                primaryColor = bodyColor,
                                secondaryColor = secondaryColor,
                            )
                        } else if (mimeType != null && mimeType.startsWith("image/") &&
                            mid != null && mkey != null && message.isViewOnce && !viewOnceRevealed
                        ) {
                            // View-once: hidden until tapped, then revealed,
                            // counted down and deleted (Signal behavior).
                            Surface(
                                modifier = Modifier
                                    .width(200.dp)
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(EnchantRadii.medium))
                                    .clickable {
                                        viewOnceRevealed = true
                                        onViewOnceViewed(message.envelopeId ?: "")
                                        viewOnceCountdown = 5
                                    },
                                color = if (isOutgoing) Color.White.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.VisibilityOff,
                                        contentDescription = null,
                                        tint = if (isOutgoing) Color.White.copy(alpha = 0.8f)
                                            else MaterialTheme.colorScheme.tertiary,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Tap to view",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryColor,
                                    )
                                }
                            }
                        } else if (mimeType != null && mimeType.startsWith("image/") &&
                            mid != null && mkey != null
                        ) {
                            EncryptedImageContent(
                                mediaId = mid,
                                mediaKey = mkey,
                                mimeType = mimeType,
                                fileName = message.content.removePrefix("📎 "),
                                contentColor = bodyColor,
                                secondaryColor = secondaryColor,
                            )
                        } else {
                            Text(
                                "📎 ${message.mediaMimeType}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = bodyColor,
                            )
                            val size = message.mediaSize
                            if (size != null) {
                                Text(
                                    formatFileSize(size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryColor,
                                )
                            }
                        }
                    } else {
                        val content = message.content
                        if (content.startsWith("POLL_ID:")) {
                            val pollId = content.removePrefix("POLL_ID:")
                            val pollVm: org.enchant.polls.PollViewModel = viewModel()
                            val pollState by pollVm.uiState.collectAsState()
                            LaunchedEffect(pollId) { pollVm.loadPoll(pollId) }
                            val poll = pollState.currentPoll
                            if (poll != null && poll.pollId == pollId) {
                                org.enchant.polls.PollBubble(
                                    poll = poll,
                                    onVote = { optionIds ->
                                        bubbleScope.launch { pollVm.vote(pollId, optionIds) }
                                    },
                                    isVoting = pollState.isSubmitting,
                                    isCreator = false,
                                )
                            } else {
                                Text(
                                    "📊 Poll",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else {
                            val urlPattern = Regex("https?://[^\\s]+")
                            val urls = urlPattern.findAll(content).map { it.value }.toList()
                            Text(
                                text = content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = bodyColor,
                            )
                            if (urls.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                LinkPreviewCard(
                                    url = urls.first(),
                                    containerColor = if (isOutgoing) Color.White.copy(alpha = 0.14f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    textColor = bodyColor,
                                    secondaryColor = secondaryColor,
                                    accentColor = if (isOutgoing) Color.White
                                        else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    TimestampRow(
                        message = message,
                        isOutgoing = isOutgoing,
                        secondaryColor = secondaryColor,
                        bodyColor = bodyColor,
                    )
                }
            }
            // Small tail triangle on the first bubble of the run.
            if (hasTail) {
                Box(
                    modifier = Modifier
                        .align(if (isOutgoing) Alignment.BottomEnd else Alignment.BottomStart)
                        .offset(x = if (isOutgoing) (-1).dp else 1.dp, y = (-1).dp),
                ) {
                    BubbleTail(color = bubbleColor, isOutgoing = isOutgoing)
                }
            }
        }

        if (message.reactions.isNotEmpty()) {
            ReactionsRow(
                reactions = message.reactions,
                isOutgoing = isOutgoing,
                onAddReaction = { showMenu = true },
            )
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE0E", "\uD83D\uDE22", "\uD83D\uDE4F")
                    .forEach { emoji ->
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onReact(emoji); showMenu = false }
                                .padding(8.dp),
                        )
                    }
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Copy") }, onClick = { onCopy(message.envelopeId ?: ""); showMenu = false })
            DropdownMenuItem(text = { Text("Reply") }, onClick = { onReply(message.envelopeId ?: ""); showMenu = false })
            if (isOutgoing) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { onEdit(message.envelopeId ?: ""); showMenu = false })
            }
            DropdownMenuItem(text = { Text("Info") }, onClick = { onInfo(message); showMenu = false })
            DropdownMenuItem(text = { Text("Forward") }, onClick = { onForward(message.envelopeId ?: ""); showMenu = false })
            DropdownMenuItem(
                text = { Text(if (message.isStarred) "Unstar" else "Star") },
                onClick = { onStar(message.localId); showMenu = false },
                leadingIcon = { Icon(if (message.isStarred) Icons.Filled.Star else Icons.Rounded.StarBorder, null) },
            )
            DropdownMenuItem(
                text = { Text("Pin") },
                onClick = { onPin(message.localId); showMenu = false },
                leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
            )
            if (isOutgoing) {
                DropdownMenuItem(
                    text = { Text("Delete for everyone") },
                    onClick = { onDeleteEveryone(message.envelopeId ?: ""); showMenu = false },
                )
            }
            DropdownMenuItem(text = { Text("Report") }, onClick = { onReport(message.envelopeId ?: ""); showMenu = false })
            DropdownMenuItem(text = { Text("Translate") }, onClick = { onTranslate(message.envelopeId ?: ""); showMenu = false })
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { onDelete(message.envelopeId ?: ""); showMenu = false },
            )
        }
    }
}

@Composable
private fun TimestampRow(
    message: Message,
    isOutgoing: Boolean,
    secondaryColor: Color,
    bodyColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        val disappearAt = message.disappearAt
        if (disappearAt != null && disappearAt > 0) {
            val remaining = DisappearTimerPresets.formatTimeRemaining(disappearAt)
            if (remaining != "Expired") {
                Icon(
                    Icons.Rounded.Timer,
                    contentDescription = "Disappears in $remaining",
                    modifier = Modifier.size(12.dp),
                    tint = if (isOutgoing) Color.White.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    remaining,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOutgoing) Color.White.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        if (message.isEdited) {
            Text(
                "edited",
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor,
            )
            Spacer(Modifier.width(2.dp))
        }

        if (message.isViewOnce) {
            Icon(
                Icons.Rounded.VisibilityOff,
                contentDescription = "View once",
                modifier = Modifier.size(12.dp),
                tint = secondaryColor,
            )
            Spacer(Modifier.width(2.dp))
        }

        Text(
            formatTime(message.timestamp),
            fontSize = 11.sp,
            color = secondaryColor,
        )
        Spacer(Modifier.width(2.dp))

        if (isOutgoing) {
            when (message.status) {
                MessageStatus.SENDING -> Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = "Sending",
                    modifier = Modifier.size(13.dp),
                    tint = secondaryColor.copy(alpha = 0.4f),
                )
                MessageStatus.FAILED -> Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = "Failed to send",
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                MessageStatus.SENT -> Icon(
                    Icons.Filled.Check,
                    contentDescription = "Sent",
                    modifier = Modifier.size(13.dp),
                    tint = secondaryColor.copy(alpha = 0.9f),
                )
                MessageStatus.DELIVERED -> ReadReceiptIcon(
                    read = false,
                    tint = secondaryColor.copy(alpha = 0.75f),
                )
                MessageStatus.READ -> ReadReceiptIcon(
                    read = true,
                    tint = if (isOutgoing) Color.White.copy(alpha = 0.95f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = "Pending",
                    modifier = Modifier.size(13.dp),
                    tint = secondaryColor.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@Composable
private fun ReactionsRow(
    reactions: List<org.enchant.core.model.Reaction>,
    isOutgoing: Boolean,
    onAddReaction: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(start = EnchantSpacing.sm, end = EnchantSpacing.sm, top = 4.dp),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        reactions.groupBy { it.emoji }.entries.forEach { (emoji, reactors) ->
            Surface(
                shape = RoundedCornerShape(EnchantRadii.pill),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.padding(end = 4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(emoji, fontSize = 13.sp)
                    if (reactors.size > 1) {
                        Spacer(Modifier.width(2.dp))
                        Text(
                            reactors.size.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(EnchantRadii.pill),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.clickable(onClick = onAddReaction),
        ) {
            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add reaction",
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// 2-3dp tail triangle nub.
@Composable
private fun BubbleTail(color: Color, isOutgoing: Boolean) {
    Canvas(modifier = Modifier.size(width = 7.dp, height = 5.dp)) {
        val path = Path().apply {
            if (isOutgoing) {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(0f, size.height)
            }
            close()
        }
        drawPath(path, color)
    }
}

/** Fetches + decrypts a media blob and renders the image in the bubble;
 *  tap opens the fullscreen viewer (Signal behavior). */
@Composable
internal fun EncryptedImageContent(
    mediaId: String,
    mediaKey: String,
    mimeType: String,
    fileName: String,
    contentColor: Color,
    secondaryColor: Color,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var mediaPath by remember(mediaId) { mutableStateOf<String?>(null) }
    var failed by remember(mediaId) { mutableStateOf(false) }
    var showViewer by remember { mutableStateOf(false) }

    LaunchedEffect(mediaId) {
        val key = runCatching { org.enchant.core.crypto.CryptoPrimitives.base64UrlDecode(mediaKey) }.getOrNull()
        if (key == null || key.size != 32) { failed = true; return@LaunchedEffect }
        org.enchant.chat.data.MediaService.downloadAndDecryptMedia(mediaId, key)
            .onSuccess { file -> mediaPath = file.absolutePath }
            .onFailure { failed = true }
    }

    if (mediaPath != null) {
        val path = mediaPath!!
        Box(
            modifier = Modifier
                .widthIn(max = 240.dp)
                .clickable { showViewer = true },
        ) {
            androidx.compose.foundation.Image(
                bitmap = remember(path) { android.graphics.BitmapFactory.decodeFile(path) }
                    ?.asImageBitmap() ?: return@Box,
                contentDescription = fileName,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Fit,
            )
        }
        if (showViewer) {
            MediaViewerScreen(mediaPath = path, mimeType = mimeType, onDismiss = { showViewer = false })
        }
    } else if (failed) {
        Text(
            "📎 $fileName (unavailable)",
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryColor,
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("📎 $fileName", style = MaterialTheme.typography.bodyMedium, color = contentColor)
        }
    }
}

/** Fetches the server-generated preview for a URL and renders the card
 *  (title + description + image, tap opens the link). */
@Composable
internal fun LinkPreviewCard(
    url: String,
    containerColor: Color,
    textColor: Color,
    secondaryColor: Color,
    accentColor: Color,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var preview by remember(url) { mutableStateOf<org.enchant.chat.data.LinkPreview?>(null) }

    LaunchedEffect(url) {
        preview = org.enchant.chat.data.ContentPreProcessor.generateLinkPreview(url)
    }

    Surface(
        shape = RoundedCornerShape(EnchantRadii.medium),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(url),
                    )
                    context.startActivity(intent)
                }
            },
    ) {
        Column(modifier = Modifier.padding(EnchantSpacing.sm)) {
            preview?.let { p ->
                if (p.title != null) {
                    Text(
                        p.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = textColor,
                    )
                }
                if (p.description != null) {
                    Text(
                        p.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            Text(
                url,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Voice-message player row, themed to sit inside a bubble. */
@Composable
internal fun VoiceMessageContent(
    mediaMimeType: String,
    mediaSize: Long?,
    content: String,
    mediaId: String?,
    mediaKey: String?,
    primaryColor: Color,
    secondaryColor: Color,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var durationMs by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var player: android.media.MediaPlayer? by remember { mutableStateOf(null) }
    var mediaPath by remember(mediaId) { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    fun loadAndPlay() {
        if (mediaPath != null) {
            val p = player ?: android.media.MediaPlayer().apply {
                setOnPreparedListener {
                    durationMs = it.duration
                    it.start()
                    isPlaying = true
                }
                setOnCompletionListener {
                    isPlaying = false
                    progress = 0f
                }
            }
            player = p
            if (isPlaying) {
                p.pause()
                isPlaying = false
            } else {
                runCatching { p.reset() }
                runCatching {
                    p.setDataSource(mediaPath!!)
                    p.prepareAsync()
                }
            }
            return
        }
        val key = mediaKey?.let { runCatching { org.enchant.core.crypto.CryptoPrimitives.base64UrlDecode(it) }.getOrNull() }
        val id = mediaId
        if (id == null || key == null || key.size != 32) { failed = true; return }
        scope.launch {
            org.enchant.chat.data.MediaService.downloadAndDecryptMedia(id, key)
                .onSuccess { file ->
                    mediaPath = file.absolutePath
                    loadAndPlay()
                }
                .onFailure { failed = true }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            player?.let { p ->
                if (p.isPlaying && p.duration > 0) {
                    progress = p.currentPosition.toFloat() / p.duration
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            androidx.compose.material3.IconButton(onClick = { loadAndPlay() }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.PauseCircle
                        else Icons.Rounded.PlayCircle,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = primaryColor,
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.small),
                color = primaryColor,
                trackColor = if (primaryColor == Color.White) Color.White.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (failed) "unavailable" else formatDuration(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor,
            )
        }
        if (mediaSize != null) {
            Text(
                formatFileSize(mediaSize),
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor,
            )
        }
    }
}
