package org.enchant.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Enchant Settings design system — Signal-grade, Apple-felt.
 * Mirrors the shared DesignSystem tokens/components so settings screens
 * render iOS grouped tables that look identical to the rest of the app.
 */

// ─── Brand ───
object EnchantBrand {
    val SignalBlue = Color(0xFF3A76F0)          // Signal's primary
    val iOSBlue = Color(0xFF007AFF)             // Apple accent
    val GroupGreen = Color(0xFF6A9C2F)
    val CallGreen = Color(0xFF34C759)
    val Red = Color(0xFFFF3B30)
    val UnreadBlue = Color(0xFF2F6FED)
}

// ─── Settings icon tile tints ───
object SettingsIconTints {
    val Teal = Color(0xFF30B0C7)
    val Purple = Color(0xFFAF52DE)
    val Orange = Color(0xFFFF9500)
    val Pink = Color(0xFFFF2D55)
    val DarkGray = Color(0xFF6E6E73)
    val Gray = Color(0xFF8E8E93)
    val Brown = Color(0xFFA2845E)
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

// ─── Settings row (iOS grouped style) ───
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconBackground: Color = EnchantBrand.iOSBlue,
    iconTint: Color = Color.White,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) {
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else {
        modifier
    }
    Row(
        modifier = clickModifier
            .padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(EnchantSpacing.lg))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(EnchantSpacing.sm))
            trailing()
        } else if (onClick != null) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ─── Settings switch row (iOS grouped toggle) ───
@Composable
fun SignalSettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (label != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(EnchantSpacing.sm))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = EnchantBrand.SignalBlue,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

// ─── Inset divider (iOS grouped) ───
@Composable
fun EnchantDivider(
    inset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(start = inset)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

// ─── Section header (iOS grouped) ───
@Composable
fun EnchantSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title.uppercase(),
        modifier = modifier.padding(
            start = EnchantSpacing.lg,
            end = EnchantSpacing.lg,
            top = EnchantSpacing.xl,
            bottom = EnchantSpacing.sm,
        ),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.5.sp,
    )
}

// ─── Grouped card container (iOS grouped cells) ───
@Composable
fun EnchantGroupedCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(EnchantRadii.medium))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        content()
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
            )
        }
    }
}

// ─── iOS title bar: back chevron + titleLarge semibold title ───
@Composable
fun SettingsTitleBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = EnchantSpacing.xs, end = EnchantSpacing.lg, top = EnchantSpacing.xs, bottom = EnchantSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Rounded.ArrowBackIosNew,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ─── Full-screen settings scaffold: grouped background + title bar ───
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SettingsTitleBar(title = title, onBack = onBack)
        content()
    }
}

// ─── Large profile header card (68dp avatar, name, handle, edit pencil) ───
@Composable
fun SettingsProfileHeader(
    displayName: String,
    username: String?,
    about: String?,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EnchantGroupedCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onEditClick,
                )
                .padding(EnchantSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EnchantAvatar(
                text = displayName.take(2).uppercase().ifBlank { "?" },
                size = 68.dp,
                background = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(EnchantSpacing.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    text = displayName.ifBlank { "User" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (username != null) {
                    Text(
                        text = username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (about != null) {
                    Spacer(Modifier.height(EnchantSpacing.xs))
                    Text(
                        text = about,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(EnchantSpacing.sm))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onEditClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "Edit profile",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
