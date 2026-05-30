package org.enchant.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.collectLatest
import org.enchant.auth.screens.AppLockScreen
import org.enchant.auth.screens.CountryCodePickerScreen
import org.enchant.auth.screens.KeyGenerationScreen
import org.enchant.auth.screens.OtpVerifyScreen
import org.enchant.auth.screens.PermissionsScreen
import org.enchant.auth.screens.PhoneEntryScreen
import org.enchant.auth.screens.ProfileSetupScreen
import org.enchant.auth.screens.RestorePromptScreen
import org.enchant.auth.screens.TwoStepPinScreen
import org.enchant.auth.screens.UsernamePickerScreen
import org.enchant.auth.screens.WelcomeScreen
import org.enchant.core.auth.AuthState
import org.enchant.core.auth.RegistrationState
import org.enchant.core.ui.navigation.TransitionSpecs

@Composable
fun AuthNavDisplay(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey> = rememberNavBackStack(AuthNavKey.Welcome),
    viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    modifier: Modifier = Modifier,
    onAuthComplete: () -> Unit = {}
) {
    val registrationState by viewModel.registrationState.collectAsState()
    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(registrationState) {
        val state = registrationState
        when (state) {
            is RegistrationState.OtpVerification -> {
                if (backStack.lastOrNull() !is AuthNavKey.OtpVerify) {
                    backStack.add(AuthNavKey.OtpVerify(identifier = state.identifier))
                }
            }
            is RegistrationState.KeyGeneration -> {
                if (backStack.lastOrNull() !is AuthNavKey.ProfileSetup) {
                    backStack.add(AuthNavKey.ProfileSetup)
                }
            }
            is RegistrationState.Permissions -> {
                if (backStack.lastOrNull() !is AuthNavKey.KeyGeneration) {
                    backStack.add(AuthNavKey.KeyGeneration)
                }
            }
            is RegistrationState.UsernamePicker -> {
                if (backStack.lastOrNull() !is AuthNavKey.AppLock) {
                    backStack.add(AuthNavKey.AppLock)
                }
            }
            is RegistrationState.Complete -> {
                if (backStack.lastOrNull() !is AuthNavKey.AppLock) {
                    backStack.add(AuthNavKey.AppLock)
                }
            }
            is RegistrationState.Error -> {}
            is RegistrationState.Loading -> {}
            else -> {}
        }
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            onAuthComplete()
        }
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        transitionSpec = {
            if (targetState.key is AuthNavKey.CountryCodePicker) {
                TransitionSpecs.VerticalSlide.transitionSpec(this)
            } else {
                TransitionSpecs.HorizontalSlide.transitionSpec(this)
            }
        },
        popTransitionSpec = {
            if (initialState.key is AuthNavKey.CountryCodePicker) {
                TransitionSpecs.VerticalSlide.popTransitionSpec(this)
            } else {
                TransitionSpecs.HorizontalSlide.popTransitionSpec(this)
            }
        },
        predictivePopTransitionSpec = {
            if (initialState.key is AuthNavKey.CountryCodePicker) {
                TransitionSpecs.VerticalSlide.predictivePopTransitionSpec(this, it)
            } else {
                TransitionSpecs.HorizontalSlide.predictivePopTransitionSpec(this, it)
            }
        },
        entryProvider = entryProvider {
            entry<AuthNavKey.Welcome> {
                WelcomeScreen(
                    onTermsAccepted = { backStack.add(AuthNavKey.Permissions(nextRoute = AuthNavKey.PhoneEntry)) },
                    onRestore = { backStack.add(AuthNavKey.RestorePrompt()) }
                )
            }

            entry<AuthNavKey.Permissions> { key ->
                PermissionsScreen(
                    onPermissionsGranted = { backStack.add(key.nextRoute) },
                    onSkip = { backStack.add(key.nextRoute) }
                )
            }

            entry<AuthNavKey.PhoneEntry> {
                PhoneEntryScreen(
                    onCountrySelected = { backStack.add(AuthNavKey.CountryCodePicker) },
                    onPhoneNumberChanged = {},
                    onPhoneNumberSubmitted = { phone ->
                        viewModel.requestOtp(phone)
                        backStack.add(AuthNavKey.OtpVerify(identifier = phone))
                    },
                    onNavigateBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                )
            }

            entry<AuthNavKey.CountryCodePicker> {
                CountryCodePickerScreen(
                    onCountrySelected = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    onDismiss = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                )
            }

            entry<AuthNavKey.OtpVerify> { key ->
                OtpVerifyScreen(
                    identifier = key.identifier,
                    onCodeSubmitted = { viewModel.verifyOtp(it) },
                    onResendCode = { viewModel.requestOtp(key.identifier) },
                    onWrongNumber = {
                        while (backStack.size > 1 && backStack.get(backStack.size - 1) !is AuthNavKey.PhoneEntry) {
                            backStack.removeAt(backStack.size - 1)
                        }
                    }
                )
            }

            entry<AuthNavKey.KeyGeneration> {
                KeyGenerationScreen(
                    onKeysGenerated = { backStack.add(AuthNavKey.ProfileSetup) },
                    onRetry = { viewModel.registerKeys() }
                )
            }

            entry<AuthNavKey.TwoStepPin> {
                TwoStepPinScreen.Screen(
                    onPinCreated = { backStack.add(AuthNavKey.KeyGeneration) }
                )
            }

            entry<AuthNavKey.ProfileSetup> {
                ProfileSetupScreen(
                    onProfileDataEntered = { displayName, about, avatarUri ->
                        viewModel.updateProfile(username = displayName, displayName = displayName, about = about)
                        backStack.add(AuthNavKey.UsernamePicker)
                    }
                )
            }

            entry<AuthNavKey.UsernamePicker> {
                UsernamePickerScreen(
                    onUsernameEntered = { username ->
                        viewModel.updateProfile(username = username, displayName = username, about = null)
                        backStack.add(AuthNavKey.AppLock)
                    },
                    onSkip = { backStack.add(AuthNavKey.AppLock) },
                    onCheckAvailability = { username -> viewModel.checkUsernameAvailability(username) }
                )
            }

            entry<AuthNavKey.AppLock> {
                AppLockScreen(
                    onVerified = {
                        viewModel.enableBiometric()
                        onAuthComplete()
                    },
                    onDismiss = {}
                )
            }

            entry<AuthNavKey.RestorePrompt> { key ->
                RestorePromptScreen(
                    hasBackup = key.hasBackup,
                    onRestore = { viewModel.restoreFromBackup() },
                    onStartFresh = {
                        while (backStack.size > 1 && backStack.get(backStack.size - 1) !is AuthNavKey.Welcome) {
                            backStack.removeAt(backStack.size - 1)
                        }
                    }
                )
            }
        }
    )
}
