package org.enchant.auth.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.view.WindowManager
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.enchant.core.base.SecurePreferences
import org.enchant.core.crypto.EnchantCrypto
import org.enchant.core.network.ApiClient
import java.security.SecureRandom

private const val TAG = "TwoStepPinScreen"
private const val ARGON2_HASH_LEN = 128

object TwoStepPinScreen {

    @Composable
    fun Screen(
        onPinCreated: (String) -> Unit = {},
        isLoading: Boolean = false
    ) {
        val context = LocalContext.current
        DisposableEffect(Unit) {
            (context as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose {
                (context as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        val scope = rememberCoroutineScope()
        var pin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var step by remember { mutableStateOf(0) }
        var error by remember { mutableStateOf<String?>(null) }

        fun handleDigit(digit: String) {
            if (step == 0 && pin.length < 6) {
                pin += digit
                if (pin.length == 6) step = 1
            } else if (step == 1 && confirmPin.length < 6) {
                confirmPin += digit
                if (confirmPin.length == 6) {
                    if (pin == confirmPin) {
                        try {
                            val hash = hashPinArgon2(pin)
                            SecurePreferences.putString("twostep.pin_hash", hash)
                            SecurePreferences.putBoolean("twostep.enabled", true)
                            scope.launch {
                                try {
                                    val client = ApiClient()
                                    client.init()
                                    client.put("/v1/auth/pin", buildJsonObject {
                                        put("pin", hash)
                                    })
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to register PIN with server: ${e.message}")
                                }
                            }
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

    object Helpers {
        fun isPinSet(): Boolean = SecurePreferences.getBoolean("twostep.enabled", false)

        fun verifyPin(pin: String): Boolean {
            val storedHash = SecurePreferences.getString("twostep.pin_hash") ?: return false
            return try {
                if (isLegacySha256Hash(storedHash)) {
                    val legacyHash = legacySha256Hash(pin)
                    if (legacyHash == storedHash) {
                        val newHash = hashPinArgon2(pin)
                        SecurePreferences.putString("twostep.pin_hash", newHash)
                        true
                    } else {
                        false
                    }
                } else {
                    verifyPinArgon2(pin, storedHash)
                }
            } catch (e: Exception) {
                Log.e(TAG, "verifyPin failed", e)
                false
            }
        }
    }
}

fun hashPinArgon2(pin: String): String {
    val output = ByteArray(ARGON2_HASH_LEN)
    val rc = EnchantCrypto.enchant_argon2id_hash(pin, pin.length, output, output.size)
    if (rc != 0) throw RuntimeException("enchant_argon2id_hash failed: $rc")
    return String(output, Charsets.US_ASCII)
}

fun verifyPinArgon2(pin: String, hash: String): Boolean {
    val rc = EnchantCrypto.enchant_argon2id_verify(hash, hash.length, pin, pin.length)
    return rc == 0
}

fun isLegacySha256Hash(hash: String): Boolean = hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' }

fun legacySha256Hash(pin: String): String {
    val hash = org.enchant.core.crypto.CryptoPrimitives.sha256(pin.toByteArray())
    return hash.joinToString("") { "%02x".format(it) }
}