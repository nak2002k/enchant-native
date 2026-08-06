package org.enchant.groups.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.enchant.core.model.DisappearTimerPresets

private val BrandPrimaryLight = Color(0xFF3A0D6E)
private val BrandPrimaryDark = Color(0xFF8E24AA)

@Composable
private fun brandPrimary(): Color = if (isSystemInDarkTheme()) BrandPrimaryDark else BrandPrimaryLight

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
                title = {
                    Text("Group Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                .verticalScroll(rememberScrollState())
        ) {
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = brandPrimary()
                )
            }

            if (error != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    "Messaging",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = RoundedCornerShape(10.dp), color = brandPrimary().copy(alpha = 0.12f)) {
                                Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = brandPrimary()
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Disappearing Messages", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                if (localEnabled) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val currentLabel = DisappearTimerPresets.GROUP_OPTIONS.find { it.seconds == localDuration }?.label
                                        ?: DisappearTimerPresets.GROUP_OPTIONS.find { it.seconds > 0 }?.label ?: "24 hours"
                                    Text(
                                        currentLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = localEnabled,
                                onCheckedChange = { enabled ->
                                    localEnabled = enabled
                                    val duration = if (enabled && localDuration == 0) 86400 else localDuration
                                    onDisappearingMessagesToggle(enabled, duration)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = brandPrimary(),
                                    checkedThumbColor = Color.White
                                )
                            )
                        }

                        if (localEnabled) {
                            HorizontalDivider(modifier = Modifier.padding(start = 62.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            var expanded by remember { mutableStateOf(false) }
                            val selectedLabel = DisappearTimerPresets.GROUP_OPTIONS.find { it.seconds == localDuration }?.label ?: "24 hours"

                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = it }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = true) { expanded = true }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Duration", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    Text(
                                        selectedLabel,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = brandPrimary(),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                                }
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    DisappearTimerPresets.GROUP_OPTIONS.filter { it.seconds > 0 }.forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    option.label,
                                                    color = if (option.seconds == localDuration) brandPrimary() else Color.Unspecified
                                                )
                                            },
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
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
