package org.enchant.groups.screens

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
import org.enchant.core.network.ApiClient
import org.enchant.groups.data.JoinRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinRequestsScreen(
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val client = remember {
        val c = ApiClient()
        c.init()
        c
    }
    var requests by remember { mutableStateOf<List<JoinRequest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val response = client.get("/v1/groups/join-requests")
        response.fold(
            onSuccess = { json ->
                val arr = json["requests"]?.jsonArray ?: return@fold
                requests = arr.mapNotNull { entry ->
                    val obj = entry.jsonObject
                    JoinRequest(
                        requestId = obj["request_id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        requesterUserId = obj["requester_user_id"]?.jsonPrimitive?.content ?: "",
                        username = obj["username"]?.jsonPrimitive?.content,
                        status = obj["status"]?.jsonPrimitive?.content ?: "",
                        requestedTs = obj["requested_ts"]?.jsonPrimitive?.content
                    )
                }
            },
            onFailure = { error = it.message ?: "Failed to load join requests" }
        )
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join Requests") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Error loading requests", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        if (requests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PersonSearch,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No pending requests", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Join requests will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(requests, key = { it.requestId }) { request ->
                Surface(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    (request.username ?: request.requesterUserId).take(2).uppercase(),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(request.username ?: request.requesterUserId, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Requested ${request.requestedTs ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        client.put("/v1/groups/join-requests/${request.requestId}",
                                            buildJsonObject { put("approve", JsonPrimitive(true)) })
                                        requests = requests.filter { it.requestId != request.requestId }
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    "Approve",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        client.put("/v1/groups/join-requests/${request.requestId}",
                                            buildJsonObject { put("approve", JsonPrimitive(false)) })
                                        requests = requests.filter { it.requestId != request.requestId }
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    "Reject",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}
