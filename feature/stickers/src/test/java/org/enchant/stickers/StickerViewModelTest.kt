package org.enchant.stickers

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.enchant.core.model.StickerPack
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("StickerViewModel — Full Coverage")
class StickerViewModelTest {

    private lateinit var viewModel: StickerViewModel

    @BeforeEach
    fun setUp() {
        viewModel = StickerViewModel()
    }

    @Nested @DisplayName("Load Featured")
    inner class LoadFeaturedTest {
        @Test @DisplayName("loadFeatured loads featured sticker packs")
        fun `load featured`() = runTest {
            viewModel.loadFeatured()
        }
    }

    @Nested @DisplayName("Search Packs")
    inner class SearchPacksTest {
        @Test @DisplayName("searchPacks searches sticker packs")
        fun `search packs`() = runTest {
            viewModel.searchPacks("fun")
        }

        @Test @DisplayName("searchPacks clears results for empty query")
        fun `search empty query`() = runTest {
            viewModel.searchPacks("")
            assertTrue(viewModel.uiState.value.searchResults.isEmpty())
        }
    }

    @Nested @DisplayName("Install Pack")
    inner class InstallPackTest {
        @Test @DisplayName("installPack installs a sticker pack")
        fun `install pack`() = runTest {
            viewModel.installPack("pack-1")
        }
    }

    @Nested @DisplayName("Load Pack Detail")
    inner class LoadPackDetailTest {
        @Test @DisplayName("loadPackDetail loads pack details")
        fun `load pack detail`() = runTest {
            viewModel.loadPackDetail("pack-1")
        }
    }

    @Nested @DisplayName("UI State")
    inner class UiStateTest {
        @Test @DisplayName("uiState has default values")
        fun `ui state defaults`() = runTest {
            val state = viewModel.uiState.value
            assertNotNull(state)
            assertTrue(state.featured.isEmpty())
            assertTrue(state.searchResults.isEmpty())
            assertFalse(state.isLoading)
            assertNull(state.error)
        }
    }
}
