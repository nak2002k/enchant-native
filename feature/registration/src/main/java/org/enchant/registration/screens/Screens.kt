package org.enchant.registration.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.enchant.core.model.AccountEntropyPool
import org.enchant.registration.ArchiveRestoreOption

@Composable
fun PhoneNumberScreen(
    state: PhoneNumberEntryState,
    onEvent: (PhoneNumberEntryEvent) -> Unit,
    modifier: Modifier = Modifier
) {
}

@Composable
fun WelcomeScreen(
    onEvent: (WelcomeScreenEvents) -> Unit,
    modifier: Modifier = Modifier
) {
}

@Composable
fun PermissionsScreen(
    onProceed: () -> Unit,
    modifier: Modifier = Modifier
) {
}

@Composable
fun CountryCodePickerScreen(
    state: CountryCodePickerState,
    onEvent: (CountryCodePickerEvent) -> Unit,
    modifier: Modifier = Modifier
) {
}

@Composable
fun VerificationCodeScreen(
    state: VerificationCodeState,
    onEvent: (VerificationCodeEvent) -> Unit,
    modifier: Modifier = Modifier
) {
}

@Composable
fun CaptchaScreen(
    onEvent: (CaptchaScreenEvents) -> Unit,
    modifier: Modifier = Modifier
) {
}

@Composable
fun PinEntryScreen(
    title: String = "Enter PIN",
    description: String = "Enter your PIN",
    onEvent: (PinEntryScreenEvents) -> Unit,
    modifier: Modifier = Modifier
) {
}

@Composable
fun AccountLockedScreen(
    timeRemainingMs: Long,
    onEvent: (AccountLockedScreenEvents) -> Unit,
    modifier: Modifier = Modifier
) {
}

@Composable
fun PinCreationScreen(
    onEvent: (PinCreationScreenEvents) -> Unit,
    modifier: Modifier = Modifier
) {
}

@Composable
fun ArchiveRestoreSelectionScreen(
    restoreOptions: List<ArchiveRestoreOption>,
    isPreRegistration: Boolean,
    onEvent: (ArchiveRestoreSelectionScreenEvents) -> Unit,
    modifier: Modifier = Modifier
) {
}

@Composable
fun LocalBackupRestoreScreen(
    isPreRegistration: Boolean,
    onEvent: (LocalBackupRestoreEvents) -> Unit,
    modifier: Modifier = Modifier
) {
}

@Composable
fun EnterLocalBackupV1PassphraseScreen(
    onPassphraseEntered: (String) -> Unit,
    modifier: Modifier = Modifier
) {
}

@Composable
fun EnterAepScreen(
    e164: String = "",
    onEvent: (EnterAepEvents) -> Unit,
    modifier: Modifier = Modifier
) {
}

@Composable
fun RemoteBackupRestoreScreen(
    aep: AccountEntropyPool,
    onEvent: (RemoteBackupRestoreScreenEvents) -> Unit,
    modifier: Modifier = Modifier
) {
}

@Composable
fun QuickRestoreQrScreen(
    onEvent: (QuickRestoreQrEvents) -> Unit,
    modifier: Modifier = Modifier
) {
}

@Composable
fun TransferScreen(
    onEvent: (TransferScreenEvents) -> Unit,
    modifier: Modifier = Modifier
) {
}

@Composable
fun ProfileScreen(
    onProfileComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
}
