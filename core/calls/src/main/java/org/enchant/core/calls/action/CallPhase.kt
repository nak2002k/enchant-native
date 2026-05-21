package org.enchant.core.calls.action

enum class CallPhase {
    IDLE,
    OUTGOING_CALL,
    INCOMING_CALL,
    CONNECTED,
    RECONNECTING,
    GROUP_CONNECTED,
    CALL_LINK
}