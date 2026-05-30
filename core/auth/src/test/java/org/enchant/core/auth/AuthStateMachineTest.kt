package org.enchant.core.auth

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("AuthStateMachine — Full Coverage")
class AuthStateMachineTest {

    @Nested @DisplayName("Welcome State Transitions")
    inner class WelcomeTest {
        @Test @DisplayName("TermsAccepted transitions to PhoneEntry")
        fun `terms accepted to phone entry`() {
            val next = AuthStateMachine.applyEvent(RegistrationState.Welcome, RegistrationEvent.TermsAccepted)
            assertTrue(next is RegistrationState.PhoneEntry)
        }

        @Test @DisplayName("other events from Welcome stay in Welcome")
        fun `other events stay in welcome`() {
            val events = listOf(
                RegistrationEvent.PhoneNumberSubmitted,
                RegistrationEvent.OtpCodeEntered("123456"),
                RegistrationEvent.ProfileDataEntered("Name", "About", null),
                RegistrationEvent.UsernameEntered("user"),
                RegistrationEvent.KeysGenerated,
                RegistrationEvent.RegistrationComplete,
                RegistrationEvent.PinCreated("1234"),
                RegistrationEvent.RestoreDecisionMade(true)
            )
            events.forEach { event ->
                val next = AuthStateMachine.applyEvent(RegistrationState.Welcome, event)
                assertTrue(next is RegistrationState.Welcome, "$event should stay in Welcome")
            }
        }
    }

    @Nested @DisplayName("PhoneEntry State Transitions")
    inner class PhoneEntryTest {
        @Test @DisplayName("PhoneNumberSubmitted transitions to Loading")
        fun `phone submitted to loading`() {
            val next = AuthStateMachine.applyEvent(RegistrationState.PhoneEntry, RegistrationEvent.PhoneNumberSubmitted)
            assertTrue(next is RegistrationState.Loading)
        }

        @Test @DisplayName("NavigateToWelcome transitions to Welcome")
        fun `navigate back to welcome`() {
            val next = AuthStateMachine.applyEvent(RegistrationState.PhoneEntry, RegistrationEvent.NavigateToWelcome)
            assertTrue(next is RegistrationState.Welcome)
        }

        @Test @DisplayName("other events stay in PhoneEntry")
        fun `other events stay`() {
            val events = listOf(
                RegistrationEvent.OtpCodeEntered("123456"),
                RegistrationEvent.ProfileDataEntered("Name", "About", null)
            )
            events.forEach { event ->
                val next = AuthStateMachine.applyEvent(RegistrationState.PhoneEntry, event)
                assertTrue(next is RegistrationState.PhoneEntry)
            }
        }
    }

    @Nested @DisplayName("Loading State Transitions")
    inner class LoadingTest {
        @Test @DisplayName("CountryCodeSelected transitions to PhoneEntry")
        fun `country selected to phone entry`() {
            val next = AuthStateMachine.applyEvent(
                RegistrationState.Loading,
                RegistrationEvent.CountryCodeSelected(1, "US", "United States", "\uD83C\uDDFA\uD83C\uDDF8")
            )
            assertTrue(next is RegistrationState.PhoneEntry)
        }

        @Test @DisplayName("other events stay in Loading")
        fun `other events stay in loading`() {
            val next = AuthStateMachine.applyEvent(RegistrationState.Loading, RegistrationEvent.TermsAccepted)
            assertTrue(next is RegistrationState.Loading)
        }
    }

    @Nested @DisplayName("OtpVerification State Transitions")
    inner class OtpVerificationTest {
        private val otpState = RegistrationState.OtpVerification("challenge-1", "+15551234567", System.currentTimeMillis() + 600000)

        @Test @DisplayName("valid OTP (6+ chars) transitions to Loading")
        fun `valid otp to loading`() {
            val next = AuthStateMachine.applyEvent(otpState, RegistrationEvent.OtpCodeEntered("123456"))
            assertTrue(next is RegistrationState.Loading)
        }

        @Test @DisplayName("short OTP (< 6 chars) stays in OtpVerification")
        fun `short otp stays`() {
            val next = AuthStateMachine.applyEvent(otpState, RegistrationEvent.OtpCodeEntered("12345"))
            assertTrue(next is RegistrationState.OtpVerification)
        }

        @Test @DisplayName("ResendOtp transitions to Loading")
        fun `resend to loading`() {
            val next = AuthStateMachine.applyEvent(otpState, RegistrationEvent.ResendOtp)
            assertTrue(next is RegistrationState.Loading)
        }

        @Test @DisplayName("WrongPhoneNumber transitions to PhoneEntry")
        fun `wrong number to phone entry`() {
            val next = AuthStateMachine.applyEvent(otpState, RegistrationEvent.WrongPhoneNumber)
            assertTrue(next is RegistrationState.PhoneEntry)
        }

        @Test @DisplayName("other events stay in OtpVerification")
        fun `other events stay in otp`() {
            val next = AuthStateMachine.applyEvent(otpState, RegistrationEvent.TermsAccepted)
            assertTrue(next is RegistrationState.OtpVerification)
        }
    }

    @Nested @DisplayName("Permissions State Transitions")
    inner class PermissionsTest {
        @Test @DisplayName("PermissionsGranted transitions to ProfileSetup")
        fun `permissions granted to profile`() {
            val next = AuthStateMachine.applyEvent(RegistrationState.Permissions, RegistrationEvent.PermissionsGranted)
            assertTrue(next is RegistrationState.ProfileSetup)
        }

        @Test @DisplayName("other events stay in Permissions")
        fun `other events stay in permissions`() {
            val next = AuthStateMachine.applyEvent(RegistrationState.Permissions, RegistrationEvent.TermsAccepted)
            assertTrue(next is RegistrationState.Permissions)
        }
    }

    @Nested @DisplayName("ProfileSetup State Transitions")
    inner class ProfileSetupTest {
        @Test @DisplayName("non-empty displayName transitions to UsernamePicker")
        fun `profile data to username picker`() {
            val next = AuthStateMachine.applyEvent(
                RegistrationState.ProfileSetup,
                RegistrationEvent.ProfileDataEntered("Alice", "Hello", null)
            )
            assertTrue(next is RegistrationState.UsernamePicker)
        }

        @Test @DisplayName("empty displayName stays in ProfileSetup")
        fun `empty display name stays`() {
            val next = AuthStateMachine.applyEvent(
                RegistrationState.ProfileSetup,
                RegistrationEvent.ProfileDataEntered("", "Hello", null)
            )
            assertTrue(next is RegistrationState.ProfileSetup)
        }

        @Test @DisplayName("blank displayName stays in ProfileSetup")
        fun `blank display name stays`() {
            val next = AuthStateMachine.applyEvent(
                RegistrationState.ProfileSetup,
                RegistrationEvent.ProfileDataEntered("   ", "Hello", null)
            )
            assertTrue(next is RegistrationState.ProfileSetup)
        }
    }

    @Nested @DisplayName("UsernamePicker State Transitions")
    inner class UsernamePickerTest {
        @Test @DisplayName("UsernameEntered transitions to KeyGeneration")
        fun `username entered to key gen`() {
            val next = AuthStateMachine.applyEvent(
                RegistrationState.UsernamePicker,
                RegistrationEvent.UsernameEntered("alice_123")
            )
            assertTrue(next is RegistrationState.KeyGeneration)
        }

        @Test @DisplayName("other events stay in UsernamePicker")
        fun `other events stay in username`() {
            val next = AuthStateMachine.applyEvent(RegistrationState.UsernamePicker, RegistrationEvent.TermsAccepted)
            assertTrue(next is RegistrationState.UsernamePicker)
        }
    }

    @Nested @DisplayName("KeyGeneration State Transitions")
    inner class KeyGenerationTest {
        @Test @DisplayName("KeysGenerated transitions to Complete")
        fun `keys generated to complete`() {
            val next = AuthStateMachine.applyEvent(RegistrationState.KeyGeneration, RegistrationEvent.KeysGenerated)
            assertTrue(next is RegistrationState.Complete)
        }

        @Test @DisplayName("other events stay in KeyGeneration")
        fun `other events stay in key gen`() {
            val next = AuthStateMachine.applyEvent(RegistrationState.KeyGeneration, RegistrationEvent.TermsAccepted)
            assertTrue(next is RegistrationState.KeyGeneration)
        }
    }

    @Nested @DisplayName("PinCreation State Transitions")
    inner class PinCreationTest {
        @Test @DisplayName("PinCreated transitions to Complete")
        fun `pin created to complete`() {
            val next = AuthStateMachine.applyEvent(RegistrationState.PinCreation, RegistrationEvent.PinCreated("1234"))
            assertTrue(next is RegistrationState.Complete)
        }

        @Test @DisplayName("other events stay in PinCreation")
        fun `other events stay in pin`() {
            val next = AuthStateMachine.applyEvent(RegistrationState.PinCreation, RegistrationEvent.TermsAccepted)
            assertTrue(next is RegistrationState.PinCreation)
        }
    }

    @Nested @DisplayName("RestorePrompt State Transitions")
    inner class RestorePromptTest {
        @Test @DisplayName("RestoreDecisionMade(true) transitions to ProfileSetup")
        fun `restore decision to profile`() {
            val next = AuthStateMachine.applyEvent(
                RegistrationState.RestorePrompt(hasBackup = true),
                RegistrationEvent.RestoreDecisionMade(true)
            )
            assertTrue(next is RegistrationState.ProfileSetup)
        }

        @Test @DisplayName("RestoreDecisionMade(false) transitions to ProfileSetup")
        fun `restore decision false to profile`() {
            val next = AuthStateMachine.applyEvent(
                RegistrationState.RestorePrompt(hasBackup = false),
                RegistrationEvent.RestoreDecisionMade(false)
            )
            assertTrue(next is RegistrationState.ProfileSetup)
        }
    }

    @Nested @DisplayName("Complete State")
    inner class CompleteTest {
        @Test @DisplayName("Complete is terminal — all events stay in Complete")
        fun `complete is terminal`() {
            val events = listOf<RegistrationEvent>(
                RegistrationEvent.TermsAccepted,
                RegistrationEvent.PhoneNumberSubmitted,
                RegistrationEvent.OtpCodeEntered("123456"),
                RegistrationEvent.KeysGenerated,
                RegistrationEvent.PinCreated("1234")
            )
            events.forEach { event ->
                val next = AuthStateMachine.applyEvent(RegistrationState.Complete, event)
                assertTrue(next is RegistrationState.Complete, "$event should stay in Complete")
            }
        }
    }

    @Nested @DisplayName("Error State Transitions")
    inner class ErrorTest {
        @Test @DisplayName("ResetState transitions to PhoneEntry")
        fun `reset to phone entry`() {
            val next = AuthStateMachine.applyEvent(
                RegistrationState.Error("something failed"),
                RegistrationEvent.ResetState
            )
            assertTrue(next is RegistrationState.PhoneEntry)
        }

        @Test @DisplayName("other events stay in Error")
        fun `other events stay in error`() {
            val next = AuthStateMachine.applyEvent(
                RegistrationState.Error("failed"),
                RegistrationEvent.TermsAccepted
            )
            assertTrue(next is RegistrationState.Error)
        }
    }

    @Nested @DisplayName("getRequiredPermissions()")
    inner class RequiredPermissionsTest {
        @Test @DisplayName("returns non-empty list")
        fun `returns permissions`() {
            val perms = AuthStateMachine.getRequiredPermissions()
            assertTrue(perms.isNotEmpty())
        }

        @Test @DisplayName("includes CAMERA")
        fun `includes camera`() {
            val perms = AuthStateMachine.getRequiredPermissions()
            assertTrue(perms.contains(android.Manifest.permission.CAMERA))
        }

        @Test @DisplayName("includes RECORD_AUDIO")
        fun `includes audio`() {
            val perms = AuthStateMachine.getRequiredPermissions()
            assertTrue(perms.contains(android.Manifest.permission.RECORD_AUDIO))
        }

        @Test @DisplayName("includes READ_CONTACTS")
        fun `includes contacts`() {
            val perms = AuthStateMachine.getRequiredPermissions()
            assertTrue(perms.contains(android.Manifest.permission.READ_CONTACTS))
        }
    }
}
