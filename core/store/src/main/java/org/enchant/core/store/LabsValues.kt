package org.enchant.core.store

class LabsValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val experimentalValue = delegates.booleanValue("$P.experimental", false)
    private val multiDeviceV2Value = delegates.booleanValue("$P.multi_device_v2", false)
    private val newStorageValue = delegates.booleanValue("$P.new_storage", false)
    private val messageRequestsValue = delegates.booleanValue("$P.message_requests", false)
    private val paymentsValue = delegates.booleanValue("$P.payments", false)
    private val storiesV2Value = delegates.booleanValue("$P.stories_v2", false)

    var experimentalFeatures: Boolean by experimentalValue
    var multiDeviceV2: Boolean by multiDeviceV2Value
    var newStorageService: Boolean by newStorageValue
    var messageRequests: Boolean by messageRequestsValue
    var payments: Boolean by paymentsValue
    var storiesV2: Boolean by storiesV2Value

    override fun onFirstEverAppLaunch() {
        if (!store.contains("$P.experimental")) {
            store.putBoolean("$P.experimental", false)
            store.putBoolean("$P.multi_device_v2", false)
            store.putBoolean("$P.new_storage", false)
        }
    }

    fun clear() {
        store.beginWrite()
            .remove("$P.experimental").remove("$P.multi_device_v2").remove("$P.new_storage")
            .remove("$P.message_requests").remove("$P.payments").remove("$P.stories_v2")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf(
        "$P.experimental", "$P.multi_device_v2", "$P.new_storage",
        "$P.message_requests", "$P.payments", "$P.stories_v2"
    )

    private companion object { const val P = "labs" }
}
