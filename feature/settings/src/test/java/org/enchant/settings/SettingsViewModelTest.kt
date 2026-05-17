package org.enchant.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SettingsViewModel")
class SettingsViewModelTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested @DisplayName("initial state")
    inner class InitialStateTest {
        @Test @DisplayName("starts with default values")
        fun `default state`() {
            val vm = SettingsViewModel()
            val state = vm.uiState.value
            assertEquals("system", state.theme)
            assertEquals(1f, state.fontSize)
            assertTrue(state.notificationEnabled)
            assertTrue(state.messageNotifications)
            assertTrue(state.showPreview)
            assertTrue(state.readReceipts)
        }

        @Test @DisplayName("starts not processing")
        fun `not processing`() {
            val vm = SettingsViewModel()
            assertFalse(vm.uiState.value.isProcessing)
        }
    }

    @Nested @DisplayName("font size")
    inner class FontSizeTest {
        @Test @DisplayName("updateFontSize updates state")
        fun `update font`() {
            val vm = SettingsViewModel()
            vm.updateFontSize(1.2f)
            assertEquals(1.2f, vm.uiState.value.fontSize, 0.01f)
        }
    }

    @Nested @DisplayName("clear messages")
    inner class ClearMessagesTest {
        @Test @DisplayName("clearMessages resets error and success")
        fun `clear messages`() {
            val vm = SettingsViewModel()
            vm.clearMessages()
            assertNull(vm.uiState.value.error)
            assertNull(vm.uiState.value.successMessage)
        }
    }
}