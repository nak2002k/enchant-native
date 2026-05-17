package org.enchant.core.auth

import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.enchant.core.base.SecurePreferences
import org.enchant.core.network.ApiClient

sealed class RegistrationEvent {
    data object ResetState : RegistrationEvent()
    data object NavigateToWelcome : RegistrationEvent()
    data object NavigateToPhoneEntry : RegistrationEvent()
    data class CountryCodeSelected(
        val countryCode: Int,
        val regionCode: String,
        val countryName: String,
        val countryEmoji: String
    ) : RegistrationEvent()

    data class PhoneNumberChanged(val nationalNumber: String) : RegistrationEvent()
    data object PhoneNumberSubmitted : RegistrationEvent()
    data class OtpCodeEntered(val code: String) : RegistrationEvent()
    data object ResendOtp : RegistrationEvent()
    data object WrongPhoneNumber : RegistrationEvent()
    data object TermsAccepted : RegistrationEvent()
    data object PermissionsGranted : RegistrationEvent()
    data class ProfileDataEntered(
        val displayName: String,
        val about: String?,
        val avatarUri: Uri?
    ) : RegistrationEvent()

    data class UsernameEntered(val username: String) : RegistrationEvent()
    data object KeysGenerated : RegistrationEvent()
    data object RegistrationComplete : RegistrationEvent()
    data class PinCreated(val pin: String) : RegistrationEvent()
    data class RestoreDecisionMade(val shouldRestore: Boolean) : RegistrationEvent()
}

sealed class RegistrationState {
    data object Welcome : RegistrationState()
    data object PhoneEntry : RegistrationState()
    data class OtpVerification(
        val challengeId: String,
        val identifier: String,
        val expiresAt: Long
    ) : RegistrationState()

    data object Permissions : RegistrationState()
    data object ProfileSetup : RegistrationState()
    data object UsernamePicker : RegistrationState()
    data object KeyGeneration : RegistrationState()
    data object PinCreation : RegistrationState()
    data class RestorePrompt(
        val hasBackup: Boolean,
        val backupInfo: Any? = null
    ) : RegistrationState()

    data object Complete : RegistrationState()
    data class Error(val message: String, val retryAfter: Long? = null) : RegistrationState()
    data object Loading : RegistrationState()
}

object AuthStateMachine {
    private val _currentState = MutableStateFlow<RegistrationState>(RegistrationState.Welcome)
    val currentState: StateFlow<RegistrationState> = _currentState.asStateFlow()

    fun applyEvent(state: RegistrationState, event: RegistrationEvent): RegistrationState {
        return when (state) {
            is RegistrationState.Welcome -> when (event) {
                is RegistrationEvent.TermsAccepted -> RegistrationState.PhoneEntry
                else -> state
            }
            is RegistrationState.PhoneEntry -> when (event) {
                is RegistrationEvent.PhoneNumberSubmitted -> RegistrationState.Loading
                is RegistrationEvent.NavigateToWelcome -> RegistrationState.Welcome
                else -> state
            }
            is RegistrationState.Loading -> when (event) {
                is RegistrationEvent.PhoneNumberSubmitted -> RegistrationState.PhoneEntry
                is RegistrationEvent.CountryCodeSelected -> RegistrationState.PhoneEntry
                else -> state
            }
            is RegistrationState.OtpVerification -> when (event) {
                is RegistrationEvent.OtpCodeEntered -> {
                    if (event.code.length >= 6) RegistrationState.Loading
                    else state
                }
                is RegistrationEvent.ResendOtp -> RegistrationState.Loading
                is RegistrationEvent.WrongPhoneNumber -> RegistrationState.PhoneEntry
                else -> state
            }
            is RegistrationState.Permissions -> when (event) {
                is RegistrationEvent.PermissionsGranted -> RegistrationState.ProfileSetup
                else -> state
            }
            is RegistrationState.ProfileSetup -> when (event) {
                is RegistrationEvent.ProfileDataEntered -> {
                    if (event.displayName.isNotBlank()) RegistrationState.UsernamePicker
                    else state
                }
                else -> state
            }
            is RegistrationState.UsernamePicker -> when (event) {
                is RegistrationEvent.UsernameEntered -> RegistrationState.KeyGeneration
                else -> state
            }
            is RegistrationState.KeyGeneration -> when (event) {
                is RegistrationEvent.KeysGenerated -> RegistrationState.Complete
                else -> state
            }
            is RegistrationState.PinCreation -> when (event) {
                is RegistrationEvent.PinCreated -> RegistrationState.Complete
                else -> state
            }
            is RegistrationState.RestorePrompt -> when (event) {
                is RegistrationEvent.RestoreDecisionMade -> RegistrationState.ProfileSetup
                else -> state
            }
            is RegistrationState.Complete -> state
            is RegistrationState.Error -> when (event) {
                is RegistrationEvent.ResetState -> RegistrationState.PhoneEntry
                else -> state
            }
        }
    }

    fun transition(current: RegistrationState, event: RegistrationEvent): RegistrationState {
        return applyEvent(current, event)
    }

    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.add(android.Manifest.permission.CAMERA)
        permissions.add(android.Manifest.permission.RECORD_AUDIO)
        permissions.add(android.Manifest.permission.READ_CONTACTS)
        return permissions
    }

    suspend fun validateRestoredState(apiClient: ApiClient? = null): RegistrationState {
        val jwt = SecurePreferences.getString("auth.jwt")
        val refreshToken = SecurePreferences.getString("auth.refresh_token")

        if (jwt == null && refreshToken == null) return RegistrationState.Welcome

        if (jwt != null) {
            try {
                val parts = jwt.split(".")
                if (parts.size == 3) {
                    val payload = java.util.Base64.getUrlDecoder().decode(parts[1])
                    val payloadStr = payload.decodeToString()
                    val expMatch = Regex("\"exp\":(\\d+)").find(payloadStr)
                    if (expMatch != null) {
                        val exp = expMatch.groupValues[1].toLongOrNull() ?: 0L
                        if (System.currentTimeMillis() / 1000 < exp) {
                            return RegistrationState.Complete
                        }
                    }
                }
            } catch (e: Exception) { Log.w("AuthSM", "JWT validation failed: ${e.message}") }
        }

        return if (refreshToken != null) {
            try {
                val client = apiClient ?: run {
                    val c = ApiClient()
                    c.init()
                    c
                }
                val repo = AuthRepository(client)
                val result = repo.refreshToken(refreshToken)
                if (result.isSuccess) {
                    val response = result.getOrThrow()
                    SecurePreferences.putString("auth.jwt", response.accessToken)
                    SecurePreferences.putString("auth.refresh_token", response.refreshToken)
                    RegistrationState.Complete
                } else {
                    RegistrationState.Welcome
                }
            } catch (_: Exception) {
                RegistrationState.Welcome
            }
        } else {
            RegistrationState.Welcome
        }
    }
}
