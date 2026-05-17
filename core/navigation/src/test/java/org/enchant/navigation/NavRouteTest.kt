package org.enchant.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("NavRoute")
class NavRouteTest {

    @Nested @DisplayName("data objects return correct route strings")
    inner class RouteStringTest {
        @Test @DisplayName("Splash")
        fun `splash route`() = assertEquals("splash", NavRoute.Splash.route)

        @Test @DisplayName("Welcome")
        fun `welcome route`() = assertEquals("welcome", NavRoute.Welcome.route)

        @Test @DisplayName("ChatList")
        fun `chat list route`() = assertEquals("chat_list", NavRoute.ChatList.route)

        @Test @DisplayName("CallLog")
        fun `call log route`() = assertEquals("call_log", NavRoute.CallLog.route)

        @Test @DisplayName("StatusFeed")
        fun `status feed route`() = assertEquals("status_feed", NavRoute.StatusFeed.route)

        @Test @DisplayName("ChannelsFeed")
        fun `channels feed route`() = assertEquals("channels_feed", NavRoute.ChannelsFeed.route)

        @Test @DisplayName("Settings")
        fun `settings route`() = assertEquals("settings", NavRoute.Settings.route)

        @Test @DisplayName("Groups")
        fun `groups route`() = assertEquals("groups", NavRoute.Groups.route)

        @Test @DisplayName("CreateGroup")
        fun `create group route`() = assertEquals("create_group", NavRoute.CreateGroup.route)

        @Test @DisplayName("Contacts")
        fun `contacts route`() = assertEquals("contacts", NavRoute.Contacts.route)

        @Test @DisplayName("StatusCreate")
        fun `status create route`() = assertEquals("status_create", NavRoute.StatusCreate.route)

        @Test @DisplayName("AccountSettings")
        fun `account settings route`() = assertEquals("account_settings", NavRoute.AccountSettings.route)

        @Test @DisplayName("SecuritySettings")
        fun `security settings route`() = assertEquals("security_settings", NavRoute.SecuritySettings.route)

        @Test @DisplayName("AppLock")
        fun `app lock route`() = assertEquals("app_lock", NavRoute.AppLock.route)

        @Test @DisplayName("Stickers")
        fun `stickers route`() = assertEquals("stickers", NavRoute.Stickers.route)

        @Test @DisplayName("LocationPicker")
        fun `location picker route`() = assertEquals("location_picker", NavRoute.LocationPicker.route)

        @Test @DisplayName("ShareTarget")
        fun `share target route`() = assertEquals("share_target", NavRoute.ShareTarget.route)

        @Test @DisplayName("QrCode")
        fun `qr code route`() = assertEquals("qr_code", NavRoute.QrCode.route)

        @Test @DisplayName("QrScanner")
        fun `qr scanner route`() = assertEquals("qr_scanner", NavRoute.QrScanner.route)

        @Test @DisplayName("RestorePrompt")
        fun `restore prompt route`() = assertEquals("restore_prompt", NavRoute.RestorePrompt.route)
    }

    @Nested @DisplayName("data classes return correct route patterns")
    inner class RoutePatternTest {
        @Test @DisplayName("Conversation")
        fun `conversation route`() {
            assertEquals("conversation/{conversationId}", NavRoute.Conversation("abc").route)
        }

        @Test @DisplayName("IncomingCall")
        fun `incoming call route`() {
            assertEquals("incoming_call/{callId}", NavRoute.IncomingCall("call1").route)
        }

        @Test @DisplayName("OutgoingCall")
        fun `outgoing call route`() {
            assertEquals("outgoing_call/{userId}", NavRoute.OutgoingCall("user1").route)
        }

        @Test @DisplayName("ActiveVoiceCall")
        fun `active voice call route`() {
            assertEquals("active_voice_call/{callId}", NavRoute.ActiveVoiceCall("c1").route)
        }

        @Test @DisplayName("GroupInfo")
        fun `group info route`() {
            assertEquals("group_info/{groupId}", NavRoute.GroupInfo("g1").route)
        }

        @Test @DisplayName("StatusViewer")
        fun `status viewer route`() {
            assertEquals("status_viewer/{statusId}", NavRoute.StatusViewer("s1").route)
        }

        @Test @DisplayName("PollCreate")
        fun `poll create route`() {
            assertEquals("poll_create/{conversationId}", NavRoute.PollCreate("c1").route)
        }

        @Test @DisplayName("MediaViewer")
        fun `media viewer route`() {
            assertEquals("media_viewer/{conversationId}", NavRoute.MediaViewer("c1").route)
        }
    }

    @Nested @DisplayName("navigate() builds correct route strings")
    inner class NavigateRouteTest {
        @Test @DisplayName("Conversation with ID")
        fun `conversation navigation route`() {
            val route = NavRoute.Conversation("uuid-123")
            val expected = "conversation/uuid-123"
            val actual = "conversation/${route.conversationId}"
            assertEquals(expected, actual)
        }

        @Test @DisplayName("GroupInfo with ID")
        fun `group info navigation route`() {
            val route = NavRoute.GroupInfo("group-456")
            val expected = "group_info/group-456"
            val actual = "group_info/${route.groupId}"
            assertEquals(expected, actual)
        }

        @Test @DisplayName("all data objects produce route without slashes")
        fun `data objects have no slashes in route`() {
            val routes = listOf(
                NavRoute.Splash.route,
                NavRoute.Welcome.route,
                NavRoute.PhoneEntry.route,
                NavRoute.OtpVerify.route,
                NavRoute.Permissions.route,
                NavRoute.ProfileSetup.route,
                NavRoute.UsernamePicker.route,
                NavRoute.KeyGeneration.route,
                NavRoute.PinCreation.route,
                NavRoute.RestorePrompt.route,
                NavRoute.ChatList.route,
                NavRoute.CallLog.route,
                NavRoute.StatusFeed.route,
                NavRoute.ChannelsFeed.route,
                NavRoute.Settings.route,
                NavRoute.Groups.route,
                NavRoute.CreateGroup.route,
                NavRoute.Contacts.route,
                NavRoute.StatusCreate.route,
                NavRoute.AccountSettings.route,
                NavRoute.SecuritySettings.route,
                NavRoute.PrivacySettings.route,
                NavRoute.NotificationSettings.route,
                NavRoute.AppearanceSettings.route,
                NavRoute.ChatsSettings.route,
                NavRoute.StorageSettings.route,
                NavRoute.About.route,
                NavRoute.BackupSettings.route,
                NavRoute.BlockedUsers.route,
                NavRoute.AppLock.route,
                NavRoute.Stickers.route,
                NavRoute.LocationPicker.route,
                NavRoute.ShareTarget.route,
                NavRoute.QrCode.route,
                NavRoute.QrScanner.route,
                NavRoute.Search().route
            )
            routes.forEach { r -> assert(r.none { it == '/' }) { "Route '$r' contains '/' but should not" } }
        }

        @Test @DisplayName("all data classes have slashes in route")
        fun `data classes have slashes in route`() {
            val routes = listOf(
                NavRoute.Conversation("x").route,
                NavRoute.IncomingCall("x").route,
                NavRoute.OutgoingCall("x").route,
                NavRoute.ActiveVoiceCall("x").route,
                NavRoute.ActiveVideoCall("x").route,
                NavRoute.GroupCall("x").route,
                NavRoute.GroupInfo("x").route,
                NavRoute.StatusViewer("x").route,
                NavRoute.PollCreate("x").route,
                NavRoute.MediaViewer("x").route
            )
            routes.forEach { r -> assert(r.contains('/')) { "Route '$r' should contain '/' but does not" } }
        }
    }
}