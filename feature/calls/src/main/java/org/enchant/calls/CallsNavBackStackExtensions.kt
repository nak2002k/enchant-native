package org.enchant.calls

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

internal fun NavBackStack<NavKey>.goToOutgoingCall(recipientId: Long) {
    val key = CallsNavKey.OutgoingCall(recipientId = recipientId)
    if (contains(key)) {
        while (size > 1 && get(size - 1) != key) removeAt(size - 1)
    } else {
        add(key)
    }
}

internal fun NavBackStack<NavKey>.goToIncomingCall(callerId: Long, callId: String) {
    add(CallsNavKey.IncomingCall(callerId = callerId, callId = callId))
}

internal fun NavBackStack<NavKey>.goToActiveCall(callId: String) {
    val key = CallsNavKey.ActiveCall(callId = callId)
    if (contains(key)) {
        while (size > 1 && get(size - 1) != key) removeAt(size - 1)
    } else {
        add(key)
    }
}

internal fun NavBackStack<NavKey>.goToGroupCall(groupId: Long) {
    add(CallsNavKey.GroupCall(groupId = groupId))
}

internal fun NavBackStack<NavKey>.goToCallLink(linkRoomId: String) {
    add(CallsNavKey.CallLink(linkRoomId = linkRoomId))
}
