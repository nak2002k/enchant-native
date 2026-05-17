package org.enchant.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@DisplayName("SettingsViewModel")
class SettingsViewModelTest {
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("Initial state has defaults")
    fun `initial state`() {
        val viewModel = SettingsViewModel()
        val state = viewModel.uiState.value
        assertEquals("system", state.theme)
        assertEquals(1f, state.fontSize)
        assertTrue(state.notificationEnabled)
        assertTrue(state.messageNotifications)
    }

    @Test
    @DisplayName("UiState updates after theme change")
    fun `update theme`() = runTest {
        val viewModel = SettingsViewModel()
        viewModel.updateTheme("dark")
        assertEquals("dark", viewModel.uiState.value.theme)
    }

    @Test
    @DisplayName("UiState updates after fontSize change")  
    fun `update fontSize`() = runTest {
        val viewModel = SettingsViewModel()
        viewModel.updateFontSize(1.2f)
        assertEquals(1.2f, viewModel.uiState.value.fontSize)
    }

    @Test
    @DisplayName("AppThemeManager syncs with SettingsViewModel theme")
    fun `theme sync`() = runTest {
        val viewModel = SettingsViewModel()
        org.enchant.core.base.AppThemeManager.setTheme("dark")
        assertEquals("dark", org.enchant.core.base.AppThemeManager.currentTheme)
        viewModel.updateTheme("light")
        assertEquals("light", viewModel.uiState.value.theme)
    }
}
