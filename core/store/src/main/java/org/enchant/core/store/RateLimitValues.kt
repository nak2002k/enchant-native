package org.enchant.core.store

class RateLimitValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val otpValue = delegates.longValue("$P.otp", 0L)
    private val otpCountValue = delegates.intValue("$P.otp_count", 0)
    private val keyRegValue = delegates.longValue("$P.key_reg", 0L)
    private val profileUpdateValue = delegates.longValue("$P.profile_update", 0L)

    var lastOtpMs: Long by otpValue
    var otpAttempts: Int by otpCountValue
    var lastKeyRegistrationMs: Long by keyRegValue
    var lastProfileUpdateMs: Long by profileUpdateValue

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.otp").remove("$P.otp_count").remove("$P.key_reg").remove("$P.profile_update")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = emptyList()

    private companion object { const val P = "ratelimit" }
}
