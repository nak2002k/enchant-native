package org.enchant.core.store

class InternalValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val syncTsValue = delegates.longValue("$P.sync_ts", 0L)
    private val prekeyTsValue = delegates.longValue("$P.prekey_ts", 0L)
    private val trimTsValue = delegates.longValue("$P.trim_ts", 0L)
    private val firstSyncValue = delegates.booleanValue("$P.first_sync", false)

    var lastDeviceSyncTs: Long by syncTsValue
    var lastPreKeyRotationTs: Long by prekeyTsValue
    var lastMessageTrimTs: Long by trimTsValue
    var hasCompletedFirstSync: Boolean by firstSyncValue

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.sync_ts").remove("$P.prekey_ts").remove("$P.trim_ts").remove("$P.first_sync")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = emptyList()

    private companion object { const val P = "internal" }
}
