package org.enchant.core.store

class TooltipValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val chatSearchValue = delegates.booleanValue("$P.chat_search", false)
    private val noteToSelfValue = delegates.booleanValue("$P.note_to_self", false)
    private val reactionsValue = delegates.booleanValue("$P.reactions", false)
    private val storiesValue = delegates.booleanValue("$P.stories", false)

    var hasSeenChatSearchTooltip: Boolean by chatSearchValue
    var hasSeenNoteToSelfTooltip: Boolean by noteToSelfValue
    var hasSeenReactionsTooltip: Boolean by reactionsValue
    var hasSeenStoriesTooltip: Boolean by storiesValue

    override fun onFirstEverAppLaunch() {
        if (!store.contains("$P.chat_search")) {
            store.putBoolean("$P.chat_search", false)
            store.putBoolean("$P.note_to_self", false)
            store.putBoolean("$P.reactions", false)
            store.putBoolean("$P.stories", false)
        }
    }

    fun clear() {
        store.beginWrite()
            .remove("$P.chat_search").remove("$P.note_to_self").remove("$P.reactions").remove("$P.stories")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf(
        "$P.chat_search", "$P.note_to_self", "$P.reactions", "$P.stories"
    )

    private companion object { const val P = "tooltips" }
}
