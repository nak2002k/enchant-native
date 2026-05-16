package org.enchant.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class EmojiCategory(val name: String, val icon: @Composable () -> Unit, val emojis: List<String>)

object EmojiData {
    val quickReactions = listOf("\u2764\uFE0F", "\uD83D\uDE0D", "\uD83D\uDE02", "\uD83D\uDE0A", "\uD83D\uDE4F", "\uD83D\uDC4D")

    val categories = listOf(
        EmojiCategory("Smileys", { Icon(Icons.Default.EmojiEmotions, "Smileys") }, listOf(
            "\uD83D\uDE00", "\uD83D\uDE01", "\uD83D\uDE02", "\uD83D\uDE03", "\uD83D\uDE04", "\uD83D\uDE05",
            "\uD83D\uDE06", "\uD83D\uDE07", "\uD83D\uDE09", "\uD83D\uDE0A", "\uD83D\uDE0B", "\uD83D\uDE0D",
            "\uD83D\uDE0E", "\uD83D\uDE0F", "\uD83D\uDE10", "\uD83D\uDE11", "\uD83D\uDE12", "\uD83D\uDE13",
            "\uD83D\uDE14", "\uD83D\uDE15", "\uD83D\uDE16", "\uD83D\uDE17", "\uD83D\uDE18", "\uD83D\uDE19",
            "\uD83D\uDE1A", "\uD83D\uDE1B", "\uD83D\uDE1C", "\uD83D\uDE1D", "\uD83D\uDE1E", "\uD83D\uDE1F",
            "\uD83D\uDE20", "\uD83D\uDE21", "\uD83D\uDE22", "\uD83D\uDE23", "\uD83D\uDE24", "\uD83D\uDE25",
            "\uD83D\uDE26", "\uD83D\uDE27", "\uD83D\uDE28", "\uD83D\uDE29", "\uD83D\uDE2A", "\uD83D\uDE2B",
            "\uD83D\uDE2C", "\uD83D\uDE2D", "\uD83D\uDE2E", "\uD83D\uDE2F", "\uD83D\uDE30", "\uD83D\uDE31",
            "\uD83D\uDE32", "\uD83D\uDE33", "\uD83D\uDE34", "\uD83D\uDE35", "\u2764\uFE0F", "\uD83E\uDD0D",
            "\uD83D\uDE36", "\uD83D\uDE37", "\uD83E\uDD12", "\uD83E\uDD14", "\uD83E\uDD17"
        )),
        EmojiCategory("Gestures", { Icon(Icons.Default.ThumbUp, "Gestures") }, listOf(
            "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDC4F", "\uD83D\uDC4C", "\u270A", "\u270B",
            "\uD83E\uDD1A", "\uD83E\uDD1B", "\uD83E\uDD1C", "\uD83E\uDD1D", "\uD83E\uDD1E",
            "\uD83E\uDD1F", "\uD83D\uDC4A", "\u270C\uFE0F", "\uD83E\uDD18",
            "\uD83D\uDC4B", "\uD83D\uDE4F", "\uD83D\uDE4C", "\uD83D\uDE4D", "\uD83D\uDE4E"
        )),
        EmojiCategory("Animals", { Icon(Icons.Default.Pets, "Animals") }, listOf(
            "\uD83D\uDC36", "\uD83D\uDC31", "\uD83D\uDC2D", "\uD83D\uDC39", "\uD83D\uDC30",
            "\uD83D\uDC3B", "\uD83D\uDC3C", "\uD83D\uDC28", "\uD83D\uDC2F", "\uD83D\uDC27",
            "\uD83D\uDC26", "\uD83D\uDC24", "\uD83D\uDC22", "\uD83D\uDC2E", "\uD83D\uDC37",
            "\uD83D\uDC3D", "\uD83D\uDC38", "\uD83D\uDC35", "\uD83D\uDE48", "\uD83D\uDE49",
            "\uD83D\uDE4A", "\uD83D\uDC14", "\uD83D\uDC17", "\uD83D\uDC34", "\uD83D\uDC0E"
        )),
        EmojiCategory("Food", { Icon(Icons.Default.Restaurant, "Food") }, listOf(
            "\uD83C\uDF4E", "\uD83C\uDF4F", "\uD83C\uDF50", "\uD83C\uDF51", "\uD83C\uDF52",
            "\uD83C\uDF53", "\uD83C\uDF54", "\uD83C\uDF55", "\uD83C\uDF56", "\uD83C\uDF57",
            "\uD83C\uDF58", "\uD83C\uDF59", "\uD83C\uDF5A", "\uD83C\uDF5B", "\uD83C\uDF5C",
            "\uD83C\uDF5D", "\uD83C\uDF5E", "\uD83C\uDF5F", "\uD83C\uDF60", "\uD83C\uDF61",
            "\uD83C\uDF62", "\uD83C\uDF63", "\uD83C\uDF64", "\uD83C\uDF65", "\uD83C\uDF66",
            "\uD83C\uDF67", "\uD83C\uDF68", "\uD83C\uDF69", "\u2615", "\uD83C\uDF75",
            "\uD83C\uDF76", "\uD83C\uDF7A", "\uD83C\uDF7B"
        )),
        EmojiCategory("Travel", { Icon(Icons.Default.Flight, "Travel") }, listOf(
            "\uD83C\uDF0D", "\uD83C\uDF0E", "\uD83C\uDF0F", "\uD83C\uDF10", "\uD83D\uDE80",
            "\uD83D\uDE81", "\uD83D\uDE82", "\uD83D\uDE83", "\uD83D\uDE84", "\uD83D\uDE85",
            "\uD83D\uDE86", "\uD83D\uDE87", "\uD83D\uDE88", "\uD83D\uDE89", "\uD83D\uDE8A",
            "\uD83D\uDE8B", "\uD83D\uDE8C", "\uD83D\uDE8D", "\uD83D\uDE8E", "\uD83D\uDE8F",
            "\uD83D\uDE90", "\uD83D\uDE91", "\uD83D\uDE92", "\u26F5", "\uD83D\uDEA2",
            "\uD83C\uDFD6\uFE0F", "\u26FA", "\uD83C\uDFD7\uFE0F"
        )),
        EmojiCategory("Symbols", { Icon(Icons.Default.Tag, "Symbols") }, listOf(
            "\u2764\uFE0F", "\uD83E\uDD0D", "\uD83D\uDC9B", "\uD83D\uDC9A", "\uD83D\uDC99",
            "\uD83D\uDC9C", "\uD83D\uDC9E", "\uD83D\uDC93", "\uD83D\uDC97", "\uD83D\uDC95",
            "\uD83E\uDD0E", "\u2728", "\u2B50", "\uD83D\uDD25", "\uD83C\uDF20",
            "\uD83C\uDF1F", "\uD83C\uDF0D", "\uD83C\uDF1E", "\u2600\uFE0F", "\uD83C\uDF26\uFE0F",
            "\u2601\uFE0F", "\u26C5", "\uD83C\uDF27\uFE0F", "\uD83C\uDF2B\uFE0F"
        ))
    )

    private val emojiNameMap = mapOf(
        "\uD83D\uDE00" to "grinning face", "\u2764\uFE0F" to "heart", "\uD83D\uDE0D" to "heart eyes",
        "\uD83D\uDE02" to "tears of joy", "\uD83D\uDE0A" to "smiling face", "\uD83D\uDC4D" to "thumbs up"
    )

    fun searchEmoji(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        return categories.flatMap { it.emojis }.filter { emoji ->
            val name = emojiNameMap[emoji] ?: ""
            name.contains(query, ignoreCase = true)
        }.take(20)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerSheet(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedCategory by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .padding(horizontal = 8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search emoji") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, "Clear")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Quick reactions", style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EmojiData.quickReactions) { emoji ->
                    Surface(
                        onClick = { onEmojiSelected(emoji) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 22.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (searchQuery.isNotBlank()) {
                val results = remember(searchQuery) { EmojiData.searchEmoji(searchQuery) }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(results) { emoji ->
                        Text(
                            emoji, fontSize = 28.sp,
                            modifier = Modifier
                                .clickable { onEmojiSelected(emoji) }
                                .padding(4.dp)
                        )
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(EmojiData.categories.size) { idx ->
                        FilterChip(
                            selected = idx == selectedCategory,
                            onClick = { selectedCategory = idx },
                            label = EmojiData.categories[idx].icon,
                            modifier = Modifier.height(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(EmojiData.categories[selectedCategory].emojis) { emoji ->
                        Text(
                            emoji, fontSize = 28.sp,
                            modifier = Modifier
                                .clickable { onEmojiSelected(emoji) }
                                .padding(4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
