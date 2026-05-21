package org.enchant.core.store

class BackupValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val enabledValue = delegates.booleanValue("$P.enabled", false)
    private val lastTsValue = delegates.longValue("$P.last_ts", 0L)
    private val keyValue = delegates.stringValue("$P.key")
    private val svrMasterKeyValue = delegates.stringValue("$P.svr_master_key")
    private val cdnCredsValue = delegates.stringValue("$P.cdn_creds")
    private val tierValue = delegates.intValue("$P.tier", 0)
    private val mediaEnabledValue = delegates.booleanValue("$P.media_enabled", false)
    private val mediaLastTsValue = delegates.longValue("$P.media_last_ts", 0L)
    private val archiveStateValue = delegates.stringValue("$P.archive_state")

    var isEnabled: Boolean by enabledValue
    var lastBackupTs: Long by lastTsValue
    var backupKey: String? by keyValue
    var svrMasterKey: String? by svrMasterKeyValue
    var backupCdnCredentials: String? by cdnCredsValue
    var backupTier: Int by tierValue
    var isMediaBackupEnabled: Boolean by mediaEnabledValue
    var lastMediaBackupTs: Long by mediaLastTsValue
    var archiveUploadState: String? by archiveStateValue

    val isEnabledFlow = enabledValue.toFlow()
    val lastBackupTsFlow = lastTsValue.toFlow()

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.enabled").remove("$P.last_ts").remove("$P.key")
            .remove("$P.svr_master_key").remove("$P.cdn_creds").remove("$P.tier")
            .remove("$P.media_enabled").remove("$P.media_last_ts").remove("$P.archive_state")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = emptyList()

    private companion object { const val P = "backup" }
}
