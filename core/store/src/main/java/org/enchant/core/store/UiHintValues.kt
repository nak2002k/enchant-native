package org.enchant.core.store

class UiHintValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val listSwipeValue = delegates.booleanValue("$P.list_swipe", false)
    private val reactionHintValue = delegates.booleanValue("$P.reaction_hint", false)
    private val swipeReplyValue = delegates.booleanValue("$P.swipe_reply", false)
    private val profileNameHintValue = delegates.booleanValue("$P.profile_name_hint", false)
    private val safetyHintValue = delegates.booleanValue("$P.safety_hint", false)

    var hasSeenConversationListSwipe: Boolean by listSwipeValue
    var hasSeenReactionHint: Boolean by reactionHintValue
    var hasSeenSwipeToReply: Boolean by swipeReplyValue
    var hasSeenProfileNameHint: Boolean by profileNameHintValue
    var hasSeenSafetyNumberHint: Boolean by safetyHintValue

    override fun onFirstEverAppLaunch() {
        if (!store.contains("$P.list_swipe")) {
            store.putBoolean("$P.list_swipe", false)
            store.putBoolean("$P.reaction_hint", false)
            store.putBoolean("$P.swipe_reply", false)
            store.putBoolean("$P.profile_name_hint", false)
            store.putBoolean("$P.safety_hint", false)
        }
    }

    fun clear() {
        store.beginWrite()
            .remove("$P.list_swipe").remove("$P.reaction_hint").remove("$P.swipe_reply")
            .remove("$P.profile_name_hint").remove("$P.safety_hint")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf(
        "$P.list_swipe", "$P.reaction_hint", "$P.swipe_reply",
        "$P.profile_name_hint", "$P.safety_hint"
    )

    private companion object { const val P = "ui_hints" }
}
