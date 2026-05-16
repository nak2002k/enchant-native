package org.enchant.groups.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.enchant.groups.data.Group
import org.enchant.groups.data.GroupMember

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
    onCreateInviteLink: () -> Unit,
    onCopyInviteLink: (String) -> Unit,
    onViewJoinRequests: () -> Unit,
    onLeaveGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "Group Info") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
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
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                            Text(group.name.take(2).uppercase(), style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(group.name, style = MaterialTheme.typography.titleLarge)
                    if (group.description != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(group.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${group.memberCount} members · ${group.myRole}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActionButton(Icons.Default.PersonAdd, "Add", onAddMembers)
                    ActionButton(Icons.Default.Link, "Invite") { onCreateInviteLink() }
                    if (joinRequests > 0) {
                        BadgedBox(badge = { Badge { Text(joinRequests.toString()) } }) {
                            ActionButton(Icons.Default.PersonSearch, "Requests", onViewJoinRequests)
                        }
                    } else {
                        ActionButton(Icons.Default.PersonSearch, "Requests", onViewJoinRequests)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (inviteLink != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { onCopyInviteLink(inviteLink) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Invite link: $inviteLink", style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            item {
                Text(
                    "Members (${members.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(members, key = { it.userId }) { member ->
                MemberRow(
                    member = member,
                    isOwner = group.myRole == "owner",
                    onRemove = { onRemoveMember(member.userId) },
                    onMakeAdmin = { onUpdateRole(member.userId, "admin") },
                    onMakeMember = { onUpdateRole(member.userId, "member") }
                )
            }

            if (error != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(error, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            if (group.myRole == "owner") {
                item {
                    OutlinedButton(
                        onClick = onDeleteGroup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Group")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (group.myRole != "owner") {
                item {
                    OutlinedButton(
                        onClick = onLeaveGroup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text("Leave Group")
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(icon, label, modifier = Modifier.padding(8.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MemberRow(member: GroupMember, isOwner: Boolean, onRemove: () -> Unit, onMakeAdmin: () -> Unit, onMakeMember: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.clickable { showMenu = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Text(member.displayName?.take(2)?.uppercase() ?: member.userId.take(2).uppercase())
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(member.displayName ?: member.username ?: member.userId.take(12), style = MaterialTheme.typography.bodyMedium)
                Text(member.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (member.role) {
                "owner" -> Icon(Icons.Default.Star, "Owner", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                "admin" -> Icon(Icons.Default.Shield, "Admin", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
            }
        }
    }

    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
        if (isOwner && member.role == "member") {
            DropdownMenuItem(text = { Text("Make admin") }, onClick = { onMakeAdmin(); showMenu = false })
        }
        if (isOwner && member.role == "admin") {
            DropdownMenuItem(text = { Text("Make member") }, onClick = { onMakeMember(); showMenu = false })
        }
        DropdownMenuItem(text = { Text("Remove from group") }, onClick = { onRemove(); showMenu = false })
    }
}
