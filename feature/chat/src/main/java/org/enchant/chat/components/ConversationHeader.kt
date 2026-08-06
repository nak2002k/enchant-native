package org.enchant.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.enchant.core.model.Conversation
import org.enchant.core.model.ConversationType
import org.enchant.ui.icons.EnchantIcons

/** iOS-style conversation header; morphs into a selection bar in selection mode. */
@Composable
internal fun ConversationHeader(
    title: String?,
    conversation: Conversation?,
    typingIndicator: Boolean,
    isPeerVerified: Boolean,
    isSelectionMode: Boolean,
    selectionCount: Int,
    onBack: () -> Unit,
    onCloseSelection: () -> Unit,
    onCopySelection: () -> Unit,
    onForwardSelection: () -> Unit,
    onDeleteSelection: () -> Unit,
    onSafetyNumber: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    onViewContact: () -> Unit,
    onSearch: () -> Unit,
    onDisappear: () -> Unit,
    onStarred: () -> Unit,
    onPinned: () -> Unit,
) {
    if (isSelectionMode) {
        SelectionBar(
            count = selectionCount,
            onClose = onCloseSelection,
            onCopy = onCopySelection,
            onForward = onForwardSelection,
            onDelete = onDeleteSelection,
        )
    } else {
        NormalHeader(
            title = title,
            conversation = conversation,
            typingIndicator = typingIndicator,
            isPeerVerified = isPeerVerified,
            onBack = onBack,
            onSafetyNumber = onSafetyNumber,
            onAudioCall = onAudioCall,
            onVideoCall = onVideoCall,
            onViewContact = onViewContact,
            onSearch = onSearch,
            onDisappear = onDisappear,
            onStarred = onStarred,
            onPinned = onPinned,
        )
    }
}

@Composable
private fun NormalHeader(
    title: String?,
    conversation: Conversation?,
    typingIndicator: Boolean,
    isPeerVerified: Boolean,
    onBack: () -> Unit,
    onSafetyNumber: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    onViewContact: () -> Unit,
    onSearch: () -> Unit,
    onDisappear: () -> Unit,
    onStarred: () -> Unit,
    onPinned: () -> Unit,
) {
    val isDirect = conversation?.type == ConversationType.DIRECT
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.semantics { contentDescription = "Navigate back" },
        ) {
            Icon(
                EnchantIcons.chevronLeft,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp),
            )
        }
        EnchantAvatar(
            text = title?.take(2),
            size = 36.dp,
            online = isDirect && !typingIndicator,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title ?: "Chat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (typingIndicator) "typing…" else "End-to-end encrypted",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isDirect) {
            IconButton(
                onClick = onSafetyNumber,
                modifier = Modifier.semantics { contentDescription = "Safety number" },
            ) {
                Icon(
                    if (isPeerVerified) EnchantIcons.lock else Icons.Rounded.LockOpen,
                    contentDescription = "Safety number",
                    tint = if (isPeerVerified) EnchantBrand.SignalBlue
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(
            onClick = onAudioCall,
            modifier = Modifier.semantics { contentDescription = "Start audio call" },
        ) {
            Icon(
                EnchantIcons.phone,
                contentDescription = "Call",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(
            onClick = onVideoCall,
            modifier = Modifier.semantics { contentDescription = "Start video call" },
        ) {
            Icon(
                EnchantIcons.video,
                contentDescription = "Video Call",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        var showMenu by remember { mutableStateOf(false) }
        IconButton(
            onClick = { showMenu = true },
            modifier = Modifier.semantics { contentDescription = "More options" },
        ) {
            Icon(
                EnchantIcons.ellipsisVertical,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("View contact") }, onClick = { onViewContact(); showMenu = false })
            DropdownMenuItem(text = { Text("Search") }, onClick = { onSearch(); showMenu = false })
            DropdownMenuItem(text = { Text("Disappearing messages") }, onClick = { onDisappear(); showMenu = false })
            DropdownMenuItem(text = { Text("Starred messages") }, onClick = { onStarred(); showMenu = false })
            DropdownMenuItem(text = { Text("Pinned messages") }, onClick = { onPinned(); showMenu = false })
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.semantics { contentDescription = "Exit selection" },
        ) {
            Icon(
                EnchantIcons.x,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = if (count == 1) "1 selected" else "$count selected",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onCopy,
            modifier = Modifier.semantics { contentDescription = "Copy selected" },
        ) {
            Icon(
                EnchantIcons.copy,
                contentDescription = "Copy",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(
            onClick = onForward,
            modifier = Modifier.semantics { contentDescription = "Forward selected" },
        ) {
            Icon(
                EnchantIcons.forward,
                contentDescription = "Forward",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.semantics { contentDescription = "Delete selected" },
        ) {
            Icon(
                EnchantIcons.trash2,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
