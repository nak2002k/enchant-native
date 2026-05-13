package org.enchant.core.auth

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.enchant.core.base.SecurePreferences
import org.enchant.core.crypto.KeyManager
import org.enchant.core.network.ApiClient

sealed class AuthState {
    data object Unknown : AuthState()
    data object Unauthenticated : AuthState()
    data object Authenticating : AuthState()
    data class Authenticated(val userId: String, val deviceId: String) : AuthState()
}

object AuthManager {
    private var initialized = false
    private lateinit var repository: AuthRepository
    private lateinit var apiClient: ApiClient
    private val _currentState = MutableStateFlow<RegistrationState>(RegistrationState.Welcome)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)

    val currentState: StateFlow<RegistrationState> = _currentState.asStateFlow()
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    suspend fun init() {
        if (initialized) return
        apiClient = ApiClient()
        apiClient.init()
        repository = AuthRepository(apiClient)
        val storedState = AuthStateMachine.validateRestoredState()
        _currentState.value = storedState
        _authState.value = when (storedState) {
            is RegistrationState.Complete -> {
                val userId = SecurePreferences.getString("auth.user_id") ?: ""
                val deviceId = SecurePreferences.getString("auth.device_id") ?: ""
                AuthState.Authenticated(userId, deviceId)
            }
            else -> AuthState.Unauthenticated
        }
        initialized = true
    }

    suspend fun requestOtp(identifier: String): Result<Unit> {
        _currentState.value = RegistrationState.Loading
        val result = repository.requestOtp(identifier)
        return result.fold(
            onSuccess = { otpResponse ->
                _currentState.value = RegistrationState.OtpVerification(
                    challengeId = otpResponse.challengeId,
                    identifier = identifier,
                    expiresAt = System.currentTimeMillis() + (otpResponse.expiresIn * 1000L)
                )
                Result.success(Unit)
            },
            onFailure = { error ->
                _currentState.value = RegistrationState.Error(
                    message = error.message ?: "Failed to send code",
                    retryAfter = null
                )
                Result.failure(error)
            }
        )
    }

    suspend fun verifyOtp(code: String): Result<Unit> {
        val state = _currentState.value
        if (state !is RegistrationState.OtpVerification) {
            return Result.failure(IllegalStateException("Not in OTP state"))
        }
        _currentState.value = RegistrationState.Loading
        val result = repository.verifyOtp(state.challengeId, code)
        return result.fold(
            onSuccess = { authResponse ->
                SecurePreferences.putString("auth.jwt", authResponse.accessToken)
                SecurePreferences.putString("auth.refresh_token", authResponse.refreshToken)
                SecurePreferences.putString("auth.user_id", authResponse.userId)
                _authState.value = AuthState.Authenticated(authResponse.userId, "")
                _currentState.value = RegistrationState.Permissions
                Result.success(Unit)
            },
            onFailure = { error ->
                _currentState.value = RegistrationState.Error(message = error.message ?: "Verification failed")
                Result.failure(error)
            }
        )
    }

    suspend fun resendOtp(): Result<Unit> {
        val state = _currentState.value
        if (state !is RegistrationState.OtpVerification) {
            return Result.failure(IllegalStateException("Not in OTP state"))
        }
        return requestOtp(state.identifier)
    }

    suspend fun refreshToken(): Boolean {
        val refreshToken = SecurePreferences.getString("auth.refresh_token") ?: return false
        return try {
            val result = repository.refreshToken(refreshToken)
            result.fold(
                onSuccess = { response ->
                    SecurePreferences.putString("auth.jwt", response.accessToken)
                    SecurePreferences.putString("auth.refresh_token", response.refreshToken)
                    true
                },
                onFailure = {
                    _currentState.value = RegistrationState.Welcome
                    _authState.value = AuthState.Unauthenticated
                    false
                }
            )
        } catch (e: Exception) {
            _currentState.value = RegistrationState.Welcome
            _authState.value = AuthState.Unauthenticated
            false
        }
    }

    suspend fun logout() {
        try {
            repository.logout()
        } catch (e: Exception) {
        }
        SecurePreferences.remove("auth.jwt")
        SecurePreferences.remove("auth.refresh_token")
        SecurePreferences.remove("auth.user_id")
        SecurePreferences.remove("auth.device_id")
        SecurePreferences.remove("crypto.identity_key")
        SecurePreferences.remove("crypto.signed_prekey")
        _authState.value = AuthState.Unauthenticated
        _currentState.value = RegistrationState.Welcome
    }

    suspend fun deleteAccount(): Result<Unit> {
        return try {
            val result = repository.deleteAccount()
            logout()
            result
        } catch (e: Exception) {
            logout()
            Result.failure(Exception("Account deletion failed: ${e.message}"))
        }
    }

    suspend fun registerKeys(): Result<Unit> {
        return try {
            KeyManager.init()
            KeyManager.generateAndUploadKeys()
            _currentState.value = RegistrationState.KeyGeneration
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(username: String, displayName: String, about: String?): Result<Unit> {
        if (username.length !in 3..32 || !username.matches(Regex("^[a-z0-9_]+$"))) {
            return Result.failure(IllegalArgumentException("Invalid username format"))
        }
        if (displayName.isEmpty() || displayName.length > 64) {
            return Result.failure(IllegalArgumentException("Invalid display name"))
        }
        if (about != null && about.length > 139) {
            return Result.failure(IllegalArgumentException("About exceeds 139 characters"))
        }
        return try {
            val body = buildJsonObject {
                put("username", username)
                put("display_name", displayName)
                if (about != null) put("about", about)
            }
            apiClient.put("/v1/profile", body)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchUsername(prefix: String): Result<List<String>> {
        if (prefix.isEmpty()) return Result.success(emptyList())
        return try {
            val result = apiClient.get("/v1/profile/search", mapOf("username" to prefix))
            result.map { json ->
                json["results"]?.jsonArray?.map { item ->
                    item.jsonObject["username"]?.jsonPrimitive?.content ?: ""
                } ?: emptyList()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
