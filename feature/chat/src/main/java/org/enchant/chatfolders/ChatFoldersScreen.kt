package org.enchant.chatfolders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val BrandPrimaryLight = Color(0xFF3A0D6E)
private val BrandPrimaryDark = Color(0xFFB388E3)
private val BrandTintLight = Color(0xFF7B1FA2)

@Composable
private fun brandPrimary(): Color = if (isSystemInDarkTheme()) BrandPrimaryDark else BrandPrimaryLight

@Composable
private fun brandTint(): Color = BrandTintLight

data class ChatFolderItem(
    val folderId: String,
    val name: String,
    val conversationCount: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatFoldersScreen(
    folders: List<ChatFolderItem>,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onBack: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Folders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (folders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = RoundedCornerShape(24.dp), color = brandTint().copy(alpha = 0.12f)) {
                        Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(44.dp), tint = brandPrimary())
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("No chat folders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Create folders to organize your chats", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = brandPrimary(),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("New folder", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCreateDialog = true },
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = CircleShape, color = brandPrimary()) {
                                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("New folder", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = brandPrimary())
                        }
                    }
                }

                items(folders, key = { it.folderId }) { folder ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = brandTint().copy(alpha = 0.14f)
                            ) {
                                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Folder, null, tint = brandPrimary(), modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(folder.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(
                                    if (folder.conversationCount == 1) "1 chat" else "${folder.conversationCount} chats",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeleteFolder(folder.folderId) }) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        onCreateFolder(newFolderName)
                        newFolderName = ""
                        showCreateDialog = false
                    }
                }) { Text("Create", color = brandPrimary()) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; newFolderName = "" }) { Text("Cancel") }
            }
        )
    }
}
