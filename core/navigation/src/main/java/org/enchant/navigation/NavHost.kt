package org.enchant.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
import org.enchant.calls.screens.ActiveVideoCallScreen
import org.enchant.calls.screens.ActiveVoiceCallScreen
import org.enchant.calls.screens.IncomingCallScreen
import org.enchant.calls.screens.OutgoingCallScreen
import org.enchant.chat.ConversationScreen
import org.enchant.chatlist.ConversationListScreen
import org.enchant.chatlist.ConversationListViewModel
import org.enchant.core.auth.AuthManager
import org.enchant.core.auth.AuthState
import org.enchant.core.auth.RegistrationState
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.CallStatusEnum

@Composable
fun EnchantNavHost(
    navController: NavHostController,
    startRoute: NavRoute,
    authViewModel: AuthViewModel,
    callViewModel: CallViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    val callUiState by callViewModel.uiState.collectAsState()

    val startRouteString = when (startRoute) {
        is NavRoute.Splash -> "splash"
        is NavRoute.Welcome -> "welcome"
        is NavRoute.PhoneEntry -> "phone_entry"
        is NavRoute.OtpVerify -> "otp_verify"
        is NavRoute.Permissions -> "permissions"
        is NavRoute.ProfileSetup -> "profile_setup"
        is NavRoute.UsernamePicker -> "username_picker"
        is NavRoute.KeyGeneration -> "key_generation"
        is NavRoute.PinCreation -> "pin_creation"
        is NavRoute.RestorePrompt -> "restore_prompt"
        is NavRoute.ChatList -> "chat_list"
        is NavRoute.CallLog -> "call_log"
        is NavRoute.StatusFeed -> "status_feed"
        is NavRoute.ChannelsFeed -> "channels_feed"
        is NavRoute.Settings -> "settings"
        is NavRoute.Conversation -> "conversation/{conversationId}"
        is NavRoute.Search -> "search?conversationId={conversationId}"
        is NavRoute.IncomingCall -> "incoming_call/{callId}"
        is NavRoute.OutgoingCall -> "outgoing_call/{userId}"
        is NavRoute.ActiveCall -> "active_call/{callId}"
        is NavRoute.VideoCall -> "video_call/{callId}"
        is NavRoute.GroupCall -> "group_call/{callId}"
        is NavRoute.Groups -> "groups"
        is NavRoute.GroupInfo -> "group_info/{groupId}"
        is NavRoute.CreateGroup -> "create_group"
        is NavRoute.Contacts -> "contacts"
        is NavRoute.StatusCreate -> "status_create"
        is NavRoute.StatusViewer -> "status_viewer/{statusId}"
        is NavRoute.AccountSettings -> "account_settings"
        is NavRoute.SecuritySettings -> "security_settings"
        is NavRoute.PrivacySettings -> "privacy_settings"
        is NavRoute.NotificationSettings -> "notification_settings"
        is NavRoute.AppearanceSettings -> "appearance_settings"
        is NavRoute.ChatsSettings -> "chats_settings"
        is NavRoute.StorageSettings -> "storage_settings"
        is NavRoute.About -> "about"
        is NavRoute.BackupSettings -> "backup_settings"
        is NavRoute.BlockedUsers -> "blocked_users"
        is NavRoute.AppLock -> "app_lock"
        is NavRoute.Stickers -> "stickers"
        is NavRoute.PollCreate -> "poll_create/{conversationId}"
        is NavRoute.LocationPicker -> "location_picker"
        is NavRoute.ShareTarget -> "share_target"
        is NavRoute.QrCode -> "qr_code"
        is NavRoute.QrScanner -> "qr_scanner"
        is NavRoute.MediaViewer -> "media_viewer/{conversationId}"
    }

    NavHost(navController = navController, startDestination = startRouteString) {
        composable("splash") {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            LaunchedEffect(authState) {
                when (authState) {
                    is AuthState.Authenticated -> navController.navigate("chat_list") { popUpTo("splash") { inclusive = true } }
                    is AuthState.Unauthenticated, is AuthState.Unknown -> navController.navigate("welcome") { popUpTo("splash") { inclusive = true } }
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
                when (state) { is RegistrationState.OtpVerification -> navController.navigate("otp_verify") { popUpTo("phone_entry") { inclusive = true } }; else -> {} }
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
                when (state) { is RegistrationState.Permissions -> navController.navigate("permissions") { popUpTo("otp_verify") { inclusive = true } }; else -> {} }
            }
        }
        composable("permissions") { PermissionsScreen(onPermissionsGranted = { navController.navigate("profile_setup") }, onSkip = { navController.navigate("profile_setup") }) }
        composable("profile_setup") { ProfileSetupScreen(onProfileDataEntered = { _, _, _ -> navController.navigate("username_picker") }) }
        composable("username_picker") { UsernamePickerScreen(onUsernameEntered = { navController.navigate("key_generation") }, onSkip = { navController.navigate("key_generation") }, onCheckAvailability = { false }) }
        composable("key_generation") { KeyGenerationScreen(onKeysGenerated = { navController.navigate("chat_list") { popUpTo("welcome") { inclusive = true } } }, onRetry = { authViewModel.registerKeys() }, progress = 1f, isError = false, errorMessage = null) }
        composable("pin_creation") { TwoStepPinScreen(onPinCreated = { navController.navigate("chat_list") { popUpTo("welcome") { inclusive = true } } }) }
        composable("restore_prompt") { RestorePromptScreen(hasBackup = false, onRestore = {}, onStartFresh = { navController.navigate("profile_setup") }) }
        composable("app_lock") { AppLockScreen(onPinSet = {}, onBiometricAuthenticate = {}, isBiometricAvailable = false) }
        composable("chat_list") {
            val listViewModel = androidx.lifecycle.viewmodel.compose.viewModel<ConversationListViewModel>()
            ConversationListScreen(viewModel = listViewModel, onConversationClick = { convId -> navController.navigate("conversation/$convId") }, onNewChat = {}, onNewGroup = {})
        }
        composable("conversation/{conversationId}", arguments = listOf(navArgument("conversationId") { type = NavType.StringType })) { entry ->
            val convId = entry.arguments?.getString("conversationId") ?: return@composable
            ConversationScreen(conversationId = convId, onNavigateBack = { navController.popBackStack() }, onStartCall = { userId, isVideo -> callViewModel.startCall(userId, isVideo) })
        }
        composable("incoming_call/{callId}", arguments = listOf(navArgument("callId") { type = NavType.StringType })) {
            IncomingCallScreen(callerName = callUiState.callState.remoteUserId ?: "Unknown", callerId = "", isVideoCall = callUiState.callState.isVideoCall, onAcceptAudio = { callViewModel.acceptCall(false) }, onAcceptVideo = { callViewModel.acceptCall(true) }, onDecline = { callViewModel.denyCall() })
        }
        composable("outgoing_call/{userId}", arguments = listOf(navArgument("userId") { type = NavType.StringType })) {
            OutgoingCallScreen(remoteName = callUiState.callState.remoteUserId ?: "Unknown", isVideoCall = callUiState.callState.isVideoCall, onEndCall = { callViewModel.endCall() }, onToggleSpeaker = { callViewModel.toggleSpeaker() }, onSwitchToVideo = { callViewModel.toggleVideo() })
        }
        composable("active_call/{callId}", arguments = listOf(navArgument("callId") { type = NavType.StringType })) {
            ActiveVoiceCallScreen(remoteName = callUiState.callState.remoteUserId ?: "Unknown", durationSeconds = callUiState.callState.durationSeconds, isMuted = callUiState.callState.isMuted, isSpeakerOn = callUiState.callState.isSpeakerOn, signalStrength = 3, onToggleMute = { callViewModel.toggleMute() }, onToggleSpeaker = { callViewModel.toggleSpeaker() }, onEndCall = { callViewModel.endCall() }, onShowKeypad = {}, onSwitchToVideo = { callViewModel.toggleVideo() }, onShowSafetyNumber = {})
        }
        composable("video_call/{callId}", arguments = listOf(navArgument("callId") { type = NavType.StringType })) {
            ActiveVideoCallScreen(remoteUserId = callUiState.callState.remoteUserId ?: "Unknown", durationSeconds = callUiState.callState.durationSeconds, isMuted = callUiState.callState.isMuted, isSpeakerOn = callUiState.callState.isSpeakerOn, onToggleMute = { callViewModel.toggleMute() }, onFlipCamera = { callViewModel.flipCamera() }, onToggleSpeaker = { callViewModel.toggleSpeaker() }, onEndCall = { callViewModel.endCall() })
        }
        composable("group_call/{callId}", arguments = listOf(navArgument("callId") { type = NavType.StringType })) {}
        composable("call_log") {}
        composable("status_feed") {}
        composable("channels_feed") {}
        composable("settings") {}
        composable("search?conversationId={conversationId}", arguments = listOf(navArgument("conversationId") { type = NavType.StringType; nullable = true; defaultValue = null })) {}
        composable("groups") {}
        composable("group_info/{groupId}", arguments = listOf(navArgument("groupId") { type = NavType.StringType })) {}
        composable("create_group") {}
        composable("contacts") {}
        composable("status_create") {}
        composable("status_viewer/{statusId}", arguments = listOf(navArgument("statusId") { type = NavType.StringType })) {}
        composable("account_settings") {}
        composable("security_settings") {}
        composable("privacy_settings") {}
        composable("notification_settings") {}
        composable("appearance_settings") {}
        composable("chats_settings") {}
        composable("storage_settings") {}
        composable("about") {}
        composable("backup_settings") {}
        composable("blocked_users") {}
        composable("stickers") {}
        composable("poll_create/{conversationId}", arguments = listOf(navArgument("conversationId") { type = NavType.StringType })) {}
        composable("location_picker") {}
        composable("share_target") {}
        composable("qr_code") {}
        composable("qr_scanner") {}
        composable("media_viewer/{conversationId}", arguments = listOf(navArgument("conversationId") { type = NavType.StringType })) {}
    }
}

fun NavHostController.navigateTo(route: NavRoute) {
    val routeString = when (route) {
        is NavRoute.Splash -> "splash"
        is NavRoute.Welcome -> "welcome"
        is NavRoute.PhoneEntry -> "phone_entry"
        is NavRoute.OtpVerify -> "otp_verify"
        is NavRoute.Permissions -> "permissions"
        is NavRoute.ProfileSetup -> "profile_setup"
        is NavRoute.UsernamePicker -> "username_picker"
        is NavRoute.KeyGeneration -> "key_generation"
        is NavRoute.PinCreation -> "pin_creation"
        is NavRoute.RestorePrompt -> "restore_prompt"
        is NavRoute.ChatList -> "chat_list"
        is NavRoute.CallLog -> "call_log"
        is NavRoute.StatusFeed -> "status_feed"
        is NavRoute.ChannelsFeed -> "channels_feed"
        is NavRoute.Settings -> "settings"
        is NavRoute.Conversation -> "conversation/${route.conversationId}"
        is NavRoute.Search -> "search?conversationId=${route.conversationId}"
        is NavRoute.IncomingCall -> "incoming_call/${route.callId}"
        is NavRoute.OutgoingCall -> "outgoing_call/${route.userId}"
        is NavRoute.ActiveCall -> "active_call/${route.callId}"
        is NavRoute.VideoCall -> "video_call/${route.callId}"
        is NavRoute.GroupCall -> "group_call/${route.callId}"
        is NavRoute.Groups -> "groups"
        is NavRoute.GroupInfo -> "group_info/${route.groupId}"
        is NavRoute.CreateGroup -> "create_group"
        is NavRoute.Contacts -> "contacts"
        is NavRoute.StatusCreate -> "status_create"
        is NavRoute.StatusViewer -> "status_viewer/${route.statusId}"
        is NavRoute.AccountSettings -> "account_settings"
        is NavRoute.SecuritySettings -> "security_settings"
        is NavRoute.PrivacySettings -> "privacy_settings"
        is NavRoute.NotificationSettings -> "notification_settings"
        is NavRoute.AppearanceSettings -> "appearance_settings"
        is NavRoute.ChatsSettings -> "chats_settings"
        is NavRoute.StorageSettings -> "storage_settings"
        is NavRoute.About -> "about"
        is NavRoute.BackupSettings -> "backup_settings"
        is NavRoute.BlockedUsers -> "blocked_users"
        is NavRoute.AppLock -> "app_lock"
        is NavRoute.Stickers -> "stickers"
        is NavRoute.PollCreate -> "poll_create/${route.conversationId}"
        is NavRoute.LocationPicker -> "location_picker"
        is NavRoute.ShareTarget -> "share_target"
        is NavRoute.QrCode -> "qr_code"
        is NavRoute.QrScanner -> "qr_scanner"
        is NavRoute.MediaViewer -> "media_viewer/${route.conversationId}"
    }
    navigate(routeString)
}

fun NavHostController.navigateAndClearStack(route: NavRoute) {
    val routeString = when (route) {
        is NavRoute.Splash -> "splash"
        is NavRoute.Welcome -> "welcome"
        is NavRoute.PhoneEntry -> "phone_entry"
        is NavRoute.OtpVerify -> "otp_verify"
        is NavRoute.Permissions -> "permissions"
        is NavRoute.ProfileSetup -> "profile_setup"
        is NavRoute.UsernamePicker -> "username_picker"
        is NavRoute.KeyGeneration -> "key_generation"
        is NavRoute.PinCreation -> "pin_creation"
        is NavRoute.RestorePrompt -> "restore_prompt"
        is NavRoute.ChatList -> "chat_list"
        is NavRoute.CallLog -> "call_log"
        is NavRoute.StatusFeed -> "status_feed"
        is NavRoute.ChannelsFeed -> "channels_feed"
        is NavRoute.Settings -> "settings"
        is NavRoute.Conversation -> "conversation/${route.conversationId}"
        is NavRoute.Search -> "search?conversationId=${route.conversationId}"
        is NavRoute.IncomingCall -> "incoming_call/${route.callId}"
        is NavRoute.OutgoingCall -> "outgoing_call/${route.userId}"
        is NavRoute.ActiveCall -> "active_call/${route.callId}"
        is NavRoute.VideoCall -> "video_call/${route.callId}"
        is NavRoute.GroupCall -> "group_call/${route.callId}"
        is NavRoute.Groups -> "groups"
        is NavRoute.GroupInfo -> "group_info/${route.groupId}"
        is NavRoute.CreateGroup -> "create_group"
        is NavRoute.Contacts -> "contacts"
        is NavRoute.StatusCreate -> "status_create"
        is NavRoute.StatusViewer -> "status_viewer/${route.statusId}"
        is NavRoute.AccountSettings -> "account_settings"
        is NavRoute.SecuritySettings -> "security_settings"
        is NavRoute.PrivacySettings -> "privacy_settings"
        is NavRoute.NotificationSettings -> "notification_settings"
        is NavRoute.AppearanceSettings -> "appearance_settings"
        is NavRoute.ChatsSettings -> "chats_settings"
        is NavRoute.StorageSettings -> "storage_settings"
        is NavRoute.About -> "about"
        is NavRoute.BackupSettings -> "backup_settings"
        is NavRoute.BlockedUsers -> "blocked_users"
        is NavRoute.AppLock -> "app_lock"
        is NavRoute.Stickers -> "stickers"
        is NavRoute.PollCreate -> "poll_create/${route.conversationId}"
        is NavRoute.LocationPicker -> "location_picker"
        is NavRoute.ShareTarget -> "share_target"
        is NavRoute.QrCode -> "qr_code"
        is NavRoute.QrScanner -> "qr_scanner"
        is NavRoute.MediaViewer -> "media_viewer/${route.conversationId}"
    }
    navigate(routeString) { popUpTo(0) { inclusive = true } }
}
