package org.enchant.groups

import app.cash.turbine.test
import org.junit.jupiter.api.Assertions.assertTrue
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.enchant.groups.data.Group
import org.enchant.groups.data.GroupMember
import org.enchant.groups.data.GroupResult
import org.enchant.groups.data.GroupsRepository
import org.enchant.groups.data.JoinRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("GroupsViewModel")
class GroupsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val mockRepository: GroupsRepository = mockk()

    private lateinit var viewModel: GroupsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = GroupsViewModel(mockRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("loadGroups")
    inner class LoadGroups {
        @Test
        fun `loads groups successfully`() = runTest {
            val testGroups = listOf(
                Group("g1", "Group 1", memberCount = 3),
                Group("g2", "Group 2", memberCount = 5)
            )
            coEvery { mockRepository.getGroups() } returns testGroups

            viewModel.loadGroups()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.groups.size == 2)
            assertTrue(viewModel.uiState.value.groups[0].name == "Group 1")
            assertTrue(viewModel.uiState.value.isLoading == false)
            assertTrue(viewModel.uiState.value.error == null)
        }

        @Test
        fun `loads groups and returns empty list`() = runTest {
            coEvery { mockRepository.getGroups() } returns emptyList()

            viewModel.loadGroups()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.groups.isEmpty())
            assertTrue(viewModel.uiState.value.isLoading == false)
        }

        @Test
        fun `handles network error when loading groups`() = runTest {
            coEvery { mockRepository.getGroups() } throws RuntimeException("Network error")

            viewModel.loadGroups()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.error != null)
            assertTrue(viewModel.uiState.value.isLoading == false)
        }
    }

    @Nested
    @DisplayName("createGroup")
    inner class CreateGroup {
        @Test
        fun `creates group successfully`() = runTest {
            coEvery { mockRepository.createGroup("Test Group", null, null) } returns
                    GroupResult.Success("g1", "Test Group", 1)

            viewModel.createGroup("Test Group")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.successMessage?.contains("Test Group") == true)
            assertTrue(viewModel.uiState.value.isLoading == false)
        }

        @Test
        fun `fails to create group with empty name`() = runTest {
            coEvery { mockRepository.createGroup("", null, null) } returns
                    GroupResult.Failed("Name must be 1-100 characters")

            viewModel.createGroup("")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.error != null)
        }

        @Test
        fun `fails to create group on network error`() = runTest {
            coEvery { mockRepository.createGroup("Test", null, null) } returns
                    GroupResult.Failed("Network error")

            viewModel.createGroup("Test")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.error == "Network error")
        }
    }

    @Nested
    @DisplayName("addMembers and removeMember")
    inner class MemberManagement {
        @Test
        fun `adds members successfully`() = runTest {
            coEvery { mockRepository.getMembers("g1") } returns emptyList()
            coEvery { mockRepository.addMembers("g1", listOf("u1", "u2")) } returns
                    GroupResult.MemberAdded(2)

            viewModel.addMembers("g1", listOf("u1", "u2"))
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.successMessage?.contains("2") == true)
        }

        @Test
        fun `fails to add members with empty list`() = runTest {
            coEvery { mockRepository.addMembers("g1", emptyList()) } returns
                    GroupResult.Failed("At least one user ID required")

            viewModel.addMembers("g1", emptyList())
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.error != null)
        }

        @Test
        fun `removes member successfully`() = runTest {
            coEvery { mockRepository.getMembers("g1") } returns emptyList()
            coEvery { mockRepository.removeMember("g1", "u1") } returns GroupResult.MemberRemoved(true)

            viewModel.removeMember("g1", "u1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.error == null)
        }

        @Test
        fun `fails to remove non-existent member`() = runTest {
            coEvery { mockRepository.removeMember("g1", "nonexistent") } returns
                    GroupResult.Failed("Member not found")

            viewModel.removeMember("g1", "nonexistent")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.error == "Member not found")
        }
    }

    @Nested
    @DisplayName("inviteLink")
    inner class InviteLink {
        @Test
        fun `creates invite link successfully`() = runTest {
            coEvery { mockRepository.createInviteLink("g1") } returns
                    GroupResult.InviteCreated("abc123", null)

            viewModel.createInviteLink("g1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.inviteLink == "abc123")
        }

        @Test
        fun `previews invite link`() = runTest {
            coEvery { mockRepository.previewInviteLink("abc123") } returns
                    GroupResult.Preview("Group 1", "A test group", 5)

            viewModel.previewInviteLink("abc123")
            testDispatcher.scheduler.advanceUntilIdle()

            val preview = viewModel.uiState.value.invitePreview
            assertTrue(preview != null)
            assertTrue(preview!!.name == "Group 1")
            assertTrue(preview.memberCount == 5)
        }

        @Test
        fun `fails with invalid invite link`() = runTest {
            coEvery { mockRepository.previewInviteLink("invalid") } returns
                    GroupResult.Failed("Invite link not found")

            viewModel.previewInviteLink("invalid")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.error == "Invite link not found")
        }
    }

    @Nested
    @DisplayName("joinRequests")
    inner class JoinRequests {
        @Test
        fun `loads join requests`() = runTest {
            val requests = listOf(
                JoinRequest("r1", "u1", "PENDING"),
                JoinRequest("r2", "u2", "PENDING")
            )
            coEvery { mockRepository.getJoinRequests("g1") } returns requests

            viewModel.loadJoinRequests("g1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.joinRequests.size == 2)
        }

        @Test
        fun `approves join request`() = runTest {
            coEvery { mockRepository.getJoinRequests("g1") } returns emptyList()
            coEvery { mockRepository.approveJoinRequest("g1", "r1", true) } returns
                    GroupResult.RequestApproved(true)

            viewModel.approveJoinRequest("g1", "r1", true)
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.successMessage?.contains("approved") == true)
        }

        @Test
        fun `rejects join request`() = runTest {
            coEvery { mockRepository.getJoinRequests("g1") } returns emptyList()
            coEvery { mockRepository.approveJoinRequest("g1", "r1", false) } returns
                    GroupResult.RequestApproved(false)

            viewModel.approveJoinRequest("g1", "r1", false)
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.successMessage?.contains("rejected") == true)
        }
    }

    @Nested
    @DisplayName("deleteGroup and updateGroup")
    inner class GroupManagement {
        @Test
        fun `deletes group successfully`() = runTest {
            coEvery { mockRepository.getGroups() } returns emptyList()
            coEvery { mockRepository.deleteGroup("g1") } returns GroupResult.Deleted(true)

            viewModel.deleteGroup("g1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.successMessage == "Group deleted")
        }

        @Test
        fun `updates group name`() = runTest {
            coEvery { mockRepository.getGroups() } returns emptyList()
            coEvery { mockRepository.updateGroup("g1", "New Name", null) } returns GroupResult.Updated(true)

            viewModel.updateGroup("g1", name = "New Name")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.successMessage == "Group updated")
        }

        @Test
        fun `updates group description`() = runTest {
            coEvery { mockRepository.getGroups() } returns emptyList()
            coEvery { mockRepository.updateGroup("g1", description = "New desc") } returns GroupResult.Updated(true)

            viewModel.updateGroup("g1", description = "New desc")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.successMessage == "Group updated")
        }

        @Test
        fun `updates member role`() = runTest {
            coEvery { mockRepository.getMembers("g1") } returns emptyList()
            coEvery { mockRepository.updateMemberRole("g1", "u1", "admin") } returns GroupResult.Updated(true)

            viewModel.updateMemberRole("g1", "u1", "admin")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.error == null)
        }

        @Test
        fun `fails to update member role with invalid role`() = runTest {
            coEvery { mockRepository.updateMemberRole("g1", "u1", "king") } returns
                    GroupResult.Failed("Invalid role: king")

            viewModel.updateMemberRole("g1", "u1", "king")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.error == "Invalid role: king")
        }
    }

    @Nested
    @DisplayName("joinViaLink")
    inner class JoinViaLink {
        @Test
        fun `joins via valid link`() = runTest {
            coEvery { mockRepository.getGroups() } returns emptyList()
            coEvery { mockRepository.joinViaLink("valid_code") } returns
                    GroupResult.Joined("g1", "Test Group")

            viewModel.joinViaLink("valid_code")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.successMessage?.contains("Test Group") == true)
        }

        @Test
        fun `fails to join via invalid link`() = runTest {
            coEvery { mockRepository.joinViaLink("bad_code") } returns
                    GroupResult.Failed("Invalid invite link")

            viewModel.joinViaLink("bad_code")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.error == "Invalid invite link")
        }
    }

    @Nested
    @DisplayName("clearMessages")
    inner class ClearMessages {
        @Test
        fun `clears error and success messages`() = runTest {
            viewModel.clearMessages()
            assertTrue(viewModel.uiState.value.error == null)
            assertTrue(viewModel.uiState.value.successMessage == null)
        }
    }
}
