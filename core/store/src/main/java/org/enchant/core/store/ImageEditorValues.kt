package org.enchant.core.store

class ImageEditorValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val lastToolValue = delegates.stringValue("$P.last_tool")
    private val brushSizeValue = delegates.floatValue("$P.brush_size", 5.0f)
    private val introSeenValue = delegates.booleanValue("$P.intro_seen", false)

    var lastUsedTool: String? by lastToolValue
    var brushSize: Float by brushSizeValue
    var hasSeenEditorIntro: Boolean by introSeenValue

    override fun onFirstEverAppLaunch() {
        if (!store.contains("$P.brush_size")) {
            store.putFloat("$P.brush_size", 5.0f)
            store.putBoolean("$P.intro_seen", false)
        }
    }

    fun clear() {
        store.beginWrite()
            .remove("$P.last_tool").remove("$P.brush_size").remove("$P.intro_seen")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf("$P.brush_size")

    private companion object { const val P = "image_editor" }
}
