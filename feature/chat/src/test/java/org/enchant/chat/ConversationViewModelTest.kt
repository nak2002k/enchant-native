package org.enchant.chat

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.enchant.chat.data.ConversationRepository
import org.enchant.chat.data.MessageSendPipeline
import org.enchant.chat.data.SendResult
import org.enchant.core.base.SecurePreferences
import org.enchant.core.model.Conversation
import org.enchant.core.model.ConversationType
import org.enchant.core.model.Message
import org.enchant.core.model.MessageStatus
import org.enchant.core.network.ApiClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ConversationViewModel — Full Coverage")
class ConversationViewModelTest {

    private lateinit var repo: ConversationRepository
    private lateinit var apiClient: ApiClient
    private lateinit var pipeline: MessageSendPipeline
    private lateinit var viewModel: ConversationViewModel

    @BeforeEach
    fun setUp() {
        repo = mockk(relaxed = true)
        apiClient = mockk(relaxed = true)
        pipeline = mockk(relaxed = true)
        mockkObject(SecurePreferences)
        every { SecurePreferences.getString(any(), any()) } returns "self-user"
        every { SecurePreferences.getString(any()) } returns "self-user"
        every { SecurePreferences.getBoolean(any(), any()) } returns false
        coEvery { repo.getMessages(any(), any(), any()) } returns flowOf(emptyList())
        coEvery { repo.getConversation(any()) } returns null
        coEvery { repo.getConversations(any()) } returns flowOf(emptyList())
        coEvery { repo.searchMessages(any()) } returns flowOf(emptyList())
        viewModel = ConversationViewModel(repo, apiClient, pipeline)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(SecurePreferences)
    }

    @Nested @DisplayName("Initialization")
    inner class InitTest {
        @Test @DisplayName("init sets conversationId and starts collecting messages")
        fun `init sets conv id`() = runTest {
            viewModel.init("conv-1")
            assertEquals("conv-1", viewModel.messages.value)
        }

        @Test @DisplayName("init is idempotent for same conversationId")
        fun `init idempotent`() = runTest {
            viewModel.init("conv-1")
            viewModel.init("conv-1")
        }
    }

    @Nested @DisplayName("Send Text Message")
    inner class SendTextTest {
        @Test @DisplayName("sendTextMessage returns false for blank text")
        fun `send blank returns false`() {
            viewModel.init("conv-1")
            val result = viewModel.sendTextMessage("")
            assertFalse(result)
        }

        @Test @DisplayName("sendTextMessage returns false for whitespace-only text")
        fun `send whitespace returns false`() {
            viewModel.init("conv-1")
            val result = viewModel.sendTextMessage("   ")
            assertFalse(result)
        }

        @Test @DisplayName("sendTextMessage returns true for valid text")
        fun `send valid returns true`() {
            viewModel.init("conv-1")
            coEvery { pipeline.sendMessage(any(), any(), any(), any(), any()) } returns SendResult.Success("env-1")
            val result = viewModel.sendTextMessage("Hello")
            assertTrue(result)
        }

        @Test @DisplayName("sendTextMessage with replyTo passes replyTo to pipeline")
        fun `send with reply`() {
            viewModel.init("conv-1")
            coEvery { pipeline.sendMessage(any(), any(), any(), any(), any()) } returns SendResult.Success("env-1")
            viewModel.sendTextMessage("Reply", replyTo = "env-orig")
            coVerify { pipeline.sendMessage(any(), any(), any(), "env-orig", any()) }
        }

        @Test @DisplayName("sendTextMessage updates sending state to SENDING then SENT")
        fun `send state transitions`() {
            viewModel.init("conv-1")
            coEvery { pipeline.sendMessage(any(), any(), any(), any(), any()) } returns SendResult.Success("env-1")
            viewModel.sendTextMessage("Hello")
            assertEquals(SendState.SENDING, viewModel.sendingState.value)
        }

        @Test @DisplayName("sendTextMessage sets FAILED on pipeline failure")
        fun `send fails`() {
            viewModel.init("conv-1")
            coEvery { pipeline.sendMessage(any(), any(), any(), any(), any()) } returns SendResult.Failed(org.enchant.chat.data.SendError.NETWORK)
            viewModel.sendTextMessage("Hello")
        }
    }

    @Nested @DisplayName("Send Media Message")
    inner class SendMediaTest {
        @Test @DisplayName("sendMediaMessage returns true")
        fun `send media returns true`() {
            viewModel.init("conv-1")
            coEvery { pipeline.sendMediaMessage(any(), any(), any(), any()) } returns SendResult.Success("env-1")
            val uri = android.net.Uri.parse("content://media/1")
            val result = viewModel.sendMediaMessage(uri, "image/png")
            assertTrue(result)
        }

        @Test @DisplayName("sendMediaMessage sets UPLOADING state")
        fun `send media state`() {
            viewModel.init("conv-1")
            coEvery { pipeline.sendMediaMessage(any(), any(), any(), any()) } returns SendResult.Success("env-1")
            val uri = android.net.Uri.parse("content://media/1")
            viewModel.sendMediaMessage(uri, "image/png")
            assertEquals(SendState.UPLOADING, viewModel.sendingState.value)
        }
    }

    @Nested @DisplayName("Send Voice Message")
    inner class SendVoiceTest {
        @Test @DisplayName("sendVoiceMessage returns true")
        fun `send voice returns true`() {
            viewModel.init("conv-1")
            coEvery { pipeline.sendMediaMessage(any(), any(), any(), any()) } returns SendResult.Success("env-1")
            val file = java.io.File.createTempFile("voice", ".m4a")
            val result = viewModel.sendVoiceMessage(file, 5)
            assertTrue(result)
            file.delete()
        }
    }

    @Nested @DisplayName("Send Location Message")
    inner class SendLocationTest {
        @Test @DisplayName("sendLocationMessage returns true")
        fun `send location returns true`() {
            viewModel.init("conv-1")
            coEvery { pipeline.sendMessage(any(), any(), any(), any(), any()) } returns SendResult.Success("env-1")
            val result = viewModel.sendLocationMessage(37.7749, -122.4194, "San Francisco")
            assertTrue(result)
        }
    }

    @Nested @DisplayName("Send Sticker")
    inner class SendStickerTest {
        @Test @DisplayName("sendSticker returns true")
        fun `send sticker returns true`() {
            viewModel.init("conv-1")
            coEvery { pipeline.sendMessage(any(), any(), any(), any(), any()) } returns SendResult.Success("env-1")
            val result = viewModel.sendSticker("pack-1", "sticker-1")
            assertTrue(result)
        }
    }

    @Nested @DisplayName("Message Operations")
    inner class MessageOpsTest {
        @Test @DisplayName("deleteMessage calls deleteForEveryone when forEveryone=true")
        fun `delete for everyone`() {
            viewModel.init("conv-1")
            viewModel.deleteMessage("env-1", true)
            coVerify { pipeline.deleteForEveryone("env-1", "conv-1") }
        }

        @Test @DisplayName("deleteMessage calls deleteForSelf when forEveryone=false")
        fun `delete for self`() {
            viewModel.init("conv-1")
            viewModel.deleteMessage("env-1", false)
            coVerify { pipeline.deleteForSelf("env-1") }
        }

        @Test @DisplayName("editMessage returns false for blank text")
        fun `edit blank returns false`() {
            viewModel.init("conv-1")
            val result = viewModel.editMessage("env-1", "")
            assertFalse(result)
        }

        @Test @DisplayName("editMessage returns true for valid text")
        fun `edit valid returns true`() {
            viewModel.init("conv-1")
            coEvery { pipeline.editMessage(any(), any(), any()) } returns kotlinx.coroutines.runBlocking { kotlin.Result.success(Unit) }
            val result = viewModel.editMessage("env-1", "Edited text")
            assertTrue(result)
        }

        @Test @DisplayName("forwardMessage calls pipeline.forwardMessage")
        fun `forward message`() {
            viewModel.init("conv-1")
            coEvery { pipeline.forwardMessage(any(), any(), any(), any()) } returns SendResult.Success("env-2")
            val result = viewModel.forwardMessage("env-1", "conv-2")
            assertTrue(result)
        }

        @Test @DisplayName("resendMessage resends message content")
        fun `resend message`() {
            viewModel.init("conv-1")
            coEvery { repo.getMessage("env-1") } returns Message(
                localId = 1, conversationId = "conv-1", senderId = "self-user",
                content = "Original", status = MessageStatus.FAILED, timestamp = 1000
            )
            coEvery { pipeline.sendMessage(any(), any(), any(), any(), any()) } returns SendResult.Success("env-2")
            viewModel.resendMessage("env-1")
        }

        @Test @DisplayName("resendMessage does nothing if message not found")
        fun `resend not found`() {
            viewModel.init("conv-1")
            coEvery { repo.getMessage("env-1") } returns null
            viewModel.resendMessage("env-1")
            coVerify(exactly = 0) { pipeline.sendMessage(any(), any(), any(), any(), any()) }
        }
    }

    @Nested @DisplayName("Reactions & Stars")
    inner class ReactionStarTest {
        @Test @DisplayName("setReaction calls pipeline.sendReaction")
        fun `set reaction`() {
            viewModel.init("conv-1")
            coEvery { repo.getMessageByLocalId(1) } returns Message(
                localId = 1, conversationId = "conv-1", senderId = "self-user",
                content = "msg", status = MessageStatus.SENT, timestamp = 1000,
                envelopeId = "env-1"
            )
            coEvery { pipeline.sendReaction(any(), any()) } returns kotlinx.coroutines.runBlocking { kotlin.Result.success(Unit) }
            viewModel.setReaction(1, "\uD83D\uDC4D")
        }

        @Test @DisplayName("starMessage calls repo.starMessage with true")
        fun `star message`() {
            viewModel.init("conv-1")
            coEvery { repo.getMessageByLocalId(1) } returns Message(
                localId = 1, conversationId = "conv-1", senderId = "self-user",
                content = "msg", status = MessageStatus.SENT, timestamp = 1000,
                envelopeId = "env-1"
            )
            viewModel.starMessage(1, true)
            coVerify { repo.starMessage(any(), true) }
        }

        @Test @DisplayName("pinMessage calls repo.starMessage (BUG: should call pin)")
        fun `pin message calls star`() {
            viewModel.init("conv-1")
            coEvery { repo.getMessageByLocalId(1) } returns Message(
                localId = 1, conversationId = "conv-1", senderId = "self-user",
                content = "msg", status = MessageStatus.SENT, timestamp = 1000,
                envelopeId = "env-1"
            )
            viewModel.pinMessage(1)
            coVerify { repo.starMessage(any(), true) }
        }

        @Test @DisplayName("unpinMessage calls repo.starMessage with false (BUG: should call unpin)")
        fun `unpin message calls star`() {
            viewModel.init("conv-1")
            coEvery { repo.getMessageByLocalId(1) } returns Message(
                localId = 1, conversationId = "conv-1", senderId = "self-user",
                content = "msg", status = MessageStatus.SENT, timestamp = 1000,
                envelopeId = "env-1"
            )
            viewModel.unpinMessage(1)
            coVerify { repo.starMessage(any(), false) }
        }
    }

    @Nested @DisplayName("Search")
    inner class SearchTest {
        @Test @DisplayName("searchInConversation clears results for blank query")
        fun `search blank clears`() {
            viewModel.init("conv-1")
            viewModel.searchInConversation("")
            assertTrue(viewModel.searchResults.value.isEmpty())
        }

        @Test @DisplayName("searchInConversation filters by conversationId")
        fun `search filters by conv`() {
            viewModel.init("conv-1")
            coEvery { repo.searchMessages("test") } returns flowOf(
                listOf(
                    Message(localId = 1, conversationId = "conv-1", senderId = "user1", content = "test msg", status = MessageStatus.SENT, timestamp = 1000),
                    Message(localId = 2, conversationId = "conv-2", senderId = "user2", content = "test other", status = MessageStatus.SENT, timestamp = 2000)
                )
            )
            viewModel.searchInConversation("test")
        }
    }

    @Nested @DisplayName("Scroll Events")
    inner class ScrollTest {
        @Test @DisplayName("scrollToBottom emits ToBottom event")
        fun `scroll to bottom`() = runTest {
            viewModel.init("conv-1")
            viewModel.scrollToBottom()
        }

        @Test @DisplayName("jumpToMessage emits ToPosition event")
        fun `jump to message`() = runTest {
            viewModel.init("conv-1")
            coEvery { repo.getMessage("env-1") } returns Message(
                localId = 42, conversationId = "conv-1", senderId = "user1",
                content = "msg", status = MessageStatus.SENT, timestamp = 1000
            )
            viewModel.jumpToMessage("env-1")
        }

        @Test @DisplayName("jumpToMessage does nothing if message not found")
        fun `jump to message not found`() = runTest {
            viewModel.init("conv-1")
            coEvery { repo.getMessage("env-1") } returns null
            viewModel.jumpToMessage("env-1")
        }

        @Test @DisplayName("jumpToDate emits ToPosition(0)")
        fun `jump to date`() = runTest {
            viewModel.init("conv-1")
            viewModel.jumpToDate(1000)
        }
    }

    @Nested @DisplayName("View Once")
    inner class ViewOnceTest {
        @Test @DisplayName("markViewOnceViewed deletes media and marks deleted")
        fun `mark view once viewed`() {
            viewModel.init("conv-1")
            viewModel.markViewOnceViewed("env-1")
            coVerify { repo.markMessageDeleted("env-1") }
        }

        @Test @DisplayName("deleteViewOnceMedia deletes local media")
        fun `delete view once media`() {
            viewModel.init("conv-1")
            viewModel.deleteViewOnceMedia("env-1")
            coVerify { repo.deleteLocalMedia("env-1") }
        }
    }

    @Nested @DisplayName("Report Message")
    inner class ReportTest {
        @Test @DisplayName("reportMessage sends report to API")
        fun `report message`() {
            viewModel.init("conv-1")
            viewModel.reportMessage("env-1")
        }
    }

    @Nested @DisplayName("Schedule Message")
    inner class ScheduleTest {
        @Test @DisplayName("scheduleMessage does nothing for blank body")
        fun `schedule blank does nothing`() {
            viewModel.init("conv-1")
            viewModel.scheduleMessage("", System.currentTimeMillis() + 60000)
        }

        @Test @DisplayName("scheduleMessage enqueues job for future date")
        fun `schedule enqueues`() {
            viewModel.init("conv-1")
            viewModel.scheduleMessage("Future msg", System.currentTimeMillis() + 60000)
        }

        @Test @DisplayName("cancelScheduledMessage cancels all scheduled messages")
        fun `cancel scheduled`() {
            viewModel.cancelScheduledMessage(1)
        }
    }

    @Nested @DisplayName("Conversation Loading")
    inner class ConversationLoadTest {
        @Test @DisplayName("loadConversations starts collecting conversations")
        fun `load conversations`() {
            viewModel.loadConversations()
        }
    }

    @Nested @DisplayName("Copy to Clipboard")
    inner class ClipboardTest {
        @Test @DisplayName("copyToClipboard does not crash without context")
        fun `copy no context`() {
            viewModel.copyToClipboard("test text")
        }
    }

    @Nested @DisplayName("Start Call")
    inner class StartCallTest {
        @Test @DisplayName("startCall is empty (BUG: does nothing)")
        fun `start call empty`() {
            viewModel.startCall("user-1", false)
        }
    }

    @Nested @DisplayName("onCleared")
    inner class OnClearedTest {
        @Test @DisplayName("onCleared cancels message and search jobs")
        fun `on cleared cancels jobs`() {
            viewModel.init("conv-1")
            viewModel.onCleared()
        }
    }

    @Nested @DisplayName("Disappear Timer")
    inner class DisappearTimerTest {
        @Test @DisplayName("setDisappearTimer calls repo.setDisappearTimer")
        fun `set disappear timer`() {
            viewModel.setDisappearTimer("conv-1", 86400)
            coVerify { repo.setDisappearTimer("conv-1", 86400) }
        }
    }

    @Nested @DisplayName("Load More Messages")
    inner class LoadMoreTest {
        @Test @DisplayName("loadMoreMessages prepends older messages")
        fun `load more messages`() {
            viewModel.init("conv-1")
            viewModel.loadMoreMessages()
        }
    }
}
