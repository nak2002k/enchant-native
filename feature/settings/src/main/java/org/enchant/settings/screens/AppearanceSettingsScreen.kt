package org.enchant.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun AppearanceSettingsScreen(
    currentTheme: String,
    fontSize: Float,
    onThemeChange: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onBack: () -> Unit
) {
    SettingsScaffold(title = "Appearance", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = EnchantSpacing.lg,
                end = EnchantSpacing.lg,
                top = EnchantSpacing.sm,
                bottom = EnchantSpacing.xxxl,
            ),
        ) {
            item { EnchantSectionHeader("Theme") }
            item {
                EnchantGroupedCard {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(EnchantSpacing.sm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
                    ) {
                        listOf(
                            "light" to "Light",
                            "dark" to "Dark",
                            "system" to "System"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = currentTheme == value,
                                onClick = { onThemeChange(value) },
                                label = { Text(label) },
                                leadingIcon = {
                                    Icon(
                                        when (value) {
                                            "light" -> Icons.Rounded.LightMode
                                            "dark" -> Icons.Rounded.DarkMode
                                            else -> Icons.Rounded.Settings
                                        },
                                        null, modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            item { EnchantSectionHeader("Font Size") }
            item {
                EnchantGroupedCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Font size",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${((fontSize - 0.8f) / 0.6f * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Slider(
                            value = fontSize,
                            onValueChange = onFontSizeChange,
                            valueRange = 0.8f..1.4f,
                            steps = 5,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("A", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("A", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { EnchantSectionHeader("Preview") }
            item {
                EnchantGroupedCard {
                    Text(
                        "This is how messages will appear at the selected font size.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontSize
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
                    )
                }
            }
        }
    }
}
