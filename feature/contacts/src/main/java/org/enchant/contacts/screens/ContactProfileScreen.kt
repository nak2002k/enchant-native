package org.enchant.contacts.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class UserProfile(
    val displayName: String = "",
    val username: String? = null,
    val about: String? = null,
    val isBlocked: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactProfileScreen(
    userId: String,
    profile: UserProfile? = null,
    isLoading: Boolean = false,
    onMessage: () -> Unit,
    onCall: () -> Unit,
    onVideoCall: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val displayName = profile?.displayName?.takeIf { it.isNotBlank() } ?: "User"
    val username = profile?.username
    val about = profile?.about
    val isBlocked = profile?.isBlocked ?: false

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        displayName.take(2).uppercase(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            if (username != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "@$username",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (about != null && about.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    about,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(Icons.Default.Chat, "Message", onMessage)
                ActionButton(Icons.Default.Phone, "Call", onCall)
                ActionButton(Icons.Default.Videocam, "Video", onVideoCall)
            }

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { if (isBlocked) onUnblock() else onBlock() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isBlocked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    if (isBlocked) Icons.Default.Block else Icons.Default.Block,
                    null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isBlocked) "Unblock" else "Block")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick = onClick) {
            Icon(icon, label, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
