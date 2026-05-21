package org.enchant.core.store

class ProxyValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val hostValue = delegates.stringValue("$P.host")
    private val portValue = delegates.intValue("$P.port", 0)
    private val enabledValue = delegates.booleanValue("$P.enabled", false)
    private val credentialsValue = delegates.stringValue("$P.credentials")

    var host: String? by hostValue
    var port: Int by portValue
    var isEnabled: Boolean by enabledValue
    var credentials: String? by credentialsValue

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.host").remove("$P.port").remove("$P.enabled").remove("$P.credentials")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf("$P.enabled")

    private companion object { const val P = "proxy" }
}
