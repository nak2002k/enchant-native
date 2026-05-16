package org.enchant.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

@Composable
fun EnchantNavHost(
    navController: NavHostController,
    startRoute: NavRoute
) {
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
        composable("splash") {}
        composable("welcome") {}
        composable("phone_entry") {}
        composable("otp_verify") {}
        composable("permissions") {}
        composable("profile_setup") {}
        composable("username_picker") {}
        composable("key_generation") {}
        composable("pin_creation") {}
        composable("restore_prompt") {}
        composable("chat_list") {}
        composable("call_log") {}
        composable("status_feed") {}
        composable("channels_feed") {}
        composable("settings") {}
        composable(
            route = "conversation/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) {}
        composable(
            route = "search?conversationId={conversationId}",
            arguments = listOf(navArgument("conversationId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) {}
        composable(
            route = "incoming_call/{callId}",
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) {}
        composable(
            route = "outgoing_call/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) {}
        composable(
            route = "active_call/{callId}",
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) {}
        composable(
            route = "video_call/{callId}",
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) {}
        composable(
            route = "group_call/{callId}",
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) {}
        composable("groups") {}
        composable(
            route = "group_info/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) {}
        composable("create_group") {}
        composable("contacts") {}
        composable("status_create") {}
        composable(
            route = "status_viewer/{statusId}",
            arguments = listOf(navArgument("statusId") { type = NavType.StringType })
        ) {}
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
        composable("app_lock") {}
        composable("stickers") {}
        composable(
            route = "poll_create/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) {}
        composable("location_picker") {}
        composable("share_target") {}
        composable("qr_code") {}
        composable("qr_scanner") {}
        composable(
            route = "media_viewer/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) {}
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
    navigate(routeString) {
        popUpTo(0) { inclusive = true }
    }
}
