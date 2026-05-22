package org.enchant

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.enchant.auth.AuthViewModel
import org.enchant.auth.screens.AppLockScreen
import org.enchant.auth.screens.RestorePromptScreen
import org.enchant.auth.screens.TwoStepPinScreen
import org.enchant.backup.BackupViewModel
import org.enchant.calls.CallLogViewModel
import org.enchant.calls.CallViewModel
import org.enchant.calls.CallsNavKey
import org.enchant.calls.SafetyNumberDialog
import org.enchant.calls.calllinks.CallLinkManager
import org.enchant.calls.calllinks.CallLinkScreen
import org.enchant.calls.screens.*
import org.enchant.chat.ConversationScreen
import org.enchant.chat.ChatNavKey
import org.enchant.chatlist.ChatListNavDisplay
import org.enchant.chatlist.ChatListNavKey
import org.enchant.channels.ChannelViewModel
import org.enchant.channels.screens.ChannelFeedScreen
import org.enchant.contacts.ContactsViewModel
import org.enchant.contacts.screens.ContactListScreen
import org.enchant.core.base.SecurePreferences
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.CallStatus
import org.enchant.core.database.DatabasePool
import org.enchant.core.database.dao.IdentityDao
import org.enchant.core.database.entity.MessageEntity
import org.enchant.core.network.ApiClient
import org.enchant.core.network.WebSocketService
import org.enchant.crypto.SessionManager
import org.enchant.groups.GroupsViewModel
import org.enchant.groups.screens.CreateGroupScreen
import org.enchant.groups.screens.GroupInfoScreen
import org.enchant.groups.screens.GroupListScreen
import org.enchant.groups.screens.JoinRequestsScreen
import org.enchant.location.LocationPickerScreen
import org.enchant.polls.screens.PollCreateSheet
import org.enchant.profile.ProfileViewModel
import org.enchant.profile.screens.ProfileScreen
import org.enchant.settings.SettingsViewModel
import org.enchant.settings.screens.*
import org.enchant.status.StatusViewModel
import org.enchant.status.screens.*
import org.enchant.stickers.StickerViewModel
import org.enchant.stickers.screens.StickerStoreScreen
import org.enchant.ui.theme.NotionTheme
import org.enchant.core.ui.navigation.TransitionSpecs

@Composable
fun MainNavDisplay(
    callViewModel: CallViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()
) {
    val backStack = rememberNavBackStack<NavKey>(ChatListNavKey.ConversationList)
    val callUiState by callViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSafetyNumber by remember { mutableStateOf(false) }
    var safetyNumber by remember { mutableStateOf("UNVERIFIED") }

    LaunchedEffect(callUiState.callState.status) {
        val status = callUiState.callState.status
        when (status) {
            CallStatus.RINGING -> {
                backStack.add(CallsNavKey.IncomingCall(
                    callerId = callUiState.callState.remoteUserId?.toLongOrNull() ?: 0L,
                    callId = callUiState.callState.callId ?: "",
                    isVideoCall = callUiState.callState.isVideoCall
                ))
            }
            CallStatus.CALLING -> {
                backStack.add(CallsNavKey.OutgoingCall(
                    recipientId = callUiState.callState.remoteUserId?.toLongOrNull() ?: 0L,
                    isVideoCall = callUiState.callState.isVideoCall
                ))
            }
            CallStatus.CONNECTED -> {
                val last = if (backStack.isNotEmpty()) backStack[backStack.size - 1] else null
                if (last is CallsNavKey.IncomingCall || last is CallsNavKey.OutgoingCall) {
                    backStack.removeAt(backStack.size - 1)
                }
                backStack.add(CallsNavKey.ActiveCall(
                    callId = callUiState.callState.callId ?: ""
                ))
            }
            CallStatus.IDLE -> {
                if (backStack.isNotEmpty()) {
                    val last = backStack[backStack.size - 1]
                    if (last is CallsNavKey.IncomingCall || last is CallsNavKey.OutgoingCall ||
                        last is CallsNavKey.ActiveCall || last is CallsNavKey.GroupCall) {
                        backStack.removeAt(backStack.size - 1)
                    }
                }
            }
            else -> {}
        }
        if (status != CallStatus.IDLE) {
            val intent = Intent(context, WebSocketService::class.java).apply {
                action = WebSocketService.ACTION_START_CALL
                putExtra("call_id", callUiState.callState.callId)
                putExtra("is_video", callUiState.callState.isVideoCall)
                putExtra("remote_user_id", callUiState.callState.remoteUserId)
            }
            context.startForegroundService(intent)
        }
    }

    LaunchedEffect(Unit) {
        context.startService(
            Intent(context, WebSocketService::class.java).apply {
                action = WebSocketService.ACTION_CONNECT
            }
        )
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        val data = activity.intent?.data ?: return@LaunchedEffect
        when (data.host) {
            "chat" -> data.pathSegments.firstOrNull()?.let {
                backStack.add(ChatNavKey.Conversation(threadId = it.toLongOrNull() ?: 0L))
            }
            "group" -> data.pathSegments.firstOrNull()?.let {
                backStack.add(MainNavKey.GroupInfo(groupId = it))
            }
        }
    }

    if (showSafetyNumber) {
        SafetyNumberDialog(
            safetyNumber = safetyNumber,
            onDismiss = { showSafetyNumber = false },
            onVerify = {
                SecurePreferences.putString("safety_number", callUiState.callState.remoteUserId ?: "")
                scope.launch {
                    try {
                        val pool = DatabasePool.instance
                        if (pool != null) {
                            IdentityDao(pool).setVerified("safety:${callUiState.callState.remoteUserId}", 1)
                        }
                    } catch (_: Exception) {}
                }
                showSafetyNumber = false
            }
        )
    }

    NavDisplay(
        backStack = backStack,
        transitionSpec = TransitionSpecs.HorizontalSlide.transitionSpec,
        popTransitionSpec = TransitionSpecs.HorizontalSlide.popTransitionSpec,
        predictivePopTransitionSpec = TransitionSpecs.HorizontalSlide.predictivePopTransitionSpec,
        entryProvider = entryProvider {
            entry<ChatListNavKey.ConversationList> {
                ChatListNavDisplay(
                    onNavigateToConversation = { threadId ->
                        backStack.add(ChatNavKey.Conversation(threadId = threadId))
                    }
                )
            }

            entry<ChatNavKey.Conversation> { key ->
                ConversationScreen(
                    conversationId = key.threadId.toString(),
                    onNavigateBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    onStartCall = { userId, isVideo -> callViewModel.startCall(userId, isVideo) }
                )
            }

            entry<CallsNavKey.CallLog> {
                val callLogViewModel: CallLogViewModel = viewModel()
                val state by callLogViewModel.uiState.collectAsStateWithLifecycle()
                CallLogScreen(
                    entries = state.filteredEntries,
                    filter = state.filter,
                    isLoading = state.isLoading,
                    isSelectionMode = state.isSelectionMode,
                    selectedIds = state.selectedIds,
                    onFilterChange = { callLogViewModel.setFilter(it) },
                    onEntryClick = { },
                    onStartSelection = { callLogViewModel.startSelection() },
                    onEndSelection = { callLogViewModel.endSelection() },
                    onToggleSelected = { id -> callLogViewModel.toggleSelected(id) },
                    onSelectAll = { callLogViewModel.selectAll() },
                    onDelete = {
                        val staged = callLogViewModel.stageDeletion()
                        callLogViewModel.confirmDeletion(staged)
                    }
                )
            }

            entry<CallsNavKey.OutgoingCall> { key ->
                OutgoingCallScreen(
                    remoteName = callUiState.callState.remoteName ?: "User ${key.recipientId}",
                    isVideoCall = callUiState.callState.isVideoCall,
                    onEndCall = { callViewModel.endCall() },
                    onToggleSpeaker = { callViewModel.toggleSpeaker() },
                    onSwitchToVideo = { callViewModel.toggleVideo() }
                )
            }

            entry<CallsNavKey.IncomingCall> { key ->
                IncomingCallScreen(
                    callerName = callUiState.callState.remoteName ?: "User ${key.callerId}",
                    callerId = key.callerId.toString(),
                    isVideoCall = callUiState.callState.isVideoCall,
                    onAcceptAudio = { callViewModel.acceptCall(false) },
                    onAcceptVideo = { callViewModel.acceptCall(true) },
                    onDecline = { callViewModel.denyCall() }
                )
            }

            entry<CallsNavKey.ActiveCall> {
                val state = callUiState.callState
                if (state.isVideoCall) {
                    ActiveVideoCallScreen(
                        remoteUserId = state.remoteUserId ?: "Unknown",
                        durationSeconds = state.durationSeconds,
                        isMuted = state.isMuted,
                        isSpeakerOn = state.isSpeakerOn,
                        onToggleMute = { callViewModel.toggleMute() },
                        onFlipCamera = { callViewModel.flipCamera() },
                        onToggleSpeaker = { callViewModel.toggleSpeaker() },
                        onEndCall = { callViewModel.endCall() }
                    )
                } else {
                    LaunchedEffect(state.remoteUserId) {
                        withContext(Dispatchers.Default) {
                            safetyNumber = SessionManager.getSafetyNumber(state.remoteUserId ?: "")
                        }
                    }
                    ActiveVoiceCallScreen(
                        remoteName = state.remoteName ?: "Unknown",
                        durationSeconds = state.durationSeconds,
                        isMuted = state.isMuted,
                        isSpeakerOn = state.isSpeakerOn,
                        signalStrength = state.signalStrength?.ordinal?.let { 3 - it } ?: 0,
                        onToggleMute = { callViewModel.toggleMute() },
                        onToggleSpeaker = { callViewModel.toggleSpeaker() },
                        onEndCall = { callViewModel.endCall() },
                        onShowKeypad = { },
                        onSwitchToVideo = { callViewModel.toggleVideo() },
                        onShowSafetyNumber = { showSafetyNumber = true }
                    )
                }
            }

            entry<CallsNavKey.GroupCall> {
                GroupCallScreen(
                    participants = emptyList(),
                    isAdmin = true,
                    durationSeconds = callUiState.callState.durationSeconds,
                    isMuted = callUiState.callState.isMuted,
                    onToggleMute = { callViewModel.toggleMute() },
                    onRaiseHand = { callViewModel.raiseHand(!callUiState.callState.isHandRaised) },
                    onSendReaction = { callViewModel.react(it) },
                    onMuteParticipant = { },
                    onRemoveParticipant = { },
                    onEndCall = { callViewModel.endCall() }
                )
            }

            entry<CallsNavKey.CallLink> { key ->
                val apiClient = remember { ApiClient() }
                val callLinkManager = remember { CallLinkManager(apiClient) }
                var callLinkData by remember { mutableStateOf<org.enchant.core.calls.CallLinkData?>(null) }
                var isLoading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(key.linkRoomId) {
                    isLoading = true
                    error = null
                    callLinkManager.getCallLink(key.linkRoomId).fold(
                        onSuccess = { callLinkData = it; isLoading = false },
                        onFailure = { error = it.message; isLoading = false }
                    )
                }

                CallLinkScreen(
                    callLink = callLinkData,
                    isOwner = false,
                    isLoading = isLoading,
                    error = error,
                    onJoinCall = {
                        scope.launch { callLinkManager.joinCallLink(key.linkRoomId) }
                    },
                    onEditName = { },
                    onDelete = {
                        scope.launch {
                            callLinkManager.deleteCallLink(key.linkRoomId)
                            if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                        }
                    },
                    onNavigateBack = {
                        if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                    }
                )
            }

            entry<MainNavKey.Contacts> {
                val contactsViewModel: ContactsViewModel = viewModel()
                val state by contactsViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { contactsViewModel.loadContacts() }
                ContactListScreen(
                    contacts = state.contacts,
                    searchResults = state.searchResults,
                    searchQuery = state.searchQuery,
                    isLoading = state.isLoading,
                    error = state.error,
                    onContactClick = { userId ->
                        backStack.add(ChatNavKey.Conversation(threadId = userId.toLongOrNull() ?: 0L))
                    },
                    onSearchQueryChange = { contactsViewModel.searchContacts(it) },
                    onAddContact = { },
                    onRefresh = { contactsViewModel.loadContacts() }
                )
            }

            entry<MainNavKey.CreateGroup> {
                val groupsViewModel: GroupsViewModel = viewModel()
                val state by groupsViewModel.uiState.collectAsStateWithLifecycle()
                CreateGroupScreen(
                    onGroupCreated = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    onNavigateBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    onCreateGroup = { name, desc, members -> groupsViewModel.createGroup(name, desc, members) },
                    isLoading = state.isLoading
                )
            }

            entry<MainNavKey.Groups> {
                val groupsViewModel: GroupsViewModel = viewModel()
                val state by groupsViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { groupsViewModel.loadGroups() }
                GroupListScreen(
                    groups = state.groups,
                    isLoading = state.isLoading,
                    error = state.error,
                    onGroupClick = { groupId -> backStack.add(MainNavKey.GroupInfo(groupId = groupId)) },
                    onCreateGroup = { backStack.add(MainNavKey.CreateGroup) },
                    onJoinGroup = { groupsViewModel.joinViaLink("") },
                    onRefresh = { groupsViewModel.loadGroups() }
                )
            }

            entry<MainNavKey.GroupInfo> { key ->
                val groupsViewModel: GroupsViewModel = viewModel()
                val state by groupsViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(key.groupId) { groupsViewModel.loadGroupInfo(key.groupId) }
                GroupInfoScreen(
                    group = state.currentGroup,
                    members = state.members,
                    joinRequests = state.joinRequests.size,
                    isLoading = state.isLoading,
                    error = state.error,
                    inviteLink = state.inviteLink,
                    onNavigateBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    onAddMembers = { groupsViewModel.loadMembers(key.groupId) },
                    onRemoveMember = { userId -> groupsViewModel.removeMember(key.groupId, userId) },
                    onUpdateRole = { userId, role -> groupsViewModel.updateMemberRole(key.groupId, userId, role) },
                    onCreateInviteLink = { groupsViewModel.createInviteLink(key.groupId) },
                    onCopyInviteLink = { },
                    onViewJoinRequests = { backStack.add(MainNavKey.JoinRequests) },
                    onLeaveGroup = { },
                    onDeleteGroup = { groupsViewModel.deleteGroup(key.groupId) },
                    onRefresh = { groupsViewModel.loadGroupInfo(key.groupId) }
                )
            }

            entry<MainNavKey.JoinRequests> {
                JoinRequestsScreen(
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                )
            }

            entry<MainNavKey.Settings> {
                SettingsHomeScreen(
                    onNavigateToAccount = { backStack.add(MainNavKey.AccountSettings) },
                    onNavigateToSecurity = { backStack.add(MainNavKey.SecuritySettings) },
                    onNavigateToPrivacy = { backStack.add(MainNavKey.PrivacySettings) },
                    onNavigateToNotifications = { backStack.add(MainNavKey.NotificationSettings) },
                    onNavigateToAppearance = { backStack.add(MainNavKey.AppearanceSettings) },
                    onNavigateToChats = { backStack.add(MainNavKey.ChatsSettings) },
                    onNavigateToStorage = { backStack.add(MainNavKey.StorageSettings) },
                    onNavigateToAbout = { backStack.add(MainNavKey.About) }
                )
            }

            entry<MainNavKey.AccountSettings> {
                val vm: SettingsViewModel = viewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { vm.loadSettings() }
                AccountSettingsScreen(
                    displayName = state.displayName,
                    username = state.username,
                    about = state.about,
                    devices = state.devices,
                    isLoading = state.isProcessing,
                    onRevokeDevice = { vm.revokeDevice(it) },
                    onDeleteAccount = { vm.deleteAccount() },
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                )
            }

            entry<MainNavKey.SecuritySettings> {
                SecuritySettingsScreen(
                    onSetupTwoStep = { backStack.add(MainNavKey.PinCreation) },
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                )
            }

            entry<MainNavKey.PrivacySettings> {
                val vm: SettingsViewModel = viewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { vm.loadSettings() }
                PrivacySettingsScreen(
                    lastSeenVisibility = state.lastSeenVisibility,
                    onlineVisibility = state.onlineVisibility,
                    avatarVisibility = state.avatarVisibility,
                    aboutVisibility = state.aboutVisibility,
                    blockedUsers = state.blockedUsers,
                    readReceipts = state.readReceipts,
                    onLastSeenChange = { vm.updatePrivacy(it, state.onlineVisibility, state.avatarVisibility, state.aboutVisibility, state.readReceipts) },
                    onOnlineVisibilityChange = { vm.updatePrivacy(state.lastSeenVisibility, it, state.avatarVisibility, state.aboutVisibility, state.readReceipts) },
                    onAvatarVisibilityChange = { vm.updatePrivacy(state.lastSeenVisibility, state.onlineVisibility, it, state.aboutVisibility, state.readReceipts) },
                    onAboutVisibilityChange = { vm.updatePrivacy(state.lastSeenVisibility, state.onlineVisibility, state.avatarVisibility, it, state.readReceipts) },
                    onReadReceiptsChange = { vm.updatePrivacy(state.lastSeenVisibility, state.onlineVisibility, state.avatarVisibility, state.aboutVisibility, it) },
                    onViewBlockedUsers = { backStack.add(MainNavKey.BlockedUsers) },
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                )
            }

            entry<MainNavKey.NotificationSettings> {
                val vm: SettingsViewModel = viewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { vm.loadSettings() }
                NotificationsSettingsScreen(
                    masterEnabled = state.notificationEnabled,
                    messageNotifications = state.messageNotifications,
                    showPreview = state.showPreview,
                    dndStartTime = "",
                    dndEndTime = "",
                    dndDaysOfWeek = emptyList(),
                    onMasterToggle = { vm.updateNotificationPrefs(it, state.messageNotifications, state.showPreview) },
                    onMessageNotificationsChange = { vm.updateNotificationPrefs(state.notificationEnabled, it, state.showPreview) },
                    onShowPreviewChange = { vm.updateNotificationPrefs(state.notificationEnabled, state.messageNotifications, it) },
                    onDndStartTimeChange = {},
                    onDndEndTimeChange = {},
                    onDndDaysChange = {},
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                )
            }

            entry<MainNavKey.AppearanceSettings> {
                val vm: SettingsViewModel = viewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { vm.loadSettings() }
                AppearanceSettingsScreen(
                    currentTheme = state.theme,
                    fontSize = state.fontSize,
                    onThemeChange = { vm.updateTheme(it) },
                    onFontSizeChange = { vm.updateFontSize(it) },
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                )
            }

            entry<MainNavKey.ChatsSettings> {
                ChatsSettingsScreen(
                    defaultDisappearingTimer = 0,
                    autoDownloadWifi = true,
                    autoDownloadCellular = false,
                    onDisappearingTimerChange = {},
                    onAutoDownloadWifiChange = {},
                    onAutoDownloadCellularChange = {},
                    onBackupSettings = { backStack.add(MainNavKey.BackupSettings) },
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                )
            }

            entry<MainNavKey.StorageSettings> {
                StorageSettingsScreen(
                    storageInfo = null,
                    isProcessing = false,
                    onClearCache = {},
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                )
            }

            entry<MainNavKey.About> {
                AboutScreen(onNavigateBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) })
            }

            entry<MainNavKey.BackupSettings> {
                BackupSettingsScreen(onNavigateBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) })
            }

            entry<MainNavKey.BlockedUsers> {
                BlockedUsersScreen(onNavigateBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) })
            }

            entry<MainNavKey.StatusFeed> {
                val statusViewModel: StatusViewModel = viewModel()
                val state by statusViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { statusViewModel.loadFeed() }
                StatusFeedScreen(
                    myStatus = state.myStatus,
                    feed = state.feed.groupBy { it.userId },
                    onStatusTap = { statusId -> backStack.add(MainNavKey.StatusViewer(statusId = statusId)) },
                    onCreateStatus = { backStack.add(MainNavKey.StatusCreate) }
                )
            }

            entry<MainNavKey.StatusCreate> {
                val statusViewModel: StatusViewModel = viewModel()
                StatusCreateScreen(
                    onCreateText = { text, bg, _ ->
                        statusViewModel.createTextStatus(text, bg, org.enchant.status.StatusPrivacy.AllContacts)
                        if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                    },
                    onCreateMedia = { _, _ -> if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                )
            }

            entry<MainNavKey.StatusViewer> { key ->
                val statusViewModel: StatusViewModel = viewModel()
                val state by statusViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { statusViewModel.loadFeed() }
                StatusViewerScreen(
                    statuses = state.feed,
                    initialIndex = state.feed.indexOfFirst { it.statusId == key.statusId }.coerceAtLeast(0),
                    onReply = { },
                    onClose = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    onViewInfo = { id -> statusViewModel.viewStatus(id) }
                )
            }

            entry<MainNavKey.ChannelsFeed> {
                val channelViewModel: ChannelViewModel = viewModel()
                val state by channelViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { channelViewModel.loadMyChannels() }
                ChannelFeedScreen(
                    channelId = state.channels.firstOrNull()?.channelId ?: "",
                    channelName = state.channels.firstOrNull()?.name ?: "Channels",
                    isSubscribed = state.channels.firstOrNull()?.isSubscribed ?: false,
                    posts = state.feed,
                    pinnedPost = state.pinnedPost,
                    onSubscribe = { channelViewModel.subscribe(state.channels.firstOrNull()?.channelId ?: "") },
                    onShare = { },
                    onLoadMore = { channelViewModel.loadMore(state.channels.firstOrNull()?.channelId ?: "") }
                )
            }

            entry<MainNavKey.Stickers> {
                val stickerViewModel: StickerViewModel = viewModel()
                val state by stickerViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { stickerViewModel.loadFeatured() }
                StickerStoreScreen(
                    featured = state.featured,
                    searchResults = state.searchResults,
                    isLoading = state.isLoading,
                    error = state.error,
                    onInstall = { packId -> stickerViewModel.installPack(packId) },
                    onSearch = { query -> stickerViewModel.searchPacks(query) },
                    onPackClick = { packId -> stickerViewModel.loadPackDetail(packId) },
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                )
            }

            entry<MainNavKey.Search> {
                var q by remember { mutableStateOf("") }
                var results by remember { mutableStateOf<List<MessageEntity>>(emptyList()) }
                var loading by remember { mutableStateOf(false) }
                LaunchedEffect(q) {
                    if (q.length >= 2) {
                        loading = true
                        val pool = DatabasePool.instance
                        if (pool != null) {
                            delay(300)
                            org.enchant.core.database.dao.MessageDao(pool)
                                .searchMessages(q).first().let { results = it }
                        }
                        loading = false
                    } else { results = emptyList() }
                }
                Box(Modifier.fillMaxSize().padding(8.dp)) {
                    TextField(value = q, onValueChange = { q = it }, placeholder = { Text("Search") })
                    if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else if (q.isNotEmpty() && results.isEmpty()) Text("No results", Modifier.align(Alignment.Center))
                    else LazyColumn(Modifier.padding(top = 56.dp)) {
                        items(results) { msg ->
                            Text(msg.content.take(80))
                        }
                    }
                }
            }

            entry<MainNavKey.Profile> { key ->
                val profileViewModel: ProfileViewModel = viewModel()
                val state by profileViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(key.userId) { profileViewModel.loadProfile(key.userId) }
                ProfileScreen(
                    userId = key.userId,
                    isOwnProfile = key.userId == SecurePreferences.getString("auth.user_id"),
                    profile = state.profile,
                    onEdit = { },
                    onMessage = { backStack.add(ChatNavKey.Conversation(threadId = key.userId.toLongOrNull() ?: 0L)) },
                    onCall = { },
                    onBlock = { profileViewModel.blockUser(key.userId) },
                    isBlocked = state.blockedUsers.any { it.userId == key.userId }
                )
            }

            entry<MainNavKey.MediaViewer> {
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    android.widget.Toast.makeText(context, "Media viewer requires a media attachment", android.widget.Toast.LENGTH_SHORT).show()
                    if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                }
            }

            entry<MainNavKey.AppLock> {
                AppLockScreen(
                    onVerified = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    onDismiss = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                )
            }

            entry<MainNavKey.PinCreation> {
                TwoStepPinScreen.Screen(
                    onPinCreated = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                )
            }

            entry<MainNavKey.LocationPicker> {
                LocationPickerScreen(
                    onLocationSelected = { _, _, _ -> if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                )
            }

            entry<MainNavKey.PollCreate> { key ->
                PollCreateSheet(
                    onCreate = { _, _, _, _ -> if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    onDismiss = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    isCreating = false
                )
            }

            entry<MainNavKey.ShareTarget> {
                val ctx = LocalContext.current
                LaunchedEffect(Unit) {
                    android.widget.Toast.makeText(ctx, "Sharing handled by ShareTargetActivity", android.widget.Toast.LENGTH_SHORT).show()
                    if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                }
            }

            entry<MainNavKey.QRCode> {
                val ctx = LocalContext.current
                LaunchedEffect(Unit) {
                    android.widget.Toast.makeText(ctx, "QR Code coming soon", android.widget.Toast.LENGTH_SHORT).show()
                    if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                }
            }

            entry<MainNavKey.QRScanner> {
                val ctx = LocalContext.current
                LaunchedEffect(Unit) {
                    android.widget.Toast.makeText(ctx, "QR Scanner coming soon", android.widget.Toast.LENGTH_SHORT).show()
                    if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                }
            }
        }
    )
}
