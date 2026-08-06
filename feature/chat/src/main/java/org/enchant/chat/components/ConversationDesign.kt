package org.enchant.chat.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * Local mirror of the app DesignSystem tokens (org.enchant.ui.theme).
 * The shared DesignSystem lives in :app which the chat feature cannot
 * depend on (dependency cycle), so the exact same tokens are mirrored
 * here to keep the Signal-grade, Apple-felt look pixel-identical.
 */

// ─── Brand ───
object EnchantBrand {
    val SignalBlue = Color(0xFF3A0D6E)          // Signal's primary
    val iOSBlue = Color(0xFF3A0D6E)             // Apple accent
    val GroupGreen = Color(0xFF6A9C2F)
    val CallGreen = Color(0xFF34C759)
    val Red = Color(0xFFFF3B30)
    val UnreadBlue = Color(0xFF3A0D6E)
}

// ─── Spacing ───
object EnchantSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

// ─── Radii ───
object EnchantRadii {
    val small = 8.dp
    val medium = 12.dp
    val card = 14.dp
    val bubble = 18.dp
    val sheet = 18.dp
    val pill = 999.dp
}

// ─── Motion ───
object EnchantMotion {
    val spring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val springBouncy = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}

// ─── Group member name colors (muted, per-sender) ───
internal val SenderNamePalette = listOf(
    Color(0xFFE91E63), Color(0xFFB388E3), Color(0xFF3F51B5), Color(0xFF009688),
    Color(0xFF795548), Color(0xFF607D8B), Color(0xFF8BC34A), Color(0xFFFF5722),
)

internal fun senderNameColor(senderId: String): Color {
    val hash = senderId.hashCode().let { if (it == Int.MIN_VALUE) 0 else abs(it) }
    return SenderNamePalette[hash % SenderNamePalette.size]
}

// ─── Avatar ───
@Composable
internal fun EnchantAvatar(
    text: String?,
    size: Dp = 44.dp,
    online: Boolean = false,
    background: Color? = null,
    textColor: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    val bg = background ?: MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text?.take(2)?.uppercase() ?: "?",
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.38f).sp,
            maxLines = 1,
        )
        if (online) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size.value * 0.30f).dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.White)
                    .padding(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(0xFF34C759)),
                )
            }
        }
    }
}

// ─── Date chip (day separators) ───
@Composable
internal fun DateChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(EnchantRadii.pill))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .padding(horizontal = EnchantSpacing.md, vertical = EnchantSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Read receipt double-check (Canvas drawn) ───
@Composable
internal fun ReadReceiptIcon(
    read: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(15.dp)) {
        val stroke = 1.6.dp.toPx()
        drawLine(
            color = tint,
            start = Offset(0f, size.height * 0.55f),
            end = Offset(size.width * 0.32f, size.height * 0.9f),
            strokeWidth = stroke,
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.32f, size.height * 0.9f),
            end = Offset(size.width * 0.72f, size.height * 0.28f),
            strokeWidth = stroke,
        )
        if (read) {
            drawLine(
                color = tint,
                start = Offset(size.width * 0.36f, size.height * 0.55f),
                end = Offset(size.width * 0.62f, size.height * 0.9f),
                strokeWidth = stroke,
            )
            drawLine(
                color = tint,
                start = Offset(size.width * 0.62f, size.height * 0.9f),
                end = Offset(size.width * 1.06f, size.height * 0.28f),
                strokeWidth = stroke,
            )
        }
    }
}

// ─── Received-side typing indicator bubble ───
@Composable
internal fun TypingBubble(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "typingBubble")
    Surface(
        shape = RoundedCornerShape(
            topStart = 18.dp, topEnd = 18.dp,
            bottomStart = 4.dp, bottomEnd = 18.dp,
        ),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0..2) {
                val alpha by transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 500, delayMillis = i * 150),
                    ),
                    label = "dot$i",
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .clip(RoundedCornerShape(EnchantRadii.pill))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)),
                )
            }
        }
    }
}
