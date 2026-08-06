package org.enchant.stickers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.enchant.ui.icons.EnchantIcons

private val BrandPrimaryLight = Color(0xFF3A0D6E)
private val BrandPrimaryDark = Color(0xFFB388E3)

@Composable
private fun brandPrimary(): Color = if (isSystemInDarkTheme()) BrandPrimaryDark else BrandPrimaryLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPicker(
    library: List<StickerPack>,
    recent: List<Pair<String, String>>,
    onStickerSelected: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onLoadLibrary: () -> Unit = {},
    onLoadRecent: () -> Unit = {},
    onOpenStore: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val brand = brandPrimary()

    LaunchedEffect(Unit) {
        onLoadLibrary()
        onLoadRecent()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 450.dp)
                .padding(horizontal = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(listOf("Installed", "Recent")) { tabLabel ->
                        val tabIndex = if (tabLabel == "Installed") 0 else 1
                        val selected = selectedTab == tabIndex
                        Surface(
                            onClick = { selectedTab = tabIndex },
                            shape = RoundedCornerShape(50),
                            color = if (selected) brand else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (selected) brand else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Text(
                                tabLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    onClick = onOpenStore,
                    shape = RoundedCornerShape(50),
                    color = brand.copy(alpha = 0.12f)
                ) {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            EnchantIcons.store,
                            "Sticker store",
                            tint = brand,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTab) {
                0 -> {
                    if (library.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(EnchantIcons.info, null, Modifier.size(48.dp),
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            library.forEach { pack ->
                                if (pack.stickers.isNotEmpty()) {
                                    item(key = pack.packId) {
                                        Text(pack.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                    items(pack.stickers, key = { "${pack.packId}_$it" }) { stickerId ->
                                        Surface(
                                            onClick = { onStickerSelected(pack.packId, stickerId) },
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
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
                                Icon(EnchantIcons.rotateCcw, null, Modifier.size(48.dp),
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(recent, key = { "${it.first}_${it.second}" }) { (packId, stickerId) ->
                                Surface(
                                    onClick = { onStickerSelected(packId, stickerId) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
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
