package org.enchant.core.store

class InAppPaymentValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val tierValue = delegates.stringValue("$P.tier")
    private val lastPaymentValue = delegates.longValue("$P.last_payment", 0L)
    private val introSeenValue = delegates.booleanValue("$P.intro_seen", false)

    var subscriptionTier: String? by tierValue
    var lastPaymentTs: Long by lastPaymentValue
    var hasSeenPaymentIntro: Boolean by introSeenValue

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.tier").remove("$P.last_payment").remove("$P.intro_seen")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf("$P.tier")

    private companion object { const val P = "in_app_payment" }
}
