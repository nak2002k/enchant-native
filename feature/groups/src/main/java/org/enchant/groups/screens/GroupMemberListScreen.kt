package org.enchant.groups.screens

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
import org.enchant.groups.data.GroupMember
import org.enchant.groups.data.MemberRole

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
                title = { Text("Members (${members.size})") },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = onAddMember) {
                            Icon(Icons.Default.PersonAdd, "Add member")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (members.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No members", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(members, key = { it.userId }) { member ->
                var showMenu by remember { mutableStateOf(false) }

                Surface(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    (member.displayName ?: member.userId).take(2).uppercase(),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(member.displayName ?: member.username ?: member.userId, style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val (icon, tint) = when (member.role) {
                                    MemberRole.OWNER -> Icons.Default.Star to MaterialTheme.colorScheme.primary
                                    MemberRole.ADMIN -> Icons.Default.Shield to MaterialTheme.colorScheme.tertiary
                                    else -> null to null
                                }
                                if (icon != null && tint != null) {
                                    Icon(icon, member.role.value, tint = tint, modifier = Modifier.size(14.dp))
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
                                Icon(Icons.Default.MoreVert, "Member options")
                            }

                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                if (member.role == MemberRole.MEMBER) {
                                    DropdownMenuItem(
                                        text = { Text("Promote to admin") },
                                        onClick = { onUpdateRole(member.userId, MemberRole.ADMIN.value); showMenu = false },
                                        leadingIcon = { Icon(Icons.Default.Shield, null) }
                                    )
                                }
                                if (member.role == MemberRole.ADMIN) {
                                    DropdownMenuItem(
                                        text = { Text("Demote to member") },
                                        onClick = { onUpdateRole(member.userId, MemberRole.MEMBER.value); showMenu = false },
                                        leadingIcon = { Icon(Icons.Default.Person, null) }
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
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
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
