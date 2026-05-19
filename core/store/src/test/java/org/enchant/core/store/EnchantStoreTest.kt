package org.enchant.core.store

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.enchant.core.base.SecurePreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(AndroidJUnit4::class)
class EnchantStoreTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SecurePreferences.init(context)
        EnchantStore.clearAll()
    }

    @After
    fun tearDown() {
        EnchantStore.clearAll()
    }

    // -- Account --------------------------------------------------------------------------

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
        EnchantStore.Account.setAbout("Hey there!")
        assertEquals("Hey there!", EnchantStore.Account.about)
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
        EnchantStore.Account.setAci("aci-abc")
        assertEquals("aci-abc", EnchantStore.Account.aci)
    }

    @Test
    fun `Account pni set and get`() {
        EnchantStore.Account.setPni("pni-xyz")
        assertEquals("pni-xyz", EnchantStore.Account.pni)
    }

    @Test
    fun `Account clear removes all values`() {
        EnchantStore.Account.setUserId("user-123")
        EnchantStore.Account.setDeviceId("device-456")
        EnchantStore.Account.setUsername("alice")
        EnchantStore.Account.setDisplayName("Alice")
        EnchantStore.Account.setAbout("Hey")
        EnchantStore.Account.setRegistrationId(42)
        EnchantStore.Account.setAci("aci-abc")
        EnchantStore.Account.setPni("pni-xyz")
        EnchantStore.Account.clear()
        assertNull(EnchantStore.Account.userId)
        assertNull(EnchantStore.Account.deviceId)
        assertNull(EnchantStore.Account.username)
        assertNull(EnchantStore.Account.displayName)
        assertNull(EnchantStore.Account.about)
        assertEquals(0, EnchantStore.Account.registrationId)
        assertNull(EnchantStore.Account.aci)
        assertNull(EnchantStore.Account.pni)
    }

    // -- Registration ---------------------------------------------------------------------

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
    fun `Registration clear removes all values`() {
        EnchantStore.Registration.setComplete(true)
        EnchantStore.Registration.setLockPin("1234")
        EnchantStore.Registration.clear()
        assertFalse(EnchantStore.Registration.isComplete)
        assertNull(EnchantStore.Registration.lockPin)
    }

    // -- Backup ---------------------------------------------------------------------------

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
    fun `Backup clear removes all values`() {
        EnchantStore.Backup.setEnabled(true)
        EnchantStore.Backup.setLastBackupTs(123L)
        EnchantStore.Backup.setBackupKey("key")
        EnchantStore.Backup.clear()
        assertFalse(EnchantStore.Backup.isEnabled)
        assertEquals(0L, EnchantStore.Backup.lastBackupTs)
        assertNull(EnchantStore.Backup.backupKey)
    }

    // -- Settings -------------------------------------------------------------------------

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
    fun `Settings linkPreviews set and get`() {
        EnchantStore.Settings.setLinkPreviews(false)
        assertFalse(EnchantStore.Settings.linkPreviews)
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
    fun `Settings fontSize defaults to one`() {
        assertEquals(1.0f, EnchantStore.Settings.fontSize, 0.001f)
    }

    @Test
    fun `Settings fontSize set and get as float`() {
        EnchantStore.Settings.setFontSize(1.25f)
        assertEquals(1.25f, EnchantStore.Settings.fontSize, 0.001f)
    }

    @Test
    fun `Settings language set and get`() {
        EnchantStore.Settings.setLanguage("en")
        assertEquals("en", EnchantStore.Settings.language)
    }

    @Test
    fun `Settings clear removes all values`() {
        EnchantStore.Settings.setReadReceipts(false)
        EnchantStore.Settings.setTypingIndicators(false)
        EnchantStore.Settings.setLinkPreviews(false)
        EnchantStore.Settings.setTheme("dark")
        EnchantStore.Settings.setFontSize(1.5f)
        EnchantStore.Settings.setLanguage("fr")
        EnchantStore.Settings.clear()
        assertTrue(EnchantStore.Settings.readReceipts)
        assertTrue(EnchantStore.Settings.typingIndicators)
        assertTrue(EnchantStore.Settings.linkPreviews)
        assertEquals("system", EnchantStore.Settings.theme)
        assertEquals(1.0f, EnchantStore.Settings.fontSize, 0.001f)
        assertNull(EnchantStore.Settings.language)
    }

    // -- Notifications --------------------------------------------------------------------

    @Test
    fun `Notifications messageNotifications defaults to true`() {
        assertTrue(EnchantStore.Notifications.messageNotifications)
    }

    @Test
    fun `Notifications messageNotifications set and get`() {
        EnchantStore.Notifications.setMessageNotifications(false)
        assertFalse(EnchantStore.Notifications.messageNotifications)
    }

    @Test
    fun `Notifications showPreview defaults to true`() {
        assertTrue(EnchantStore.Notifications.showPreview)
    }

    @Test
    fun `Notifications showPreview set and get`() {
        EnchantStore.Notifications.setShowPreview(false)
        assertFalse(EnchantStore.Notifications.showPreview)
    }

    @Test
    fun `Notifications sound set and get`() {
        EnchantStore.Notifications.setSound("default")
        assertEquals("default", EnchantStore.Notifications.sound)
    }

    @Test
    fun `Notifications vibrate defaults to true`() {
        assertTrue(EnchantStore.Notifications.vibrate)
    }

    @Test
    fun `Notifications vibrate set and get`() {
        EnchantStore.Notifications.setVibrate(false)
        assertFalse(EnchantStore.Notifications.vibrate)
    }

    @Test
    fun `Notifications clear removes all values`() {
        EnchantStore.Notifications.setMessageNotifications(false)
        EnchantStore.Notifications.setShowPreview(false)
        EnchantStore.Notifications.setSound("silent")
        EnchantStore.Notifications.setVibrate(false)
        EnchantStore.Notifications.clear()
        assertTrue(EnchantStore.Notifications.messageNotifications)
        assertTrue(EnchantStore.Notifications.showPreview)
        assertNull(EnchantStore.Notifications.sound)
        assertTrue(EnchantStore.Notifications.vibrate)
    }

    // -- Privacy --------------------------------------------------------------------------

    @Test
    fun `Privacy lastSeenVisibility defaults to contacts`() {
        assertEquals("contacts", EnchantStore.Privacy.lastSeenVisibility)
    }

    @Test
    fun `Privacy lastSeenVisibility set and get`() {
        EnchantStore.Privacy.setLastSeenVisibility("everyone")
        assertEquals("everyone", EnchantStore.Privacy.lastSeenVisibility)
    }

    @Test
    fun `Privacy onlineVisibility defaults to contacts`() {
        assertEquals("contacts", EnchantStore.Privacy.onlineVisibility)
    }

    @Test
    fun `Privacy onlineVisibility set and get`() {
        EnchantStore.Privacy.setOnlineVisibility("nobody")
        assertEquals("nobody", EnchantStore.Privacy.onlineVisibility)
    }

    @Test
    fun `Privacy avatarVisibility defaults to contacts`() {
        assertEquals("contacts", EnchantStore.Privacy.avatarVisibility)
    }

    @Test
    fun `Privacy avatarVisibility set and get`() {
        EnchantStore.Privacy.setAvatarVisibility("everyone")
        assertEquals("everyone", EnchantStore.Privacy.avatarVisibility)
    }

    @Test
    fun `Privacy aboutVisibility defaults to contacts`() {
        assertEquals("contacts", EnchantStore.Privacy.aboutVisibility)
    }

    @Test
    fun `Privacy aboutVisibility set and get`() {
        EnchantStore.Privacy.setAboutVisibility("nobody")
        assertEquals("nobody", EnchantStore.Privacy.aboutVisibility)
    }

    @Test
    fun `Privacy groupsAddPolicy defaults to everyone`() {
        assertEquals("everyone", EnchantStore.Privacy.groupsAddPolicy)
    }

    @Test
    fun `Privacy groupsAddPolicy set and get`() {
        EnchantStore.Privacy.setGroupsAddPolicy("contacts")
        assertEquals("contacts", EnchantStore.Privacy.groupsAddPolicy)
    }

    @Test
    fun `Privacy clear removes all values`() {
        EnchantStore.Privacy.setLastSeenVisibility("nobody")
        EnchantStore.Privacy.setOnlineVisibility("nobody")
        EnchantStore.Privacy.setAvatarVisibility("nobody")
        EnchantStore.Privacy.setAboutVisibility("nobody")
        EnchantStore.Privacy.setGroupsAddPolicy("nobody")
        EnchantStore.Privacy.clear()
        assertEquals("contacts", EnchantStore.Privacy.lastSeenVisibility)
        assertEquals("contacts", EnchantStore.Privacy.onlineVisibility)
        assertEquals("contacts", EnchantStore.Privacy.avatarVisibility)
        assertEquals("contacts", EnchantStore.Privacy.aboutVisibility)
        assertEquals("everyone", EnchantStore.Privacy.groupsAddPolicy)
    }

    // -- Pin ------------------------------------------------------------------------------

    @Test
    fun `Pin hash set and get`() {
        EnchantStore.Pin.setHash("hash123")
        assertEquals("hash123", EnchantStore.Pin.hash)
    }

    @Test
    fun `Pin salt set and get`() {
        EnchantStore.Pin.setSalt("salt456")
        assertEquals("salt456", EnchantStore.Pin.salt)
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
    fun `Pin clear removes all values`() {
        EnchantStore.Pin.setHash("hash")
        EnchantStore.Pin.setSalt("salt")
        EnchantStore.Pin.setFailedAttempts(5)
        EnchantStore.Pin.clear()
        assertNull(EnchantStore.Pin.hash)
        assertNull(EnchantStore.Pin.salt)
        assertEquals(0, EnchantStore.Pin.failedAttempts)
    }

    // -- Onboarding -----------------------------------------------------------------------

    @Test
    fun `Onboarding isComplete defaults to false`() {
        assertFalse(EnchantStore.Onboarding.isComplete)
    }

    @Test
    fun `Onboarding isComplete set and get`() {
        EnchantStore.Onboarding.setComplete(true)
        assertTrue(EnchantStore.Onboarding.isComplete)
    }

    @Test
    fun `Onboarding hasSeenWelcome defaults to false`() {
        assertFalse(EnchantStore.Onboarding.hasSeenWelcome)
    }

    @Test
    fun `Onboarding hasSeenWelcome set and get`() {
        EnchantStore.Onboarding.setHasSeenWelcome(true)
        assertTrue(EnchantStore.Onboarding.hasSeenWelcome)
    }

    @Test
    fun `Onboarding clear removes all values`() {
        EnchantStore.Onboarding.setComplete(true)
        EnchantStore.Onboarding.setHasSeenWelcome(true)
        EnchantStore.Onboarding.clear()
        assertFalse(EnchantStore.Onboarding.isComplete)
        assertFalse(EnchantStore.Onboarding.hasSeenWelcome)
    }

    // -- Proxy ----------------------------------------------------------------------------

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

    @Test
    fun `Proxy clear removes all values`() {
        EnchantStore.Proxy.setHost("proxy.example.com")
        EnchantStore.Proxy.setPort(3128)
        EnchantStore.Proxy.clear()
        assertNull(EnchantStore.Proxy.host)
        assertEquals(0, EnchantStore.Proxy.port)
    }

    // -- RateLimit ------------------------------------------------------------------------

    @Test
    fun `RateLimit lastOtpMs defaults to 0`() {
        assertEquals(0L, EnchantStore.RateLimit.lastOtpMs)
    }

    @Test
    fun `RateLimit lastOtpMs set and get`() {
        EnchantStore.RateLimit.setLastOtpMs(1234567890L)
        assertEquals(1234567890L, EnchantStore.RateLimit.lastOtpMs)
    }

    @Test
    fun `RateLimit otpAttempts defaults to 0`() {
        assertEquals(0, EnchantStore.RateLimit.otpAttempts)
    }

    @Test
    fun `RateLimit otpAttempts set and get`() {
        EnchantStore.RateLimit.setOtpAttempts(5)
        assertEquals(5, EnchantStore.RateLimit.otpAttempts)
    }

    @Test
    fun `RateLimit clear removes all values`() {
        EnchantStore.RateLimit.setLastOtpMs(123L)
        EnchantStore.RateLimit.setOtpAttempts(3)
        EnchantStore.RateLimit.clear()
        assertEquals(0L, EnchantStore.RateLimit.lastOtpMs)
        assertEquals(0, EnchantStore.RateLimit.otpAttempts)
    }

    // -- PhoneNumberPrivacy ---------------------------------------------------------------

    @Test
    fun `PhoneNumberPrivacy shareWithContacts defaults to true`() {
        assertTrue(EnchantStore.PhoneNumberPrivacy.shareWithContacts)
    }

    @Test
    fun `PhoneNumberPrivacy shareWithContacts set and get`() {
        EnchantStore.PhoneNumberPrivacy.setShareWithContacts(false)
        assertFalse(EnchantStore.PhoneNumberPrivacy.shareWithContacts)
    }

    @Test
    fun `PhoneNumberPrivacy clear removes all values`() {
        EnchantStore.PhoneNumberPrivacy.setShareWithContacts(false)
        EnchantStore.PhoneNumberPrivacy.clear()
        assertTrue(EnchantStore.PhoneNumberPrivacy.shareWithContacts)
    }

    // -- Emoji ----------------------------------------------------------------------------

    @Test
    fun `Emoji recent set and get`() {
        EnchantStore.Emoji.setRecent("😀,❤️,👍")
        assertEquals("😀,❤️,👍", EnchantStore.Emoji.recent)
    }

    @Test
    fun `Emoji clear removes all values`() {
        EnchantStore.Emoji.setRecent("😀")
        EnchantStore.Emoji.clear()
        assertNull(EnchantStore.Emoji.recent)
    }

    // -- ChatColors -----------------------------------------------------------------------

    @Test
    fun `ChatColors wallpaper set and get`() {
        EnchantStore.ChatColors.setWallpaper("default")
        assertEquals("default", EnchantStore.ChatColors.wallpaper)
    }

    @Test
    fun `ChatColors color set and get`() {
        EnchantStore.ChatColors.setColor("blue")
        assertEquals("blue", EnchantStore.ChatColors.color)
    }

    @Test
    fun `ChatColors clear removes all values`() {
        EnchantStore.ChatColors.setWallpaper("custom")
        EnchantStore.ChatColors.setColor("red")
        EnchantStore.ChatColors.clear()
        assertNull(EnchantStore.ChatColors.wallpaper)
        assertNull(EnchantStore.ChatColors.color)
    }

    // -- CallQuality ----------------------------------------------------------------------

    @Test
    fun `CallQuality useLowBandwidth defaults to false`() {
        assertFalse(EnchantStore.CallQuality.useLowBandwidth)
    }

    @Test
    fun `CallQuality useLowBandwidth set and get`() {
        EnchantStore.CallQuality.setUseLowBandwidth(true)
        assertTrue(EnchantStore.CallQuality.useLowBandwidth)
    }

    @Test
    fun `CallQuality clear removes all values`() {
        EnchantStore.CallQuality.setUseLowBandwidth(true)
        EnchantStore.CallQuality.clear()
        assertFalse(EnchantStore.CallQuality.useLowBandwidth)
    }

    // -- Labs -----------------------------------------------------------------------------

    @Test
    fun `Labs experimentalFeatures defaults to false`() {
        assertFalse(EnchantStore.Labs.experimentalFeatures)
    }

    @Test
    fun `Labs experimentalFeatures set and get`() {
        EnchantStore.Labs.setExperimentalFeatures(true)
        assertTrue(EnchantStore.Labs.experimentalFeatures)
    }

    @Test
    fun `Labs clear removes all values`() {
        EnchantStore.Labs.setExperimentalFeatures(true)
        EnchantStore.Labs.clear()
        assertFalse(EnchantStore.Labs.experimentalFeatures)
    }

    // -- Stories --------------------------------------------------------------------------

    @Test
    fun `Stories myStoriesPrivacy defaults to contacts`() {
        assertEquals("contacts", EnchantStore.Stories.myStoriesPrivacy)
    }

    @Test
    fun `Stories myStoriesPrivacy set and get`() {
        EnchantStore.Stories.setMyStoriesPrivacy("everyone")
        assertEquals("everyone", EnchantStore.Stories.myStoriesPrivacy)
    }

    @Test
    fun `Stories clear removes all values`() {
        EnchantStore.Stories.setMyStoriesPrivacy("nobody")
        EnchantStore.Stories.clear()
        assertEquals("contacts", EnchantStore.Stories.myStoriesPrivacy)
    }

    // -- Internal -------------------------------------------------------------------------

    @Test
    fun `Internal lastDeviceSyncTs defaults to 0`() {
        assertEquals(0L, EnchantStore.Internal.lastDeviceSyncTs)
    }

    @Test
    fun `Internal lastDeviceSyncTs set and get`() {
        EnchantStore.Internal.setLastDeviceSyncTs(1234567890L)
        assertEquals(1234567890L, EnchantStore.Internal.lastDeviceSyncTs)
    }

    @Test
    fun `Internal lastPreKeyRotationTs defaults to 0`() {
        assertEquals(0L, EnchantStore.Internal.lastPreKeyRotationTs)
    }

    @Test
    fun `Internal lastPreKeyRotationTs set and get`() {
        EnchantStore.Internal.setLastPreKeyRotationTs(9876543210L)
        assertEquals(9876543210L, EnchantStore.Internal.lastPreKeyRotationTs)
    }

    @Test
    fun `Internal clear removes all values`() {
        EnchantStore.Internal.setLastDeviceSyncTs(1L)
        EnchantStore.Internal.setLastPreKeyRotationTs(2L)
        EnchantStore.Internal.clear()
        assertEquals(0L, EnchantStore.Internal.lastDeviceSyncTs)
        assertEquals(0L, EnchantStore.Internal.lastPreKeyRotationTs)
    }

    // -- Global clearAll ------------------------------------------------------------------

    @Test
    fun `clearAll resets every category`() {
        EnchantStore.Account.setUserId("user-123")
        EnchantStore.Registration.setComplete(true)
        EnchantStore.Backup.setEnabled(true)
        EnchantStore.Settings.setTheme("dark")
        EnchantStore.Notifications.setMessageNotifications(false)
        EnchantStore.Privacy.setLastSeenVisibility("nobody")
        EnchantStore.Pin.setHash("hash")
        EnchantStore.Onboarding.setComplete(true)
        EnchantStore.Proxy.setHost("proxy.example.com")
        EnchantStore.RateLimit.setLastOtpMs(123L)
        EnchantStore.PhoneNumberPrivacy.setShareWithContacts(false)
        EnchantStore.Emoji.setRecent("😀")
        EnchantStore.ChatColors.setWallpaper("custom")
        EnchantStore.CallQuality.setUseLowBandwidth(true)
        EnchantStore.Labs.setExperimentalFeatures(true)
        EnchantStore.Stories.setMyStoriesPrivacy("nobody")
        EnchantStore.Internal.setLastDeviceSyncTs(1L)

        EnchantStore.clearAll()

        assertNull(EnchantStore.Account.userId)
        assertFalse(EnchantStore.Registration.isComplete)
        assertFalse(EnchantStore.Backup.isEnabled)
        assertEquals("system", EnchantStore.Settings.theme)
        assertTrue(EnchantStore.Notifications.messageNotifications)
        assertEquals("contacts", EnchantStore.Privacy.lastSeenVisibility)
        assertNull(EnchantStore.Pin.hash)
        assertFalse(EnchantStore.Onboarding.isComplete)
        assertNull(EnchantStore.Proxy.host)
        assertEquals(0L, EnchantStore.RateLimit.lastOtpMs)
        assertTrue(EnchantStore.PhoneNumberPrivacy.shareWithContacts)
        assertNull(EnchantStore.Emoji.recent)
        assertNull(EnchantStore.ChatColors.wallpaper)
        assertFalse(EnchantStore.CallQuality.useLowBandwidth)
        assertFalse(EnchantStore.Labs.experimentalFeatures)
        assertEquals("contacts", EnchantStore.Stories.myStoriesPrivacy)
        assertEquals(0L, EnchantStore.Internal.lastDeviceSyncTs)
    }
}
