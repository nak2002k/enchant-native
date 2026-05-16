package org.enchant.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.enchant.contacts.data.Contact
import org.enchant.contacts.data.ContactResult
import org.enchant.contacts.data.ContactsRepository

data class ContactsUiState(
    val contacts: List<Contact> = emptyList(),
    val searchResults: List<Contact> = emptyList(),
    val blockedUsers: List<Contact> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

class ContactsViewModel(
    private val repository: ContactsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun loadContacts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val contacts = repository.getContacts()
            _uiState.value = _uiState.value.copy(contacts = contacts, isLoading = false)
        }
    }

    fun searchContacts(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            val results = repository.searchUsers(query)
            _uiState.value = _uiState.value.copy(searchResults = results)
        }
    }

    fun addContact(userId: String, customName: String? = null) {
        viewModelScope.launch {
            val result = repository.addContact(userId, customName)
            when (result) {
                is ContactResult.Added -> {
                    loadContacts()
                    _uiState.value = _uiState.value.copy(successMessage = "Contact added")
                }
                is ContactResult.Failed -> _uiState.value = _uiState.value.copy(error = result.error)
                else -> {}
            }
        }
    }

    fun removeContact(userId: String) {
        viewModelScope.launch {
            val result = repository.removeContact(userId)
            when (result) {
                is ContactResult.Removed -> {
                    loadContacts()
                    _uiState.value = _uiState.value.copy(successMessage = "Contact removed")
                }
                is ContactResult.Failed -> _uiState.value = _uiState.value.copy(error = result.error)
                else -> {}
            }
        }
    }

    fun blockUser(userId: String) {
        viewModelScope.launch {
            val result = repository.blockUser(userId)
            when (result) {
                is ContactResult.Added -> {
                    _uiState.value = _uiState.value.copy(successMessage = "User blocked")
                    loadBlockedUsers()
                }
                is ContactResult.Failed -> _uiState.value = _uiState.value.copy(error = result.error)
                else -> {}
            }
        }
    }

    fun unblockUser(userId: String) {
        viewModelScope.launch {
            val result = repository.unblockUser(userId)
            when (result) {
                is ContactResult.Removed -> {
                    _uiState.value = _uiState.value.copy(successMessage = "User unblocked")
                    loadBlockedUsers()
                }
                is ContactResult.Failed -> _uiState.value = _uiState.value.copy(error = result.error)
                else -> {}
            }
        }
    }

    fun loadBlockedUsers() {
        viewModelScope.launch {
            val blocked = repository.getBlockedUsers()
            _uiState.value = _uiState.value.copy(blockedUsers = blocked)
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
