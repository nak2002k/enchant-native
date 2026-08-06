package org.enchant.settings.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.enchant.core.network.ApiClient

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

    SettingsScaffold(title = "Blocked Users", onBack = onNavigateBack) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                blockedUsers.isEmpty() -> {
                    EnchantEmptyState(
                        icon = Icons.Rounded.Block,
                        title = "No blocked users",
                        subtitle = "Users you block won't be able to message or call you.",
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = EnchantSpacing.lg,
                            end = EnchantSpacing.lg,
                            top = EnchantSpacing.sm,
                            bottom = EnchantSpacing.xxxl,
                        ),
                    ) {
                        item { EnchantSectionHeader("Blocked") }
                        item {
                            EnchantGroupedCard {
                                blockedUsers.forEachIndexed { index, user ->
                                    if (index > 0) EnchantDivider(inset = 56.dp)
                                    BlockedRow(
                                        user = user,
                                        onUnblock = {
                                            scope.launch {
                                                client.del("/v1/blocks/${user.userId}")
                                                blockedUsers = blockedUsers.filter { it.userId != user.userId }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedRow(
    user: BlockedUser,
    onUnblock: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EnchantAvatar(
            text = user.userId.take(2).uppercase().ifBlank { "?" },
            size = 44.dp,
        )
        Spacer(Modifier.width(EnchantSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = user.userId,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = "Blocked ${user.blockedTs}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        TextButton(onClick = onUnblock) {
            Text("Unblock", color = MaterialTheme.colorScheme.error)
        }
    }
}

private data class BlockedUser(val userId: String, val blockedTs: String)
