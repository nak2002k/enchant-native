package org.enchant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.enchant.core.store.EnchantStore

object NotionColors {
    val Blue = Color(0xFF3B82F6)
    val Indigo = Color(0xFF6366F1)
    val Green = Color(0xFF22C55E)
    val Red = Color(0xFFEF4444)
    val Orange = Color(0xFFF59E0B)

    val LightBg = Color(0xFFFFFFFF)
    val LightCard = Color(0xFFF7F7F7)
    val LightBorder = Color(0xFFE9E9E9)
    val LightText = Color(0xFF37352F)
    val LightTextSecondary = Color(0xFF9B9A97)

    val DarkBg = Color(0xFF191919)
    val DarkCard = Color(0xFF2D2D2D)
    val DarkBorder = Color(0xFF3D3D3D)
    val DarkText = Color(0xFFFFFFFF).copy(alpha = 0.8f)
    val DarkTextSecondary = Color(0xFFFFFFFF).copy(alpha = 0.45f)
}

private val NotionLightColorScheme = lightColorScheme(
    primary = NotionColors.Blue,
    onPrimary = Color.White,
    primaryContainer = NotionColors.Blue.copy(alpha = 0.12f),
    onPrimaryContainer = NotionColors.Blue,
    secondary = NotionColors.Indigo,
    onSecondary = Color.White,
    background = NotionColors.LightBg,
    onBackground = NotionColors.LightText,
    surface = NotionColors.LightCard,
    onSurface = NotionColors.LightText,
    surfaceVariant = NotionColors.LightCard,
    onSurfaceVariant = NotionColors.LightTextSecondary,
    outline = NotionColors.LightBorder,
    error = NotionColors.Red,
    onError = Color.White
)

private val NotionDarkColorScheme = darkColorScheme(
    primary = NotionColors.Blue,
    onPrimary = Color.White,
    primaryContainer = NotionColors.Blue.copy(alpha = 0.2f),
    onPrimaryContainer = NotionColors.Blue.copy(alpha = 0.8f),
    secondary = NotionColors.Indigo,
    onSecondary = Color.White,
    background = NotionColors.DarkBg,
    onBackground = NotionColors.DarkText,
    surface = NotionColors.DarkCard,
    onSurface = NotionColors.DarkText,
    surfaceVariant = NotionColors.DarkCard,
    onSurfaceVariant = NotionColors.DarkTextSecondary,
    outline = NotionColors.DarkBorder,
    error = NotionColors.Red,
    onError = Color.White
)

object AppThemeManager {

    fun currentTheme(): String = EnchantStore.settings.theme ?: "system"

    fun setTheme(theme: String) {
        EnchantStore.settings.theme = theme
    }

    fun loadTheme() {
    }
}

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

    val colorScheme = if (isDark) NotionDarkColorScheme else NotionLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
