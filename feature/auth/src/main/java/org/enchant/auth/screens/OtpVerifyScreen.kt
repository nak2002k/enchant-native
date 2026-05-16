package org.enchant.auth.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OtpVerifyScreen(
    identifier: String,
    onCodeSubmitted: (String) -> Unit,
    onResendCode: () -> Unit,
    onWrongNumber: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    remainingAttempts: Int? = null
) {
    var code by remember { mutableStateOf("") }
    var countdown by remember { mutableStateOf(30) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text("Verify your number", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Enter the code sent to $identifier",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { newValue: String ->
                    if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                        code = newValue
                        if (newValue.length == 6) {
                            onCodeSubmitted(newValue)
                        }
                    }
                },
                label = { Text("Verification code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = errorMessage != null,
                supportingText = if (errorMessage != null) {
                    { Text(errorMessage) }
                } else if (remainingAttempts != null) {
                    { Text("$remainingAttempts attempts remaining") }
                } else {
                    null
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                CircularProgressIndicator()
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (countdown > 0) {
                Text("Resend in ${countdown}s")
            } else {
                Button(onClick = {
                    onResendCode()
                    countdown = 60
                    scope.launch {
                        while (countdown > 0) {
                            delay(1000)
                            countdown--
                        }
                    }
                }) {
                    Text("Resend Code")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(onClick = onWrongNumber) {
                Text("Wrong number?")
            }
        }
    }
}
