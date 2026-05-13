package org.enchant.auth.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(
    onTermsAccepted: () -> Unit,
    onRestore: () -> Unit
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
                text = "Enchant",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Private, end-to-end encrypted messaging",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(64.dp))
            Button(
                onClick = onTermsAccepted,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Agree & Continue")
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onRestore) {
                Text("Restore or Transfer Account")
            }
        }
    }
}
