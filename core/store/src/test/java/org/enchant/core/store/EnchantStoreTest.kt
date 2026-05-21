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
        SecurePreferences.init(context)
        SecurePreferences.clearAll()
        resetStoreState()
        EnchantStore.init(InMemoryKeyValueStorage())
    }

    @After
    fun tearDown() {
        EnchantStore.store.close()
        resetStoreState()
    }

    private fun resetStoreState() {
        val field = EnchantStore::class.java.getDeclaredField("initialized")
        field.isAccessible = true
        field.set(null, false)
    }

    // -- Account tests --------------------------------------------------------------------

    @Test
    fun `Account userId defaults to null`() {
        assertNull(EnchantStore.Account.userId)
    }

    @Test
    fun `Account userId set and get`() {
        EnchantStore.Account.setUserId("user-123")
        assertEquals("user-123", EnchantStore.Account.userId)
    }

    @Test
    fun `Account deviceId set and get`() {
        EnchantStore.Account.setDeviceId("device-456")
        assertEquals("device-456", EnchantStore.Account.deviceId)
    }

    @Test
    fun `Account username set and get`() {
        EnchantStore.Account.setUsername("alice")
        assertEquals("alice", EnchantStore.Account.username)
    }

    @Test
    fun `Account displayName set and get`() {
        EnchantStore.Account.setDisplayName("Alice Smith")
        assertEquals("Alice Smith", EnchantStore.Account.displayName)
    }

    @Test
    fun `Account about set and get`() {
        EnchantStore.Account.setAbout("Hello!")
        assertEquals("Hello!", EnchantStore.Account.about)
    }

    @Test
    fun `Account registrationId defaults to 0`() {
        assertEquals(0, EnchantStore.Account.registrationId)
    }

    @Test
    fun `Account registrationId set and get`() {
        EnchantStore.Account.setRegistrationId(42)
        assertEquals(42, EnchantStore.Account.registrationId)
    }

    @Test
    fun `Account aci set and get`() {
        EnchantStore.Account.setAci("aci-uuid")
        assertEquals("aci-uuid", EnchantStore.Account.aci)
    }

    @Test
    fun `Account pni set and get`() {
        EnchantStore.Account.setPni("pni-uuid")
        assertEquals("pni-uuid", EnchantStore.Account.pni)
    }

    @Test
    fun `Account isRegistered defaults to false`() {
        assertFalse(EnchantStore.Account.isRegistered)
    }

    @Test
    fun `Account isRegistered set and get`() {
        EnchantStore.Account.setRegistered(true)
        assertTrue(EnchantStore.Account.isRegistered)
    }

    @Test
    fun `Account fcmToken set and get`() {
        EnchantStore.Account.setFcmToken("fcm-token-abc")
        assertEquals("fcm-token-abc", EnchantStore.Account.fcmToken)
    }

    @Test
    fun `Account multiDevice defaults to false`() {
        assertFalse(EnchantStore.Account.multiDevice)
    }

    @Test
    fun `Account multiDevice set and get`() {
        EnchantStore.Account.setMultiDevice(true)
        assertTrue(EnchantStore.Account.multiDevice)
    }

    @Test
    fun `Account clear resets all fields`() {
        EnchantStore.Account.setUserId("user-123")
        EnchantStore.Account.setDeviceId("device-456")
        EnchantStore.Account.setUsername("alice")
        EnchantStore.Account.setRegistrationId(42)
        EnchantStore.Account.setRegistered(true)

        EnchantStore.Account.clear()

        assertNull(EnchantStore.Account.userId)
        assertNull(EnchantStore.Account.deviceId)
        assertNull(EnchantStore.Account.username)
        assertEquals(0, EnchantStore.Account.registrationId)
        assertFalse(EnchantStore.Account.isRegistered)
    }

    @Test
    fun `Account backup keys are non-empty`() {
        val keys = EnchantStore.Account.getKeysToIncludeInBackup()
        assertTrue(keys.contains("account.user_id"))
        assertTrue(keys.contains("account.device_id"))
        assertTrue(keys.contains("account.registered"))
    }

    // -- Registration tests ---------------------------------------------------------------

    @Test
    fun `Registration isComplete defaults to false`() {
        assertFalse(EnchantStore.Registration.isComplete)
    }

    @Test
    fun `Registration isComplete set and get`() {
        EnchantStore.Registration.setComplete(true)
        assertTrue(EnchantStore.Registration.isComplete)
    }

    @Test
    fun `Registration lockPin set and get`() {
        EnchantStore.Registration.setLockPin("1234")
        assertEquals("1234", EnchantStore.Registration.lockPin)
    }

    @Test
    fun `Registration clear`() {
        EnchantStore.Registration.setComplete(true)
        EnchantStore.Registration.setLockPin("1234")
        EnchantStore.Registration.clear()
        assertFalse(EnchantStore.Registration.isComplete)
        assertNull(EnchantStore.Registration.lockPin)
    }

    // -- Backup tests ---------------------------------------------------------------------

    @Test
    fun `Backup isEnabled defaults to false`() {
        assertFalse(EnchantStore.Backup.isEnabled)
    }

    @Test
    fun `Backup isEnabled set and get`() {
        EnchantStore.Backup.setEnabled(true)
        assertTrue(EnchantStore.Backup.isEnabled)
    }

    @Test
    fun `Backup lastBackupTs defaults to 0`() {
        assertEquals(0L, EnchantStore.Backup.lastBackupTs)
    }

    @Test
    fun `Backup lastBackupTs set and get`() {
        EnchantStore.Backup.setLastBackupTs(1234567890L)
        assertEquals(1234567890L, EnchantStore.Backup.lastBackupTs)
    }

    @Test
    fun `Backup backupKey set and get`() {
        EnchantStore.Backup.setBackupKey("key-abc")
        assertEquals("key-abc", EnchantStore.Backup.backupKey)
    }

    @Test
    fun `Backup clear`() {
        EnchantStore.Backup.setEnabled(true)
        EnchantStore.Backup.setLastBackupTs(123L)
        EnchantStore.Backup.setBackupKey("key")
        EnchantStore.Backup.clear()
        assertFalse(EnchantStore.Backup.isEnabled)
        assertEquals(0L, EnchantStore.Backup.lastBackupTs)
        assertNull(EnchantStore.Backup.backupKey)
    }

    // -- Settings tests -------------------------------------------------------------------

    @Test
    fun `Settings readReceipts defaults to true`() {
        assertTrue(EnchantStore.Settings.readReceipts)
    }

    @Test
    fun `Settings readReceipts set and get`() {
        EnchantStore.Settings.setReadReceipts(false)
        assertFalse(EnchantStore.Settings.readReceipts)
    }

    @Test
    fun `Settings typingIndicators defaults to true`() {
        assertTrue(EnchantStore.Settings.typingIndicators)
    }

    @Test
    fun `Settings typingIndicators set and get`() {
        EnchantStore.Settings.setTypingIndicators(false)
        assertFalse(EnchantStore.Settings.typingIndicators)
    }

    @Test
    fun `Settings linkPreviews defaults to true`() {
        assertTrue(EnchantStore.Settings.linkPreviews)
    }

    @Test
    fun `Settings theme defaults to system`() {
        assertEquals("system", EnchantStore.Settings.theme)
    }

    @Test
    fun `Settings theme set and get`() {
        EnchantStore.Settings.setTheme("dark")
        assertEquals("dark", EnchantStore.Settings.theme)
    }

    @Test
    fun `Settings fontSize defaults to 1_0`() {
        assertEquals(1.0f, EnchantStore.Settings.fontSize)
    }

    @Test
    fun `Settings fontSize set and get`() {
        EnchantStore.Settings.setFontSize(1.5f)
        assertEquals(1.5f, EnchantStore.Settings.fontSize)
    }

    @Test
    fun `Settings language set and get`() {
        EnchantStore.Settings.setLanguage("ja")
        assertEquals("ja", EnchantStore.Settings.language)
    }

    @Test
    fun `Settings screenLockEnabled defaults to false`() {
        assertFalse(EnchantStore.Settings.screenLockEnabled)
    }

    @Test
    fun `Settings spellCheck defaults to true`() {
        assertTrue(EnchantStore.Settings.spellCheck)
    }

    @Test
    fun `Settings clear`() {
        EnchantStore.Settings.setReadReceipts(false)
        EnchantStore.Settings.setTheme("dark")
        EnchantStore.Settings.setFontSize(2.0f)
        EnchantStore.Settings.clear()
        assertTrue(EnchantStore.Settings.readReceipts)
        assertEquals("system", EnchantStore.Settings.theme)
        assertEquals(1.0f, EnchantStore.Settings.fontSize)
    }

    // -- Notifications tests --------------------------------------------------------------

    @Test
    fun `Notifications messageNotifications defaults to true`() {
        assertTrue(EnchantStore.Notifications.messageNotifications)
    }

    @Test
    fun `Notifications showPreview defaults to true`() {
        assertTrue(EnchantStore.Notifications.showPreview)
    }

    @Test
    fun `Notifications vibrate defaults to true`() {
        assertTrue(EnchantStore.Notifications.vibrate)
    }

    @Test
    fun `Notifications sound set and get`() {
        EnchantStore.Notifications.setSound("chime")
        assertEquals("chime", EnchantStore.Notifications.sound)
    }

    @Test
    fun `Notifications callNotifications defaults to true`() {
        assertTrue(EnchantStore.Notifications.callNotifications)
    }

    @Test
    fun `Notifications clear`() {
        EnchantStore.Notifications.setMessageNotifications(false)
        EnchantStore.Notifications.setSound("bell")
        EnchantStore.Notifications.clear()
        assertTrue(EnchantStore.Notifications.messageNotifications)
        assertNull(EnchantStore.Notifications.sound)
    }

    // -- Privacy tests --------------------------------------------------------------------

    @Test
    fun `Privacy lastSeenVisibility defaults to contacts`() {
        assertEquals("contacts", EnchantStore.Privacy.lastSeenVisibility)
    }

    @Test
    fun `Privacy onlineVisibility defaults to contacts`() {
        assertEquals("contacts", EnchantStore.Privacy.onlineVisibility)
    }

    @Test
    fun `Privacy avatarVisibility defaults to contacts`() {
        assertEquals("contacts", EnchantStore.Privacy.avatarVisibility)
    }

    @Test
    fun `Privacy groupsAddPolicy defaults to everyone`() {
        assertEquals("everyone", EnchantStore.Privacy.groupsAddPolicy)
    }

    @Test
    fun `Privacy set and get`() {
        EnchantStore.Privacy.setLastSeenVisibility("nobody")
        EnchantStore.Privacy.setOnlineVisibility("everyone")
        assertEquals("nobody", EnchantStore.Privacy.lastSeenVisibility)
        assertEquals("everyone", EnchantStore.Privacy.onlineVisibility)
    }

    @Test
    fun `Privacy clear`() {
        EnchantStore.Privacy.setLastSeenVisibility("nobody")
        EnchantStore.Privacy.clear()
        assertEquals("contacts", EnchantStore.Privacy.lastSeenVisibility)
    }

    // -- Pin tests ------------------------------------------------------------------------

    @Test
    fun `Pin hash set and get`() {
        EnchantStore.Pin.setHash("hashed-pin")
        assertEquals("hashed-pin", EnchantStore.Pin.hash)
    }

    @Test
    fun `Pin salt set and get`() {
        EnchantStore.Pin.setSalt("salt-value")
        assertEquals("salt-value", EnchantStore.Pin.salt)
    }

    @Test
    fun `Pin failedAttempts defaults to 0`() {
        assertEquals(0, EnchantStore.Pin.failedAttempts)
    }

    @Test
    fun `Pin failedAttempts set and get`() {
        EnchantStore.Pin.setFailedAttempts(3)
        assertEquals(3, EnchantStore.Pin.failedAttempts)
    }

    @Test
    fun `Pin clear`() {
        EnchantStore.Pin.setHash("h")
        EnchantStore.Pin.setSalt("s")
        EnchantStore.Pin.setFailedAttempts(5)
        EnchantStore.Pin.clear()
        assertNull(EnchantStore.Pin.hash)
        assertNull(EnchantStore.Pin.salt)
        assertEquals(0, EnchantStore.Pin.failedAttempts)
    }

    // -- Onboarding tests -----------------------------------------------------------------

    @Test
    fun `Onboarding isComplete defaults to false`() {
        assertFalse(EnchantStore.Onboarding.isComplete)
    }

    @Test
    fun `Onboarding hasSeenWelcome defaults to false`() {
        assertFalse(EnchantStore.Onboarding.hasSeenWelcome)
    }

    @Test
    fun `Onboarding set and get`() {
        EnchantStore.Onboarding.setComplete(true)
        EnchantStore.Onboarding.setHasSeenWelcome(true)
        assertTrue(EnchantStore.Onboarding.isComplete)
        assertTrue(EnchantStore.Onboarding.hasSeenWelcome)
    }

    // -- Proxy tests ----------------------------------------------------------------------

    @Test
    fun `Proxy host set and get`() {
        EnchantStore.Proxy.setHost("proxy.example.com")
        assertEquals("proxy.example.com", EnchantStore.Proxy.host)
    }

    @Test
    fun `Proxy port defaults to 0`() {
        assertEquals(0, EnchantStore.Proxy.port)
    }

    @Test
    fun `Proxy port set and get`() {
        EnchantStore.Proxy.setPort(8080)
        assertEquals(8080, EnchantStore.Proxy.port)
    }

    // -- RateLimit tests ------------------------------------------------------------------

    @Test
    fun `RateLimit lastOtpMs defaults to 0`() {
        assertEquals(0L, EnchantStore.RateLimit.lastOtpMs)
    }

    @Test
    fun `RateLimit otpAttempts defaults to 0`() {
        assertEquals(0, EnchantStore.RateLimit.otpAttempts)
    }

    @Test
    fun `RateLimit set and get`() {
        EnchantStore.RateLimit.setLastOtpMs(1000L)
        EnchantStore.RateLimit.setOtpAttempts(3)
        assertEquals(1000L, EnchantStore.RateLimit.lastOtpMs)
        assertEquals(3, EnchantStore.RateLimit.otpAttempts)
    }

    // -- PhoneNumberPrivacy tests ---------------------------------------------------------

    @Test
    fun `PhoneNumberPrivacy shareWithContacts defaults to true`() {
        assertTrue(EnchantStore.PhoneNumberPrivacy.shareWithContacts)
    }

    @Test
    fun `PhoneNumberPrivacy set and get`() {
        EnchantStore.PhoneNumberPrivacy.setShareWithContacts(false)
        assertFalse(EnchantStore.PhoneNumberPrivacy.shareWithContacts)
    }

    // -- Emoji tests ----------------------------------------------------------------------

    @Test
    fun `Emoji recent set and get`() {
        EnchantStore.Emoji.setRecent("😀,❤️,👍")
        assertEquals("😀,❤️,👍", EnchantStore.Emoji.recent)
    }

    @Test
    fun `Emoji clear`() {
        EnchantStore.Emoji.setRecent("😀")
        EnchantStore.Emoji.clear()
        assertNull(EnchantStore.Emoji.recent)
    }

    // -- ChatColors tests -----------------------------------------------------------------

    @Test
    fun `ChatColors wallpaper set and get`() {
        EnchantStore.ChatColors.setWallpaper("wallpaper-1")
        assertEquals("wallpaper-1", EnchantStore.ChatColors.wallpaper)
    }

    @Test
    fun `ChatColors color set and get`() {
        EnchantStore.ChatColors.setColor("#FF5733")
        assertEquals("#FF5733", EnchantStore.ChatColors.color)
    }

    // -- CallQuality tests ----------------------------------------------------------------

    @Test
    fun `CallQuality useLowBandwidth defaults to false`() {
        assertFalse(EnchantStore.CallQuality.useLowBandwidth)
    }

    @Test
    fun `CallQuality useLowBandwidth set and get`() {
        EnchantStore.CallQuality.setUseLowBandwidth(true)
        assertTrue(EnchantStore.CallQuality.useLowBandwidth)
    }

    // -- Labs tests -----------------------------------------------------------------------

    @Test
    fun `Labs experimentalFeatures defaults to false`() {
        assertFalse(EnchantStore.Labs.experimentalFeatures)
    }

    @Test
    fun `Labs experimentalFeatures set and get`() {
        EnchantStore.Labs.setExperimentalFeatures(true)
        assertTrue(EnchantStore.Labs.experimentalFeatures)
    }

    // -- Stories tests --------------------------------------------------------------------

    @Test
    fun `Stories myStoriesPrivacy defaults to contacts`() {
        assertEquals("contacts", EnchantStore.Stories.myStoriesPrivacy)
    }

    @Test
    fun `Stories myStoriesPrivacy set and get`() {
        EnchantStore.Stories.setMyStoriesPrivacy("everyone")
        assertEquals("everyone", EnchantStore.Stories.myStoriesPrivacy)
    }

    // -- Internal tests -------------------------------------------------------------------

    @Test
    fun `Internal lastDeviceSyncTs defaults to 0`() {
        assertEquals(0L, EnchantStore.Internal.lastDeviceSyncTs)
    }

    @Test
    fun `Internal lastPreKeyRotationTs defaults to 0`() {
        assertEquals(0L, EnchantStore.Internal.lastPreKeyRotationTs)
    }

    @Test
    fun `Internal set and get`() {
        EnchantStore.Internal.setLastDeviceSyncTs(100L)
        EnchantStore.Internal.setLastPreKeyRotationTs(200L)
        assertEquals(100L, EnchantStore.Internal.lastDeviceSyncTs)
        assertEquals(200L, EnchantStore.Internal.lastPreKeyRotationTs)
    }

    // -- SVR tests ------------------------------------------------------------------------

    @Test
    fun `Svr masterKey set and get`() {
        EnchantStore.Svr.setMasterKey("svr-master-key")
        assertEquals("svr-master-key", EnchantStore.Svr.masterKey)
    }

    @Test
    fun `Svr isConfigured defaults to false`() {
        assertFalse(EnchantStore.Svr.isConfigured)
    }

    @Test
    fun `Svr isConfigured set and get`() {
        EnchantStore.Svr.setIsConfigured(true)
        assertTrue(EnchantStore.Svr.isConfigured)
    }

    @Test
    fun `Svr clear`() {
        EnchantStore.Svr.setMasterKey("key")
        EnchantStore.Svr.setBackupId("backup-1")
        EnchantStore.Svr.setIsConfigured(true)
        EnchantStore.Svr.clear()
        assertNull(EnchantStore.Svr.masterKey)
        assertNull(EnchantStore.Svr.backupId)
        assertFalse(EnchantStore.Svr.isConfigured)
    }

    // -- RemoteConfig tests ---------------------------------------------------------------

    @Test
    fun `RemoteConfig values set and get`() {
        EnchantStore.RemoteConfig.setValues("""{"feature": true}""")
        assertEquals("""{"feature": true}""", EnchantStore.RemoteConfig.values)
    }

    @Test
    fun `RemoteConfig lastFetchTs defaults to 0`() {
        assertEquals(0L, EnchantStore.RemoteConfig.lastFetchTs)
    }

    // -- StorageService tests -------------------------------------------------------------

    @Test
    fun `StorageService manifestVersion defaults to 0`() {
        assertEquals(0, EnchantStore.StorageService.manifestVersion)
    }

    @Test
    fun `StorageService isSyncEnabled defaults to true`() {
        assertTrue(EnchantStore.StorageService.isSyncEnabled)
    }

    @Test
    fun `StorageService set and get`() {
        EnchantStore.StorageService.setManifestVersion(5)
        EnchantStore.StorageService.setSyncEnabled(false)
        assertEquals(5, EnchantStore.StorageService.manifestVersion)
        assertFalse(EnchantStore.StorageService.isSyncEnabled)
    }

    // -- UiHints tests --------------------------------------------------------------------

    @Test
    fun `UiHints hasSeenConversationListSwipe defaults to false`() {
        assertFalse(EnchantStore.UiHints.hasSeenConversationListSwipe)
    }

    @Test
    fun `UiHints set and get`() {
        EnchantStore.UiHints.setHasSeenConversationListSwipe(true)
        EnchantStore.UiHints.setHasSeenReactionHint(true)
        assertTrue(EnchantStore.UiHints.hasSeenConversationListSwipe)
        assertTrue(EnchantStore.UiHints.hasSeenReactionHint)
    }

    // -- Tooltips tests -------------------------------------------------------------------

    @Test
    fun `Tooltips hasSeenChatSearchTooltip defaults to false`() {
        assertFalse(EnchantStore.Tooltips.hasSeenChatSearchTooltip)
    }

    @Test
    fun `Tooltips set and get`() {
        EnchantStore.Tooltips.setHasSeenChatSearchTooltip(true)
        EnchantStore.Tooltips.setHasSeenStoriesTooltip(true)
        assertTrue(EnchantStore.Tooltips.hasSeenChatSearchTooltip)
        assertTrue(EnchantStore.Tooltips.hasSeenStoriesTooltip)
    }

    // -- Certificate tests ----------------------------------------------------------------

    @Test
    fun `Certificate unidentifiedAccessCertificate set and get`() {
        EnchantStore.Certificate.setUnidentifiedAccessCertificate("cert-data")
        assertEquals("cert-data", EnchantStore.Certificate.unidentifiedAccessCertificate)
    }

    @Test
    fun `Certificate certificateExpiration defaults to 0`() {
        assertEquals(0L, EnchantStore.Certificate.certificateExpiration)
    }

    // -- Wallpaper tests ------------------------------------------------------------------

    @Test
    fun `Wallpaper globalWallpaper set and get`() {
        EnchantStore.Wallpaper.setGlobalWallpaper("wp-1")
        assertEquals("wp-1", EnchantStore.Wallpaper.globalWallpaper)
    }

    @Test
    fun `Wallpaper brightness defaults to 1_0`() {
        assertEquals(1.0f, EnchantStore.Wallpaper.brightness)
    }

    @Test
    fun `Wallpaper brightness set and get`() {
        EnchantStore.Wallpaper.setBrightness(0.5f)
        assertEquals(0.5f, EnchantStore.Wallpaper.brightness)
    }

    // -- Payments tests -------------------------------------------------------------------

    @Test
    fun `Payments isEnabled defaults to false`() {
        assertFalse(EnchantStore.Payments.isEnabled)
    }

    @Test
    fun `Payments set and get`() {
        EnchantStore.Payments.setEnabled(true)
        assertTrue(EnchantStore.Payments.isEnabled)
    }

    // -- InAppPayment tests ---------------------------------------------------------------

    @Test
    fun `InAppPayment subscriptionTier set and get`() {
        EnchantStore.InAppPayment.setSubscriptionTier("premium")
        assertEquals("premium", EnchantStore.InAppPayment.subscriptionTier)
    }

    // -- ImageEditor tests ----------------------------------------------------------------

    @Test
    fun `ImageEditor brushSize defaults to 5_0`() {
        assertEquals(5.0f, EnchantStore.ImageEditor.brushSize)
    }

    @Test
    fun `ImageEditor brushSize set and get`() {
        EnchantStore.ImageEditor.setBrushSize(10.0f)
        assertEquals(10.0f, EnchantStore.ImageEditor.brushSize)
    }

    // -- NotificationProfile tests --------------------------------------------------------

    @Test
    fun `NotificationProfile customProfiles set and get`() {
        EnchantStore.NotificationProfile.setCustomProfiles("[{\"id\":\"work\"}]")
        assertEquals("[{\"id\":\"work\"}]", EnchantStore.NotificationProfile.customProfiles)
    }

    // -- ReleaseChannel tests -------------------------------------------------------------

    @Test
    fun `ReleaseChannel channel defaults to stable`() {
        assertEquals("stable", EnchantStore.ReleaseChannel.channel)
    }

    @Test
    fun `ReleaseChannel channel set and get`() {
        EnchantStore.ReleaseChannel.setChannel("beta")
        assertEquals("beta", EnchantStore.ReleaseChannel.channel)
    }

    // -- ApkUpdate tests ------------------------------------------------------------------

    @Test
    fun `ApkUpdate lastCheckTs defaults to 0`() {
        assertEquals(0L, EnchantStore.ApkUpdate.lastCheckTs)
    }

    @Test
    fun `ApkUpdate lastVersionCode defaults to 0`() {
        assertEquals(0, EnchantStore.ApkUpdate.lastVersionCode)
    }

    // -- Miscellaneous tests --------------------------------------------------------------

    @Test
    fun `Miscellaneous lastVersionCode defaults to 0`() {
        assertEquals(0, EnchantStore.Miscellaneous.lastVersionCode)
    }

    @Test
    fun `Miscellaneous hasCompletedFirstRun defaults to false`() {
        assertFalse(EnchantStore.Miscellaneous.hasCompletedFirstRun)
    }

    // -- Global clearAll tests ------------------------------------------------------------

    @Test
    fun `clearAll resets every category`() {
        EnchantStore.Account.setUserId("user-123")
        EnchantStore.Registration.setComplete(true)
        EnchantStore.Backup.setEnabled(true)
        EnchantStore.Settings.setTheme("dark")
        EnchantStore.Notifications.setSound("bell")
        EnchantStore.Privacy.setLastSeenVisibility("nobody")
        EnchantStore.Pin.setHash("hash")
        EnchantStore.Onboarding.setComplete(true)
        EnchantStore.Proxy.setHost("proxy")
        EnchantStore.RateLimit.setOtpAttempts(5)
        EnchantStore.PhoneNumberPrivacy.setShareWithContacts(false)
        EnchantStore.Emoji.setRecent("😀")
        EnchantStore.ChatColors.setColor("#FFF")
        EnchantStore.CallQuality.setUseLowBandwidth(true)
        EnchantStore.Labs.setExperimentalFeatures(true)
        EnchantStore.Stories.setMyStoriesPrivacy("everyone")
        EnchantStore.Internal.setLastDeviceSyncTs(100L)
        EnchantStore.Svr.setMasterKey("svr-key")
        EnchantStore.RemoteConfig.setValues("{}")
        EnchantStore.StorageService.setManifestVersion(3)
        EnchantStore.UiHints.setHasSeenConversationListSwipe(true)
        EnchantStore.Tooltips.setHasSeenChatSearchTooltip(true)
        EnchantStore.Certificate.setUnidentifiedAccessCertificate("cert")
        EnchantStore.Wallpaper.setGlobalWallpaper("wp")
        EnchantStore.Payments.setEnabled(true)
        EnchantStore.InAppPayment.setSubscriptionTier("premium")
        EnchantStore.ImageEditor.setBrushSize(10f)
        EnchantStore.NotificationProfile.setCustomProfiles("[]")
        EnchantStore.ReleaseChannel.setChannel("beta")
        EnchantStore.ApkUpdate.setLastCheckTs(999L)
        EnchantStore.Miscellaneous.setLastVersionCode(42)

        EnchantStore.clearAll()

        assertNull(EnchantStore.Account.userId)
        assertFalse(EnchantStore.Registration.isComplete)
        assertFalse(EnchantStore.Backup.isEnabled)
        assertEquals("system", EnchantStore.Settings.theme)
        assertNull(EnchantStore.Notifications.sound)
        assertEquals("contacts", EnchantStore.Privacy.lastSeenVisibility)
        assertNull(EnchantStore.Pin.hash)
        assertFalse(EnchantStore.Onboarding.isComplete)
        assertNull(EnchantStore.Proxy.host)
        assertEquals(0, EnchantStore.RateLimit.otpAttempts)
        assertTrue(EnchantStore.PhoneNumberPrivacy.shareWithContacts)
        assertNull(EnchantStore.Emoji.recent)
        assertNull(EnchantStore.ChatColors.color)
        assertFalse(EnchantStore.CallQuality.useLowBandwidth)
        assertFalse(EnchantStore.Labs.experimentalFeatures)
        assertEquals("contacts", EnchantStore.Stories.myStoriesPrivacy)
        assertEquals(0L, EnchantStore.Internal.lastDeviceSyncTs)
        assertNull(EnchantStore.Svr.masterKey)
        assertNull(EnchantStore.RemoteConfig.values)
        assertEquals(0, EnchantStore.StorageService.manifestVersion)
        assertFalse(EnchantStore.UiHints.hasSeenConversationListSwipe)
        assertFalse(EnchantStore.Tooltips.hasSeenChatSearchTooltip)
        assertNull(EnchantStore.Certificate.unidentifiedAccessCertificate)
        assertNull(EnchantStore.Wallpaper.globalWallpaper)
        assertFalse(EnchantStore.Payments.isEnabled)
        assertNull(EnchantStore.InAppPayment.subscriptionTier)
        assertEquals(5.0f, EnchantStore.ImageEditor.brushSize)
        assertNull(EnchantStore.NotificationProfile.customProfiles)
        assertEquals("stable", EnchantStore.ReleaseChannel.channel)
        assertEquals(0L, EnchantStore.ApkUpdate.lastCheckTs)
        assertEquals(0, EnchantStore.Miscellaneous.lastVersionCode)
    }

    // -- Backup keys tests ----------------------------------------------------------------

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
        val keys = EnchantStore.Backup.getKeysToIncludeInBackup()
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `Internal category returns empty keys`() {
        val keys = EnchantStore.Internal.getKeysToIncludeInBackup()
        assertTrue(keys.isEmpty())
    }

    // -- Atomic batch write tests ---------------------------------------------------------

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

    // -- Flow observation tests -----------------------------------------------------------

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

    // -- KeyValueStore direct tests -------------------------------------------------------

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
}
