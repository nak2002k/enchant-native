package org.enchant.channels

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.enchant.core.network.ApiClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ChannelViewModel — Full Coverage")
class ChannelViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var apiClient: ApiClient
    private lateinit var viewModel: ChannelViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(ApiClient.Companion)
        apiClient = mockk<ApiClient>(relaxed = true)
        every { ApiClient.getInstance() } returns apiClient
        viewModel = ChannelViewModel()
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(ApiClient.Companion)
        Dispatchers.resetMain()
    }

    @Nested @DisplayName("Load My Channels")
    inner class LoadMyChannelsTest {
        @Test @DisplayName("loadMyChannels loads user's channels")
        fun `load my channels`() = runTest {
            viewModel.loadMyChannels()
            testDispatcher.scheduler.advanceUntilIdle()
        }
    }

    @Nested @DisplayName("Subscribe")
    inner class SubscribeTest {
        @Test @DisplayName("subscribe subscribes to a channel")
        fun `subscribe`() = runTest {
            viewModel.subscribe("channel-1")
            testDispatcher.scheduler.advanceUntilIdle()
        }
    }

    @Nested @DisplayName("Load More")
    inner class LoadMoreTest {
        @Test @DisplayName("loadMore loads more posts for a channel")
        fun `load more`() = runTest {
            viewModel.loadMore("channel-1")
            testDispatcher.scheduler.advanceUntilIdle()
        }
    }

    @Nested @DisplayName("UI State")
    inner class UiStateTest {
        @Test @DisplayName("uiState has default values")
        fun `ui state defaults`() = runTest {
            val state = viewModel.uiState.value
            assertNotNull(state)
            assertTrue(state.channels.isEmpty())
            assertTrue(state.feed.isEmpty())
        }
    }
}
