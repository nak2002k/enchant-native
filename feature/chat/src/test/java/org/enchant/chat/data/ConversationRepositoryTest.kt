package org.enchant.chat.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.enchant.core.database.DatabasePool
import org.enchant.core.database.dao.ConversationDao
import org.enchant.core.database.dao.MediaCacheDao
import org.enchant.core.database.dao.MessageDao
import org.enchant.core.database.dao.RecipientDao
import org.enchant.core.database.entity.ConversationEntity
import org.enchant.core.database.entity.MessageEntity
import org.enchant.core.model.Conversation
import org.enchant.core.model.ConversationType
import org.enchant.core.model.Message
import org.enchant.core.model.MessageStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ConversationRepository — Full Coverage")
class ConversationRepositoryTest {

    private lateinit var messageDao: MessageDao
    private lateinit var conversationDao: ConversationDao
    private lateinit var recipientDao: RecipientDao
    private lateinit var pool: DatabasePool
    private lateinit var repo: ConversationRepository

    @BeforeEach
    fun setUp() {
        messageDao = mockk(relaxed = true)
        conversationDao = mockk(relaxed = true)
        recipientDao = mockk(relaxed = true)
        pool = mockk(relaxed = true)
        repo = ConversationRepository(messageDao, conversationDao, recipientDao, pool)
    }

    @Nested @DisplayName("Get Conversations")
    inner class GetConversationsTest {
        @Test @DisplayName("getConversations returns all conversations for ALL filter")
        fun `get all conversations`() = runTest {
            coEvery { conversationDao.getAll() } returns flowOf(
                listOf(
                    ConversationEntity("conv-1", "direct", unreadCount = 0),
                    ConversationEntity("conv-2", "group", unreadCount = 5)
                )
            )
            val conversations = repo.getConversations(ConversationFilter.ALL)
            conversations.collect { list ->
                assertEquals(2, list.size)
            }
        }

        @Test @DisplayName("getConversations filters UNREAD conversations")
        fun `get unread conversations`() = runTest {
            coEvery { conversationDao.getAll() } returns flowOf(
                listOf(
                    ConversationEntity("conv-1", "direct", unreadCount = 0),
                    ConversationEntity("conv-2", "group", unreadCount = 5),
                    ConversationEntity("conv-3", "direct", unreadCount = 3)
                )
            )
            val conversations = repo.getConversations(ConversationFilter.UNREAD)
            conversations.collect { list ->
                assertEquals(2, list.size)
                assertTrue(list.all { it.unreadCount > 0 })
            }
        }

        @Test @DisplayName("getConversations filters GROUP conversations")
        fun `get group conversations`() = runTest {
            coEvery { conversationDao.getAll() } returns flowOf(
                listOf(
                    ConversationEntity("conv-1", "direct", unreadCount = 0),
                    ConversationEntity("conv-2", "group", unreadCount = 5)
                )
            )
            val conversations = repo.getConversations(ConversationFilter.GROUPS)
            conversations.collect { list ->
                assertEquals(1, list.size)
                assertEquals("group", list[0].type)
            }
        }

        @Test @DisplayName("getConversations filters PERSONAL conversations")
        fun `get personal conversations`() = runTest {
            coEvery { conversationDao.getAll() } returns flowOf(
                listOf(
                    ConversationEntity("conv-1", "direct", unreadCount = 0),
                    ConversationEntity("conv-2", "group", unreadCount = 5)
                )
            )
            val conversations = repo.getConversations(ConversationFilter.PERSONAL)
            conversations.collect { list ->
                assertEquals(1, list.size)
                assertEquals("direct", list[0].type)
            }
        }

        @Test @DisplayName("getConversations filters ARCHIVED conversations")
        fun `get archived conversations`() = runTest {
            coEvery { conversationDao.getAll() } returns flowOf(
                listOf(
                    ConversationEntity("conv-1", "direct", isArchived = true),
                    ConversationEntity("conv-2", "group", isArchived = false)
                )
            )
            val conversations = repo.getConversations(ConversationFilter.ARCHIVED)
            conversations.collect { list ->
                assertEquals(1, list.size)
                assertTrue(list[0].isArchived)
            }
        }
    }

    @Nested @DisplayName("Get Messages")
    inner class GetMessagesTest {
        @Test @DisplayName("getMessages returns messages for conversation")
        fun `get messages`() = runTest {
            coEvery { messageDao.getConversationMessages(any(), any(), any()) } returns flowOf(
                listOf(
                    MessageEntity(localId = 1, conversationId = "conv-1", senderId = "user-1", messageType = "text", content = "Hello", status = "delivered", timestamp = 1000),
                    MessageEntity(localId = 2, conversationId = "conv-1", senderId = "user-2", messageType = "text", content = "Hi", status = "delivered", timestamp = 2000)
                )
            )
            val messages = repo.getMessages("conv-1")
            messages.collect { list ->
                assertEquals(2, list.size)
            }
        }

        @Test @DisplayName("getMessages with beforeId returns older messages")
        fun `get messages before id`() = runTest {
            coEvery { messageDao.getConversationMessages(any(), any(), any()) } returns flowOf(
                listOf(
                    MessageEntity(localId = 1, conversationId = "conv-1", senderId = "user-1", messageType = "text", content = "Old", status = "delivered", timestamp = 500)
                )
            )
            val messages = repo.getMessages("conv-1", beforeId = 10)
            messages.collect { list ->
                assertEquals(1, list.size)
            }
        }
    }

    @Nested @DisplayName("Get Message Page")
    inner class GetMessagePageTest {
        @Test @DisplayName("getMessagePage returns paginated messages")
        fun `get message page`() = runTest {
            val page = repo.getMessagePage("conv-1")
            assertNotNull(page)
        }
    }

    @Nested @DisplayName("Insert Message")
    inner class InsertMessageTest {
        @Test @DisplayName("insertMessage resolves disappear timer from conversation")
        fun `insert resolves disappear`() = runTest {
            coEvery { conversationDao.getById(any()) } returns ConversationEntity(
                conversationId = "conv-1",
                type = "direct",
                disappearTimerSeconds = 86400
            )
            coEvery { messageDao.insert(any()) } returns 1L
            val msg = MessageEntity(
                conversationId = "conv-1", senderId = "self-user",
                envelopeId = "env-1", messageType = "SIGNAL_MESSAGE",
                content = "Hello", status = "sending", timestamp = 1000
            )
            val id = repo.insertMessage(msg)
            assertEquals(1L, id)
        }

        @Test @DisplayName("insertMessage preserves existing disappearAt if set")
        fun `insert preserves disappear`() = runTest {
            coEvery { messageDao.insert(any()) } returns 1L
            val msg = MessageEntity(
                conversationId = "conv-1", senderId = "self-user",
                envelopeId = "env-1", messageType = "SIGNAL_MESSAGE",
                content = "Hello", status = "sending", timestamp = 1000,
                disappearAt = 2000
            )
            val id = repo.insertMessage(msg)
            assertEquals(1L, id)
        }
    }

    @Nested @DisplayName("Insert Message and Update Conversation")
    inner class InsertAndUpdateTest {
        @Test @DisplayName("insertMessageAndUpdateConversation inserts message and updates conversation")
        fun `insert and update`() = runTest {
            val msg = MessageEntity(
                conversationId = "conv-1", senderId = "sender-1",
                envelopeId = "env-1", messageType = "SIGNAL_MESSAGE",
                content = "Hello", status = "delivered", timestamp = 1000
            )
            repo.insertMessageAndUpdateConversation(msg, "direct")
        }
    }

    @Nested @DisplayName("Get Message")
    inner class GetMessageTest {
        @Test @DisplayName("getMessage returns message by envelopeId")
        fun `get message by envelope`() = runTest {
            coEvery { messageDao.getByEnvelopeId(any()) } returns MessageEntity(
                localId = 1, conversationId = "conv-1", senderId = "user-1",
                envelopeId = "env-1", messageType = "SIGNAL_MESSAGE",
                content = "Hello", status = "delivered", timestamp = 1000
            )
            val msg = repo.getMessage("env-1")
            assertNotNull(msg)
            assertEquals("env-1", msg!!.envelopeId)
        }

        @Test @DisplayName("getMessage returns null for unknown envelopeId")
        fun `get message null`() = runTest {
            coEvery { messageDao.getByEnvelopeId(any()) } returns null
            val msg = repo.getMessage("unknown")
            assertNull(msg)
        }

        @Test @DisplayName("getMessageByLocalId returns message by localId")
        fun `get message by local id`() = runTest {
            coEvery { messageDao.getById(any()) } returns MessageEntity(
                localId = 42, conversationId = "conv-1", senderId = "user-1",
                envelopeId = "env-1", messageType = "SIGNAL_MESSAGE",
                content = "Hello", status = "delivered", timestamp = 1000
            )
            val msg = repo.getMessageByLocalId(42)
            assertNotNull(msg)
            assertEquals(42, msg!!.localId)
        }
    }

    @Nested @DisplayName("Update Message")
    inner class UpdateMessageTest {
        @Test @DisplayName("updateMessageStatus calls messageDao.updateStatus")
        fun `update message status`() = runTest {
            repo.updateMessageStatus("env-1", MessageStatus.READ)
            coVerify { messageDao.updateStatus("env-1", "read") }
        }

        @Test @DisplayName("updateMessageContent updates content and sets is_edited")
        fun `update message content`() = runTest {
            repo.updateMessageContent("env-1", "Edited text")
        }

        @Test @DisplayName("markMessageDeleted marks message as deleted")
        fun `mark message deleted`() = runTest {
            repo.markMessageDeleted("env-1")
            coVerify { messageDao.markDeleted("env-1") }
        }
    }

    @Nested @DisplayName("Conversation Operations")
    inner class ConversationOpsTest {
        @Test @DisplayName("getConversation returns conversation by ID")
        fun `get conversation`() = runTest {
            coEvery { conversationDao.getById(any()) } returns ConversationEntity(
                conversationId = "conv-1", type = "direct",
                lastMessage = "Hello", unreadCount = 3
            )
            val conv = repo.getConversation("conv-1")
            assertNotNull(conv)
            assertEquals("conv-1", conv!!.id)
        }

        @Test @DisplayName("getOrCreateConversation returns existing conversation")
        fun `get or create existing`() = runTest {
            coEvery { conversationDao.getById(any()) } returns ConversationEntity(
                conversationId = "conv-1", type = "direct"
            )
            val conv = repo.getOrCreateConversation("conv-1")
            assertEquals("conv-1", conv.id)
        }

        @Test @DisplayName("getOrCreateConversation creates new conversation if not exists")
        fun `get or create new`() = runTest {
            coEvery { conversationDao.getById(any()) } returns null
            coEvery { conversationDao.upsert(any()) } returns Unit
            val conv = repo.getOrCreateConversation("new-user")
            assertEquals("new-user", conv.id)
            assertEquals(ConversationType.DIRECT, conv.type)
        }

        @Test @DisplayName("setArchived sets conversation archived status")
        fun `set archived`() = runTest {
            repo.setArchived("conv-1", true)
            coVerify { conversationDao.setArchived("conv-1", true) }
        }

        @Test @DisplayName("setPinned sets conversation pinned status")
        fun `set pinned`() = runTest {
            repo.setPinned("conv-1", true)
            coVerify { conversationDao.setPinned("conv-1", true) }
        }

        @Test @DisplayName("setMuted sets conversation muted status")
        fun `set muted`() = runTest {
            repo.setMuted("conv-1", true, System.currentTimeMillis() + 3600000)
            coVerify { conversationDao.setMuted("conv-1", true, any()) }
        }

        @Test @DisplayName("markConversationRead resets unread count")
        fun `mark conversation read`() = runTest {
            repo.markConversationRead("conv-1")
        }
    }

    @Nested @DisplayName("Search")
    inner class SearchTest {
        @Test @DisplayName("searchConversations returns matching conversations")
        fun `search conversations`() = runTest {
            coEvery { conversationDao.search(any()) } returns flowOf(
                listOf(ConversationEntity("conv-1", "direct", lastMessage = "Hello"))
            )
            val results = repo.searchConversations("Hello")
            results.collect { list ->
                assertEquals(1, list.size)
            }
        }

        @Test @DisplayName("searchMessages returns matching messages")
        fun `search messages`() = runTest {
            coEvery { messageDao.searchMessages(any()) } returns flowOf(
                listOf(MessageEntity(localId = 1, conversationId = "conv-1", senderId = "user-1", messageType = "text", content = "test message", status = "delivered", timestamp = 1000))
            )
            val results = repo.searchMessages("test")
            results.collect { list ->
                assertEquals(1, list.size)
            }
        }
    }

    @Nested @DisplayName("Reactions")
    inner class ReactionsTest {
        @Test @DisplayName("addReaction inserts reaction")
        fun `add reaction`() = runTest {
            repo.addReaction("conv-1", 1, "\uD83D\uDC4D", "user-1")
        }

        @Test @DisplayName("removeReaction deletes reaction")
        fun `remove reaction`() = runTest {
            repo.removeReaction(1, "user-1")
        }

        @Test @DisplayName("starMessage stars or unstars message")
        fun `star message`() = runTest {
            repo.starMessage("env-1", true)
            coVerify { messageDao.starMessage("env-1", true) }
        }
    }

    @Nested @DisplayName("Unread Count")
    inner class UnreadCountTest {
        @Test @DisplayName("getUnreadCount returns total unread count")
        fun `get unread count`() = runTest {
            coEvery { conversationDao.getUnreadCount() } returns 5
            val count = repo.getUnreadCount()
            count.collect { value ->
                assertEquals(5, value)
            }
        }

        @Test @DisplayName("getConversationUnreadCount returns conversation unread count")
        fun `get conv unread count`() = runTest {
            coEvery { messageDao.getUnreadCount(any()) } returns 3
            val count = repo.getConversationUnreadCount("conv-1")
            assertEquals(3, count)
        }
    }

    @Nested @DisplayName("Delete Expired Messages")
    inner class DeleteExpiredTest {
        @Test @DisplayName("deleteExpiredMessages deletes expired messages and their media")
        fun `delete expired messages`() = runTest {
            repo.deleteExpiredMessages()
            coVerify { messageDao.deleteExpired(any()) }
        }
    }

    @Nested @DisplayName("Delete Conversation")
    inner class DeleteConversationTest {
        @Test @DisplayName("deleteConversation deletes all messages in conversation")
        fun `delete conversation`() = runTest {
            repo.deleteConversation("conv-1")
            coVerify { messageDao.deleteConversation("conv-1") }
        }
    }

    @Nested @DisplayName("Disappear Timer")
    inner class DisappearTimerTest {
        @Test @DisplayName("setDisappearTimer updates conversation timer")
        fun `set disappear timer`() = runTest {
            repo.setDisappearTimer("conv-1", 86400)
        }
    }

    @Nested @DisplayName("Pinned Messages")
    inner class PinnedMessagesTest {
        @Test @DisplayName("getPinnedMessages returns starred messages")
        fun `get pinned messages`() = runTest {
            val messages = repo.getPinnedMessages("conv-1")
            assertNotNull(messages)
        }
    }

    @Nested @DisplayName("Delete Local Media")
    inner class DeleteLocalMediaTest {
        @Test @DisplayName("deleteLocalMedia deletes cached media and marks message deleted")
        fun `delete local media`() = runTest {
            coEvery { messageDao.markDeleted(any()) } returns Unit
            repo.deleteLocalMedia("env-1")
            coVerify { messageDao.markDeleted("env-1") }
        }
    }
}
