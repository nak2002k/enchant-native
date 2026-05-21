package org.enchant.core.store

class PrivacyValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val lastSeenValue = delegates.stringValue("$P.last_seen", "contacts")
    private val onlineValue = delegates.stringValue("$P.online", "contacts")
    private val avatarValue = delegates.stringValue("$P.avatar", "contacts")
    private val aboutVisValue = delegates.stringValue("$P.about_vis", "contacts")
    private val groupsAddValue = delegates.stringValue("$P.groups_add", "everyone")
    private val rrContactsOnlyValue = delegates.booleanValue("$P.rr_contacts_only", false)
    private val typingContactsOnlyValue = delegates.booleanValue("$P.typing_contacts_only", false)

    var lastSeenVisibility: String? by lastSeenValue
    var onlineVisibility: String? by onlineValue
    var avatarVisibility: String? by avatarValue
    var aboutVisibility: String? by aboutVisValue
    var groupsAddPolicy: String? by groupsAddValue
    var readReceiptsForContactsOnly: Boolean by rrContactsOnlyValue
    var typingIndicatorsForContactsOnly: Boolean by typingContactsOnlyValue

    val lastSeenVisibilityFlow = lastSeenValue.toFlow()

    override fun onFirstEverAppLaunch() {
        if (!store.contains("$P.last_seen")) {
            store.putString("$P.last_seen", "contacts")
            store.putString("$P.online", "contacts")
            store.putString("$P.avatar", "contacts")
            store.putString("$P.about_vis", "contacts")
            store.putString("$P.groups_add", "everyone")
        }
    }

    fun clear() {
        store.beginWrite()
            .remove("$P.last_seen").remove("$P.online").remove("$P.avatar")
            .remove("$P.about_vis").remove("$P.groups_add")
            .remove("$P.rr_contacts_only").remove("$P.typing_contacts_only")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf(
        "$P.last_seen", "$P.online", "$P.avatar", "$P.about_vis", "$P.groups_add"
    )

    private companion object { const val P = "privacy" }
}
