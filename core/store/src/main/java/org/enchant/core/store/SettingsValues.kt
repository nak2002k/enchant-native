package org.enchant.core.store

class SettingsValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val readReceiptsValue = delegates.booleanValue("$P.read_receipts", true)
    private val typingValue = delegates.booleanValue("$P.typing", true)
    private val linkPreviewsValue = delegates.booleanValue("$P.link_previews", true)
    private val themeValue = delegates.stringValue("$P.theme", "system")
    private val fontSizeValue = delegates.floatValue("$P.font_size", 1.0f)
    private val languageValue = delegates.stringValue("$P.language")
    private val screenLockValue = delegates.booleanValue("$P.screen_lock", false)
    private val screenLockTimeoutValue = delegates.longValue("$P.screen_lock_timeout", 0L)
    private val mediaAutoDlValue = delegates.intValue("$P.media_auto_dl", 0)
    private val mediaAutoDlRoamingValue = delegates.intValue("$P.media_auto_dl_roaming", 0)
    private val keepScreenOnValue = delegates.booleanValue("$P.keep_screen_on_calls", true)
    private val inAppSoundsValue = delegates.booleanValue("$P.in_app_sounds", true)
    private val incognitoKeyboardValue = delegates.booleanValue("$P.incognito_keyboard", false)
    private val spellCheckValue = delegates.booleanValue("$P.spell_check", true)
    private val emojiExtraValue = delegates.booleanValue("$P.emoji_extra", false)
    private val trimLengthValue = delegates.intValue("$P.trim_length", 0)

    var readReceipts: Boolean by readReceiptsValue
    var typingIndicators: Boolean by typingValue
    var linkPreviews: Boolean by linkPreviewsValue
    var theme: String? by themeValue
    var fontSize: Float by fontSizeValue
    var language: String? by languageValue
    var screenLockEnabled: Boolean by screenLockValue
    var screenLockTimeout: Long by screenLockTimeoutValue
    var mediaAutoDownload: Int by mediaAutoDlValue
    var mediaAutoDownloadRoaming: Int by mediaAutoDlRoamingValue
    var keepScreenOnDuringCalls: Boolean by keepScreenOnValue
    var playInAppSounds: Boolean by inAppSoundsValue
    var incognitoKeyboard: Boolean by incognitoKeyboardValue
    var spellCheck: Boolean by spellCheckValue
    var emojiExtraVariants: Boolean by emojiExtraValue
    var messageTrimLength: Int by trimLengthValue

    val readReceiptsFlow = readReceiptsValue.toFlow()
    val typingIndicatorsFlow = typingValue.toFlow()
    val themeFlow = themeValue.toFlow()

    override fun onFirstEverAppLaunch() {
        if (!store.contains("$P.read_receipts")) {
            store.putBoolean("$P.read_receipts", true)
            store.putBoolean("$P.typing", true)
            store.putBoolean("$P.link_previews", true)
            store.putString("$P.theme", "system")
            store.putFloat("$P.font_size", 1.0f)
            store.putBoolean("$P.keep_screen_on_calls", true)
            store.putBoolean("$P.in_app_sounds", true)
            store.putBoolean("$P.spell_check", true)
        }
    }

    fun clear() {
        store.beginWrite()
            .remove("$P.read_receipts").remove("$P.typing").remove("$P.link_previews")
            .remove("$P.theme").remove("$P.font_size").remove("$P.language")
            .remove("$P.screen_lock").remove("$P.screen_lock_timeout")
            .remove("$P.media_auto_dl").remove("$P.media_auto_dl_roaming")
            .remove("$P.keep_screen_on_calls").remove("$P.in_app_sounds")
            .remove("$P.incognito_keyboard").remove("$P.spell_check")
            .remove("$P.emoji_extra").remove("$P.trim_length")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf(
        "$P.read_receipts", "$P.typing", "$P.link_previews", "$P.theme",
        "$P.font_size", "$P.language", "$P.screen_lock", "$P.screen_lock_timeout",
        "$P.media_auto_dl", "$P.media_auto_dl_roaming", "$P.keep_screen_on_calls",
        "$P.in_app_sounds", "$P.incognito_keyboard", "$P.spell_check", "$P.emoji_extra"
    )

    private companion object { const val P = "settings" }
}
