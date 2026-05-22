package org.enchant.auth

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("AuthNavBackStackExtensions")
class AuthNavBackStackExtensionsTest {

    @Nested
    @DisplayName("goToPhoneEntry")
    inner class GoToPhoneEntry {

        @Test
        fun `adds PhoneEntry to empty stack`() {
            val stack = NavBackStack<NavKey>()
            stack.goToPhoneEntry()
            assertEquals(1, stack.size)
            assertTrue(stack.get(0) is AuthNavKey.PhoneEntry)
        }

        @Test
        fun `adds PhoneEntry on top of existing stack`() {
            val stack = NavBackStack<NavKey>()
            stack.add(AuthNavKey.Welcome)
            stack.goToPhoneEntry()
            assertEquals(2, stack.size)
            assertTrue(stack.get(1) is AuthNavKey.PhoneEntry)
        }
    }

    @Nested
    @DisplayName("goToOtpVerify")
    inner class GoToOtpVerify {

        @Test
        fun `adds OtpVerify with identifier to empty stack`() {
            val stack = NavBackStack<NavKey>()
            stack.goToOtpVerify("+15551234567")
            assertEquals(1, stack.size)
            val key = stack.get(0) as AuthNavKey.OtpVerify
            assertEquals("+15551234567", key.identifier)
        }

        @Test
        fun `adds OtpVerify with empty identifier`() {
            val stack = NavBackStack<NavKey>()
            stack.goToOtpVerify("")
            assertEquals(1, stack.size)
            val key = stack.get(0) as AuthNavKey.OtpVerify
            assertEquals("", key.identifier)
        }

        @Test
        fun `adds OtpVerify with special characters in identifier`() {
            val stack = NavBackStack<NavKey>()
            stack.goToOtpVerify("+1 (555) 123-4567")
            assertEquals(1, stack.size)
            val key = stack.get(0) as AuthNavKey.OtpVerify
            assertEquals("+1 (555) 123-4567", key.identifier)
        }
    }

    @Nested
    @DisplayName("goToKeyGeneration")
    inner class GoToKeyGeneration {

        @Test
        fun `adds KeyGeneration to empty stack`() {
            val stack = NavBackStack<NavKey>()
            stack.goToKeyGeneration()
            assertEquals(1, stack.size)
            assertTrue(stack.get(0) is AuthNavKey.KeyGeneration)
        }

        @Test
        fun `adds KeyGeneration on top of existing stack`() {
            val stack = NavBackStack<NavKey>()
            stack.add(AuthNavKey.Welcome)
            stack.goToKeyGeneration()
            assertEquals(2, stack.size)
            assertTrue(stack.get(1) is AuthNavKey.KeyGeneration)
        }
    }

    @Nested
    @DisplayName("goToProfileSetup")
    inner class GoToProfileSetup {

        @Test
        fun `adds ProfileSetup to empty stack`() {
            val stack = NavBackStack<NavKey>()
            stack.goToProfileSetup()
            assertEquals(1, stack.size)
            assertTrue(stack.get(0) is AuthNavKey.ProfileSetup)
        }

        @Test
        fun `adds ProfileSetup on top of existing stack`() {
            val stack = NavBackStack<NavKey>()
            stack.add(AuthNavKey.KeyGeneration)
            stack.goToProfileSetup()
            assertEquals(2, stack.size)
            assertTrue(stack.get(1) is AuthNavKey.ProfileSetup)
        }
    }

    @Nested
    @DisplayName("popToPhoneEntry")
    inner class PopToPhoneEntry {

        @Test
        fun `pops to PhoneEntry when it exists in stack`() {
            val stack = NavBackStack<NavKey>()
            stack.add(AuthNavKey.Welcome)
            stack.add(AuthNavKey.PhoneEntry)
            stack.add(AuthNavKey.OtpVerify("test"))
            stack.popToPhoneEntry()
            assertEquals(2, stack.size)
            assertTrue(stack.get(1) is AuthNavKey.PhoneEntry)
        }

        @Test
        fun `does nothing when only element is PhoneEntry`() {
            val stack = NavBackStack<NavKey>()
            stack.add(AuthNavKey.PhoneEntry)
            stack.popToPhoneEntry()
            assertEquals(1, stack.size)
        }

        @Test
        fun `does nothing when PhoneEntry not in stack`() {
            val stack = NavBackStack<NavKey>()
            stack.add(AuthNavKey.Welcome)
            stack.add(AuthNavKey.KeyGeneration)
            stack.popToPhoneEntry()
            assertEquals(2, stack.size)
        }

        @Test
        fun `does nothing on empty stack`() {
            val stack = NavBackStack<NavKey>()
            stack.popToPhoneEntry()
            assertEquals(0, stack.size)
        }

        @Test
        fun `pops to last PhoneEntry when multiple exist`() {
            val stack = NavBackStack<NavKey>()
            stack.add(AuthNavKey.PhoneEntry)
            stack.add(AuthNavKey.OtpVerify("1"))
            stack.add(AuthNavKey.PhoneEntry)
            stack.add(AuthNavKey.OtpVerify("2"))
            stack.popToPhoneEntry()
            assertTrue(stack.get(stack.size - 1) is AuthNavKey.PhoneEntry)
        }
    }
}
