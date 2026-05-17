package org.enchant.core.base

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@DisplayName("AppThemeManager")
class AppThemeManagerTest {

    @Test
    @DisplayName("Default theme is system")
    fun `default theme`() {
        assertEquals("system", AppThemeManager.currentTheme)
    }

    @Test
    @DisplayName("setTheme updates currentTheme")
    fun `set theme`() {
        AppThemeManager.setTheme("dark")
        assertEquals("dark", AppThemeManager.currentTheme)
        AppThemeManager.setTheme("light")
        assertEquals("light", AppThemeManager.currentTheme)
        AppThemeManager.setTheme("system")
        assertEquals("system", AppThemeManager.currentTheme)
    }

    @Test
    @DisplayName("setTheme stores in SecurePreferences")
    fun `set theme persists`() {
        AppThemeManager.setTheme("dark")
        assertEquals("dark", AppThemeManager.getTheme())
    }
}
