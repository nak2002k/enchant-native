package org.enchant.navigation

import androidx.navigation.NavHostController

fun NavHostController.navigateTo(route: NavRoute) {
    val routeString = route.toRouteString()
    navigate(routeString)
}

fun NavHostController.navigateAndClearStack(route: NavRoute) {
    val routeString = route.toRouteString()
    navigate(routeString) {
        popUpTo(0) { inclusive = true }
    }
}

fun NavRoute.toRouteString(): String = when (this) {
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
    is NavRoute.Conversation -> "conversation/${conversationId}"
    is NavRoute.Search -> "search?conversationId=${conversationId}"
    is NavRoute.IncomingCall -> "incoming_call/${callId}"
    is NavRoute.OutgoingCall -> "outgoing_call/${userId}"
    is NavRoute.ActiveCall -> "active_call/${callId}"
    is NavRoute.VideoCall -> "video_call/${callId}"
    is NavRoute.GroupCall -> "group_call/${callId}"
    is NavRoute.Groups -> "groups"
    is NavRoute.GroupInfo -> "group_info/${groupId}"
    is NavRoute.CreateGroup -> "create_group"
    is NavRoute.Contacts -> "contacts"
    is NavRoute.StatusCreate -> "status_create"
    is NavRoute.StatusViewer -> "status_viewer/${statusId}"
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
    is NavRoute.PollCreate -> "poll_create/${conversationId}"
    is NavRoute.LocationPicker -> "location_picker"
    is NavRoute.ShareTarget -> "share_target"
    is NavRoute.QrCode -> "qr_code"
    is NavRoute.QrScanner -> "qr_scanner"
    is NavRoute.MediaViewer -> "media_viewer/${conversationId}"
}
