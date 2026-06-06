package org.enchant

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.enchant.auth.AuthViewModel
import org.enchant.calls.CallViewModel
import org.enchant.chatlist.ChatListNavDisplay
import org.enchant.chatlist.ConversationListViewModel
import org.enchant.core.calls.CallStatus
import org.enchant.main.EmptyDetailScreen
import org.enchant.main.MainNavigationBar
import org.enchant.main.MainNavigationDetailLocationEffect
import org.enchant.main.MainNavigationRail
import org.enchant.MainNavigationViewModel
import org.enchant.main.MainFloatingActionButtons
import org.enchant.main.MainFloatingActionButtonsCallback
import org.enchant.settings.screens.*
import org.enchant.window.AppScaffold
import org.enchant.window.rememberAppScaffoldNavigator

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
    }

    LaunchedEffect(callUiState.callState.status) {
        val status = callUiState.callState.status
        when (status) {
            CallStatus.RINGING, CallStatus.CALLING, CallStatus.CONNECTED -> {
                mainNavViewModel.goTo(MainNavigationDetailLocation.Calls.CallLinks.EditCallLinkName(
                    callUiState.callState.callId ?: ""
                ))
            }
            CallStatus.IDLE -> {}
            else -> {}
        }
    }

    MainNavigationDetailLocationEffect(mainNavigationViewModel = mainNavViewModel)

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
                }
            )
        },
        primaryContent = {
            DetailPaneContent(
                detailStack = getDetailStackForLocation(mainNavState.currentListLocation, mainNavViewModel),
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
}

@Composable
private fun ListPaneContent(
    currentListLocation: MainNavigationListLocation,
    onConversationClick: (String) -> Unit,
    onArchiveClick: (String) -> Unit
) {
    val listViewModel: ConversationListViewModel = viewModel()

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentListLocation) {
            MainNavigationListLocation.CHATS -> {
                ChatListNavDisplay(
                    viewModel = listViewModel,
                    onNavigateToConversation = onConversationClick
                )
            }
            MainNavigationListLocation.ARCHIVE -> {
                ChatListNavDisplay(
                    viewModel = listViewModel,
                    onNavigateToConversation = onArchiveClick
                )
            }
            MainNavigationListLocation.CALLS -> {
                CallsListContent()
            }
            MainNavigationListLocation.STORIES -> {
                StoriesListContent()
            }
        }
    }
}

@Composable
private fun CallsListContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Calls", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun StoriesListContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Stories", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun DetailPaneContent(
    detailStack: List<MainNavigationDetailLocation>,
    onNavigate: (MainNavigationDetailLocation) -> Unit,
    onNavigateBack: () -> Unit
) {
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
                ConversationDetailContent(conversationId = topDetail.conversationId)
            }
            topDetail is MainNavigationDetailLocation.Calls.CallLinks.EditCallLinkName -> {
                CallLinkDetailContent(roomId = topDetail.callLinkRoomId)
            }
            // Settings routes
            topDetail is MainNavigationDetailLocation.Settings -> {
                SettingsHomeScreen(
                    onNavigateToAccount = { onNavigate(MainNavigationDetailLocation.AccountSettings) },
                    onNavigateToSecurity = { onNavigate(MainNavigationDetailLocation.SecuritySettings) },
                    onNavigateToPrivacy = { onNavigate(MainNavigationDetailLocation.PrivacySettings) },
                    onNavigateToNotifications = { onNavigate(MainNavigationDetailLocation.NotificationSettings) },
                    onNavigateToAppearance = { onNavigate(MainNavigationDetailLocation.AppearanceSettings) },
                    onNavigateToChats = { onNavigate(MainNavigationDetailLocation.ChatsSettings) },
                    onNavigateToStorage = { onNavigate(MainNavigationDetailLocation.StorageSettings) },
                    onNavigateToAbout = { onNavigate(MainNavigationDetailLocation.About) }
                )
            }
            topDetail is MainNavigationDetailLocation.AccountSettings -> {
                AccountSettingsScreen(
                    displayName = "",
                    username = null,
                    about = null,
                    onNavigateBack = onNavigateBack,
                    onSave = {},
                    onDeleteAccount = {}
                )
            }
            topDetail is MainNavigationDetailLocation.SecuritySettings -> {
                SecuritySettingsScreen(
                    onSetupTwoStep = {},
                    onBack = onNavigateBack
                )
            }
            topDetail is MainNavigationDetailLocation.PrivacySettings -> {
                PrivacySettingsScreen(
                    lastSeenVisibility = "everyone",
                    onlineVisibility = true,
                    avatarVisibility = "everyone",
                    aboutVisibility = "everyone",
                    readReceipts = true,
                    blockedCount = 0,
                    onLastSeenChange = {},
                    onOnlineVisibilityChange = {},
                    onAvatarVisibilityChange = {},
                    onAboutVisibilityChange = {},
                    onReadReceiptsChange = {},
                    onBlockedUsersClick = { onNavigate(MainNavigationDetailLocation.BlockedUsers) },
                    onNavigateBack = onNavigateBack
                )
            }
            topDetail is MainNavigationDetailLocation.NotificationSettings -> {
                NotificationsSettingsScreen(
                    masterEnabled = true,
                    messageNotifications = true,
                    showPreview = true,
                    groupNotifications = true,
                    onMasterEnabledChange = {},
                    onMessageNotificationsChange = {},
                    onShowPreviewChange = {},
                    onGroupNotificationsChange = {},
                    onNavigateBack = onNavigateBack
                )
            }
            topDetail is MainNavigationDetailLocation.AppearanceSettings -> {
                AppearanceSettingsScreen(
                    currentTheme = "system",
                    fontSize = 16f,
                    onThemeChange = {},
                    onFontSizeChange = {},
                    onNavigateBack = onNavigateBack
                )
            }
            topDetail is MainNavigationDetailLocation.ChatsSettings -> {
                ChatsSettingsScreen(
                    defaultDisappearingTimer = 0,
                    autoDownloadWifi = true,
                    autoDownloadCellular = false,
                    onDisappearingTimerChange = {},
                    onAutoDownloadChange = { _, _ -> },
                    onNavigateBack = onNavigateBack
                )
            }
            topDetail is MainNavigationDetailLocation.StorageSettings -> {
                StorageSettingsScreen(
                    storageInfo = null,
                    isProcessing = false,
                    onClearCache = {},
                    onTrimMessages = {},
                    onNavigateBack = onNavigateBack
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
                PlaceholderScreen("Groups")
            }
            topDetail is MainNavigationDetailLocation.CreateGroup -> {
                PlaceholderScreen("Create Group")
            }
            topDetail is MainNavigationDetailLocation.GroupInfo -> {
                PlaceholderScreen("Group Info: ${topDetail.groupId}")
            }
            // Contacts
            topDetail is MainNavigationDetailLocation.Contacts -> {
                PlaceholderScreen("Contacts")
            }
            // Status/Stories
            topDetail is MainNavigationDetailLocation.StatusFeed -> {
                PlaceholderScreen("Status Feed")
            }
            topDetail is MainNavigationDetailLocation.StatusCreate -> {
                PlaceholderScreen("Create Status")
            }
            topDetail is MainNavigationDetailLocation.StatusViewer -> {
                PlaceholderScreen("Status: ${topDetail.statusId}")
            }
            // Other
            topDetail is MainNavigationDetailLocation.Channels -> {
                PlaceholderScreen("Channels")
            }
            topDetail is MainNavigationDetailLocation.Stickers -> {
                PlaceholderScreen("Stickers")
            }
            topDetail is MainNavigationDetailLocation.Profile -> {
                PlaceholderScreen("Profile: ${topDetail.userId}")
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
private fun ConversationDetailContent(conversationId: String) {
    org.enchant.chat.ConversationScreen(
        conversationId = conversationId,
        onNavigateBack = { },
        onStartCall = { _, _ -> }
    )
}

@Composable
private fun CallLinkDetailContent(roomId: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Call Link: $roomId", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
    }
}

private fun getDetailStackForLocation(
    location: MainNavigationListLocation,
    viewModel: MainNavigationViewModel
): List<MainNavigationDetailLocation> {
    return when (location) {
        MainNavigationListLocation.CHATS -> viewModel.chatsDetailStack.value
        MainNavigationListLocation.ARCHIVE -> viewModel.archiveDetailStack.value
        MainNavigationListLocation.CALLS -> viewModel.callsDetailStack.value
        MainNavigationListLocation.STORIES -> viewModel.storiesDetailStack.value
    }
}
