package org.enchant.calls

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface CallsNavKey : NavKey {

    @Serializable
    data object CallLog : CallsNavKey

    @Serializable
    data class OutgoingCall(
        val recipientId: Long
    ) : CallsNavKey

    @Serializable
    data class IncomingCall(
        val callerId: Long,
        val callId: String
    ) : CallsNavKey

    @Serializable
    data class ActiveCall(
        val callId: String
    ) : CallsNavKey

    @Serializable
    data class GroupCall(
        val groupId: Long
    ) : CallsNavKey

    @Serializable
    data class CallLink(
        val linkRoomId: String
    ) : CallsNavKey
}
