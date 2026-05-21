package org.enchant.core.store

class RemoteConfigValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val valuesValue = delegates.stringValue("$P.values")
    private val lastFetchValue = delegates.longValue("$P.last_fetch", 0L)
    private val eTagValue = delegates.stringValue("$P.etag")

    var values: String? by valuesValue
    var lastFetchTs: Long by lastFetchValue
    var eTag: String? by eTagValue

    override fun onFirstEverAppLaunch() {
        if (!store.contains("$P.last_fetch")) {
            store.putLong("$P.last_fetch", 0L)
        }
    }

    fun clear() {
        store.beginWrite()
            .remove("$P.values").remove("$P.last_fetch").remove("$P.etag")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = emptyList()

    private companion object { const val P = "remote_config" }
}
