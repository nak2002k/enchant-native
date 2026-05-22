package org.enchant.registration.screens.util

import org.enchant.registration.RegistrationFlowEvent
import org.enchant.registration.RegistrationNavKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("EmitterExtensions")
class EmitterExtensionsTest {

    @Test
    fun `navigateTo emits NavigateToScreen event`() {
        var receivedEvent: RegistrationFlowEvent? = null
        val emitter: (RegistrationFlowEvent) -> Unit = { receivedEvent = it }
        val route = RegistrationNavKey.PhoneNumberEntry

        emitter.navigateTo(route)

        assertTrue(receivedEvent is RegistrationFlowEvent.NavigateToScreen)
        val event = receivedEvent as RegistrationFlowEvent.NavigateToScreen
        assertEquals(route, event.route)
    }

    @Test
    fun `navigateBack emits NavigateBack event`() {
        var receivedEvent: RegistrationFlowEvent? = null
        val emitter: (RegistrationFlowEvent) -> Unit = { receivedEvent = it }

        emitter.navigateBack()

        assertEquals(RegistrationFlowEvent.NavigateBack, receivedEvent)
    }

    @Test
    fun `navigateTo works with any RegistrationNavKey subtype`() {
        var receivedEvent: RegistrationFlowEvent? = null
        val emitter: (RegistrationFlowEvent) -> Unit = { receivedEvent = it }

        emitter.navigateTo(RegistrationNavKey.Welcome)
        assertTrue(receivedEvent is RegistrationFlowEvent.NavigateToScreen)

        emitter.navigateTo(RegistrationNavKey.VerificationCodeEntry)
        assertTrue(receivedEvent is RegistrationFlowEvent.NavigateToScreen)
    }
}