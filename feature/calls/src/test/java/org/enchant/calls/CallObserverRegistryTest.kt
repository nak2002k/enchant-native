package org.enchant.calls

import org.enchant.core.calls.CallEndReason
import org.enchant.core.calls.CallObserver
import org.enchant.core.calls.CallObserverRegistry
import org.enchant.core.calls.CallSummary
import org.enchant.core.calls.RingUpdate
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
        fun `registerObserver adds observer`() {
            var called = false
            val observer = object : CallObserver {
                override fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) { called = true }
            }
            registry.registerObserver(observer)
            registry.notifyCallStarted("user_1", true)
            assert(called)
        }

        @Test
        fun `unregistered observer does not receive events`() {
            var called = false
            val observer = object : CallObserver {
                override fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) { called = true }
            }
            registry.registerObserver(observer)
            registry.unregisterObserver(observer)
            registry.notifyCallStarted("user_1", true)
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
            registry.registerObserver(observer1)
            registry.registerObserver(observer2)
            registry.notifyCallEnded(CallEndReason.HANGUP_LOCAL, null)
            assert(count == 2)
        }
    }

    @Nested
    @DisplayName("notifications")
    inner class Notifications {
        @Test
        fun `notifyCallStarted fires onCallStarted`() {
            var userId = ""
            val observer = object : CallObserver {
                override fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) { userId = remoteUserId }
            }
            registry.registerObserver(observer)
            registry.notifyCallStarted("alice", true)
            assert(userId == "alice")
        }

        @Test
        fun `notifyCallEnded fires onCallEnded`() {
            var reason: CallEndReason? = null
            val observer = object : CallObserver {
                override fun onCallEnded(r: CallEndReason, summary: CallSummary?) { reason = r }
            }
            registry.registerObserver(observer)
            registry.notifyCallEnded(CallEndReason.HANGUP_REMOTE, null)
            assert(reason == CallEndReason.HANGUP_REMOTE)
        }

        @Test
        fun `notifyOfferSent fires onOfferSent`() {
            var capturedSdp = ""
            val observer = object : CallObserver {
                override fun onOfferSent(remoteUserId: String, sdp: String) { capturedSdp = sdp }
            }
            registry.registerObserver(observer)
            registry.notifyOfferSent("bob", "sdp_offer")
            assert(capturedSdp == "sdp_offer")
        }

        @Test
        fun `notifyAnswerSent fires onAnswerSent`() {
            var capturedSdp = ""
            val observer = object : CallObserver {
                override fun onAnswerSent(remoteUserId: String, sdp: String) { capturedSdp = sdp }
            }
            registry.registerObserver(observer)
            registry.notifyAnswerSent("bob", "sdp_answer")
            assert(capturedSdp == "sdp_answer")
        }

        @Test
        fun `notifyHangupSent fires onHangupSent`() {
            var userId = ""
            val observer = object : CallObserver {
                override fun onHangupSent(remoteUserId: String) { userId = remoteUserId }
            }
            registry.registerObserver(observer)
            registry.notifyHangupSent("alice")
            assert(userId == "alice")
        }

        @Test
        fun `notifyGroupCallRingUpdate fires onGroupCallRingUpdate`() {
            var update: RingUpdate? = null
            val observer = object : CallObserver {
                override fun onGroupCallRingUpdate(groupId: String, ringUpdate: RingUpdate) { update = ringUpdate }
            }
            registry.registerObserver(observer)
            registry.notifyGroupCallRingUpdate("group_1", RingUpdate.JOINED)
            assert(update == RingUpdate.JOINED)
        }

        @Test
        fun `notifyMessageSentError fires onMessageSentError`() {
            var error: Exception? = null
            val observer = object : CallObserver {
                override fun onMessageSentError(exception: Exception) { error = exception }
            }
            registry.registerObserver(observer)
            registry.notifyMessageSentError(Exception("test error"))
            assert(error?.message == "test error")
        }
    }

    @Nested
    @DisplayName("edge cases")
    inner class EdgeCases {
        @Test
        fun `notify with no observers does not throw`() {
            registry.notifyCallStarted("user", true)
            registry.notifyCallEnded(CallEndReason.HANGUP_LOCAL, null)
            registry.notifyOfferSent("user", "sdp")
        }

        @Test
        fun `unregister non-existent observer does not throw`() {
            registry.unregisterObserver(object : CallObserver {})
        }

        @Test
        fun `register same observer twice does not duplicate`() {
            var count = 0
            val observer = object : CallObserver {
                override fun onCallStarted(remoteUserId: String, isVideoCall: Boolean) { count++ }
            }
            registry.registerObserver(observer)
            registry.registerObserver(observer)
            registry.notifyCallStarted("user", true)
            assert(count == 1)
        }
    }
}
