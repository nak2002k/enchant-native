package org.enchant.calls.calllinks

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.enchant.core.calls.CallLinkData
import org.enchant.core.calls.model.CallLinkRestrictions

private val JewelPurpleLight = Color(0xFF3A0D6E)
private val JewelPurpleDark = Color(0xFFB388E3)
private val GradientTintLight = Color(0xFF7B1FA2)
private val GradientTintDark = Color(0xFFAB47BC)

@Composable
private fun brandPurple(): Color = if (isSystemInDarkTheme()) JewelPurpleDark else JewelPurpleLight

@Composable
private fun heroGradient(): Brush = if (isSystemInDarkTheme()) {
    Brush.linearGradient(listOf(GradientTintDark, JewelPurpleDark))
} else {
    Brush.linearGradient(listOf(JewelPurpleLight, GradientTintLight))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLinkScreen(
    callLink: CallLinkData?,
    isOwner: Boolean,
    isLoading: Boolean,
    error: String?,
    onJoinCall: () -> Unit,
    onEditName: (String) -> Unit,
    onDelete: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Call Link",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = brandPurple())
                }
                return@Column
            }

            if (error != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                return@Column
            }

            val link = callLink ?: return@Column
            val linkUrl = "enchant://call-link/${link.roomId}"
            var requireApproval by remember(link) {
                mutableStateOf(link.restrictions == CallLinkRestrictions.APPROVAL_REQUIRED)
            }
            var adminsOnly by remember(link) { mutableStateOf(true) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(heroGradient())
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        link.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    SelectionContainer {
                        Text(
                            linkUrl,
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Call link", linkUrl))
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy link", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Join my Enchant call: $linkUrl")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Link"))
                            },
                            shape = CircleShape,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "Join settings",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp)
            )
            Spacer(Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Require approval",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = requireApproval,
                            onCheckedChange = { requireApproval = it },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = brandPurple(),
                                checkedThumbColor = Color.White,
                                checkedBorderColor = brandPurple()
                            )
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Only admins can edit",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = adminsOnly,
                            onCheckedChange = { adminsOnly = it },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = brandPurple(),
                                checkedThumbColor = Color.White,
                                checkedBorderColor = brandPurple()
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onJoinCall,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = CircleShape,
                enabled = link.isActive,
                colors = ButtonDefaults.buttonColors(
                    containerColor = brandPurple(),
                    contentColor = Color.White,
                    disabledContainerColor = brandPurple().copy(alpha = 0.4f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                )
            ) {
                Icon(Icons.Default.Call, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start call", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            if (!link.isActive) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "This link is no longer active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (isOwner) {
                Spacer(Modifier.height(28.dp))
                Text(
                    "Admin",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = { onEditName(link.name) })
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = brandPurple()
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Edit Name", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.Default.ChevronRight,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(start = 48.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onDelete)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = Color(0xFFFF3B30)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Delete Link",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFFFF3B30)
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.Default.ChevronRight,
                                null,
                                tint = Color(0xFFFF3B30).copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
