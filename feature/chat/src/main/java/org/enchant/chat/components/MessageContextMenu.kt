package org.enchant.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private val BrandPrimaryLight = Color(0xFF3A0D6E)
private val BrandPrimaryDark = Color(0xFFB388E3)
private val DangerRed = Color(0xFFFF3B30)

@Composable
private fun brandPrimary(): Color = if (isSystemInDarkTheme()) BrandPrimaryDark else BrandPrimaryLight

data class MessageContextAction(val icon: ImageVector, val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberModalBottomSheetState()
    val brand = brandPrimary()

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                "Message actions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            var deleteGroupStarted = false
            actions.filter { it.enabled }.forEach { action ->
                if (action.label.startsWith("Delete") && !deleteGroupStarted) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 76.dp, end = 0.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    deleteGroupStarted = true
                }

                val tint = when {
                    action.label.startsWith("Delete") -> DangerRed
                    action.label == "Edit" || action.label == "Star" ||
                        action.label == "Unstar" || action.label == "Info" ->
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else -> brand
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable { action.onClick(); onDismiss() }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(action.icon, null, tint = tint, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(action.label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
