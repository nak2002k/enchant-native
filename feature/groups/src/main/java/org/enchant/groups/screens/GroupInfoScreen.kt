package org.enchant.groups.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.enchant.groups.data.Group
import org.enchant.groups.data.GroupMember
import org.enchant.groups.data.MemberRole
import org.enchant.ui.icons.EnchantIcons

private val BrandPrimaryLight = Color(0xFF3A0D6E)
private val BrandPrimaryDark = Color(0xFFB388E3)
private val BrandTintLight = Color(0xFF7B1FA2)
private val BrandRed = Color(0xFFFF3B30)

@Composable
private fun brandPrimary(): Color = if (isSystemInDarkTheme()) BrandPrimaryDark else BrandPrimaryLight

@Composable
private fun brandTint(): Color = BrandTintLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    group: Group?,
    members: List<GroupMember>,
    joinRequests: Int,
    isLoading: Boolean,
    error: String?,
    inviteLink: String?,
    onNavigateBack: () -> Unit,
    onAddMembers: () -> Unit,
    onRemoveMember: (String) -> Unit,
    onUpdateRole: (String, String) -> Unit,
    onUpdateGroup: (name: String?, description: String?) -> Unit = { _, _ -> },
    onCreateInviteLink: () -> Unit,
    onCopyInviteLink: (String) -> Unit,
    onViewJoinRequests: () -> Unit,
    onLeaveGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    onRefresh: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var editDescription by remember(group?.description) { mutableStateOf(group?.description ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(EnchantIcons.arrowLeft, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        if (group == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (isLoading) CircularProgressIndicator()
                else Text("Group not found")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = brandTint().copy(alpha = 0.16f),
                        border = androidx.compose.foundation.BorderStroke(2.dp, brandPrimary().copy(alpha = 0.6f))
                    ) {
                        Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                            Text(
                                group.name.take(2).uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = brandPrimary()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        group.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${group.memberCount} members · ${group.myRole.value}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (group.description != null) {
                            Text(group.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        } else {
                            Text("Add description", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                        IconButton(onClick = { editDescription = group.description ?: ""; showEditDialog = true }) {
                            Icon(EnchantIcons.pencil, "Edit description", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionPill(EnchantIcons.userPlus, "Add members", onAddMembers, Modifier.weight(1f))
                    ActionPill(EnchantIcons.link, "Invite", { onCreateInviteLink() }, Modifier.weight(1f))
                    if (joinRequests > 0) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = brandPrimary()) { Text(joinRequests.toString()) }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            ActionPill(EnchantIcons.search, "Requests", onViewJoinRequests, Modifier.fillMaxWidth())
                        }
                    } else {
                        ActionPill(EnchantIcons.search, "Requests", onViewJoinRequests, Modifier.weight(1f))
                    }
                }
            }

            if (inviteLink != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = brandTint().copy(alpha = 0.10f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { onCopyInviteLink(inviteLink) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(EnchantIcons.link, null, modifier = Modifier.size(16.dp), tint = brandPrimary())
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Invite link: $inviteLink", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Icon(EnchantIcons.copy, "Copy", modifier = Modifier.size(16.dp), tint = brandPrimary())
                        }
                    }
                }
            }

            item {
                SectionHeader("Members (${members.size})")
            }

            items(members, key = { it.userId }) { member ->
                MemberRow(
                    member = member,
                    isOwner = group.myRole == MemberRole.OWNER,
                    onRemove = { onRemoveMember(member.userId) },
                    onMakeAdmin = { onUpdateRole(member.userId, MemberRole.ADMIN.value) },
                    onMakeMember = { onUpdateRole(member.userId, MemberRole.MEMBER.value) }
                )
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddMembers),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = brandPrimary()) {
                            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                Icon(EnchantIcons.plusCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Add members", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = brandPrimary())
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader("Settings")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column {
                        GroupSettingRow(
                            icon = EnchantIcons.link,
                            title = "Group link",
                            subtitle = inviteLink ?: "Create an invite link",
                            onClick = { if (inviteLink != null) onCopyInviteLink(inviteLink) else onCreateInviteLink() }
                        )
                        InsetDivider()
                        GroupSettingRow(
                            icon = EnchantIcons.pencil,
                            title = "Edit description",
                            onClick = { editDescription = group.description ?: ""; showEditDialog = true }
                        )
                        InsetDivider()
                        if (group.myRole == MemberRole.OWNER) {
                            GroupSettingRow(
                                icon = EnchantIcons.trash2,
                                title = "Delete group",
                                destructive = true,
                                onClick = onDeleteGroup
                            )
                        } else {
                            GroupSettingRow(
                                icon = EnchantIcons.logOut,
                                title = "Leave group",
                                destructive = true,
                                onClick = onLeaveGroup
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (error != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(error, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Description") },
            text = {
                OutlinedTextField(
                    value = editDescription,
                    onValueChange = { editDescription = it },
                    label = { Text("Group description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showEditDialog = false
                    onUpdateGroup(null, editDescription.ifBlank { null })
                }) { Text("Save", color = brandPrimary()) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun InsetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun ActionPill(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = brandTint().copy(alpha = 0.12f),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = brandPrimary())
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = brandPrimary(), maxLines = 1)
        }
    }
}

@Composable
private fun GroupSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(22.dp),
            tint = if (destructive) BrandRed else brandPrimary()
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (destructive) BrandRed else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        if (!destructive) {
            Icon(
                EnchantIcons.chevronRight,
                null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun MemberRow(member: GroupMember, isOwner: Boolean, onRemove: () -> Unit, onMakeAdmin: () -> Unit, onMakeMember: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showMenu = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = brandTint().copy(alpha = 0.12f)) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Text(
                        member.displayName?.take(2)?.uppercase() ?: member.userId.take(2).uppercase(),
                        fontWeight = FontWeight.SemiBold,
                        color = brandPrimary()
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(member.displayName ?: member.username ?: member.userId.take(12), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, tint, roleLabel) = when (member.role) {
                        MemberRole.OWNER -> Triple(EnchantIcons.star, brandPrimary(), "Owner")
                        MemberRole.ADMIN -> Triple(EnchantIcons.shieldCheck, brandTint(), "Admin")
                        else -> Triple(null, null, member.role.value)
                    }
                    if (icon != null && tint != null) {
                        Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(roleLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(EnchantIcons.ellipsisVertical, "Member options", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        }
    }

    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
        if (isOwner && member.role == MemberRole.MEMBER) {
            DropdownMenuItem(text = { Text("Make admin") }, onClick = { onMakeAdmin(); showMenu = false })
        }
        if (isOwner && member.role == MemberRole.ADMIN) {
            DropdownMenuItem(text = { Text("Make member") }, onClick = { onMakeMember(); showMenu = false })
        }
        DropdownMenuItem(text = { Text("Remove from group", color = BrandRed) }, onClick = { onRemove(); showMenu = false })
    }
}
