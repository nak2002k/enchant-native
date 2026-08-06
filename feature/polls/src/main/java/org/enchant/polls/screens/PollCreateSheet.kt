package org.enchant.polls.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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

private const val DEFAULT_CLOSE_SECONDS = 3600
private const val MIN_CLOSE_SECONDS = 60
private const val MAX_CLOSE_SECONDS = 604800

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollCreateSheet(
    conversationId: String,
    onCreate: (conversationId: String, question: String, options: List<String>, allowMultiple: Boolean, anonymous: Boolean, closeInSeconds: Int?) -> Unit,
    onDismiss: () -> Unit,
    isCreating: Boolean = false
) {
    val sheetState = rememberModalBottomSheetState()
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var allowMultiple by remember { mutableStateOf(false) }
    var anonymous by remember { mutableStateOf(false) }
    var enableCloseTimer by remember { mutableStateOf(false) }
    var closeInSeconds by remember { mutableStateOf(DEFAULT_CLOSE_SECONDS.toString()) }

    val createEnabled = question.isNotBlank() && options.count { it.isNotBlank() } >= 2 && !isCreating

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Create poll",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            TextField(
                value = question,
                onValueChange = { if (it.length <= 200) question = it },
                placeholder = { Text("Ask a question", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = underlineFreeFieldColors(),
                supportingText = { Text("${question.length}/200") }
            )

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Options", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text("2–12", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(options) { index, option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = option,
                            onValueChange = { newVal ->
                                if (newVal.length <= 100) {
                                    options = options.toMutableList().also { it[index] = newVal }
                                }
                            },
                            placeholder = { Text("Option ${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = underlineFreeFieldColors(),
                            supportingText = { Text("${option.length}/100") }
                        )
                        if (options.size > 2) {
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = { options = options.toMutableList().also { it.removeAt(index) } }) {
                                Icon(Icons.Default.RemoveCircle, "Remove option",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            if (options.size < 12) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { options = options + "" }
                        .padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Default.AddCircle, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add option", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(8.dp))

            SettingsSwitchRow("Multiple choice", allowMultiple) { allowMultiple = it }
            SettingsSwitchRow("Anonymous", anonymous) { anonymous = it }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Auto-close after", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                if (enableCloseTimer) {
                    TextField(
                        value = closeInSeconds,
                        onValueChange = { closeInSeconds = it.filter { c -> c.isDigit() }.take(7) },
                        modifier = Modifier.width(84.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = RoundedCornerShape(12.dp),
                        colors = underlineFreeFieldColors(),
                        supportingText = { Text("s") }
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Switch(checked = enableCloseTimer, onCheckedChange = { enableCloseTimer = it },
                    colors = purpleSwitchColors())
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val validOptions = options.filter { it.isNotBlank() }
                    val closeSecs = if (enableCloseTimer) closeInSeconds.toIntOrNull()
                        ?.coerceIn(MIN_CLOSE_SECONDS, MAX_CLOSE_SECONDS) else null
                    onCreate(conversationId, question, validOptions, allowMultiple, anonymous, closeSecs)
                },
                enabled = createEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                )
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Create")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = purpleSwitchColors())
    }
}

@Composable
private fun purpleSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline
)

@Composable
private fun underlineFreeFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary
)
