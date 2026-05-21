package org.enchant.core.store

class PhoneNumberPrivacyValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val shareValue = delegates.booleanValue("$P.share", true)
    private val discoverableValue = delegates.booleanValue("$P.discoverable", true)
    private val lastCheckValue = delegates.longValue("$P.last_check", 0L)

    var shareWithContacts: Boolean by shareValue
    var discoverableByPhoneNumber: Boolean by discoverableValue
    var lastDiscoverableCheckTs: Long by lastCheckValue

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.share").remove("$P.discoverable").remove("$P.last_check")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf("$P.share", "$P.discoverable")

    private companion object { const val P = "phone_privacy" }
}
