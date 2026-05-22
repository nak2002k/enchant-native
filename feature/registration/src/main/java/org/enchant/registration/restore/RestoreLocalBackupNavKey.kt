package org.enchant.registration.restore

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface RestoreNavKey : NavKey {
    @Serializable data object SelectRestoreType : RestoreNavKey
    @Serializable data object FolderInstructionSheet : RestoreNavKey
    @Serializable data object FileInstructionSheet : RestoreNavKey
    @Serializable data object SelectBackup : RestoreNavKey
    @Serializable data object SelectBackupSheet : RestoreNavKey
    @Serializable data object EnterBackupKey : RestoreNavKey
    @Serializable data object NoRecoveryKeySheet : RestoreNavKey
}
