package org.enchant.core.crypto

/**
 * Helper for generating and comparing safety-number fingerprints.
 *
 * Uses the native enchant_fingerprint_* FFI functions.
 */
object FingerprintHelper {

    private const val DISPLAYABLE_CAPACITY = 256
    private const val SCANNABLE_CAPACITY = 64

    /**
     * Generate a displayable safety-number string and a scannable fingerprint
     * for the given identity key pair.
     *
     * @param localName Local user identifier (e.g. UUID)
     * @param localKey Local identity public key (32 bytes)
     * @param remoteName Remote user identifier
     * @param remoteKey Remote identity public key (32 bytes)
     * @return Pair(displayableSafetyNumber, scannableFingerprintBytes)
     */
    fun generate(
        localName: String,
        localKey: ByteArray,
        remoteName: String,
        remoteKey: ByteArray
    ): Pair<String, ByteArray> {
        require(localKey.size == 32) { "localKey must be 32 bytes" }
        require(remoteKey.size == 32) { "remoteKey must be 32 bytes" }

        val displayable = ByteArray(DISPLAYABLE_CAPACITY)
        val scannable = ByteArray(SCANNABLE_CAPACITY)
        val scannableLen = LongArray(1)

        val rc = EnchantCrypto.enchant_fingerprint_generate(
            version = 1,
            iterations = 5200,
            localName = localName,
            localKey = localKey,
            remoteName = remoteName,
            remoteKey = remoteKey,
            displayableOut = displayable,
            displayableOutLen = DISPLAYABLE_CAPACITY.toLong(),
            scannableOut = scannable,
            scannableLen = scannableLen
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_fingerprint_generate failed: $rc")
        }
        val displayableString = displayable.toString(Charsets.UTF_8).trimEnd('\u0000')
        return Pair(displayableString, scannable.copyOf(scannableLen[0].toInt()))
    }

    /**
     * Compare two scannable fingerprints for equality (constant-time).
     *
     * @return true if fingerprints match
     */
    fun compare(scannableA: ByteArray, scannableB: ByteArray): Boolean {
        val matchOut = IntArray(1)
        val rc = EnchantCrypto.enchant_fingerprint_compare(
            scannableA, scannableA.size.toLong(),
            scannableB, scannableB.size.toLong(),
            matchOut
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_fingerprint_compare failed: $rc")
        }
        return matchOut[0] != 0
    }
}
