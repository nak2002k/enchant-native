package org.enchant.auth.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun UsernamePickerScreen(
    onUsernameEntered: (String) -> Unit,
    onSkip: () -> Unit,
    onCheckAvailability: suspend (String) -> Boolean
) {
    var username by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var isAvailable by remember { mutableStateOf<Boolean?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val statusText = when {
        isChecking -> "Checking availability..."
        isAvailable == true -> "Available!"
        isAvailable == false -> "Username taken"
        else -> ""
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text("Choose your username", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "This is your unique @handle",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { newValue ->
                    val cleaned = newValue.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
                    if (cleaned.length <= 32) {
                        username = cleaned
                        searchJob?.cancel()
                        if (cleaned.length >= 3) {
                            isChecking = true
                            isAvailable = null
                            searchJob = scope.launch {
                                delay(300)
                                val available = onCheckAvailability(cleaned)
                                isAvailable = available
                                isChecking = false
                            }
                        } else {
                            isAvailable = null
                            isChecking = false
                        }
                    }
                },
                label = { Text("@username") },
                placeholder = { Text("john_doe") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    if (statusText.isNotEmpty()) {
                        Text(
                            statusText,
                            color = when {
                                isAvailable == true -> MaterialTheme.colorScheme.primary
                                isAvailable == false -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onUsernameEntered(username) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = username.length in 3..32 && isAvailable == true
            ) {
                Text("Continue")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onSkip) {
                Text("Skip")
            }
        }
    }
}
