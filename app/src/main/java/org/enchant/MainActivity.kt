package org.enchant

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
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
import org.enchant.groups.screens.GroupInfoScreen
import org.enchant.status.screens.StatusCreateScreen
import org.enchant.status.screens.StatusFeedScreen
import org.enchant.status.screens.StatusViewerScreen
import org.enchant.stickers.screens.StickerStoreScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        handleCallIntent(intent)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallIntent(intent)
    }

    private fun handleCallIntent(intent: Intent?) {
        if (intent?.hasExtra("navigate_to") == true) {
            // Call screen navigation is handled reactively via CallViewModel
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

    LaunchedEffect(callUiState.callState.status) {
        when (callUiState.callState.status) {
            CallStatusEnum.RINGING -> navController.navigate("incoming_call/${callUiState.callState.callId}")
            CallStatusEnum.CALLING -> navController.navigate("outgoing_call/${callUiState.callState.remoteUserId}")
            CallStatusEnum.CONNECTED -> {
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
                    authViewModel.updateProfile("user_${System.currentTimeMillis()}", displayName, about)
                    navController.navigate("username_picker")
                }
            )
        }

        composable("username_picker") {
            UsernamePickerScreen(
                onUsernameEntered = { navController.navigate("key_generation") },
                onSkip = { navController.navigate("key_generation") },
                onCheckAvailability = { prefix ->
                    try {
                        AuthManager.searchUsername(prefix).getOrNull()?.let { it.isEmpty() } ?: false
                    } catch (_: Exception) { false }
                }
            )
        }

        composable("key_generation") {
            val state by authViewModel.registrationState.collectAsState()
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
                progress = if (state is RegistrationState.KeyGeneration) 1f else 0.5f,
                isError = state is RegistrationState.Error,
                errorMessage = (state as? RegistrationState.Error)?.message
            )
            LaunchedEffect(Unit) {
                authViewModel.registerKeys()
            }
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
            ContactListScreen(
                contacts = emptyList(),
                searchResults = emptyList(),
                searchQuery = "",
                isLoading = false,
                error = null,
                onContactClick = { userId -> navController.navigate("conversation/$userId") },
                onSearchQueryChange = {},
                onAddContact = {},
                onRefresh = {}
            )
        }

        composable("create_group") {
            CreateGroupScreen(
                onGroupCreated = { groupId -> navController.popBackStack() },
                onNavigateBack = { navController.popBackStack() },
                onCreateGroup = { _, _, _ -> },
                isLoading = false
            )
        }

        composable("groups") {
            GroupListScreen(
                groups = emptyList(),
                isLoading = false,
                error = null,
                onGroupClick = { groupId -> navController.navigate("group_info/$groupId") },
                onCreateGroup = { navController.navigate("create_group") },
                onJoinGroup = {},
                onRefresh = {}
            )
        }

        composable("restore_prompt") {
            RestorePromptScreen(
                hasBackup = false,
                onRestore = {},
                onStartFresh = { navController.navigate("profile_setup") }
            )
        }

        composable("app_lock") {
            AppLockScreen(
                onVerified = { navController.popBackStack() }
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
            StatusFeedScreen(
                myStatus = null,
                feed = emptyMap(),
                onStatusTap = { },
                onCreateStatus = { navController.navigate("status_create") }
            )
        }

        composable("status_create") {
            StatusCreateScreen(
                onCreateText = { _, _, _ -> navController.popBackStack() },
                onCreateMedia = { _, _ -> navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable("status_viewer/{statusId}") { backStackEntry ->
            val statusId = backStackEntry.arguments?.getString("statusId") ?: ""
            StatusViewerScreen(
                statuses = emptyList(),
                initialIndex = 0,
                onReply = { },
                onClose = { navController.popBackStack() },
                onViewInfo = { }
            )
        }

        composable("channels_feed") {
            ChannelFeedScreen(
                channelId = "",
                channelName = "Channels",
                isSubscribed = false,
                posts = emptyList(),
                pinnedPost = null,
                onSubscribe = { },
                onShare = { },
                onLoadMore = { }
            )
        }

        composable("group_info/{groupId}") { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            GroupInfoScreen(
                group = null,
                members = emptyList(),
                joinRequests = 0,
                isLoading = false,
                error = null,
                inviteLink = null,
                onNavigateBack = { navController.popBackStack() },
                onAddMembers = { },
                onRemoveMember = { },
                onUpdateRole = { _, _ -> },
                onCreateInviteLink = { },
                onCopyInviteLink = { },
                onViewJoinRequests = { },
                onLeaveGroup = { },
                onDeleteGroup = { },
                onRefresh = { }
            )
        }

        composable("stickers") {
            StickerStoreScreen(
                featured = emptyList(),
                searchResults = emptyList(),
                isLoading = false,
                error = null,
                onInstall = { },
                onSearch = { },
                onPackClick = { },
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
                    onVerify = { showSafetyNumber = false }
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
            CallLogScreen(
                entries = emptyList(),
                filter = CallLogFilter.ALL,
                isLoading = false,
                isSelectionMode = false,
                selectedIds = emptySet(),
                onFilterChange = { },
                onEntryClick = { },
                onStartSelection = { },
                onEndSelection = { },
                onToggleSelected = { },
                onSelectAll = { },
                onDelete = { }
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Text("Search coming soon", style = MaterialTheme.typography.bodyLarge)
            }
        }

        composable("qr_code") {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Text("QR Code coming soon", style = MaterialTheme.typography.bodyLarge)
            }
        }

        composable("qr_scanner") {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Text("QR Scanner coming soon", style = MaterialTheme.typography.bodyLarge)
            }
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
            // ShareTarget is handled by ShareTargetActivity via Android Intent filters
            navController.popBackStack()
        }

        composable("media_viewer/{conversationId}") { backStackEntry ->
            val convId = backStackEntry.arguments?.getString("conversationId") ?: ""
            // MediaViewerScreen requires mediaPath + mimeType from a specific message
            // Route exists for future integration from ConversationScreen
            navController.popBackStack()
        }

    }
}
