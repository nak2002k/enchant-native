package org.enchant.core.store

class MiscellaneousValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val lastVersionValue = delegates.intValue("$P.last_version", 0)
    private val firstRunValue = delegates.booleanValue("$P.first_run", false)
    private val appStartValue = delegates.longValue("$P.app_start", 0L)
    private val dbUpgradeSeenValue = delegates.booleanValue("$P.db_upgrade_seen", false)

    var lastVersionCode: Int by lastVersionValue
    var hasCompletedFirstRun: Boolean by firstRunValue
    var appStartTime: Long by appStartValue
    var hasSeenDatabaseUpgrade: Boolean by dbUpgradeSeenValue

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.last_version").remove("$P.first_run").remove("$P.app_start").remove("$P.db_upgrade_seen")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf("$P.first_run")

    private companion object { const val P = "misc" }
}
