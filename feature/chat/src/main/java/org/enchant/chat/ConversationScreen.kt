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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.enchant.chat.components.AttachmentSheet
import org.enchant.chat.components.ComposerBar
import org.enchant.chat.components.ConversationHeader
import org.enchant.chat.components.DateChip
import org.enchant.chat.components.EmojiPickerSheet
import org.enchant.chat.components.EnchantMotion
import org.enchant.chat.components.EnchantRadii
import org.enchant.chat.components.EnchantSpacing
import org.enchant.chat.components.MediaViewerScreen
import org.enchant.chat.components.MessageBubble
import org.enchant.chat.components.TypingBubble
import org.enchant.chat.components.formatFileSize
import org.enchant.chat.components.formatTime
import org.enchant.core.model.ConversationType
import org.enchant.core.model.DisappearTimerPresets
import org.enchant.core.model.Message
import org.enchant.core.model.MessageStatus
import org.enchant.stickers.StickerPicker
import org.enchant.stickers.StickerViewModel
import org.enchant.location.LocationPickerScreen
import org.enchant.ui.icons.EnchantIcons
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
    val peerVerified by viewModel.isPeerVerified.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // Reading older messages: show the jump-to-bottom FAB once scrolled up >400px.
    val showScrollToBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 400
        }
    }

    var messageText by remember { mutableStateOf("") }
    var replyToId by remember { mutableStateOf<String?>(null) }
    var viewOnceMode by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDisappearDialog by remember { mutableStateOf(false) }
    var showSafetyNumber by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<Message?>(null) }
    var showPollDialog by remember { mutableStateOf(false) }
    var pollQuestion by remember { mutableStateOf("") }
    var pollOptions by remember { mutableStateOf(listOf("", "")) }
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

    // Selection mode state
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val enterSelection: (Long) -> Unit = { id ->
        selectionMode = true
        selectedIds = selectedIds + id
    }
    val toggleSelection: (Long) -> Unit = { id ->
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
        if (selectedIds.isEmpty()) selectionMode = false
    }
    val exitSelection = {
        selectionMode = false
        selectedIds = emptySet()
    }

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

    val isGroupChat = conversation?.type != ConversationType.DIRECT
    val selfUserId = org.enchant.core.base.SecurePreferences.getString("auth.user_id")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AnimatedContent(
            targetState = conversationId,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            }
        ) { convId ->
            Column(modifier = Modifier.fillMaxSize()) {
                ConversationHeader(
                    title = title,
                    conversation = conversation,
                    typingIndicator = typingIndicator,
                    isPeerVerified = peerVerified,
                    isSelectionMode = selectionMode,
                    selectionCount = selectedIds.size,
                    onBack = onNavigateBack,
                    onCloseSelection = { exitSelection() },
                    onCopySelection = {
                        selectedIds.firstOrNull()?.let { id ->
                            messages.find { it.localId == id }?.let { viewModel.copyToClipboard(it.content) }
                        }
                        exitSelection()
                    },
                    onForwardSelection = {
                        selectedIds.firstOrNull()?.let { id ->
                            messages.find { it.localId == id }?.let { forwardDialogMessageId = it.envelopeId }
                        }
                        exitSelection()
                    },
                    onDeleteSelection = {
                        selectedIds.firstOrNull()?.let { id ->
                            messages.find { it.localId == id }?.let { m ->
                                deleteEnvelopeId = m.envelopeId ?: ""
                                deleteForEveryone = false
                                showDeleteConfirmDialog = true
                            }
                        }
                        exitSelection()
                    },
                    onSafetyNumber = { showSafetyNumber = true },
                    onAudioCall = { onStartCall(conversationId, false) },
                    onVideoCall = { onStartCall(conversationId, true) },
                    onViewContact = { showContactInfo = true },
                    onSearch = { showSearch = true },
                    onDisappear = { showDisappearDialog = true },
                    onStarred = {},
                    onPinned = {},
                )
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
                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search messages") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = EnchantSpacing.lg, vertical = 4.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        leadingIcon = { Icon(EnchantIcons.search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = ""; showSearch = false },
                                    modifier = Modifier.semantics { this.contentDescription = "Clear search" }
                                ) {
                                    Icon(EnchantIcons.x, "Clear")
                                }
                            }
                        }
                    )
                    if (searchResults.isNotEmpty()) {
                        Text(
                            "${searchResults.size} results",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = EnchantSpacing.lg, vertical = 4.dp)
                        )
                        LazyColumn(modifier = Modifier.height(200.dp)) {
                            items(searchResults, key = { it.envelopeId ?: it.localId.toString() }) { result ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = EnchantSpacing.lg, vertical = 2.dp)
                                        .clickable { viewModel.jumpToMessage(result.envelopeId ?: ""); showSearch = false },
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(EnchantRadii.medium),
                                ) {
                                    Column(modifier = Modifier.padding(EnchantSpacing.md)) {
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
                            modifier = Modifier.padding(EnchantSpacing.lg)
                        )
                    }
                }
                if (pinnedMessages.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(EnchantRadii.medium),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EnchantSpacing.sm, vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = EnchantSpacing.md, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                EnchantIcons.pin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(EnchantSpacing.sm))
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    EnchantIcons.lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(EnchantSpacing.lg))
                            Text(
                                "End-to-end encrypted",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(EnchantSpacing.sm))
                            Text(
                                "Messages are secure and private. Tap the lock for more info.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 32.dp),
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
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
                                DateChip(
                                    text = formatDayLabel(message.timestamp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = EnchantSpacing.sm),
                                )
                            }
                            val prevNewer = messages.getOrNull(index - 1)
                            val sameAsBelow = prevNewer != null && prevNewer.senderId == message.senderId
                            val sameAsAbove = nextOlder != null && nextOlder.senderId == message.senderId
                            val isOutgoing = message.senderId == selfUserId
                            MessageBubble(
                                message = message,
                                isOutgoing = isOutgoing,
                                senderId = message.senderId,
                                senderName = senderNames[message.senderId],
                                showSenderName = isGroupChat && !isOutgoing &&
                                    senderNames[message.senderId] != null && !sameAsAbove,
                                hasTail = !sameAsBelow,
                                verticalGap = if (!sameAsAbove) 12.dp else 2.dp,
                                isSelectionMode = selectionMode,
                                isSelected = message.localId in selectedIds,
                                onLongPress = { enterSelection(message.localId) },
                                onToggleSelection = { toggleSelection(message.localId) },
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
                                onInfo = { message -> infoMessage = message },
                                onStar = { viewModel.starMessage(it, !message.isStarred) },
                                onPin = { viewModel.pinMessage(it) }
                            )
                        }
                    }
                        ScrollToBottomFab(
                            visible = showScrollToBottom,
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = EnchantSpacing.md, bottom = EnchantSpacing.sm),
                        )
                    }
                }

                sendingState?.let { state ->
                    when (state) {
                        SendState.SENDING -> Text(
                            "Sending…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = EnchantSpacing.lg)
                        )
                        SendState.UPLOADING -> LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(2.dp)
                        )
                        SendState.FAILED -> Text(
                            "Failed to send",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = EnchantSpacing.lg)
                        )
                        else -> {}
                    }
                }

                AnimatedVisibility(visible = typingIndicator) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EnchantSpacing.md, vertical = 2.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        TypingBubble()
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
            },
            onPoll = {
                showAttachments = false
                showPollDialog = true
            },
            onSticker = {
                showAttachments = false
                showStickerPicker = true
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

    if (showPollDialog) {
        AlertDialog(
            onDismissRequest = { showPollDialog = false },
            title = { Text("Create poll") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pollQuestion,
                        onValueChange = { pollQuestion = it },
                        label = { Text("Question") },
                        singleLine = true
                    )
                    pollOptions.forEachIndexed { index, opt ->
                        OutlinedTextField(
                            value = opt,
                            onValueChange = { updated ->
                                pollOptions = pollOptions.toMutableList().also { it[index] = updated }
                            },
                            label = { Text("Option ${index + 1}") },
                            singleLine = true
                        )
                    }
                    TextButton(onClick = { pollOptions = pollOptions + "" }) {
                        Text("Add option")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pollQuestion.isNotBlank() && pollOptions.count { it.isNotBlank() } >= 2,
                    onClick = {
                        val validOptions = pollOptions.filter { it.isNotBlank() }
                        scope.launch {
                            viewModel.createPollAndSend(pollQuestion, validOptions, conversationId)
                        }
                        showPollDialog = false
                        pollQuestion = ""
                        pollOptions = listOf("", "")
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showPollDialog = false }) { Text("Cancel") }
            }
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

    val infoMsg = infoMessage
    if (infoMsg != null) {
        AlertDialog(
            onDismissRequest = { infoMessage = null },
            title = { Text("Message info") },
            text = {
                Column {
                    InfoRow("Status", when (infoMsg.status) {
                        MessageStatus.SENDING -> "Sending"
                        MessageStatus.SENT -> "Sent"
                        MessageStatus.DELIVERED -> "Delivered"
                        MessageStatus.READ -> "Read"
                        MessageStatus.FAILED -> "Failed"
                        MessageStatus.PENDING -> "Pending"
                    })
                    InfoRow("Sent", java.text.SimpleDateFormat("EEE, MMM d, HH:mm:ss", java.util.Locale.US)
                        .format(java.util.Date(infoMsg.timestamp)))
                    val serverTs = infoMsg.serverTs
                    if (serverTs != null && serverTs > 0) {
                        InfoRow("Received by server", java.text.SimpleDateFormat("EEE, MMM d, HH:mm:ss", java.util.Locale.US)
                            .format(java.util.Date(serverTs)))
                    }
                    if (infoMsg.isEdited) InfoRow("Edited", "Yes")
                    val mediaSz = infoMsg.mediaSize
                    if (mediaSz != null) {
                        InfoRow("Size", formatFileSize(mediaSz))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Ticks: ${if (infoMsg.status == MessageStatus.READ) "double, filled" else if (infoMsg.status == MessageStatus.DELIVERED) "double" else "single"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { infoMessage = null }) { Text("Close") }
            }
        )
    }

    if (showSafetyNumber) {
        val safetyNum by viewModel.safetyNumber.collectAsState()
        val ktStatus by viewModel.ktStatus.collectAsState()
        var showQr by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showSafetyNumber = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (peerVerified) EnchantIcons.lock else EnchantIcons.lockOpen,
                        contentDescription = null,
                        tint = if (peerVerified) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (peerVerified) "Verified" else "Safety number")
                }
            },
            text = {
                Column {
                    Text(
                        "Compare this number with ${title ?: "the other person"} in person or "
                            + "over a trusted channel. If it matches, verification is complete.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        safetyNum ?: "Computing...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (ktStatus != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            ktStatus!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ktStatus!!.contains("Confirmed")) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { showQr = !showQr }) {
                        Text(if (showQr) "Hide QR" else "Show QR")
                    }
                    TextButton(onClick = { viewModel.verifyPeer() }) { Text("Mark as verified") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSafetyNumber = false }) { Text("Close") }
            }
        )
        if (showQr && safetyNum != null) {
            AlertDialog(
                onDismissRequest = { showQr = false },
                title = { Text("Scan this QR with ${title ?: "the other person"}") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val selfId = org.enchant.core.base.SecurePreferences.getString("auth.user_id") ?: ""
                        val qrPayload = "enchant-safety:${selfId}:${safetyNum}"
                        val qrBitmap = remember(qrPayload) { generateQrBitmap(qrPayload, 320) }
                        if (qrBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Safety number QR",
                                modifier = Modifier.size(240.dp)
                            )
                        } else {
                            Text("QR unavailable", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "The other person scans this with their app and the safety numbers must match.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showQr = false }) { Text("Done") }
                }
            )
        }
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
                                        EnchantIcons.check,
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
        shape = RoundedCornerShape(EnchantRadii.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EnchantSpacing.sm, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Reply",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(message.take(80), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { this.contentDescription = "Dismiss reply" }
            ) {
                Icon(EnchantIcons.x, "Dismiss reply")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(160.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/** Circular scroll-to-bottom FAB: springs in when the user has scrolled up, then jumps back to the newest message. */
@Composable
private fun ScrollToBottomFab(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = EnchantMotion.springBouncy,
        label = "scrollToBottomScale",
    )
    Box(
        modifier = modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = scale
            }
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = visible, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            EnchantIcons.arrowDown,
            contentDescription = "Scroll to bottom",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Renders the payload as a QR bitmap (zxing core). */
private fun generateQrBitmap(content: String, size: Int): android.graphics.Bitmap? {
    return try {
        val hints = mapOf(com.google.zxing.EncodeHintType.MARGIN to 1)
        val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
            content, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints
        )
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
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

private fun createTempFile(context: android.content.Context, prefix: String, suffix: String): File {
    val dir = File(context.cacheDir, "media_temp")
    dir.mkdirs()
    return File.createTempFile(prefix, suffix, dir)
}
