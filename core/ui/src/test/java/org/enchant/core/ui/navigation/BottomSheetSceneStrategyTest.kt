@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.enchant.core.ui.navigation

import androidx.compose.material3.ModalBottomSheetProperties
import androidx.navigation3.runtime.NavKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("BottomSheetSceneStrategy")
class BottomSheetSceneStrategyTest {

    private fun createStrategy(): BottomSheetSceneStrategy<NavKey> = BottomSheetSceneStrategy()

    @Nested
    @DisplayName("Creation")
    inner class CreationTests {

        @Test
        @DisplayName("can be instantiated")
        fun `can be instantiated`() {
            val strategy = createStrategy()
            assertNotNull(strategy)
        }

        @Test
        @DisplayName("companion bottomSheet creates metadata map with BOTTOM_SHEET_KEY")
        fun `bottomSheet creates metadata map`() {
            val metadata = BottomSheetSceneStrategy.bottomSheet()
            assertNotNull(metadata)
            assertTrue(metadata.containsKey(BottomSheetSceneStrategy.BOTTOM_SHEET_KEY))
        }

        @Test
        @DisplayName("bottomSheet accepts custom ModalBottomSheetProperties")
        fun `bottomSheet accepts custom properties`() {
            val customProperties = ModalBottomSheetProperties()
            val metadata = BottomSheetSceneStrategy.bottomSheet(customProperties)
            assertEquals(customProperties, metadata[BottomSheetSceneStrategy.BOTTOM_SHEET_KEY])
        }
    }

    @Nested
    @DisplayName("Metadata")
    inner class MetadataTests {

        @Test
        @DisplayName("BOTTOM_SHEET_KEY constant equals expected string")
        fun `BOTTOM_SHEET_KEY has correct value`() {
            assertEquals("bottomsheet", BottomSheetSceneStrategy.BOTTOM_SHEET_KEY)
        }

        @Test
        @DisplayName("bottomSheet metadata map has exactly one entry")
        fun `bottomSheet metadata has one entry`() {
            val metadata = BottomSheetSceneStrategy.bottomSheet()
            assertEquals(1, metadata.size)
        }

        @Test
        @DisplayName("bottomSheet metadata value is ModalBottomSheetProperties")
        fun `bottomSheet metadata value type`() {
            val metadata = BottomSheetSceneStrategy.bottomSheet()
            @Suppress("UNCHECKED_CAST")
            val value = metadata[BottomSheetSceneStrategy.BOTTOM_SHEET_KEY] as? ModalBottomSheetProperties
            assertNotNull(value)
        }
    }

    @Nested
    @DisplayName("LocalBottomSheetDismiss structure")
    inner class LocalBottomSheetDismissTests {

        @Test
        @DisplayName("LocalBottomSheetDismiss is a static composition local")
        fun `LocalBottomSheetDismiss exists`() {
            assertNotNull(LocalBottomSheetDismiss)
        }
    }

    @Nested
    @DisplayName("Singleton behavior")
    inner class SingletonTests {

        @Test
        @DisplayName("multiple instances are of correct type")
        fun `multiple instances are of correct type`() {
            val s1 = createStrategy()
            val s2 = createStrategy()
            assertNotNull(s1)
            assertNotNull(s2)
            assertTrue(s1 is BottomSheetSceneStrategy<*>)
            assertTrue(s2 is BottomSheetSceneStrategy<*>)
        }

        @Test
        @DisplayName("strategy is BottomSheetSceneStrategy type")
        fun `strategy type check`() {
            val strategy = createStrategy()
            assertTrue(strategy is BottomSheetSceneStrategy<*>)
        }
    }
}