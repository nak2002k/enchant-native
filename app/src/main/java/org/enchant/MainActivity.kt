package org.enchant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.enchant.auth.AuthViewModel
import org.enchant.auth.screens.AppLockScreen
import org.enchant.auth.screens.KeyGenerationScreen
import org.enchant.auth.screens.OtpVerifyScreen
import org.enchant.auth.screens.PermissionsScreen
import org.enchant.auth.screens.PhoneEntryScreen
import org.enchant.auth.screens.ProfileSetupScreen
import org.enchant.auth.screens.RestorePromptScreen
import org.enchant.auth.screens.TwoStepPinScreen
import org.enchant.auth.screens.UsernamePickerScreen
import org.enchant.auth.screens.WelcomeScreen
import org.enchant.chat.ConversationScreen
import org.enchant.chatlist.ConversationListScreen
import org.enchant.chatlist.ConversationListViewModel
import org.enchant.core.auth.AuthManager
import org.enchant.core.auth.AuthState
import org.enchant.core.auth.RegistrationState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsState()

    val startDest = when (authState) {
        is AuthState.Authenticated -> "chat_list"
        else -> "splash"
    }

    NavHost(navController = navController, startDestination = startDest) {
        composable("splash") {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            LaunchedEffect(authState) {
                when (authState) {
                    is AuthState.Authenticated -> {
                        navController.navigate("chat_list") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                    is AuthState.Unauthenticated, is AuthState.Unknown -> {
                        navController.navigate("welcome") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                    else -> {}
                }
            }
        }

        composable("welcome") {
            WelcomeScreen(
                onTermsAccepted = { navController.navigate("phone_entry") },
                onRestore = { navController.navigate("restore_prompt") }
            )
        }

        composable("phone_entry") {
            val state by authViewModel.registrationState.collectAsState()
            PhoneEntryScreen(
                onCountrySelected = {},
                onPhoneNumberChanged = {},
                onPhoneNumberSubmitted = { authViewModel.requestOtp(it) },
                onNavigateBack = { navController.popBackStack() },
                isLoading = state is RegistrationState.Loading,
                errorMessage = (state as? RegistrationState.Error)?.message
            )
            LaunchedEffect(state) {
                when (state) {
                    is RegistrationState.OtpVerification -> {
                        navController.navigate("otp_verify") {
                            popUpTo("phone_entry") { inclusive = true }
                        }
                    }
                    else -> {}
                }
            }
        }

        composable("otp_verify") {
            val state by authViewModel.registrationState.collectAsState()
            val otpState = state as? RegistrationState.OtpVerification
            OtpVerifyScreen(
                identifier = otpState?.identifier ?: "",
                onCodeSubmitted = { authViewModel.verifyOtp(it) },
                onResendCode = { authViewModel.resendOtp() },
                onWrongNumber = { navController.popBackStack() },
                isLoading = state is RegistrationState.Loading,
                errorMessage = (state as? RegistrationState.Error)?.message
            )
            LaunchedEffect(state) {
                when (state) {
                    is RegistrationState.Permissions -> {
                        navController.navigate("permissions") {
                            popUpTo("otp_verify") { inclusive = true }
                        }
                    }
                    else -> {}
                }
            }
        }

        composable("permissions") {
            PermissionsScreen(
                onPermissionsGranted = { navController.navigate("profile_setup") },
                onSkip = { navController.navigate("profile_setup") }
            )
        }

        composable("profile_setup") {
            ProfileSetupScreen(
                onProfileDataEntered = { displayName, about, _ ->
                    AuthManager.updateProfile("user_${System.currentTimeMillis()}", displayName, about)
                    navController.navigate("username_picker")
                }
            )
        }

        composable("username_picker") {
            UsernamePickerScreen(
                onUsernameEntered = { navController.navigate("key_generation") },
                onSkip = { navController.navigate("key_generation") },
                onCheckAvailability = { prefix ->
                    try {
                        AuthManager.searchUsername(prefix).getOrNull()?.let { it.isEmpty() } ?: false
                    } catch (_: Exception) { false }
                }
            )
        }

        composable("key_generation") {
            val state by authViewModel.registrationState.collectAsState()
            KeyGenerationScreen(
                onKeysGenerated = {
                    navController.navigate("chat_list") {
                        popUpTo("welcome") { inclusive = true }
                    }
                },
                onRetry = { authViewModel.registerKeys() },
                progress = if (state is RegistrationState.KeyGeneration) 1f else 0.5f,
                isError = state is RegistrationState.Error,
                errorMessage = (state as? RegistrationState.Error)?.message
            )
            LaunchedEffect(Unit) {
                authViewModel.registerKeys()
            }
        }

        composable("pin_creation") {
            TwoStepPinScreen(
                onPinCreated = {
                    navController.navigate("chat_list") {
                        popUpTo("welcome") { inclusive = true }
                    }
                }
            )
        }

        composable("restore_prompt") {
            RestorePromptScreen(
                hasBackup = false,
                onRestore = {},
                onStartFresh = { navController.navigate("profile_setup") }
            )
        }

        composable("app_lock") {
            AppLockScreen(
                onPinSet = {},
                onBiometricAuthenticate = {},
                isBiometricAvailable = false
            )
        }

        composable("chat_list") {
            val listViewModel: ConversationListViewModel = viewModel()
            ConversationListScreen(
                viewModel = listViewModel,
                onConversationClick = { convId ->
                    navController.navigate("conversation/$convId")
                },
                onNewChat = { },
                onNewGroup = { }
            )
        }

        composable("conversation/{conversationId}") { backStackEntry ->
            val convId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            ConversationScreen(
                conversationId = convId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
