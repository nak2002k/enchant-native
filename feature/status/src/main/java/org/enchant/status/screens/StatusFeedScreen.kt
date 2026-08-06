package org.enchant.status.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.enchant.status.StatusFeedEntry

private val BrandBlue = Color(0xFF3A76F0)
private val BrandBlueLight = Color(0xFF7FB0FF)
private val RingGray = Color(0xFFC9CDD4)
private val StoryRingStroke = 2.5.dp
private val AvatarSize = 64.dp
private val ListAvatarSize = 48.dp

@Composable
fun StatusFeedScreen(
    myStatus: StatusFeedEntry?,
    feed: List<StatusFeedEntry>,
    onStatusTap: (String) -> Unit,
    onCreateStatus: () -> Unit
) {
    val groupedFeed = remember(feed) {
        feed.groupBy { it.userId }
    }
    val sortedUsers = remember(groupedFeed) {
        groupedFeed.entries.sortedBy { entry ->
            entry.value.any { !it.isViewed }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Stories",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            FilledIconButton(
                onClick = onCreateStatus,
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New story",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                StoryRingsRow(
                    myStatus = myStatus,
                    friends = sortedUsers,
                    onStatusTap = onStatusTap,
                    onCreateStatus = onCreateStatus
                )
            }

            if (feed.isNotEmpty()) {
                item {
                    Text(
                        "Recent updates",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 4.dp)
                    )
                }
            }

            if (feed.isEmpty()) {
                item {
                    EmptyUpdatesState()
                }
            }

            sortedUsers.forEach { (userId, statuses) ->
                val anyUnviewed = statuses.any { !it.isViewed }
                item(key = userId) {
                    val targetStatus = (statuses.firstOrNull { !it.isViewed } ?: statuses.first())
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStatusTap(targetStatus.statusId) }
                            .then(if (anyUnviewed) Modifier else Modifier.alpha(0.55f))
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(ListAvatarSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = 2.dp,
                                    color = if (anyUnviewed) BrandBlue else RingGray,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                statuses.firstOrNull()?.username?.take(2)?.uppercase() ?: "?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                statuses.firstOrNull()?.username ?: "Unknown",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                formatRelativeTime(statuses.last().createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        if (anyUnviewed) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(BrandBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = BrandBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 82.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryRingsRow(
    myStatus: StatusFeedEntry?,
    friends: List<Map.Entry<String, List<StatusFeedEntry>>>,
    onStatusTap: (String) -> Unit,
    onCreateStatus: () -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "my_story") {
            Column(
                modifier = Modifier
                    .width(68.dp)
                    .clickable {
                        if (myStatus != null) onStatusTap(myStatus.statusId) else onCreateStatus()
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(AvatarSize), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(AvatarSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = StoryRingStroke,
                                brush = Brush.linearGradient(listOf(BrandBlue, BrandBlueLight)),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (myStatus != null) {
                            Text(
                                myStatus.text?.take(2)?.uppercase() ?: "S",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(BrandBlue)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "My story",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        items(friends, key = { it.key }) { (_, statuses) ->
            val anyUnviewed = statuses.any { !it.isViewed }
            val targetStatus = (statuses.firstOrNull { !it.isViewed } ?: statuses.first())
            Column(
                modifier = Modifier
                    .width(68.dp)
                    .clickable { onStatusTap(targetStatus.statusId) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(AvatarSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = StoryRingStroke,
                            color = if (anyUnviewed) BrandBlue else RingGray,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        statuses.firstOrNull()?.username?.take(2)?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    statuses.firstOrNull()?.username ?: "Unknown",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyUpdatesState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No updates",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Share photos, videos and updates with your contacts.\nThey disappear after 24 hours.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

private fun formatRelativeTime(iso: String): String {
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
