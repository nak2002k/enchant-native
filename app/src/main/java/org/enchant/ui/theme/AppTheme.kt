package org.enchant.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.enchant.core.store.EnchantStore

// ─── Premium Color Palette ───

@Immutable
data class EnchantColors(
    val brand: Color,
    val brandLight: Color,
    val brandDark: Color,
    val chatBubbleSent: Color,
    val chatBubbleReceived: Color,
    val online: Color,
    val unread: Color,
    val danger: Color,
    val warning: Color,
    val success: Color,
    val muted: Color,
    val divider: Color,
    val cardElevated: Color,
    val shimmer: Color,
)

val LocalEnchantColors = staticCompositionLocalOf {
    EnchantColors(
        brand = Color.Unspecified,
        brandLight = Color.Unspecified,
        brandDark = Color.Unspecified,
        chatBubbleSent = Color.Unspecified,
        chatBubbleReceived = Color.Unspecified,
        online = Color.Unspecified,
        unread = Color.Unspecified,
        danger = Color.Unspecified,
        warning = Color.Unspecified,
        success = Color.Unspecified,
        muted = Color.Unspecified,
        divider = Color.Unspecified,
        cardElevated = Color.Unspecified,
        shimmer = Color.Unspecified,
    )
}

// ─── Light Mode (iOS-like) ───

private val EnchantLightColors = EnchantColors(
    brand = Color(0xFF007AFF),
    brandLight = Color(0xFF5AC8FA),
    brandDark = Color(0xFF0051D5),
    chatBubbleSent = Color(0xFF007AFF),
    chatBubbleReceived = Color(0xFFE9E9EB),
    online = Color(0xFF34C759),
    unread = Color(0xFFFF3B30),
    danger = Color(0xFFFF3B30),
    warning = Color(0xFFFF9500),
    success = Color(0xFF34C759),
    muted = Color(0xFF8E8E93),
    divider = Color(0xFFC6C6C8),
    cardElevated = Color(0xFFF2F2F7),
    shimmer = Color(0xFFE5E5EA),
)

private val EnchantLightScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF007AFF).copy(alpha = 0.12f),
    onPrimaryContainer = Color(0xFF007AFF),
    secondary = Color(0xFF5856D6),
    onSecondary = Color.White,
    tertiary = Color(0xFFFF9500),
    onTertiary = Color.White,
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFE5E5EA),
    error = Color(0xFFFF3B30),
    onError = Color.White,
    errorContainer = Color(0xFFFF3B30).copy(alpha = 0.12f),
    onErrorContainer = Color(0xFFFF3B30),
    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color(0xFFF2F2F7),
    surfaceTint = Color(0xFF007AFF),
)

// ─── Dark Mode (iOS-like) ───

private val EnchantDarkColors = EnchantColors(
    brand = Color(0xFF0A84FF),
    brandLight = Color(0xFF64D2FF),
    brandDark = Color(0xFF0A84FF),
    chatBubbleSent = Color(0xFF0A84FF),
    chatBubbleReceived = Color(0xFF2C2C2E),
    online = Color(0xFF30D158),
    unread = Color(0xFFFF453A),
    danger = Color(0xFFFF453A),
    warning = Color(0xFFFFD60A),
    success = Color(0xFF30D158),
    muted = Color(0xFF8E8E93),
    divider = Color(0xFF38383A),
    cardElevated = Color(0xFF2C2C2E),
    shimmer = Color(0xFF38383A),
)

private val EnchantDarkScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0A84FF).copy(alpha = 0.2f),
    onPrimaryContainer = Color(0xFF0A84FF),
    secondary = Color(0xFF5E5CE6),
    onSecondary = Color.Black,
    tertiary = Color(0xFFFFD60A),
    onTertiary = Color.Black,
    background = Color(0xFF000000),
    onBackground = Color(0xFFE5E5E7),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFE5E5E7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF38383A),
    error = Color(0xFFFF453A),
    onError = Color.Black,
    errorContainer = Color(0xFFFF453A).copy(alpha = 0.2f),
    onErrorContainer = Color(0xFFFF453A),
    inverseSurface = Color(0xFFE5E5E7),
    inverseOnSurface = Color(0xFF1C1C1E),
    surfaceTint = Color(0xFF0A84FF),
)

// ─── Typography ───

private val EnchantTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.1).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.4).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.2).sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.1).sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.1).sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp,
    ),
)

// ─── Animation Specs ───

object EnchantAnimation {
    val spring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )
    val snappy = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    val gentle = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessLow,
    )
    val colorTransition = animateColorAsState(
        targetValue = Color.Unspecified,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        )
    )
}

// ─── Theme Entry Point ───

@Composable
fun NotionTheme(
    content: @Composable () -> Unit
) {
    val theme = try {
        AppThemeManager.currentTheme()
    } catch (e: Exception) {
        "system"
    }
    val isDark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) EnchantDarkScheme else EnchantLightScheme
    val enchantColors = if (isDark) EnchantDarkColors else EnchantLightColors

    CompositionLocalProvider(LocalEnchantColors provides enchantColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EnchantTypography,
            content = content
        )
    }
}

object AppThemeManager {

    fun currentTheme(): String = EnchantStore.settings.theme ?: "system"

    fun setTheme(theme: String) {
        EnchantStore.settings.theme = theme
    }

    fun loadTheme() {
    }
}
