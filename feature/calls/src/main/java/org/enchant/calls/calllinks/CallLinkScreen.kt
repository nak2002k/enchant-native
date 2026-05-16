package org.enchant.calls.calllinks

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.enchant.core.calls.CallLinkData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLinkScreen(
    callLink: CallLinkData?,
    isOwner: Boolean,
    isLoading: Boolean,
    error: String?,
    onJoinCall: () -> Unit,
    onEditName: (String) -> Unit,
    onDelete: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(callLink?.name ?: "Call Link") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator()
                return@Column
            }

            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error)
                return@Column
            }

            val link = callLink ?: return@Column

            Icon(Icons.Default.Link, null, modifier = Modifier.size(64.dp).padding(bottom = 16.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text(link.name, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("${link.restrictions.name.lowercase().replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (!link.isActive) {
                Spacer(Modifier.height(8.dp))
                Text("This link is no longer active", color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(32.dp))

            Button(onClick = onJoinCall, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = link.isActive) {
                Icon(Icons.Default.Call, null)
                Spacer(Modifier.width(8.dp))
                Text("Join Call")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(onClick = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Join my Enchant call: enchant://call-link/${link.roomId}")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Link"))
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(8.dp))
                Text("Share Link")
            }

            if (isOwner) {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text("Admin", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))

                OutlinedButton(onClick = { /* edit name dialog */ }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Edit Name")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Link")
                }
            }
        }
    }
}
