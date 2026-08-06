package org.enchant.auth.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.phone.SmsRetriever
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.regex.Pattern

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
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var countdown by remember { mutableStateOf(30) }
    var userEditedCode by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val smsReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
                    try {
                        val extras = intent.extras
                        val status = extras?.get(SmsRetriever.EXTRA_STATUS) as? com.google.android.gms.common.api.Status
                        if (status?.isSuccess == true) {
                            val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE) ?: return
                            val matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(message)
                            if (matcher.find()) {
                                val otp = matcher.group(1)
                                if (code.length < 6 && !userEditedCode) {
                                    code = otp
                                    onCodeSubmitted(otp)
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }
            }
        }
        try {
            val client = SmsRetriever.getClient(context)
            client.startSmsRetriever()
            context.registerReceiver(smsReceiver, IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION))
        } catch (e: Exception) {
            android.util.Log.w("OtpVerify", "SMS retriever not available: ${e.message}")
        }
        onDispose {
            try { context.unregisterReceiver(smsReceiver) } catch (_: Exception) { }
        }
    }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeatureSpacing.xxl)
        ) {
            Spacer(modifier = Modifier.height(FeatureSpacing.lg))
            FeatureBackButton(onClick = onWrongNumber)
            Spacer(modifier = Modifier.height(FeatureSpacing.xxxl))
            FeatureTitle(text = "Enter your code")
            Spacer(modifier = Modifier.height(FeatureSpacing.sm))
            FeatureSubtitle(text = "Enter the code sent to $identifier")
            Spacer(modifier = Modifier.height(FeatureSpacing.xxxl))

            OutlinedTextField(
                value = code,
                onValueChange = { newValue: String ->
                    if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                        code = newValue
                        userEditedCode = true
                        if (newValue.length == 6) {
                            onCodeSubmitted(newValue)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = errorMessage != null,
                supportingText = if (errorMessage != null) {
                    { Text(errorMessage, color = Red) }
                } else if (remainingAttempts != null) {
                    { Text("$remainingAttempts attempts remaining") }
                } else {
                    null
                },
                placeholder = {
                    Text(
                        text = "000 000",
                        fontSize = 22.sp,
                        color = Gray,
                        textAlign = TextAlign.Center
                    )
                },
                textStyle = TextStyle(
                    fontSize = 28.sp,
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(FeatureRadii.card),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    cursorColor = BrandBlue
                )
            )

            Spacer(modifier = Modifier.height(FeatureSpacing.xxl))

            if (isLoading) {
                CircularProgressIndicator(
                    color = BrandBlue,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(FeatureSpacing.lg))
            }

            if (countdown > 0) {
                Text(
                    text = "Resend code in ${formatCountdown(countdown)}",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                FeatureTextButton(
                    text = "Resend",
                    onClick = {
                        onResendCode()
                        countdown = 60
                        scope.launch {
                            while (countdown > 0) {
                                delay(1000)
                                countdown--
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                onClick = onWrongNumber,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "Wrong number?",
                    color = Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(FeatureSpacing.lg))
        }
    }
}
