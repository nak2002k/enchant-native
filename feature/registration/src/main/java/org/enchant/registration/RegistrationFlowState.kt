package org.enchant.registration

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.enchant.core.model.AccountEntropyPool

data class RegistrationFlowState(
    val isRestoringNavigationState: Boolean = true,
    val backStack: NavBackStack<NavKey> = NavBackStack(
        RegistrationNavKey.Welcome
    ),
    val sessionMetadata: SessionMetadata? = null,
    val sessionE164: String? = null,
    val accountEntropyPool: AccountEntropyPool? = null,
    val temporaryMasterKey: MasterKey? = null,
    val doNotAttemptRecoveryPassword: Boolean = false,
    val pendingRestoreOption: PendingRestoreOption? = null,
    val unverifiedRestoredAep: AccountEntropyPool? = null,
    val finishRequests: SharedFlow<Unit> = MutableSharedFlow()
)
