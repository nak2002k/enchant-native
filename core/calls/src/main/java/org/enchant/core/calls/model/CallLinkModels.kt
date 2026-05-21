package org.enchant.core.calls.model

enum class CallLinkRestrictions {
    ANYONE,
    APPROVAL_REQUIRED,
    CONTACTS_ONLY
}

data class CallLinkData(
    val roomId: String,
    val name: String,
    val creatorId: String,
    val restrictions: CallLinkRestrictions,
    val isActive: Boolean
)

data class CallLinkCredentials(
    val roomId: String,
    val authToken: String,
    val iceServers: List<IceServer>
)