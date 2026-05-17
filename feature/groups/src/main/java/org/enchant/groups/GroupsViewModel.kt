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

data class GroupsUiState(
    val groups: List<Group> = emptyList(),
    val members: List<GroupMember> = emptyList(),
    val joinRequests: List<JoinRequest> = emptyList(),
    val currentGroup: Group? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val inviteLink: String? = null,
    val invitePreview: GroupResult.Preview? = null
)

class GroupsViewModel(
    private val repository: GroupsRepository
) : ViewModel() {
    constructor() : this(GroupsRepository(org.enchant.core.network.ApiClient.getInstance(), org.enchant.core.database.DatabasePool.instance!!))
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
                val result = repository.createGroup(name, description, memberIds)
                when (result) {
                    is GroupResult.Success -> {
                        loadGroups()
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
                        currentGroup = Group(result.groupId, result.name, memberCount = result.memberCount),
                        isLoading = false
                    )
                    loadMembers(groupId)
                }
                is GroupResult.Failed -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = result.error
                )
                else -> {}
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
            val result = repository.addMembers(groupId, userIds)
            when (result) {
                is GroupResult.MemberAdded -> {
                    loadMembers(groupId)
                    _uiState.value = _uiState.value.copy(
                        successMessage = "${result.added} member(s) added"
                    )
                }
                is GroupResult.Failed -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = result.error
                )
                else -> {}
            }
        }
    }

    fun removeMember(groupId: String, userId: String) {
        viewModelScope.launch {
            val result = repository.removeMember(groupId, userId)
            when (result) {
                is GroupResult.MemberRemoved -> loadMembers(groupId)
                is GroupResult.Failed -> _uiState.value = _uiState.value.copy(error = result.error)
                else -> {}
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
                else -> {}
            }
        }
    }

    fun updateMemberRole(groupId: String, userId: String, role: String) {
        viewModelScope.launch {
            val result = repository.updateMemberRole(groupId, userId, role)
            when (result) {
                is GroupResult.Updated -> loadMembers(groupId)
                is GroupResult.Failed -> _uiState.value = _uiState.value.copy(error = result.error)
                else -> {}
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
                else -> {}
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
                else -> {}
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
                else -> {}
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
                else -> {}
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
                else -> {}
            }
        }
    }

    fun loadJoinRequests(groupId: String) {
        viewModelScope.launch {
            val requests = repository.getJoinRequests(groupId)
            _uiState.value = _uiState.value.copy(joinRequests = requests)
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
                else -> {}
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
