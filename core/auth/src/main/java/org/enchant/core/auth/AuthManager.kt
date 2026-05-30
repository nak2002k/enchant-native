package org.enchant.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import android.util.Log
import org.enchant.core.base.SecurePreferences
import org.enchant.core.crypto.KeyManager
import org.enchant.core.model.User
import org.enchant.core.network.ApiClient
import org.enchant.core.network.models.OtpResponse

sealed class AuthState {
    data object Unknown : AuthState()
    data object Unauthenticated : AuthState()
    data object Authenticating : AuthState()
    data class Authenticated(val userId: String, val deviceId: String) : AuthState()
}

object AuthManager {
    @Volatile
    private var initialized = false
    private var repository: AuthRepository? = null
    private var apiClient: ApiClient? = null
    private val _currentState = MutableStateFlow<RegistrationState>(RegistrationState.Welcome)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)

    @Volatile
    var pendingDisplayName: String? = null
    @Volatile
    var pendingAbout: String? = null

    val currentState: StateFlow<RegistrationState> = _currentState.asStateFlow()
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun setApiClient(client: ApiClient) {
        apiClient = client
        repository = AuthRepository(client)
    }

    suspend fun init() {
        if (initialized) return
        if (apiClient == null) {
            val client = ApiClient()
            client.init()
            apiClient = client
            repository = AuthRepository(client)
        }
        val storedState = AuthStateMachine.validateRestoredState(repository!!)
        _currentState.value = storedState
        _authState.value = when (storedState) {
            is RegistrationState.Complete -> {
                val userId = SecurePreferences.getString(AuthConstants.USER_ID_KEY) ?: ""
                val deviceId = SecurePreferences.getString(AuthConstants.DEVICE_ID_KEY) ?: ""
                AuthState.Authenticated(userId, deviceId)
            }
            else -> AuthState.Unauthenticated
        }
        initialized = true
    }

    fun resetForTesting() {
        initialized = false
        repository = null
        apiClient = null
        _currentState.value = RegistrationState.Welcome
        _authState.value = AuthState.Unknown
    }

    suspend fun requestOtp(identifier: String): Result<OtpResponse> {
        val repo = repository
        if (repo == null) {
            _currentState.value = RegistrationState.Error(
                message = "App not initialized. Try restarting.",
                retryAfter = null
            )
            return Result.failure(IllegalStateException("AuthManager not initialized"))
        }
        val now = System.currentTimeMillis()
        if (now - lastOtpRequestMs < otpCooldownMs) {
            val remaining = (otpCooldownMs - (now - lastOtpRequestMs)) / 1000
            _currentState.value = RegistrationState.Error(
                message = "Please wait ${remaining}s before requesting",
                retryAfter = remaining
            )
            return Result.failure(IllegalStateException("Please wait ${remaining}s before requesting"))
        }
        lastOtpRequestMs = now
        _currentState.value = RegistrationState.Loading
        val result = repo.requestOtp(identifier)
        return result.fold(
            onSuccess = { otpResponse ->
                _currentState.value = RegistrationState.OtpVerification(
                    challengeId = otpResponse.challengeId,
                    identifier = identifier,
                    expiresAt = System.currentTimeMillis() + (otpResponse.expiresIn * 1000L)
                )
                Result.success(otpResponse)
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
        val repo = repository ?: return Result.failure(IllegalStateException("AuthManager not initialized"))
        val state = _currentState.value
        if (state !is RegistrationState.OtpVerification) {
            return Result.failure(IllegalStateException("Not in OTP state"))
        }
        if (System.currentTimeMillis() > state.expiresAt) {
            _currentState.value = RegistrationState.Error(message = "Code expired. Request a new one.")
            return Result.failure(IllegalStateException("OTP code expired"))
        }
        _currentState.value = RegistrationState.Loading
        val result = repo.verifyOtp(state.challengeId, code)
        return result.fold(
            onSuccess = { authResponse ->
                SecurePreferences.putString(AuthConstants.JWT_KEY, authResponse.accessToken)
                SecurePreferences.putString(AuthConstants.REFRESH_TOKEN_KEY, authResponse.refreshToken)
                SecurePreferences.putString(AuthConstants.USER_ID_KEY, authResponse.userId)
                SecurePreferences.putString(AuthConstants.DEVICE_ID_KEY, authResponse.deviceId)
                _authState.value = AuthState.Authenticated(authResponse.userId, authResponse.deviceId)
                _currentState.value = RegistrationState.Permissions
                Result.success(Unit)
            },
            onFailure = { error ->
                _currentState.value = RegistrationState.Error(message = error.message ?: "Verification failed")
                Result.failure(error)
            }
        )
    }

    private var lastOtpRequestMs: Long
        get() = SecurePreferences.getLong(AuthConstants.LAST_OTP_REQUEST_KEY, 0L)
        set(value) = SecurePreferences.putLong(AuthConstants.LAST_OTP_REQUEST_KEY, value)
    private val otpCooldownMs = AuthConstants.OTP_COOLDOWN_MS

    suspend fun resendOtp(): Result<Unit> {
        val now = System.currentTimeMillis()
        if (now - lastOtpRequestMs < otpCooldownMs) {
            val remaining = (otpCooldownMs - (now - lastOtpRequestMs)) / 1000
            return Result.failure(IllegalStateException("Please wait ${remaining}s before resending"))
        }
        val state = _currentState.value
        if (state !is RegistrationState.OtpVerification) {
            return Result.failure(IllegalStateException("Not in OTP state"))
        }
        lastOtpRequestMs = now
        return requestOtp(state.identifier).map { }
    }

    suspend fun refreshToken(): Boolean {
        val repo = repository ?: return false
        val refreshToken = SecurePreferences.getString(AuthConstants.REFRESH_TOKEN_KEY) ?: return false
        return try {
            val result = repo.refreshToken(refreshToken)
            result.fold(
                onSuccess = { response ->
                    SecurePreferences.putString(AuthConstants.JWT_KEY, response.accessToken)
                    SecurePreferences.putString(AuthConstants.REFRESH_TOKEN_KEY, response.refreshToken)
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
            repository?.logout()
        } catch (e: Exception) {
            Log.w("AuthManager", "Server logout failed: ${e.message}")
        }
        SecurePreferences.remove(AuthConstants.JWT_KEY)
        SecurePreferences.remove(AuthConstants.REFRESH_TOKEN_KEY)
        SecurePreferences.remove(AuthConstants.USER_ID_KEY)
        SecurePreferences.remove(AuthConstants.DEVICE_ID_KEY)
        SecurePreferences.remove("crypto.identity_key")
        SecurePreferences.remove("crypto.signed_prekey")
        _authState.value = AuthState.Unauthenticated
        _currentState.value = RegistrationState.Welcome
    }

    suspend fun deleteAccount(): Result<Unit> {
        val repo = repository ?: return Result.failure(IllegalStateException("AuthManager not initialized"))
        return try {
            val result = repo.deleteAccount()
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
            _currentState.value = RegistrationState.KeyGeneration
            val result = KeyManager.generateAndUploadKeys()
            if (result.isSuccess) {
                _currentState.value = RegistrationState.Complete
            } else {
                val error = result.exceptionOrNull()
                _currentState.value = RegistrationState.Error(
                    message = error?.message ?: "Key registration failed"
                )
            }
            result
        } catch (e: Exception) {
            _currentState.value = RegistrationState.Error(message = e.message ?: "Key registration failed")
            Result.failure(e)
        }
    }

    suspend fun updateProfile(username: String, displayName: String, about: String?): Result<Unit> {
        val client = apiClient ?: return Result.failure(IllegalStateException("AuthManager not initialized"))
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
            client.put("/v1/profile", body)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchUsername(prefix: String): Result<List<User>> {
        val client = apiClient ?: return Result.failure(IllegalStateException("AuthManager not initialized"))
        if (prefix.isEmpty()) return Result.success(emptyList())
        return try {
            val result = client.get("/v1/profile/search", mapOf("username" to prefix))
            result.map { json ->
                json["results"]?.jsonArray?.map { item ->
                    val obj = item.jsonObject
                    User(
                        userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                        username = obj["username"]?.jsonPrimitive?.content ?: "",
                        displayName = obj["display_name"]?.jsonPrimitive?.content,
                        avatarMediaId = obj["avatar_media_id"]?.jsonPrimitive?.content
                    )
                } ?: emptyList()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
