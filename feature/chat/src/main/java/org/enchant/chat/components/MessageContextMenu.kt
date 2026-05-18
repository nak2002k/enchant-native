package org.enchant.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class MessageContextAction(val icon: ImageVector, val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

@Composable
fun MessageContextMenu(
    isOwnMessage: Boolean,
    sentAt: Long,
    isStarred: Boolean = false,
    onCopy: () -> Unit = {},
    onReply: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDeleteForEveryone: () -> Unit = {},
    onDeleteForSelf: () -> Unit = {},
    onForward: () -> Unit = {},
    onStar: () -> Unit = {},
    onInfo: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val twentyFourHoursAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000
    val canEdit = isOwnMessage && sentAt < twentyFourHoursAgo
    val canDeleteForEveryone = isOwnMessage && sentAt < twentyFourHoursAgo

    val actions = listOf(
        MessageContextAction(Icons.Default.ContentCopy, "Copy", onClick = onCopy),
        MessageContextAction(Icons.Default.Reply, "Reply", onClick = onReply),
        MessageContextAction(Icons.Default.Edit, "Edit", enabled = canEdit, onClick = onEdit),
        MessageContextAction(Icons.Default.DeleteForever, "Delete for everyone", enabled = canDeleteForEveryone, onClick = onDeleteForEveryone),
        MessageContextAction(Icons.Default.Delete, "Delete for self", onClick = onDeleteForSelf),
        MessageContextAction(Icons.Default.Forward, "Forward", onClick = onForward),
        MessageContextAction(if (isStarred) Icons.Default.Star else Icons.Default.StarBorder, if (isStarred) "Unstar" else "Star", onClick = onStar),
        MessageContextAction(Icons.Default.Info, "Info", onClick = onInfo)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message actions") },
        text = {
            Column {
                actions.filter { it.enabled }.forEach { action ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { action.onClick(); onDismiss() }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(action.icon, null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(action.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
