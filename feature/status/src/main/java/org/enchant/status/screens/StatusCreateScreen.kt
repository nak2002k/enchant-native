package org.enchant.status.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import org.enchant.status.StatusPrivacy

private val presetColors = listOf(
    "#FF6B6B", "#FFA94D", "#FFD43B", "#69DB7C",
    "#38D9A9", "#4DABF7", "#748FFC", "#DA77F2",
    "#F06595", "#495057", "#212529", "#FFF"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusCreateScreen(
    onCreateText: (String, String, StatusPrivacy) -> Unit,
    onCreateMedia: (String, StatusPrivacy) -> Unit,
    onBack: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(presetColors[0]) }
    var selectedPrivacy by remember { mutableStateOf<StatusPrivacy>(StatusPrivacy.AllContacts) }
    var showPrivacyDropdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Status") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onCreateText(text, selectedColor, selectedPrivacy) },
                        enabled = text.isNotBlank()
                    ) {
                        Text("Share")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = parseColor(selectedColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                Box(
                    modifier = Modifier.padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text.ifBlank { "Type a status..." },
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (selectedColor == "#FFF") Color.Black else Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 700) text = it },
                label = { Text("Status text") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5,
                supportingText = { Text("${text.length}/700") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text("Background color", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetColors.chunked(6).forEach { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { colorHex ->
                            val isSelected = colorHex == selectedColor
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(parseColor(colorHex))
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = colorHex }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onCreateMedia("gif_placeholder", selectedPrivacy) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Gif, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add GIF")
                }
                OutlinedButton(
                    onClick = { onCreateMedia("image_placeholder", selectedPrivacy) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Image")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Privacy", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            Box {
                OutlinedButton(
                    onClick = { showPrivacyDropdown = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Visibility, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when (selectedPrivacy) {
                            StatusPrivacy.AllContacts -> "All Contacts"
                            StatusPrivacy.Selected -> "Selected Contacts"
                            StatusPrivacy.CloseFriends -> "Close Friends"
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(
                    expanded = showPrivacyDropdown,
                    onDismissRequest = { showPrivacyDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Contacts") },
                        onClick = { selectedPrivacy = StatusPrivacy.AllContacts; showPrivacyDropdown = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Selected Contacts") },
                        onClick = { selectedPrivacy = StatusPrivacy.Selected; showPrivacyDropdown = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Close Friends") },
                        onClick = { selectedPrivacy = StatusPrivacy.CloseFriends; showPrivacyDropdown = false }
                    )
                }
            }
        }
    }
}

private fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color.Gray
    }
}
