package org.enchant.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val packageInfo = try {
        context.packageManager.getPackageInfo(context.packageName, 0)
    } catch (_: Exception) { null }

    SettingsScaffold(title = "About", onBack = onNavigateBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(EnchantSpacing.xxxl))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(EnchantRadii.card))
                    .background(EnchantBrand.SignalBlue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.ChatBubble,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(EnchantSpacing.lg))
            Text(
                text = "Enchant",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Version ${packageInfo?.versionName ?: "1.0.0"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(EnchantSpacing.xxl))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EnchantSpacing.lg),
            ) {
                EnchantSectionHeader("Security")
                EnchantGroupedCard {
                    Column(modifier = Modifier.padding(EnchantSpacing.lg)) {
                        Text(
                            text = "End-to-End Encrypted Messenger",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(EnchantSpacing.sm))
                        Text(
                            text = "Messages are secured with X3DH key agreement " +
                                "and Double Ratchet encryption. " +
                                "Your private keys never leave your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(EnchantSpacing.xxl))
            Text(
                text = "© 2026 Enchant Messaging",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(EnchantSpacing.lg))
        }
    }
}
