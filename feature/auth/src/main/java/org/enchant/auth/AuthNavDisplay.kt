package org.enchant.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
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
import org.enchant.core.ui.navigation.LocalResultEventBus
import org.enchant.core.ui.navigation.ResultEventBus
import org.enchant.core.ui.navigation.TransitionSpecs

@Composable
fun AuthNavDisplay(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey> = rememberNavBackStack(AuthNavKey.Welcome),
    viewModel: AuthViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onAuthComplete: () -> Unit = {}
) {
    val resultBus = remember { ResultEventBus() }

    CompositionLocalProvider(
        LocalResultEventBus provides resultBus,
        LocalNavigationEventDispatcherOwner provides
            rememberNavigationEventDispatcherOwner(parent = null)
    ) {
        val entryProvider = entryProvider {
            navigationEntries(
                backStack = backStack,
                viewModel = viewModel,
                onAuthComplete = onAuthComplete
            )
        }

        val decorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
            rememberViewModelStoreNavEntryDecorator()
        )

        val entries = rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = decorators,
            entryProvider = entryProvider
        )

        NavDisplay(
            entries = entries,
            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
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
            }
        )
    }
}

private fun EntryProviderScope<NavKey>.navigationEntries(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey>,
    viewModel: AuthViewModel,
    onAuthComplete: () -> Unit
) {
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
            onRestore = { /* Phase 3: wire restore flow here */ },
            onStartFresh = {
                while (backStack.size > 1 && backStack.get(backStack.size - 1) !is AuthNavKey.Welcome) {
                    backStack.removeAt(backStack.size - 1)
                }
            }
        )
    }
}
