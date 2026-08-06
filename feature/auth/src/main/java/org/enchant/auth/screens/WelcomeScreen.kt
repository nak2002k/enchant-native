package org.enchant.auth.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.enchant.ui.icons.EnchantIcons

@Composable
fun WelcomeScreen(
    onTermsAccepted: () -> Unit,
    onRestore: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeatureSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(FeatureRadii.pill))
                    .background(BrandBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    EnchantIcons.bolt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.height(FeatureSpacing.xxxl))
            Text(
                text = "Enchant",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.4).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(FeatureSpacing.sm))
            Text(
                text = "Private, end-to-end encrypted messaging",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(FeatureSpacing.xxxl * 2))
            EnchantPrimaryButton(
                text = "Agree & Continue",
                onClick = onTermsAccepted,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(FeatureSpacing.md))
            FeatureTextButton(
                text = "Restore from backup",
                onClick = onRestore
            )
        }
    }
}
