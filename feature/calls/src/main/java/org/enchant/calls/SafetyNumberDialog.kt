package org.enchant.calls

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.view.WindowManager
import org.enchant.core.crypto.CryptoPrimitives
import org.enchant.ui.icons.EnchantIcons

private val JewelPurpleLight = Color(0xFF3A0D6E)
private val JewelPurpleDark = Color(0xFFB388E3)
private val VerifiedGreen = Color(0xFF34C759)

@Composable
private fun brandPurple(): Color = if (isSystemInDarkTheme()) JewelPurpleDark else JewelPurpleLight

object SafetyNumberHelper {
    fun computeFingerprint(ourKey: ByteArray, theirKey: ByteArray): String {
        val out = ByteArray(32)
        val outLen = longArrayOf(32)
        val rc = org.enchant.core.crypto.EnchantCrypto.enchant_safety_number_generate(
            ourKey, theirKey, "", "", out, outLen
        )
        if (rc != 0) {
            val combined = ourKey + theirKey
            val hash = CryptoPrimitives.sha512(combined)
            return formatFingerprint(hash.copyOfRange(0, 32))
        }
        return formatFingerprint(out.copyOf(outLen[0].toInt()))
    }

    fun formatFingerprint(digest: ByteArray): String {
        return digest.joinToString("") { String.format("%02X", it) }
            .chunked(4).joinToString("-")
    }

    fun formatSafetyNumberRows(fingerprint: String): List<String> {
        return fingerprint.replace("-", "")
            .chunked(12)
            .map { it.chunked(4).joinToString(" ") }
    }

    fun verify(remote: String, local: String): Boolean {
        val normalizedRemote = remote.replace("-", "").replace(" ", "").uppercase()
        val normalizedLocal = local.replace("-", "").replace(" ", "").uppercase()
        return CryptoPrimitives.constantTimeEquals(
            normalizedRemote.encodeToByteArray(),
            normalizedLocal.encodeToByteArray()
        )
    }
}

@Composable
fun SafetyNumberDialog(
    safetyNumber: String,
    onDismiss: () -> Unit,
    onVerify: () -> Unit,
    isVerified: Boolean = false
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        (context as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            (context as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    val digitsRows = remember(safetyNumber) {
        safetyNumber.replace("-", "").replace(" ", "").uppercase()
            .chunked(3).chunked(4)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        icon = {
            Icon(
                if (isVerified) EnchantIcons.checkCircle else EnchantIcons.lock,
                null,
                tint = if (isVerified) VerifiedGreen else brandPurple()
            )
        },
        title = {
            Text(
                if (isVerified) "Verified" else "Safety number",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Verify this conversation is end-to-end encrypted by comparing the safety number with the other participant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        digitsRows.forEach { row ->
                            Text(
                                row.joinToString(" "),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 2.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            EnchantIcons.qrCode,
                            null,
                            modifier = Modifier.size(24.dp),
                            tint = brandPurple()
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Scan this code",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "To verify on another device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    "If the numbers are identical, you are chatting with the right person and your messages are secure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            if (!isVerified) {
                Button(
                    onClick = onVerify,
                    shape = CircleShape,
                    modifier = Modifier.height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandPurple(),
                        contentColor = Color.White
                    )
                ) {
                    Text("Verify", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isVerified) "Close" else "Dismiss") }
        }
    )
}
