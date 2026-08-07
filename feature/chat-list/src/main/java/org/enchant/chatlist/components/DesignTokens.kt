package org.enchant.chatlist.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Local mirror of the app DesignSystem tokens (org.enchant.ui.theme).
 * The shared DesignSystem lives in :app which chat-list cannot depend on
 * (dependency cycle), so the exact same tokens are mirrored here to keep
 * the Signal-grade, Apple-felt look pixel-identical.
 */

// ─── Brand ───
object EnchantBrand {
    val SignalBlue = Color(0xFF3A0D6E)          // Signal's primary
    val iOSBlue = Color(0xFF3A0D6E)             // Apple accent
    val GroupGreen = Color(0xFF6A9C2F)
    val CallGreen = Color(0xFF34C759)
    val Red = Color(0xFFFF3B30)
    val UnreadBlue = Color(0xFFCE4A40)
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

// ─── Avatar ───
@Composable
fun EnchantAvatar(
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
            .clip(CircleShape)
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
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color(0xFF34C759))
                )
            }
        }
    }
}

// ─── Group avatar (stack of two) ───
@Composable
fun EnchantGroupAvatar(
    members: List<String>,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(size)) {
        if (members.isEmpty()) {
            EnchantAvatar(text = "?", size = size, background = EnchantBrand.GroupGreen)
        } else if (members.size == 1) {
            EnchantAvatar(text = members[0], size = size)
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(size * 0.68f)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(1.dp)
            ) {
                EnchantAvatar(text = members[0], size = size * 0.68f, background = EnchantBrand.GroupGreen)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.68f)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(1.dp)
            ) {
                EnchantAvatar(text = members[1], size = size * 0.68f, background = EnchantBrand.SignalBlue)
            }
        }
    }
}

// ─── Unread badge ───
@Composable
fun UnreadBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(EnchantRadii.pill))
            .background(EnchantBrand.UnreadBlue)
            .widthIn(min = 20.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 999) "999+" else count.toString(),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

// ─── FAB with press spring ───
@Composable
fun EnchantFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    containerColor: Color = EnchantBrand.SignalBlue,
    contentColor: Color = Color.White,
    size: Dp = 56.dp,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = EnchantMotion.springBouncy,
        label = "fabScale",
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                pressed = true
                onClick()
                pressed = false
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(size * 0.42f))
    }
}

// ─── Empty state ───
@Composable
fun EnchantEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(EnchantSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(EnchantSpacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(EnchantSpacing.sm))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
