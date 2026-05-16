package org.enchant.chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.enchant.chat.components.EmojiPickerSheet
import org.enchant.chat.components.MediaViewerScreen
import org.enchant.core.model.Message
import org.enchant.core.model.MessageStatus
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    onStartCall: (userId: String, isVideo: Boolean) -> Unit = { _, _ -> },
    viewModel: ConversationViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val conversation by viewModel.conversation.collectAsState()
    val typingIndicator by viewModel.typingIndicator.collectAsState()
    val sendingState by viewModel.sendingState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var messageText by remember { mutableStateOf("") }
    var replyToId by remember { mutableStateOf<String?>(null) }
    var showAttachments by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    LaunchedEffect(conversationId) { viewModel.init(conversationId) }

    DisposableEffect(Unit) {
        val activity = context as? androidx.activity.ComponentActivity
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && listState.firstVisibleItemIndex < 2) {
            listState.animateScrollToItem(0)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.sendMediaMessage(it, "image/*") }
    }

    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUri != null) {
            viewModel.sendMediaMessage(cameraUri!!, "image/jpeg")
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                val _unused = org.enchant.chat.data.MediaService.startRecording()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = conversation?.id?.take(16) ?: "Chat",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (typingIndicator) {
                            Text(
                                "typing...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Call, "Call")
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Videocam, "Video Call")
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("View contact") }, onClick = { showMenu = false })
                        DropdownMenuItem(text = { Text("Search") }, onClick = { showMenu = false })
                        DropdownMenuItem(text = { Text("Disappearing messages") }, onClick = { showMenu = false })
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "Messages are end-to-end encrypted. Tap for more info.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    reverseLayout = true
                ) {
                    items(messages, key = { it.localId }) { message ->
                        MessageBubble(
                            message = message,
                            isOutgoing = message.senderId == org.enchant.core.base.SecurePreferences.getString("auth.user_id"),
                            onReply = { replyToId = it },
                            onDelete = { viewModel.deleteMessage(it, false) },
                            onDeleteEveryone = { viewModel.deleteMessage(it, true) },
                            onEdit = { },
                            onForward = { viewModel.forwardMessage(it, "") },
                            onCopy = { viewModel.copyToClipboard(message.content) },
                            onReact = { viewModel.setReaction(message.localId, it) }
                        )
                    }
                }
            }

            sendingState?.let { state ->
                when (state) {
                    SendState.SENDING -> Text("Sending...", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                    SendState.UPLOADING -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    SendState.FAILED -> Text("Failed to send", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                    else -> {}
                }
            }

            AnimatedVisibility(visible = replyToId != null) {
                ReplyPreview(
                    message = messages.find { it.replyToEnvelopeId == replyToId }?.content ?: "",
                    onDismiss = { replyToId = null }
                )
            }

            ComposerBar(
                text = messageText,
                onTextChange = { messageText = it },
                onSend = {
                    if (messageText.isNotBlank()) {
                        viewModel.sendTextMessage(messageText, replyToId)
                        messageText = ""
                        replyToId = null
                    }
                },
                onAttach = { showAttachments = true },
                onEmoji = { showEmojiPicker = true },
                onVoiceStart = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            scope.launch {
                                org.enchant.chat.data.MediaService.startRecording()
                            }
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                onVoiceStop = {
                    scope.launch {
                        val file = org.enchant.chat.data.MediaService.stopRecording()
                        if (file != null) {
                            viewModel.sendVoiceMessage(file, 0)
                        }
                    }
                }
            )
        }
    }

    if (showAttachments) {
        AttachmentSheet(
            onDismiss = { showAttachments = false },
            onGallery = {
                showAttachments = false
                imagePickerLauncher.launch("image/*")
            },
            onCamera = {
                showAttachments = false
                val photoFile = createTempFile(context, "photo_", ".jpg")
                cameraUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                cameraLauncher.launch(cameraUri!!)
            },
            onDocument = {
                showAttachments = false
                val intent = org.enchant.chat.data.MediaService.pickDocument()
                context.startActivity(intent)
            },
            onLocation = {
                showAttachments = false
            }
        )
    }

    if (showEmojiPicker) {
        EmojiPickerSheet(
            onEmojiSelected = { emoji ->
                viewModel.sendTextMessage(emoji)
                showEmojiPicker = false
            },
            onDismiss = { showEmojiPicker = false }
        )
    }

    var mediaViewerPath by remember { mutableStateOf<String?>(null) }
    mediaViewerPath?.let { path ->
        MediaViewerScreen(
            mediaPath = path,
            mimeType = "image/*",
            onDismiss = { mediaViewerPath = null }
        )
    }
}



@Composable
private fun ReplyPreview(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Reply", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(message.take(80), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Dismiss reply")
            }
        }
    }
}

@Composable
private fun ComposerBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onEmoji: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttach) {
                Icon(Icons.Default.Add, "Attach")
            }

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.surface
                )
            )

            if (text.isBlank()) {
                var isPressed by remember { mutableStateOf(false) }
                IconButton(
                    onClick = {
                        if (!isPressed) {
                            isPressed = true
                            onVoiceStart()
                        } else {
                            isPressed = false
                            onVoiceStop()
                        }
                    }
                ) {
                    Icon(
                        if (isPressed) Icons.Default.Mic else Icons.Default.Mic,
                        "Voice message",
                        tint = if (isPressed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onEmoji) {
                    Icon(Icons.Default.EmojiEmotions, "Emoji")
                }
            } else {
                IconButton(onClick = onSend) {
                    Icon(Icons.Default.Send, "Send")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isOutgoing: Boolean,
    onReply: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteEveryone: (String) -> Unit,
    onEdit: (String) -> Unit,
    onForward: (String) -> Unit,
    onCopy: (String) -> Unit,
    onReact: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val bubbleColor = if (isOutgoing) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    val alignment = if (isOutgoing) Arrangement.End else Arrangement.Start

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (message.mediaMimeType != null) {
                    Text(
                        "📎 ${message.mediaMimeType}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val size = message.mediaSize
                    if (size != null) {
                        Text(
                            formatFileSize(size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        formatTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (message.isEdited) {
                        Text(
                            "edited",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isOutgoing) {
                        Icon(
                            imageVector = when (message.status) {
                                MessageStatus.SENDING -> Icons.Default.AccessTime
                                MessageStatus.SENT -> Icons.Default.Check
                                MessageStatus.DELIVERED -> Icons.Default.DoneAll
                                MessageStatus.READ -> Icons.Default.DoneAll
                                else -> Icons.Default.AccessTime
                            },
                            contentDescription = message.status.name,
                            modifier = Modifier.size(14.dp),
                            tint = if (message.status == MessageStatus.READ) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Copy") }, onClick = { onCopy(message.envelopeId ?: ""); showMenu = false })
            DropdownMenuItem(text = { Text("Reply") }, onClick = { onReply(message.envelopeId ?: ""); showMenu = false })
            if (isOutgoing) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { onEdit(message.envelopeId ?: ""); showMenu = false })
            }
            DropdownMenuItem(text = { Text("Forward") }, onClick = { onForward(message.envelopeId ?: ""); showMenu = false })
            if (isOutgoing) {
                DropdownMenuItem(text = { Text("Delete for everyone") }, onClick = { onDeleteEveryone(message.envelopeId ?: ""); showMenu = false })
            }
            DropdownMenuItem(text = { Text("Delete") }, onClick = { onDelete(message.envelopeId ?: ""); showMenu = false })
        }
    }
}

@Composable
private fun AttachmentSheet(
    onDismiss: () -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onDocument: () -> Unit,
    onLocation: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Attach") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AttachmentButton(Icons.Default.Photo, "Gallery", onGallery)
                AttachmentButton(Icons.Default.CameraAlt, "Camera", onCamera)
                AttachmentButton(Icons.Default.Description, "Document", onDocument)
                AttachmentButton(Icons.Default.LocationOn, "Location", onLocation)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AttachmentButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(icon, label, modifier = Modifier.padding(12.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}

private fun formatTime(timestamp: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val min = cal.get(java.util.Calendar.MINUTE)
    return "${hour.toString().padStart(2, '0')}:${min.toString().padStart(2, '0')}"
}

private fun createTempFile(context: android.content.Context, prefix: String, suffix: String): File {
    val dir = File(context.cacheDir, "media_temp")
    dir.mkdirs()
    return File.createTempFile(prefix, suffix, dir)
}
