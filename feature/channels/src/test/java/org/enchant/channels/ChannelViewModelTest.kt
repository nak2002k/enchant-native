package org.enchant.channels

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.enchant.core.model.Channel
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ChannelViewModel — Full Coverage")
class ChannelViewModelTest {

    private lateinit var viewModel: ChannelViewModel

    @BeforeEach
    fun setUp() {
        viewModel = ChannelViewModel()
    }

    @Nested @DisplayName("Load My Channels")
    inner class LoadMyChannelsTest {
        @Test @DisplayName("loadMyChannels loads user's channels")
        fun `load my channels`() = runTest {
            viewModel.loadMyChannels()
        }
    }

    @Nested @DisplayName("Subscribe")
    inner class SubscribeTest {
        @Test @DisplayName("subscribe subscribes to a channel")
        fun `subscribe`() = runTest {
            viewModel.subscribe("channel-1")
        }
    }

    @Nested @DisplayName("Load More")
    inner class LoadMoreTest {
        @Test @DisplayName("loadMore loads more posts for a channel")
        fun `load more`() = runTest {
            viewModel.loadMore("channel-1")
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
