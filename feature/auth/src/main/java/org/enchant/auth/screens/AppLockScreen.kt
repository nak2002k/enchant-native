package org.enchant.auth.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.enchant.core.base.AppConfig
import org.enchant.core.base.SecurePreferences
import java.security.MessageDigest

private const val PIN_LENGTH = 6

private enum class AppLockStep { Create, Confirm, Verify }

@Composable
fun AppLockScreen(
    onVerified: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val alreadyEnabled = SecurePreferences.getBoolean("applock.enabled", false)
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(if (alreadyEnabled) AppLockStep.Verify else AppLockStep.Create) }
    var error by remember { mutableStateOf<String?>(null) }

    val biometricManager = remember {
        BiometricManager.from(context)
    }

    val canAuthenticateWithBiometric = remember {
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    fun verifyPin(pin: String): Boolean {
        val hash = SecurePreferences.getString("applock.pin_hash") ?: return false
        return hash == sha256(pin)
    }

    fun authenticateWithBiometric() {
        if (context !is FragmentActivity) return
        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                SecurePreferences.putBoolean("applock.biometric", true)
                SecurePreferences.putBoolean("applock.enabled", true)
                onVerified()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                error = errString.toString()
            }
            override fun onAuthenticationFailed() {
                error = "Biometric not recognized"
            }
        }
        val prompt = BiometricPrompt(context as FragmentActivity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Enchant")
            .setSubtitle("Verify your identity")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(promptInfo)
    }

    fun handleDigit(digit: String) {
        error = null
        when (step) {
            AppLockStep.Create -> {
                if (pin.length < PIN_LENGTH) {
                    pin += digit
                    if (pin.length == PIN_LENGTH) step = AppLockStep.Confirm
                }
            }
            AppLockStep.Confirm -> {
                if (confirmPin.length < PIN_LENGTH) {
                    confirmPin += digit
                    if (confirmPin.length == PIN_LENGTH) {
                        if (pin == confirmPin) {
                            SecurePreferences.putString("applock.pin_hash", sha256(pin))
                            SecurePreferences.putBoolean("applock.enabled", true)
                            onVerified()
                        } else {
                            error = "PINs don\u2019t match"
                            confirmPin = ""
                        }
                    }
                }
            }
            AppLockStep.Verify -> {
                if (pin.length < PIN_LENGTH) {
                    pin += digit
                    if (pin.length == PIN_LENGTH) {
                        if (verifyPin(pin)) {
                            onVerified()
                        } else {
                            error = "Wrong PIN"
                            pin = ""
                        }
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        when (step) {
            AppLockStep.Create -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
            AppLockStep.Confirm -> if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
            AppLockStep.Verify -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
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
            Text("App Lock", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                when (step) {
                    AppLockStep.Verify -> "Enter your PIN or use biometric"
                    else -> "Secure your chats with a PIN"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                when (step) {
                    AppLockStep.Create -> pin.padEnd(PIN_LENGTH, '\u00B7')
                    AppLockStep.Confirm -> confirmPin.padEnd(PIN_LENGTH, '\u00B7')
                    AppLockStep.Verify -> pin.padEnd(PIN_LENGTH, '\u00B7')
                },
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
                                onClick = { handleDigit(digit.toString()) },
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
                    FilledTonalButton(
                        onClick = { handleDigit("0") },
                        modifier = Modifier.size(72.dp)
                    ) { Text("0", style = MaterialTheme.typography.headlineMedium) }
                    FilledTonalButton(
                        onClick = { handleBackspace() },
                        modifier = Modifier.size(72.dp)
                    ) { Text("<", style = MaterialTheme.typography.headlineMedium) }
                }
            }

            if (canAuthenticateWithBiometric && step == AppLockStep.Verify) {
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(onClick = { authenticateWithBiometric() }) {
                    Text("Use biometric")
                }
            }

            if (step == AppLockStep.Verify && alreadyEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = {
                    pin = ""
                    step = AppLockStep.Create
                }) {
                    Text("Change PIN")
                }
            }
        }
    }
}
