package org.enchant.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.enchant.core.auth.AuthManager
import org.enchant.core.auth.AuthState
import org.enchant.core.auth.RegistrationState
import org.enchant.core.base.SecurePreferences

class AuthViewModel : ViewModel() {
    val registrationState: StateFlow<RegistrationState> = AuthManager.currentState
    val authState: StateFlow<AuthState> = AuthManager.authState

    fun requestOtp(identifier: String) {
        viewModelScope.launch { AuthManager.requestOtp(identifier) }
    }

    fun verifyOtp(code: String) {
        viewModelScope.launch { AuthManager.verifyOtp(code) }
    }

    fun resendOtp() {
        viewModelScope.launch { AuthManager.resendOtp() }
    }

    fun updateProfile(username: String, displayName: String, about: String?) {
        viewModelScope.launch { AuthManager.updateProfile(username, displayName, about) }
    }

    fun registerKeys() {
        viewModelScope.launch { AuthManager.registerKeys() }
    }

    fun checkUsernameAvailability(username: String): Boolean {
        return try {
            val result = kotlinx.coroutines.runBlocking {
                AuthManager.searchUsername(username)
            }
            result.getOrNull()?.isEmpty() == true
        } catch (e: Exception) {
            true
        }
    }

    fun enableBiometric() {
        SecurePreferences.putBoolean("applock.biometric", true)
    }

    fun restoreFromBackup() {
        viewModelScope.launch { AuthManager.restoreFromBackup() }
    }

    fun logout() {
        viewModelScope.launch { AuthManager.logout() }
    }
}
