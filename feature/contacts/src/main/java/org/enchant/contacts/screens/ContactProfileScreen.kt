package org.enchant.contacts.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.enchant.ui.icons.EnchantIcons

data class UserProfile(
    val displayName: String = "",
    val username: String? = null,
    val about: String? = null,
    val isBlocked: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactProfileScreen(
    userId: String,
    profile: UserProfile? = null,
    isLoading: Boolean = false,
    onMessage: () -> Unit,
    onCall: () -> Unit,
    onVideoCall: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val displayName = profile?.displayName?.takeIf { it.isNotBlank() } ?: "User"
    val username = profile?.username
    val about = profile?.about
    val isBlocked = profile?.isBlocked ?: false

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
                    IconButton(onClick = onNavigateBack) {
                        Icon(EnchantIcons.arrowLeft, "Back")
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
            Spacer(modifier = Modifier.height(24.dp))

            InitialAvatar(
                text = displayName,
                size = 96.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                displayName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            if (username != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "@$username",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            if (about != null && about.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    about,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(44.dp),
                shape = RoundedCornerShape(ContactsRadii.pill),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ContactsBrand.BrandBlue,
                    contentColor = Color.White
                )
            ) {
                Icon(EnchantIcons.messageCircle, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Message",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCall,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(ContactsRadii.pill),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        EnchantIcons.phone,
                        null,
                        tint = ContactsBrand.CallGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Call",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                OutlinedButton(
                    onClick = onVideoCall,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(ContactsRadii.pill),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        EnchantIcons.video,
                        null,
                        tint = ContactsBrand.CallGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Video Call",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader("Details")
            GroupedCard {
                SettingsRow(
                    title = "Safety Number",
                    icon = EnchantIcons.shieldCheck,
                    iconBg = ContactsBrand.BrandBlue
                )
                SettingsRow(
                    title = if (isBlocked) "Unblock" else "Block User",
                    icon = EnchantIcons.ban,
                    iconBg = ContactsBrand.Red,
                    titleColor = ContactsBrand.Red,
                    onClick = { if (isBlocked) onUnblock() else onBlock() },
                    showDivider = false
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
