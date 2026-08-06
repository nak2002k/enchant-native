package org.enchant.auth.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.enchant.core.base.SecurePreferences

private const val PIN_LENGTH = 6
private const val TAG = "AppLockScreen"

private enum class AppLockStep { Create, Confirm, Verify }

private fun triggerBiometricAuth(
    context: android.content.Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (context !is FragmentActivity) return
    val executor = ContextCompat.getMainExecutor(context)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onError(errString.toString())
        }
        override fun onAuthenticationFailed() {
            onError("Biometric not recognized")
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

@Composable
fun AppLockScreen(
    onVerified: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        (context as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            (context as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    val alreadyEnabled = SecurePreferences.getBoolean("applock.enabled", false)
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(if (alreadyEnabled) AppLockStep.Verify else AppLockStep.Create) }
    var error by remember { mutableStateOf<String?>(null) }
    var errorTick by remember { mutableStateOf(0) }

    val biometricManager = remember {
        BiometricManager.from(context)
    }

    val canAuthenticateWithBiometric = remember {
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    val useBiometricUnlock = SecurePreferences.getBoolean("applock.biometric", false)

    var biometricTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(step) {
        if (step == AppLockStep.Verify && canAuthenticateWithBiometric && useBiometricUnlock && !biometricTriggered) {
            biometricTriggered = true
            triggerBiometricAuth(
                context = context,
                onSuccess = {
                    SecurePreferences.putBoolean("applock.biometric", true)
                    SecurePreferences.putBoolean("applock.enabled", true)
                    onVerified()
                },
                onError = { errMsg -> error = errMsg }
            )
        }
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = SecurePreferences.getString("applock.pin_hash") ?: return false
        return try {
            if (isLegacySha256Hash(storedHash)) {
                val legacyHash = legacySha256Hash(pin)
                if (legacyHash == storedHash) {
                    val newHash = hashPinArgon2(pin)
                    SecurePreferences.putString("applock.pin_hash", newHash)
                    true
                } else {
                    false
                }
            } else {
                verifyPinArgon2(pin, storedHash)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "verifyPin failed", e)
            false
        }
    }

    fun authenticateWithBiometric() {
        triggerBiometricAuth(
            context = context,
            onSuccess = {
                SecurePreferences.putBoolean("applock.biometric", true)
                SecurePreferences.putBoolean("applock.enabled", true)
                onVerified()
            },
            onError = { errMsg -> error = errMsg }
        )
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
                            val hash = hashPinArgon2(pin)
                            SecurePreferences.putString("applock.pin_hash", hash)
                            SecurePreferences.putBoolean("applock.enabled", true)
                            onVerified()
                        } else {
                            error = "PINs don\u2019t match"
                            errorTick++
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
                            errorTick++
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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeatureSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(FeatureSpacing.xxl * 2))
            FeatureTitle(text = "App Lock")
            Spacer(modifier = Modifier.height(FeatureSpacing.sm))
            FeatureSubtitle(
                text = when (step) {
                    AppLockStep.Verify -> "Enter your PIN or use biometric"
                    AppLockStep.Confirm -> "Enter it again to confirm"
                    AppLockStep.Create -> "Secure your chats with a PIN"
                }
            )
            Spacer(modifier = Modifier.height(FeatureSpacing.xxxl))

            PinDots(
                count = PIN_LENGTH,
                filled = when (step) {
                    AppLockStep.Create -> pin.length
                    AppLockStep.Confirm -> confirmPin.length
                    AppLockStep.Verify -> pin.length
                },
                errorTick = errorTick,
                showError = error != null
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(FeatureSpacing.md))
                Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(FeatureSpacing.xxl))

            PinKeypad(
                onDigit = { handleDigit(it) },
                onBackspace = { handleBackspace() }
            )

            if (canAuthenticateWithBiometric && step == AppLockStep.Verify) {
                Spacer(modifier = Modifier.height(FeatureSpacing.lg))
                OutlinedButton(
                    onClick = { authenticateWithBiometric() },
                    shape = RoundedCornerShape(FeatureRadii.pill),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Text(
                        text = "Use biometric",
                        color = BrandBlue,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                }
            }

            if (step == AppLockStep.Verify && alreadyEnabled) {
                Spacer(modifier = Modifier.height(FeatureSpacing.sm))
                FeatureTextButton(
                    text = "Change PIN",
                    onClick = {
                        pin = ""
                        step = AppLockStep.Create
                    }
                )
            }
        }
    }
}
