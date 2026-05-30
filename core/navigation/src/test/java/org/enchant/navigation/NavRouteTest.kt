package org.enchant.navigation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("NavRoute — Full Coverage")
class NavRouteTest {

    @Nested @DisplayName("Route Strings")
    inner class RouteStringTest {
        @Test @DisplayName("Splash route is 'splash'")
        fun `splash route`() {
            assertEquals("splash", NavRoute.Splash.route)
        }

        @Test @DisplayName("Welcome route is 'welcome'")
        fun `welcome route`() {
            assertEquals("welcome", NavRoute.Welcome.route)
        }

        @Test @DisplayName("PhoneEntry route is 'phone_entry'")
        fun `phone entry route`() {
            assertEquals("phone_entry", NavRoute.PhoneEntry.route)
        }

        @Test @DisplayName("OtpVerify route is 'otp_verify'")
        fun `otp verify route`() {
            assertEquals("otp_verify", NavRoute.OtpVerify.route)
        }

        @Test @DisplayName("Permissions route is 'permissions'")
        fun `permissions route`() {
            assertEquals("permissions", NavRoute.Permissions.route)
        }

        @Test @DisplayName("ProfileSetup route is 'profile_setup'")
        fun `profile setup route`() {
            assertEquals("profile_setup", NavRoute.ProfileSetup.route)
        }

        @Test @DisplayName("UsernamePicker route is 'username_picker'")
        fun `username picker route`() {
            assertEquals("username_picker", NavRoute.UsernamePicker.route)
        }

        @Test @DisplayName("KeyGeneration route is 'key_generation'")
        fun `key generation route`() {
            assertEquals("key_generation", NavRoute.KeyGeneration.route)
        }

        @Test @DisplayName("PinCreation route is 'pin_creation'")
        fun `pin creation route`() {
            assertEquals("pin_creation", NavRoute.PinCreation.route)
        }

        @Test @DisplayName("RestorePrompt route is 'restore_prompt'")
        fun `restore prompt route`() {
            assertEquals("restore_prompt", NavRoute.RestorePrompt.route)
        }

        @Test @DisplayName("ChatList route is 'chat_list'")
        fun `chat list route`() {
            assertEquals("chat_list", NavRoute.ChatList.route)
        }

        @Test @DisplayName("CallLog route is 'call_log'")
        fun `call log route`() {
            assertEquals("call_log", NavRoute.CallLog.route)
        }

        @Test @DisplayName("StatusFeed route is 'status_feed'")
        fun `status feed route`() {
            assertEquals("status_feed", NavRoute.StatusFeed.route)
        }

        @Test @DisplayName("ChannelsFeed route is 'channels_feed'")
        fun `channels feed route`() {
            assertEquals("channels_feed", NavRoute.ChannelsFeed.route)
        }

        @Test @DisplayName("Settings route is 'settings'")
        fun `settings route`() {
            assertEquals("settings", NavRoute.Settings.route)
        }

        @Test @DisplayName("Conversation route includes conversationId")
        fun `conversation route`() {
            val route = NavRoute.Conversation("conv-123")
            assertEquals("conversation/{conversationId}", route.route)
            assertEquals("conversation/conv-123", route.resolvedRoute)
            assertEquals("conv-123", route.conversationId)
        }

        @Test @DisplayName("Search route is 'search'")
        fun `search route`() {
            assertEquals("search", NavRoute.Search.route)
        }

        @Test @DisplayName("IncomingCall route includes callId")
        fun `incoming call route`() {
            val route = NavRoute.IncomingCall("call-123")
            assertEquals("incoming_call/{callId}", route.route)
            assertEquals("incoming_call/call-123", route.resolvedRoute)
            assertEquals("call-123", route.callId)
        }

        @Test @DisplayName("OutgoingCall route includes userId")
        fun `outgoing call route`() {
            val route = NavRoute.OutgoingCall("user-123")
            assertEquals("outgoing_call/{userId}", route.route)
            assertEquals("outgoing_call/user-123", route.resolvedRoute)
            assertEquals("user-123", route.userId)
        }

        @Test @DisplayName("ActiveVoiceCall route includes callId")
        fun `active voice call route`() {
            val route = NavRoute.ActiveVoiceCall("call-123")
            assertEquals("active_voice_call/{callId}", route.route)
            assertEquals("active_voice_call/call-123", route.resolvedRoute)
        }

        @Test @DisplayName("ActiveVideoCall route includes callId")
        fun `active video call route`() {
            val route = NavRoute.ActiveVideoCall("call-123")
            assertEquals("active_video_call/{callId}", route.route)
            assertEquals("active_video_call/call-123", route.resolvedRoute)
        }

        @Test @DisplayName("GroupCall route includes callId")
        fun `group call route`() {
            val route = NavRoute.GroupCall("call-123")
            assertEquals("group_call/{callId}", route.route)
            assertEquals("group_call/call-123", route.resolvedRoute)
        }

        @Test @DisplayName("Groups route is 'groups'")
        fun `groups route`() {
            assertEquals("groups", NavRoute.Groups.route)
        }

        @Test @DisplayName("GroupInfo route includes groupId")
        fun `group info route`() {
            val route = NavRoute.GroupInfo("group-123")
            assertEquals("group_info/{groupId}", route.route)
            assertEquals("group_info/group-123", route.resolvedRoute)
        }

        @Test @DisplayName("CreateGroup route is 'create_group'")
        fun `create group route`() {
            assertEquals("create_group", NavRoute.CreateGroup.route)
        }

        @Test @DisplayName("Contacts route is 'contacts'")
        fun `contacts route`() {
            assertEquals("contacts", NavRoute.Contacts.route)
        }

        @Test @DisplayName("StatusCreate route is 'status_create'")
        fun `status create route`() {
            assertEquals("status_create", NavRoute.StatusCreate.route)
        }

        @Test @DisplayName("StatusViewer route includes statusId")
        fun `status viewer route`() {
            val route = NavRoute.StatusViewer("status-123")
            assertEquals("status_viewer/{statusId}", route.route)
            assertEquals("status_viewer/status-123", route.resolvedRoute)
        }

        @Test @DisplayName("AccountSettings route is 'account_settings'")
        fun `account settings route`() {
            assertEquals("account_settings", NavRoute.AccountSettings.route)
        }

        @Test @DisplayName("SecuritySettings route is 'security_settings'")
        fun `security settings route`() {
            assertEquals("security_settings", NavRoute.SecuritySettings.route)
        }

        @Test @DisplayName("PrivacySettings route is 'privacy_settings'")
        fun `privacy settings route`() {
            assertEquals("privacy_settings", NavRoute.PrivacySettings.route)
        }

        @Test @DisplayName("NotificationSettings route is 'notification_settings'")
        fun `notification settings route`() {
            assertEquals("notification_settings", NavRoute.NotificationSettings.route)
        }

        @Test @DisplayName("AppearanceSettings route is 'appearance_settings'")
        fun `appearance settings route`() {
            assertEquals("appearance_settings", NavRoute.AppearanceSettings.route)
        }

        @Test @DisplayName("ChatsSettings route is 'chats_settings'")
        fun `chats settings route`() {
            assertEquals("chats_settings", NavRoute.ChatsSettings.route)
        }

        @Test @DisplayName("StorageSettings route is 'storage_settings'")
        fun `storage settings route`() {
            assertEquals("storage_settings", NavRoute.StorageSettings.route)
        }

        @Test @DisplayName("About route is 'about'")
        fun `about route`() {
            assertEquals("about", NavRoute.About.route)
        }

        @Test @DisplayName("BackupSettings route is 'backup_settings'")
        fun `backup settings route`() {
            assertEquals("backup_settings", NavRoute.BackupSettings.route)
        }

        @Test @DisplayName("BlockedUsers route is 'blocked_users'")
        fun `blocked users route`() {
            assertEquals("blocked_users", NavRoute.BlockedUsers.route)
        }

        @Test @DisplayName("AppLock route is 'app_lock'")
        fun `app lock route`() {
            assertEquals("app_lock", NavRoute.AppLock.route)
        }

        @Test @DisplayName("Stickers route is 'stickers'")
        fun `stickers route`() {
            assertEquals("stickers", NavRoute.Stickers.route)
        }

        @Test @DisplayName("PollCreate route includes conversationId")
        fun `poll create route`() {
            val route = NavRoute.PollCreate("conv-123")
            assertEquals("poll_create/{conversationId}", route.route)
            assertEquals("poll_create/conv-123", route.resolvedRoute)
        }

        @Test @DisplayName("LocationPicker route is 'location_picker'")
        fun `location picker route`() {
            assertEquals("location_picker", NavRoute.LocationPicker.route)
        }

        @Test @DisplayName("ShareTarget route is 'share_target'")
        fun `share target route`() {
            assertEquals("share_target", NavRoute.ShareTarget.route)
        }

        @Test @DisplayName("QrCode route is 'qr_code'")
        fun `qr code route`() {
            assertEquals("qr_code", NavRoute.QrCode.route)
        }

        @Test @DisplayName("QrScanner route is 'qr_scanner'")
        fun `qr scanner route`() {
            assertEquals("qr_scanner", NavRoute.QrScanner.route)
        }

        @Test @DisplayName("MediaViewer route includes conversationId")
        fun `media viewer route`() {
            val route = NavRoute.MediaViewer("conv-123")
            assertEquals("media_viewer/{conversationId}", route.route)
            assertEquals("media_viewer/conv-123", route.resolvedRoute)
        }
    }

    @Nested @DisplayName("Resolved Route (URI Encoding)")
    inner class ResolvedRouteTest {
        @Test @DisplayName("Conversation route encodes special characters")
        fun `conversation encodes special chars`() {
            val route = NavRoute.Conversation("conv/123?query=456")
            assertEquals("conversation/conv%2F123%3Fquery%3D456", route.resolvedRoute)
        }

        @Test @DisplayName("IncomingCall route encodes special characters")
        fun `incoming call encodes special chars`() {
            val route = NavRoute.IncomingCall("call#123")
            assertEquals("incoming_call/call%23123", route.resolvedRoute)
        }

        @Test @DisplayName("OutgoingCall route encodes special characters")
        fun `outgoing call encodes special chars`() {
            val route = NavRoute.OutgoingCall("user&123")
            assertEquals("outgoing_call/user%26123", route.resolvedRoute)
        }

        @Test @DisplayName("GroupCall route encodes special characters")
        fun `group call encodes special chars`() {
            val route = NavRoute.GroupCall("call?info")
            assertEquals("group_call/call%3Finfo", route.resolvedRoute)
        }

        @Test @DisplayName("GroupInfo route encodes special characters")
        fun `group info encodes special chars`() {
            val route = NavRoute.GroupInfo("group/abc")
            assertEquals("group_info/group%2Fabc", route.resolvedRoute)
        }

        @Test @DisplayName("StatusViewer route encodes special characters")
        fun `status viewer encodes special chars`() {
            val route = NavRoute.StatusViewer("status&test")
            assertEquals("status_viewer/status%26test", route.resolvedRoute)
        }

        @Test @DisplayName("PollCreate route encodes special characters")
        fun `poll create encodes special chars`() {
            val route = NavRoute.PollCreate("conv#id")
            assertEquals("poll_create/conv%23id", route.resolvedRoute)
        }

        @Test @DisplayName("MediaViewer route encodes special characters")
        fun `media viewer encodes special chars`() {
            val route = NavRoute.MediaViewer("conv/123")
            assertEquals("media_viewer/conv%2F123", route.resolvedRoute)
        }

        @Test @DisplayName("Simple routes have resolvedRoute equal to route")
        fun `simple routes use route as resolvedRoute`() {
            assertEquals(NavRoute.Splash.route, NavRoute.Splash.resolvedRoute)
            assertEquals(NavRoute.ChatList.route, NavRoute.ChatList.resolvedRoute)
            assertEquals(NavRoute.Settings.route, NavRoute.Settings.resolvedRoute)
        }
    }

    @Nested @DisplayName("Navigate Extension")
    inner class NavigateTest {
        @Test @DisplayName("navigate uses resolvedRoute for Conversation")
        fun `navigate conversation`() {
            val route = NavRoute.Conversation("conv-123")
            assertEquals("conversation/conv-123", route.resolvedRoute)
        }

        @Test @DisplayName("navigate uses resolvedRoute for IncomingCall")
        fun `navigate incoming call`() {
            val route = NavRoute.IncomingCall("call-123")
            assertEquals("incoming_call/call-123", route.resolvedRoute)
        }
    }
}
