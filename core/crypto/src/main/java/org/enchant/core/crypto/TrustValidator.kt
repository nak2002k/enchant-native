package org.enchant.core.crypto

/**
 * Sesame-style trust validator for identity certificates and trust tokens.
 *
 * Wraps enchant_sesame_* FFI functions.
 */
class TrustValidator private constructor(private val handle: Long) {

    fun close() {
        EnchantCrypto.enchant_sesame_destroy_validator(handle)
    }

    /**
     * Add a server trust root certificate (DER-encoded Ed25519 public key).
     */
    fun addTrustRoot(trustRoot: ByteArray) {
        val rc = EnchantCrypto.enchant_sesame_add_trust_root(handle, trustRoot, trustRoot.size.toLong())
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_sesame_add_trust_root failed: $rc")
        }
    }

    /**
     * Set the local user's UUID for trust context.
     */
    fun setOwnIdentity(ownUuid: String) {
        val rc = EnchantCrypto.enchant_sesame_set_own_identity(handle, ownUuid)
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_sesame_set_own_identity failed: $rc")
        }
    }

    /**
     * Validate a sender certificate.
     *
     * @return Triple(trustLevel, certificateValid, keyChanged)
     */
    fun validateSender(certData: ByteArray, validationTimestamp: Long): Triple<Int, Boolean, Boolean> {
        val trustLevelOut = IntArray(1)
        val certValidOut = IntArray(1)
        val keyChangedOut = IntArray(1)
        val rc = EnchantCrypto.enchant_sesame_validate_sender(
            handle, certData, certData.size.toLong(), validationTimestamp,
            trustLevelOut, certValidOut, keyChangedOut
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_sesame_validate_sender failed: $rc")
        }
        return Triple(
            trustLevelOut[0],
            certValidOut[0] != 0,
            keyChangedOut[0] != 0
        )
    }

    /**
     * Establish trust for a sender identity.
     */
    fun establishTrust(
        senderUuid: String,
        senderIdentityKey: ByteArray,
        senderDeviceId: Int,
        trustLevel: Int,
        currentTimestamp: Long
    ) {
        require(senderIdentityKey.size == 32) { "senderIdentityKey must be 32 bytes" }
        val rc = EnchantCrypto.enchant_sesame_establish_trust(
            handle, senderUuid, senderIdentityKey, senderDeviceId, trustLevel, currentTimestamp
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_sesame_establish_trust failed: $rc")
        }
    }

    /**
     * Verify a sender identity is trusted.
     *
     * @return true if verified
     */
    fun verifyIdentity(
        senderUuid: String,
        senderIdentityKey: ByteArray,
        senderDeviceId: Int,
        currentTimestamp: Long
    ): Boolean {
        require(senderIdentityKey.size == 32) { "senderIdentityKey must be 32 bytes" }
        val verifiedOut = IntArray(1)
        val rc = EnchantCrypto.enchant_sesame_verify_identity(
            handle, senderUuid, senderIdentityKey, senderDeviceId, currentTimestamp, verifiedOut
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_sesame_verify_identity failed: $rc")
        }
        return verifiedOut[0] != 0
    }

    /**
     * Mark a server key ID as revoked.
     */
    fun addRevokedServerKey(keyId: Int) {
        val rc = EnchantCrypto.enchant_sesame_add_revoked_server_key(handle, keyId)
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_sesame_add_revoked_server_key failed: $rc")
        }
    }

    /**
     * Get aggregated trust level for a sender.
     */
    fun getAggregatedTrust(senderUuid: String): Int {
        val trustLevelOut = IntArray(1)
        val rc = EnchantCrypto.enchant_sesame_get_aggregated_trust(handle, senderUuid, trustLevelOut)
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_sesame_get_aggregated_trust failed: $rc")
        }
        return trustLevelOut[0]
    }

    companion object {
        fun create(): TrustValidator {
            val handle = LongArray(1)
            val rc = EnchantCrypto.enchant_sesame_create_validator(handle)
            if (rc != EnchantCrypto.SUCCESS) {
                throw IllegalStateException("enchant_sesame_create_validator failed: $rc")
            }
            return TrustValidator(handle[0])
        }
    }
}
