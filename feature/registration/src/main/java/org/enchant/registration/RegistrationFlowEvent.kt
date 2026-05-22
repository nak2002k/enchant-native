package org.enchant.registration

sealed interface RegistrationFlowEvent : DebugLoggable {

    data class NavigateToScreen(val route: RegistrationNavKey) : RegistrationFlowEvent {
        override val debugDescription: String get() = "NavigateToScreen(route=${route::class.simpleName})"
    }

    data object NavigateBack : RegistrationFlowEvent {
        override val debugDescription: String get() = "NavigateBack"
    }

    data object ResetState : RegistrationFlowEvent {
        override val debugDescription: String get() = "ResetState"
    }

    data class SessionUpdated(val session: SessionMetadata) : RegistrationFlowEvent {
        override val debugDescription: String get() = "SessionUpdated(sessionId=${session.sessionId})"
    }

    data class E164Chosen(val e164: String) : RegistrationFlowEvent {
        override val debugDescription: String get() = "E164Chosen(e164=$e164)"
    }

    data class Registered(val aep: String) : RegistrationFlowEvent {
        override val debugDescription: String get() = "Registered"
    }

    data class MasterKeyRestoredFromSvr(val masterKey: MasterKey) : RegistrationFlowEvent {
        override val debugDescription: String get() = "MasterKeyRestoredFromSvr"
    }

    data object RecoveryPasswordInvalid : RegistrationFlowEvent {
        override val debugDescription: String get() = "RecoveryPasswordInvalid"
    }

    data class PendingRestoreOptionSelected(val option: PendingRestoreOption?) : RegistrationFlowEvent {
        override val debugDescription: String get() = "PendingRestoreOptionSelected(option=$option)"
    }

    data class UserSuppliedAepSubmitted(val aep: String) : RegistrationFlowEvent {
        override val debugDescription: String get() = "UserSuppliedAepSubmitted"
    }

    data class UserSuppliedAepVerified(val aep: String) : RegistrationFlowEvent {
        override val debugDescription: String get() = "UserSuppliedAepVerified"
    }

    data object RegistrationComplete : RegistrationFlowEvent {
        override val debugDescription: String get() = "RegistrationComplete"
    }
}
