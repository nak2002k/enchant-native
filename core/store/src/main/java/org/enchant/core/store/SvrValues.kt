package org.enchant.core.store

class SvrValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val masterKeyValue = delegates.stringValue("$P.master_key")
    private val backupIdValue = delegates.stringValue("$P.backup_id")
    private val lastRestoreValue = delegates.longValue("$P.last_restore", 0L)
    private val configuredValue = delegates.booleanValue("$P.configured", false)
    private val pinHashValue = delegates.stringValue("$P.pin_hash")
    private val saltValue = delegates.stringValue("$P.salt")

    var masterKey: String? by masterKeyValue
    var backupId: String? by backupIdValue
    var lastRestoreTs: Long by lastRestoreValue
    var isConfigured: Boolean by configuredValue
    var pinHash: String? by pinHashValue
    var salt: String? by saltValue

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.master_key").remove("$P.backup_id").remove("$P.last_restore")
            .remove("$P.configured").remove("$P.pin_hash").remove("$P.salt")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf("$P.backup_id", "$P.configured")

    private companion object { const val P = "svr" }
}
