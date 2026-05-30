package org.enchant.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.view.WindowManager
import java.security.MessageDigest

object SafetyNumberHelper {
    fun computeFingerprint(ourKey: ByteArray, theirKey: ByteArray): String {
        val combined = ourKey + theirKey
        val hash = MessageDigest.getInstance("SHA-512").digest(combined)
        return formatFingerprint(hash)
    }

    fun formatFingerprint(digest: ByteArray): String {
        return digest.joinToString("") { String.format("%02X", it) }
            .chunked(4).joinToString("-")
    }

    fun formatSafetyNumberRows(fingerprint: String): List<String> {
        return fingerprint.replace("-", "")
            .chunked(12) // 3 groups of 4 = 12 chars per row
            .map { it.chunked(4).joinToString(" ") }
    }

    fun verify(remote: String, local: String): Boolean {
        return MessageDigest.isEqual(remote.encodeToByteArray(), local.encodeToByteArray())
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
    var showNumbers by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (isVerified) Icons.Default.CheckCircle else Icons.Default.Shield,
                null,
                tint = if (isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        },
        title = {
            Text(
                if (isVerified) "Verified" else "Safety Number",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Verify this conversation is end-to-end encrypted by comparing the safety number with ${
                        if (showNumbers) "\n\n$safetyNumber" else " the other participant."
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )

                if (showNumbers) {
                    Spacer(Modifier.height(16.dp))
                    val rows = SafetyNumberHelper.formatSafetyNumberRows(safetyNumber)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            rows.forEach { row ->
                                Text(
                                    row,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    ),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "If the numbers are identical, you are chatting with the right person and your messages are secure.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { showNumbers = true }) {
                        Icon(Icons.Default.Visibility, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Show safety number")
                    }
                }
            }
        },
        confirmButton = {
            if (!isVerified && showNumbers) {
                TextButton(onClick = { onVerify() }) {
                    Text("Mark as Verified")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isVerified) "Close" else "Not now") }
        }
    )
}
