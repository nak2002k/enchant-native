package org.enchant.calls

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.security.MessageDigest
import java.util.Arrays

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

    fun verify(remote: String, local: String): Boolean {
        return MessageDigest.isEqual(remote.encodeToByteArray(), local.encodeToByteArray())
    }
}

@Composable
fun SafetyNumberDialog(
    safetyNumber: String,
    onDismiss: () -> Unit,
    onVerify: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Shield, null) },
        title = { Text("Safety Number") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, null, modifier = Modifier.size(32.dp).padding(bottom = 8.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Verify this call is end-to-end encrypted by comparing the safety number below with the other participant.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    safetyNumber.chunked(4).joinToString("  "),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "If the numbers match, your call is secure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onVerify(); onDismiss() }) {
                Text("Verified")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
