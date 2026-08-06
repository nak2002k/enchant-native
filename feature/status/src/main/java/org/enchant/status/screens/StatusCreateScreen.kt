package org.enchant.status.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.enchant.status.StatusPrivacy
import org.enchant.ui.icons.EnchantIcons

private val StoryBlack = Color(0xFF000000)
private val StoryElevated = Color(0xFF1C1C1E)
private val CaptionHint = Color.White.copy(alpha = 0.4f)

private val presetColors = listOf(
    "#FF6B6B", "#FFA94D", "#FFD43B", "#69DB7C",
    "#38D9A9", "#4DABF7", "#748FFC", "#DA77F2",
    "#F06595", "#495057", "#212529", "#FFF"
)

@Composable
fun StatusCreateScreen(
    onCreateText: (String, String, StatusPrivacy) -> Unit,
    onCreateMedia: (String, StatusPrivacy) -> Unit,
    onBack: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(presetColors[0]) }
    var selectedPrivacy by remember { mutableStateOf<StatusPrivacy>(StatusPrivacy.AllContacts) }
    var showPrivacyDropdown by remember { mutableStateOf(false) }

    val canPost = text.isNotBlank() && text.length <= 700
    val previewTextColor = if (selectedColor == "#FFF") Color.Black else Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StoryBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(EnchantIcons.arrowLeft, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    "New story",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onCreateText(text, selectedColor, selectedPrivacy) },
                    enabled = canPost,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.White.copy(alpha = 0.4f),
                        disabledContentColor = Color.Black.copy(alpha = 0.5f)
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 7.dp)
                ) {
                    Text(
                        "Post",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = parseColor(selectedColor),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text.ifBlank { "Type a status..." },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = previewTextColor,
                                textAlign = TextAlign.Center,
                                maxLines = 10,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (text.isNotBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    "${text.length}/700",
                                    fontSize = 12.sp,
                                    color = previewTextColor.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            TextField(
                value = text,
                onValueChange = { if (it.length <= 700) text = it },
                placeholder = { Text("Add a caption…", color = CaptionHint) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White,
                    textAlign = TextAlign.Center
                ),
                maxLines = 3,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                presetColors.forEach { colorHex ->
                    val isSelected = colorHex == selectedColor
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(parseColor(colorHex))
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = Color.White,
                                shape = CircleShape
                            )
                            .clickable { selectedColor = colorHex }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onCreateMedia("", selectedPrivacy) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(
                        EnchantIcons.image,
                        contentDescription = "Pick from gallery",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .clickable { onCreateMedia("", selectedPrivacy) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }

                Box {
                    OutlinedButton(
                        onClick = { showPrivacyDropdown = true },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, Color.White.copy(alpha = 0.25f)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            EnchantIcons.eye,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            when (selectedPrivacy) {
                                StatusPrivacy.AllContacts -> "All"
                                is StatusPrivacy.Selected -> "Selected"
                                StatusPrivacy.CloseFriends -> "Close"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    DropdownMenu(
                        expanded = showPrivacyDropdown,
                        onDismissRequest = { showPrivacyDropdown = false },
                        containerColor = StoryElevated
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Contacts", color = Color.White) },
                            onClick = { selectedPrivacy = StatusPrivacy.AllContacts; showPrivacyDropdown = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Selected Contacts (coming soon)", color = Color.White) },
                            onClick = { showPrivacyDropdown = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Close Friends", color = Color.White) },
                            onClick = { selectedPrivacy = StatusPrivacy.CloseFriends; showPrivacyDropdown = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Cancel", color = Color.White.copy(alpha = 0.6f)) },
                            onClick = { showPrivacyDropdown = false }
                        )
                    }
                }
            }
        }
    }
}

private fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color.Gray
    }
}
