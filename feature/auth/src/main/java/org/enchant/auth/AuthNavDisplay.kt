package org.enchant.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
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
import org.enchant.core.ui.navigation.TransitionSpecs

@Composable
fun AuthNavDisplay(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey> = rememberNavBackStack(AuthNavKey.Welcome),
    viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    modifier: Modifier = Modifier,
    onAuthComplete: () -> Unit = {}
) {
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
                    onProfileDataEntered = { _, _, _ ->
                        viewModel.updateProfile("", "", null)
                        backStack.add(AuthNavKey.UsernamePicker)
                    }
                )
            }

            entry<AuthNavKey.UsernamePicker> {
                UsernamePickerScreen(
                    onUsernameEntered = { backStack.add(AuthNavKey.AppLock) },
                    onSkip = { backStack.add(AuthNavKey.AppLock) },
                    onCheckAvailability = { true }
                )
            }

            entry<AuthNavKey.AppLock> {
                AppLockScreen(
                    onVerified = onAuthComplete,
                    onDismiss = {}
                )
            }

            entry<AuthNavKey.RestorePrompt> { key ->
                RestorePromptScreen(
                    hasBackup = key.hasBackup,
                    onRestore = { },
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
