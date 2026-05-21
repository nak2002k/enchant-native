package org.enchant.core.store

class StoriesValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val privacyValue = delegates.stringValue("$P.privacy", "contacts")
    private val introViewedValue = delegates.booleanValue("$P.intro_viewed", false)
    private val lastSendValue = delegates.longValue("$P.last_send", 0L)
    private val viewedReceiptsValue = delegates.booleanValue("$P.viewed_receipts", true)

    var myStoriesPrivacy: String? by privacyValue
    var hasViewedStoryIntro: Boolean by introViewedValue
    var lastStorySendTs: Long by lastSendValue
    var viewedReceiptsEnabled: Boolean by viewedReceiptsValue

    override fun onFirstEverAppLaunch() {
        if (!store.contains("$P.privacy")) {
            store.putString("$P.privacy", "contacts")
            store.putBoolean("$P.viewed_receipts", true)
        }
    }

    fun clear() {
        store.beginWrite()
            .remove("$P.privacy").remove("$P.intro_viewed").remove("$P.last_send").remove("$P.viewed_receipts")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf(
        "$P.privacy", "$P.intro_viewed", "$P.viewed_receipts"
    )

    private companion object { const val P = "stories" }
}
