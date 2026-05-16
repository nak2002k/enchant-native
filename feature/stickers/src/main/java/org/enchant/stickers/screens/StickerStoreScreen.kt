package org.enchant.stickers.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.enchant.stickers.StickerPack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerStoreScreen(
    featured: List<StickerPack>,
    searchResults: List<StickerPack>,
    isLoading: Boolean,
    error: String?,
    onInstall: (String) -> Unit,
    onSearch: (String) -> Unit,
    onPackClick: (String) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sticker Store") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    onSearch(it)
                },
                placeholder = { Text("Search sticker packs") },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = ""; onSearch("") }) {
                            Icon(Icons.Default.Close, "Clear")
                        }
                    }
                }
            )

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) {
                    Text(error, modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            val displayList = if (searchQuery.isNotBlank()) searchResults else featured

            if (displayList.isEmpty() && !isLoading) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Mood, null, modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))
                        Text("No sticker packs found",
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isBlank()) {
                        item {
                            Text("Featured Packs",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                    items(displayList, key = { it.packId }) { pack ->
                        StickerPackTile(pack = pack, onInstall = { onInstall(pack.packId) },
                            onClick = { onPackClick(pack.packId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerPackTile(
    pack: StickerPack,
    onInstall: () -> Unit,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (pack.cover != null) {
                        Text(pack.cover.take(2), style = MaterialTheme.typography.headlineSmall)
                    } else {
                        Icon(Icons.Default.Mood, null, modifier = Modifier.size(28.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(pack.title, style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${pack.stickerCount} stickers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (pack.author != null) {
                        Spacer(Modifier.width(8.dp))
                        Text("by ${pack.author}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (pack.isInstalled) {
                Text("Installed", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            } else {
                FilledTonalButton(onClick = onInstall) {
                    Text("Install")
                }
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
}
