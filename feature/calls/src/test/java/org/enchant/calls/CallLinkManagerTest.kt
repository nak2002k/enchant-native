package org.enchant.calls

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.enchant.calls.calllinks.CallLinkManager
import org.enchant.core.calls.CallLinkData
import org.enchant.core.calls.CallLinkRestrictions
import org.enchant.core.network.ApiClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

@DisplayName("CallLinkManager")
class CallLinkManagerTest {
    private val mockApi = mockk<ApiClient>()
    private lateinit var manager: CallLinkManager

    @BeforeEach
    fun setUp() {
        manager = CallLinkManager(mockApi)
    }

    @Nested
    @DisplayName("CallLinkData")
    inner class CallLinkDataTest {
        @Test
        fun `CallLinkData created with correct fields`() {
            val data = CallLinkData(
                roomId = "room_1",
                name = "Test Room",
                creatorId = "user_1",
                restrictions = CallLinkRestrictions.ANYONE,
                isActive = true
            )
            assert(data.roomId == "room_1")
            assert(data.name == "Test Room")
            assert(data.creatorId == "user_1")
            assert(data.restrictions == CallLinkRestrictions.ANYONE)
            assert(data.isActive)
        }

        @Test
        fun `CallLinkData with approval required`() {
            val data = CallLinkData(
                roomId = "room_2",
                name = "Private Room",
                creatorId = "user_2",
                restrictions = CallLinkRestrictions.APPROVAL_REQUIRED,
                isActive = true
            )
            assert(data.restrictions == CallLinkRestrictions.APPROVAL_REQUIRED)
        }

        @Test
        fun `CallLinkData with contacts only`() {
            val data = CallLinkData(
                roomId = "room_3",
                name = "Friends",
                creatorId = "user_3",
                restrictions = CallLinkRestrictions.CONTACTS_ONLY,
                isActive = false
            )
            assert(data.restrictions == CallLinkRestrictions.CONTACTS_ONLY)
            assert(!data.isActive)
        }
    }

    @Nested
    @DisplayName("create")
    inner class Create {
        @Test
        fun `createCallLink returns room ID on success`() = runTest {
            coEvery { mockApi.post(any(), any()) } returns Result.success(buildJsonObject {
                put("room_id", "new_room_123")
            })
            val result = manager.createCallLink("My Room", CallLinkRestrictions.ANYONE)
            assert(result.isSuccess)
            assert(result.getOrNull() == "new_room_123")
        }

        @Test
        fun `createCallLink returns failure on API error`() = runTest {
            coEvery { mockApi.post(any(), any()) } returns Result.failure(Exception("API error"))
            val result = manager.createCallLink("My Room", CallLinkRestrictions.ANYONE)
            assert(result.isFailure)
        }
    }

    @Nested
    @DisplayName("update")
    inner class Update {
        @Test
        fun `updateCallLinkName does not throw`() = runTest {
            coEvery { mockApi.put(any(), any()) } returns Result.success(buildJsonObject {})
            manager.updateCallLinkName("room_1", "New Name")
        }

        @Test
        fun `updateCallLinkRestrictions does not throw`() = runTest {
            coEvery { mockApi.put(any(), any()) } returns Result.success(buildJsonObject {})
            manager.updateCallLinkRestrictions("room_1", CallLinkRestrictions.APPROVAL_REQUIRED)
        }
    }

    @Nested
    @DisplayName("delete")
    inner class Delete {
        @Test
        fun `deleteCallLink does not throw`() = runTest {
            coEvery { mockApi.del(any()) } returns Result.success(buildJsonObject {})
            manager.deleteCallLink("room_1")
        }
    }

    @Nested
    @DisplayName("get")
    inner class Get {
        @Test
        fun `getCallLink returns data on success`() = runTest {
            coEvery { mockApi.get(any()) } returns Result.success(buildJsonObject {
                put("room_id", "room_1")
                put("name", "Test Room")
                put("creator_id", "user_1")
                put("restrictions", "ANYONE")
                put("is_active", "true")
            })
            val result = manager.getCallLink("room_1")
            assert(result.isSuccess)
            val data = result.getOrNull()
            assert(data?.name == "Test Room")
            assert(data?.creatorId == "user_1")
        }

        @Test
        fun `getCallLink returns failure on API error`() = runTest {
            coEvery { mockApi.get(any()) } returns Result.failure(Exception("Not found"))
            val result = manager.getCallLink("nonexistent")
            assert(result.isFailure)
        }
    }

    @Nested
    @DisplayName("join")
    inner class Join {
        @Test
        fun `joinCallLink delegates to getCallLink`() = runTest {
            coEvery { mockApi.get(any()) } returns Result.success(buildJsonObject {
                put("room_id", "room_1")
                put("name", "Test Room")
                put("creator_id", "user_1")
                put("restrictions", "ANYONE")
                put("is_active", "true")
            })
            val result = manager.joinCallLink("room_1")
            assert(result.isSuccess)
            assert(result.getOrNull()?.name == "Test Room")
        }
    }
}
