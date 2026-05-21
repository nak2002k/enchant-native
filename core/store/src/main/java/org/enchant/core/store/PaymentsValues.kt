package org.enchant.core.store

class PaymentsValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val enabledValue = delegates.booleanValue("$P.enabled", false)
    private val introSeenValue = delegates.booleanValue("$P.intro_seen", false)
    private val lastBalanceValue = delegates.longValue("$P.last_balance", 0L)

    var isEnabled: Boolean by enabledValue
    var hasSeenIntro: Boolean by introSeenValue
    var lastBalanceFetchTs: Long by lastBalanceValue

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.enabled").remove("$P.intro_seen").remove("$P.last_balance")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf("$P.enabled")

    private companion object { const val P = "payments" }
}
