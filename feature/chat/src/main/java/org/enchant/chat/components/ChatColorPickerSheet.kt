package org.enchant.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Signal-style per-conversation chat-color picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatColorPickerSheet(
    conversationId: String,
    onDismiss: () -> Unit,
) {
    val current = ChatColorsDrawable.getColor(conversationId)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                "Chat color",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(presetColorList) { preset ->
                    val selected = when (current) {
                        is ChatColor.Solid -> current.color == preset
                        else -> false
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(preset)
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape,
                            )
                            .clickable {
                                ChatColorsDrawable.setConversationColor(
                                    conversationId,
                                    ChatColor.Solid(preset),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Text("✓", color = Color.White, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            Text(
                "Wallpaper",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            val currentWallpaper = ChatColorsDrawable.getWallpaper(conversationId)
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(wallpaperList) { wp ->
                    val selected = currentWallpaper != null && currentWallpaper == wp
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(wp)
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape,
                            )
                            .clickable {
                                ChatColorsDrawable.setWallpaper(conversationId, wp)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Text("✓", color = Color.White)
                    }
                }
            }
        }
    }
}

private val wallpaperList = listOf(
    Color(0xFFF0F4F8), Color(0xFFFDEBD0), Color(0xFFE8F6F3),
    Color(0xFFF3E8F6), Color(0xFFE8EAF6), Color(0xFFFCF0E3),
    Color(0xFFE8F5E9), Color(0xFFFFF8E1), Color(0xFFECEFF1),
    Color(0xFF1C1C1E), Color(0xFF2C2C2E), Color(0xFF38383A),
)

private val presetColorList = listOf(
    Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF9800),
    Color(0xFFE91E63), Color(0xFFB388E3), Color(0xFF00BCD4),
    Color(0xFF795548), Color(0xFF607D8B), Color(0xFFF44336),
    Color(0xFF3F51B5), Color(0xFF009688), Color(0xFFFF5722),
)
