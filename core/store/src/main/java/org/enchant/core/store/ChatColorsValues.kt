package org.enchant.core.store

class ChatColorsValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val wallpaperValue = delegates.stringValue("$P.wallpaper")
    private val colorValue = delegates.stringValue("$P.color")
    private val systemAccentValue = delegates.booleanValue("$P.system_accent", true)

    var wallpaper: String? by wallpaperValue
    var color: String? by colorValue
    var useSystemAccent: Boolean by systemAccentValue

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.wallpaper").remove("$P.color").remove("$P.system_accent")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf("$P.wallpaper", "$P.color", "$P.system_accent")

    private companion object { const val P = "chat_colors" }
}
