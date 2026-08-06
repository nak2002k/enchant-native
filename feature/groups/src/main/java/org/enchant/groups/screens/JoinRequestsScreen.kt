package org.enchant.groups.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.enchant.core.network.ApiClient
import org.enchant.groups.data.JoinRequest
import org.enchant.ui.icons.EnchantIcons

private val BrandPrimaryLight = Color(0xFF3A0D6E)
private val BrandPrimaryDark = Color(0xFFB388E3)
private val BrandTintLight = Color(0xFF7B1FA2)
private val BrandRed = Color(0xFFFF3B30)

@Composable
private fun brandPrimary(): Color = if (isSystemInDarkTheme()) BrandPrimaryDark else BrandPrimaryLight

@Composable
private fun brandTint(): Color = BrandTintLight

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
                title = {
                    Text("Join Requests", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(EnchantIcons.arrowLeft, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = brandPrimary())
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
                        EnchantIcons.alertCircle,
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
                        EnchantIcons.search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = brandPrimary().copy(alpha = 0.35f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No pending requests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = CircleShape, color = brandTint().copy(alpha = 0.12f)) {
                        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            Text(
                                (request.username ?: request.requesterUserId).take(2).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = brandPrimary()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(request.username ?: request.requesterUserId, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        if (!request.requestedTs.isNullOrBlank()) {
                            Text(
                                "Requested ${request.requestedTs}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = brandPrimary(),
                        modifier = Modifier.clickable {
                            scope.launch {
                                client.put("/v1/groups/join-requests/${request.requestId}",
                                    buildJsonObject { put("approve", JsonPrimitive(true)) })
                                requests = requests.filter { it.requestId != request.requestId }
                            }
                        }
                    ) {
                        Text(
                            "Approve",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed.copy(alpha = 0.6f)),
                        modifier = Modifier.clickable {
                            scope.launch {
                                client.put("/v1/groups/join-requests/${request.requestId}",
                                    buildJsonObject { put("approve", JsonPrimitive(false)) })
                                requests = requests.filter { it.requestId != request.requestId }
                            }
                        }
                    ) {
                        Text(
                            "Decline",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandRed,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}
