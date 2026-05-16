package org.enchant.polls

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PollBubble(
    poll: PollData,
    onVote: (List<String>) -> Unit,
    isVoting: Boolean
) {
    var selectedOptions by remember(poll.pollId) { mutableStateOf(poll.yourVote) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        Text(
            text = poll.question,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (poll.isClosed && poll.totalVotes == 0) {
            Text("Poll closed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        poll.options.forEach { option ->
            val isSelected = option in selectedOptions
            val voteCount = poll.results[option] ?: 0
            val percentage = if (poll.totalVotes > 0)
                (voteCount.toFloat() / poll.totalVotes * 100) else 0f

            val hasVoted = poll.yourVote.isNotEmpty()
            val isClosed = poll.isClosed

            Surface(
                onClick = {
                    if (!isClosed && !hasVoted && !isVoting) {
                        selectedOptions = if (poll.allowMultiple) {
                            if (isSelected) selectedOptions - option
                            else selectedOptions + option
                        } else {
                            listOf(option)
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp),
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    isClosed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (hasVoted || isClosed) {
                        val barColor = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        LinearProgressIndicator(
                            progress = { percentage / 100f },
                            color = barColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (poll.allowMultiple && !hasVoted && !isClosed) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            color = if (isClosed && !isSelected)
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (hasVoted || isClosed) {
                            Text(
                                text = "${percentage.toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${poll.totalVotes} vote${if (poll.totalVotes != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (poll.isClosed) {
                Text("Closed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }

        if (poll.yourVote.isEmpty() && !poll.isClosed) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onVote(selectedOptions) },
                enabled = selectedOptions.isNotEmpty() && !isVoting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isVoting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Vote")
            }
        }
    }
}
