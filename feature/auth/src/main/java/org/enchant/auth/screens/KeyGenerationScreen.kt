package org.enchant.auth.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun KeyGenerationScreen(
    onKeysGenerated: () -> Unit,
    onRetry: () -> Unit,
    progress: Float = 0f,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    LaunchedEffect(Unit) {
        if (progress >= 1f && !isError) {
            onKeysGenerated()
        }
    }

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
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(BrandBlue),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(40.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    strokeWidth = 3.5.dp
                )
            }
            Spacer(modifier = Modifier.height(FeatureSpacing.xxxl))
            Text(
                text = if (isError) "Couldn't generate keys" else "Generating your keys\u2026",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(FeatureSpacing.sm))
            Text(
                text = if (isError) (errorMessage ?: "Something went wrong") else "This takes a few seconds",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = if (isError) Red else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isError) {
                Spacer(modifier = Modifier.height(FeatureSpacing.xxl))
                EnchantPrimaryButton(
                    text = "Retry",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
