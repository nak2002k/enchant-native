package org.enchant.contacts.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import kotlinx.serialization.json.jsonObject
import org.enchant.core.network.ApiClient

data class FriendRequest(
    val id: String,
    val userId: String,
    val username: String,
    val createdAt: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendRequestsScreen(onNavigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val client = remember { ApiClient() }
    var incoming by remember { mutableStateOf<List<FriendRequest>>(emptyList()) }
    var outgoing by remember { mutableStateOf<List<FriendRequest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Incoming", "Outgoing")

    LaunchedEffect(Unit) {
        client.init()
        val response = client.get("/v1/contacts/requests")
        response.fold(
            onSuccess = { json ->
                incoming = parseRequests(json, "incoming")
                outgoing = parseRequests(json, "outgoing")
                isLoading = false
            },
            onFailure = { isLoading = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friend Requests") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title)
                                if (index == 0 && incoming.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge { Text(incoming.size.toString()) }
                                }
                            }
                        }
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val currentList = if (selectedTab == 0) incoming else outgoing

                if (currentList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                if (selectedTab == 0) "No incoming requests"
                                else "No outgoing requests",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(currentList, key = { it.id }) { request ->
                            Surface(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                request.username.take(2).uppercase(),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(request.username, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            request.createdAt,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (selectedTab == 0) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            FilledTonalButton(
                                                onClick = {
                                                    scope.launch {
                                                        val body = buildJsonObject {
                                                            put("approve", JsonPrimitive(true))
                                                        }
                                                        client.put("/v1/contacts/requests/${request.id}", body)
                                                        incoming = incoming.filter { it.id != request.id }
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Accept")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        val body = buildJsonObject {
                                                            put("approve", JsonPrimitive(false))
                                                        }
                                                        client.put("/v1/contacts/requests/${request.id}", body)
                                                        incoming = incoming.filter { it.id != request.id }
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Text("Decline")
                                            }
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    client.del("/v1/contacts/requests/${request.id}")
                                                    outgoing = outgoing.filter { it.id != request.id }
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Text("Cancel")
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun parseRequests(json: JsonElement, key: String): List<FriendRequest> {
    val arr = json.jsonObject[key] as? JsonArray ?: return emptyList()
    return arr.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        FriendRequest(
            id = obj["id"]?.jsonPrimitive?.content ?: "",
            userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
            username = obj["username"]?.jsonPrimitive?.content ?: "",
            createdAt = obj["created_at"]?.jsonPrimitive?.content ?: ""
        )
    }
}
