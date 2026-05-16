package org.enchant.auth.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TwoStepPinScreen(
    onPinCreated: (String) -> Unit,
    isLoading: Boolean = false
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(0) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                if (step == 0) "Create a PIN" else "Confirm your PIN",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "This helps protect your account",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                if (step == 0) pin.padEnd(6, '·') else confirmPin.padEnd(6, '·'),
                style = MaterialTheme.typography.displayMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in 0..2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0..2) {
                            val digit = row * 3 + col + 1
                            FilledTonalButton(
                                onClick = {
                                    if (step == 0 && pin.length < 6) {
                                        pin += digit.toString()
                                        if (pin.length == 6) {
                                            step = 1
                                        }
                                    } else if (step == 1 && confirmPin.length < 6) {
                                        confirmPin += digit.toString()
                                        if (confirmPin.length == 6) {
                                            if (pin == confirmPin) {
                                                onPinCreated(pin)
                                            } else {
                                                confirmPin = ""
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(72.dp)
                            ) {
                                Text(digit.toString(), style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilledTonalButton(
                        onClick = { },
                        modifier = Modifier.size(72.dp)
                    ) { }
                    FilledTonalButton(
                        onClick = { /* digit 0 */ },
                        modifier = Modifier.size(72.dp)
                    ) {
                        Text("0", style = MaterialTheme.typography.headlineMedium)
                    }
                    FilledTonalButton(
                        onClick = {
                            if (step == 0 && pin.isNotEmpty()) {
                                pin = pin.dropLast(1)
                            } else if (step == 1 && confirmPin.isNotEmpty()) {
                                confirmPin = confirmPin.dropLast(1)
                            }
                        },
                        modifier = Modifier.size(72.dp)
                    ) {
                        Text("<", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }

            if (step == 1 && pin != confirmPin && confirmPin.length < 6) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("PINs don't match", color = MaterialTheme.colorScheme.error)
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        }
    }
}
