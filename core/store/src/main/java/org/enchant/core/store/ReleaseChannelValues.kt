package org.enchant.core.store

class ReleaseChannelValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val channelValue = delegates.stringValue("$P.channel", "stable")
    private val lastCheckValue = delegates.longValue("$P.last_check", 0L)

    var channel: String? by channelValue
    var lastUpdateCheckTs: Long by lastCheckValue

    override fun onFirstEverAppLaunch() {
        if (!store.contains("$P.channel")) {
            store.putString("$P.channel", "stable")
        }
    }

    fun clear() {
        store.beginWrite()
            .remove("$P.channel").remove("$P.last_check")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf("$P.channel")

    private companion object { const val P = "release_channel" }
}
