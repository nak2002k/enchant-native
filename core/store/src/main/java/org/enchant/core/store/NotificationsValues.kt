package org.enchant.core.store

class NotificationsValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val messageValue = delegates.booleanValue("$P.message", true)
    private val previewValue = delegates.booleanValue("$P.preview", true)
    private val soundValue = delegates.stringValue("$P.sound")
    private val vibrateValue = delegates.booleanValue("$P.vibrate", true)
    private val callsValue = delegates.booleanValue("$P.calls", true)
    private val groupsValue = delegates.booleanValue("$P.groups", true)
    private val silentMembersValue = delegates.booleanValue("$P.silent_members", false)

    var messageNotifications: Boolean by messageValue
    var showPreview: Boolean by previewValue
    var sound: String? by soundValue
    var vibrate: Boolean by vibrateValue
    var callNotifications: Boolean by callsValue
    var groupNotifications: Boolean by groupsValue
    var silentMembers: Boolean by silentMembersValue

    val messageNotificationsFlow = messageValue.toFlow()

    override fun onFirstEverAppLaunch() {
        if (!store.contains("$P.message")) {
            store.putBoolean("$P.message", true)
            store.putBoolean("$P.preview", true)
            store.putBoolean("$P.vibrate", true)
            store.putBoolean("$P.calls", true)
            store.putBoolean("$P.groups", true)
        }
    }

    fun clear() {
        store.beginWrite()
            .remove("$P.message").remove("$P.preview").remove("$P.sound")
            .remove("$P.vibrate").remove("$P.calls").remove("$P.groups")
            .remove("$P.silent_members")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf(
        "$P.message", "$P.preview", "$P.sound", "$P.vibrate", "$P.calls", "$P.groups"
    )

    private companion object { const val P = "notifications" }
}
