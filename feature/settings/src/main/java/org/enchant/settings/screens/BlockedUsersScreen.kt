package org.enchant.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.enchant.core.network.ApiClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(onNavigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val client = remember { ApiClient.getInstance() }
    var blockedUsers by remember { mutableStateOf<List<BlockedUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val response = client.get("/v1/blocks")
        response.fold(
            onSuccess = { json ->
                val arr = json["blocked_users"]?.let {
                    (it as? kotlinx.serialization.json.JsonArray)
                }
                if (arr != null) {
                    blockedUsers = arr.mapNotNull { entry ->
                        val obj = entry as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                        BlockedUser(
                            userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                            blockedTs = obj["blocked_ts"]?.jsonPrimitive?.content ?: ""
                        )
                    }
                }
                isLoading = false
            },
            onFailure = { isLoading = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked Users") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                blockedUsers.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No blocked users", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                else -> {
                    LazyColumn {
                        items(blockedUsers) { user ->
                            ListItem(
                                headlineContent = { Text(user.userId) },
                                supportingContent = { Text("Blocked ${user.blockedTs}") },
                                leadingContent = { Icon(Icons.Default.Block, contentDescription = null) },
                                trailingContent = {
                                    TextButton(onClick = {
                                        scope.launch {
                                            client.del("/v1/blocks/${user.userId}")
                                            blockedUsers = blockedUsers.filter { it.userId != user.userId }
                                        }
                                    }) { Text("Unblock") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class BlockedUser(val userId: String, val blockedTs: String)
