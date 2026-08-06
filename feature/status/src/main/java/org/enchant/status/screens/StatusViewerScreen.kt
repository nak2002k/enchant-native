package org.enchant.status.screens

import androidx.compose.material.icons.Icons
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.enchant.core.base.AppConfig
import org.enchant.status.StatusFeedEntry
import org.enchant.ui.icons.EnchantIcons

private val ViewerBlack = Color(0xFF000000)
private val ViewerAvatar = Color(0xFF2C2C2E)
private val ReplyPill = Color.White.copy(alpha = 0.15f)
private val Muted = Color.White.copy(alpha = 0.6f)

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
    var controlsVisible by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
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
        Box(modifier = Modifier.fillMaxSize().background(ViewerBlack), contentAlignment = Alignment.Center) {
            Text("No statuses", color = Color.White)
        }
        return
    }

    val currentStatus = statuses[currentIndex]
    val chromeAlpha by animateFloatAsState(
        targetValue = if (controlsVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 320f),
        label = "chromeAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ViewerBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .alpha(chromeAlpha)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                                .height(2.dp)
                                .clip(CircleShape),
                            color = Color.White.copy(alpha = 0.9f),
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(ViewerAvatar),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            currentStatus.username.take(2).uppercase(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            currentStatus.username,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            formatViewerTime(currentStatus.createdAt),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = { onViewInfo(currentStatus.statusId) }) {
                        Icon(
                            EnchantIcons.info,
                            contentDescription = "View info",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(EnchantIcons.x, contentDescription = "Close", tint = Color.White)
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
                                val third = size.width / 3f
                                when {
                                    offset.x < third && currentIndex > 0 -> currentIndex--
                                    offset.x > third * 2 && currentIndex < statuses.size - 1 -> currentIndex++
                                    else -> controlsVisible = !controlsVisible
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
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 28.dp)
                    )
                } else if (currentStatus.type == "image" && currentStatus.mediaId != null) {
                    AsyncImage(
                        model = "${AppConfig.gatewayUrl}/v1/media/${currentStatus.mediaId}",
                        contentDescription = "Status image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (currentStatus.type == "video" && currentStatus.mediaId != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = "${AppConfig.gatewayUrl}/v1/media/${currentStatus.mediaId}/thumbnail",
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
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 28.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .alpha(chromeAlpha),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(ReplyPill)
                        .clickable { onReply(currentStatus.statusId) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        EnchantIcons.reply,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Reply to @${currentStatus.username}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ReplyPill)
                ) {
                    Icon(
                        if (isMuted) EnchantIcons.volumeX else EnchantIcons.speakerHigh,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = if (isMuted) Muted else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatViewerTime(iso: String): String {
    return try {
        val instant = java.time.Instant.parse(iso)
        val diff = java.time.Duration.between(instant, java.time.Instant.now())
        when {
            diff.isNegative || diff.seconds < 60 -> "Just now"
            diff.toMinutes() < 60 -> "${diff.toMinutes()}m ago"
            diff.toHours() < 24 -> "${diff.toHours()}h ago"
            diff.toDays() < 7 -> "${diff.toDays()}d ago"
            else -> java.time.format.DateTimeFormatter.ofPattern("MMM d")
                .withZone(java.time.ZoneId.systemDefault())
                .format(instant)
        }
    } catch (_: Exception) {
        "Recently"
    }
}
