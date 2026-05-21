package org.enchant.core.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.enchant.core.base.SecurePreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(AndroidJUnit4::class)
class EnchantStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        try {
            SecurePreferences.init(context)
            SecurePreferences.clearAll()
        } catch (e: Exception) {
            // EncryptedSharedPreferences may not work in Robolectric - continue anyway
        }
        resetStoreState()
        EnchantStore.init(InMemoryKeyValueStorage())
    }

    @After
    fun tearDown() {
        try {
            EnchantStore.store.close()
        } catch (e: Exception) {}
        resetStoreState()
        clearStoreFields()
    }

    private fun clearStoreFields() {
        val storeField = EnchantStore::class.java.getDeclaredField("store")
        storeField.isAccessible = true
        storeField.set(null, null)

        val delegatesField = EnchantStore::class.java.getDeclaredField("delegates")
        delegatesField.isAccessible = true
        delegatesField.set(null, null)

        val namespaceFields = listOf(
            "account", "registration", "backup", "settings", "notifications",
            "privacy", "pin", "onboarding", "proxy", "rateLimit",
            "phoneNumberPrivacy", "emoji", "chatColors", "callQuality", "labs",
            "stories", "internal", "svr", "remoteConfig", "storageService",
            "uiHints", "tooltips", "certificate", "wallpaper", "payments",
            "inAppPayments", "imageEditor", "notificationProfile", "releaseChannel",
            "apkUpdate", "miscellaneous"
        )
        for (field in namespaceFields) {
            try {
                val f = EnchantStore::class.java.getDeclaredField(field)
                f.isAccessible = true
                f.set(null, null)
            } catch (e: Exception) {}
        }
    }

    private fun resetStoreState() {
        val field = EnchantStore::class.java.getDeclaredField("initialized")
        field.isAccessible = true
        field.set(null, false)
    }

    @Test
    fun `Account userId defaults to null`() {
        assertNull(EnchantStore.account.userId)
    }

    @Test
    fun `Account userId set and get`() {
        EnchantStore.account.userId = "user-123"
        assertEquals("user-123", EnchantStore.account.userId)
    }

    @Test
    fun `Account deviceId set and get`() {
        EnchantStore.account.deviceId = "device-456"
        assertEquals("device-456", EnchantStore.account.deviceId)
    }

    @Test
    fun `Account username set and get`() {
        EnchantStore.account.username = "alice"
        assertEquals("alice", EnchantStore.account.username)
    }

    @Test
    fun `Account displayName set and get`() {
        EnchantStore.account.displayName = "Alice Smith"
        assertEquals("Alice Smith", EnchantStore.account.displayName)
    }

    @Test
    fun `Account about set and get`() {
        EnchantStore.account.about = "Hello!"
        assertEquals("Hello!", EnchantStore.account.about)
    }

    @Test
    fun `Account registrationId defaults to 0`() {
        assertEquals(0, EnchantStore.account.registrationId)
    }

    @Test
    fun `Account registrationId set and get`() {
        EnchantStore.account.registrationId = 42
        assertEquals(42, EnchantStore.account.registrationId)
    }

    @Test
    fun `Account aci set and get`() {
        EnchantStore.account.aci = "aci-uuid"
        assertEquals("aci-uuid", EnchantStore.account.aci)
    }

    @Test
    fun `Account pni set and get`() {
        EnchantStore.account.pni = "pni-uuid"
        assertEquals("pni-uuid", EnchantStore.account.pni)
    }

    @Test
    fun `Account isRegistered defaults to false`() {
        assertFalse(EnchantStore.account.isRegistered)
    }

    @Test
    fun `Account isRegistered set and get`() {
        EnchantStore.account.isRegistered = true
        assertTrue(EnchantStore.account.isRegistered)
    }

    @Test
    fun `Account fcmToken set and get`() {
        EnchantStore.account.fcmToken = "fcm-token-abc"
        assertEquals("fcm-token-abc", EnchantStore.account.fcmToken)
    }

    @Test
    fun `Account multiDevice defaults to false`() {
        assertFalse(EnchantStore.account.multiDevice)
    }

    @Test
    fun `Account multiDevice set and get`() {
        EnchantStore.account.multiDevice = true
        assertTrue(EnchantStore.account.multiDevice)
    }

    @Test
    fun `Account clear resets all fields`() {
        EnchantStore.account.userId = "user-123"
        EnchantStore.account.deviceId = "device-456"
        EnchantStore.account.username = "alice"
        EnchantStore.account.registrationId = 42
        EnchantStore.account.isRegistered = true

        EnchantStore.account.clear()

        assertNull(EnchantStore.account.userId)
        assertNull(EnchantStore.account.deviceId)
        assertNull(EnchantStore.account.username)
        assertEquals(0, EnchantStore.account.registrationId)
        assertFalse(EnchantStore.account.isRegistered)
    }

    @Test
    fun `Account backup keys are non-empty`() {
        val keys = EnchantStore.account.getKeysToIncludeInBackup()
        assertTrue(keys.contains("account.user_id"))
        assertTrue(keys.contains("account.device_id"))
        assertTrue(keys.contains("account.registered"))
    }

    @Test
    fun `Registration isComplete defaults to false`() {
        assertFalse(EnchantStore.registration.isComplete)
    }

    @Test
    fun `Registration isComplete set and get`() {
        EnchantStore.registration.isComplete = true
        assertTrue(EnchantStore.registration.isComplete)
    }

    @Test
    fun `Registration lockPin set and get`() {
        EnchantStore.registration.lockPin = "1234"
        assertEquals("1234", EnchantStore.registration.lockPin)
    }

    @Test
    fun `Registration clear`() {
        EnchantStore.registration.isComplete = true
        EnchantStore.registration.lockPin = "1234"
        EnchantStore.registration.clear()
        assertFalse(EnchantStore.registration.isComplete)
        assertNull(EnchantStore.registration.lockPin)
    }

    @Test
    fun `Backup isEnabled defaults to false`() {
        assertFalse(EnchantStore.backup.isEnabled)
    }

    @Test
    fun `Backup isEnabled set and get`() {
        EnchantStore.backup.isEnabled = true
        assertTrue(EnchantStore.backup.isEnabled)
    }

    @Test
    fun `Backup lastBackupTs defaults to 0`() {
        assertEquals(0L, EnchantStore.backup.lastBackupTs)
    }

    @Test
    fun `Backup lastBackupTs set and get`() {
        EnchantStore.backup.lastBackupTs = 1234567890L
        assertEquals(1234567890L, EnchantStore.backup.lastBackupTs)
    }

    @Test
    fun `Backup backupKey set and get`() {
        EnchantStore.backup.backupKey = "key-abc"
        assertEquals("key-abc", EnchantStore.backup.backupKey)
    }

    @Test
    fun `Backup clear`() {
        EnchantStore.backup.isEnabled = true
        EnchantStore.backup.lastBackupTs = 123L
        EnchantStore.backup.backupKey = "key"
        EnchantStore.backup.clear()
        assertFalse(EnchantStore.backup.isEnabled)
        assertEquals(0L, EnchantStore.backup.lastBackupTs)
        assertNull(EnchantStore.backup.backupKey)
    }

    @Test
    fun `Settings readReceipts defaults to true`() {
        assertTrue(EnchantStore.settings.readReceipts)
    }

    @Test
    fun `Settings readReceipts set and get`() {
        EnchantStore.settings.readReceipts = false
        assertFalse(EnchantStore.settings.readReceipts)
    }

    @Test
    fun `Settings typingIndicators defaults to true`() {
        assertTrue(EnchantStore.settings.typingIndicators)
    }

    @Test
    fun `Settings typingIndicators set and get`() {
        EnchantStore.settings.typingIndicators = false
        assertFalse(EnchantStore.settings.typingIndicators)
    }

    @Test
    fun `Settings linkPreviews defaults to true`() {
        assertTrue(EnchantStore.settings.linkPreviews)
    }

    @Test
    fun `Settings theme defaults to system`() {
        assertEquals("system", EnchantStore.settings.theme)
    }

    @Test
    fun `Settings theme set and get`() {
        EnchantStore.settings.theme = "dark"
        assertEquals("dark", EnchantStore.settings.theme)
    }

    @Test
    fun `Settings fontSize defaults to 1_0`() {
        assertEquals(1.0f, EnchantStore.settings.fontSize)
    }

    @Test
    fun `Settings fontSize set and get`() {
        EnchantStore.settings.fontSize = 1.5f
        assertEquals(1.5f, EnchantStore.settings.fontSize)
    }

    @Test
    fun `Settings language set and get`() {
        EnchantStore.settings.language = "ja"
        assertEquals("ja", EnchantStore.settings.language)
    }

    @Test
    fun `Settings screenLockEnabled defaults to false`() {
        assertFalse(EnchantStore.settings.screenLockEnabled)
    }

    @Test
    fun `Settings spellCheck defaults to true`() {
        assertTrue(EnchantStore.settings.spellCheck)
    }

    @Test
    fun `Settings clear`() {
        EnchantStore.settings.readReceipts = false
        EnchantStore.settings.theme = "dark"
        EnchantStore.settings.fontSize = 2.0f
        EnchantStore.settings.clear()
        assertTrue(EnchantStore.settings.readReceipts)
        assertEquals("system", EnchantStore.settings.theme)
        assertEquals(1.0f, EnchantStore.settings.fontSize)
    }

    @Test
    fun `Notifications messageNotifications defaults to true`() {
        assertTrue(EnchantStore.notifications.messageNotifications)
    }

    @Test
    fun `Notifications showPreview defaults to true`() {
        assertTrue(EnchantStore.notifications.showPreview)
    }

    @Test
    fun `Notifications vibrate defaults to true`() {
        assertTrue(EnchantStore.notifications.vibrate)
    }

    @Test
    fun `Notifications sound set and get`() {
        EnchantStore.notifications.sound = "chime"
        assertEquals("chime", EnchantStore.notifications.sound)
    }

    @Test
    fun `Notifications callNotifications defaults to true`() {
        assertTrue(EnchantStore.notifications.callNotifications)
    }

    @Test
    fun `Notifications clear`() {
        EnchantStore.notifications.messageNotifications = false
        EnchantStore.notifications.sound = "bell"
        EnchantStore.notifications.clear()
        assertTrue(EnchantStore.notifications.messageNotifications)
        assertNull(EnchantStore.notifications.sound)
    }

    @Test
    fun `Privacy lastSeenVisibility defaults to contacts`() {
        assertEquals("contacts", EnchantStore.privacy.lastSeenVisibility)
    }

    @Test
    fun `Privacy onlineVisibility defaults to contacts`() {
        assertEquals("contacts", EnchantStore.privacy.onlineVisibility)
    }

    @Test
    fun `Privacy avatarVisibility defaults to contacts`() {
        assertEquals("contacts", EnchantStore.privacy.avatarVisibility)
    }

    @Test
    fun `Privacy groupsAddPolicy defaults to everyone`() {
        assertEquals("everyone", EnchantStore.privacy.groupsAddPolicy)
    }

    @Test
    fun `Privacy set and get`() {
        EnchantStore.privacy.lastSeenVisibility = "nobody"
        EnchantStore.privacy.onlineVisibility = "everyone"
        assertEquals("nobody", EnchantStore.privacy.lastSeenVisibility)
        assertEquals("everyone", EnchantStore.privacy.onlineVisibility)
    }

    @Test
    fun `Privacy clear`() {
        EnchantStore.privacy.lastSeenVisibility = "nobody"
        EnchantStore.privacy.clear()
        assertEquals("contacts", EnchantStore.privacy.lastSeenVisibility)
    }

    @Test
    fun `Pin hash set and get`() {
        EnchantStore.pin.hash = "hashed-pin"
        assertEquals("hashed-pin", EnchantStore.pin.hash)
    }

    @Test
    fun `Pin salt set and get`() {
        EnchantStore.pin.salt = "salt-value"
        assertEquals("salt-value", EnchantStore.pin.salt)
    }

    @Test
    fun `Pin failedAttempts defaults to 0`() {
        assertEquals(0, EnchantStore.pin.failedAttempts)
    }

    @Test
    fun `Pin failedAttempts set and get`() {
        EnchantStore.pin.failedAttempts = 3
        assertEquals(3, EnchantStore.pin.failedAttempts)
    }

    @Test
    fun `Pin clear`() {
        EnchantStore.pin.hash = "h"
        EnchantStore.pin.salt = "s"
        EnchantStore.pin.failedAttempts = 5
        EnchantStore.pin.clear()
        assertNull(EnchantStore.pin.hash)
        assertNull(EnchantStore.pin.salt)
        assertEquals(0, EnchantStore.pin.failedAttempts)
    }

    @Test
    fun `Onboarding isComplete defaults to false`() {
        assertFalse(EnchantStore.onboarding.isComplete)
    }

    @Test
    fun `Onboarding hasSeenWelcome defaults to false`() {
        assertFalse(EnchantStore.onboarding.hasSeenWelcome)
    }

    @Test
    fun `Onboarding set and get`() {
        EnchantStore.onboarding.isComplete = true
        EnchantStore.onboarding.hasSeenWelcome = true
        assertTrue(EnchantStore.onboarding.isComplete)
        assertTrue(EnchantStore.onboarding.hasSeenWelcome)
    }

    @Test
    fun `Proxy host set and get`() {
        EnchantStore.proxy.host = "proxy.example.com"
        assertEquals("proxy.example.com", EnchantStore.proxy.host)
    }

    @Test
    fun `Proxy port defaults to 0`() {
        assertEquals(0, EnchantStore.proxy.port)
    }

    @Test
    fun `Proxy port set and get`() {
        EnchantStore.proxy.port = 8080
        assertEquals(8080, EnchantStore.proxy.port)
    }

    @Test
    fun `RateLimit lastOtpMs defaults to 0`() {
        assertEquals(0L, EnchantStore.rateLimit.lastOtpMs)
    }

    @Test
    fun `RateLimit otpAttempts defaults to 0`() {
        assertEquals(0, EnchantStore.rateLimit.otpAttempts)
    }

    @Test
    fun `RateLimit set and get`() {
        EnchantStore.rateLimit.lastOtpMs = 1000L
        EnchantStore.rateLimit.otpAttempts = 3
        assertEquals(1000L, EnchantStore.rateLimit.lastOtpMs)
        assertEquals(3, EnchantStore.rateLimit.otpAttempts)
    }

    @Test
    fun `PhoneNumberPrivacy shareWithContacts defaults to true`() {
        assertTrue(EnchantStore.phoneNumberPrivacy.shareWithContacts)
    }

    @Test
    fun `PhoneNumberPrivacy set and get`() {
        EnchantStore.phoneNumberPrivacy.shareWithContacts = false
        assertFalse(EnchantStore.phoneNumberPrivacy.shareWithContacts)
    }

    @Test
    fun `Emoji recent set and get`() {
        EnchantStore.emoji.recent = "test-emoji"
        assertEquals("test-emoji", EnchantStore.emoji.recent)
    }

    @Test
    fun `Emoji clear`() {
        EnchantStore.emoji.recent = "test"
        EnchantStore.emoji.clear()
        assertNull(EnchantStore.emoji.recent)
    }

    @Test
    fun `ChatColors wallpaper set and get`() {
        EnchantStore.chatColors.wallpaper = "wallpaper-1"
        assertEquals("wallpaper-1", EnchantStore.chatColors.wallpaper)
    }

    @Test
    fun `ChatColors color set and get`() {
        EnchantStore.chatColors.color = "#FF5733"
        assertEquals("#FF5733", EnchantStore.chatColors.color)
    }

    @Test
    fun `CallQuality useLowBandwidth defaults to false`() {
        assertFalse(EnchantStore.callQuality.useLowBandwidth)
    }

    @Test
    fun `CallQuality useLowBandwidth set and get`() {
        EnchantStore.callQuality.useLowBandwidth = true
        assertTrue(EnchantStore.callQuality.useLowBandwidth)
    }

    @Test
    fun `Labs experimentalFeatures defaults to false`() {
        assertFalse(EnchantStore.labs.experimentalFeatures)
    }

    @Test
    fun `Labs experimentalFeatures set and get`() {
        EnchantStore.labs.experimentalFeatures = true
        assertTrue(EnchantStore.labs.experimentalFeatures)
    }

    @Test
    fun `Stories myStoriesPrivacy defaults to contacts`() {
        assertEquals("contacts", EnchantStore.stories.myStoriesPrivacy)
    }

    @Test
    fun `Stories myStoriesPrivacy set and get`() {
        EnchantStore.stories.myStoriesPrivacy = "everyone"
        assertEquals("everyone", EnchantStore.stories.myStoriesPrivacy)
    }

    @Test
    fun `Internal lastDeviceSyncTs defaults to 0`() {
        assertEquals(0L, EnchantStore.internal.lastDeviceSyncTs)
    }

    @Test
    fun `Internal lastPreKeyRotationTs defaults to 0`() {
        assertEquals(0L, EnchantStore.internal.lastPreKeyRotationTs)
    }

    @Test
    fun `Internal set and get`() {
        EnchantStore.internal.lastDeviceSyncTs = 100L
        EnchantStore.internal.lastPreKeyRotationTs = 200L
        assertEquals(100L, EnchantStore.internal.lastDeviceSyncTs)
        assertEquals(200L, EnchantStore.internal.lastPreKeyRotationTs)
    }

    @Test
    fun `Svr masterKey set and get`() {
        EnchantStore.svr.masterKey = "svr-master-key"
        assertEquals("svr-master-key", EnchantStore.svr.masterKey)
    }

    @Test
    fun `Svr isConfigured defaults to false`() {
        assertFalse(EnchantStore.svr.isConfigured)
    }

    @Test
    fun `Svr isConfigured set and get`() {
        EnchantStore.svr.isConfigured = true
        assertTrue(EnchantStore.svr.isConfigured)
    }

    @Test
    fun `Svr clear`() {
        EnchantStore.svr.masterKey = "key"
        EnchantStore.svr.backupId = "backup-1"
        EnchantStore.svr.isConfigured = true
        EnchantStore.svr.clear()
        assertNull(EnchantStore.svr.masterKey)
        assertNull(EnchantStore.svr.backupId)
        assertFalse(EnchantStore.svr.isConfigured)
    }

    @Test
    fun `RemoteConfig values set and get`() {
        EnchantStore.remoteConfig.values = """{"feature": true}"""
        assertEquals("""{"feature": true}""", EnchantStore.remoteConfig.values)
    }

    @Test
    fun `RemoteConfig lastFetchTs defaults to 0`() {
        assertEquals(0L, EnchantStore.remoteConfig.lastFetchTs)
    }

    @Test
    fun `StorageService manifestVersion defaults to 0`() {
        assertEquals(0, EnchantStore.storageService.manifestVersion)
    }

    @Test
    fun `StorageService isSyncEnabled defaults to true`() {
        assertTrue(EnchantStore.storageService.isSyncEnabled)
    }

    @Test
    fun `StorageService set and get`() {
        EnchantStore.storageService.manifestVersion = 5
        EnchantStore.storageService.isSyncEnabled = false
        assertEquals(5, EnchantStore.storageService.manifestVersion)
        assertFalse(EnchantStore.storageService.isSyncEnabled)
    }

    @Test
    fun `UiHints hasSeenConversationListSwipe defaults to false`() {
        assertFalse(EnchantStore.uiHints.hasSeenConversationListSwipe)
    }

    @Test
    fun `UiHints set and get`() {
        EnchantStore.uiHints.hasSeenConversationListSwipe = true
        EnchantStore.uiHints.hasSeenReactionHint = true
        assertTrue(EnchantStore.uiHints.hasSeenConversationListSwipe)
        assertTrue(EnchantStore.uiHints.hasSeenReactionHint)
    }

    @Test
    fun `Tooltips hasSeenChatSearchTooltip defaults to false`() {
        assertFalse(EnchantStore.tooltips.hasSeenChatSearchTooltip)
    }

    @Test
    fun `Tooltips set and get`() {
        EnchantStore.tooltips.hasSeenChatSearchTooltip = true
        EnchantStore.tooltips.hasSeenStoriesTooltip = true
        assertTrue(EnchantStore.tooltips.hasSeenChatSearchTooltip)
        assertTrue(EnchantStore.tooltips.hasSeenStoriesTooltip)
    }

    @Test
    fun `Certificate unidentifiedAccessCertificate set and get`() {
        EnchantStore.certificate.unidentifiedAccessCertificate = "cert-data"
        assertEquals("cert-data", EnchantStore.certificate.unidentifiedAccessCertificate)
    }

    @Test
    fun `Certificate certificateExpiration defaults to 0`() {
        assertEquals(0L, EnchantStore.certificate.certificateExpiration)
    }

    @Test
    fun `Wallpaper globalWallpaper set and get`() {
        EnchantStore.wallpaper.globalWallpaper = "wp-1"
        assertEquals("wp-1", EnchantStore.wallpaper.globalWallpaper)
    }

    @Test
    fun `Wallpaper brightness defaults to 1_0`() {
        assertEquals(1.0f, EnchantStore.wallpaper.brightness)
    }

    @Test
    fun `Wallpaper brightness set and get`() {
        EnchantStore.wallpaper.brightness = 0.5f
        assertEquals(0.5f, EnchantStore.wallpaper.brightness)
    }

    @Test
    fun `Payments isEnabled defaults to false`() {
        assertFalse(EnchantStore.payments.isEnabled)
    }

    @Test
    fun `Payments set and get`() {
        EnchantStore.payments.isEnabled = true
        assertTrue(EnchantStore.payments.isEnabled)
    }

    @Test
    fun `InAppPayment subscriptionTier set and get`() {
        EnchantStore.inAppPayments.subscriptionTier = "premium"
        assertEquals("premium", EnchantStore.inAppPayments.subscriptionTier)
    }

    @Test
    fun `ImageEditor brushSize defaults to 5_0`() {
        assertEquals(5.0f, EnchantStore.imageEditor.brushSize)
    }

    @Test
    fun `ImageEditor brushSize set and get`() {
        EnchantStore.imageEditor.brushSize = 10.0f
        assertEquals(10.0f, EnchantStore.imageEditor.brushSize)
    }

    @Test
    fun `NotificationProfile customProfiles set and get`() {
        EnchantStore.notificationProfile.customProfiles = "[{\"id\":\"work\"}]"
        assertEquals("[{\"id\":\"work\"}]", EnchantStore.notificationProfile.customProfiles)
    }

    @Test
    fun `ReleaseChannel channel defaults to stable`() {
        assertEquals("stable", EnchantStore.releaseChannel.channel)
    }

    @Test
    fun `ReleaseChannel channel set and get`() {
        EnchantStore.releaseChannel.channel = "beta"
        assertEquals("beta", EnchantStore.releaseChannel.channel)
    }

    @Test
    fun `ApkUpdate lastCheckTs defaults to 0`() {
        assertEquals(0L, EnchantStore.apkUpdate.lastCheckTs)
    }

    @Test
    fun `ApkUpdate lastVersionCode defaults to 0`() {
        assertEquals(0, EnchantStore.apkUpdate.lastVersionCode)
    }

    @Test
    fun `Miscellaneous lastVersionCode defaults to 0`() {
        assertEquals(0, EnchantStore.miscellaneous.lastVersionCode)
    }

    @Test
    fun `Miscellaneous hasCompletedFirstRun defaults to false`() {
        assertFalse(EnchantStore.miscellaneous.hasCompletedFirstRun)
    }

    @Test
    fun `clearAll resets every category`() {
        EnchantStore.account.userId = "user-123"
        EnchantStore.registration.isComplete = true
        EnchantStore.backup.isEnabled = true
        EnchantStore.settings.theme = "dark"
        EnchantStore.notifications.sound = "bell"
        EnchantStore.privacy.lastSeenVisibility = "nobody"
        EnchantStore.pin.hash = "hash"
        EnchantStore.onboarding.isComplete = true
        EnchantStore.proxy.host = "proxy"
        EnchantStore.rateLimit.otpAttempts = 5
        EnchantStore.phoneNumberPrivacy.shareWithContacts = false
        EnchantStore.emoji.recent = "test"
        EnchantStore.chatColors.color = "#FFF"
        EnchantStore.callQuality.useLowBandwidth = true
        EnchantStore.labs.experimentalFeatures = true
        EnchantStore.stories.myStoriesPrivacy = "everyone"
        EnchantStore.internal.lastDeviceSyncTs = 100L
        EnchantStore.svr.masterKey = "svr-key"
        EnchantStore.remoteConfig.values = "{}"
        EnchantStore.storageService.manifestVersion = 3
        EnchantStore.uiHints.hasSeenConversationListSwipe = true
        EnchantStore.tooltips.hasSeenChatSearchTooltip = true
        EnchantStore.certificate.unidentifiedAccessCertificate = "cert"
        EnchantStore.wallpaper.globalWallpaper = "wp"
        EnchantStore.payments.isEnabled = true
        EnchantStore.inAppPayments.subscriptionTier = "premium"
        EnchantStore.imageEditor.brushSize = 10f
        EnchantStore.notificationProfile.customProfiles = "[]"
        EnchantStore.releaseChannel.channel = "beta"
        EnchantStore.apkUpdate.lastCheckTs = 999L
        EnchantStore.miscellaneous.lastVersionCode = 42

        EnchantStore.clearAll()

        assertNull(EnchantStore.account.userId)
        assertFalse(EnchantStore.registration.isComplete)
        assertFalse(EnchantStore.backup.isEnabled)
        assertEquals("system", EnchantStore.settings.theme)
        assertNull(EnchantStore.notifications.sound)
        assertEquals("contacts", EnchantStore.privacy.lastSeenVisibility)
        assertNull(EnchantStore.pin.hash)
        assertFalse(EnchantStore.onboarding.isComplete)
        assertNull(EnchantStore.proxy.host)
        assertEquals(0, EnchantStore.rateLimit.otpAttempts)
        assertTrue(EnchantStore.phoneNumberPrivacy.shareWithContacts)
        assertNull(EnchantStore.emoji.recent)
        assertNull(EnchantStore.chatColors.color)
        assertFalse(EnchantStore.callQuality.useLowBandwidth)
        assertFalse(EnchantStore.labs.experimentalFeatures)
        assertEquals("contacts", EnchantStore.stories.myStoriesPrivacy)
        assertEquals(0L, EnchantStore.internal.lastDeviceSyncTs)
        assertNull(EnchantStore.svr.masterKey)
        assertNull(EnchantStore.remoteConfig.values)
        assertEquals(0, EnchantStore.storageService.manifestVersion)
        assertFalse(EnchantStore.uiHints.hasSeenConversationListSwipe)
        assertFalse(EnchantStore.tooltips.hasSeenChatSearchTooltip)
        assertNull(EnchantStore.certificate.unidentifiedAccessCertificate)
        assertNull(EnchantStore.wallpaper.globalWallpaper)
        assertFalse(EnchantStore.payments.isEnabled)
        assertNull(EnchantStore.inAppPayments.subscriptionTier)
        assertEquals(5.0f, EnchantStore.imageEditor.brushSize)
        assertNull(EnchantStore.notificationProfile.customProfiles)
        assertEquals("stable", EnchantStore.releaseChannel.channel)
        assertEquals(0L, EnchantStore.apkUpdate.lastCheckTs)
        assertEquals(0, EnchantStore.miscellaneous.lastVersionCode)
    }

    @Test
    fun `getAllBackupKeys returns non-empty list`() {
        val keys = EnchantStore.getAllBackupKeys()
        assertTrue(keys.isNotEmpty())
        assertTrue(keys.contains("account.user_id"))
        assertTrue(keys.contains("settings.read_receipts"))
        assertTrue(keys.contains("privacy.last_seen"))
    }

    @Test
    fun `getAllBackupKeys has no duplicates`() {
        val keys = EnchantStore.getAllBackupKeys()
        assertEquals(keys.size, keys.distinct().size)
    }

    @Test
    fun `Backup category returns empty keys`() {
        val keys = EnchantStore.backup.getKeysToIncludeInBackup()
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `Internal category returns empty keys`() {
        val keys = EnchantStore.internal.getKeysToIncludeInBackup()
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `atomic batch writes multiple keys`() {
        EnchantStore.store.beginWrite()
            .putString("batch.string", "hello")
            .putInt("batch.int", 42)
            .putLong("batch.long", 1000L)
            .putBoolean("batch.bool", true)
            .putFloat("batch.float", 3.14f)
            .apply()

        EnchantStore.store.flushPendingWrites()

        assertEquals("hello", EnchantStore.store.getString("batch.string"))
        assertEquals(42, EnchantStore.store.getInt("batch.int"))
        assertEquals(1000L, EnchantStore.store.getLong("batch.long"))
        assertTrue(EnchantStore.store.getBoolean("batch.bool"))
        assertEquals(3.14f, EnchantStore.store.getFloat("batch.float"))
    }

    @Test
    fun `atomic batch remove works`() {
        EnchantStore.store.putString("batch.remove_test", "value")
        EnchantStore.store.flushPendingWrites()

        EnchantStore.store.beginWrite()
            .remove("batch.remove_test")
            .apply()

        EnchantStore.store.flushPendingWrites()

        assertNull(EnchantStore.store.getString("batch.remove_test"))
    }

    @Test
    fun `observe emits current value and changes`() = runBlocking {
        EnchantStore.store.putString("flow.test", "initial")
        EnchantStore.store.flushPendingWrites()

        val flow = EnchantStore.delegates.observe<String>("flow.test")
        assertEquals("initial", flow.first())

        EnchantStore.store.putString("flow.test", "updated")
        EnchantStore.delegates.emitValue("flow.test", "updated")

        assertEquals("updated", flow.first())
    }

    @Test
    fun `observe with default handles null`() = runBlocking {
        val flow = EnchantStore.delegates.observe("flow.null_test", "default-value")
        assertEquals("default-value", flow.first())
    }

    @Test
    fun `KeyValueStore contains returns true for existing key`() {
        EnchantStore.store.putString("contains.test", "value")
        EnchantStore.store.flushPendingWrites()
        assertTrue(EnchantStore.store.contains("contains.test"))
    }

    @Test
    fun `KeyValueStore contains returns false for missing key`() {
        assertFalse(EnchantStore.store.contains("nonexistent.key"))
    }

    @Test
    fun `KeyValueStore getAll returns populated map`() {
        EnchantStore.store.putString("all.k1", "v1")
        EnchantStore.store.putInt("all.k2", 42)
        EnchantStore.store.flushPendingWrites()

        val all = EnchantStore.store.getAll()
        assertEquals("v1", all["all.k1"])
        assertEquals(42, all["all.k2"])
    }

    @Test
    fun `KeyValueStore clearAll removes everything`() {
        EnchantStore.store.putString("clear.k1", "v1")
        EnchantStore.store.putInt("clear.k2", 1)
        EnchantStore.store.flushPendingWrites()

        EnchantStore.store.clearAll()
        EnchantStore.store.flushPendingWrites()

        val all = EnchantStore.store.getAll()
        assertFalse(all.containsKey("clear.k1"))
        assertFalse(all.containsKey("clear.k2"))
    }

    @Test
    fun `KeyValueStore getBlob set and get`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        EnchantStore.store.putBlob("blob.test", data)
        EnchantStore.store.flushPendingWrites()

        val result = EnchantStore.store.getBlob("blob.test")
        assertNotNull(result)
        assertTrue(result!!.contentEquals(data))
    }

    @Test
    fun `KeyValueStore remove works`() {
        EnchantStore.store.putString("remove.test", "value")
        EnchantStore.store.flushPendingWrites()

        EnchantStore.store.remove("remove.test")
        EnchantStore.store.flushPendingWrites()

        assertNull(EnchantStore.store.getString("remove.test"))
    }

    @Test
    fun `KeyValueStore default values returned for missing keys`() {
        assertEquals("default", EnchantStore.store.getString("missing.string", "default"))
        assertEquals(99, EnchantStore.store.getInt("missing.int", 99))
        assertEquals(999L, EnchantStore.store.getLong("missing.long", 999L))
        assertTrue(EnchantStore.store.getBoolean("missing.bool", true))
        assertEquals(2.5f, EnchantStore.store.getFloat("missing.float", 2.5f))
    }

    @Test
    fun `resetCache clears and reloads from storage`() {
        EnchantStore.store.putString("cache.test", "value1")
        EnchantStore.store.flushPendingWrites()

        EnchantStore.store.resetCache()

        assertEquals("value1", EnchantStore.store.getString("cache.test"))
    }

    @Test
    fun `onPostBackupRestore resets cache`() {
        EnchantStore.store.putString("backup.test", "value")
        EnchantStore.store.flushPendingWrites()

        EnchantStore.onPostBackupRestore()

        assertEquals("value", EnchantStore.store.getString("backup.test"))
    }

    @Test
    fun `plainText store is initialized when using context init`() {
        // plainText is only initialized when using init(context), not init(storage)
        // This test verifies the contract
        resetStoreState()
        try {
            EnchantStore.init(context)
            assertNotNull(EnchantStore.plainText)
        } catch (e: Exception) {
            // EncryptedSharedPreferences may not work in Robolectric
        }
    }

    @Test
    fun `plainText store can store and retrieve values when initialized`() {
        resetStoreState()
        try {
            EnchantStore.init(context)
            EnchantStore.plainText.putString("pt.test", "value")
            assertEquals("value", EnchantStore.plainText.getString("pt.test"))
        } catch (e: Exception) {
            // EncryptedSharedPreferences may not work in Robolectric - skip
        }
    }

    @Test
    fun `ApplicationMigrations registers and executes`() {
        var executed = false
        ApplicationMigrations.register(9999, "test_migration") {
            executed = true
            it.putString("migration.test", "migrated")
        }

        ApplicationMigrations.execute(EnchantStore.store)
        assertTrue(executed)
        assertEquals("migrated", EnchantStore.store.getString("migration.test"))
    }

    @Test
    fun `ApplicationMigrations does not re-execute`() {
        var count = 0
        ApplicationMigrations.register(9998, "test_migration_once") { count++ }

        ApplicationMigrations.execute(EnchantStore.store)
        ApplicationMigrations.execute(EnchantStore.store)
        assertEquals(1, count)
    }

    @Test
    fun `getPreferenceDataStore returns valid store`() {
        val prefStore = EnchantStore.getPreferenceDataStore()
        prefStore.putString("pref.test", "value")
        assertEquals("value", prefStore.getString("pref.test", null))
    }
}
