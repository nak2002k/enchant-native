package org.enchant.core.calls.model

enum class GroupCallState {
    IDLE,
    RINGING,
    DISCONNECTED,
    CONNECTING,
    RECONNECTING,
    CONNECTED,
    CONNECTED_AND_PENDING,
    CONNECTED_AND_JOINING,
    CONNECTED_AND_JOINED;

    val isIdle: Boolean
        get() = this == IDLE

    val isNotIdle: Boolean
        get() = this != IDLE

    val isConnected: Boolean
        get() = this == CONNECTED || this == CONNECTED_AND_JOINING || this == CONNECTED_AND_JOINED || this == CONNECTED_AND_PENDING

    val isNotIdleOrConnected: Boolean
        get() = this == DISCONNECTED || this == CONNECTING || this == RECONNECTING

    val isRinging: Boolean
        get() = this == RINGING
}

data class GroupCallParticipant(
    val userId: String,
    val demuxId: Int,
    val isAudioMuted: Boolean = true,
    val isVideoMuted: Boolean = true,
    val isHandRaised: Boolean = false,
    val handRaisedTimestamp: Long = 0,
    val speakerTime: Long = 0,
    val addedTime: Long = 0,
    val isPresenting: Boolean = false,
    val deviceOrdinal: DeviceOrdinal = DeviceOrdinal.PRIMARY
)

enum class DeviceOrdinal {
    PRIMARY,
    SECONDARY
}