package org.enchant.auth.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun PermissionsScreen(
    onPermissionsGranted: () -> Unit,
    onSkip: () -> Unit,
    registerAgentActions: ((grantPermissions: () -> Unit, notNow: () -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    var permissionsGranted by remember {
        mutableStateOf(
            REQUIRED_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        if (permissionsGranted) {
            onPermissionsGranted()
        }
    }

    val allGranted = remember(permissionsGranted) {
        REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    val grantPermissions: () -> Unit = {
        if (allGranted) onPermissionsGranted() else permissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    SideEffect {
        registerAgentActions?.invoke(grantPermissions, onSkip)
    }

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

            if (allGranted) {
                Button(
                    onClick = onPermissionsGranted,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Continue")
                }
            } else {
                Button(
                    onClick = grantPermissions,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Grant Permissions")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onSkip) {
                Text("Not now")
            }
        }
    }
}

private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.READ_CONTACTS
    )
} else {
    arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.READ_CONTACTS
    )
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
