package org.enchant.calls

import org.enchant.core.calls.CallEndReason
import org.enchant.core.calls.CallObserver
import org.enchant.core.calls.CallObserverRegistry
import org.enchant.core.calls.CallSummary
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CallObserverRegistry")
class CallObserverRegistryTest {
    private lateinit var registry: CallObserverRegistry

    @BeforeEach
    fun setUp() {
        registry = CallObserverRegistry()
    }

    @Nested
    @DisplayName("registration")
    inner class Registration {
        @Test
        fun `register adds observer`() {
            var called = false
            val observer = object : CallObserver {
                override fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) { called = true }
            }
            registry.register(observer)
            registry.notifyStarted("user_1", true)
            assert(called)
        }

        @Test
        fun `unregistered observer does not receive events`() {
            var called = false
            val observer = object : CallObserver {
                override fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) { called = true }
            }
            registry.register(observer)
            registry.unregister(observer)
            registry.notifyStarted("user_1", true)
            assert(!called)
        }

        @Test
        fun `multiple observers all receive event`() {
            var count = 0
            val observer1 = object : CallObserver {
                override fun onCallEnded(reason: CallEndReason, summary: CallSummary?) { count++ }
            }
            val observer2 = object : CallObserver {
                override fun onCallEnded(reason: CallEndReason, summary: CallSummary?) { count++ }
            }
            registry.register(observer1)
            registry.register(observer2)
            registry.notifyEnded(CallEndReason.HANGUP_LOCAL, null)
            assert(count == 2)
        }
    }

    @Nested
    @DisplayName("notifications")
    inner class Notifications {
        @Test
        fun `notifyStarted fires onCallStarted`() {
            var userId = ""
            val observer = object : CallObserver {
                override fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) { userId = remoteUserId }
            }
            registry.register(observer)
            registry.notifyStarted("alice", true)
            assert(userId == "alice")
        }

        @Test
        fun `notifyEnded fires onCallEnded`() {
            var reason: CallEndReason? = null
            val observer = object : CallObserver {
                override fun onCallEnded(r: CallEndReason, summary: CallSummary?) { reason = r }
            }
            registry.register(observer)
            registry.notifyEnded(CallEndReason.HANGUP_REMOTE, null)
            assert(reason == CallEndReason.HANGUP_REMOTE)
        }

        @Test
        fun `notifyOfferSent fires onOfferSent`() {
            var capturedSdp = ""
            val observer = object : CallObserver {
                override fun onOfferSent(remoteUserId: String, sdp: String) { capturedSdp = sdp }
            }
            registry.register(observer)
            registry.notifyOfferSent("bob", "sdp_offer")
            assert(capturedSdp == "sdp_offer")
        }

        @Test
        fun `notifyAnswerSent fires onAnswerSent`() {
            var capturedSdp = ""
            val observer = object : CallObserver {
                override fun onAnswerSent(remoteUserId: String, sdp: String) { capturedSdp = sdp }
            }
            registry.register(observer)
            registry.notifyAnswerSent("bob", "sdp_answer")
            assert(capturedSdp == "sdp_answer")
        }

        @Test
        fun `notifyHangup fires onHangupSent`() {
            var capturedUserId = ""
            val observer = object : CallObserver {
                override fun onHangupSent(remoteUserId: String) { capturedUserId = remoteUserId }
            }
            registry.register(observer)
            registry.notifyHangup("alice")
            assert(capturedUserId == "alice")
        }

        @Test
        fun `notifyError fires onError`() {
            var errorMsg = ""
            val observer = object : CallObserver {
                override fun onError(error: String) { errorMsg = error }
            }
            registry.register(observer)
            registry.notifyError("test error")
            assert(errorMsg == "test error")
        }
    }

    @Nested
    @DisplayName("edge cases")
    inner class EdgeCases {
        @Test
        fun `notify with no observers does not throw`() {
            assertDoesNotThrow { registry.notifyStarted("user", true) }
            assertDoesNotThrow { registry.notifyEnded(CallEndReason.HANGUP_LOCAL, null) }
            assertDoesNotThrow { registry.notifyOfferSent("user", "sdp") }
            assertDoesNotThrow { registry.notifyError("error") }
        }

        @Test
        fun `unregister non-existent observer does not throw`() {
            assertDoesNotThrow {
                registry.unregister(object : CallObserver {})
            }
        }

        @Test
        fun `register same observer twice does not duplicate`() {
            var count = 0
            val observer = object : CallObserver {
                override fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) { count++ }
            }
            registry.register(observer)
            registry.register(observer)
            registry.notifyStarted("user", true)
            assert(count == 1)
        }

        @Test
        fun `clear removes all observers`() {
            var count = 0
            val observer = object : CallObserver {
                override fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) { count++ }
            }
            registry.register(observer)
            registry.clear()
            registry.notifyStarted("user", true)
            assert(count == 0)
        }

        @Test
        fun `notifyConnected fires onCallConnected`() {
            var connected = false
            val observer = object : CallObserver {
                override fun onCallConnected() { connected = true }
            }
            registry.register(observer)
            registry.notifyConnected()
            assert(connected)
        }

        @Test
        fun `notifyReconnecting fires onCallReconnecting`() {
            var reconnecting = false
            val observer = object : CallObserver {
                override fun onCallReconnecting() { reconnecting = true }
            }
            registry.register(observer)
            registry.notifyReconnecting()
            assert(reconnecting)
        }
    }
}