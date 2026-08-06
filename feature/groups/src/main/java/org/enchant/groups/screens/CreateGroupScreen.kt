package org.enchant.groups.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
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

private val BrandPrimaryLight = Color(0xFF3A0D6E)
private val BrandPrimaryDark = Color(0xFFB388E3)
private val BrandTintLight = Color(0xFF7B1FA2)

@Composable
private fun brandPrimary(): Color = if (isSystemInDarkTheme()) BrandPrimaryDark else BrandPrimaryLight

@Composable
private fun brandTint(): Color = BrandTintLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onGroupCreated: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onCreateGroup: (String, String?, List<String>?) -> Unit,
    isLoading: Boolean = false,
    error: String? = null
) {
    var groupName by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    var selectedContacts by remember { mutableStateOf(listOf<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("New Group", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = brandTint().copy(alpha = 0.16f),
                    border = androidx.compose.foundation.BorderStroke(2.dp, brandPrimary().copy(alpha = 0.6f))
                ) {
                    Box(modifier = Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Group, null, tint = brandPrimary(), modifier = Modifier.size(32.dp))
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = brandPrimary(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp)
                ) {
                    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextField(
                value = groupName,
                onValueChange = { if (it.length <= 100) groupName = it },
                placeholder = {
                    Text(
                        "Group name",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                isError = error != null,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                supportingText = {
                    when {
                        error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                        else -> Text("${groupName.length}/100")
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = groupDescription,
                onValueChange = { if (it.length <= 512) groupDescription = it },
                placeholder = {
                    Text(
                        "Description (optional)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                supportingText = { Text("${groupDescription.length}/512") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text("Members", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "You can add members later",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column {
                    if (selectedContacts.isNotEmpty()) {
                        selectedContacts.forEach { contactId ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(shape = CircleShape, color = brandTint().copy(alpha = 0.12f)) {
                                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            contactId.take(2).uppercase(),
                                            fontWeight = FontWeight.SemiBold,
                                            color = brandPrimary()
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(contactId, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Surface(shape = CircleShape, color = brandPrimary()) {
                                    Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = brandPrimary()) {
                            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Add members",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = brandPrimary()
                        )
                    }
                }
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = brandPrimary()
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onCreateGroup(groupName, groupDescription.ifBlank { null }, selectedContacts.ifEmpty { null }) },
                enabled = groupName.isNotBlank() && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = brandPrimary(),
                    contentColor = Color.White,
                    disabledContainerColor = brandPrimary().copy(alpha = 0.4f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(50)
            ) {
                Text("Create", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
