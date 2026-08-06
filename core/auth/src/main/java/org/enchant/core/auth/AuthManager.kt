package org.enchant.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import org.enchant.core.base.SecurePreferences
import org.enchant.core.crypto.KeyManager
import org.enchant.core.crypto.PreKeyWorker
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
    private var _pendingRegistrationPin: String? = null
    private val _registrationPinRequired = MutableStateFlow(false)
    val registrationPinRequired: StateFlow<Boolean> = _registrationPinRequired.asStateFlow()

    /** Registration lock: the server requires the account PIN on
     *  re-registration. The UI supplies it here, then retries the OTP. */
    fun setRegistrationPin(pin: String?) {
        _pendingRegistrationPin = pin
    }
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)

    @Volatile
    var pendingDisplayName: String? = null
    @Volatile
    var pendingAbout: String? = null

    fun setPendingProfile(displayName: String, about: String?) {
        pendingDisplayName = displayName
        pendingAbout = about
    }

    fun getPendingProfile(): Pair<String, String?> {
        return Pair(pendingDisplayName ?: "", pendingAbout)
    }

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
        val userId = SecurePreferences.getString(AuthConstants.USER_ID_KEY) ?: ""
        val deviceId = SecurePreferences.getString(AuthConstants.DEVICE_ID_KEY) ?: ""
        val jwt = SecurePreferences.getString(AuthConstants.JWT_KEY)
        _authState.value = when {
            storedState is RegistrationState.Complete ||
                (userId.isNotBlank() && !jwt.isNullOrBlank()) ->
                AuthState.Authenticated(userId, deviceId)
            else -> AuthState.Unauthenticated
        }
        initialized = true
    }

    suspend fun schedulePreKeyRotation(context: Context) {
        val ctx = context.applicationContext ?: context
        PreKeyWorker.schedule(ctx)
        Log.d("AuthManager", "PreKeyRotationWorker scheduled")
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
        val result = if (_pendingRegistrationPin != null) {
            repo.verifyOtp(state.challengeId, code, pin = _pendingRegistrationPin)
        } else {
            repo.verifyOtp(state.challengeId, code)
        }
        return result.fold(
            onSuccess = { authResponse ->
                _pendingRegistrationPin = null
                SecurePreferences.putString(AuthConstants.JWT_KEY, authResponse.accessToken)
                SecurePreferences.putString(AuthConstants.REFRESH_TOKEN_KEY, authResponse.refreshToken)
                SecurePreferences.putString(AuthConstants.USER_ID_KEY, authResponse.userId)
                SecurePreferences.putString(AuthConstants.DEVICE_ID_KEY, authResponse.deviceId)
                authResponse.phoneSalt?.let {
                    SecurePreferences.putString(AuthConstants.PHONE_SALT_KEY, it)
                } ?: SecurePreferences.remove(AuthConstants.PHONE_SALT_KEY)
                _authState.value = AuthState.Authenticated(authResponse.userId, authResponse.deviceId)
                _currentState.value = RegistrationState.ProfileSetup
                Result.success(Unit)
            },
            onFailure = { error ->
                val message = error.message ?: "Verification failed"
                if (message.contains("pin_required", ignoreCase = true)) {
                    _registrationPinRequired.value = true
                    _currentState.value = RegistrationState.Error(
                        message = "This number is protected by a registration lock PIN. Enter the PIN to continue."
                    )
                } else if (message.contains("pin_invalid", ignoreCase = true)) {
                    _registrationPinRequired.value = true
                    _currentState.value = RegistrationState.Error(message = "Incorrect registration lock PIN. Try again.")
                } else {
                    _currentState.value = RegistrationState.Error(message = message)
                }
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
                    response.phoneSalt?.let {
                        SecurePreferences.putString(AuthConstants.PHONE_SALT_KEY, it)
                    }
                    true
                },
                onFailure = {
                    val msg = it.message.orEmpty()
                    // Transient failures must not log the user out (Signal
                    // parity); only a hard auth rejection resets the session.
                    if (msg.contains("401") || msg.contains("403") || msg.contains("Unauthorized")) {
                        _currentState.value = RegistrationState.Welcome
                        _authState.value = AuthState.Unauthenticated
                    }
                    false
                }
            )
        } catch (e: Exception) {
            Log.w("AuthManager", "Token refresh failed (transient): ${e.message}")
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
        SecurePreferences.remove(AuthConstants.PHONE_SALT_KEY)
        KeyManager.clearIdentityKey()
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
        val jwt = SecurePreferences.getString(AuthConstants.JWT_KEY)
        if (jwt.isNullOrBlank()) {
            return Result.failure(
                IllegalStateException("Not authenticated — complete OTP verification before registering keys")
            )
        }
        return try {
            KeyManager.init()
            _currentState.value = RegistrationState.KeyGeneration
            val result = KeyManager.generateAndUploadKeys()
            if (result.isSuccess) {
                KeyManager.syncNativeIdentity()
                _currentState.value = RegistrationState.PinCreation
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

    suspend fun restoreFromBackup(): Result<Unit> {
        val repo = repository ?: return Result.failure(IllegalStateException("AuthManager not initialized"))
        _currentState.value = RegistrationState.Loading
        return try {
            val result = repo.restoreFromBackup()
            result.fold(
                onSuccess = {
                    _currentState.value = RegistrationState.Permissions
                    Result.success(Unit)
                },
                onFailure = { error ->
                    _currentState.value = RegistrationState.Error(message = error.message ?: "Restore failed")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            _currentState.value = RegistrationState.Error(message = e.message ?: "Restore failed")
            Result.failure(e)
        }
    }

    fun completeRegistration() {
        _currentState.value = RegistrationState.Complete
        val userId = SecurePreferences.getString(AuthConstants.USER_ID_KEY) ?: ""
        val deviceId = SecurePreferences.getString(AuthConstants.DEVICE_ID_KEY) ?: ""
        if (userId.isNotBlank()) {
            _authState.value = AuthState.Authenticated(userId, deviceId)
        }
    }
}
