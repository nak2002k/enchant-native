package org.enchant.groups.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.enchant.core.model.DisappearTimerPresets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    disappearingMessagesEnabled: Boolean,
    disappearingMessagesDurationSeconds: Int,
    isLoading: Boolean,
    error: String?,
    onDisappearingMessagesToggle: (Boolean, Int) -> Unit,
    onBack: () -> Unit
) {
    var localEnabled by remember(disappearingMessagesEnabled) { mutableStateOf(disappearingMessagesEnabled) }
    var localDuration by remember(disappearingMessagesDurationSeconds) { mutableStateOf(disappearingMessagesDurationSeconds) }

    LaunchedEffect(disappearingMessagesEnabled, disappearingMessagesDurationSeconds) {
        localEnabled = disappearingMessagesEnabled
        localDuration = disappearingMessagesDurationSeconds
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Disappearing Messages", style = MaterialTheme.typography.titleSmall)
                        }
                        Switch(
                            checked = localEnabled,
                            onCheckedChange = { enabled ->
                                localEnabled = enabled
                                val duration = if (enabled && localDuration == 0) 86400 else localDuration
                                onDisappearingMessagesToggle(enabled, duration)
                            }
                        )
                    }

                    if (localEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "Duration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        var expanded by remember { mutableStateOf(false) }
                        val selectedLabel = DisappearTimerPresets.GROUP_OPTIONS.find { it.seconds == localDuration }?.label ?: "24 hours"

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedLabel,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DisappearTimerPresets.GROUP_OPTIONS.filter { it.seconds > 0 }.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            localDuration = option.seconds
                                            onDisappearingMessagesToggle(localEnabled, option.seconds)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (error != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}