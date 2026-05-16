package org.enchant.stickers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPicker(
    library: List<StickerPack>,
    recent: List<Pair<String, String>>,
    onStickerSelected: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onLoadLibrary: () -> Unit = {},
    onLoadRecent: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        onLoadLibrary()
        onLoadRecent()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 450.dp)
                .padding(horizontal = 8.dp)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Installed") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("Recent") })
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTab) {
                0 -> {
                    if (library.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Info, null, Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                Spacer(Modifier.height(8.dp))
                                Text("No sticker packs installed",
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            library.forEach { pack ->
                                if (pack.stickers.isNotEmpty()) {
                                    item(key = pack.packId) {
                                        Text(pack.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                    items(pack.stickers, key = { "${pack.packId}_$it" }) { stickerId ->
                                        Surface(
                                            onClick = { onStickerSelected(pack.packId, stickerId) },
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Box(
                                                modifier = Modifier.aspectRatio(1f).padding(4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(stickerId.take(4),
                                                    fontSize = 24.sp, textAlign = TextAlign.Center)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (recent.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.History, null, Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                Spacer(Modifier.height(8.dp))
                                Text("No recent stickers",
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(recent, key = { "${it.first}_${it.second}" }) { (packId, stickerId) ->
                                Surface(
                                    onClick = { onStickerSelected(packId, stickerId) },
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(
                                        modifier = Modifier.aspectRatio(1f).padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(stickerId.take(4),
                                            fontSize = 24.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
