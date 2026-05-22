package org.enchant.registration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("RegistrationNavKey")
class RegistrationNavKeyTest {

    @Nested
    @DisplayName("Welcome")
    inner class Welcome {
        @Test
        fun `is data object`() {
            val key1 = RegistrationNavKey.Welcome
            val key2 = RegistrationNavKey.Welcome
            assertEquals(key1, key2)
        }
    }

    @Nested
    @DisplayName("Permissions")
    inner class Permissions {
        @Test
        fun `contains nextRoute`() {
            val key = RegistrationNavKey.Permissions(nextRoute = RegistrationNavKey.PhoneNumberEntry)
            assertEquals(RegistrationNavKey.PhoneNumberEntry, key.nextRoute)
        }

        @Test
        fun `equals based on nextRoute`() {
            val key1 = RegistrationNavKey.Permissions(nextRoute = RegistrationNavKey.PhoneNumberEntry)
            val key2 = RegistrationNavKey.Permissions(nextRoute = RegistrationNavKey.PhoneNumberEntry)
            val key3 = RegistrationNavKey.Permissions(nextRoute = RegistrationNavKey.Welcome)
            assertEquals(key1, key2)
            assertFalse(key1 == key3)
        }
    }

    @Nested
    @DisplayName("CountryCodePicker")
    inner class CountryCodePicker {
        @Test
        fun `defaults to null country`() {
            val key = RegistrationNavKey.CountryCodePicker()
            assertEquals(null, key.country)
        }

        @Test
        fun `contains country when provided`() {
            val country = CountryData("US", "United States", 1)
            val key = RegistrationNavKey.CountryCodePicker(country = country)
            assertEquals(country, key.country)
        }
    }

    @Nested
    @DisplayName("ArchiveRestoreSelection")
    inner class ArchiveRestoreSelection {
        @Test
        fun `forQuickRestore creates correct selection`() {
            val key = RegistrationNavKey.ArchiveRestoreSelection.forQuickRestore(hasRemoteBackup = true)
            assertTrue(key.restoreOptions.contains(ArchiveRestoreOption.SignalSecureBackup))
            assertTrue(key.restoreOptions.contains(ArchiveRestoreOption.DeviceTransfer))
            assertFalse(key.restoreOptions.contains(ArchiveRestoreOption.None))
            assertFalse(key.isPreRegistration)
        }

        @Test
        fun `forQuickRestore without remote backup excludes SignalSecureBackup`() {
            val key = RegistrationNavKey.ArchiveRestoreSelection.forQuickRestore(hasRemoteBackup = false)
            assertTrue(key.restoreOptions.contains(ArchiveRestoreOption.DeviceTransfer))
            assertTrue(key.restoreOptions.contains(ArchiveRestoreOption.LocalBackup))
            assertFalse(key.restoreOptions.contains(ArchiveRestoreOption.SignalSecureBackup))
        }

        @Test
        fun `forManualRestore includes local backup and signal backup`() {
            val key = RegistrationNavKey.ArchiveRestoreSelection.forManualRestore()
            assertTrue(key.restoreOptions.contains(ArchiveRestoreOption.LocalBackup))
            assertTrue(key.restoreOptions.contains(ArchiveRestoreOption.SignalSecureBackup))
            assertTrue(key.isPreRegistration)
        }

        @Test
        fun `forPostRegister includes all options`() {
            val key = RegistrationNavKey.ArchiveRestoreSelection.forPostRegister()
            assertEquals(ArchiveRestoreOption.entries.toSet(), key.restoreOptions.toSet())
            assertFalse(key.isPreRegistration)
        }
    }

    @Nested
    @DisplayName("Captcha")
    inner class Captcha {
        @Test
        fun `contains session metadata`() {
            val session = SessionMetadata(sessionId = "captcha-session")
            val key = RegistrationNavKey.Captcha(session = session)
            assertEquals(session, key.session)
        }
    }

    @Nested
    @DisplayName("PinEntryForRegistrationLock")
    inner class PinEntryForRegistrationLock {
        @Test
        fun `contains time remaining and credentials`() {
            val credentials = SvrCredentials("user", "pass")
            val key = RegistrationNavKey.PinEntryForRegistrationLock(timeRemaining = 5000L, svrCredentials = credentials)
            assertEquals(5000L, key.timeRemaining)
            assertEquals(credentials, key.svrCredentials)
        }
    }

    @Nested
    @DisplayName("RemoteRestore")
    inner class RemoteRestore {
        @Test
        fun `contains aep as String`() {
            val key = RegistrationNavKey.RemoteRestore(aep = "test-aep-value")
            assertEquals("test-aep-value", key.aep)
        }
    }

    @Nested
    @DisplayName("LocalBackupRestore")
    inner class LocalBackupRestore {
        @Test
        fun `contains isPreRegistration flag`() {
            val key1 = RegistrationNavKey.LocalBackupRestore(isPreRegistration = true)
            val key2 = RegistrationNavKey.LocalBackupRestore(isPreRegistration = false)
            assertTrue(key1.isPreRegistration)
            assertFalse(key2.isPreRegistration)
        }
    }

    @Nested
    @DisplayName("EnterAepForRemoteBackupPreRegistration")
    inner class EnterAepForRemoteBackupPreRegistration {
        @Test
        fun `contains e164`() {
            val key = RegistrationNavKey.EnterAepForRemoteBackupPreRegistration(e164 = "+1234567890")
            assertEquals("+1234567890", key.e164)
        }
    }
}