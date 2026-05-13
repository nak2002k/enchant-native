package org.enchant.auth.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun PermissionsScreen(
    onPermissionsGranted: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Text("Permissions", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Enchant needs some permissions to work properly",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionCard(
                    title = "Notifications",
                    description = "Get notified when you receive messages"
                )
            }

            PermissionCard(
                title = "Microphone",
                description = "Send voice messages and make calls"
            )

            PermissionCard(
                title = "Camera",
                description = "Take photos and make video calls"
            )

            PermissionCard(
                title = "Contacts",
                description = "Find your friends who use Enchant"
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                "You can change these later in Settings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onPermissionsGranted,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Continue")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onSkip) {
                Text("Not now")
            }
        }
    }
}

@Composable
private fun PermissionCard(title: String, description: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
