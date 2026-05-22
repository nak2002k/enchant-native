package org.enchant.auth

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AuthNavKey : NavKey {

    @Serializable data object Welcome : AuthNavKey

    @Serializable data class Permissions(
        val nextRoute: AuthNavKey
    ) : AuthNavKey

    @Serializable data object PhoneEntry : AuthNavKey

    @Serializable data object CountryCodePicker : AuthNavKey

    @Serializable data class OtpVerify(
        val identifier: String
    ) : AuthNavKey

    @Serializable data object KeyGeneration : AuthNavKey

    @Serializable data object TwoStepPin : AuthNavKey

    @Serializable data object ProfileSetup : AuthNavKey

    @Serializable data object UsernamePicker : AuthNavKey

    @Serializable data class RestorePrompt(
        val hasBackup: Boolean = false
    ) : AuthNavKey

    @Serializable data object AppLock : AuthNavKey
}
