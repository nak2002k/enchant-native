package org.enchant.navigation

import android.net.Uri

sealed class NavRoute {
    abstract val route: String

    open val resolvedRoute: String get() = route

    data object Splash : NavRoute() { override val route = "splash" }
    data object Welcome : NavRoute() { override val route = "welcome" }
    data object PhoneEntry : NavRoute() { override val route = "phone_entry" }
    data object OtpVerify : NavRoute() { override val route = "otp_verify" }
    data object Permissions : NavRoute() { override val route = "permissions" }
    data object ProfileSetup : NavRoute() { override val route = "profile_setup" }
    data object UsernamePicker : NavRoute() { override val route = "username_picker" }
    data object KeyGeneration : NavRoute() { override val route = "key_generation" }
    data object PinCreation : NavRoute() { override val route = "pin_creation" }
    data object RestorePrompt : NavRoute() { override val route = "restore_prompt" }
    data object ChatList : NavRoute() { override val route = "chat_list" }
    data object CallLog : NavRoute() { override val route = "call_log" }
    data object StatusFeed : NavRoute() { override val route = "status_feed" }
    data object ChannelsFeed : NavRoute() { override val route = "channels_feed" }
    data object Settings : NavRoute() { override val route = "settings" }
    data class Conversation(val conversationId: String) : NavRoute() {
        override val route = "conversation/{conversationId}"
        override val resolvedRoute = "conversation/${Uri.encode(conversationId)}"
    }
    data object Search : NavRoute() { override val route = "search" }
    data class IncomingCall(val callId: String) : NavRoute() {
        override val route = "incoming_call/{callId}"
        override val resolvedRoute = "incoming_call/${Uri.encode(callId)}"
    }
    data class OutgoingCall(val userId: String) : NavRoute() {
        override val route = "outgoing_call/{userId}"
        override val resolvedRoute = "outgoing_call/${Uri.encode(userId)}"
    }
    data class ActiveVoiceCall(val callId: String) : NavRoute() {
        override val route = "active_voice_call/{callId}"
        override val resolvedRoute = "active_voice_call/${Uri.encode(callId)}"
    }
    data class ActiveVideoCall(val callId: String) : NavRoute() {
        override val route = "active_video_call/{callId}"
        override val resolvedRoute = "active_video_call/${Uri.encode(callId)}"
    }
    data class GroupCall(val callId: String) : NavRoute() {
        override val route = "group_call/{callId}"
        override val resolvedRoute = "group_call/${Uri.encode(callId)}"
    }
    data object Groups : NavRoute() { override val route = "groups" }
    data class GroupInfo(val groupId: String) : NavRoute() {
        override val route = "group_info/{groupId}"
        override val resolvedRoute = "group_info/${Uri.encode(groupId)}"
    }
    data object CreateGroup : NavRoute() { override val route = "create_group" }
    data object Contacts : NavRoute() { override val route = "contacts" }
    data object StatusCreate : NavRoute() { override val route = "status_create" }
    data class StatusViewer(val statusId: String) : NavRoute() {
        override val route = "status_viewer/{statusId}"
        override val resolvedRoute = "status_viewer/${Uri.encode(statusId)}"
    }
    data object AccountSettings : NavRoute() { override val route = "account_settings" }
    data object SecuritySettings : NavRoute() { override val route = "security_settings" }
    data object PrivacySettings : NavRoute() { override val route = "privacy_settings" }
    data object NotificationSettings : NavRoute() { override val route = "notification_settings" }
    data object AppearanceSettings : NavRoute() { override val route = "appearance_settings" }
    data object ChatsSettings : NavRoute() { override val route = "chats_settings" }
    data object StorageSettings : NavRoute() { override val route = "storage_settings" }
    data object About : NavRoute() { override val route = "about" }
    data object BackupSettings : NavRoute() { override val route = "backup_settings" }
    data object BlockedUsers : NavRoute() { override val route = "blocked_users" }
    data object AppLock : NavRoute() { override val route = "app_lock" }
    data object Stickers : NavRoute() { override val route = "stickers" }
    data class PollCreate(val conversationId: String) : NavRoute() {
        override val route = "poll_create/{conversationId}"
        override val resolvedRoute = "poll_create/${Uri.encode(conversationId)}"
    }
    data object LocationPicker : NavRoute() { override val route = "location_picker" }
    data object ShareTarget : NavRoute() { override val route = "share_target" }
    data object QrCode : NavRoute() { override val route = "qr_code" }
    data object QrScanner : NavRoute() { override val route = "qr_scanner" }
    data class MediaViewer(val conversationId: String) : NavRoute() {
        override val route = "media_viewer/{conversationId}"
        override val resolvedRoute = "media_viewer/${Uri.encode(conversationId)}"
    }
}
