package org.enchant.core.store

class ApkUpdateValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val lastCheckValue = delegates.longValue("$P.last_check", 0L)
    private val lastVersionValue = delegates.intValue("$P.last_version", 0)
    private val dismissedValue = delegates.booleanValue("$P.dismissed", false)

    var lastCheckTs: Long by lastCheckValue
    var lastVersionCode: Int by lastVersionValue
    var hasDismissedUpdate: Boolean by dismissedValue

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.last_check").remove("$P.last_version").remove("$P.dismissed")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = emptyList()

    private companion object { const val P = "apk_update" }
}
