package org.enchant.chat

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.enchant.chat.components.EmojiPickerSheet
import org.enchant.chat.components.MediaViewerScreen
import org.enchant.core.model.DisappearTimerPresets
import org.enchant.core.model.Message
import org.enchant.core.model.MessageStatus
import org.enchant.stickers.StickerPicker
import org.enchant.stickers.StickerViewModel
import org.enchant.location.LocationPickerScreen
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
    val title by viewModel.title.collectAsState()
    val senderNames by viewModel.senderNames.collectAsState()
    val typingIndicator by viewModel.typingIndicator.collectAsState()
    val sendingState by viewModel.sendingState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messageText by remember { mutableStateOf("") }
    var replyToId by remember { mutableStateOf<String?>(null) }
    var viewOnceMode by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDisappearDialog by remember { mutableStateOf(false) }
    var showAttachments by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showStickerPicker by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var showContactShareDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteEnvelopeId by remember { mutableStateOf("") }
    var deleteForEveryone by remember { mutableStateOf(false) }
    var forwardDialogMessageId by remember { mutableStateOf<String?>(null) }
    var showContactInfo by remember { mutableStateOf(false) }
    var contactShareUserId by remember { mutableStateOf("") }
    var translateDialogEnvelopeId by remember { mutableStateOf<String?>(null) }

    val pinnedMessages by viewModel.pinnedMessages.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val translatedMessage by viewModel.translatedMessage.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    val stickerVM: StickerViewModel = viewModel()
    val stickerState by stickerVM.uiState.collectAsState()

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(conversationId) {
        viewModel.init(conversationId)
        viewModel.loadPinnedMessages()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && listState.firstVisibleItemIndex < 2) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(Unit) { viewModel.loadConversations() }

    LaunchedEffect(translateDialogEnvelopeId) {
        translateDialogEnvelopeId?.let { envelopeId ->
            viewModel.translateMessage(envelopeId)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.sendFileMessage(it, "image", "image/*", viewOnceMode) }
    }

    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUri != null) {
            viewModel.sendFileMessage(cameraUri!!, "camera_photo", "image/jpeg", viewOnceMode)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                org.enchant.chat.data.MediaService.startRecording()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    (title ?: "?").take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = title ?: conversation?.id?.take(16) ?: "Chat",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (typingIndicator) {
                                TypingDots()
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { this.contentDescription = "Navigate back" }
                    ) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onStartCall(conversationId, false) },
                        modifier = Modifier.semantics { this.contentDescription = "Start audio call" }
                    ) {
                        Icon(Icons.Default.Call, "Call")
                    }
                    IconButton(
                        onClick = { onStartCall(conversationId, true) },
                        modifier = Modifier.semantics { this.contentDescription = "Start video call" }
                    ) {
                        Icon(Icons.Default.Videocam, "Video Call")
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.semantics { this.contentDescription = "More options" }
                    ) {
                        Icon(Icons.Default.MoreVert, "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("View contact") }, onClick = {
                            showContactInfo = true
                            showMenu = false
                        })
                        DropdownMenuItem(text = { Text("Search") }, onClick = { showSearch = true; showMenu = false })
                        DropdownMenuItem(text = { Text("Disappearing messages") }, onClick = { showDisappearDialog = true; showMenu = false })
                        DropdownMenuItem(text = { Text("Starred messages") }, onClick = { showMenu = false })
                        DropdownMenuItem(text = { Text("Pinned messages") }, onClick = { showMenu = false })
                    }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = conversationId,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            }
        ) { convId ->
            if (showContactInfo) {
                AlertDialog(
                    onDismissRequest = { showContactInfo = false },
                    title = { Text(title ?: "Contact") },
                    text = {
                        Column {
                            Text(
                                convId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onStartCall(convId, false) }) {
                                    Text("Voice call")
                                }
                                OutlinedButton(onClick = { onStartCall(convId, true) }) {
                                    Text("Video call")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val clipboard = clipboardManager
                            clipboard.setText(AnnotatedString(convId))
                            showContactInfo = false
                        }) {
                            Text("Copy ID")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showContactInfo = false }) {
                            Text("Close")
                        }
                    }
                )
            }
            Column(modifier = Modifier.padding(padding)) {
                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search messages") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = ""; showSearch = false },
                                    modifier = Modifier.semantics { this.contentDescription = "Clear search" }
                                ) {
                                    Icon(Icons.Default.Close, "Clear")
                                }
                            }
                        }
                    )
                    if (searchResults.isNotEmpty()) {
                        Text(
                            "${searchResults.size} results",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        LazyColumn(modifier = Modifier.height(200.dp)) {
                            items(searchResults, key = { it.envelopeId ?: it.localId.toString() }) { result ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.jumpToMessage(result.envelopeId ?: ""); showSearch = false },
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(result.content, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            formatTime(result.timestamp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else if (searchQuery.isNotEmpty()) {
                        Text(
                            "No results",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                if (pinnedMessages.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Pinned message",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                pinnedMessages.first().content.take(40),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
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
                        modifier = Modifier
                            .weight(1f)
                            .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                        state = listState,
                        reverseLayout = true
                    ) {
                        itemsIndexed(messages, key = { _, m -> m.localId }) { index, message ->
                            // List is newest-first; a day group's oldest message
                            // (the one followed by a different day) gets the
                            // separator above it.
                            val nextOlder = messages.getOrNull(index + 1)
                            if (index > 0 && nextOlder != null &&
                                formatDayKey(message.timestamp) != formatDayKey(nextOlder.timestamp)
                            ) {
                                DaySeparator(timestamp = message.timestamp)
                            }
                            MessageBubble(
                                message = message,
                                isOutgoing = message.senderId == org.enchant.core.base.SecurePreferences.getString("auth.user_id"),
                                senderName = senderNames[message.senderId],
                                onReply = { replyToId = it },
                                onDelete = {
                                    deleteEnvelopeId = it
                                    deleteForEveryone = false
                                    showDeleteConfirmDialog = true
                                },
                                onDeleteEveryone = {
                                    deleteEnvelopeId = it
                                    deleteForEveryone = true
                                    showDeleteConfirmDialog = true
                                },
                                onEdit = { envelopeId ->
                                    val newText = messageText.ifBlank { null }
                                    if (newText != null) {
                                        viewModel.editMessage(envelopeId, newText)
                                        messageText = ""
                                    }
                                },
                                onForward = { envelopeId -> forwardDialogMessageId = envelopeId },
                                onCopy = { viewModel.copyToClipboard(message.content) },
                                onReact = { viewModel.setReaction(message.localId, it) },
                                onReport = { viewModel.reportMessage(it) },
                                onTranslate = { translateDialogEnvelopeId = it },
                                onViewOnceViewed = { viewModel.markViewOnceViewed(it) },
                                onStar = { viewModel.starMessage(it, !message.isStarred) },
                                onPin = { viewModel.pinMessage(it) }
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
                    onTextChange = { messageText = it; viewModel.onComposerTextChanged(it) },
                    onSend = {
                        if (messageText.isNotBlank()) {
                            viewModel.sendTextMessage(messageText, replyToId)
                            messageText = ""
                            replyToId = null
                        }
                    },
                    onAttach = { showAttachments = true },
                    onEmoji = { showEmojiPicker = true },
                    onSticker = { showStickerPicker = true },
                    viewOnceMode = viewOnceMode,
                    onViewOnceToggle = { viewOnceMode = !viewOnceMode },
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
                showLocationPicker = true
            },
            onContact = {
                showAttachments = false
                showContactShareDialog = true
            }
        )
    }

    if (showLocationPicker) {
        LocationPickerScreen(
            onLocationSelected = { lat, lng, label ->
                viewModel.sendLocationMessage(lat, lng, label)
                showLocationPicker = false
            },
            onBack = { showLocationPicker = false }
        )
    }

    if (showContactShareDialog) {
        AlertDialog(
            onDismissRequest = { showContactShareDialog = false },
            title = { Text("Share Contact") },
            text = {
                Column {
                    Text("Enter the user ID to share:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = contactShareUserId,
                        onValueChange = { contactShareUserId = it },
                        label = { Text("User ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (contactShareUserId.isNotBlank()) {
                            viewModel.sendContactCard(contactShareUserId, conversationId)
                            contactShareUserId = ""
                            showContactShareDialog = false
                        }
                    },
                    enabled = contactShareUserId.isNotBlank()
                ) {
                    Text("Share")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    contactShareUserId = ""
                    showContactShareDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEmojiPicker) {
        EmojiPickerSheet(
            onEmojiSelected = { emoji ->
                // Insert into the composer so multiple emoji can be
                // combined before sending (Signal/WhatsApp behavior).
                messageText += emoji
                viewModel.onComposerTextChanged(messageText)
            },
            onDismiss = { showEmojiPicker = false }
        )
    }

    if (showStickerPicker) {
        StickerPicker(
            library = stickerState.library,
            recent = stickerState.recent,
            onStickerSelected = { packId, stickerId ->
                viewModel.sendSticker(packId, stickerId)
                stickerVM.recordStickerUse(packId, stickerId)
                showStickerPicker = false
            },
            onDismiss = { showStickerPicker = false },
            onLoadLibrary = { stickerVM.loadLibrary() },
            onLoadRecent = { stickerVM.loadRecent() }
        )
    }

    if (showDisappearDialog) {
        val currentTimer = conversation?.disappearTimerSeconds ?: 0
        AlertDialog(
            onDismissRequest = { showDisappearDialog = false },
            title = { Text("Disappearing messages") },
            text = {
                Column {
                    Text(
                        "Messages that disappear after a set time. Current: ${DisappearTimerPresets.formatDuration(currentTimer)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DisappearTimerPresets.CONVERSATION_OPTIONS.forEach { option ->
                        val isSelected = currentTimer == option.seconds
                        Surface(
                            onClick = {
                                viewModel.setDisappearTimer(conversationId, option.seconds)
                                showDisappearDialog = false
                            },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(option.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (forwardDialogMessageId != null) {
        AlertDialog(
            onDismissRequest = { forwardDialogMessageId = null },
            title = { Text("Forward to...") },
            text = {
                Column {
                    if (conversations.isEmpty()) {
                        Text("No conversations")
                    } else {
                        conversations.forEach { conv ->
                            TextButton(onClick = {
                                forwardDialogMessageId?.let { id ->
                                    viewModel.forwardMessage(id, conv.id)
                                }
                                forwardDialogMessageId = null
                            }) { Text(conv.id.take(16)) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { forwardDialogMessageId = null }) { Text("Cancel") }
            }
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

    if (translateDialogEnvelopeId != null) {
        val translated = translatedMessage?.second
        AlertDialog(
            onDismissRequest = {
                translateDialogEnvelopeId = null
                viewModel.clearTranslation()
            },
            title = { Text("Translation") },
            text = {
                Column {
                    if (translated != null) {
                        Text(translated)
                    } else {
                        Text("Translating...")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    translated?.let { viewModel.copyToClipboard(it) }
                    translateDialogEnvelopeId = null
                    viewModel.clearTranslation()
                }) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    translateDialogEnvelopeId = null
                    viewModel.clearTranslation()
                }) {
                    Text("Close")
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(if (deleteForEveryone) "Delete for everyone" else "Delete message") },
            text = {
                Text(
                    if (deleteForEveryone) "This message will be deleted for everyone in the conversation."
                    else "This message will be deleted from your device.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (deleteForEveryone) {
                            viewModel.deleteMessageForEveryone(deleteEnvelopeId)
                        } else {
                            viewModel.deleteMessage(deleteEnvelopeId, false)
                        }
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
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
IconButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { this.contentDescription = "Dismiss reply" }
            ) {
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
    onSticker: () -> Unit = {},
    viewOnceMode: Boolean = false,
    onViewOnceToggle: () -> Unit = {},
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit
) {
    // WhatsApp/Signal composer: [attach] [field] [emoji] [send] — the layout
    // never changes, nothing appears or disappears while typing.
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onAttach,
                modifier = Modifier.semantics { this.contentDescription = "Attach file" }
            ) {
                Icon(Icons.Default.AttachFile, "Attach")
            }

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                maxLines = 5,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send,
                    autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            IconButton(
                onClick = onEmoji,
                modifier = Modifier.semantics { this.contentDescription = "Open emoji picker" }
            ) {
                Icon(Icons.Default.EmojiEmotions, "Emoji")
            }

            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank(),
                modifier = Modifier.semantics { this.contentDescription = "Send message" }
            ) {
                Icon(
                    Icons.Default.Send,
                    "Send",
                    tint = if (text.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isOutgoing: Boolean,
    senderName: String? = null,
    onReply: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteEveryone: (String) -> Unit,
    onEdit: (String) -> Unit,
    onForward: (String) -> Unit,
    onCopy: (String) -> Unit,
    onReact: (String) -> Unit,
    onReport: (String) -> Unit = {},
    onTranslate: (String) -> Unit = {},
    onStar: (Long) -> Unit = {},
    onPin: (Long) -> Unit = {},
    onViewOnceViewed: (String) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val bubbleColor = if (isOutgoing) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    val alignment = if (isOutgoing) Arrangement.End else Arrangement.Start
    var viewOnceRevealed by remember { mutableStateOf(false) }
    var viewOnceCountdown by remember { mutableIntStateOf(0) }

    val showSenderMeta = !isOutgoing && senderName != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        if (showSenderMeta) {
            // WhatsApp-style: avatar + sender name above the incoming bubble.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(24.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            senderName.take(1).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    senderName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
        Surface(
            // Signal bubble: 18dp radius, only the tail corner (bottom,
            // sender side) is tight at 4dp.
            shape = if (isOutgoing) {
                RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = 18.dp, bottomEnd = 4.dp
                )
            } else {
                RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = 4.dp, bottomEnd = 18.dp
                )
            },
            color = bubbleColor,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(
                    onClick = { },
                    onLongClick = { showMenu = true }
                )
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                    if (message.isDeleted) {
                        Text(
                            "This message was deleted",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    } else if (message.mediaMimeType != null && !message.isViewOnce) {
                    val mimeType = message.mediaMimeType
                    if (mimeType != null && mimeType.startsWith("audio/")) {
                        VoiceMessageContent(
                            mediaMimeType = mimeType,
                            mediaSize = message.mediaSize,
                            content = message.content
                        )
                    } else {
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
                    }
                } else {
                    val content = message.content
                    val urlPattern = Regex("https?://[^\\s]+")
                    val urls = urlPattern.findAll(content).map { it.value }.toList()
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (urls.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    urls.first().take(60),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
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

                    val disappearAt = message.disappearAt
                    if (disappearAt != null && disappearAt > 0) {
                        val remaining = DisappearTimerPresets.formatTimeRemaining(disappearAt)
                        if (remaining != "Expired") {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = "Disappears in $remaining",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                remaining,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    if (message.isEdited) {
                        Text(
                            "edited",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (message.isViewOnce) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = "View once",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            "View once",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
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

                if (message.reactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        message.reactions.groupBy { it.emoji }.entries.forEach { (emoji, reactors) ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.height(24.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(emoji, style = MaterialTheme.typography.labelSmall)
                                    if (reactors.size > 1) {
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            reactors.size.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE0E", "\uD83D\uDE22", "\uD83D\uDE4F")
                    .forEach { emoji ->
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onReact(emoji); showMenu = false }
                                .padding(8.dp)
                        )
                    }
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Copy") }, onClick = { onCopy(message.envelopeId ?: ""); showMenu = false })
            DropdownMenuItem(text = { Text("Reply") }, onClick = { onReply(message.envelopeId ?: ""); showMenu = false })
            if (isOutgoing) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { onEdit(message.envelopeId ?: ""); showMenu = false })
            }
            DropdownMenuItem(text = { Text("Forward") }, onClick = { onForward(message.envelopeId ?: ""); showMenu = false })
            DropdownMenuItem(
                text = { Text(if (message.isStarred) "Unstar" else "Star") },
                onClick = { onStar(message.localId); showMenu = false },
                leadingIcon = { Icon(if (message.isStarred) Icons.Default.Star else Icons.Default.StarBorder, null) }
            )
            DropdownMenuItem(
                text = { Text("Pin") },
                onClick = { onPin(message.localId); showMenu = false },
                leadingIcon = { Icon(Icons.Default.PushPin, null) }
            )
            if (isOutgoing) {
                DropdownMenuItem(text = { Text("Delete for everyone") }, onClick = { onDeleteEveryone(message.envelopeId ?: ""); showMenu = false })
            }
            DropdownMenuItem(text = { Text("Report") }, onClick = { onReport(message.envelopeId ?: ""); showMenu = false })
            DropdownMenuItem(text = { Text("Translate") }, onClick = { onTranslate(message.envelopeId ?: ""); showMenu = false })
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
    onLocation: () -> Unit,
    onContact: () -> Unit = {}
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
                AttachmentButton(Icons.Default.Person, "Contact", onContact)
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

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                modifier = Modifier
                    .padding(end = 3.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun DaySeparator(timestamp: Long) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        ) {
            Text(
                formatDayLabel(timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

private fun formatDayKey(timestamp: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
}

private fun formatDayLabel(timestamp: Long): String {
    val now = java.util.Calendar.getInstance()
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    return when {
        now.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR) &&
            now.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR) -> "Today"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(timestamp)
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

@Composable
private fun VoiceMessageContent(
    mediaMimeType: String,
    mediaSize: Long?,
    content: String
) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { isPlaying = !isPlaying }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.small),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "0:00",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (mediaSize != null) {
            Text(
                formatFileSize(mediaSize),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
