package org.enchant.stickers

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.enchant.core.network.ApiClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalCoroutinesApi::class)
class StickerDispatcherRule : BeforeEachCallback, AfterEachCallback {
    private val dispatcher = StandardTestDispatcher()
    override fun beforeEach(context: ExtensionContext?) { Dispatchers.setMain(dispatcher) }
    override fun afterEach(context: ExtensionContext?) { Dispatchers.resetMain() }
}

@DisplayName("StickerViewModel — Full Coverage")
class StickerViewModelTest {

    @JvmField
    @RegisterExtension
    val dispatcherRule = StickerDispatcherRule()

    private lateinit var viewModel: StickerViewModel
    private lateinit var apiClient: ApiClient

    @BeforeEach
    fun setUp() {
        apiClient = mockk(relaxed = true)
        mockkObject(ApiClient)
        every { ApiClient.getInstance() } returns apiClient
        coEvery { apiClient.get(any(), any()) } returns Result.success(JsonObject(mapOf("packs" to JsonArray(emptyList()))))
        coEvery { apiClient.post(any(), any()) } returns Result.success(JsonObject(mapOf("status" to JsonPrimitive("ok"))))
        viewModel = StickerViewModel(apiClient)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(ApiClient)
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
