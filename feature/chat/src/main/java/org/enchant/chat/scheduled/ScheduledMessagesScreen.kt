package org.enchant.chat.scheduled

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BrandPrimaryLight = Color(0xFF3A0D6E)
private val BrandPrimaryDark = Color(0xFFB388E3)
private val BrandRed = Color(0xFFFF3B30)

@Composable
private fun brandPrimary(): Color = if (isSystemInDarkTheme()) BrandPrimaryDark else BrandPrimaryLight

data class ScheduledMessageItem(
    val id: Long,
    val content: String,
    val scheduledAt: Long,
    val isSent: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledMessagesScreen(
    messages: List<ScheduledMessageItem>,
    onCancel: (Long) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Scheduled", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, color = brandPrimary().copy(alpha = 0.10f)) {
                        Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Schedule, null, modifier = Modifier.size(44.dp), tint = brandPrimary())
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("No scheduled messages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(messages, key = { it.id }) { msg ->
                    val dateStr = remember(msg.scheduledAt) {
                        try {
                            SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(msg.scheduledAt))
                        } catch (_: Exception) { msg.scheduledAt.toString() }
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    msg.content,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Schedule, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            if (!msg.isSent) {
                                Surface(
                                    shape = CircleShape,
                                    color = BrandRed.copy(alpha = 0.12f),
                                    modifier = Modifier.clickable { onCancel(msg.id) }
                                ) {
                                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Close,
                                            "Cancel",
                                            tint = BrandRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            } else {
                                Icon(Icons.Default.CheckCircle, "Sent", tint = brandPrimary(), modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
