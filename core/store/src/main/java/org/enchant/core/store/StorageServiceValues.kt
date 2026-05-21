package org.enchant.core.store

class StorageServiceValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val manifestVersionValue = delegates.intValue("$P.manifest_version", 0)
    private val lastSyncValue = delegates.longValue("$P.last_sync", 0L)
    private val storageKeyValue = delegates.stringValue("$P.storage_key")
    private val syncEnabledValue = delegates.booleanValue("$P.sync_enabled", true)

    var manifestVersion: Int by manifestVersionValue
    var lastSyncTs: Long by lastSyncValue
    var storageKey: String? by storageKeyValue
    var isSyncEnabled: Boolean by syncEnabledValue

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.manifest_version").remove("$P.last_sync").remove("$P.storage_key").remove("$P.sync_enabled")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf(
        "$P.manifest_version", "$P.storage_key", "$P.sync_enabled"
    )

    private companion object { const val P = "storage_service" }
}
