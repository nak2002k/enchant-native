package org.enchant.contacts.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onAddContact: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val isSearchMode = searchQuery.isNotBlank()
    val displayList = if (isSearchMode) searchResults else contacts
    val groupedContacts = remember(displayList, isSearchMode) {
        if (isSearchMode) emptyList() else groupContacts(displayList)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Contacts",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Default.PersonAdd,
                            "Add contact",
                            tint = ContactsBrand.BrandBlue
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                SearchPill(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = "Search by username",
                    modifier = Modifier.padding(
                        horizontal = ContactsSpacing.lg,
                        vertical = ContactsSpacing.sm
                    )
                )

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (error != null) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(
                            horizontal = ContactsSpacing.lg,
                            vertical = ContactsSpacing.sm
                        )
                    )
                }

                if (displayList.isEmpty() && !isLoading) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (isSearchMode) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "No results",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(ContactsSpacing.xs))
                                Text(
                                    "Try a different username",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = ContactsSpacing.xxxl)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.People,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(ContactsSpacing.lg))
                                Text(
                                    "No contacts yet",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Spacer(modifier = Modifier.height(ContactsSpacing.sm))
                                Text(
                                    "Search for users to add them",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(ContactsSpacing.xxl))
                                Button(
                                    onClick = onRefresh,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(ContactsRadii.pill),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ContactsBrand.BrandBlue,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.height(44.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        "Add a contact",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        if (isSearchMode) {
                            items(displayList, key = { it.userId }) { contact ->
                                SearchResultRow(
                                    contact = contact,
                                    isAdded = contacts.any { it.userId == contact.userId },
                                    onAdd = { onAddContact(contact.userId) },
                                    onClick = { onContactClick(contact.userId) }
                                )
                            }
                        } else {
                            groupedContacts.forEach { (letter, group) ->
                                item(key = "header_$letter") {
                                    Text(
                                        letter,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(
                                            start = ContactsSpacing.xl,
                                            end = ContactsSpacing.lg,
                                            top = ContactsSpacing.lg,
                                            bottom = ContactsSpacing.sm
                                        )
                                    )
                                }
                                items(group, key = { it.userId }) { contact ->
                                    ContactRow(
                                        contact = contact,
                                        onClick = { onContactClick(contact.userId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ContactsFab(
                onClick = onRefresh,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(ContactsSpacing.xxl)
            )
        }
    }
}

@Composable
private fun SearchResultRow(contact: Contact, isAdded: Boolean, onAdd: () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ContactsSpacing.lg, vertical = ContactsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitialAvatar(
            text = contact.displayName ?: contact.username ?: contact.userId,
            size = 44.dp
        )
        Spacer(modifier = Modifier.width(ContactsSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                contact.displayName ?: contact.username ?: "Unknown",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (contact.username != null) {
                Text(
                    "@${contact.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(ContactsSpacing.sm))
        AddPillButton(onClick = onAdd, added = isAdded)
    }
    InsetDivider(inset = 60.dp)
}

@Composable
private fun ContactRow(contact: Contact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ContactsSpacing.lg, vertical = ContactsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitialAvatar(
            text = contact.displayName ?: contact.username ?: contact.userId,
            size = 44.dp
        )
        Spacer(modifier = Modifier.width(ContactsSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                contact.displayName ?: contact.username ?: "Unknown",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (contact.username != null) {
                Text(
                    "@${contact.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (contact.isBlocked) {
            Icon(
                Icons.Default.Block,
                contentDescription = "Blocked",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
    InsetDivider(inset = 60.dp)
}

private fun groupContacts(contacts: List<Contact>): List<Pair<String, List<Contact>>> {
    return contacts
        .groupBy { contact ->
            val first = (contact.displayName ?: contact.username ?: "").trim().firstOrNull()
            if (first != null && first.isLetterOrDigit()) first.uppercaseChar() else '#'
        }
        .toSortedMap()
        .map { it.key.toString() to it.value }
}
