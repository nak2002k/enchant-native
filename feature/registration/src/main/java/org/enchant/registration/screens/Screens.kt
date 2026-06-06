package org.enchant.registration.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.enchant.core.model.AccountEntropyPool
import org.enchant.registration.ArchiveRestoreOption

@Composable
fun WelcomeScreen(
    onEvent: (WelcomeScreenEvents) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Enchant",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Private, end-to-end encrypted messaging",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(64.dp))
            Button(
                onClick = { onEvent(WelcomeScreenEvents.Continue) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Agree & Continue")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { onEvent(WelcomeScreenEvents.LinkDevice) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Link This Device")
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "By continuing, you agree to our Terms of Service and Privacy Policy.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PhoneNumberScreen(
    state: PhoneNumberEntryState,
    onEvent: (PhoneNumberEntryEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Enter your phone number",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "We'll send you a verification code",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = state.phoneNumber,
                onValueChange = { onEvent(PhoneNumberEntryEvent.PhoneNumberChanged(it)) },
                label = { Text("Phone number") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            )

            state.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onEvent(PhoneNumberEntryEvent.Submit) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = state.phoneNumber.isNotBlank() && !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Continue")
                }
            }
        }
    }
}

@Composable
fun PermissionsScreen(
    onProceed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Allow Access",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Enchant needs access to your contacts to message people you know.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            PermissionItem(
                title = "Contacts",
                description = "Find friends who use Enchant"
            )
            Spacer(modifier = Modifier.height(16.dp))
            PermissionItem(
                title = "Notifications",
                description = "Get notified of new messages"
            )
            Spacer(modifier = Modifier.height(16.dp))
            PermissionItem(
                title = "Media",
                description = "Send photos, videos, and files"
            )

            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onProceed,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Continue")
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onProceed) {
                Text("Skip for now")
            }
        }
    }
}

@Composable
private fun PermissionItem(title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CountryCodePickerScreen(
    state: CountryCodePickerState,
    onEvent: (CountryCodePickerEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Select Country",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = { onEvent(CountryCodePickerEvent.SearchQueryChanged(it)) },
                label = { Text("Search countries") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            val filtered = state.countries.filter {
                state.query.isEmpty() ||
                    it.name.contains(state.query, ignoreCase = true) ||
                    it.code.contains(state.query, ignoreCase = true)
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filtered) { country ->
                    ListItem(
                        headlineContent = { Text(country.name) },
                        trailingContent = { Text("+${country.dialCode}") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ListItemDefaults.colors(
                            containerColor = if (country == state.selectedCountry)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun VerificationCodeScreen(
    state: VerificationCodeState,
    onEvent: (VerificationCodeEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Verify your number",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter the 6-digit code we sent you",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = state.code,
                onValueChange = {
                    if (it.length <= 6) onEvent(VerificationCodeEvent.CodeChanged(it))
                },
                label = { Text("Verification code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            )

            state.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row {
                TextButton(onClick = { onEvent(VerificationCodeEvent.ResendCode) }) {
                    Text("Resend code")
                }
                Spacer(modifier = Modifier.width(16.dp))
                TextButton(onClick = { onEvent(VerificationCodeEvent.CallMe) }) {
                    Text("Call me")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onEvent(VerificationCodeEvent.Submit) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = state.code.length == 6 && !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Verify")
                }
            }
        }
    }
}

@Composable
fun CaptchaScreen(
    onEvent: (CaptchaScreenEvents) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Verifying you're human...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun PinEntryScreen(
    title: String = "Enter PIN",
    description: String = "Enter your PIN",
    onEvent: (PinEntryScreenEvents) -> Unit,
    modifier: Modifier = Modifier
) {
    var pin by remember { mutableStateOf("") }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 4) pin = it },
                label = { Text("PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row {
                TextButton(onClick = { onEvent(PinEntryScreenEvents.NeedHelp) }) {
                    Text("Need help?")
                }
                Spacer(modifier = Modifier.width(16.dp))
                TextButton(onClick = { onEvent(PinEntryScreenEvents.Skip) }) {
                    Text("Skip")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onEvent(PinEntryScreenEvents.PinEntered(pin)) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = pin.length == 4
            ) {
                Text("Continue")
            }
        }
    }
}

@Composable
fun AccountLockedScreen(
    timeRemainingMs: Long,
    onEvent: (AccountLockedScreenEvents) -> Unit,
    modifier: Modifier = Modifier
) {
    val minutes = (timeRemainingMs / 60000).toInt()
    val seconds = ((timeRemainingMs % 60000) / 1000).toInt()

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Account Locked",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Too many failed attempts. Try again in $minutes:${String.format("%02d", seconds)}",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { onEvent(AccountLockedScreenEvents.Next) }) {
                Text("OK")
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = { onEvent(AccountLockedScreenEvents.LearnMore) }) {
                Text("Learn more")
            }
        }
    }
}

@Composable
fun PinCreationScreen(
    onEvent: (PinCreationScreenEvents) -> Unit,
    modifier: Modifier = Modifier
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Create a PIN",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your PIN will be required when you register a new device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 4) pin = it },
                label = { Text("PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { if (it.length <= 4) confirmPin = it },
                label = { Text("Confirm PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = confirmPin.isNotEmpty() && pin != confirmPin
            )
            if (confirmPin.isNotEmpty() && pin != confirmPin) {
                Text(
                    text = "PINs don't match",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { onEvent(PinCreationScreenEvents.LearnMore) }) {
                Text("Learn more about PINs")
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onEvent(PinCreationScreenEvents.PinSubmitted(pin)) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = pin.length == 4 && pin == confirmPin
            ) {
                Text("Create PIN")
            }
        }
    }
}

@Composable
fun ArchiveRestoreSelectionScreen(
    restoreOptions: List<ArchiveRestoreOption>,
    isPreRegistration: Boolean,
    onEvent: (ArchiveRestoreSelectionScreenEvents) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Restore account",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose how you'd like to restore your account",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            restoreOptions.forEach { option ->
                val (title, description) = when (option) {
                    ArchiveRestoreOption.EnchantSecureBackup -> "Enchant Secure Backup" to "Restore from cloud backup"
                    ArchiveRestoreOption.DeviceTransfer -> "Transfer from Old Device" to "Move data from your old phone"
                    ArchiveRestoreOption.LocalBackup -> "Local Backup" to "Restore from a file on this device"
                    ArchiveRestoreOption.None -> "" to ""
                }
                if (title.isNotEmpty()) {
                    OutlinedCard(
                        onClick = {
                            onEvent(ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected(option))
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    ) {
                        ListItem(
                            headlineContent = { Text(title) },
                            supportingContent = { Text(description) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (!isPreRegistration) {
                TextButton(onClick = { onEvent(ArchiveRestoreSelectionScreenEvents.Skip) }) {
                    Text("Skip")
                }
            }
        }
    }
}

@Composable
fun LocalBackupRestoreScreen(
    isPreRegistration: Boolean,
    onEvent: (LocalBackupRestoreEvents) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Local Backup",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onEvent(LocalBackupRestoreEvents.PickBackupFolder) }) {
                Text("Select Backup File")
            }
        }
    }
}

@Composable
fun EnterLocalBackupV1PassphraseScreen(
    onPassphraseEntered: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var passphrase by remember { mutableStateOf("") }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Backup Passphrase",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text("Passphrase") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onPassphraseEntered(passphrase) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = passphrase.isNotBlank()
            ) {
                Text("Restore")
            }
        }
    }
}

@Composable
fun EnterAepScreen(
    e164: String = "",
    onEvent: (EnterAepEvents) -> Unit,
    modifier: Modifier = Modifier
) {
    var key by remember { mutableStateOf("") }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Enter Backup Key",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    onEvent(EnterAepEvents.BackupKeyChanged(it))
                },
                label = { Text("64-character backup key") },
                modifier = Modifier.fillMaxWidth(),
                isError = key.isNotEmpty() && key.length != 64
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onEvent(EnterAepEvents.Submit) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = key.length == 64
            ) {
                Text("Verify Key")
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = { onEvent(EnterAepEvents.Cancel) }) {
                Text("Cancel")
            }
        }
    }
}

@Composable
fun RemoteBackupRestoreScreen(
    aep: AccountEntropyPool,
    onEvent: (RemoteBackupRestoreScreenEvents) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Restore from Cloud",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onEvent(RemoteBackupRestoreScreenEvents.BackupRestoreBackup) }) {
                Text("Restore Backup")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { onEvent(RemoteBackupRestoreScreenEvents.Cancel) }) {
                Text("Cancel")
            }
        }
    }
}

@Composable
fun QuickRestoreQrScreen(
    onEvent: (QuickRestoreQrEvents) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Scan QR Code",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Scan the QR code on your old device to restore your account",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Surface(
                modifier = Modifier.size(200.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("QR Code", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row {
                TextButton(onClick = { onEvent(QuickRestoreQrEvents.RetryQrCode) }) {
                    Text("Retry")
                }
                Spacer(modifier = Modifier.width(16.dp))
                TextButton(onClick = { onEvent(QuickRestoreQrEvents.Cancel) }) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun TransferScreen(
    onEvent: (TransferScreenEvents) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Transfer Account",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Transfer your account data from your old device to this one.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { onEvent(TransferScreenEvents.TransferClicked) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Start Transfer")
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = { onEvent(TransferScreenEvents.NavigateBack) }) {
                Text("Go Back")
            }
        }
    }
}

@Composable
fun ProfileScreen(
    onProfileComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Set up your profile",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tell people who you are",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            Surface(
                modifier = Modifier.size(80.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = displayName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.lowercase().replace("[^a-z0-9_]".toRegex(), "") },
                label = { Text("Username") },
                prefix = { Text("@") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onProfileComplete,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = displayName.isNotBlank()
            ) {
                Text("Continue")
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onSkip) {
                Text("Skip for now")
            }
        }
    }
}
