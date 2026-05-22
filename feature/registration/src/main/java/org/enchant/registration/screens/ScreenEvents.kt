package org.enchant.registration.screens

import org.enchant.registration.ArchiveRestoreOption

sealed interface WelcomeScreenEvents {
    data object Continue : WelcomeScreenEvents
    data object LinkDevice : WelcomeScreenEvents
    data object HasOldPhone : WelcomeScreenEvents
    data object DoesNotHaveOldPhone : WelcomeScreenEvents
}

sealed interface CaptchaScreenEvents {
    data class CaptchaCompleted(val token: String) : CaptchaScreenEvents
    data object CaptchaCancel : CaptchaScreenEvents
}

sealed interface PinEntryScreenEvents {
    data class PinEntered(val pin: String) : PinEntryScreenEvents
    data object ToggleKeyboard : PinEntryScreenEvents
    data object NeedHelp : PinEntryScreenEvents
    data object Skip : PinEntryScreenEvents
}

sealed interface AccountLockedScreenEvents {
    data object Next : AccountLockedScreenEvents
    data object LearnMore : AccountLockedScreenEvents
}

sealed interface PinCreationScreenEvents {
    data class PinSubmitted(val pin: String) : PinCreationScreenEvents
    data object ToggleKeyboard : PinCreationScreenEvents
    data object LearnMore : PinCreationScreenEvents
}

sealed interface ArchiveRestoreSelectionScreenEvents {
    data class RestoreOptionSelected(val option: ArchiveRestoreOption) : ArchiveRestoreSelectionScreenEvents
    data object Skip : ArchiveRestoreSelectionScreenEvents
    data object ConfirmSkip : ArchiveRestoreSelectionScreenEvents
    data object DismissSkipWarning : ArchiveRestoreSelectionScreenEvents
}

sealed interface LocalBackupRestoreEvents {
    data object PickBackupFolder : LocalBackupRestoreEvents
    data class BackupFolderSelected(val uri: String) : LocalBackupRestoreEvents
    data class BackupSelected(val backupInfo: Any) : LocalBackupRestoreEvents
    data class PassphraseSubmitted(val passphrase: String) : LocalBackupRestoreEvents
}

sealed interface EnterAepEvents {
    data class BackupKeyChanged(val key: String) : EnterAepEvents
    data object Submit : EnterAepEvents
    data object Cancel : EnterAepEvents
    data object DismissError : EnterAepEvents
}

sealed interface RemoteBackupRestoreScreenEvents {
    data object BackupRestoreBackup : RemoteBackupRestoreScreenEvents
    data object Retry : RemoteBackupRestoreScreenEvents
    data object Cancel : RemoteBackupRestoreScreenEvents
    data object DismissError : RemoteBackupRestoreScreenEvents
}

sealed interface QuickRestoreQrEvents {
    data object RetryQrCode : QuickRestoreQrEvents
    data object Cancel : QuickRestoreQrEvents
    data object UseProxy : QuickRestoreQrEvents
    data object DismissError : QuickRestoreQrEvents
}

sealed interface TransferScreenEvents {
    data object TransferClicked : TransferScreenEvents
    data object ContinueOnOtherDeviceDismiss : TransferScreenEvents
    data object ErrorDialogDismissed : TransferScreenEvents
    data object NavigateBack : TransferScreenEvents
}
