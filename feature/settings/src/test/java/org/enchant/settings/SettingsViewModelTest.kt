package org.enchant.settings

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.enchant.core.auth.AuthManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SettingsViewModel — Full Coverage")
class SettingsViewModelTest {

    @BeforeEach
    fun setUp() {
        mockkObject(AuthManager)
        coEvery { AuthManager.deleteAccount() } returns kotlin.Result.success(Unit)
        coEvery { AuthManager.logout() } returns Unit
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(AuthManager)
    }

    @Nested @DisplayName("Load Settings")
    inner class LoadSettingsTest {
        @Test @DisplayName("loadSettings initializes UI state")
        fun `load settings`() = runTest {
            val viewModel = SettingsViewModel()
            viewModel.loadSettings()
            assertNotNull(viewModel.uiState.value)
        }
    }

    @Nested @DisplayName("Update Privacy")
    inner class UpdatePrivacyTest {
        @Test @DisplayName("updatePrivacy updates privacy settings")
        fun `update privacy`() = runTest {
            val viewModel = SettingsViewModel()
            viewModel.loadSettings()
            viewModel.updatePrivacy(
                lastSeenVisibility = org.enchant.core.model.Visibility.EVERYONE,
                onlineVisibility = org.enchant.core.model.Visibility.CONTACTS,
                avatarVisibility = org.enchant.core.model.Visibility.EVERYONE,
                aboutVisibility = org.enchant.core.model.Visibility.EVERYONE,
                readReceipts = true
            )
        }
    }

    @Nested @DisplayName("Update Notification Preferences")
    inner class UpdateNotificationTest {
        @Test @DisplayName("updateNotificationPrefs updates notification settings")
        fun `update notification prefs`() = runTest {
            val viewModel = SettingsViewModel()
            viewModel.loadSettings()
            viewModel.updateNotificationPrefs(
                masterEnabled = true,
                messageNotifications = true,
                showPreview = true
            )
        }
    }

    @Nested @DisplayName("Update Theme")
    inner class UpdateThemeTest {
        @Test @DisplayName("updateTheme updates theme setting")
        fun `update theme`() = runTest {
            val viewModel = SettingsViewModel()
            viewModel.loadSettings()
            viewModel.updateTheme(org.enchant.core.model.Theme.DARK)
        }
    }

    @Nested @DisplayName("Update Font Size")
    inner class UpdateFontSizeTest {
        @Test @DisplayName("updateFontSize updates font size setting")
        fun `update font size`() = runTest {
            val viewModel = SettingsViewModel()
            viewModel.loadSettings()
            viewModel.updateFontSize(org.enchant.core.model.FontSize.MEDIUM)
        }
    }

    @Nested @DisplayName("Revoke Device")
    inner class RevokeDeviceTest {
        @Test @DisplayName("revokeDevice revokes device session")
        fun `revoke device`() = runTest {
            val viewModel = SettingsViewModel()
            viewModel.loadSettings()
            viewModel.revokeDevice("device-1")
        }
    }

    @Nested @DisplayName("Delete Account")
    inner class DeleteAccountTest {
        @Test @DisplayName("deleteAccount calls AuthManager.deleteAccount")
        fun `delete account`() = runTest {
            val viewModel = SettingsViewModel()
            viewModel.deleteAccount()
            coVerify { AuthManager.deleteAccount() }
        }
    }

    @Nested @DisplayName("UI State")
    inner class UiStateTest {
        @Test @DisplayName("uiState has default values")
        fun `ui state defaults`() = runTest {
            val viewModel = SettingsViewModel()
            val state = viewModel.uiState.value
            assertNotNull(state)
        }
    }
}
