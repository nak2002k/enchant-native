package org.enchant.chatlist.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.enchant.chat.data.ConversationFilter
import org.enchant.chatlist.components.EnchantBrand
import org.enchant.chatlist.components.EnchantRadii
import org.enchant.chatlist.components.EnchantSpacing

@Composable
fun FilterPillsRow(
    currentFilter: ConversationFilter,
    onFilterSelected: (ConversationFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = EnchantSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(EnchantSpacing.sm)
    ) {
        items(ConversationFilter.entries) { filter ->
            FilterPill(
                label = filter.label(),
                selected = currentFilter == filter,
                onClick = { onFilterSelected(filter) }
            )
        }
    }
}

private fun ConversationFilter.label(): String = when (this) {
    ConversationFilter.ALL -> "All"
    ConversationFilter.UNREAD -> "Unread"
    ConversationFilter.GROUPS -> "Groups"
    ConversationFilter.PERSONAL -> "Personal"
    ConversationFilter.ARCHIVED -> "Archived"
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(EnchantRadii.pill)
    val containerColor by animateColorAsState(
        targetValue = if (selected) EnchantBrand.SignalBlue else MaterialTheme.colorScheme.surface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillContainer"
    )
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .clip(shape)
            .background(containerColor)
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
