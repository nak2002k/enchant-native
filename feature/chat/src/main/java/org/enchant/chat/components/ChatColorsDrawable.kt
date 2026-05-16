package org.enchant.chat.components

import androidx.compose.ui.graphics.Color
import org.enchant.core.base.SecurePreferences

sealed class ChatColor {
    data class Solid(val color: Color) : ChatColor()
    data class Gradient(val start: Color, val end: Color) : ChatColor()
    data object Default : ChatColor()
}

object ChatColorsDrawable {
    private val presetColors = listOf(
        Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF9800),
        Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF00BCD4),
        Color(0xFF795548), Color(0xFF607D8B), Color(0xFFF44336),
        Color(0xFF3F51B5)
    )

    private val colorCache = mutableMapOf<String, ChatColor>()

    fun getColor(conversationId: String): ChatColor {
        return colorCache[conversationId] ?: loadColor(conversationId)
    }

    fun getBubbleColor(isOutgoing: Boolean): Color {
        return if (isOutgoing) Color(0xFFE3F2FD) else Color(0xFFF5F5F5)
    }

    suspend fun setConversationColor(conversationId: String, color: ChatColor) {
        val encoded = when (color) {
            is ChatColor.Solid -> "solid:${color.color.value.toLong()}"
            is ChatColor.Gradient -> "gradient:${color.start.value.toLong()}:${color.end.value.toLong()}"
            is ChatColor.Default -> "default"
        }
        SecurePreferences.putString("chat_color_$conversationId", encoded)
        colorCache[conversationId] = color
    }

    fun getPresetColor(index: Int): Color {
        return presetColors[index % presetColors.size]
    }

    fun generateColor(conversationId: String): Color {
        val hash = conversationId.hashCode()
        val idx = (hash and Int.MAX_VALUE) % presetColors.size
        return presetColors[idx]
    }

    private fun loadColor(conversationId: String): ChatColor {
        val encoded = SecurePreferences.getString("chat_color_$conversationId")
        val color = if (encoded == null) {
            ChatColor.Solid(generateColor(conversationId))
        } else {
            val parts = encoded.split(":")
            when (parts[0]) {
                "solid" -> ChatColor.Solid(Color(parts.getOrNull(1)?.toLongOrNull() ?: 0L))
                "gradient" -> ChatColor.Gradient(
                    Color(parts.getOrNull(1)?.toLongOrNull() ?: 0L),
                    Color(parts.getOrNull(2)?.toLongOrNull() ?: 0L)
                )
                else -> ChatColor.Default
            }
        }
        colorCache[conversationId] = color
        return color
    }
}
