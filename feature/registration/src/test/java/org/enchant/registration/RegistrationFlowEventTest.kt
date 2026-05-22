package org.enchant.registration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("RegistrationFlowEvent")
class RegistrationFlowEventTest {

    @Nested
    @DisplayName("NavigateToScreen")
    inner class NavigateToScreen {
        @Test
        fun `debugDescription contains route class name`() {
            val event = RegistrationFlowEvent.NavigateToScreen(RegistrationNavKey.Welcome)
            assertTrue(event.debugDescription.contains("NavigateToScreen"))
            assertTrue(event.debugDescription.contains("Welcome"))
        }

        @Test
        fun `equals is based on route`() {
            val event1 = RegistrationFlowEvent.NavigateToScreen(RegistrationNavKey.Welcome)
            val event2 = RegistrationFlowEvent.NavigateToScreen(RegistrationNavKey.Welcome)
            val event3 = RegistrationFlowEvent.NavigateToScreen(RegistrationNavKey.PhoneNumberEntry)
            assertEquals(event1, event2)
            assertFalse(event1 == event3)
        }
    }

    @Nested
    @DisplayName("NavigateBack")
    inner class NavigateBack {
        @Test
        fun `debugDescription is NavigateBack`() {
            val event = RegistrationFlowEvent.NavigateBack
            assertEquals("NavigateBack", event.debugDescription)
        }

        @Test
        fun `singleton behavior`() {
            assertEquals(RegistrationFlowEvent.NavigateBack, RegistrationFlowEvent.NavigateBack)
        }
    }

    @Nested
    @DisplayName("ResetState")
    inner class ResetState {
        @Test
        fun `debugDescription is ResetState`() {
            val event = RegistrationFlowEvent.ResetState
            assertEquals("ResetState", event.debugDescription)
        }
    }

    @Nested
    @DisplayName("SessionUpdated")
    inner class SessionUpdated {
        @Test
        fun `debugDescription contains sessionId`() {
            val session = SessionMetadata(sessionId = "abc123", verified = true)
            val event = RegistrationFlowEvent.SessionUpdated(session)
            assertTrue(event.debugDescription.contains("abc123"))
        }

        @Test
        fun `contains session metadata`() {
            val session = SessionMetadata(sessionId = "abc123", verified = true)
            val event = RegistrationFlowEvent.SessionUpdated(session)
            assertEquals(session, event.session)
        }
    }

    @Nested
    @DisplayName("E164Chosen")
    inner class E164Chosen {
        @Test
        fun `debugDescription contains e164`() {
            val event = RegistrationFlowEvent.E164Chosen("+1234567890")
            assertTrue(event.debugDescription.contains("+1234567890"))
        }

        @Test
        fun `contains e164 value`() {
            val event = RegistrationFlowEvent.E164Chosen("+1234567890")
            assertEquals("+1234567890", event.e164)
        }
    }

    @Nested
    @DisplayName("PendingRestoreOptionSelected")
    inner class PendingRestoreOptionSelected {
        @Test
        fun `debugDescription contains option`() {
            val event = RegistrationFlowEvent.PendingRestoreOptionSelected(PendingRestoreOption.LocalBackup)
            assertTrue(event.debugDescription.contains("LocalBackup"))
        }

        @Test
        fun `can be null`() {
            val event = RegistrationFlowEvent.PendingRestoreOptionSelected(null)
            assertEquals(null, event.option)
        }
    }

    @Nested
    @DisplayName("RegistrationComplete")
    inner class RegistrationComplete {
        @Test
        fun `debugDescription is RegistrationComplete`() {
            val event = RegistrationFlowEvent.RegistrationComplete
            assertEquals("RegistrationComplete", event.debugDescription)
        }
    }
}