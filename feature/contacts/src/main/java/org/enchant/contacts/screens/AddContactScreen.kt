package org.enchant.contacts.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class User(
    val userId: String,
    val username: String,
    val displayName: String?,
    val avatarMediaId: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(
    onSearch: (String) -> Unit,
    searchResults: List<User>,
    onAddContact: (String) -> Unit,
    isSearching: Boolean
) {
    var query by remember { mutableStateOf("") }
    var addedIds by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Add Contact",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchPill(
                value = query,
                onValueChange = {
                    query = it
                    onSearch(it)
                },
                placeholder = "Search by username",
                modifier = Modifier.padding(
                    horizontal = ContactsSpacing.lg,
                    vertical = ContactsSpacing.lg
                )
            )

            if (isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (query.isNotBlank() && searchResults.isEmpty() && !isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No results",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(ContactsSpacing.xs))
                        Text(
                            "Try a different username",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else if (query.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(ContactsSpacing.lg))
                        Text(
                            "Search for users to add",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(searchResults, key = { it.userId }) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ContactsSpacing.lg, vertical = ContactsSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InitialAvatar(
                                text = user.displayName ?: user.username,
                                size = 44.dp
                            )
                            Spacer(modifier = Modifier.width(ContactsSpacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    user.displayName ?: user.username,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "@${user.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(ContactsSpacing.sm))
                            AddPillButton(
                                onClick = {
                                    addedIds = addedIds + user.userId
                                    onAddContact(user.userId)
                                },
                                added = user.userId in addedIds
                            )
                        }
                        InsetDivider(inset = 60.dp)
                    }
                }
            }
        }
    }
}
