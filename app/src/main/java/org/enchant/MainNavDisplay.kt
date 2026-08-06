package org.enchant

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.enchant.auth.AuthViewModel
import org.enchant.calls.CallViewModel
import org.enchant.chatlist.ChatListNavDisplay
import org.enchant.chatlist.ConversationListViewModel
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.CallStatus
import org.enchant.main.EmptyDetailScreen
import org.enchant.main.MainNavigationBar
import org.enchant.main.MainNavigationDetailLocationEffect
import org.enchant.main.MainNavigationRail
import org.enchant.MainNavigationViewModel
import org.enchant.main.MainFloatingActionButtons
import org.enchant.main.MainFloatingActionButtonsCallback
import org.enchant.settings.SettingsViewModel
import org.enchant.settings.screens.*
import org.enchant.contacts.screens.ContactListScreen
import org.enchant.contacts.ContactsViewModel
import org.enchant.groups.GroupsViewModel
import org.enchant.groups.screens.*
import org.enchant.status.StatusViewModel
import org.enchant.status.StatusPrivacy
import org.enchant.status.screens.*
import org.enchant.calls.CallLogViewModel
import org.enchant.calls.screens.CallLogScreen
import org.enchant.calls.screens.CallDetailScreen
import org.enchant.core.calls.CallLogFilter
import org.enchant.window.AppScaffold
import org.enchant.window.rememberAppScaffoldNavigator
import org.enchant.profile.screens.ProfileScreen
import org.enchant.channels.ChannelViewModel
import org.enchant.channels.screens.ChannelFeedScreen
import org.enchant.stickers.StickerViewModel
import org.enchant.stickers.screens.StickerStoreScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavDisplay(
    callViewModel: CallViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()
) {
    val mainNavViewModel: MainNavigationViewModel = viewModel()
    val mainNavState by mainNavViewModel.mainNavigationState.collectAsStateWithLifecycle()
    val callUiState by callViewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val navigator = rememberAppScaffoldNavigator()

    LaunchedEffect(Unit) {
        mainNavViewModel.wrapNavigator(scope, navigator)
        mainNavViewModel.clearEarlyDetailLocation()
        if (org.enchant.BuildConfig.DEBUG) {
            runCatching {
                val clazz = Class.forName("org.enchant.agent.AgentRuntime")
                val field = clazz.getDeclaredField("mainNavigationViewModel")
                field.isAccessible = true
                field.set(null, mainNavViewModel)
            }
        }
    }

    MainNavigationDetailLocationEffect(mainNavigationViewModel = mainNavViewModel)

    Box(modifier = Modifier.fillMaxSize()) {
    AppScaffold(
        navigator = navigator,
        modifier = Modifier.fillMaxSize(),
        secondaryContent = {
            ListPaneContent(
                currentListLocation = mainNavState.currentListLocation,
                onConversationClick = { conversationId ->
                    mainNavViewModel.goTo(MainNavigationDetailLocation.Conversation(conversationId))
                },
                onArchiveClick = { conversationId ->
                    mainNavViewModel.goTo(MainNavigationDetailLocation.Conversation(conversationId))
                },
                onNewChat = { mainNavViewModel.goTo(MainNavigationDetailLocation.Contacts) },
                onNewGroup = { mainNavViewModel.goTo(MainNavigationDetailLocation.CreateGroup) },
                onNavigate = { mainNavViewModel.goTo(it) }
            )
        },
        primaryContent = {
            // The detail stacks must be observed as Compose state: pushing a
            // second detail (e.g. Contacts -> Profile) leaves currentDetail
            // unchanged ("Primary" -> "Primary"), so the stack read has to be
            // the recomposition trigger itself.
            val chatsStack by mainNavViewModel.chatsDetailStack.collectAsStateWithLifecycle()
            val archiveStack by mainNavViewModel.archiveDetailStack.collectAsStateWithLifecycle()
            val callsStack by mainNavViewModel.callsDetailStack.collectAsStateWithLifecycle()
            val storiesStack by mainNavViewModel.storiesDetailStack.collectAsStateWithLifecycle()
            val settingsStack by mainNavViewModel.settingsDetailStack.collectAsStateWithLifecycle()
            DetailPaneContent(
                detailStack = when (mainNavState.currentListLocation) {
                    MainNavigationListLocation.CHATS -> chatsStack
                    MainNavigationListLocation.ARCHIVE -> archiveStack
                    MainNavigationListLocation.CALLS -> callsStack
                    MainNavigationListLocation.STATUS -> storiesStack
                    MainNavigationListLocation.SETTINGS -> settingsStack
                },
                onNavigate = { mainNavViewModel.goTo(it) },
                onNavigateBack = { mainNavViewModel.goBackInCurrentTab() }
            )
        },
        navRailContent = {
            MainNavigationRail(
                state = mainNavState,
                mainFloatingActionButtons = {
                    MainFloatingActionButtons(
                        destination = mainNavState.currentListLocation,
                        callback = MainFloatingActionButtonsCallback.Empty
                    )
                },
                onDestinationSelected = { mainNavViewModel.goTo(it) }
            )
        },
        bottomNavContent = {
            MainNavigationBar(
                state = mainNavState,
                onDestinationSelected = { mainNavViewModel.goTo(it) }
            )
        }
    )

        // Full-screen call overlay: outgoing/incoming/active screens mount
        // over the app whenever a call is live (Signal behavior).
        val callStatus = callUiState.callState.status
        if (callStatus != CallStatus.IDLE) {
            val initialKey: androidx.navigation3.runtime.NavKey = when {
                callStatus == CallStatus.CONNECTED -> org.enchant.calls.CallsNavKey.ActiveCall(
                    callUiState.callState.callId ?: ""
                )
                callUiState.callState.direction == org.enchant.core.calls.CallDirection.INCOMING ->
                    org.enchant.calls.CallsNavKey.IncomingCall(0, callUiState.callState.callId ?: "")
                else -> org.enchant.calls.CallsNavKey.OutgoingCall(0)
            }
            val callBackStack = androidx.navigation3.runtime.rememberNavBackStack(initialKey)
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = androidx.compose.material3.MaterialTheme.colorScheme.background
            ) {
                org.enchant.calls.CallsNavDisplay(backStack = callBackStack)
            }
        }
    }
}

@Composable
private fun ListPaneContent(
    currentListLocation: MainNavigationListLocation,
    onConversationClick: (String) -> Unit,
    onArchiveClick: (String) -> Unit,
    onNewChat: () -> Unit = {},
    onNewGroup: () -> Unit = {},
    onNavigate: (MainNavigationDetailLocation) -> Unit = {}
) {
    val listViewModel: ConversationListViewModel = viewModel()

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentListLocation) {
            MainNavigationListLocation.CHATS -> {
                ChatListNavDisplay(
                    viewModel = listViewModel,
                    onNavigateToConversation = onConversationClick,
                    onNewChat = onNewChat,
                    onNewGroup = onNewGroup,
                    onProfileClick = { onNavigate(MainNavigationDetailLocation.Profile("self")) },
                    onSettingsClick = { onNavigate(MainNavigationDetailLocation.Settings) }
                )
            }
            MainNavigationListLocation.ARCHIVE -> {
                ChatListNavDisplay(
                    viewModel = listViewModel,
                    onNavigateToConversation = onArchiveClick
                )
            }
            MainNavigationListLocation.CALLS -> {
                CallsListContent(onNavigate = onNavigate)
            }
            MainNavigationListLocation.STATUS -> {
                StoriesListContent()
            }
            MainNavigationListLocation.SETTINGS -> {
                SettingsTabContent(onNavigate = onNavigate)
            }
        }
    }
}

@Composable
private fun CallsListContent(onNavigate: (MainNavigationDetailLocation) -> Unit = {}) {
    val callLogViewModel: CallLogViewModel = viewModel()
    LaunchedEffect(Unit) { callLogViewModel.loadCallLogs() }
    val callLogState by callLogViewModel.uiState.collectAsStateWithLifecycle()
    CallLogScreen(
        entries = callLogState.filteredEntries,
        filter = callLogState.filter,
        isLoading = callLogState.isLoading,
        isSelectionMode = callLogState.isSelectionMode,
        selectedIds = callLogState.selectedIds,
        onFilterChange = { callLogViewModel.setFilter(it) },
        onEntryClick = { callId -> onNavigate(MainNavigationDetailLocation.Calls.CallDetail(callId)) },
        onStartSelection = { callLogViewModel.startSelection() },
        onEndSelection = { callLogViewModel.endSelection() },
        onToggleSelected = { callLogViewModel.toggleSelected(it) },
        onSelectAll = { callLogViewModel.selectAll() },
        onDelete = {
            val staged = callLogViewModel.stageDeletion()
            callLogViewModel.confirmDeletion(staged)
        }
    )
}

@Composable
private fun StoriesListContent(onNavigate: (MainNavigationDetailLocation) -> Unit = {}) {
    val statusViewModel: StatusViewModel = viewModel()
    LaunchedEffect(Unit) { statusViewModel.loadFeed() }
    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()
    StatusFeedScreen(
        myStatus = statusState.myStatus,
        feed = statusState.feed,
        onStatusTap = { statusId ->
            onNavigate(MainNavigationDetailLocation.StatusViewer(statusId))
        },
        onCreateStatus = { onNavigate(MainNavigationDetailLocation.StatusCreate) }
    )
}

/** Settings as a bottom tab: no back button, profile header opens the profile. */
@Composable
private fun SettingsTabContent(onNavigate: (MainNavigationDetailLocation) -> Unit) {
    val settingsViewModel: SettingsViewModel = viewModel()
    LaunchedEffect(Unit) { settingsViewModel.loadSettings() }
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    SettingsHomeScreen(
        displayName = settingsState.displayName ?: "User",
        username = settingsState.username,
        about = settingsState.about,
        onOpenProfile = { onNavigate(MainNavigationDetailLocation.Profile("self")) },
        onNavigateToAccount = { onNavigate(MainNavigationDetailLocation.AccountSettings) },
        onNavigateToSecurity = { onNavigate(MainNavigationDetailLocation.SecuritySettings) },
        onNavigateToPrivacy = { onNavigate(MainNavigationDetailLocation.PrivacySettings) },
        onNavigateToNotifications = { onNavigate(MainNavigationDetailLocation.NotificationSettings) },
        onNavigateToAppearance = { onNavigate(MainNavigationDetailLocation.AppearanceSettings) },
        onNavigateToChats = { onNavigate(MainNavigationDetailLocation.ChatsSettings) },
        onNavigateToStorage = { onNavigate(MainNavigationDetailLocation.StorageSettings) },
        onNavigateToAbout = { onNavigate(MainNavigationDetailLocation.About) },
        onBack = null
    )
}

@Composable
private fun DetailPaneContent(
    detailStack: List<MainNavigationDetailLocation>,
    onNavigate: (MainNavigationDetailLocation) -> Unit,
    onNavigateBack: () -> Unit
) {
    val settingsViewModel: SettingsViewModel = viewModel()
    LaunchedEffect(Unit) { settingsViewModel.loadSettings() }
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    AnimatedContent(
        targetState = detailStack.lastOrNull(),
        transitionSpec = {
            fadeIn(tween(200)) + slideInHorizontally(tween(300), { it / 4 }) togetherWith
                fadeOut(tween(150))
        },
        label = "detailPane"
    ) { topDetail ->
        when {
            topDetail == null || topDetail is MainNavigationDetailLocation.Empty -> {
                EmptyDetailScreen()
            }
            topDetail is MainNavigationDetailLocation.Conversation -> {
                ConversationDetailContent(
                    conversationId = topDetail.conversationId,
                    onNavigateBack = onNavigateBack,
                    onStartCall = { userId, isVideo ->
                        scope.launch {
                            org.enchant.core.calls.CallManager.startOutgoingCall(userId, isVideo)
                        }
                    }
                )
            }
            topDetail is MainNavigationDetailLocation.Calls.EditCallLinkName -> {
                CallLinkDetailContent(roomId = topDetail.callLinkRoomId)
            }
            // Settings routes
            topDetail is MainNavigationDetailLocation.Settings -> {
                SettingsHomeScreen(
                    displayName = settingsState.displayName ?: "User",
                    username = settingsState.username,
                    about = settingsState.about,
                    onOpenProfile = { onNavigate(MainNavigationDetailLocation.Profile("self")) },
                    onNavigateToAccount = { onNavigate(MainNavigationDetailLocation.AccountSettings) },
                    onNavigateToSecurity = { onNavigate(MainNavigationDetailLocation.SecuritySettings) },
                    onNavigateToPrivacy = { onNavigate(MainNavigationDetailLocation.PrivacySettings) },
                    onNavigateToNotifications = { onNavigate(MainNavigationDetailLocation.NotificationSettings) },
                    onNavigateToAppearance = { onNavigate(MainNavigationDetailLocation.AppearanceSettings) },
                    onNavigateToChats = { onNavigate(MainNavigationDetailLocation.ChatsSettings) },
                    onNavigateToStorage = { onNavigate(MainNavigationDetailLocation.StorageSettings) },
                    onNavigateToAbout = { onNavigate(MainNavigationDetailLocation.About) },
                    onBack = onNavigateBack
                )
            }
            topDetail is MainNavigationDetailLocation.AccountSettings -> {
                LaunchedEffect(Unit) { settingsViewModel.loadDevices() }
                AccountSettingsScreen(
                    displayName = settingsState.displayName,
                    username = settingsState.username,
                    about = settingsState.about,
                    devices = settingsState.devices,
                    isLoading = settingsState.isProcessing,
                    onProfileUpdate = { name, user, ab -> settingsViewModel.updateProfile(name, user, ab) },
                    onRevokeDevice = { settingsViewModel.revokeDevice(it) },
                    onDeleteAccount = { settingsViewModel.deleteAccount() },
                    onBack = onNavigateBack
                )
            }
            topDetail is MainNavigationDetailLocation.SecuritySettings -> {
                LaunchedEffect(Unit) { settingsViewModel.loadSecuritySettings() }
                SecuritySettingsScreen(
                    twoStepEnabled = settingsState.twoStepEnabled,
                    onSetupTwoStep = { pin -> settingsViewModel.setupTwoStepVerification(pin) },
                    onDisableTwoStep = { pin -> settingsViewModel.disableTwoStepVerification(pin) },
                    onBack = onNavigateBack
                )
            }
            topDetail is MainNavigationDetailLocation.PrivacySettings -> {
                PrivacySettingsScreen(
                    lastSeenVisibility = settingsState.lastSeenVisibility,
                    onlineVisibility = settingsState.onlineVisibility,
                    avatarVisibility = settingsState.avatarVisibility,
                    aboutVisibility = settingsState.aboutVisibility,
                    blockedUsers = settingsState.blockedUsers,
                    readReceipts = settingsState.readReceipts,
                    veilSender = settingsState.veilSender,
                    onLastSeenChange = { settingsViewModel.updatePrivacy(it, settingsState.onlineVisibility, settingsState.avatarVisibility, settingsState.aboutVisibility, settingsState.readReceipts) },
                    onOnlineVisibilityChange = { settingsViewModel.updatePrivacy(settingsState.lastSeenVisibility, it, settingsState.avatarVisibility, settingsState.aboutVisibility, settingsState.readReceipts) },
                    onAvatarVisibilityChange = { settingsViewModel.updatePrivacy(settingsState.lastSeenVisibility, settingsState.onlineVisibility, it, settingsState.aboutVisibility, settingsState.readReceipts) },
                    onAboutVisibilityChange = { settingsViewModel.updatePrivacy(settingsState.lastSeenVisibility, settingsState.onlineVisibility, settingsState.avatarVisibility, it, settingsState.readReceipts) },
                    onReadReceiptsChange = { settingsViewModel.updatePrivacy(settingsState.lastSeenVisibility, settingsState.onlineVisibility, settingsState.avatarVisibility, settingsState.aboutVisibility, it) },
                    onVeilSenderChange = { settingsViewModel.setVeilSender(it) },
                    onViewBlockedUsers = { onNavigate(MainNavigationDetailLocation.BlockedUsers) },
                    onBack = onNavigateBack
                )
            }
            topDetail is MainNavigationDetailLocation.NotificationSettings -> {
                LaunchedEffect(Unit) { settingsViewModel.loadSettings() }
                NotificationsSettingsScreen(
                    masterEnabled = settingsState.notificationEnabled,
                    messageNotifications = settingsState.messageNotifications,
                    showPreview = settingsState.showPreview,
                    dndStartTime = settingsState.dndStartTime,
                    dndEndTime = settingsState.dndEndTime,
                    dndDaysOfWeek = settingsState.dndDaysOfWeek,
                    onMasterToggle = { settingsViewModel.updateNotificationPrefs(it, settingsState.messageNotifications, settingsState.showPreview) },
                    onMessageNotificationsChange = { settingsViewModel.updateNotificationPrefs(settingsState.notificationEnabled, it, settingsState.showPreview) },
                    onShowPreviewChange = { settingsViewModel.updateNotificationPrefs(settingsState.notificationEnabled, settingsState.messageNotifications, it) },
                    onDndStartTimeChange = { settingsViewModel.updateDndSchedule(it, settingsState.dndEndTime, settingsState.dndDaysOfWeek) },
                    onDndEndTimeChange = { settingsViewModel.updateDndSchedule(settingsState.dndStartTime, it, settingsState.dndDaysOfWeek) },
                    onDndDaysChange = { settingsViewModel.updateDndSchedule(settingsState.dndStartTime, settingsState.dndEndTime, it) },
                    onBack = onNavigateBack
                )
            }
            topDetail is MainNavigationDetailLocation.AppearanceSettings -> {
                AppearanceSettingsScreen(
                    currentTheme = settingsState.theme,
                    fontSize = settingsState.fontSize,
                    onThemeChange = { settingsViewModel.updateTheme(it) },
                    onFontSizeChange = { settingsViewModel.updateFontSize(it) },
                    onBack = onNavigateBack
                )
            }
            topDetail is MainNavigationDetailLocation.ChatsSettings -> {
                ChatsSettingsScreen(
                    defaultDisappearingTimer = settingsState.defaultDisappearingTimer,
                    autoDownloadWifi = settingsState.autoDownloadWifi,
                    autoDownloadCellular = settingsState.autoDownloadCellular,
                    onDisappearingTimerChange = { settingsViewModel.updateDisappearingTimer(it) },
                    onAutoDownloadWifiChange = { settingsViewModel.updateAutoDownload(it, settingsState.autoDownloadCellular) },
                    onAutoDownloadCellularChange = { settingsViewModel.updateAutoDownload(settingsState.autoDownloadWifi, it) },
                    onBackupSettings = { onNavigate(MainNavigationDetailLocation.BackupSettings) },
                    onBack = onNavigateBack
                )
            }
            topDetail is MainNavigationDetailLocation.StorageSettings -> {
                LaunchedEffect(Unit) { settingsViewModel.getStorageUsage() }
                StorageSettingsScreen(
                    storageInfo = settingsState.storageInfo,
                    isProcessing = settingsState.isProcessing,
                    messageRetentionDays = settingsState.messageRetentionDays,
                    onClearCache = { settingsViewModel.clearCache() },
                    onRetentionChange = { settingsViewModel.updateMessageRetention(it) },
                    onTrimMessages = { settingsViewModel.trimOldMessages() },
                    onBack = onNavigateBack
                )
            }
            topDetail is MainNavigationDetailLocation.About -> {
                AboutScreen(onNavigateBack = onNavigateBack)
            }
            topDetail is MainNavigationDetailLocation.BackupSettings -> {
                BackupSettingsScreen(onNavigateBack = onNavigateBack)
            }
            topDetail is MainNavigationDetailLocation.BlockedUsers -> {
                BlockedUsersScreen(onNavigateBack = onNavigateBack)
            }
            // Groups
            topDetail is MainNavigationDetailLocation.Groups -> {
                val groupsViewModel: GroupsViewModel = viewModel()
                LaunchedEffect(Unit) { groupsViewModel.loadGroups() }
                val groupsState by groupsViewModel.uiState.collectAsStateWithLifecycle()
                GroupListScreen(
                    groups = groupsState.groups,
                    isLoading = groupsState.isLoading,
                    error = groupsState.error,
                    onGroupClick = { groupId -> onNavigate(MainNavigationDetailLocation.GroupInfo(groupId)) },
                    onCreateGroup = { onNavigate(MainNavigationDetailLocation.CreateGroup) },
                    onJoinGroup = { onNavigate(MainNavigationDetailLocation.JoinGroup) },
                    onRefresh = { groupsViewModel.loadGroups() }
                )
            }
            topDetail is MainNavigationDetailLocation.CreateGroup -> {
                val groupsViewModel: GroupsViewModel = viewModel()
                CreateGroupScreen(
                    onGroupCreated = { groupId ->
                        onNavigate(MainNavigationDetailLocation.GroupInfo(groupId))
                    },
                    onNavigateBack = onNavigateBack,
                    onCreateGroup = { name, description, members ->
                        groupsViewModel.createGroup(name, description, members)
                    }
                )
            }
            topDetail is MainNavigationDetailLocation.GroupInfo -> {
                val groupsViewModel: GroupsViewModel = viewModel()
                LaunchedEffect(topDetail.groupId) {
                    groupsViewModel.loadGroupInfo(topDetail.groupId)
                    groupsViewModel.loadMembers(topDetail.groupId)
                }
                val groupsState by groupsViewModel.uiState.collectAsStateWithLifecycle()
                GroupInfoScreen(
                    group = groupsState.currentGroup,
                    members = groupsState.members,
                    joinRequests = groupsState.joinRequests.size,
                    isLoading = groupsState.isLoading,
                    error = groupsState.error,
                    inviteLink = groupsState.inviteLink,
                    onNavigateBack = onNavigateBack,
                    onAddMembers = { onNavigate(MainNavigationDetailLocation.Contacts) },
                    onRemoveMember = { userId -> groupsViewModel.removeMember(topDetail.groupId, userId) },
                    onUpdateRole = { userId, role -> groupsViewModel.updateMemberRole(topDetail.groupId, userId, role) },
                    onUpdateGroup = { name, desc -> groupsViewModel.updateGroup(topDetail.groupId, name, desc) },
                    onCreateInviteLink = { groupsViewModel.createInviteLink(topDetail.groupId) },
                    onCopyInviteLink = { link -> clipboardManager.setText(AnnotatedString(link)) },
                    onViewJoinRequests = { onNavigate(MainNavigationDetailLocation.JoinRequests) },
                    onLeaveGroup = { groupsViewModel.leaveGroup(topDetail.groupId); onNavigateBack() },
                    onDeleteGroup = { groupsViewModel.deleteGroup(topDetail.groupId); onNavigateBack() },
                    onRefresh = { groupsViewModel.loadGroupInfo(topDetail.groupId) }
                )
            }
            // Contacts
            topDetail is MainNavigationDetailLocation.Contacts -> {
                val contactsViewModel: ContactsViewModel = viewModel()
                LaunchedEffect(Unit) { contactsViewModel.loadContacts() }
                val contactsState by contactsViewModel.uiState.collectAsStateWithLifecycle()
                ContactListScreen(
                    contacts = contactsState.contacts,
                    searchResults = contactsState.searchResults,
                    searchQuery = contactsState.searchQuery,
                    isLoading = contactsState.isLoading,
                    error = contactsState.error,
                    onContactClick = { userId -> onNavigate(MainNavigationDetailLocation.Profile(userId)) },
                    onSearchQueryChange = { contactsViewModel.searchContacts(it) },
                    onAddContact = { userId -> contactsViewModel.addContact(userId) },
                    onRefresh = { contactsViewModel.loadContacts() }
                )
            }
            // Status/Stories
            topDetail is MainNavigationDetailLocation.StatusFeed -> {
                val statusViewModel: StatusViewModel = viewModel()
                LaunchedEffect(Unit) { statusViewModel.loadFeed() }
                val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()
                StatusFeedScreen(
                    myStatus = statusState.myStatus,
                    feed = statusState.feed,
                    onStatusTap = { statusId ->
                        val index = statusState.feed.indexOfFirst { it.statusId == statusId }
                        onNavigate(MainNavigationDetailLocation.StatusViewer(statusId))
                    },
                    onCreateStatus = { onNavigate(MainNavigationDetailLocation.StatusCreate) }
                )
            }
            topDetail is MainNavigationDetailLocation.StatusCreate -> {
                val statusViewModel: StatusViewModel = viewModel()
                val mediaLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let {
                        statusViewModel.createMediaStatus(it.toString(), StatusPrivacy.AllContacts)
                        onNavigateBack()
                    }
                }
                StatusCreateScreen(
                    onCreateText = { text, bgColor, privacy ->
                        statusViewModel.createTextStatus(text, bgColor, privacy)
                        onNavigateBack()
                    },
                    onCreateMedia = { caption, privacy ->
                        mediaLauncher.launch("image/*")
                    },
                    onBack = onNavigateBack
                )
            }
            topDetail is MainNavigationDetailLocation.StatusViewer -> {
                val statusViewModel: StatusViewModel = viewModel()
                LaunchedEffect(Unit) { statusViewModel.loadFeed() }
                val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()
                val index = statusState.feed.indexOfFirst { it.statusId == topDetail.statusId }.coerceAtLeast(0)
                var showInfoSheet by remember { mutableStateOf(false) }
                var infoStatusId by remember { mutableStateOf("") }
                val currentStatus = statusState.feed.getOrNull(index)
                StatusViewerScreen(
                    statuses = statusState.feed,
                    initialIndex = index,
                    onReply = { statusId ->
                        val entry = statusState.feed.find { it.statusId == statusId }
                        entry?.let {
                            onNavigate(MainNavigationDetailLocation.Conversation(it.userId))
                        }
                    },
                    onClose = onNavigateBack,
                    onViewInfo = { statusId ->
                        infoStatusId = statusId
                        showInfoSheet = true
                    }
                )
                if (showInfoSheet) {
                    val sheetState = rememberModalBottomSheetState()
                    ModalBottomSheet(
                        onDismissRequest = { showInfoSheet = false },
                        sheetState = sheetState
                    ) {
                        val infoEntry = statusState.feed.find { it.statusId == infoStatusId }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Status Info", style = MaterialTheme.typography.titleMedium)
                            HorizontalDivider()
                            if (infoEntry != null) {
                                Text("From: ${infoEntry.username}", style = MaterialTheme.typography.bodyMedium)
                                Text("Type: ${infoEntry.type}", style = MaterialTheme.typography.bodyMedium)
                                Text("Created: ${infoEntry.createdAt}", style = MaterialTheme.typography.bodyMedium)
                                Text("Viewed: ${if (infoEntry.isViewed) "Yes" else "No"}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            // Other
            topDetail is MainNavigationDetailLocation.Channels -> {
                val channelViewModel: ChannelViewModel = viewModel()
                LaunchedEffect(Unit) { channelViewModel.loadFeed("default"); channelViewModel.loadMyChannels() }
                val channelState by channelViewModel.uiState.collectAsStateWithLifecycle()
                val myChannel = channelState.myChannels.firstOrNull()
                ChannelFeedScreen(
                    channelId = myChannel?.channelId ?: "default",
                    channelName = myChannel?.name ?: "Channels",
                    isSubscribed = myChannel?.isSubscribed ?: false,
                    isAdmin = myChannel?.isAdmin ?: false,
                    posts = channelState.feed,
                    pinnedPost = channelState.pinnedPost,
                    onSubscribe = {
                        val ch = myChannel
                        if (ch != null) {
                            if (ch.isSubscribed) channelViewModel.unsubscribe(ch.channelId)
                            else channelViewModel.subscribe(ch.channelId)
                        }
                    },
                    onShare = {},
                    onLoadMore = { channelViewModel.loadMore("default") },
                    onEditPost = { channelId, postId, content -> channelViewModel.editPost(channelId, postId, content) },
                    onDeletePost = { channelId, postId -> channelViewModel.deletePost(channelId, postId) },
                    onPinPost = { channelId, postId, pinned -> channelViewModel.pinPost(channelId, postId, pinned) }
                )
            }
            topDetail is MainNavigationDetailLocation.Stickers -> {
                val stickerViewModel: StickerViewModel = viewModel()
                LaunchedEffect(Unit) { stickerViewModel.loadFeatured() }
                val stickerState by stickerViewModel.uiState.collectAsStateWithLifecycle()
                StickerStoreScreen(
                    featured = stickerState.featured,
                    searchResults = stickerState.searchResults,
                    isLoading = stickerState.isLoading,
                    error = stickerState.error,
                    onInstall = { stickerViewModel.installPack(it) },
                    onSearch = { if (it.isNotBlank()) stickerViewModel.searchPacks(it) },
                    onPackClick = { stickerViewModel.loadPackDetail(it) },
                    onBack = onNavigateBack
                )
            }
            topDetail is MainNavigationDetailLocation.Profile -> {
                val profileViewModel: org.enchant.profile.ProfileViewModel = viewModel()
                LaunchedEffect(topDetail.userId) { profileViewModel.loadProfile(topDetail.userId) }
                val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
                val isOwn = topDetail.userId == "self"
                ProfileScreen(
                    userId = topDetail.userId,
                    isOwnProfile = isOwn,
                    profile = profileState.profile,
                    onBack = onNavigateBack,
                    onEdit = {},
                    onMessage = { onNavigate(MainNavigationDetailLocation.Conversation(topDetail.userId)) },
                    onCall = {
                        scope.launch { CallManager.startOutgoingCall(topDetail.userId, false) }
                    },
                    onBlock = { profileViewModel.blockUser(topDetail.userId) },
                    isBlocked = false
                )
            }
            // Group join
            topDetail is MainNavigationDetailLocation.JoinGroup -> {
                var inviteCode by remember { mutableStateOf("") }
                val groupsViewModel: GroupsViewModel = viewModel()
                AlertDialog(
                    onDismissRequest = { onNavigateBack() },
                    title = { Text("Join Group") },
                    text = {
                        OutlinedTextField(
                            value = inviteCode,
                            onValueChange = { inviteCode = it },
                            label = { Text("Invite code") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (inviteCode.isNotBlank()) {
                                groupsViewModel.joinViaLink(inviteCode)
                                onNavigateBack()
                            }
                        }) { Text("Join") }
                    },
                    dismissButton = {
                        TextButton(onClick = onNavigateBack) { Text("Cancel") }
                    }
                )
            }
            // Join requests
            topDetail is MainNavigationDetailLocation.JoinRequests -> {
                JoinRequestsScreen(onBack = onNavigateBack)
            }
            // Group settings
            topDetail is MainNavigationDetailLocation.GroupSettings -> {
                val groupsViewModel: GroupsViewModel = viewModel()
                val groupsState by groupsViewModel.uiState.collectAsStateWithLifecycle()
                GroupSettingsScreen(
                    disappearingMessagesEnabled = groupsState.disappearingMessagesEnabled,
                    disappearingMessagesDurationSeconds = groupsState.disappearingMessagesDurationSeconds,
                    isLoading = groupsState.isLoading,
                    error = groupsState.error,
                    onDisappearingMessagesToggle = { enabled, duration ->
                        groupsViewModel.updateDisappearingMessages(topDetail.groupId, enabled, duration)
                    },
                    onBack = onNavigateBack
                )
            }
            // Call log
            topDetail is MainNavigationDetailLocation.Calls.EditCallLinkName -> {
                CallLinkDetailContent(roomId = topDetail.callLinkRoomId)
            }
            topDetail is MainNavigationDetailLocation.Calls.CallDetail -> {
                val callLogViewModel: CallLogViewModel = viewModel()
                LaunchedEffect(Unit) { callLogViewModel.loadCallLogs() }
                val callLogState by callLogViewModel.uiState.collectAsStateWithLifecycle()
                val entry = callLogState.entries.find { it.callId == topDetail.callId }
                CallDetailScreen(
                    entry = entry,
                    onCall = {
                        entry?.let {
                            scope.launch {
                                CallManager.startOutgoingCall(it.remoteUserId, false)
                            }
                        }
                    },
                    onMessage = { entry?.let { onNavigate(MainNavigationDetailLocation.Conversation(it.remoteUserId)) } },
                    onNavigateBack = onNavigateBack
                )
            }
            else -> {
                EmptyDetailScreen()
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun ConversationDetailContent(
    conversationId: String,
    onNavigateBack: () -> Unit,
    onStartCall: (String, Boolean) -> Unit
) {
    org.enchant.chat.ConversationScreen(
        conversationId = conversationId,
        onNavigateBack = onNavigateBack,
        onStartCall = onStartCall
    )
}

@Composable
private fun CallLinkDetailContent(roomId: String) {
    val scope = rememberCoroutineScope()
    var callLinkData by remember { mutableStateOf<org.enchant.core.calls.CallLinkData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val callLinkManager = remember { org.enchant.calls.calllinks.CallLinkManager(org.enchant.core.network.ApiClient.getInstance()) }

    LaunchedEffect(roomId) {
        isLoading = true
        error = null
        val result = callLinkManager.getCallLink(roomId)
        result.fold(
            onSuccess = { callLinkData = it; isLoading = false },
            onFailure = { error = it.message; isLoading = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else if (error != null) {
            Text("Error: $error", color = MaterialTheme.colorScheme.error)
        } else {
            val data = callLinkData
            Icon(
                Icons.Default.Link,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                data?.name ?: "Call Link",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Room: $roomId",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (data != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "By: ${data.creatorId.take(12)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (data.isActive) "Active" else "Inactive",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (data.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    scope.launch {
                        org.enchant.core.calls.CallManager.startOutgoingCall(roomId, false)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Join Call")
            }
        }
    }
}
