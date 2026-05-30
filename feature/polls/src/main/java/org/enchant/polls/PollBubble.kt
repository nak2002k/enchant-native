package org.enchant.polls

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    onClosePoll: ((String) -> Unit)? = null,
    onViewVoters: ((String, String) -> Unit)? = null,
    isVoting: Boolean,
    isCreator: Boolean = false
) {
    var selectedOptionIds by remember(poll.pollId) { mutableStateOf(poll.yourVote) }
    var showVotersSheet by remember { mutableStateOf(false) }
    var selectedOptionForVoters by remember { mutableStateOf<String?>(null) }

    if (showVotersSheet && selectedOptionForVoters != null) {
        val voters = poll.results[selectedOptionForVoters] ?: 0
        VotersBottomSheet(
            optionText = poll.options.find { it.id == selectedOptionForVoters }?.text ?: "",
            voteCount = voters,
            onDismiss = { showVotersSheet = false },
            onLoadVoters = { onViewVoters?.invoke(poll.pollId, selectedOptionForVoters!!) }
        )
    }

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
            val isSelected = option.id in selectedOptionIds
            val voteCount = poll.results[option.id] ?: 0
            val percentage = if (poll.totalVotes > 0)
                (voteCount.toFloat() / poll.totalVotes * 100) else 0f

            val hasVoted = poll.yourVote.isNotEmpty()
            val isClosed = poll.isClosed

            Surface(
                onClick = {
                    if (!isClosed && !hasVoted && !isVoting) {
                        selectedOptionIds = if (poll.allowMultiple) {
                            if (isSelected) selectedOptionIds - option.id
                            else selectedOptionIds + option.id
                        } else {
                            listOf(option.id)
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
                            text = option.text,
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
                        if (isClosed && poll.totalVotes > 0) {
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    selectedOptionForVoters = option.id
                                    showVotersSheet = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Voters", style = MaterialTheme.typography.labelSmall)
                            }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (poll.isClosed) {
                    Text("Closed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
                if (isCreator && !poll.isClosed) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { onClosePoll?.invoke(poll.pollId) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Close Poll", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (poll.yourVote.isEmpty() && !poll.isClosed) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onVote(selectedOptionIds) },
                enabled = selectedOptionIds.isNotEmpty() && !isVoting,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VotersBottomSheet(
    optionText: String,
    voteCount: Int,
    voters: List<Voter> = emptyList(),
    isLoading: Boolean = false,
    onDismiss: () -> Unit,
    onLoadVoters: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(voters) {
        if (voters.isEmpty() && !isLoading) {
            onLoadVoters()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Voters: $optionText",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "$voteCount vote${if (voteCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (voters.isEmpty()) {
                Text(
                    text = "No voters yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(voters) { voter ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (voter.displayName.ifEmpty { voter.username }).ifEmpty { "User" },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (voter.username.isNotEmpty() && voter.displayName.isNotEmpty()) {
                                Text(
                                    text = "@${voter.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
