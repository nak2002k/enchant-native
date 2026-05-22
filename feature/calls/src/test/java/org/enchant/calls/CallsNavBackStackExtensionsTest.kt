package org.enchant.calls

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CallsNavBackStackExtensions")
class CallsNavBackStackExtensionsTest {

    @Nested
    @DisplayName("goToOutgoingCall")
    inner class GoToOutgoingCall {

        @Test
        fun `adds OutgoingCall to empty stack`() {
            val stack = NavBackStack<NavKey>()
            stack.goToOutgoingCall(42L)
            assertEquals(1, stack.size)
            val key = stack.get(0) as CallsNavKey.OutgoingCall
            assertEquals(42L, key.recipientId)
        }

        @Test
        fun `adds OutgoingCall on top of existing stack`() {
            val stack = NavBackStack<NavKey>()
            stack.add(CallsNavKey.CallLog)
            stack.goToOutgoingCall(1L)
            assertEquals(2, stack.size)
            val key = stack.get(1) as CallsNavKey.OutgoingCall
            assertEquals(1L, key.recipientId)
        }

        @Test
        fun `pops to existing OutgoingCall when already in stack`() {
            val stack = NavBackStack<NavKey>()
            stack.add(CallsNavKey.CallLog)
            stack.add(CallsNavKey.OutgoingCall(1L))
            stack.add(CallsNavKey.OutgoingCall(2L))
            stack.goToOutgoingCall(1L)
            assertEquals(2, stack.size)
            val key = stack.get(1) as CallsNavKey.OutgoingCall
            assertEquals(1L, key.recipientId)
        }

        @Test
        fun `does not duplicate when OutgoingCall is at top`() {
            val stack = NavBackStack<NavKey>()
            stack.add(CallsNavKey.OutgoingCall(1L))
            stack.goToOutgoingCall(1L)
            assertEquals(1, stack.size)
        }
    }

    @Nested
    @DisplayName("goToIncomingCall")
    inner class GoToIncomingCall {

        @Test
        fun `adds IncomingCall to empty stack`() {
            val stack = NavBackStack<NavKey>()
            stack.goToIncomingCall(42L, "call-id")
            assertEquals(1, stack.size)
            val key = stack.get(0) as CallsNavKey.IncomingCall
            assertEquals(42L, key.callerId)
            assertEquals("call-id", key.callId)
        }

        @Test
        fun `always adds even if same IncomingCall exists`() {
            val stack = NavBackStack<NavKey>()
            stack.goToIncomingCall(1L, "same-id")
            stack.goToIncomingCall(1L, "same-id")
            assertEquals(2, stack.size)
        }
    }

    @Nested
    @DisplayName("goToActiveCall")
    inner class GoToActiveCall {

        @Test
        fun `adds ActiveCall to empty stack`() {
            val stack = NavBackStack<NavKey>()
            stack.goToActiveCall("active-1")
            assertEquals(1, stack.size)
            val key = stack.get(0) as CallsNavKey.ActiveCall
            assertEquals("active-1", key.callId)
        }

        @Test
        fun `pops to existing ActiveCall when already in stack`() {
            val stack = NavBackStack<NavKey>()
            stack.add(CallsNavKey.CallLog)
            stack.add(CallsNavKey.ActiveCall("call-1"))
            stack.add(CallsNavKey.ActiveCall("call-2"))
            stack.goToActiveCall("call-1")
            assertEquals(2, stack.size)
            val key = stack.get(1) as CallsNavKey.ActiveCall
            assertEquals("call-1", key.callId)
        }

        @Test
        fun `does not duplicate when ActiveCall is at top`() {
            val stack = NavBackStack<NavKey>()
            stack.add(CallsNavKey.ActiveCall("call-1"))
            stack.goToActiveCall("call-1")
            assertEquals(1, stack.size)
        }
    }

    @Nested
    @DisplayName("goToGroupCall")
    inner class GoToGroupCall {

        @Test
        fun `adds GroupCall to empty stack`() {
            val stack = NavBackStack<NavKey>()
            stack.goToGroupCall(99L)
            assertEquals(1, stack.size)
            val key = stack.get(0) as CallsNavKey.GroupCall
            assertEquals(99L, key.groupId)
        }

        @Test
        fun `always adds even if same GroupCall exists`() {
            val stack = NavBackStack<NavKey>()
            stack.goToGroupCall(1L)
            stack.goToGroupCall(1L)
            assertEquals(2, stack.size)
        }
    }

    @Nested
    @DisplayName("goToCallLink")
    inner class GoToCallLink {

        @Test
        fun `adds CallLink to empty stack`() {
            val stack = NavBackStack<NavKey>()
            stack.goToCallLink("room-id")
            assertEquals(1, stack.size)
            val key = stack.get(0) as CallsNavKey.CallLink
            assertEquals("room-id", key.linkRoomId)
        }

        @Test
        fun `always adds even if same CallLink exists`() {
            val stack = NavBackStack<NavKey>()
            stack.goToCallLink("room-id")
            stack.goToCallLink("room-id")
            assertEquals(2, stack.size)
        }

        @Test
        fun `handles empty linkRoomId`() {
            val stack = NavBackStack<NavKey>()
            stack.goToCallLink("")
            assertEquals(1, stack.size)
        }
    }
}
