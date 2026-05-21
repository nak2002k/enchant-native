package org.enchant.core.store

class WallpaperValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val globalValue = delegates.stringValue("$P.global")
    private val systemValue = delegates.booleanValue("$P.system", false)
    private val brightnessValue = delegates.floatValue("$P.brightness", 1.0f)

    var globalWallpaper: String? by globalValue
    var useSystemWallpaper: Boolean by systemValue
    var brightness: Float by brightnessValue

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.global").remove("$P.system").remove("$P.brightness")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf("$P.global", "$P.system", "$P.brightness")

    private companion object { const val P = "wallpaper" }
}
