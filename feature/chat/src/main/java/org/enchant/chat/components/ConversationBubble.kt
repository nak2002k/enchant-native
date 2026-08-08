package org.enchant.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.enchant.core.model.DisappearTimerPresets
import org.enchant.core.model.Message
import org.enchant.core.model.MessageStatus
import org.enchant.ui.icons.EnchantIcons
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
    quotedMessage: Message? = null,
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
    var showScrubber by remember { mutableStateOf(false) }
    val bubbleScope = rememberCoroutineScope()
    var viewOnceRevealed by remember { mutableStateOf(false) }
    var viewOnceCountdown by remember { mutableIntStateOf(0) }

    // Swipe-to-reply (Signal gesture): dragging right reveals a reply icon
    // and, past the trigger distance, opens the reply composer on release.
    var swipeDx by remember { mutableFloatStateOf(0f) }
    val swipeThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    val maxSwipePx = with(LocalDensity.current) { 96.dp.toPx() }
    val swipeProgress = (swipeDx / swipeThresholdPx).coerceIn(0f, 1f)

    val bubbleColor = if (isOutgoing) {
        // Signal: outgoing bubble takes the per-conversation chat color
        // (deterministically generated or user-picked), text stays white.
        val chat = ChatColorsDrawable.getColor(message.conversationId)
        when (chat) {
            is ChatColor.Solid -> chat.color
            is ChatColor.Gradient -> chat.start
            else -> EnchantBrand.SignalBlue
        }
    } else {
        MaterialTheme.colorScheme.surface
    }
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
        !isOutgoing -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
        Box(
            modifier = Modifier
                .pointerInput(message.envelopeId) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (swipeDx > swipeThresholdPx) {
                                message.envelopeId?.let { onReply(it) }
                            }
                            swipeDx = 0f
                        },
                        onDragCancel = { swipeDx = 0f },
                    ) { change, dragAmount ->
                        change.consume()
                        // Only swipe right (LTR). Signal replies drag toward the
                        // trailing edge; our bubbles sit left or right, so a
                        // rightward swipe always means "reply to this".
                        swipeDx = (swipeDx + dragAmount).coerceIn(0f, maxSwipePx)
                    }
                }
                .graphicsLayer {
                    translationX = swipeDx
                    val scale = 1f - 0.06f * swipeProgress
                    scaleX = scale
                    scaleY = scale
                }
        ) {
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
                            if (!isSelectionMode) showScrubber = true
                        },
                    )
                    .drawWithContent {
                        drawContent()
                        // 1dp white/4% top highlight, like a soft inner glow on sent bubbles.
                        if (isOutgoing) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.04f),
                                start = Offset(0f, 0.5f),
                                end = Offset(size.width, 0.5f),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                    },
            ) {
                val isImage = message.mediaMimeType?.startsWith("image/") == true &&
                    message.mediaId != null && message.mediaKey != null && !message.isViewOnce
                Column(
                    modifier = Modifier.padding(
                        if (isImage) PaddingValues(4.dp)
                        else PaddingValues(horizontal = EnchantSpacing.md, vertical = EnchantSpacing.sm)
                    )
                ) {
                    // Reply-to preview (Signal-style quoted bubble): the message
                    // this one replies to, rendered with a colored accent bar +
                    // the author's name, above the body.
                    if (quotedMessage != null && message.replyToEnvelopeId != null && !message.isDeleted) {
                        QuotedMessagePreview(
                            text = quotedMessage.content,
                            authorName = if (quotedMessage.senderId == message.senderId &&
                                !isOutgoing) senderName ?: "You" else quotedMessage.senderId.take(8),
                            isOutgoing = isOutgoing,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (message.forwardedFromUserId != null && !message.isDeleted) {
                        Text(
                            "Forwarded",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryColor,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
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
                                        EnchantIcons.eyeOff,
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
                                text = buildMentionAnnotatedString(
                                    content,
                                    baseColor = bodyColor,
                                    mentionColor = if (isOutgoing) Color.White else MaterialTheme.colorScheme.primary,
                                ),
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

            // Swipe-to-reply icon (Signal gesture): fades + scales in at the
            // leading edge as the bubble is dragged right.
            if (swipeProgress > 0.05f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .graphicsLayer {
                            val iconProgress = swipeProgress.coerceIn(0f, 1f)
                            alpha = iconProgress
                            val scale = 1f + 0.2f * iconProgress
                            scaleX = scale
                            scaleY = scale
                            translationX = -12.dp.toPx() * (1f - iconProgress)
                        }
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(bubbleColor.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = EnchantIcons.reply,
                        contentDescription = "Reply",
                        tint = if (isOutgoing) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Reaction scrubber (Signal): long-press reveals a draggable row of
            // quick emoji; tapping one reacts instantly.
            Box(modifier = Modifier.align(Alignment.TopStart)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrubber,
                    enter = fadeIn(animationSpec = tween(120)) + slideInVertically { it / 2 },
                    exit = fadeOut(animationSpec = tween(120)),
                ) {
                    ReactionScrubber(
                        selected = { emoji ->
                            onReact(emoji)
                            showScrubber = false
                        },
                        onDismiss = { showScrubber = false },
                    )
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
                leadingIcon = { Icon(EnchantIcons.star, null) },
            )
            DropdownMenuItem(
                text = { Text("Pin") },
                onClick = { onPin(message.localId); showMenu = false },
                leadingIcon = { Icon(EnchantIcons.pin, null) },
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
                    EnchantIcons.clock,
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
                EnchantIcons.eyeOff,
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
                    EnchantIcons.clock,
                    contentDescription = "Sending",
                    modifier = Modifier.size(13.dp),
                    tint = secondaryColor.copy(alpha = 0.65f),
                )
                MessageStatus.FAILED -> Icon(
                    EnchantIcons.alertCircle,
                    contentDescription = "Failed to send",
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                MessageStatus.SENT -> Icon(
                    EnchantIcons.check,
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
                    EnchantIcons.clock,
                    contentDescription = "Pending",
                    modifier = Modifier.size(13.dp),
                    tint = secondaryColor.copy(alpha = 0.65f),
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
                    EnchantIcons.plusCircle,
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
                bitmap = remember(path) { org.enchant.chat.data.MediaService.decodeBoundedBitmap(path) }
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
                    imageVector = if (isPlaying) EnchantIcons.pause
                        else EnchantIcons.circlePlay,
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

@Composable
private fun QuotedMessagePreview(
    text: String,
    authorName: String,
    isOutgoing: Boolean,
) {
    val accent = if (isOutgoing) Color.White.copy(alpha = 0.85f)
        else MaterialTheme.colorScheme.primary
    val surface = if (isOutgoing) Color.White.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                authorName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text.take(80),
                style = MaterialTheme.typography.bodySmall,
                color = if (isOutgoing) Color.White.copy(alpha = 0.9f)
                    else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReactionScrubber(
    selected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val emojis = listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE0E", "\uD83D\uDE22", "\uD83D\uDE4F", "\uD83D\uDE2E")
    var pressedIndex by remember { mutableIntStateOf(-1) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(EnchantRadii.pill))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .shadow(6.dp, RoundedCornerShape(EnchantRadii.pill))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(EnchantRadii.pill))
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (pressedIndex in emojis.indices) {
                            selected(emojis[pressedIndex])
                        }
                        pressedIndex = -1
                    },
                    onDragCancel = { pressedIndex = -1 },
                ) { change, dragAmount ->
                    change.consume()
                    // Track which emoji the drag is currently over (approximate by x).
                    val x = change.position.x
                    pressedIndex = ((x - 6.dp.toPx()) / 40.dp.toPx()).toInt().coerceIn(0, emojis.size - 1)
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            emojis.forEachIndexed { i, emoji ->
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == pressedIndex) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .clickable {
                            selected(emoji)
                        }
                        .padding(6.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    // Dismiss on outside tap handled by the parent Box's click area; this is
    // enough for quick reactions.
}

/**
 * Renders message body text with Signal-style inline styling:
 *   @mention  → bold + accent color
 *   *bold*    → bold
 *   _italic_  → italic
 *   ~strike~  → strikethrough
 *   ||spoiler|| → blurred-reveal spoiler
 */
private fun buildMentionAnnotatedString(
    text: String,
    baseColor: Color,
    mentionColor: Color,
): AnnotatedString {
    // Single pass over the string; apply inline styles (bold/italic/strike/
    // spoiler) and @mention highlighting. Patterns:
    //   @word      → mention
    //   *word*     → bold
    //   _word_     → italic
    //   ~word~     → strikethrough
    //   ||word||   → spoiler (greyed)
    val token = Regex("""@([A-Za-z0-9_.]+)|\*([^*]+)\*|_([^_]+)_|~([^~]+)~|\|\|([^|]+)\|\|""")
    return buildAnnotatedString {
        var i = 0
        for (m in token.findAll(text)) {
            if (m.range.first > i) append(text.substring(i, m.range.first))
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> withStyle(SpanStyle(color = mentionColor, fontWeight = FontWeight.Bold)) { append("@${g[1]}") }
                g[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[2]) }
                g[3].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[3]) }
                g[4].isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(g[4]) }
                g[5].isNotEmpty() -> withStyle(SpanStyle(color = baseColor.copy(alpha = 0.55f))) { append(g[5]) }
            }
            i = m.range.last + 1
        }
        if (i < text.length) append(text.substring(i))
    }
}
