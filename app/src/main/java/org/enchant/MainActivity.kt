package org.enchant

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.enchant.auth.AuthViewModel
import org.enchant.auth.screens.AppLockScreen
import org.enchant.auth.screens.KeyGenerationScreen
import org.enchant.auth.screens.OtpVerifyScreen
import org.enchant.auth.screens.PermissionsScreen
import org.enchant.auth.screens.PhoneEntryScreen
import org.enchant.auth.screens.ProfileSetupScreen
import org.enchant.auth.screens.RestorePromptScreen
import org.enchant.auth.screens.TwoStepPinScreen
import org.enchant.auth.screens.UsernamePickerScreen
import org.enchant.auth.screens.WelcomeScreen
import org.enchant.calls.CallViewModel
import org.enchant.core.base.NotionTheme
import org.enchant.core.calls.CallLogFilter
import org.enchant.calls.SafetyNumberDialog
import org.enchant.calls.screens.ActiveVideoCallScreen
import org.enchant.calls.screens.ActiveVoiceCallScreen
import org.enchant.calls.screens.GroupCallScreen
import org.enchant.calls.screens.IncomingCallScreen
import org.enchant.calls.screens.OutgoingCallScreen

import org.enchant.chat.ConversationScreen
import org.enchant.chatlist.ConversationListScreen
import org.enchant.chatlist.ConversationListViewModel
import org.enchant.contacts.screens.ContactListScreen
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.CallStatusEnum
import org.enchant.core.auth.AuthManager
import org.enchant.core.auth.AuthState
import org.enchant.core.auth.RegistrationState
import org.enchant.core.crypto.SessionManager
import org.enchant.groups.screens.CreateGroupScreen
import org.enchant.groups.screens.GroupListScreen
import org.enchant.groups.screens.JoinRequestsScreen
import org.enchant.settings.screens.SettingsHomeScreen
import org.enchant.settings.screens.AccountSettingsScreen
import org.enchant.settings.screens.AppearanceSettingsScreen
import org.enchant.settings.screens.BackupSettingsScreen
import org.enchant.settings.screens.BlockedUsersScreen
import org.enchant.settings.screens.ChatsSettingsScreen
import org.enchant.settings.screens.NotificationsSettingsScreen
import org.enchant.settings.screens.PrivacySettingsScreen
import org.enchant.settings.screens.SecuritySettingsScreen
import org.enchant.settings.screens.StorageSettingsScreen
import org.enchant.settings.screens.AboutScreen
import org.enchant.settings.SettingsViewModel
import org.enchant.calls.screens.CallLogScreen
import org.enchant.channels.screens.ChannelFeedScreen
import org.enchant.location.LocationPickerScreen
import org.enchant.polls.screens.PollCreateSheet
import org.enchant.profile.screens.ProfileScreen
import org.enchant.groups.screens.GroupInfoScreen
import org.enchant.status.screens.StatusCreateScreen
import org.enchant.status.screens.StatusFeedScreen
import org.enchant.status.screens.StatusViewerScreen
import org.enchant.stickers.screens.StickerStoreScreen
import org.enchant.contacts.ContactsViewModel
import org.enchant.groups.GroupsViewModel
import org.enchant.status.StatusViewModel
import org.enchant.channels.ChannelViewModel
import org.enchant.stickers.StickerViewModel
import org.enchant.calls.CallLogViewModel
import org.enchant.profile.ProfileViewModel
import org.enchant.backup.BackupViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleCallIntent(intent)
        setContent {
            NotionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallIntent(intent)
    }

    private fun handleCallIntent(intent: Intent?) {
        val data = intent?.data ?: return
        when (data.host) {
            "call-link" -> {
                val roomId = data.pathSegments.firstOrNull()
                if (roomId != null) {
                    // Launch call link join screen
                    // Navigate handled reactively via CallViewModel
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsState()
    val callViewModel: CallViewModel = viewModel()
    val callUiState by callViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(callUiState.callState.status) {
        when (callUiState.callState.status) {
            CallStatusEnum.RINGING -> navController.navigate("incoming_call/${callUiState.callState.callId}")
            CallStatusEnum.CALLING -> navController.navigate("outgoing_call/${callUiState.callState.remoteUserId}")
            CallStatusEnum.CONNECTED -> {
                if (navController.currentDestination?.route?.startsWith("incoming_") == true) {
                    navController.popBackStack()
                }
                if (callUiState.callState.isVideoCall) {
                    navController.navigate("active_video_call/${callUiState.callState.callId}")
                } else {
                    navController.navigate("active_voice_call/${callUiState.callState.callId}")
                }
            }
            CallStatusEnum.IDLE -> {
                if (navController.currentDestination?.route?.startsWith("active_") == true ||
                    navController.currentDestination?.route?.startsWith("incoming_") == true ||
                    navController.currentDestination?.route?.startsWith("outgoing_") == true) {
                    navController.popBackStack("chat_list", inclusive = false)
                }
            }
            else -> {}
        }
    }

    LaunchedEffect(callUiState.callState.status) {
        if (callUiState.callState.status != CallStatusEnum.IDLE) {
            val intent = Intent(context, org.enchant.core.calls.CallForegroundService::class.java).apply {
                action = org.enchant.core.calls.CallForegroundService.ACTION_START_CALL
                putExtra("call_id", callUiState.callState.callId)
                putExtra("is_video", callUiState.callState.isVideoCall)
                putExtra("remote_user_id", callUiState.callState.remoteUserId)
            }
            context.startForegroundService(intent)
        }
    }

    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        val data = activity.intent?.data ?: return@LaunchedEffect
        when (data.host) {
            "chat" -> data.pathSegments.firstOrNull()?.let {
                navController.navigate("conversation/$it")
            }
            "group" -> data.pathSegments.firstOrNull()?.let {
                navController.navigate("group_info/$it")
            }
            "call-link" -> { /* call-link deep links handled separately */ }
        }
    }

    val startDest = when (authState) {
        is AuthState.Authenticated -> "chat_list"
        else -> "splash"
    }

    NavHost(navController = navController, startDestination = startDest) {
        composable("splash") {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            LaunchedEffect(authState) {
                var waited = 0
                while (!org.enchant.DI.isInitialized && waited < 100) {
                    kotlinx.coroutines.delay(50)
                    waited++
                }
                when (authState) {
                    is AuthState.Authenticated -> {
                        context.startService(
                            Intent(context, org.enchant.core.network.WebSocketService::class.java).apply {
                                action = org.enchant.core.network.WebSocketService.ACTION_CONNECT
                            }
                        )
                        navController.navigate("chat_list") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                    is AuthState.Unauthenticated, is AuthState.Unknown -> {
                        navController.navigate("welcome") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                    else -> {}
                }
            }
        }

        composable("welcome") {
            WelcomeScreen(
                onTermsAccepted = { navController.navigate("phone_entry") },
                onRestore = { navController.navigate("restore_prompt") }
            )
        }

        composable("phone_entry") {
            val state by authViewModel.registrationState.collectAsState()
            PhoneEntryScreen(
                onCountrySelected = {},
                onPhoneNumberChanged = {},
                onPhoneNumberSubmitted = { authViewModel.requestOtp(it) },
                onNavigateBack = { navController.popBackStack() },
                isLoading = state is RegistrationState.Loading,
                errorMessage = (state as? RegistrationState.Error)?.message
            )
            LaunchedEffect(state) {
                when (state) {
                    is RegistrationState.OtpVerification -> {
                        navController.navigate("otp_verify") {
                            popUpTo("phone_entry") { inclusive = true }
                        }
                    }
                    else -> {}
                }
            }
        }

        composable("otp_verify") {
            val state by authViewModel.registrationState.collectAsState()
            val otpState = state as? RegistrationState.OtpVerification
            OtpVerifyScreen(
                identifier = otpState?.identifier ?: "",
                onCodeSubmitted = { authViewModel.verifyOtp(it) },
                onResendCode = { authViewModel.resendOtp() },
                onWrongNumber = { navController.popBackStack() },
                isLoading = state is RegistrationState.Loading,
                errorMessage = (state as? RegistrationState.Error)?.message
            )
            LaunchedEffect(state) {
                when (state) {
                    is RegistrationState.Permissions -> {
                        navController.navigate("permissions") {
                            popUpTo("otp_verify") { inclusive = true }
                        }
                    }
                    else -> {}
                }
            }
        }

        composable("permissions") {
            PermissionsScreen(
                onPermissionsGranted = { navController.navigate("profile_setup") },
                onSkip = { navController.navigate("profile_setup") }
            )
        }

        composable("profile_setup") {
            ProfileSetupScreen(
                onProfileDataEntered = { displayName, about, _ ->
                    AuthManager.pendingDisplayName = displayName
                    AuthManager.pendingAbout = about
                    navController.navigate("username_picker")
                }
            )
        }

        composable("username_picker") {
            val state by authViewModel.registrationState.collectAsState()
            var isSubmitting by remember { mutableStateOf(false) }
            var localError by remember { mutableStateOf<String?>(null) }

            UsernamePickerScreen(
                onUsernameEntered = { username ->
                    val displayName = AuthManager.pendingDisplayName ?: run {
                        localError = "Display name not set. Go back and try again."
                        return@UsernamePickerScreen
                    }
                    val about = AuthManager.pendingAbout
                    isSubmitting = true
                    localError = null
                    scope.launch {
                        val result = AuthManager.updateProfile(username, displayName, about)
                        if (result.isSuccess) {
                            AuthManager.pendingDisplayName = null
                            AuthManager.pendingAbout = null
                            navController.navigate("key_generation")
                        } else {
                            localError = result.exceptionOrNull()?.message ?: "Failed to save profile"
                        }
                        isSubmitting = false
                    }
                },
                onSkip = {
                    val displayName = AuthManager.pendingDisplayName ?: run {
                        localError = "Display name not set. Go back and try again."
                        return@UsernamePickerScreen
                    }
                    val about = AuthManager.pendingAbout
                    isSubmitting = true
                    localError = null
                    scope.launch {
                        val username = "user_${System.currentTimeMillis()}"
                        val result = AuthManager.updateProfile(username, displayName, about)
                        if (result.isSuccess) {
                            AuthManager.pendingDisplayName = null
                            AuthManager.pendingAbout = null
                            navController.navigate("key_generation")
                        } else {
                            localError = result.exceptionOrNull()?.message ?: "Failed to save profile"
                        }
                        isSubmitting = false
                    }
                },
                onCheckAvailability = { prefix ->
                    try {
                        val result = AuthManager.searchUsername(prefix)
                        if (result.isSuccess) {
                            result.getOrDefault(emptyList()).isEmpty()
                        } else {
                            throw result.exceptionOrNull() ?: Exception("check failed")
                        }
                    } catch (_: Exception) { null }
                },
                isLoading = isSubmitting,
                errorMessage = localError
            )
        }

        composable("key_generation") {
            val state by authViewModel.registrationState.collectAsState()
            var progress by remember { mutableStateOf(0f) }

            LaunchedEffect(Unit) {
                progress = 0.2f
                authViewModel.registerKeys()
            }

            LaunchedEffect(state) {
                when (state) {
                    is RegistrationState.KeyGeneration -> progress = 0.6f
                    is RegistrationState.Complete -> progress = 1f
                    is RegistrationState.Error -> progress = 0f
                    else -> {}
                }
            }

            KeyGenerationScreen(
                onKeysGenerated = {
                    context.startService(
                        Intent(context, org.enchant.core.network.WebSocketService::class.java).apply {
                            action = org.enchant.core.network.WebSocketService.ACTION_CONNECT
                        }
                    )
                    navController.navigate("chat_list") {
                        popUpTo("welcome") { inclusive = true }
                    }
                },
                onRetry = { authViewModel.registerKeys() },
                progress = progress,
                isError = state is RegistrationState.Error,
                errorMessage = (state as? RegistrationState.Error)?.message
            )
        }

        composable("pin_creation") {
            TwoStepPinScreen.Screen(
                onPinCreated = {
                    navController.navigate("chat_list") {
                        popUpTo("welcome") { inclusive = true }
                    }
                }
            )
        }

        composable("contacts") {
            val contactsViewModel: ContactsViewModel = viewModel()
            val state by contactsViewModel.uiState.collectAsState()
            LaunchedEffect(Unit) { contactsViewModel.loadContacts() }
            ContactListScreen(
                contacts = state.contacts,
                searchResults = state.searchResults,
                searchQuery = state.searchQuery,
                isLoading = state.isLoading,
                error = state.error,
                onContactClick = { userId -> navController.navigate("conversation/$userId") },
                onSearchQueryChange = { contactsViewModel.searchContacts(it) },
                onAddContact = { },
                onRefresh = { contactsViewModel.loadContacts() }
            )
        }

        composable("create_group") {
            val groupsViewModel: GroupsViewModel = viewModel()
            val state by groupsViewModel.uiState.collectAsState()
            CreateGroupScreen(
                onGroupCreated = { groupId -> navController.popBackStack() },
                onNavigateBack = { navController.popBackStack() },
                onCreateGroup = { name, desc, members -> groupsViewModel.createGroup(name, desc, members) },
                isLoading = state.isLoading
            )
        }

        composable("groups") {
            val groupsViewModel: GroupsViewModel = viewModel()
            val state by groupsViewModel.uiState.collectAsState()
            LaunchedEffect(Unit) { groupsViewModel.loadGroups() }
            GroupListScreen(
                groups = state.groups,
                isLoading = state.isLoading,
                error = state.error,
                onGroupClick = { groupId -> navController.navigate("group_info/$groupId") },
                onCreateGroup = { navController.navigate("create_group") },
                onJoinGroup = { groupsViewModel.joinViaLink("") },
                onRefresh = { groupsViewModel.loadGroups() }
            )
        }

        composable("restore_prompt") {
            RestorePromptScreen(
                hasBackup = false,
                onRestore = {},
                onStartFresh = { navController.navigate("phone_entry") }
            )
        }

        composable("app_lock") {
            AppLockScreen(
                onVerified = { navController.popBackStack() },
                onDismiss = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsHomeScreen(
                onNavigateToAccount = { navController.navigate("account_settings") },
                onNavigateToSecurity = { navController.navigate("security_settings") },
                onNavigateToPrivacy = { navController.navigate("privacy_settings") },
                onNavigateToNotifications = { navController.navigate("notification_settings") },
                onNavigateToAppearance = { navController.navigate("appearance_settings") },
                onNavigateToChats = { navController.navigate("chats_settings") },
                onNavigateToStorage = { navController.navigate("storage_settings") },
                onNavigateToAbout = { navController.navigate("about") }
            )
        }

        composable("account_settings") {
            val vm: SettingsViewModel = viewModel()
            val state by vm.uiState.collectAsState()
            LaunchedEffect(Unit) { vm.loadSettings() }
            AccountSettingsScreen(
                displayName = state.displayName,
                username = state.username,
                about = state.about,
                devices = state.devices,
                isLoading = state.isProcessing,
                onRevokeDevice = { vm.revokeDevice(it) },
                onDeleteAccount = { vm.deleteAccount() },
                onBack = { navController.popBackStack() }
            )
        }

        composable("security_settings") {
            SecuritySettingsScreen(
                onSetupTwoStep = { navController.navigate("pin_creation") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("privacy_settings") {
            val vm: SettingsViewModel = viewModel()
            val state by vm.uiState.collectAsState()
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
                onViewBlockedUsers = { navController.navigate("blocked_users") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("notification_settings") {
            val vm: SettingsViewModel = viewModel()
            val state by vm.uiState.collectAsState()
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
                onBack = { navController.popBackStack() }
            )
        }

        composable("appearance_settings") {
            val vm: SettingsViewModel = viewModel()
            val state by vm.uiState.collectAsState()
            LaunchedEffect(Unit) { vm.loadSettings() }
            AppearanceSettingsScreen(
                currentTheme = state.theme,
                fontSize = state.fontSize,
                onThemeChange = { vm.updateTheme(it) },
                onFontSizeChange = { vm.updateFontSize(it) },
                onBack = { navController.popBackStack() }
            )
        }

        composable("chats_settings") {
            ChatsSettingsScreen(
                defaultDisappearingTimer = 0,
                autoDownloadWifi = true,
                autoDownloadCellular = false,
                onDisappearingTimerChange = {},
                onAutoDownloadWifiChange = {},
                onAutoDownloadCellularChange = {},
                onBackupSettings = { navController.navigate("backup_settings") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("storage_settings") {
            StorageSettingsScreen(
                storageInfo = null,
                isProcessing = false,
                onClearCache = {},
                onBack = { navController.popBackStack() }
            )
        }

        composable("about") {
            AboutScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable("backup_settings") {
            BackupSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable("blocked_users") {
            BlockedUsersScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable("status_feed") {
            val statusViewModel: StatusViewModel = viewModel()
            val state by statusViewModel.uiState.collectAsState()
            LaunchedEffect(Unit) { statusViewModel.loadFeed() }
            StatusFeedScreen(
                myStatus = state.myStatus,
                feed = state.feed.groupBy { it.userId },
                onStatusTap = { statusId -> navController.navigate("status_viewer/$statusId") },
                onCreateStatus = { navController.navigate("status_create") }
            )
        }

        composable("status_create") {
            val statusViewModel: StatusViewModel = viewModel()
            StatusCreateScreen(
                onCreateText = { text, bg, _ ->
                    statusViewModel.createTextStatus(text, bg, org.enchant.status.StatusPrivacy.AllContacts)
                    navController.popBackStack()
                },
                onCreateMedia = { _, _ -> navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable("status_viewer/{statusId}") { backStackEntry ->
            val statusId = backStackEntry.arguments?.getString("statusId") ?: ""
            val statusViewModel: StatusViewModel = viewModel()
            val state by statusViewModel.uiState.collectAsState()
            LaunchedEffect(Unit) { statusViewModel.loadFeed() }
            StatusViewerScreen(
                statuses = state.feed,
                initialIndex = state.feed.indexOfFirst { it.statusId == statusId }.coerceAtLeast(0),
                onReply = { },
                onClose = { navController.popBackStack() },
                onViewInfo = { id ->
                    statusViewModel.viewStatus(id)
                }
            )
        }

        composable("channels_feed") {
            val channelViewModel: ChannelViewModel = viewModel()
            val state by channelViewModel.uiState.collectAsState()
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

        composable("group_info/{groupId}") { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val groupsViewModel: GroupsViewModel = viewModel()
            val state by groupsViewModel.uiState.collectAsState()
            LaunchedEffect(groupId) { groupsViewModel.loadGroupInfo(groupId) }
            GroupInfoScreen(
                group = state.currentGroup,
                members = state.members,
                joinRequests = state.joinRequests.size,
                isLoading = state.isLoading,
                error = state.error,
                inviteLink = state.inviteLink,
                onNavigateBack = { navController.popBackStack() },
                onAddMembers = { groupsViewModel.loadMembers(groupId) },
                onRemoveMember = { userId -> groupsViewModel.removeMember(groupId, userId) },
                onUpdateRole = { userId, role -> groupsViewModel.updateMemberRole(groupId, userId, role) },
                onCreateInviteLink = { groupsViewModel.createInviteLink(groupId) },
                onCopyInviteLink = { _ -> },
                onViewJoinRequests = { navController.navigate("join_requests") },
                onLeaveGroup = { },
                onDeleteGroup = { groupsViewModel.deleteGroup(groupId) },
                onRefresh = { groupsViewModel.loadGroupInfo(groupId) }
            )
        }

        composable("stickers") {
            val stickerViewModel: StickerViewModel = viewModel()
            val state by stickerViewModel.uiState.collectAsState()
            LaunchedEffect(Unit) { stickerViewModel.loadFeatured() }
            StickerStoreScreen(
                featured = state.featured,
                searchResults = state.searchResults,
                isLoading = state.isLoading,
                error = state.error,
                onInstall = { packId -> stickerViewModel.installPack(packId) },
                onSearch = { query -> stickerViewModel.searchPacks(query) },
                onPackClick = { packId -> stickerViewModel.loadPackDetail(packId) },
                onBack = { navController.popBackStack() }
            )
        }

        composable("join_requests") {
            JoinRequestsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("chat_list") {
            val listViewModel: ConversationListViewModel = viewModel()
            ConversationListScreen(
                viewModel = listViewModel,
                onConversationClick = { convId ->
                    navController.navigate("conversation/$convId")
                },
                onNewChat = { navController.navigate("contacts") },
                onNewGroup = { navController.navigate("create_group") }
            )
        }

        composable("conversation/{conversationId}") { backStackEntry ->
            val convId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            ConversationScreen(
                conversationId = convId,
                onNavigateBack = { navController.popBackStack() },
                onStartCall = { userId, isVideo -> callViewModel.startCall(userId, isVideo) }
            )
        }

        composable("incoming_call/{callId}") {
            val state = callUiState.callState
            IncomingCallScreen(
                callerName = state.remoteUserId ?: "Unknown",
                callerId = state.remoteUserId ?: "",
                isVideoCall = state.isVideoCall,
                onAcceptAudio = { callViewModel.acceptCall(false) },
                onAcceptVideo = { callViewModel.acceptCall(true) },
                onDecline = { callViewModel.denyCall() }
            )
        }

        composable("outgoing_call/{userId}") {
            OutgoingCallScreen(
                remoteName = callUiState.callState.remoteUserId ?: "Unknown",
                isVideoCall = callUiState.callState.isVideoCall,
                onToggleSpeaker = { callViewModel.toggleSpeaker() },
                onEndCall = { callViewModel.endCall() },
                onSwitchToVideo = { callViewModel.toggleVideo() }
            )
        }

        composable("active_voice_call/{callId}") {
            val state = callUiState.callState
            var showSafetyNumber by remember { mutableStateOf(false) }
            val remoteUserId = state.remoteUserId ?: ""
            var safetyNumber by remember { mutableStateOf("UNVERIFIED") }
            LaunchedEffect(remoteUserId) {
                withContext(Dispatchers.Default) {
                    safetyNumber = SessionManager.getSafetyNumber(remoteUserId)
                }
            }
            if (showSafetyNumber) {
                SafetyNumberDialog(
                    safetyNumber = safetyNumber,
                    onDismiss = { showSafetyNumber = false },
                    onVerify = {
                        org.enchant.core.base.SecurePreferences.putString("safety_number", remoteUserId)
                        scope.launch {
                            try {
                                val pool = org.enchant.core.database.DatabasePool.instance
                                if (pool != null) {
                                    val dao = org.enchant.core.database.dao.IdentityDao(pool)
                                    dao.setVerified("safety:$remoteUserId", 1)
                                }
                            } catch (_: Exception) {}
                        }
                        showSafetyNumber = false
                    }
                )
            }
            ActiveVoiceCallScreen(
                remoteName = state.remoteUserId ?: "Unknown",
                durationSeconds = state.durationSeconds,
                isMuted = state.isMuted,
                isSpeakerOn = state.isSpeakerOn,
                signalStrength = 3,
                onToggleMute = { callViewModel.toggleMute() },
                onToggleSpeaker = { callViewModel.toggleSpeaker() },
                onEndCall = { callViewModel.endCall() },
                onShowKeypad = {},
                onSwitchToVideo = { callViewModel.toggleVideo() },
                onShowSafetyNumber = { showSafetyNumber = true }
            )
        }

        composable("active_video_call/{callId}") {
            val state = callUiState.callState
            ActiveVideoCallScreen(
                remoteUserId = state.remoteUserId ?: "Unknown",
                durationSeconds = state.durationSeconds,
                isMuted = state.isMuted,
                isSpeakerOn = state.isSpeakerOn,
                onToggleMute = { callViewModel.toggleMute() },
                onToggleSpeaker = { callViewModel.toggleSpeaker() },
                onEndCall = { callViewModel.endCall() },
                onFlipCamera = { callViewModel.flipCamera() }
            )
        }

        composable("call_log") {
            val callLogViewModel: CallLogViewModel = viewModel()
            val state by callLogViewModel.uiState.collectAsState()
            CallLogScreen(
                entries = state.entries,
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

        composable("location_picker") {
            LocationPickerScreen(
                onLocationSelected = { _, _, _ -> navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable("poll_create/{conversationId}") { backStackEntry ->
            val convId = backStackEntry.arguments?.getString("conversationId") ?: ""
            PollCreateSheet(
                onCreate = { _, _, _, _ -> navController.popBackStack() },
                onDismiss = { navController.popBackStack() },
                isCreating = false
            )
        }

        composable("search") {
            var q by remember { mutableStateOf("") }
            var results by remember { mutableStateOf<List<org.enchant.core.database.entity.MessageEntity>>(emptyList()) }
            var loading by remember { mutableStateOf(false) }

            LaunchedEffect(q) {
                if (q.length >= 2) {
                    loading = true
                    val pool = org.enchant.core.database.DatabasePool.instance
                    if (pool != null) {
                        kotlinx.coroutines.delay(300)
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

        composable("qr_code") {
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                android.widget.Toast.makeText(context, "QR Code coming soon", android.widget.Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        }

        composable("qr_scanner") {
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                android.widget.Toast.makeText(context, "QR Scanner coming soon", android.widget.Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        }

        composable("profile/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            val profileViewModel: ProfileViewModel = viewModel()
            val state by profileViewModel.uiState.collectAsState()
            LaunchedEffect(userId) { profileViewModel.loadProfile(userId) }
            ProfileScreen(
                userId = userId,
                isOwnProfile = userId == org.enchant.core.base.SecurePreferences.getString("auth.user_id"),
                profile = state.profile,
                onEdit = { },
                onMessage = { navController.navigate("conversation/$userId") },
                onCall = { },
                onBlock = { profileViewModel.blockUser(userId) },
                isBlocked = state.blockedUsers.any { it.userId == userId }
            )
        }

        composable("group_call/{callId}") {
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

        composable("share_target") {
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                android.widget.Toast.makeText(context, "Sharing handled by ShareTargetActivity", android.widget.Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        }

        composable("media_viewer/{conversationId}") { backStackEntry ->
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                android.widget.Toast.makeText(context, "Media viewer requires a media attachment", android.widget.Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        }

    }
}
