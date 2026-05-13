package org.enchant.auth.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ProfileSetupScreen(
    onProfileDataEntered: (displayName: String, about: String?, avatarUri: Uri?) -> Unit,
    isLoading: Boolean = false
) {
    var displayName by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text("Create your profile", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { if (it.length <= 64) displayName = it },
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("${displayName.length}/64") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = about,
                onValueChange = { if (it.length <= 139) about = it },
                label = { Text("About (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                supportingText = { Text("${about.length}/139") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { onProfileDataEntered(displayName, about.ifBlank { null }, null) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = displayName.isNotBlank()
                ) {
                    Text("Continue")
                }
            }
        }
    }
}
