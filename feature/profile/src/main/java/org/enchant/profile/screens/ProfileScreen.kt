package org.enchant.profile.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.enchant.profile.ProfileData

// Local mirror of the contacts design tokens (feature modules cannot import
// org.enchant.ui.theme from :app, and profile cannot depend on contacts).
private val BrandBlue = Color(0xFF3A76F0)
private val CallGreen = Color(0xFF34C759)
private val Red = Color(0xFFFF3B30)
private val GroupGreen = Color(0xFF6A9C2F)

private val SpacingXs = 4.dp
private val SpacingSm = 8.dp
private val SpacingMd = 12.dp
private val SpacingLg = 16.dp
private val SpacingXxl = 24.dp
private val SpacingXxxl = 32.dp

@Composable
private fun InitialAvatar(
    text: String?,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    bg: Color = BrandBlue,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text?.take(2)?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.38f).sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun GroupedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SpacingLg)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        content()
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(
            start = SpacingLg,
            end = SpacingLg,
            top = SpacingXxl,
            bottom = SpacingSm,
        ),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    icon: ImageVector? = null,
    iconBg: Color? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    titleColor: Color? = null,
    showDivider: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = SpacingLg, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBg ?: MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(SpacingMd))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor ?: MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
        if (onClick != null) {
            Spacer(Modifier.width(SpacingXs))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
    if (showDivider) {
        HorizontalDivider(modifier = Modifier.padding(start = 62.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String,
    isOwnProfile: Boolean,
    profile: ProfileData?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMessage: () -> Unit,
    onCall: () -> Unit,
    onBlock: () -> Unit,
    isBlocked: Boolean
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Profile",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (isOwnProfile) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, "Edit")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(SpacingXxl))

            InitialAvatar(
                text = profile?.displayName ?: userId,
                size = 96.dp,
                bg = BrandBlue,
                modifier = if (isOwnProfile) Modifier.clickable { onEdit() } else Modifier
            )

            if (isOwnProfile) {
                Spacer(modifier = Modifier.height(SpacingSm))
                Text(
                    "Tap to change",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(SpacingLg))

            Text(
                profile?.displayName ?: "User",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = SpacingXxxl)
            )

            if (profile?.username != null) {
                Spacer(modifier = Modifier.height(SpacingXs))
                Text(
                    "@${profile.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            if (profile?.about != null) {
                Spacer(modifier = Modifier.height(SpacingMd))
                Text(
                    profile.about,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = SpacingXxxl)
                )
            }

            Spacer(modifier = Modifier.height(SpacingXxl))

            if (isOwnProfile && profile != null) {
                SectionHeader("Profile")
                GroupedCard {
                    SettingsRow(
                        title = "Edit name",
                        icon = Icons.Default.Person,
                        iconBg = BrandBlue,
                        onClick = onEdit
                    )
                    SettingsRow(
                        title = "About",
                        icon = Icons.Default.Info,
                        iconBg = CallGreen,
                        onClick = onEdit
                    )
                    SettingsRow(
                        title = "Username",
                        icon = Icons.Default.AlternateEmail,
                        iconBg = GroupGreen,
                        onClick = onEdit
                    )
                    SettingsRow(
                        title = "Avatar",
                        icon = Icons.Default.Photo,
                        iconBg = BrandBlue,
                        onClick = onEdit,
                        showDivider = false
                    )
                }
            } else {
                SectionHeader("Actions")
                GroupedCard {
                    SettingsRow(
                        title = "Message",
                        icon = Icons.Default.Chat,
                        iconBg = BrandBlue,
                        onClick = onMessage
                    )
                    SettingsRow(
                        title = "Call",
                        icon = Icons.Default.Phone,
                        iconBg = CallGreen,
                        onClick = onCall
                    )
                    SettingsRow(
                        title = if (isBlocked) "Unblock" else "Block User",
                        icon = Icons.Default.Block,
                        iconBg = Red,
                        titleColor = Red,
                        onClick = onBlock,
                        showDivider = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(SpacingXxxl))
        }
    }
}
