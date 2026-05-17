package org.enchant.core.calls

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CallManager")
class CallManagerTest {

    @BeforeEach
    fun setUp() = runTest {
        // No Android context available, test only the state management
    }

    @Nested @DisplayName("initial state")
    inner class InitialStateTest {
        @Test @DisplayName("starts in IDLE state")
        fun `initial state is idle`() {
            assertEquals(CallStatusEnum.IDLE, CallManager.callState.value.status)
        }

        @Test @DisplayName("has no remote user")
        fun `initial remote user is null`() {
            assertNull(CallManager.callState.value.remoteUserId)
        }

        @Test @DisplayName("is not muted")
        fun `initial muted is false`() {
            assertFalse(CallManager.callState.value.isMuted)
        }

        @Test @DisplayName("is not video call")
        fun `initial video is false`() {
            assertFalse(CallManager.callState.value.isVideoCall)
        }

        @Test @DisplayName("duration starts at 0")
        fun `initial duration`() {
            assertEquals(0, CallManager.callState.value.durationSeconds)
        }
    }

    @Nested @DisplayName("mute toggling")
    inner class MuteTest {
        @Test @DisplayName("toggleMute flips from false to true")
        fun `toggle mute on`() {
            CallManager.toggleMute()
            assertTrue(CallManager.callState.value.isMuted)
        }

        @Test @DisplayName("toggleMute flips back to false")
        fun `toggle mute off`() {
            // Reset: first ensure muted is true
            if (!CallManager.callState.value.isMuted) CallManager.toggleMute()
            assertTrue(CallManager.callState.value.isMuted)
            CallManager.toggleMute()
            assertFalse(CallManager.callState.value.isMuted)
        }
    }

    @Nested @DisplayName("endCall from IDLE")
    inner class EndCallIdleTest {
        @Test @DisplayName("endCall when IDLE is no-op")
        fun `end call idle`() {
            CallManager.endCall()
            assertEquals(CallStatusEnum.IDLE, CallManager.callState.value.status)
        }
    }

    @Nested @DisplayName("observer registration")
    inner class ObserverTest {
        @Test @DisplayName("register and unregister does not throw")
        fun `register unregister`() {
            val observer = object : CallObserver {}
            CallManager.registerObserver(observer)
            CallManager.unregisterObserver(observer)
            assertTrue(true)
        }
    }

    @Nested @DisplayName("coroutine scope lifecycle")
    inner class ScopeLifecycleTest {
        @Test @DisplayName("callScope is available after init")
        fun `scope available`() = runTest {
            CallManager.init()
            assertTrue(true) // no exception
        }

        @Test @DisplayName("multiple init calls are safe")
        fun `double init`() = runTest {
            CallManager.init()
            CallManager.init()
            assertTrue(true)
        }
    }
}