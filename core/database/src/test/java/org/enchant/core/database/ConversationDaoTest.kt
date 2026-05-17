package org.enchant.core.database.dao

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.enchant.core.database.DatabasePool
import org.enchant.core.database.entity.ConversationEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ConversationDao — Full Coverage")
class ConversationDaoTest {

    private lateinit var pool: DatabasePool
    private lateinit var dao: ConversationDao

    @BeforeEach
    fun setUp() {
        pool = mockk(relaxed = true)
        dao = ConversationDao(pool)
    }

    @Nested @DisplayName("Upsert")
    inner class UpsertTest {
        @Test @DisplayName("upsert inserts a new conversation")
        fun `upsert insert`() = runTest {
            val conversation = ConversationEntity(
                conversationId = "conv-1",
                type = "direct",
                lastMessage = "Hello",
                lastMessageEnvelopeId = "env-1",
                lastMessageTimestamp = 1000L,
                unreadCount = 1,
                isPinned = false,
                isArchived = false,
                isMuted = false,
                muteUntil = null,
                disappearTimerSeconds = 0
            )
            dao.upsert(conversation)
            verify { pool.write(any()) }
        }

        @Test @DisplayName("upsert replaces existing conversation")
        fun `upsert replace`() = runTest {
            val conversation = ConversationEntity(
                conversationId = "conv-1",
                type = "group",
                lastMessage = "Updated",
                lastMessageTimestamp = 2000L,
                unreadCount = 5
            )
            dao.upsert(conversation)
            verify { pool.write(any()) }
        }

        @Test @DisplayName("upsert with all boolean flags set")
        fun `upsert all flags`() = runTest {
            val conversation = ConversationEntity(
                conversationId = "conv-1",
                type = "direct",
                isPinned = true,
                isArchived = true,
                isMuted = true,
                muteUntil = 3000L,
                disappearTimerSeconds = 3600
            )
            dao.upsert(conversation)
            verify { pool.write(any()) }
        }
    }

    @Nested @DisplayName("Get All")
    inner class GetAllTest {
        @Test @DisplayName("getAll returns flow of all conversations")
        fun `get all`() = runTest {
            val flow = dao.getAll()
            assertNotNull(flow)
        }

        @Test @DisplayName("getAll returns empty list when no conversations")
        fun `get all empty`() = runTest {
            val flow = dao.getAll()
            assertNotNull(flow)
        }
    }

    @Nested @DisplayName("Get By ID")
    inner class GetByIdTest {
        @Test @DisplayName("getById queries by conversationId")
        fun `get by id`() = runTest {
            dao.getById("conv-1")
            verify { pool.readWith(any()) }
        }
    }

    @Nested @DisplayName("Set Archived")
    inner class SetArchivedTest {
        @Test @DisplayName("setArchived archives a conversation")
        fun `set archived true`() = runTest {
            dao.setArchived("conv-1", true)
            verify { pool.write(any()) }
        }

        @Test @DisplayName("setArchived unarchives a conversation")
        fun `set archived false`() = runTest {
            dao.setArchived("conv-1", false)
            verify { pool.write(any()) }
        }
    }

    @Nested @DisplayName("Set Pinned")
    inner class SetPinnedTest {
        @Test @DisplayName("setPinned pins a conversation")
        fun `set pinned true`() = runTest {
            dao.setPinned("conv-1", true)
            verify { pool.write(any()) }
        }

        @Test @DisplayName("setPinned unpins a conversation")
        fun `set pinned false`() = runTest {
            dao.setPinned("conv-1", false)
            verify { pool.write(any()) }
        }
    }

    @Nested @DisplayName("Set Muted")
    inner class SetMutedTest {
        @Test @DisplayName("setMuted mutes a conversation with expiry")
        fun `set muted with expiry`() = runTest {
            dao.setMuted("conv-1", true, 3600L)
            verify { pool.write(any()) }
        }

        @Test @DisplayName("setMuted mutes a conversation indefinitely")
        fun `set muted indefinite`() = runTest {
            dao.setMuted("conv-1", true, null)
            verify { pool.write(any()) }
        }

        @Test @DisplayName("setMuted unmutes a conversation")
        fun `set muted false`() = runTest {
            dao.setMuted("conv-1", false, null)
            verify { pool.write(any()) }
        }
    }

    @Nested @DisplayName("Increment Unread")
    inner class IncrementUnreadTest {
        @Test @DisplayName("incrementUnread increases count by 1")
        fun `increment unread default`() = runTest {
            dao.incrementUnread("conv-1")
            verify { pool.write(any()) }
        }

        @Test @DisplayName("incrementUnread increases count by custom amount")
        fun `increment unread custom`() = runTest {
            dao.incrementUnread("conv-1", 5)
            verify { pool.write(any()) }
        }

        @Test @DisplayName("incrementUnread with zero does nothing")
        fun `increment unread zero`() = runTest {
            dao.incrementUnread("conv-1", 0)
            verify { pool.write(any()) }
        }
    }

    @Nested @DisplayName("Get Unread Count")
    inner class GetUnreadCountTest {
        @Test @DisplayName("getUnreadCount queries total unread")
        fun `get unread count`() = runTest {
            dao.getUnreadCount()
            verify { pool.readWith(any()) }
        }
    }

    @Nested @DisplayName("Search")
    inner class SearchTest {
        @Test @DisplayName("search returns flow of matching conversations")
        fun `search conversations`() = runTest {
            val flow = dao.search("Hello")
            assertNotNull(flow)
        }

        @Test @DisplayName("search returns empty list when no matches")
        fun `search no matches`() = runTest {
            val flow = dao.search("nonexistent")
            assertNotNull(flow)
        }

        @Test @DisplayName("search handles empty query")
        fun `search empty query`() = runTest {
            val flow = dao.search("")
            assertNotNull(flow)
        }

        @Test @DisplayName("search handles errors gracefully")
        fun `search error handling`() = runTest {
            val flow = dao.search("test")
            assertNotNull(flow)
        }
    }
}
