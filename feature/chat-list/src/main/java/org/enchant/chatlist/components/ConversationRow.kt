package org.enchant.chatlist.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.enchant.core.model.Conversation
import org.enchant.core.model.ConversationType
import org.enchant.core.model.DisappearTimerPresets
import org.enchant.chatlist.components.EnchantAvatar
import org.enchant.chatlist.components.EnchantGroupAvatar
import org.enchant.chatlist.components.EnchantSpacing
import org.enchant.chatlist.components.UnreadBadge

private val DraftOrange = Color(0xFFFF9500)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationRow(
    conversation: Conversation,
    title: String?,
    lastSenderName: String? = null,
    ownUserId: String? = null,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onMute: () -> Unit,
    onPin: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val unread = conversation.unreadCount > 0
    val hasDraft = !conversation.draftContent.isNullOrBlank()
    val isGroup = conversation.type == ConversationType.GROUP
    val isOutgoing = conversation.lastMessageSenderId != null &&
        ownUserId != null && conversation.lastMessageSenderId == ownUserId

    val preview = remember(conversation, lastSenderName, isOutgoing) {
        buildPreview(conversation, lastSenderName, isOutgoing)
    }
    val previewColor = when {
        hasDraft -> DraftOrange
        unread -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                if (isGroup) {
                    EnchantGroupAvatar(
                        members = groupMemberInitials(title, lastSenderName),
                        size = 52.dp
                    )
                } else {
                    EnchantAvatar(
                        text = title,
                        size = 52.dp,
                        background = avatarColorFor(conversation.id)
                    )
                }
                if (conversation.isPinned) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.padding(4.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(EnchantSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title ?: conversation.id.take(16),
                        fontSize = 17.sp,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = (-0.2).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (unread) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (conversation.isMuted) {
                        Icon(
                            Icons.Rounded.NotificationsOff,
                            contentDescription = "Muted",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(EnchantSpacing.xs))
                    }
                    if (conversation.disappearTimerSeconds > 0) {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = "Disappearing messages: ${DisappearTimerPresets.formatDuration(conversation.disappearTimerSeconds)}",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(EnchantSpacing.xs))
                    }
                    Text(
                        text = formatTimestamp(conversation.lastMessageTimestamp),
                        fontSize = 12.sp,
                        fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                        color = if (unread) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = preview,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = previewColor,
                        modifier = Modifier.weight(1f)
                    )
                    if (unread) {
                        Spacer(modifier = Modifier.width(EnchantSpacing.sm))
                        UnreadBadge(
                            count = conversation.unreadCount,
                            modifier = Modifier.semantics {
                                contentDescription = "${conversation.unreadCount} unread messages"
                            }
                        )
                    }
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

private fun buildPreview(
    conversation: Conversation,
    lastSenderName: String?,
    isOutgoing: Boolean
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val draft = conversation.draftContent
    when {
        !draft.isNullOrBlank() -> {
            builder.append(AnnotatedString("Draft: ", SpanStyle(fontWeight = FontWeight.Bold)))
            builder.append(draft)
        }
        isOutgoing -> {
            builder.append(AnnotatedString("You: ", SpanStyle(fontWeight = FontWeight.Medium)))
            builder.append(conversation.lastMessage ?: "")
        }
        conversation.type == ConversationType.GROUP &&
            conversation.lastMessageSenderId != null && lastSenderName != null -> {
            builder.append("$lastSenderName: ")
            builder.append(conversation.lastMessage ?: "")
        }
        else -> builder.append(conversation.lastMessage ?: "No messages yet")
    }
    return builder.toAnnotatedString()
}

private fun groupMemberInitials(title: String?, lastSenderName: String?): List<String> =
    listOfNotNull(title?.firstOrNull()?.uppercase(), lastSenderName?.firstOrNull()?.uppercase())

// A stable distinct hue per conversation.
private val avatarPalette = listOf(
    Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7), Color(0xFF3F51B5),
    Color(0xFF2196F3), Color(0xFF009688), Color(0xFF4CAF50), Color(0xFFFF9800),
    Color(0xFF795548), Color(0xFF607D8B)
)

private fun avatarColorFor(id: String): Color {
    val hash = id.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7FFFFFFF }
    return avatarPalette[hash % avatarPalette.size]
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
