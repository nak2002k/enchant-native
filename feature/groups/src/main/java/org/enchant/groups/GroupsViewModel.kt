package org.enchant.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.enchant.groups.data.Group
import org.enchant.groups.data.GroupMember
import org.enchant.groups.data.GroupResult
import org.enchant.groups.data.GroupsRepository
import org.enchant.groups.data.InviteLink
import org.enchant.groups.data.JoinRequest
import org.enchant.groups.data.MemberRole

data class GroupsUiState(
    val groups: List<Group> = emptyList(),
    val members: List<GroupMember> = emptyList(),
    val joinRequests: List<JoinRequest> = emptyList(),
    val currentGroup: Group? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val inviteLink: String? = null,
    val invitePreview: GroupResult.Preview? = null,
    val disappearingMessagesEnabled: Boolean = false,
    val disappearingMessagesDurationSeconds: Int = 0
)

class GroupsViewModel(
    private val repository: GroupsRepository
) : ViewModel() {
    constructor() : this(GroupsRepository(org.enchant.core.network.ApiClient.getInstance(), org.enchant.core.database.DatabasePool.instance ?: error("DatabasePool not initialized")))
    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    fun loadGroups() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val groups = repository.getGroups()
                _uiState.value = _uiState.value.copy(groups = groups, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun createGroup(name: String, description: String? = null, memberIds: List<String>? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val selfId = org.enchant.core.base.SecurePreferences.getString("auth.user_id") ?: ""
                val result = repository.createGroup(name, description, memberIds)
                when (result) {
                    is GroupResult.Success -> {
                        loadGroups()
                        // Distribute the group sender key to all members so
                        // group messages can be sealed/decrypted end-to-end.
                        val allMembers = (memberIds ?: emptyList()) + selfId
                        viewModelScope.launch {
                            repository.broadcastSenderKeyDistribution(result.groupId, allMembers)
                        }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            successMessage = "Group '$name' created"
                        )
                    }
                    is GroupResult.Failed -> _uiState.value = _uiState.value.copy(
                        isLoading = false, error = result.error
                    )
                    else -> _uiState.value = _uiState.value.copy(
                        isLoading = false, error = "Unexpected result"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun loadGroupInfo(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.getGroupInfo(groupId)
            when (result) {
                is GroupResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        currentGroup = Group(result.groupId, result.name, memberCount = result.memberCount, myRole = result.role),
                        isLoading = false
                    )
                    loadMembers(groupId)
                }
                is GroupResult.Failed -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = result.error
                )
                else -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = "Unexpected result"
                )
            }
        }
    }

    fun loadMembers(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val members = repository.getMembers(groupId)
            _uiState.value = _uiState.value.copy(members = members, isLoading = false)
        }
    }

    fun addMembers(groupId: String, userIds: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val selfId = org.enchant.core.base.SecurePreferences.getString("auth.user_id") ?: ""
            val result = repository.addMembers(groupId, userIds)
            when (result) {
                is GroupResult.MemberAdded -> {
                    loadMembers(groupId)
                    // New members need the sender key distribution too.
                    viewModelScope.launch {
                        repository.broadcastSenderKeyDistribution(groupId, userIds + selfId)
                    }
                    _uiState.value = _uiState.value.copy(
                        successMessage = "${result.added} member(s) added"
                    )
                }
                is GroupResult.Failed -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = result.error
                )
                else -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = "Unexpected result"
                )
            }
        }
    }

    fun removeMember(groupId: String, userId: String) {
        viewModelScope.launch {
            val result = repository.removeMember(groupId, userId)
            when (result) {
                is GroupResult.MemberRemoved -> loadMembers(groupId)
                is GroupResult.Failed -> _uiState.value = _uiState.value.copy(error = result.error)
                else -> _uiState.value = _uiState.value.copy(error = "Unexpected result")
            }
        }
    }

    fun updateGroup(groupId: String, name: String? = null, description: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.updateGroup(groupId, name, description)
            when (result) {
                is GroupResult.Updated -> {
                    loadGroups()
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Group updated", isLoading = false
                    )
                }
                is GroupResult.Failed -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = result.error
                )
                else -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = "Unexpected result"
                )
            }
        }
    }

    fun updateMemberRole(groupId: String, userId: String, role: String) {
        viewModelScope.launch {
            val memberRole = MemberRole.fromString(role)
            val result = repository.updateMemberRole(groupId, userId, memberRole)
            when (result) {
                is GroupResult.Updated -> loadMembers(groupId)
                is GroupResult.Failed -> _uiState.value = _uiState.value.copy(error = result.error)
                else -> _uiState.value = _uiState.value.copy(error = "Unexpected result")
            }
        }
    }

    fun transferOwnership(groupId: String, newOwnerUserId: String) {
        viewModelScope.launch {
            val result = repository.transferOwnership(groupId, newOwnerUserId)
            when (result) {
                is GroupResult.Updated -> _uiState.value = _uiState.value.copy(
                    successMessage = "Ownership transferred"
                )
                is GroupResult.Failed -> _uiState.value = _uiState.value.copy(error = result.error)
                else -> _uiState.value = _uiState.value.copy(error = "Unexpected result")
            }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.deleteGroup(groupId)
            when (result) {
                is GroupResult.Deleted -> {
                    loadGroups()
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Group deleted", isLoading = false
                    )
                }
                is GroupResult.Failed -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = result.error
                )
                else -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = "Unexpected result"
                )
            }
        }
    }

    fun leaveGroup(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.leaveGroup(groupId)
            when (result) {
                is GroupResult.Deleted -> {
                    loadGroups()
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Left group", isLoading = false
                    )
                }
                is GroupResult.Failed -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = result.error
                )
                else -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = "Unexpected result"
                )
            }
        }
    }

    fun createInviteLink(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.createInviteLink(groupId)
            when (result) {
                is GroupResult.InviteCreated -> _uiState.value = _uiState.value.copy(
                    inviteLink = result.linkCode, isLoading = false
                )
                is GroupResult.Failed -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = result.error
                )
                else -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = "Unexpected result"
                )
            }
        }
    }

    fun joinViaLink(linkCode: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.joinViaLink(linkCode)
            when (result) {
                is GroupResult.Joined -> {
                    loadGroups()
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Joined '${result.name}'", isLoading = false
                    )
                }
                is GroupResult.Failed -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = result.error
                )
                else -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = "Unexpected result"
                )
            }
        }
    }

    fun previewInviteLink(linkCode: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.previewInviteLink(linkCode)
            when (result) {
                is GroupResult.Preview -> _uiState.value = _uiState.value.copy(
                    invitePreview = result, isLoading = false
                )
                is GroupResult.Failed -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = result.error
                )
                else -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = "Unexpected result"
                )
            }
        }
    }

    fun loadJoinRequests(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val requests = repository.getJoinRequests(groupId)
                _uiState.value = _uiState.value.copy(joinRequests = requests, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Failed to load join requests")
            }
        }
    }

    fun approveJoinRequest(groupId: String, requestId: String, approve: Boolean) {
        viewModelScope.launch {
            val result = repository.approveJoinRequest(groupId, requestId, approve)
            when (result) {
                is GroupResult.RequestApproved -> {
                    loadJoinRequests(groupId)
                    _uiState.value = _uiState.value.copy(
                        successMessage = if (approve) "Request approved" else "Request rejected"
                    )
                }
                is GroupResult.Failed -> _uiState.value = _uiState.value.copy(error = result.error)
                else -> _uiState.value = _uiState.value.copy(error = "Unexpected result")
            }
        }
    }

    fun updateDisappearingMessages(groupId: String, enabled: Boolean, durationSeconds: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.updateDisappearingMessages(groupId, enabled, durationSeconds)
            when (result) {
                is GroupResult.Updated -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    disappearingMessagesEnabled = enabled,
                    disappearingMessagesDurationSeconds = durationSeconds,
                    successMessage = "Disappearing messages updated"
                )
                is GroupResult.Failed -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = result.error
                )
                else -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = "Unexpected result"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
