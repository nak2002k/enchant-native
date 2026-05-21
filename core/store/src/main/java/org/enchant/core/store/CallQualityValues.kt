package org.enchant.core.store

class CallQualityValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val lowBwValue = delegates.booleanValue("$P.low_bw", false)
    private val alwaysRelayValue = delegates.booleanValue("$P.always_relay", false)
    private val directP2PValue = delegates.booleanValue("$P.direct_p2p", true)
    private val dataSavingValue = delegates.booleanValue("$P.data_saving", false)

    var useLowBandwidth: Boolean by lowBwValue
    var alwaysRelayCalls: Boolean by alwaysRelayValue
    var useDirectP2P: Boolean by directP2PValue
    var dataSavingMode: Boolean by dataSavingValue

    override fun onFirstEverAppLaunch() {
        if (!store.contains("$P.low_bw")) {
            store.putBoolean("$P.low_bw", false)
            store.putBoolean("$P.always_relay", false)
            store.putBoolean("$P.direct_p2p", true)
        }
    }

    fun clear() {
        store.beginWrite()
            .remove("$P.low_bw").remove("$P.always_relay").remove("$P.direct_p2p").remove("$P.data_saving")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf(
        "$P.low_bw", "$P.always_relay", "$P.direct_p2p", "$P.data_saving"
    )

    private companion object { const val P = "call_quality" }
}
