package org.enchant.registration

import android.os.Parcelable
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import kotlinx.serialization.Serializable
import org.enchant.core.model.AccountEntropyPool
import org.enchant.core.ui.navigation.LocalResultEventBus
import org.enchant.core.ui.navigation.ResultEffect
import org.enchant.core.ui.navigation.TransitionSpecs
import org.enchant.registration.screens.AccountLockedScreen
import org.enchant.registration.screens.AccountLockedScreenEvents
import org.enchant.registration.screens.ArchiveRestoreSelectionScreen
import org.enchant.registration.screens.ArchiveRestoreSelectionScreenEvents
import org.enchant.registration.screens.CaptchaScreen
import org.enchant.registration.screens.CaptchaScreenEvents
import org.enchant.registration.screens.CountryCodePickerScreen
import org.enchant.registration.screens.CountryCodePickerViewModel
import org.enchant.registration.screens.EnterAepScreen
import org.enchant.registration.screens.EnterAepEvents
import org.enchant.registration.screens.EnterLocalBackupV1PassphraseScreen
import org.enchant.registration.screens.LocalBackupRestoreEvents
import org.enchant.registration.screens.LocalBackupRestoreScreen
import org.enchant.registration.screens.PermissionsScreen
import org.enchant.registration.screens.PhoneNumberEntryViewModel
import org.enchant.registration.screens.PhoneNumberScreen
import org.enchant.registration.screens.PinCreationScreen
import org.enchant.registration.screens.PinCreationScreenEvents
import org.enchant.registration.screens.PinEntryScreen
import org.enchant.registration.screens.PinEntryScreenEvents
import org.enchant.registration.screens.ProfileScreen
import org.enchant.registration.screens.QuickRestoreQrEvents
import org.enchant.registration.screens.QuickRestoreQrScreen
import org.enchant.registration.screens.RemoteBackupRestoreScreen
import org.enchant.registration.screens.RemoteBackupRestoreScreenEvents
import org.enchant.registration.screens.TransferScreen
import org.enchant.registration.screens.TransferScreenEvents
import org.enchant.registration.screens.VerificationCodeScreen
import org.enchant.registration.screens.VerificationCodeViewModel
import org.enchant.registration.screens.WelcomeScreen
import org.enchant.registration.screens.WelcomeScreenEvents
import org.enchant.registration.screens.util.navigateBack
import org.enchant.registration.screens.util.navigateTo
import org.enchant.registration.util.AccountEntropyPoolSerializer

@Serializable
sealed interface RegistrationNavKey : NavKey {

    @Serializable data object Welcome : RegistrationNavKey

    @Serializable data class Permissions(
        val nextRoute: RegistrationNavKey
    ) : RegistrationNavKey

    @Serializable data object PhoneNumberEntry : RegistrationNavKey

    @Serializable data class CountryCodePicker(
        val country: CountryData? = null
    ) : RegistrationNavKey

    @Serializable data object VerificationCodeEntry : RegistrationNavKey

    @Serializable data class Captcha(
        val session: SessionMetadata
    ) : RegistrationNavKey

    @Serializable data object PinEntryForSvrRestore : RegistrationNavKey

    @Serializable data class PinEntryForRegistrationLock(
        val timeRemaining: Long,
        val svrCredentials: SvrCredentials
    ) : RegistrationNavKey

    @Serializable data class PinEntryForSmsBypass(
        val svrCredentials: SvrCredentials
    ) : RegistrationNavKey

    @Serializable data class AccountLocked(
        val timeRemainingMs: Long
    ) : RegistrationNavKey

    @Serializable data object PinCreate : RegistrationNavKey

    @Serializable data class ArchiveRestoreSelection(
        val restoreOptions: List<ArchiveRestoreOption>,
        val isPreRegistration: Boolean
    ) : RegistrationNavKey {
        companion object {
            fun forQuickRestore(hasRemoteBackup: Boolean): ArchiveRestoreSelection =
                ArchiveRestoreSelection(
                    restoreOptions = if (hasRemoteBackup) {
                        listOf(ArchiveRestoreOption.EnchantSecureBackup, ArchiveRestoreOption.DeviceTransfer)
                    } else {
                        listOf(ArchiveRestoreOption.DeviceTransfer, ArchiveRestoreOption.LocalBackup)
                    },
                    isPreRegistration = false
                )

            fun forManualRestore(): ArchiveRestoreSelection =
                ArchiveRestoreSelection(
                    restoreOptions = listOf(
                        ArchiveRestoreOption.LocalBackup,
                        ArchiveRestoreOption.EnchantSecureBackup
                    ),
                    isPreRegistration = true
                )

            fun forPostRegister(): ArchiveRestoreSelection =
                ArchiveRestoreSelection(
                    restoreOptions = ArchiveRestoreOption.entries,
                    isPreRegistration = false
                )
        }
    }

    @Serializable data class LocalBackupRestore(
        val isPreRegistration: Boolean
    ) : RegistrationNavKey

    @Serializable data object EnterLocalBackupV1Passphrase : RegistrationNavKey

    @Serializable data object EnterAepForLocalBackup : RegistrationNavKey

    @Serializable data class EnterAepForRemoteBackupPreRegistration(
        val e164: String
    ) : RegistrationNavKey

    @Serializable data object EnterAepForRemoteBackupPostRegistration : RegistrationNavKey

    @Serializable
    data class RemoteRestore(
        @Serializable(with = AccountEntropyPoolSerializer::class)
        val aep: AccountEntropyPool
    ) : RegistrationNavKey

    @Serializable data object QuickRestoreQrScan : RegistrationNavKey

    @Serializable data object Transfer : RegistrationNavKey

    @Serializable data object Profile : RegistrationNavKey

    @Serializable data object FullyComplete : RegistrationNavKey
}

private const val CAPTCHA_RESULT = "captcha_token"
private const val COUNTRY_CODE_RESULT = "country_code_result"
private const val BACKUP_CREDENTIAL_RESULT = "backup_credential_result"
private const val LOCAL_BACKUP_RESTORE_RESULT = "local_backup_restore_result"

@Composable
fun RegistrationNavHost(
    registrationRepository: Any,
    registrationViewModel: RegistrationViewModel? = null,
    permissionsState: Any? = null,
    modifier: Modifier = Modifier,
    onRegistrationComplete: () -> Unit = {}
) {
    val viewModel: RegistrationViewModel = registrationViewModel ?: viewModel(
        factory = RegistrationViewModel.Factory(registrationRepository)
    )

    val registrationState by viewModel.state.collectAsStateWithLifecycle()

    if (registrationState.isRestoringNavigationState) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    LaunchedEffect(viewModel, backDispatcher) {
        viewModel.finishRequests.collect {
            backDispatcher?.onBackPressed()
        }
    }

    CompositionLocalProvider(
        LocalResultEventBus provides viewModel.resultBus,
        LocalNavigationEventDispatcherOwner provides
            rememberNavigationEventDispatcherOwner(parent = null)
    ) {
        val entryProvider = entryProvider {
            navigationEntries(
                registrationRepository = registrationRepository,
                registrationViewModel = viewModel,
                permissionsState = permissionsState,
                onRegistrationComplete = onRegistrationComplete
            )
        }

        val decorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
            rememberViewModelStoreNavEntryDecorator()
        )

        val entries = rememberDecoratedNavEntries(
            backStack = registrationState.backStack,
            entryDecorators = decorators,
            entryProvider = entryProvider
        )

        NavDisplay(
            entries = entries,
            onBack = { viewModel.onEvent(RegistrationFlowEvent.NavigateBack) },
            modifier = modifier,
            transitionSpec = {
                if (targetState.key is RegistrationNavKey.CountryCodePicker) {
                    TransitionSpecs.VerticalSlide.transitionSpec(this)
                } else {
                    TransitionSpecs.HorizontalSlide.transitionSpec(this)
                }
            },
            popTransitionSpec = {
                when {
                    initialState.key is RegistrationNavKey.CountryCodePicker ->
                        TransitionSpecs.VerticalSlide.popTransitionSpec(this)
                    else ->
                        TransitionSpecs.HorizontalSlide.popTransitionSpec(this)
                }
            },
            predictivePopTransitionSpec = {
                if (initialState.key is RegistrationNavKey.CountryCodePicker) {
                    TransitionSpecs.VerticalSlide.predictivePopTransitionSpec(this, it)
                } else {
                    TransitionSpecs.HorizontalSlide.predictivePopTransitionSpec(this, it)
                }
            }
        )
    }
}

private fun EntryProviderScope<NavKey>.navigationEntries(
    registrationRepository: Any,
    registrationViewModel: RegistrationViewModel,
    permissionsState: Any?,
    onRegistrationComplete: () -> Unit
) {
    val parentEventEmitter: (RegistrationFlowEvent) -> Unit =
        registrationViewModel::onEvent

    entry<RegistrationNavKey.Welcome> {
        val context = LocalContext.current
        WelcomeScreen(
            onEvent = { event ->
                when (event) {
                    WelcomeScreenEvents.Continue ->
                        parentEventEmitter.navigateTo(
                            RegistrationNavKey.Permissions(
                                nextRoute = RegistrationNavKey.PhoneNumberEntry
                            )
                        )
                    WelcomeScreenEvents.LinkDevice ->
                        parentEventEmitter.navigateTo(RegistrationNavKey.QuickRestoreQrScan)
                    WelcomeScreenEvents.HasOldPhone ->
                        parentEventEmitter.navigateTo(RegistrationNavKey.Permissions(
                            nextRoute = RegistrationNavKey.QuickRestoreQrScan
                        ))
                    WelcomeScreenEvents.DoesNotHaveOldPhone -> {
                        parentEventEmitter(RegistrationFlowEvent.PendingRestoreOptionSelected(null))
                        parentEventEmitter.navigateTo(
                            RegistrationNavKey.Permissions(
                                nextRoute = RegistrationNavKey.PhoneNumberEntry
                            )
                        )
                    }
                }
            }
        )
    }

    entry<RegistrationNavKey.Permissions> { key ->
        PermissionsScreen(
            onProceed = { parentEventEmitter.navigateTo(key.nextRoute) }
        )
    }

    entry<RegistrationNavKey.PhoneNumberEntry> {
        val vm: PhoneNumberEntryViewModel = viewModel(
            factory = PhoneNumberEntryViewModel.Factory()
        )
        val state by vm.state.collectAsStateWithLifecycle()

        ResultEffect<String?>(registrationViewModel.resultBus, CAPTCHA_RESULT) { token ->
            if (token != null) vm.onCaptchaCompleted(token)
        }
        ResultEffect<CountryData?>(registrationViewModel.resultBus, COUNTRY_CODE_RESULT) { country ->
            if (country != null) vm.onCountrySelected(country)
        }

        PhoneNumberScreen(state = state, onEvent = vm::onEvent)
    }

    entry<RegistrationNavKey.CountryCodePicker> {
        val vm: CountryCodePickerViewModel = viewModel(
            factory = CountryCodePickerViewModel.Factory()
        )
        val state by vm.state.collectAsStateWithLifecycle()
        CountryCodePickerScreen(state = state, onEvent = vm::onEvent)
    }

    entry<RegistrationNavKey.VerificationCodeEntry> {
        val vm: VerificationCodeViewModel = viewModel(
            factory = VerificationCodeViewModel.Factory()
        )
        val state by vm.state.collectAsStateWithLifecycle()
        VerificationCodeScreen(state = state, onEvent = vm::onEvent)
    }

    entry<RegistrationNavKey.Captcha> {
        CaptchaScreen(
            onEvent = { event ->
                when (event) {
                    is CaptchaScreenEvents.CaptchaCompleted -> {
                        registrationViewModel.resultBus.sendResult(CAPTCHA_RESULT, event.token)
                        parentEventEmitter.navigateBack()
                    }
                    CaptchaScreenEvents.CaptchaCancel -> parentEventEmitter.navigateBack()
                }
            }
        )
    }

    entry<RegistrationNavKey.PinEntryForSvrRestore> {
        PinEntryScreen(
            onEvent = { event ->
                when (event) {
                    is PinEntryScreenEvents.PinEntered ->
                        registrationViewModel.onEvent(
                            RegistrationFlowEvent.MasterKeyRestoredFromSvr(
                                MasterKey(org.enchant.core.crypto.CryptoHelper.sha256(event.pin.toByteArray()))
                            )
                        )
                    PinEntryScreenEvents.Skip -> parentEventEmitter.navigateBack()
                    else -> {}
                }
            }
        )
    }

    entry<RegistrationNavKey.PinEntryForRegistrationLock> { key ->
        PinEntryScreen(
            title = "Registration Lock",
            description = "Enter your PIN to unlock (${key.timeRemaining / 1000}s remaining)",
            onEvent = { event ->
                when (event) {
                    is PinEntryScreenEvents.PinEntered ->
                        parentEventEmitter.navigateTo(RegistrationNavKey.PinCreate)
                    PinEntryScreenEvents.Skip -> parentEventEmitter.navigateBack()
                    else -> {}
                }
            }
        )
    }

    entry<RegistrationNavKey.PinEntryForSmsBypass> {
        PinEntryScreen(
            title = "Enter PIN",
            description = "Enter your PIN to bypass SMS verification",
            onEvent = { event ->
                when (event) {
                    is PinEntryScreenEvents.PinEntered ->
                        parentEventEmitter.navigateTo(RegistrationNavKey.PinCreate)
                    PinEntryScreenEvents.Skip -> parentEventEmitter.navigateBack()
                    else -> {}
                }
            }
        )
    }

    entry<RegistrationNavKey.AccountLocked> { key ->
        val context = LocalContext.current
        AccountLockedScreen(
            timeRemainingMs = key.timeRemainingMs,
            onEvent = { event ->
                when (event) {
                    AccountLockedScreenEvents.Next ->
                        parentEventEmitter.navigateTo(RegistrationNavKey.PhoneNumberEntry)
                    AccountLockedScreenEvents.LearnMore -> {
                        Toast.makeText(context, "Learn more about account locking", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    entry<RegistrationNavKey.PinCreate> {
        val context = LocalContext.current
        PinCreationScreen(
            onEvent = { event ->
                when (event) {
                    is PinCreationScreenEvents.PinSubmitted ->
                        parentEventEmitter.navigateTo(RegistrationNavKey.ArchiveRestoreSelection.forManualRestore())
                    PinCreationScreenEvents.ToggleKeyboard -> {}
                    PinCreationScreenEvents.LearnMore -> {
                        Toast.makeText(context, "Learn more about PINs", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    entry<RegistrationNavKey.ArchiveRestoreSelection> { key ->
        ArchiveRestoreSelectionScreen(
            restoreOptions = key.restoreOptions,
            isPreRegistration = key.isPreRegistration,
            onEvent = { event ->
                when (event) {
                    is ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected -> {
                        when (event.option) {
                            ArchiveRestoreOption.EnchantSecureBackup ->
                                parentEventEmitter.navigateTo(
                                    RegistrationNavKey.EnterAepForRemoteBackupPreRegistration(
                                        e164 = ""
                                    )
                                )
                            ArchiveRestoreOption.LocalBackup ->
                                parentEventEmitter.navigateTo(
                                    RegistrationNavKey.LocalBackupRestore(
                                        isPreRegistration = key.isPreRegistration
                                    )
                                )
                            ArchiveRestoreOption.DeviceTransfer ->
                                parentEventEmitter.navigateTo(RegistrationNavKey.Transfer)
                            ArchiveRestoreOption.None ->
                                parentEventEmitter.navigateTo(RegistrationNavKey.Profile)
                        }
                    }
                    ArchiveRestoreSelectionScreenEvents.Skip ->
                        parentEventEmitter.navigateTo(RegistrationNavKey.Profile)
                    ArchiveRestoreSelectionScreenEvents.ConfirmSkip ->
                        parentEventEmitter.navigateTo(RegistrationNavKey.Profile)
                    ArchiveRestoreSelectionScreenEvents.DismissSkipWarning -> {}
                }
            }
        )
    }

    entry<RegistrationNavKey.LocalBackupRestore> { key ->
        LocalBackupRestoreScreen(
            isPreRegistration = key.isPreRegistration,
            onEvent = { event ->
                when (event) {
                    is LocalBackupRestoreEvents.PickBackupFolder -> {}
                    is LocalBackupRestoreEvents.BackupFolderSelected -> {}
                    is LocalBackupRestoreEvents.BackupSelected -> {}
                    is LocalBackupRestoreEvents.PassphraseSubmitted ->
                        parentEventEmitter.navigateTo(RegistrationNavKey.EnterAepForLocalBackup)
                }
            }
        )
    }

    entry<RegistrationNavKey.EnterLocalBackupV1Passphrase> {
        EnterLocalBackupV1PassphraseScreen(
            onPassphraseEntered = {
                parentEventEmitter.navigateTo(RegistrationNavKey.EnterAepForLocalBackup)
            }
        )
    }

    entry<RegistrationNavKey.EnterAepForLocalBackup> {
        var aepKey by androidx.compose.runtime.mutableStateOf("")
        EnterAepScreen(
            onEvent = { event ->
                when (event) {
                    is EnterAepEvents.BackupKeyChanged -> { aepKey = event.key }
                    EnterAepEvents.Submit -> {
                        parentEventEmitter(
                            RegistrationFlowEvent.UserSuppliedAepSubmitted(
                                AccountEntropyPool(aepKey.ifBlank { "local_backup_entropy" })
                            )
                        )
                    }
                    EnterAepEvents.Cancel -> parentEventEmitter.navigateBack()
                    EnterAepEvents.DismissError -> {}
                }
            }
        )
    }

    entry<RegistrationNavKey.EnterAepForRemoteBackupPreRegistration> { key ->
        var aepKey by androidx.compose.runtime.mutableStateOf("")
        EnterAepScreen(
            e164 = key.e164,
            onEvent = { event ->
                when (event) {
                    is EnterAepEvents.BackupKeyChanged -> { aepKey = event.key }
                    EnterAepEvents.Submit -> {
                        parentEventEmitter(
                            RegistrationFlowEvent.UserSuppliedAepSubmitted(
                                AccountEntropyPool(aepKey.ifBlank { "remote_backup_pre_reg_entropy" })
                            )
                        )
                    }
                    EnterAepEvents.Cancel -> parentEventEmitter.navigateBack()
                    EnterAepEvents.DismissError -> {}
                }
            }
        )
    }

    entry<RegistrationNavKey.EnterAepForRemoteBackupPostRegistration> {
        var aepKey by androidx.compose.runtime.mutableStateOf("")
        EnterAepScreen(
            onEvent = { event ->
                when (event) {
                    is EnterAepEvents.BackupKeyChanged -> { aepKey = event.key }
                    EnterAepEvents.Submit -> {
                        parentEventEmitter(
                            RegistrationFlowEvent.UserSuppliedAepSubmitted(
                                AccountEntropyPool(aepKey.ifBlank { "remote_backup_post_reg_entropy" })
                            )
                        )
                    }
                    EnterAepEvents.Cancel -> parentEventEmitter.navigateBack()
                    EnterAepEvents.DismissError -> {}
                }
            }
        )
    }

    entry<RegistrationNavKey.RemoteRestore> { key ->
        RemoteBackupRestoreScreen(
            aep = key.aep,
            onEvent = { event ->
                when (event) {
                    is RemoteBackupRestoreScreenEvents.BackupRestoreBackup -> {}
                    RemoteBackupRestoreScreenEvents.Retry -> {}
                    RemoteBackupRestoreScreenEvents.Cancel ->
                        parentEventEmitter.navigateTo(RegistrationNavKey.ArchiveRestoreSelection.forPostRegister())
                    RemoteBackupRestoreScreenEvents.DismissError -> {}
                }
            }
        )
    }

    entry<RegistrationNavKey.QuickRestoreQrScan> {
        QuickRestoreQrScreen(
            onEvent = { event ->
                when (event) {
                    QuickRestoreQrEvents.RetryQrCode -> {}
                    QuickRestoreQrEvents.Cancel -> parentEventEmitter.navigateBack()
                    QuickRestoreQrEvents.UseProxy -> {}
                    QuickRestoreQrEvents.DismissError -> {}
                }
            }
        )
    }

    entry<RegistrationNavKey.Transfer> {
        TransferScreen(
            onEvent = { event ->
                when (event) {
                    is TransferScreenEvents.TransferClicked -> {}
                    TransferScreenEvents.ContinueOnOtherDeviceDismiss -> {}
                    TransferScreenEvents.ErrorDialogDismissed -> {}
                    TransferScreenEvents.NavigateBack -> parentEventEmitter.navigateBack()
                }
            }
        )
    }

    entry<RegistrationNavKey.Profile> {
        ProfileScreen(
            onProfileComplete = {
                parentEventEmitter(RegistrationFlowEvent.RegistrationComplete)
            },
            onSkip = {
                parentEventEmitter(RegistrationFlowEvent.RegistrationComplete)
            }
        )
    }

    entry<RegistrationNavKey.FullyComplete> {
        LaunchedEffect(Unit) {
            onRegistrationComplete()
        }
    }
}
