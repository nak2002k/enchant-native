package org.enchant.registration.screens.util

import org.enchant.registration.RegistrationFlowEvent
import org.enchant.registration.RegistrationNavKey

fun ((RegistrationFlowEvent) -> Unit).navigateTo(route: RegistrationNavKey) {
    this(RegistrationFlowEvent.NavigateToScreen(route))
}

fun ((RegistrationFlowEvent) -> Unit).navigateBack() {
    this(RegistrationFlowEvent.NavigateBack)
}
