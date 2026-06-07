package org.enchant.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
fun MentionHighlightText(
    text: String,
    mentions: List<Triple<String, Int, Int>> = emptyList(),
    onMentionClick: (String) -> Unit = {},
    fontSize: TextUnit = 14.sp,
    color: Color = Color.Unspecified
) {
    if (mentions.isEmpty()) {
        Text(text = text, fontSize = fontSize, color = color)
        return
    }

    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        mentions.sortedBy { it.second }.forEach { (userId, start, length) ->
            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }
            val mentionText = text.substring(start, (start + length).coerceAtMost(text.length))
            pushStringAnnotation(tag = "MENTION", annotation = userId)
            withStyle(style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )) {
                append(mentionText)
            }
            pop()
            lastIndex = start + length
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }

    Text(
        text = annotatedString,
        fontSize = fontSize,
        color = color
    )
}
