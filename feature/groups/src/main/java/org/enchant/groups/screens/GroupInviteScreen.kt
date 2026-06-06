package org.enchant.groups.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInviteScreen(
    inviteLink: String,
    onCopyLink: () -> Unit,
    onShareLink: () -> Unit,
    onJoinViaCode: (String) -> Unit,
    onBack: () -> Unit = {}
) {
    var joinCode by remember { mutableStateOf("") }
    val qrPattern = remember(inviteLink) { generateQrPattern(inviteLink) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invite to Group") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Share invite link",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "People can join your group using this link",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (inviteLink.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            inviteLink,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopyLink,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy link")
                    }

                    Button(
                        onClick = onShareLink,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share")
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(32.dp))
            }

            Text(
                "QR Code",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier.size(200.dp),
                shadowElevation = 2.dp
            ) {
                Canvas(modifier = Modifier.padding(16.dp)) {
                    drawQrCode(qrPattern)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Join via code",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = joinCode,
                onValueChange = { if (it.length <= 64) joinCode = it },
                label = { Text("Enter invite code") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (joinCode.isNotEmpty()) {
                        IconButton(onClick = { joinCode = "" }) {
                            Icon(Icons.Default.Close, "Clear")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onJoinViaCode(joinCode) },
                enabled = joinCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Join Group")
            }
        }
    }
}

private fun generateQrPattern(data: String): Array<BooleanArray> {
    val size = 25
    val pattern = Array(size) { BooleanArray(size) }
    val hash = data.hashCode().toLong()

    // Fixed patterns (finder patterns in corners)
    for (i in 0..6) {
        for (j in 0..6) {
            pattern[i][j] = i == 0 || i == 6 || j == 0 || j == 6 ||
                (i in 2..4 && j in 2..4)
            pattern[i][size - 7 + j] = i == 0 || i == 6 || j == 0 || j == 6 ||
                (i in 2..4 && j in 2..4)
            pattern[size - 7 + i][j] = i == 0 || i == 6 || j == 0 || j == 6 ||
                (i in 2..4 && j in 2..4)
        }
    }

    // Timing patterns
    for (i in 8 until size - 8) {
        pattern[6][i] = i % 2 == 0
        pattern[i][6] = i % 2 == 0
    }

    // Data area (pseudo-random based on hash)
    val rng = Random(hash)
    for (i in 0 until size) {
        for (j in 0 until size) {
            if (!pattern[i][j] && i > 7 && j > 7 && !(i < 9 && j > size - 9) && !(i > size - 9 && j < 9)) {
                pattern[i][j] = rng.nextBoolean()
            }
        }
    }

    return pattern
}

private fun DrawScope.drawQrCode(pattern: Array<BooleanArray>) {
    val size = pattern.size
    val cellSize = this.size.minDimension / size
    val offsetX = (this.size.width - cellSize * size) / 2
    val offsetY = (this.size.height - cellSize * size) / 2

    for (i in 0 until size) {
        for (j in 0 until size) {
            if (pattern[i][j]) {
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(offsetX + j * cellSize, offsetY + i * cellSize),
                    size = Size(cellSize, cellSize)
                )
            }
        }
    }
}
