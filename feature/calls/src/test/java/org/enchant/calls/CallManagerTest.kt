package org.enchant.calls

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.enchant.core.calls.CallEndReason
import org.enchant.core.calls.CallManager
import org.enchant.core.calls.CallObserver
import org.enchant.core.calls.CallState
import org.enchant.core.calls.CallsModule
import org.enchant.core.calls.model.CallLogEntry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CallManager")
class CallManagerTest {

    private lateinit var mockCallManager: org.enchant.core.calls.DefaultCallManager
    private lateinit var stateFlow: MutableStateFlow<CallState>

    @BeforeEach
    fun setUp() {
        stateFlow = MutableStateFlow(CallState())
        mockCallManager = mockk(relaxed = true)
        every { mockCallManager.callState } returns stateFlow
        mockkObject(CallsModule)
        every { CallsModule.getCallManager() } returns mockCallManager
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(CallsModule)
    }

    @Nested
    @DisplayName("CallManager delegate")
    inner class DelegateTests {
        @Test
        fun `callState returns state from CallsModule`() {
            assertNotNull(CallManager.callState)
        }

        @Test
        fun `toggleMute delegates to CallsModule`() {
            CallManager.toggleMute()
            verify { mockCallManager.toggleMute() }
        }

        @Test
        fun `toggleSpeaker delegates to CallsModule`() {
            CallManager.toggleSpeaker()
            verify { mockCallManager.toggleSpeaker() }
        }

        @Test
        fun `flipCamera delegates to CallsModule`() {
            CallManager.flipCamera()
            verify { mockCallManager.flipCamera() }
        }

        @Test
        fun `endCall delegates to CallsModule`() {
            CallManager.endCall()
            verify { mockCallManager.endCall() }
        }

        @Test
        fun `denyCall delegates to CallsModule`() {
            CallManager.denyCall()
            verify { mockCallManager.denyCall() }
        }

        @Test
        fun `setOnHold delegates to CallsModule`() {
            CallManager.setOnHold(true)
            verify { mockCallManager.setOnHold(true) }
        }

        @Test
        fun `raiseHand delegates to CallsModule`() {
            CallManager.raiseHand(true)
            verify { mockCallManager.raiseHand(true) }
        }

        @Test
        fun `handleReceivedOffer delegates to CallsModule`() {
            CallManager.handleReceivedOffer("user", "sdp", "call-1", false)
            verify { mockCallManager.handleReceivedOffer("user", "sdp", "call-1", false) }
        }

        @Test
        fun `handleReceivedHangup delegates to CallsModule`() {
            CallManager.handleReceivedHangup()
            verify { mockCallManager.handleReceivedHangup() }
        }

        @Test
        fun `toggleVideo delegates to CallsModule`() {
            CallManager.toggleVideo()
            verify { mockCallManager.toggleVideo() }
        }
    }

    @Nested
    @DisplayName("Observer registration")
    inner class ObserverTests {
        @Test
        fun `registerObserver adds observer to underlying manager`() {
            val observer = object : CallObserver {
                override fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) {}
            }
            CallManager.registerObserver(observer)
            verify { mockCallManager.registerObserver(observer) }
        }

        @Test
        fun `unregisterObserver removes observer from underlying manager`() {
            val observer = object : CallObserver {
                override fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) {}
            }
            CallManager.unregisterObserver(observer)
            verify { mockCallManager.unregisterObserver(observer) }
        }
    }

    @Nested
    @DisplayName("Async operations")
    inner class AsyncTests {
        @Test
        fun `acceptCall delegates suspend to CallsModule`() = runTest {
            coEvery { mockCallManager.acceptCall(any()) } returns Unit
            CallManager.acceptCall(false)
            coVerify { mockCallManager.acceptCall(false) }
        }

        @Test
        fun `startOutgoingCall delegates suspend to CallsModule`() = runTest {
            coEvery { mockCallManager.startOutgoingCall(any(), any()) } returns Unit
            CallManager.startOutgoingCall("user", false)
            coVerify { mockCallManager.startOutgoingCall("user", false) }
        }

        @Test
        fun `getCallLogs returns list from underlying manager`() = runTest {
            val expectedLogs = listOf<CallLogEntry>()
            coEvery { mockCallManager.getCallLogs(any()) } returns expectedLogs
            val result = CallManager.getCallLogs()
            assert(result == expectedLogs)
        }
    }
}