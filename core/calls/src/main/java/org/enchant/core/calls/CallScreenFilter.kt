package org.enchant.core.calls

interface CallScreenFilter {
    fun shouldAllowIncomingCall(callerId: String, isVideoCall: Boolean): FilterResult
    fun shouldAllowOutgoingCall(recipientId: String, isVideoCall: Boolean): FilterResult
    fun shouldRingGroupCall(groupId: String): RingResult

    sealed class FilterResult {
        data object Allow : FilterResult()
        data class Block(val reason: String) : FilterResult()
        data class Silence(val reason: String) : FilterResult()
    }

    sealed class RingResult {
        data object Ring : RingResult()
        data object DoNotRing : RingResult()
        data class RingAfterDelay(val delayMs: Long) : RingResult()
    }
}

class DefaultCallScreenFilter : CallScreenFilter {
    override fun shouldAllowIncomingCall(callerId: String, isVideoCall: Boolean): CallScreenFilter.FilterResult {
        return CallScreenFilter.FilterResult.Allow
    }

    override fun shouldAllowOutgoingCall(recipientId: String, isVideoCall: Boolean): CallScreenFilter.FilterResult {
        return CallScreenFilter.FilterResult.Allow
    }

    override fun shouldRingGroupCall(groupId: String): CallScreenFilter.RingResult {
        return CallScreenFilter.RingResult.Ring
    }
}

class ScreeningCallScreenFilter(
    private val blockedUsers: Set<String> = emptySet(),
    private val silencedUsers: Set<String> = emptySet(),
    private val blockedGroups: Set<String> = emptySet()
) : CallScreenFilter {
    override fun shouldAllowIncomingCall(callerId: String, isVideoCall: Boolean): CallScreenFilter.FilterResult {
        return when {
            blockedUsers.contains(callerId) -> CallScreenFilter.FilterResult.Block("User is blocked")
            silencedUsers.contains(callerId) -> CallScreenFilter.FilterResult.Silence("User is silenced")
            else -> CallScreenFilter.FilterResult.Allow
        }
    }

    override fun shouldAllowOutgoingCall(recipientId: String, isVideoCall: Boolean): CallScreenFilter.FilterResult {
        return when {
            blockedUsers.contains(recipientId) -> CallScreenFilter.FilterResult.Block("User is blocked")
            else -> CallScreenFilter.FilterResult.Allow
        }
    }

    override fun shouldRingGroupCall(groupId: String): CallScreenFilter.RingResult {
        return when {
            blockedGroups.contains(groupId) -> CallScreenFilter.RingResult.DoNotRing
            else -> CallScreenFilter.RingResult.Ring
        }
    }
}