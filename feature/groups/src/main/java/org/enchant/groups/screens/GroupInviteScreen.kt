package org.enchant.groups.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private val BrandPrimaryLight = Color(0xFF3A0D6E)
private val BrandPrimaryDark = Color(0xFFB388E3)

@Composable
private fun brandPrimary(): Color = if (isSystemInDarkTheme()) BrandPrimaryDark else BrandPrimaryLight

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
                title = {
                    Text("Invite to Group", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = brandPrimary().copy(alpha = 0.12f)) {
                Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = brandPrimary()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Share invite link",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "People can join your group using this link",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (inviteLink.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Text(
                            inviteLink,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(16.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onCopyLink,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandPrimary(),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy link", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onShareLink)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp), tint = brandPrimary())
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share", style = MaterialTheme.typography.labelLarge, color = brandPrimary(), fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(24.dp))
            }

            Text(
                "QR Code",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier.size(200.dp)
            ) {
                Canvas(modifier = Modifier.padding(16.dp)) {
                    drawQrCode(qrPattern)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Join via code",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
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
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = brandPrimary(),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50)
            ) {
                Text("Join Group", fontWeight = FontWeight.SemiBold)
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
