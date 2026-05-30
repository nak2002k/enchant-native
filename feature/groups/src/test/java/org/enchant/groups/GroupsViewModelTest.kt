package org.enchant.groups

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.enchant.groups.data.GroupResult
import org.enchant.groups.data.GroupsRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("GroupsViewModel — Full Coverage")
class GroupsViewModelTest {

    private lateinit var repo: GroupsRepository
    private lateinit var viewModel: GroupsViewModel

    @BeforeEach
    fun setUp() {
        repo = mockk(relaxed = true)
        coEvery { repo.getGroups() } returns emptyList()
        coEvery { repo.getGroupInfo(any()) } returns GroupResult.Failed("not found")
        coEvery { repo.getMembers(any()) } returns emptyList()
        viewModel = GroupsViewModel(repo)
    }

    @Nested @DisplayName("Load Groups")
    inner class LoadGroupsTest {
        @Test @DisplayName("loadGroups fetches groups from repository")
        fun `load groups`() = runTest {
            viewModel.loadGroups()
            coVerify { repo.getGroups() }
        }
    }

    @Nested @DisplayName("Create Group")
    inner class CreateGroupTest {
        @Test @DisplayName("createGroup creates a new group")
        fun `create group`() = runTest {
            coEvery { repo.createGroup(any(), any(), any()) } returns GroupResult.Success("group-1", "Test Group", 1)
            viewModel.createGroup("Test Group", "Description", listOf("user-1"))
            coVerify { repo.createGroup("Test Group", "Description", listOf("user-1")) }
        }
    }

    @Nested @DisplayName("Load Group Info")
    inner class LoadGroupInfoTest {
        @Test @DisplayName("loadGroupInfo fetches group info")
        fun `load group info`() = runTest {
            viewModel.loadGroupInfo("group-1")
            coVerify { repo.getGroupInfo("group-1") }
        }
    }

    @Nested @DisplayName("Load Members")
    inner class LoadMembersTest {
        @Test @DisplayName("loadMembers fetches group members")
        fun `load members`() = runTest {
            viewModel.loadMembers("group-1")
            coVerify { repo.getMembers("group-1") }
        }
    }

    @Nested @DisplayName("Remove Member")
    inner class RemoveMemberTest {
        @Test @DisplayName("removeMember removes a member from group")
        fun `remove member`() = runTest {
            viewModel.removeMember("group-1", "user-1")
            coVerify { repo.removeMember("group-1", "user-1") }
        }
    }

    @Nested @DisplayName("Update Member Role")
    inner class UpdateMemberRoleTest {
        @Test @DisplayName("updateMemberRole updates member role")
        fun `update member role`() = runTest {
            viewModel.updateMemberRole("group-1", "user-1", "admin")
            coVerify { repo.updateMemberRole("group-1", "user-1", org.enchant.groups.data.MemberRole.ADMIN) }
        }
    }

    @Nested @DisplayName("Create Invite Link")
    inner class CreateInviteLinkTest {
        @Test @DisplayName("createInviteLink creates an invite link")
        fun `create invite link`() = runTest {
            viewModel.createInviteLink("group-1")
            coVerify { repo.createInviteLink("group-1") }
        }
    }

    @Nested @DisplayName("Join Via Link")
    inner class JoinViaLinkTest {
        @Test @DisplayName("joinViaLink joins a group via invite link")
        fun `join via link`() = runTest {
            viewModel.joinViaLink("link-code-1")
            coVerify { repo.joinViaLink("link-code-1") }
        }
    }

    @Nested @DisplayName("Delete Group")
    inner class DeleteGroupTest {
        @Test @DisplayName("deleteGroup deletes a group")
        fun `delete group`() = runTest {
            viewModel.deleteGroup("group-1")
            coVerify { repo.deleteGroup("group-1") }
        }
    }

    @Nested @DisplayName("Leave Group")
    inner class LeaveGroupTest {
        @Test @DisplayName("leaveGroup leaves a group")
        fun `leave group`() = runTest {
            viewModel.leaveGroup("group-1")
            coVerify { repo.leaveGroup("group-1") }
        }
    }

    @Nested @DisplayName("Transfer Ownership")
    inner class TransferOwnershipTest {
        @Test @DisplayName("transferOwnership transfers group ownership")
        fun `transfer ownership`() = runTest {
            viewModel.transferOwnership("group-1", "user-2")
            coVerify { repo.transferOwnership("group-1", "user-2") }
        }
    }

    @Nested @DisplayName("Update Group")
    inner class UpdateGroupTest {
        @Test @DisplayName("updateGroup updates group details")
        fun `update group`() = runTest {
            viewModel.updateGroup("group-1", "New Name", "New Desc")
            coVerify { repo.updateGroup("group-1", "New Name", "New Desc") }
        }
    }

    @Nested @DisplayName("UI State")
        @Test @DisplayName("uiState has default values")
        fun `ui state defaults`() = runTest {
            val state = viewModel.uiState.value
            assertNotNull(state)
            assertTrue(state.groups.isEmpty())
            assertFalse(state.isLoading)
            assertNull(state.error)
        }
    }
}
