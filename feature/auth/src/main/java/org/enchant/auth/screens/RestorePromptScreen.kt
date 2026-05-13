package org.enchant.auth.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RestorePromptScreen(
    hasBackup: Boolean,
    onRestore: () -> Unit,
    onStartFresh: () -> Unit,
    isLoading: Boolean = false
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                if (hasBackup) "Restore from backup?" else "Start fresh",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (hasBackup) "We found a previous backup. Would you like to restore it?"
                else "No backup found. You'll start with a clean account.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else if (hasBackup) {
                Button(
                    onClick = onRestore,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Restore")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onStartFresh,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Start fresh")
                }
            } else {
                Button(
                    onClick = onStartFresh,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Continue")
                }
            }
        }
    }
}
