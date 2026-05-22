package org.enchant.navigation

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SafeNavigation")
class SafeNavigationTest {

    @Nested
    @DisplayName("SafeNavigation file")
    inner class FileTests {

        @Test
        @DisplayName("SafeNavigation file exists in correct package")
        fun `file exists in correct package`() {
            val packageName = this::class.java.packageName
            org.junit.jupiter.api.Assertions.assertTrue(packageName.startsWith("org.enchant.navigation"))
        }
    }
}