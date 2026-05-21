package org.enchant.core.store

class NotificationProfileValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val profilesValue = delegates.stringValue("$P.profiles")
    private val activeIdValue = delegates.stringValue("$P.active_id")

    var customProfiles: String? by profilesValue
    var activeProfileId: String? by activeIdValue

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.profiles").remove("$P.active_id")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf("$P.profiles", "$P.active_id")

    private companion object { const val P = "notif_profile" }
}
