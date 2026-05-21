package org.enchant.core.store

class EmojiValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val recentValue = delegates.stringValue("$P.recent")
    private val variantValue = delegates.intValue("$P.variant", 0)
    private val keyboardHeightValue = delegates.floatValue("$P.keyboard_height", 0f)

    var recent: String? by recentValue
    var variantSelector: Int by variantValue
    var keyboardHeight: Float by keyboardHeightValue

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.recent").remove("$P.variant").remove("$P.keyboard_height")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf("$P.recent", "$P.variant")

    private companion object { const val P = "emoji" }
}
