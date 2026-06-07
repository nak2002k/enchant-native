package org.enchant.status.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.enchant.status.StatusFeedEntry

@Composable
fun StatusViewerScreen(
    statuses: List<StatusFeedEntry>,
    initialIndex: Int,
    onReply: (String) -> Unit,
    onClose: () -> Unit,
    onViewInfo: (String) -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(initialIndex) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }
    val statusDuration = 5000L

    LaunchedEffect(currentIndex, isPaused) {
        if (isPaused || statuses.isEmpty()) return@LaunchedEffect
        progress = 0f
        val steps = 100
        for (i in 1..steps) {
            delay(statusDuration / steps)
            progress = i.toFloat() / steps
        }
        if (currentIndex < statuses.size - 1) {
            currentIndex++
        } else {
            onClose()
        }
    }

    if (statuses.isEmpty() || currentIndex !in statuses.indices) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("No statuses", color = Color.White)
        }
        return
    }

    val currentStatus = statuses[currentIndex]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                    statuses.forEachIndexed { index, _ ->
                        val animProgress by animateFloatAsState(
                            targetValue = if (index < currentIndex) 1f
                            else if (index == currentIndex) progress else 0f,
                            animationSpec = tween(durationMillis = 100)
                        )
                        LinearProgressIndicator(
                            progress = { animProgress },
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                currentStatus.username.take(2).uppercase(),
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        currentStatus.username,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPaused = true
                                tryAwaitRelease()
                                isPaused = false
                            },
                            onTap = { offset ->
                                val halfWidth = size.width / 2f
                                if (offset.x < halfWidth && currentIndex > 0) {
                                    currentIndex--
                                } else if (offset.x > halfWidth && currentIndex < statuses.size - 1) {
                                    currentIndex++
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (currentStatus.type == "text") {
                    Text(
                        currentStatus.text ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                } else if (currentStatus.type == "image" && currentStatus.mediaId != null) {
                    AsyncImage(
                        model = "https://api.enchant.local/v1/media/${currentStatus.mediaId}",
                        contentDescription = "Status image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (currentStatus.type == "video" && currentStatus.mediaId != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = "https://api.enchant.local/v1/media/${currentStatus.mediaId}/thumbnail",
                            contentDescription = "Video thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Play video",
                            modifier = Modifier.size(64.dp).align(Alignment.Center),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                } else {
                    Text(
                        currentStatus.text ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = { onReply(currentStatus.statusId) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Reply, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reply")
                }
                OutlinedButton(
                    onClick = { onViewInfo(currentStatus.statusId) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("View info")
                }
            }
        }
    }
}
