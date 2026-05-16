package org.enchant.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.enchant.chat.components.ClusterPosition

@Composable
fun TextMessageBubble(
    text: String, isOutgoing: Boolean, clusterPosition: ClusterPosition,
    timestamp: String, status: String, isEdited: Boolean,
    onLongPress: () -> Unit = {}
) {
    val shape = when (clusterPosition) {
        ClusterPosition.SINGLE -> RoundedCornerShape(18.dp)
        ClusterPosition.START -> if (isOutgoing) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
        ClusterPosition.MIDDLE -> if (isOutgoing) RoundedCornerShape(18.dp, 18.dp, 4.dp, 4.dp) else RoundedCornerShape(18.dp, 18.dp, 4.dp, 4.dp)
        ClusterPosition.END -> if (isOutgoing) RoundedCornerShape(4.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 4.dp, 18.dp, 4.dp)
    }
    val bg = if (isOutgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        Surface(shape = shape, color = bg, modifier = Modifier.widthIn(max = 280.dp).clickable { onLongPress() }) {
            Column(modifier = Modifier.padding(10.dp, 6.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Text(timestamp, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isEdited) Text(" · edited", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(2.dp))
                    when (status) {
                        "sending" -> Text("○", fontSize = 10.sp, color = Color.Gray)
                        "sent" -> Text("✓", fontSize = 10.sp, color = Color.Gray)
                        "delivered" -> Text("✓✓", fontSize = 10.sp, color = Color.Gray)
                        "read" -> Text("✓✓", fontSize = 10.sp, color = Color(0xFF4FC3F7))
                    }
                }
            }
        }
    }
}

@Composable
fun MediaMessageBubble(imageUrl: String, isOutgoing: Boolean, clusterPosition: ClusterPosition, caption: String? = null, onTap: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.widthIn(max = 280.dp).clickable { onTap() }) {
            Column {
                Box(modifier = Modifier.size(200.dp, 200.dp).clip(RoundedCornerShape(12.dp)).background(Color.LightGray), contentAlignment = Alignment.Center) {
                    Text("📷", fontSize = 48.sp)
                }
                caption?.let { Text(it, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
fun VoiceMessageBubble(duration: Int, isOutgoing: Boolean, isPlaying: Boolean = false, onPlay: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        Surface(shape = RoundedCornerShape(18.dp), color = if (isOutgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 200.dp).clickable { onPlay() }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp, 8.dp)) {
                Text(if (isPlaying) "⏸" else "▶", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.height(24.dp).weight(1f).background(Color.Transparent)) {
                    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        repeat(20) { Box(modifier = Modifier.width(2.dp).height((4 + Math.random() * 16).toInt().dp).padding(end = 1.dp).background(MaterialTheme.colorScheme.onSurfaceVariant)) }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text("${duration / 60}:${(duration % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun DocumentBubble(filename: String, fileSize: String, mimeType: String, onDownload: () -> Unit = {}) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp), horizontalAlignment = Alignment.Start) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.widthIn(max = 280.dp).clickable { onDownload() }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                Text("📄", fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(filename, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(fileSize, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun LocationBubble(latitude: Double, longitude: Double, address: String? = null, onTap: () -> Unit = {}) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp), horizontalAlignment = Alignment.Start) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.widthIn(max = 280.dp).clickable { onTap() }) {
            Column(modifier = Modifier.padding(0.dp)) {
                Box(modifier = Modifier.size(200.dp, 120.dp).background(Color(0xFFE8F5E9)), contentAlignment = Alignment.Center) { Text("📍", fontSize = 36.sp) }
                address?.let { Text(it, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
fun StickerBubble(stickerImageUrl: String, packName: String? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp), horizontalAlignment = Alignment.Start) {
        Box(modifier = Modifier.size(128.dp).clip(RoundedCornerShape(8.dp)).background(Color.Transparent), contentAlignment = Alignment.Center) { Text("🎨", fontSize = 48.sp) }
    }
}

@Composable
fun SystemMessageBubble(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
