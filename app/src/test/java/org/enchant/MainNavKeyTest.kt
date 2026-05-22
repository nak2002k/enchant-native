package org.enchant

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MainNavKey serialization")
class MainNavKeyTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Nested
    @DisplayName("Data object routes")
    inner class DataObjectRoutes {

        @Test
        fun `Contacts round-trip`() = assertRoundTrip(MainNavKey.Contacts)
        @Test
        fun `CreateGroup round-trip`() = assertRoundTrip(MainNavKey.CreateGroup)
        @Test
        fun `Groups round-trip`() = assertRoundTrip(MainNavKey.Groups)
        @Test
        fun `JoinRequests round-trip`() = assertRoundTrip(MainNavKey.JoinRequests)
        @Test
        fun `Settings round-trip`() = assertRoundTrip(MainNavKey.Settings)
        @Test
        fun `AccountSettings round-trip`() = assertRoundTrip(MainNavKey.AccountSettings)
        @Test
        fun `SecuritySettings round-trip`() = assertRoundTrip(MainNavKey.SecuritySettings)
        @Test
        fun `PrivacySettings round-trip`() = assertRoundTrip(MainNavKey.PrivacySettings)
        @Test
        fun `NotificationSettings round-trip`() = assertRoundTrip(MainNavKey.NotificationSettings)
        @Test
        fun `AppearanceSettings round-trip`() = assertRoundTrip(MainNavKey.AppearanceSettings)
        @Test
        fun `ChatsSettings round-trip`() = assertRoundTrip(MainNavKey.ChatsSettings)
        @Test
        fun `StorageSettings round-trip`() = assertRoundTrip(MainNavKey.StorageSettings)
        @Test
        fun `About round-trip`() = assertRoundTrip(MainNavKey.About)
        @Test
        fun `BackupSettings round-trip`() = assertRoundTrip(MainNavKey.BackupSettings)
        @Test
        fun `BlockedUsers round-trip`() = assertRoundTrip(MainNavKey.BlockedUsers)
        @Test
        fun `StatusFeed round-trip`() = assertRoundTrip(MainNavKey.StatusFeed)
        @Test
        fun `StatusCreate round-trip`() = assertRoundTrip(MainNavKey.StatusCreate)
        @Test
        fun `ChannelsFeed round-trip`() = assertRoundTrip(MainNavKey.ChannelsFeed)
        @Test
        fun `Stickers round-trip`() = assertRoundTrip(MainNavKey.Stickers)
        @Test
        fun `Search round-trip`() = assertRoundTrip(MainNavKey.Search)
        @Test
        fun `QRCode round-trip`() = assertRoundTrip(MainNavKey.QRCode)
        @Test
        fun `QRScanner round-trip`() = assertRoundTrip(MainNavKey.QRScanner)
        @Test
        fun `LocationPicker round-trip`() = assertRoundTrip(MainNavKey.LocationPicker)
        @Test
        fun `ShareTarget round-trip`() = assertRoundTrip(MainNavKey.ShareTarget)
        @Test
        fun `AppLock round-trip`() = assertRoundTrip(MainNavKey.AppLock)
        @Test
        fun `PinCreation round-trip`() = assertRoundTrip(MainNavKey.PinCreation)
        @Test
        fun `RestorePrompt round-trip`() = assertRoundTrip(MainNavKey.RestorePrompt)
    }

    @Nested
    @DisplayName("Data class routes")
    inner class DataClassRoutes {

        @Test
        fun `GroupInfo round-trip`() {
            assertRoundTrip(MainNavKey.GroupInfo(groupId = "group-abc"))
        }

        @Test
        fun `GroupInfo with empty groupId`() {
            assertRoundTrip(MainNavKey.GroupInfo(groupId = ""))
        }

        @Test
        fun `StatusViewer round-trip`() {
            assertRoundTrip(MainNavKey.StatusViewer(statusId = "status-xyz"))
        }

        @Test
        fun `StatusViewer with empty statusId`() {
            assertRoundTrip(MainNavKey.StatusViewer(statusId = ""))
        }

        @Test
        fun `Profile round-trip`() {
            assertRoundTrip(MainNavKey.Profile(userId = "user-42"))
        }

        @Test
        fun `Profile with empty userId`() {
            assertRoundTrip(MainNavKey.Profile(userId = ""))
        }

        @Test
        fun `PollCreate round-trip`() {
            assertRoundTrip(MainNavKey.PollCreate(conversationId = "conv-abc"))
        }

        @Test
        fun `PollCreate with empty conversationId`() {
            assertRoundTrip(MainNavKey.PollCreate(conversationId = ""))
        }

        @Test
        fun `MediaViewer round-trip`() {
            assertRoundTrip(MainNavKey.MediaViewer(messageId = 100L, attachmentId = 200L))
        }

        @Test
        fun `MediaViewer with zero ids`() {
            assertRoundTrip(MainNavKey.MediaViewer(messageId = 0L, attachmentId = 0L))
        }
    }

    private fun assertRoundTrip(original: MainNavKey) {
        val serialized = json.encodeToString(MainNavKey.serializer(), original)
        val deserialized = json.decodeFromString(MainNavKey.serializer(), serialized)
        assertEquals(original, deserialized)
    }
}
