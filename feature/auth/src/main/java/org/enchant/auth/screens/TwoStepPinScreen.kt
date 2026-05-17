package org.enchant.auth.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.enchant.core.base.SecurePreferences
import java.security.MessageDigest

private const val TAG = "TwoStepPinScreen"

object TwoStepPinScreen {

    @Composable
    fun Screen(
        onPinCreated: (String) -> Unit = {},
        isLoading: Boolean = false
    ) {
        var pin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var step by remember { mutableStateOf(0) }
        var error by remember { mutableStateOf<String?>(null) }

        fun sha256(input: String): String =
            MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

        fun handleDigit(digit: String) {
            if (step == 0 && pin.length < 6) {
                pin += digit
                if (pin.length == 6) step = 1
            } else if (step == 1 && confirmPin.length < 6) {
                confirmPin += digit
                if (confirmPin.length == 6) {
                    if (pin == confirmPin) {
                        try {
                            SecurePreferences.putString("twostep.pin_hash", sha256(pin))
                            SecurePreferences.putBoolean("twostep.enabled", true)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to store two-step PIN", e)
                        }
                        onPinCreated(pin)
                    } else {
                        error = "PINs don\u2019t match"
                        confirmPin = ""
                    }
                }
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
                    if (step == 0) pin.padEnd(6, '\u00B7') else confirmPin.padEnd(6, '\u00B7'),
                    style = MaterialTheme.typography.displayMedium
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }

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
                                    onClick = { handleDigit(digit.toString()) },
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
                            onClick = { handleDigit("0") },
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
                    Text("PINs don\u2019t match", color = MaterialTheme.colorScheme.error)
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
            }
        }
    }

    companion object {
        fun isPinSet(): Boolean = SecurePreferences.getBoolean("twostep.enabled", false)
        fun verifyPin(pin: String): Boolean {
            val hash = SecurePreferences.getString("twostep.pin_hash") ?: return false
            return try {
                hash == MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                Log.e(TAG, "verifyPin failed", e)
                false
            }
        }
    }
}
