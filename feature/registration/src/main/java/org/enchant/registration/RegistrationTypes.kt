package org.enchant.registration

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

interface DebugLoggable {
    val debugDescription: String
}

@Serializable
enum class PendingRestoreOption {
    LocalBackup, RemoteBackup
}

@Serializable
enum class ArchiveRestoreOption {
    EnchantSecureBackup, LocalBackup, DeviceTransfer, None
}

@Serializable
data class SessionMetadata(
    val sessionId: String,
    val verified: Boolean = false
)

@Serializable
data class SvrCredentials(
    val username: String,
    val password: String
)

@Serializable
data class CountryData(
    val code: String,
    val displayName: String,
    val countryCode: Int
)

@Serializable
data class MasterKey(val value: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MasterKey) return false
        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = "MasterKey(hidden)"
}
