package org.enchant.profile.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.enchant.profile.ProfileData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String,
    isOwnProfile: Boolean,
    profile: ProfileData?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMessage: () -> Unit,
    onCall: () -> Unit,
    onBlock: () -> Unit,
    isBlocked: Boolean
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (isOwnProfile) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, "Edit")
                        }
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
                modifier = Modifier
                    .size(96.dp)
                    .then(
                        if (isOwnProfile) Modifier.clickable { onEdit() }
                        else Modifier
                    ),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        (profile?.displayName ?: userId).take(2).uppercase(),
                        style = MaterialTheme.typography.displaySmall
                    )
                }
            }

            if (isOwnProfile) {
                Text(
                    "Tap to change",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                profile?.displayName ?: "User",
                style = MaterialTheme.typography.headlineSmall
            )

            if (profile?.username != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "@${profile.username}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (profile?.about != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    profile.about,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (!isOwnProfile) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActionButton(Icons.Default.Chat, "Message", onMessage)
                    ActionButton(Icons.Default.Phone, "Call", onCall)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            if (!isOwnProfile) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onBlock,
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
            }

            if (isOwnProfile && profile != null) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = profile.displayName ?: "",
                    onValueChange = {},
                    label = { Text("Display Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    readOnly = true,
                    leadingIcon = { Icon(Icons.Default.Person, null) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = profile.about ?: "",
                    onValueChange = {},
                    label = { Text("About") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    readOnly = true,
                    leadingIcon = { Icon(Icons.Default.Info, null) },
                    maxLines = 3
                )
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
