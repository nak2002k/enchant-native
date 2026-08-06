package org.enchant.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.enchant.ui.icons.EnchantIcons

/** Pill composer: [attach] [field] [view-once] [emoji] [send/mic]. */
@Composable
internal fun ComposerBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onEmoji: () -> Unit,
    viewOnceMode: Boolean = false,
    onViewOnceToggle: () -> Unit = {},
    onVoiceStart: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
) {
    val composerInteraction = remember { MutableInteractionSource() }
    val isComposerFocused by composerInteraction.collectIsFocusedAsState()
    val composerBorderColor by animateColorAsState(
        targetValue = if (isComposerFocused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(durationMillis = 180),
        label = "composerBorderColor",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (isComposerFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, composerBorderColor),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(
                    icon = EnchantIcons.plusCircle,
                    description = "Attach file",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onAttach,
                )
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        interactionSource = composerInteraction,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(EnchantBrand.SignalBlue),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Send,
                            autoCorrectEnabled = false,
                        ),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.padding(vertical = EnchantSpacing.sm)) {
                                if (text.isEmpty()) {
                                    Text(
                                        "Message",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
                RoundIconButton(
                    icon = EnchantIcons.clock,
                    description = "View once mode",
                    tint = if (viewOnceMode) EnchantBrand.SignalBlue
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    onClick = onViewOnceToggle,
                )
                RoundIconButton(
                    icon = EnchantIcons.smile,
                    description = "Open emoji picker",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onEmoji,
                )
                SendButton(
                    canSend = text.isNotBlank(),
                    onSend = onSend,
                    onVoiceStart = onVoiceStart,
                    onVoiceStop = onVoiceStop,
                )
            }
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Circular send button: arrow-up when text is present, mic (hold to record) when empty. */
@Composable
private fun SendButton(
    canSend: Boolean,
    onSend: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = EnchantMotion.springBouncy,
        label = "sendScale",
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (canSend) EnchantBrand.SignalBlue
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics {
                contentDescription = if (canSend) "Send message" else "Hold to record voice message"
            }
            .pointerInput(canSend) {
                detectTapGestures(
                    onTap = {
                        if (canSend) {
                            pressed = true
                            onSend()
                            pressed = false
                        }
                    },
                    onPress = {
                        if (!canSend) {
                            pressed = true
                            onVoiceStart()
                            tryAwaitRelease()
                            onVoiceStop()
                            pressed = false
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (canSend) EnchantIcons.arrowUp else EnchantIcons.mic,
            contentDescription = null,
            tint = if (canSend) Color.White
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Attachment sheet: circular icon tiles in a grid (Gallery/Camera/File/Location/Poll/Contact/Sticker). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachmentSheet(
    onDismiss: () -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onDocument: () -> Unit,
    onLocation: () -> Unit,
    onContact: () -> Unit = {},
    onPoll: () -> Unit = {},
    onSticker: () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
    ) {
        Text(
            "Attach",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = EnchantSpacing.xl, end = EnchantSpacing.xl, bottom = EnchantSpacing.sm),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EnchantSpacing.xl, vertical = EnchantSpacing.md)
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AttachmentCell(EnchantIcons.image, "Gallery", Color(0xFF34C759), onGallery)
                AttachmentCell(EnchantIcons.camera, "Camera", Color(0xFF3A0D6E), onCamera)
                AttachmentCell(EnchantIcons.file, "File", Color(0xFF5856D6), onDocument)
                AttachmentCell(EnchantIcons.mapPin, "Location", Color(0xFFFF9500), onLocation)
            }
            Spacer(Modifier.height(EnchantSpacing.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AttachmentCell(EnchantIcons.poll, "Poll", Color(0xFFFF2D55), onPoll)
                AttachmentCell(EnchantIcons.user, "Contact", Color(0xFFFF3B30), onContact)
                AttachmentCell(EnchantIcons.sparkles, "Sticker", Color(0xFFAF52DE), onSticker)
                Spacer(Modifier.size(54.dp))
            }
            Spacer(Modifier.height(EnchantSpacing.sm))
        }
    }
}

@Composable
private fun AttachmentCell(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
