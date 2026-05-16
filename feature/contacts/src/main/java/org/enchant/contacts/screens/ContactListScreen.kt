package org.enchant.contacts.screens

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
import org.enchant.contacts.data.Contact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    contacts: List<Contact>,
    searchResults: List<Contact>,
    searchQuery: String,
    isLoading: Boolean,
    error: String?,
    onContactClick: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAddContact: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contacts") },
                actions = {
                    IconButton(onClick = onAddContact) {
                        Icon(Icons.Default.PersonAdd, "Add Contact")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddContact) {
                Icon(Icons.Default.PersonAdd, "Add Contact")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search users") },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, "Clear")
                        }
                    }
                }
            )

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (error != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(error, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            val displayList = if (searchQuery.isNotBlank()) searchResults else contacts

            if (displayList.isEmpty() && !isLoading) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.People, null, modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))
                        Text(if (searchQuery.isNotBlank()) "No users found" else "No contacts yet",
                            style = MaterialTheme.typography.titleMedium)
                        if (searchQuery.isBlank()) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onAddContact) { Text("Add your first contact") }
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(displayList, key = { it.userId }) { contact ->
                        ContactTile(contact, onClick = { onContactClick(contact.userId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactTile(contact: Contact, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Text((contact.displayName ?: contact.username ?: contact.userId).take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.displayName ?: contact.username ?: "Unknown", style = MaterialTheme.typography.titleSmall)
                if (contact.username != null) {
                    Text("@${contact.username}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (contact.isBlocked) {
                Icon(Icons.Default.Block, "Blocked", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
}
