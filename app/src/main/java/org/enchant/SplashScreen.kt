package org.enchant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.enchant.ui.icons.EnchantIcons

/**
 * Animated cold-start splash: a jewel-purple glowing mark + the Enchant
 * wordmark letters springing in, then the whole thing fades to reveal the app.
 */
@Composable
fun EnchantSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = Color(0xFF3A0D6E)
    val brandGlow = Color(0xFF7B1FA2)
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val bg = if (dark) Color(0xFF0E0A14) else Color(0xFF3A0D6E)

    var visible by remember { mutableStateOf(true) }
    val markScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.86f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "markScale",
    )
    val markAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(320),
        label = "markAlpha",
    )

    // Glow pulse behind the mark
    val infinite = rememberInfiniteTransition(label = "glow")
    val glowScale by infinite.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowScale",
    )
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowAlpha",
    )

    // Letter-by-letter wordmark
    val word = "Enchant"
    val letterProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(700),
        label = "letters",
    )

    LaunchedEffect(Unit) {
        delay(1800)
        visible = false
        delay(420)
        onFinished()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(400)),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Glow + mark
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .graphicsLayer {
                                scaleX = glowScale
                                scaleY = glowScale
                                this.alpha = glowAlpha
                            }
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(brandGlow, Color.Transparent)
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .graphicsLayer {
                                scaleX = markScale
                                scaleY = markScale
                                this.alpha = markAlpha
                            }
                            .clip(RoundedCornerShape(26.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(brandGlow, Color(0xFF5B21B6))
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            EnchantIcons.sparkles,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
                Spacer(Modifier.height(30.dp))

                // Wordmark letters
                Box {
                    word.forEachIndexed { i, ch ->
                        val delay = i * 90
                        val progress = ((letterProgress * 1000 - delay) / 90f).coerceIn(0f, 1f)
                        val scale = if (progress <= 0f) 0f else springOut(progress)
                        val alpha = if (progress <= 0f) 0f else 1f
                        Text(
                            text = ch.toString(),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Color.White,
                            modifier = Modifier
                                .offset(x = (i * 27).dp, y = 0.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "End-to-end encrypted messenger",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier
                        .graphicsLayer { this.alpha = markAlpha },
                )
            }
        }
    }
}

private fun springOut(progress: Float): Float {
    // Back-out easing: overshoot slightly past 1 then settle
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val p = progress - 1f
    return 1f + c3 * p * p * p + c1 * p * p
}
