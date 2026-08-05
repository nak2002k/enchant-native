package org.enchant.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
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
import org.enchant.auth.screens.hashPinArgon2
import org.enchant.core.auth.AuthManager
import org.enchant.core.auth.RegistrationState
import org.enchant.core.base.SecurePreferences
import org.enchant.core.ui.navigation.TransitionSpecs
import java.util.function.Consumer

@Composable
fun AuthNavDisplay(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey> = rememberNavBackStack(AuthNavKey.Welcome),
    viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    modifier: Modifier = Modifier,
    onAuthComplete: () -> Unit = {}
) {
    val registrationState by viewModel.registrationState.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(registrationState) {
        val state = registrationState
        when (state) {
            is RegistrationState.OtpVerification -> {
                if (backStack.lastOrNull() !is AuthNavKey.OtpVerify) {
                    scope.launch { backStack.add(AuthNavKey.OtpVerify(identifier = state.identifier)) }
                }
            }
            is RegistrationState.ProfileSetup,
            is RegistrationState.Permissions -> {
                if (backStack.lastOrNull() !is AuthNavKey.ProfileSetup) {
                    scope.launch { backStack.add(AuthNavKey.ProfileSetup) }
                }
            }
            is RegistrationState.UsernamePicker -> {
                if (backStack.lastOrNull() !is AuthNavKey.UsernamePicker) {
                    scope.launch { backStack.add(AuthNavKey.UsernamePicker) }
                }
            }
            is RegistrationState.KeyGeneration -> {
                if (backStack.lastOrNull() !is AuthNavKey.KeyGeneration) {
                    scope.launch { backStack.add(AuthNavKey.KeyGeneration) }
                }
            }
            is RegistrationState.PinCreation -> {
                if (backStack.lastOrNull() !is AuthNavKey.TwoStepPin) {
                    scope.launch { backStack.add(AuthNavKey.TwoStepPin) }
                }
            }
            is RegistrationState.Complete -> {
                if (backStack.lastOrNull() !is AuthNavKey.AppLock) {
                    scope.launch { backStack.add(AuthNavKey.AppLock) }
                }
            }
            is RegistrationState.Error -> {}
            is RegistrationState.Loading -> {}
            else -> {}
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
                val acceptTerms: () -> Unit = {
                    scope.launch {
                        backStack.add(AuthNavKey.Permissions(nextRoute = AuthNavKey.PhoneEntry))
                    }
                }
                val restore: () -> Unit = {
                    scope.launch { backStack.add(AuthNavKey.RestorePrompt()) }
                }
                val skipToPhone: () -> Unit = {
                    scope.launch {
                        if (backStack.none { it is AuthNavKey.PhoneEntry }) {
                            backStack.add(AuthNavKey.PhoneEntry)
                        }
                    }
                }
                DisposableEffect(Unit) {
                    agentBindWelcome(acceptTerms, restore, skipToPhone)
                    onDispose { }
                }
                WelcomeScreen(
                    onTermsAccepted = acceptTerms,
                    onRestore = restore
                )
            }

            entry<AuthNavKey.Permissions> { key ->
                val onContinue: () -> Unit = {
                    scope.launch { backStack.add(key.nextRoute) }
                }
                val onSkip: () -> Unit = {
                    scope.launch { backStack.add(key.nextRoute) }
                }
                PermissionsScreen(
                    onPermissionsGranted = onContinue,
                    onSkip = onSkip,
                    registerAgentActions = { grant, _ ->
                        val goPhone: () -> Unit = { scope.launch { backStack.add(key.nextRoute) } }
                        agentBindPermissions(
                            { scope.launch { grant() } },
                            goPhone
                        )
                    }
                )
            }

            entry<AuthNavKey.PhoneEntry> {
                val isLoading = registrationState is RegistrationState.Loading
                val errorMessage = (registrationState as? RegistrationState.Error)?.message
                DisposableEffect(Unit) {
                    agentRegisterPhone { phone ->
                        scope.launch { viewModel.requestOtp(phone) }
                    }
                    onDispose { }
                }
                PhoneEntryScreen(
                    onCountrySelected = { backStack.add(AuthNavKey.CountryCodePicker) },
                    onPhoneNumberChanged = {},
                    onPhoneNumberSubmitted = { phone -> viewModel.requestOtp(phone) },
                    onNavigateBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    isLoading = isLoading,
                    errorMessage = errorMessage
                )
            }

            entry<AuthNavKey.CountryCodePicker> {
                CountryCodePickerScreen(
                    onCountrySelected = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    onDismiss = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                )
            }

            entry<AuthNavKey.OtpVerify> { key ->
                val isLoading = registrationState is RegistrationState.Loading
                val errorMessage = (registrationState as? RegistrationState.Error)?.message
                val resend = { viewModel.requestOtp(key.identifier) }
                DisposableEffect(key.identifier) {
                    agentRegisterOtp(
                        identifier = key.identifier,
                        onSubmit = { otp -> scope.launch { viewModel.verifyOtp(otp) } },
                        onResend = { scope.launch { viewModel.requestOtp(key.identifier) } }
                    )
                    onDispose { }
                }
                OtpVerifyScreen(
                    identifier = key.identifier,
                    onCodeSubmitted = { viewModel.verifyOtp(it) },
                    onResendCode = resend,
                    onWrongNumber = {
                        while (backStack.size > 1 && backStack.get(backStack.size - 1) !is AuthNavKey.PhoneEntry) {
                            backStack.removeAt(backStack.size - 1)
                        }
                    },
                    isLoading = isLoading,
                    errorMessage = errorMessage
                )
            }

            entry<AuthNavKey.KeyGeneration> {
                val isGenerating = registrationState is RegistrationState.KeyGeneration
                val isError = registrationState is RegistrationState.Error
                val errorMessage = (registrationState as? RegistrationState.Error)?.message
                val retry = { viewModel.registerKeys() }
                LaunchedEffect(registrationState) {
                    if (registrationState is RegistrationState.KeyGeneration) {
                        viewModel.registerKeys()
                    }
                }
                DisposableEffect(Unit) {
                    agentBindKeyGeneration(retry)
                    onDispose { }
                }
                KeyGenerationScreen(
                    onKeysGenerated = { },
                    onRetry = retry,
                    progress = when {
                        isError -> 0f
                        isGenerating -> 0.5f
                        registrationState is RegistrationState.PinCreation -> 1f
                        registrationState is RegistrationState.Complete -> 1f
                        else -> 0f
                    },
                    isError = isError,
                    errorMessage = errorMessage
                )
            }

            entry<AuthNavKey.TwoStepPin> {
                val completePin: (String) -> Unit = { AuthManager.completeRegistration() }
                val skipPin: () -> Unit = { AuthManager.completeRegistration() }
                DisposableEffect(Unit) {
                    agentBindTwoStepPin(skipPin)
                    onDispose { }
                }
                TwoStepPinScreen.Screen(onPinCreated = completePin)
            }

            entry<AuthNavKey.ProfileSetup> {
                DisposableEffect(Unit) {
                    agentBindProfileSetup()
                    onDispose { }
                }
                ProfileSetupScreen(
                    onProfileDataEntered = { displayName, about, _ ->
                        AuthManager.setPendingProfile(displayName, about)
                        backStack.add(AuthNavKey.UsernamePicker)
                    }
                )
            }

            entry<AuthNavKey.UsernamePicker> {
                val skip: () -> Unit = {
                    scope.launch { backStack.add(AuthNavKey.AppLock) }
                }
                DisposableEffect(Unit) {
                    agentBindUsernamePicker(skip)
                    onDispose { }
                }
                UsernamePickerScreen(
                    onUsernameEntered = { username ->
                        val (displayName, about) = AuthManager.getPendingProfile()
                        viewModel.updateProfile(username = username, displayName = displayName, about = about)
                        backStack.add(AuthNavKey.KeyGeneration)
                    },
                    onSkip = skip,
                    onCheckAvailability = { username -> viewModel.checkUsernameAvailability(username) }
                )
            }

            entry<AuthNavKey.AppLock> {
                val verified = {
                    viewModel.enableBiometric()
                    onAuthComplete()
                }
                DisposableEffect(Unit) {
                    agentRegisterAppLock { pin ->
                        scope.launch {
                            if (pin != null) {
                                SecurePreferences.putString("applock.pin_hash", hashPinArgon2(pin))
                                SecurePreferences.putBoolean("applock.enabled", true)
                            }
                            verified()
                        }
                    }
                    onDispose { }
                }
                AppLockScreen(onVerified = verified, onDismiss = {})
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

private fun agentBindWelcome(onAccept: () -> Unit, onRestore: () -> Unit, onSkipToPhone: () -> Unit) {
    runCatching {
        Class.forName("org.enchant.agent.AuthScreenAgent")
            .getMethod("bindWelcome", Runnable::class.java, Runnable::class.java, Runnable::class.java)
            .invoke(null, Runnable(onAccept), Runnable(onRestore), Runnable(onSkipToPhone))
    }
}

private fun agentBindPermissions(onContinue: () -> Unit, onSkip: () -> Unit) {
    agentCall("bindPermissions", onContinue, onSkip)
}

private fun agentRegisterPhone(onSubmit: (String) -> Unit) {
    runCatching {
        Class.forName("org.enchant.agent.AuthBindBridge")
            .getMethod("registerPhone", Consumer::class.java)
            .invoke(null, Consumer(onSubmit))
    }
}

private fun agentRegisterOtp(identifier: String, onSubmit: (String) -> Unit, onResend: () -> Unit) {
    runCatching {
        Class.forName("org.enchant.agent.AuthBindBridge")
            .getMethod("registerOtp", String::class.java, Consumer::class.java, Runnable::class.java)
            .invoke(null, identifier, Consumer(onSubmit), Runnable(onResend))
    }
}

private fun agentRegisterAppLock(onComplete: (String?) -> Unit) {
    runCatching {
        Class.forName("org.enchant.agent.AuthBindBridge")
            .getMethod("registerAppLock", Consumer::class.java)
            .invoke(null, Consumer(onComplete))
    }
}

private fun agentBindProfileSetup() = agentCall0("bindProfileSetup")
private fun agentBindUsernamePicker(onSkip: () -> Unit) = agentCall1("bindUsernamePicker", onSkip)
private fun agentBindKeyGeneration(onRetry: () -> Unit) = agentCall1("bindKeyGeneration", onRetry)
private fun agentBindTwoStepPin(onComplete: () -> Unit) = agentCall1("bindTwoStepPin", onComplete)

private fun agentCall0(method: String) {
    runCatching {
        Class.forName("org.enchant.agent.AuthScreenAgent")
            .getMethod(method)
            .invoke(null)
    }
}

private fun agentCall1(method: String, action: () -> Unit) {
    runCatching {
        Class.forName("org.enchant.agent.AuthScreenAgent")
            .getMethod(method, Runnable::class.java)
            .invoke(null, Runnable(action))
    }
}

private fun agentCall(method: String, a: () -> Unit, b: () -> Unit) {
    runCatching {
        Class.forName("org.enchant.agent.AuthScreenAgent")
            .getMethod(method, Runnable::class.java, Runnable::class.java)
            .invoke(null, Runnable(a), Runnable(b))
    }
}
