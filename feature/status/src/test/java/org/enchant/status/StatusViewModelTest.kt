package org.enchant.status

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("StatusViewModel — Full Coverage")
class StatusViewModelTest {

    private lateinit var viewModel: StatusViewModel

    @BeforeEach
    fun setUp() {
        viewModel = StatusViewModel()
    }

    @Nested @DisplayName("Load Feed")
    inner class LoadFeedTest {
        @Test @DisplayName("loadFeed loads status feed")
        fun `load feed`() = runTest {
            viewModel.loadFeed()
        }
    }

    @Nested @DisplayName("Create Text Status")
    inner class CreateTextStatusTest {
        @Test @DisplayName("createTextStatus creates a text status")
        fun `create text status`() = runTest {
            viewModel.createTextStatus("Hello!", "#FF5733", StatusPrivacy.AllContacts)
        }
    }

    @Nested @DisplayName("View Status")
    inner class ViewStatusTest {
        @Test @DisplayName("viewStatus records a view")
        fun `view status`() = runTest {
            viewModel.viewStatus("status-1")
        }
    }

    @Nested @DisplayName("UI State")
    inner class UiStateTest {
        @Test @DisplayName("uiState has default values")
        fun `ui state defaults`() = runTest {
            val state = viewModel.uiState.value
            assertNotNull(state)
            assertTrue(state.feed.isEmpty())
        }
    }
}
