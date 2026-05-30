package org.enchant.settings

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("SettingsViewModel — Full Coverage")
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var apiClient: org.enchant.core.network.ApiClient

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        apiClient = mockk(relaxed = true)
        coEvery { apiClient.get(any()) } returns Result.success(buildJsonObject { })
        coEvery { apiClient.put(any(), any()) } returns Result.success(buildJsonObject { })
        coEvery { apiClient.del(any()) } returns Result.success(buildJsonObject { })
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SettingsViewModel(apiClient)

    @Nested @DisplayName("Load Settings")
    inner class LoadSettingsTest {
        @Test @DisplayName("loadSettings initializes UI state")
        fun `load settings`() = runTest {
            val viewModel = createViewModel()
            viewModel.loadSettings()
            assertNotNull(viewModel.uiState.value)
        }

        @Test @DisplayName("loadSettings sets error on failure")
        fun `load settings failure`() = runTest {
            coEvery { apiClient.get("/v1/settings") } returns Result.failure(Exception("Network error"))
            val viewModel = createViewModel()
            viewModel.loadSettings()
            assertEquals("Network error", viewModel.uiState.value.error)
        }

        @Test @DisplayName("loadSettings parses theme from response")
        fun `load settings parses theme`() = runTest {
            coEvery { apiClient.get("/v1/settings") } returns Result.success(buildJsonObject {
                put("theme", JsonPrimitive("dark"))
            })
            val viewModel = createViewModel()
            viewModel.loadSettings()
            assertEquals("dark", viewModel.uiState.value.theme)
        }
    }

    @Nested @DisplayName("Update Privacy")
    inner class UpdatePrivacyTest {
        @Test @DisplayName("updatePrivacy sends correct payload")
        fun `update privacy`() = runTest {
            val viewModel = createViewModel()
            viewModel.updatePrivacy(
                lastSeen = "everyone",
                online = true,
                avatar = "everyone",
                about = "everyone",
                readReceipts = true
            )
            coVerify { apiClient.put("/v1/settings/privacy", any()) }
        }

        @Test @DisplayName("updatePrivacy sets error on failure")
        fun `update privacy failure`() = runTest {
            coEvery { apiClient.put("/v1/settings/privacy", any()) } returns Result.failure(Exception("Server error"))
            val viewModel = createViewModel()
            viewModel.updatePrivacy("everyone", true, "everyone", "everyone", true)
            assertEquals("Server error", viewModel.uiState.value.error)
        }
    }

    @Nested @DisplayName("Update Notification Preferences")
    inner class UpdateNotificationTest {
        @Test @DisplayName("updateNotificationPrefs sends correct payload")
        fun `update notification prefs`() = runTest {
            val viewModel = createViewModel()
            viewModel.updateNotificationPrefs(
                notificationEnabled = true,
                messageNotifications = true,
                showPreview = true
            )
            coVerify { apiClient.put("/v1/settings/notifications", any()) }
            assertTrue(viewModel.uiState.value.notificationEnabled)
            assertTrue(viewModel.uiState.value.messageNotifications)
            assertTrue(viewModel.uiState.value.showPreview)
        }

        @Test @DisplayName("updateNotificationPrefs sets error on failure")
        fun `update notification prefs failure`() = runTest {
            coEvery { apiClient.put("/v1/settings/notifications", any()) } returns Result.failure(Exception("Server error"))
            val viewModel = createViewModel()
            viewModel.updateNotificationPrefs(true, true, true)
            assertEquals("Server error", viewModel.uiState.value.error)
        }
    }

    @Nested @DisplayName("Update Theme")
    inner class UpdateThemeTest {
        @Test @DisplayName("updateTheme updates theme setting")
        fun `update theme`() = runTest {
            val viewModel = createViewModel()
            viewModel.updateTheme("dark")
            assertEquals("dark", viewModel.uiState.value.theme)
            coVerify { apiClient.put("/v1/settings/theme", any()) }
        }

        @Test @DisplayName("updateTheme sets error on failure")
        fun `update theme failure`() = runTest {
            coEvery { apiClient.put("/v1/settings/theme", any()) } returns Result.failure(Exception("Server error"))
            val viewModel = createViewModel()
            viewModel.updateTheme("dark")
            assertEquals("dark", viewModel.uiState.value.theme)
            assertEquals("Server error", viewModel.uiState.value.error)
        }
    }

    @Nested @DisplayName("Update Font Size")
    inner class UpdateFontSizeTest {
        @Test @DisplayName("updateFontSize updates font size setting")
        fun `update font size`() = runTest {
            val viewModel = createViewModel()
            viewModel.updateFontSize(1.5f)
            assertEquals(1.5f, viewModel.uiState.value.fontSize)
            coVerify { apiClient.put("/v1/settings/font-size", any()) }
        }

        @Test @DisplayName("updateFontSize sets error on failure")
        fun `update font size failure`() = runTest {
            coEvery { apiClient.put("/v1/settings/font-size", any()) } returns Result.failure(Exception("Server error"))
            val viewModel = createViewModel()
            viewModel.updateFontSize(1.5f)
            assertEquals("Server error", viewModel.uiState.value.error)
        }
    }

    @Nested @DisplayName("Revoke Device")
    inner class RevokeDeviceTest {
        @Test @DisplayName("revokeDevice removes device from state immediately")
        fun `revoke device`() = runTest {
            coEvery { apiClient.get("/v1/devices") } returns Result.success(buildJsonObject {
                put("devices", kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject {
                        put("device_id", JsonPrimitive("device-1"))
                        put("name", JsonPrimitive("Phone"))
                        put("is_current", JsonPrimitive(false))
                    })
                })
            })
            val viewModel = createViewModel()
            viewModel.loadDevices()
            assertEquals(1, viewModel.uiState.value.devices.size)

            viewModel.revokeDevice("device-1")
            assertEquals(0, viewModel.uiState.value.devices.size)
            coVerify { apiClient.del("/v1/devices/device-1") }
        }

        @Test @DisplayName("revokeDevice sets error on failure")
        fun `revoke device failure`() = runTest {
            coEvery { apiClient.del("/v1/devices/device-1") } returns Result.failure(Exception("Server error"))
            val viewModel = createViewModel()
            viewModel.revokeDevice("device-1")
            assertEquals("Server error", viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isProcessing)
        }
    }

    @Nested @DisplayName("Delete Account")
    inner class DeleteAccountTest {
        @Test @DisplayName("deleteAccount sends delete request")
        fun `delete account`() = runTest {
            val viewModel = createViewModel()
            viewModel.deleteAccount()
            coVerify { apiClient.del("/v1/account") }
            assertEquals("Account deleted", viewModel.uiState.value.successMessage)
        }

        @Test @DisplayName("deleteAccount is guarded against re-entrancy")
        fun `delete account guarded`() = runTest {
            val viewModel = createViewModel()
            viewModel.deleteAccount()
            viewModel.deleteAccount()
            coVerify(exactly = 1) { apiClient.del("/v1/account") }
        }

        @Test @DisplayName("deleteAccount sets error on failure")
        fun `delete account failure`() = runTest {
            coEvery { apiClient.del("/v1/account") } returns Result.failure(Exception("Server error"))
            val viewModel = createViewModel()
            viewModel.deleteAccount()
            assertEquals("Server error", viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isProcessing)
        }
    }

    @Nested @DisplayName("Clear Cache")
    inner class ClearCacheTest {
        @Test @DisplayName("clearCache sends delete request")
        fun `clear cache`() = runTest {
            val viewModel = createViewModel()
            viewModel.clearCache()
            coVerify { apiClient.del("/v1/settings/cache") }
            assertEquals("Cache cleared", viewModel.uiState.value.successMessage)
        }

        @Test @DisplayName("clearCache sets error on failure")
        fun `clear cache failure`() = runTest {
            coEvery { apiClient.del("/v1/settings/cache") } returns Result.failure(Exception("Server error"))
            val viewModel = createViewModel()
            viewModel.clearCache()
            assertEquals("Server error", viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isProcessing)
        }
    }

    @Nested @DisplayName("UI State")
    inner class UiStateTest {
        @Test @DisplayName("uiState has default values")
        fun `ui state defaults`() = runTest {
            val viewModel = createViewModel()
            val state = viewModel.uiState.value
            assertNotNull(state)
            assertEquals("system", state.theme)
            assertEquals(1f, state.fontSize)
            assertTrue(state.notificationEnabled)
            assertTrue(state.messageNotifications)
            assertTrue(state.showPreview)
            assertEquals("contacts", state.lastSeenVisibility)
            assertTrue(state.onlineVisibility)
            assertEquals("contacts", state.avatarVisibility)
            assertEquals("contacts", state.aboutVisibility)
            assertTrue(state.readReceipts)
            assertFalse(state.isProcessing)
            assertNull(state.error)
            assertNull(state.successMessage)
        }
    }
}
