package org.enchant.auth.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.enchant.ui.icons.EnchantIcons

@Composable
fun RestorePromptScreen(
    hasBackup: Boolean,
    onRestore: () -> Unit,
    onStartFresh: () -> Unit,
    isLoading: Boolean = false
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeatureSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    EnchantIcons.rotateCcw,
                    contentDescription = null,
                    tint = BrandBlue,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(FeatureSpacing.xxxl))
            FeatureTitle(
                text = if (hasBackup) "Restore from backup?" else "Start fresh"
            )
            Spacer(modifier = Modifier.height(FeatureSpacing.sm))
            FeatureSubtitle(
                text = if (hasBackup) "We found a previous backup. Would you like to restore it?"
                else "No backup found. You'll start with a clean account."
            )
            Spacer(modifier = Modifier.height(FeatureSpacing.xxxl))

            if (isLoading) {
                CircularProgressIndicator(color = BrandBlue)
            } else if (hasBackup) {
                EnchantPrimaryButton(
                    text = "Restore",
                    onClick = onRestore,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(FeatureSpacing.sm))
                FeatureTextButton(
                    text = "Start fresh",
                    onClick = onStartFresh
                )
            } else {
                EnchantPrimaryButton(
                    text = "Continue",
                    onClick = onStartFresh,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
