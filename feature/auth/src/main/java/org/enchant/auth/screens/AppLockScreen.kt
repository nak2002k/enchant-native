package org.enchant.auth.screens

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppLockScreen(
    onPinSet: (String) -> Unit,
    onBiometricAuthenticate: () -> Unit,
    isBiometricAvailable: Boolean,
    isLoading: Boolean = false
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text("App Lock", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Secure your chats with a PIN",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                if (step == 0) pin.padEnd(6, '·') else confirmPin.padEnd(6, '·'),
                style = MaterialTheme.typography.displayMedium
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                for (row in 0..2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0..2) {
                            val digit = row * 3 + col + 1
                            FilledTonalButton(
                                onClick = {
                                    error = null
                                    if (step == 0 && pin.length < 6) {
                                        pin += digit.toString()
                                        if (pin.length == 6) { step = 1 }
                                    } else if (step == 1 && confirmPin.length < 6) {
                                        confirmPin += digit.toString()
                                        if (confirmPin.length == 6) {
                                            if (pin == confirmPin) { onPinSet(pin) }
                                            else { error = "PINs don't match"; confirmPin = "" }
                                        }
                                    }
                                },
                                modifier = Modifier.size(72.dp)
                            ) { Text(digit.toString(), style = MaterialTheme.typography.headlineMedium) }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilledTonalButton(onClick = { }, modifier = Modifier.size(72.dp)) { }
                    FilledTonalButton(onClick = {
                        if (step == 0 && pin.length < 6) { pin += "0"; if (pin.length == 6) step = 1 }
                        else if (step == 1 && confirmPin.length < 6) { confirmPin += "0"; if (confirmPin.length == 6) {
                            if (pin == confirmPin) onPinSet(pin) else { error = "PINs don't match"; confirmPin = "" }
                        }}
                    }, modifier = Modifier.size(72.dp)) { Text("0", style = MaterialTheme.typography.headlineMedium) }
                    FilledTonalButton(onClick = {
                        if (step == 0 && pin.isNotEmpty()) pin = pin.dropLast(1)
                        else if (step == 1 && confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                    }, modifier = Modifier.size(72.dp)) { Text("<", style = MaterialTheme.typography.headlineMedium) }
                }
            }

            if (isBiometricAvailable) {
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(onClick = onBiometricAuthenticate) {
                    Text("Use biometric")
                }
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        }
    }
}
