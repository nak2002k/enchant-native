package org.enchant.polls.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollCreateSheet(
    onCreate: (question: String, options: List<String>, allowMultiple: Boolean, closeInSeconds: Int?) -> Unit,
    onDismiss: () -> Unit,
    isCreating: Boolean = false
) {
    val sheetState = rememberModalBottomSheetState()
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var allowMultiple by remember { mutableStateOf(false) }
    var enableCloseTimer by remember { mutableStateOf(false) }
    var closeInSeconds by remember { mutableStateOf("3600") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .padding(horizontal = 16.dp)
        ) {
            Text("Create Poll", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp))

            OutlinedTextField(
                value = question,
                onValueChange = { if (it.length <= 200) question = it },
                label = { Text("Question") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("${question.length}/200") }
            )

            Spacer(Modifier.height(16.dp))

            Text("Options", style = MaterialTheme.typography.titleSmall)
            Text("Minimum 2, maximum 12", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

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
                        OutlinedTextField(
                            value = option,
                            onValueChange = { newVal ->
                                if (newVal.length <= 100) {
                                    options = options.toMutableList().also { it[index] = newVal }
                                }
                            },
                            label = { Text("Option ${index + 1}") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            supportingText = { Text("${option.length}/100") }
                        )
                        if (options.size > 2) {
                            IconButton(onClick = { options = options.toMutableList().also { it.removeAt(index) } }) {
                                Icon(Icons.Default.RemoveCircle, "Remove option",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            if (options.size < 12) {
                TextButton(onClick = { options = options + "" }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add option")
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = allowMultiple, onCheckedChange = { allowMultiple = it })
                Text("Allow multiple votes", style = MaterialTheme.typography.bodyMedium)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = enableCloseTimer, onCheckedChange = { enableCloseTimer = it })
                Text("Auto-close after", style = MaterialTheme.typography.bodyMedium)
                if (enableCloseTimer) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = closeInSeconds,
                        onValueChange = { closeInSeconds = it.filter { c -> c.isDigit() }.take(7) },
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        supportingText = { Text("seconds") }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val validOptions = options.filter { it.isNotBlank() }
                    val closeSecs = if (enableCloseTimer) closeInSeconds.toIntOrNull()
                        ?.coerceIn(60, 604800) else null
                    onCreate(question, validOptions, allowMultiple, closeSecs)
                },
                enabled = question.isNotBlank() && options.count { it.isNotBlank() } >= 2 && !isCreating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Create Poll")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
