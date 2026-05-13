package org.enchant.auth.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun KeyGenerationScreen(
    onKeysGenerated: () -> Unit,
    onRetry: () -> Unit,
    progress: Float = 0f,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    LaunchedEffect(Unit) {
        if (progress >= 1f && !isError) {
            onKeysGenerated()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Setting up your keys", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(32.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                when {
                    isError -> errorMessage ?: "Something went wrong"
                    progress < 0.2f -> "Generating identity keys..."
                    progress < 0.4f -> "Creating signed pre-key..."
                    progress < 0.6f -> "Generating one-time pre-keys..."
                    progress < 0.8f -> "Uploading to server..."
                    progress < 1f -> "Setting up local storage..."
                    else -> "Complete!"
                },
                style = MaterialTheme.typography.bodyMedium
            )

            if (isError) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}
