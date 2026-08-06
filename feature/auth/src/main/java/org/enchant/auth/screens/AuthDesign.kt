package org.enchant.auth.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

val BrandBlue = Color(0xFF3A0D6E)
val iOSBlue = Color(0xFF3A0D6E)
val CallGreen = Color(0xFF34C759)
val Red = Color(0xFFFF3B30)
val Gray = Color(0xFF8E8E93)

object FeatureSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

object FeatureRadii {
    val card = 12.dp
    val sheet = 18.dp
    val pill = 999.dp
}

fun featureSpring(): SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

fun formatCountdown(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
fun FeatureTitle(
    text: String,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Center
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp
        ),
        textAlign = align,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
fun FeatureSubtitle(
    text: String,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Center
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = align,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun FeatureBackButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = featureSpring(),
        label = "backPress"
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.ChevronLeft,
            contentDescription = "Back",
            tint = BrandBlue,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
fun EnchantPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    containerColor: Color = BrandBlue
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = featureSpring(),
        label = "primaryPress"
    )
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(FeatureRadii.pill))
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.3f))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
fun FeatureTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = BrandBlue,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = featureSpring(),
        label = "textPress"
    )
    Text(
        text = text,
        modifier = modifier
            .padding(horizontal = FeatureSpacing.lg, vertical = FeatureSpacing.sm)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        color = color
    )
}

@Composable
fun PinDots(
    count: Int,
    filled: Int,
    modifier: Modifier = Modifier,
    errorTick: Int = 0,
    showError: Boolean = false
) {
    val shake = remember { Animatable(0f) }
    val errorAlpha = remember { Animatable(0f) }
    LaunchedEffect(errorTick) {
        if (errorTick == 0) return@LaunchedEffect
        errorAlpha.snapTo(1f)
        shake.snapTo(0f)
        shake.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 420
                -8f at 40
                8f at 90
                -5f at 150
                5f at 210
                0f at 280
            }
        )
        errorAlpha.animateTo(0f, animationSpec = tween(durationMillis = 280))
    }
    val dotColor = lerp(BrandBlue, Red, errorAlpha.value)
    val emptyColor = when {
        showError -> Red.copy(alpha = 0.6f)
        else -> BrandBlue.copy(alpha = 0.35f)
    }
    Row(
        modifier = modifier.offset { IntOffset(shake.value.roundToInt(), 0) },
        horizontalArrangement = Arrangement.spacedBy(FeatureSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val isFilled = i < filled
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .then(
                        if (isFilled) Modifier.background(dotColor)
                        else Modifier.border(2.dp, emptyColor, CircleShape)
                    )
            )
        }
    }
}

@Composable
fun KeypadKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = featureSpring(),
        label = "keypadKey"
    )
    Box(
        modifier = modifier
            .size(76.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun PinKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onEmptyClick: () -> Unit = {}
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in 0..2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (col in 0..2) {
                    val digit = row * 3 + col + 1
                    KeypadKey(
                        onClick = { onDigit(digit.toString()) },
                        content = {
                            Text(
                                digit.toString(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            KeypadKey(onClick = onEmptyClick, content = {})
            KeypadKey(
                onClick = { onDigit("0") },
                content = {
                    Text(
                        "0",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            )
            KeypadKey(
                onClick = onBackspace,
                content = {
                    Text(
                        "\u232B",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            )
        }
    }
}
