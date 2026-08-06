package org.enchant.groups.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.enchant.groups.data.GroupMember
import org.enchant.groups.data.MemberRole
import org.enchant.ui.icons.EnchantIcons

private val BrandPrimaryLight = Color(0xFF3A0D6E)
private val BrandPrimaryDark = Color(0xFFB388E3)
private val BrandTintLight = Color(0xFF7B1FA2)

@Composable
private fun brandPrimary(): Color = if (isSystemInDarkTheme()) BrandPrimaryDark else BrandPrimaryLight

@Composable
private fun brandTint(): Color = BrandTintLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMemberListScreen(
    members: List<GroupMember>,
    isAdmin: Boolean,
    onAddMember: () -> Unit,
    onRemoveMember: (String) -> Unit,
    onUpdateRole: (String, String) -> Unit
) {
    var showRemoveDialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Members", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = { }) {
                        Icon(EnchantIcons.arrowLeft, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (members.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No members", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Invite people to join this group",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isAdmin) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onAddMember),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = CircleShape, color = brandPrimary()) {
                                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                    Icon(EnchantIcons.plusCircle, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Add members", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = brandPrimary())
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }

            items(members, key = { it.userId }) { member ->
                var showMenu by remember { mutableStateOf(false) }

                Surface(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = brandTint().copy(alpha = 0.12f)) {
                            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    (member.displayName ?: member.userId).take(2).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = brandPrimary()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(member.displayName ?: member.username ?: member.userId, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val (icon, tint) = when (member.role) {
                                    MemberRole.OWNER -> EnchantIcons.star to brandPrimary()
                                    MemberRole.ADMIN -> EnchantIcons.shieldCheck to brandTint()
                                    else -> null to null
                                }
                                if (icon != null && tint != null) {
                                    Icon(icon, member.role.value, tint = tint, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    member.role.value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isAdmin && member.role != MemberRole.OWNER) {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(EnchantIcons.ellipsisVertical, "Member options", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }

                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                if (member.role == MemberRole.MEMBER) {
                                    DropdownMenuItem(
                                        text = { Text("Promote to admin") },
                                        onClick = { onUpdateRole(member.userId, MemberRole.ADMIN.value); showMenu = false },
                                        leadingIcon = { Icon(EnchantIcons.shieldCheck, null, tint = brandPrimary()) }
                                    )
                                }
                                if (member.role == MemberRole.ADMIN) {
                                    DropdownMenuItem(
                                        text = { Text("Demote to member") },
                                        onClick = { onUpdateRole(member.userId, MemberRole.MEMBER.value); showMenu = false },
                                        leadingIcon = { Icon(EnchantIcons.user, null, tint = brandPrimary()) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Remove from group", color = MaterialTheme.colorScheme.error) },
                                    onClick = { showRemoveDialog = member.userId; showMenu = false },
                                    leadingIcon = { Icon(Icons.Default.PersonRemove, null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 76.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }

    showRemoveDialog?.let { userId ->
        val name = members.find { it.userId == userId }?.displayName ?: userId
        AlertDialog(
            onDismissRequest = { showRemoveDialog = null },
            title = { Text("Remove member") },
            text = { Text("Remove $name from the group?") },
            confirmButton = {
                TextButton(onClick = { onRemoveMember(userId); showRemoveDialog = null }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
