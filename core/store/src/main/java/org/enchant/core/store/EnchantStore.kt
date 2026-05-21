package org.enchant.core.store

import android.content.Context
import org.enchant.core.base.SecurePreferences
import kotlinx.coroutines.flow.Flow

/**
 * Centralized encrypted key-value store for Enchant.
 *
 * Architecture:
 * - SQLite-backed via [KeyValueStore] (encrypted with SQLCipher)
 * - Kotlin property delegates for clean access (`by delegates.booleanValue(...)`)
 * - Reactive [Flow] support for UI observation
 * - Migration framework for schema evolution
 * - Backup awareness via [getKeysToIncludeInBackup]
 * - First-launch defaults via [onFirstEverAppLaunch]
 *
 * Usage:
 * ```
 * EnchantStore.init(context)
 * EnchantStore.Account.setUserId("user-123")
 * val id = EnchantStore.Account.userId
 *
 * // Reactive:
 * EnchantStore.Settings.readReceiptsFlow.collect { enabled -> ... }
 * ```
 */
object EnchantStore {

    private var initialized = false
    internal lateinit var store: KeyValueStorage
        private set

    val storage: KeyValueStorage
        get() = store
    internal lateinit var delegates: StoreValueDelegates
        private set

    /**
     * Initializes the store. Must be called once at app startup.
     *
     * @param context Android context
     * @param password Encryption password for SQLCipher. If null, derives from SecurePreferences.
     */
    fun init(context: Context, password: String? = null) {
        val dbPassword = password ?: derivePassword(context)
        init(KeyValueStore(context, dbPassword))
        migrateFromLegacyPreferences(context)
    }

    /**
     * Initializes the store with a custom [KeyValueStorage] implementation.
     * Used for testing with in-memory storage.
     */
    internal fun init(storage: KeyValueStorage) {
        if (initialized) return
        store = storage
        delegates = StoreValueDelegates(storage)
        onFirstEverAppLaunch()
        initialized = true
    }

    fun flushPendingWrites() {
        store.flushPendingWrites()
    }

    private fun derivePassword(context: Context): String {
        SecurePreferences.init(context)
        val existing = SecurePreferences.getString("enchant.store.password")
        if (existing != null) return existing

        val newPassword = java.util.UUID.randomUUID().toString()
        SecurePreferences.putString("enchant.store.password", newPassword)
        return newPassword
    }

    // -- First launch defaults -----------------------------------------------------------

    private var firstLaunchKey = "enchant.first_launch_done"

    private fun onFirstEverAppLaunch() {
        if (store.getBoolean(firstLaunchKey, false)) return

        Account.onFirstEverAppLaunch()
        Settings.onFirstEverAppLaunch()
        Notifications.onFirstEverAppLaunch()
        Privacy.onFirstEverAppLaunch()
        CallQuality.onFirstEverAppLaunch()
        Labs.onFirstEverAppLaunch()
        Stories.onFirstEverAppLaunch()
        RemoteConfig.onFirstEverAppLaunch()
        UiHints.onFirstEverAppLaunch()
        Tooltips.onFirstEverAppLaunch()
        ImageEditor.onFirstEverAppLaunch()
        ReleaseChannel.onFirstEverAppLaunch()

        store.putBoolean(firstLaunchKey, true)
    }

    // -- Migration from legacy SharedPreferences -----------------------------------------

    private fun migrateFromLegacyPreferences(context: Context) {
        val migrationKey = "enchant.migration_v1_done"
        if (store.getBoolean(migrationKey, false)) return

        SecurePreferences.init(context)
        var migrated = false

        migrated = migrateString("enchant.account.user_id") || migrated
        migrated = migrateString("enchant.account.device_id") || migrated
        migrated = migrateString("enchant.account.username") || migrated
        migrated = migrateString("enchant.account.display_name") || migrated
        migrated = migrateString("enchant.account.about") || migrated
        migrated = migrateInt("enchant.account.reg_id") || migrated
        migrated = migrateString("enchant.account.aci") || migrated
        migrated = migrateString("enchant.account.pni") || migrated
        migrated = migrateBoolean("enchant.reg.complete") || migrated
        migrated = migrateBoolean("enchant.backup.enabled") || migrated
        migrated = migrateLong("enchant.backup.last_ts") || migrated
        migrated = migrateString("enchant.backup.key") || migrated
        migrated = migrateBoolean("enchant.settings.read_receipts") || migrated
        migrated = migrateBoolean("enchant.settings.typing") || migrated
        migrated = migrateBoolean("enchant.settings.link_previews") || migrated
        migrated = migrateString("enchant.settings.theme") || migrated
        migrated = migrateFloat("enchant.settings.font_size") || migrated
        migrated = migrateString("enchant.settings.language") || migrated
        migrated = migrateBoolean("enchant.notif.message") || migrated
        migrated = migrateBoolean("enchant.notif.preview") || migrated
        migrated = migrateString("enchant.notif.sound") || migrated
        migrated = migrateBoolean("enchant.notif.vibrate") || migrated
        migrated = migrateString("enchant.privacy.last_seen") || migrated
        migrated = migrateString("enchant.privacy.online") || migrated
        migrated = migrateString("enchant.privacy.avatar") || migrated
        migrated = migrateString("enchant.privacy.about_vis") || migrated
        migrated = migrateString("enchant.privacy.groups_add") || migrated
        migrated = migrateString("enchant.pin.hash") || migrated
        migrated = migrateString("enchant.pin.salt") || migrated
        migrated = migrateInt("enchant.pin.fails") || migrated
        migrated = migrateBoolean("enchant.onboard.complete") || migrated
        migrated = migrateBoolean("enchant.onboard.welcome") || migrated
        migrated = migrateString("enchant.proxy.host") || migrated
        migrated = migrateInt("enchant.proxy.port") || migrated
        migrated = migrateLong("enchant.ratelimit.otp") || migrated
        migrated = migrateInt("enchant.ratelimit.otp_count") || migrated
        migrated = migrateBoolean("enchant.phone_privacy.share") || migrated
        migrated = migrateString("enchant.emoji.recent") || migrated
        migrated = migrateString("enchant.chat_colors.wallpaper") || migrated
        migrated = migrateString("enchant.chat_colors.color") || migrated
        migrated = migrateBoolean("enchant.call_quality.low_bw") || migrated
        migrated = migrateBoolean("enchant.labs.experimental") || migrated
        migrated = migrateString("enchant.stories.privacy") || migrated
        migrated = migrateLong("enchant.internal.sync_ts") || migrated
        migrated = migrateLong("enchant.internal.prekey_ts") || migrated

        if (migrated) {
            store.putBoolean(migrationKey, true)
        }
    }

    private fun migrateString(legacyKey: String): Boolean {
        val value = SecurePreferences.getString(legacyKey) ?: return false
        store.putString(legacyKey, value)
        SecurePreferences.remove(legacyKey)
        return true
    }

    private fun migrateInt(legacyKey: String): Boolean {
        val value = SecurePreferences.getInt(legacyKey, Int.MIN_VALUE)
        if (value == Int.MIN_VALUE) return false
        store.putInt(legacyKey, value)
        SecurePreferences.remove(legacyKey)
        return true
    }

    private fun migrateLong(legacyKey: String): Boolean {
        val value = SecurePreferences.getLong(legacyKey, Long.MIN_VALUE)
        if (value == Long.MIN_VALUE) return false
        store.putLong(legacyKey, value)
        SecurePreferences.remove(legacyKey)
        return true
    }

    private fun migrateBoolean(legacyKey: String): Boolean {
        val value = SecurePreferences.getBoolean(legacyKey, null)
        if (value == null) return false
        store.putBoolean(legacyKey, value)
        SecurePreferences.remove(legacyKey)
        return true
    }

    private fun migrateFloat(legacyKey: String): Boolean {
        val value = SecurePreferences.getFloat(legacyKey, Float.MIN_VALUE)
        if (value == Float.MIN_VALUE) return false
        store.putFloat(legacyKey, value)
        SecurePreferences.remove(legacyKey)
        return true
    }

    // -- Account --------------------------------------------------------------------------

    object Account {
        private const val P = "account"

        val userId: String? get() = store.getString("$P.user_id")
        fun setUserId(v: String) = store.putString("$P.user_id", v)

        val deviceId : String? get() = store.getString("$P.device_id")
        fun setDeviceId(v: String) = store.putString("$P.device_id", v)

        val username : String? get() = store.getString("$P.username")
        fun setUsername(v: String) = store.putString("$P.username", v)

        val displayName : String? get() = store.getString("$P.display_name")
        fun setDisplayName(v: String) = store.putString("$P.display_name", v)

        val about : String? get() = store.getString("$P.about")
        fun setAbout(v: String) = store.putString("$P.about", v)

        val registrationId: Int get() = store.getInt("$P.reg_id", 0)
        fun setRegistrationId(v: Int) = store.putInt("$P.reg_id", v)

        val aci : String? get() = store.getString("$P.aci")
        fun setAci(v: String) = store.putString("$P.aci", v)

        val pni : String? get() = store.getString("$P.pni")
        fun setPni(v: String) = store.putString("$P.pni", v)

        val fcmToken : String? get() = store.getString("$P.fcm_token")
        fun setFcmToken(v: String) = store.putString("$P.fcm_token", v)

        val isRegistered: Boolean get() = store.getBoolean("$P.registered", false)
        fun setRegistered(v: Boolean) = store.putBoolean("$P.registered", v)

        val usernameLinkHandle : String? get() = store.getString("$P.username_link")
        fun setUsernameLinkHandle(v: String) = store.putString("$P.username_link", v)

        val entitlements : String? get() = store.getString("$P.entitlements")
        fun setEntitlements(v: String) = store.putString("$P.entitlements", v)

        val number : String? get() = store.getString("$P.number")
        fun setNumber(v: String) = store.putString("$P.number", v)

        val unidentifiedAccessKey : String? get() = store.getString("$P.ua_key")
        fun setUnidentifiedAccessKey(v: String) = store.putString("$P.ua_key", v)

        val unrestrictedUnidentifiedAccess: Boolean get() = store.getBoolean("$P.unrestricted_ua", false)
        fun setUnrestrictedUnidentifiedAccess(v: Boolean) = store.putBoolean("$P.unrestricted_ua", v)

        val hasSeenOnboarding: Boolean get() = store.getBoolean("$P.has_seen_onboarding", false)
        fun setHasSeenOnboarding(v: Boolean) = store.putBoolean("$P.has_seen_onboarding", v)

        val multiDevice: Boolean get() = store.getBoolean("$P.multi_device", false)
        fun setMultiDevice(v: Boolean) = store.putBoolean("$P.multi_device", v)

        val capabilities : String? get() = store.getString("$P.capabilities")
        fun setCapabilities(v: String) = store.putString("$P.capabilities", v)

        fun onFirstEverAppLaunch() {
            if (!store.contains("$P.registered")) {
                store.putBoolean("$P.registered", false)
            }
        }

        fun clear() {
            store.beginWrite()
                .remove("$P.user_id")
                .remove("$P.device_id")
                .remove("$P.username")
                .remove("$P.display_name")
                .remove("$P.about")
                .remove("$P.reg_id")
                .remove("$P.aci")
                .remove("$P.pni")
                .remove("$P.fcm_token")
                .remove("$P.registered")
                .remove("$P.username_link")
                .remove("$P.entitlements")
                .remove("$P.number")
                .remove("$P.ua_key")
                .remove("$P.unrestricted_ua")
                .remove("$P.has_seen_onboarding")
                .remove("$P.multi_device")
                .remove("$P.capabilities")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf(
            "$P.user_id", "$P.device_id", "$P.username", "$P.display_name", "$P.about",
            "$P.reg_id", "$P.aci", "$P.pni", "$P.registered", "$P.number",
            "$P.ua_key", "$P.unrestricted_ua", "$P.multi_device", "$P.capabilities"
        )
    }

    // -- Registration ---------------------------------------------------------------------

    object Registration {
        private const val P = "registration"

        val isComplete: Boolean get() = store.getBoolean("$P.complete", false)
        fun setComplete(v: Boolean) = store.putBoolean("$P.complete", v)

        val lockPin : String? get() = store.getString("$P.lock_pin")
        fun setLockPin(v: String) = store.putString("$P.lock_pin", v)

        val restoreDecisionState : String? get() = store.getString("$P.restore_state")
        fun setRestoreDecisionState(v: String) = store.putString("$P.restore_state", v)

        val sessionId : String? get() = store.getString("$P.session_id")
        fun setSessionId(v: String) = store.putString("$P.session_id", v)

        val localRegistrationId: Int get() = store.getInt("$P.local_reg_id", 0)
        fun setLocalRegistrationId(v: Int) = store.putInt("$P.local_reg_id", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.complete")
                .remove("$P.lock_pin")
                .remove("$P.restore_state")
                .remove("$P.session_id")
                .remove("$P.local_reg_id")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf(
            "$P.complete", "$P.local_reg_id"
        )
    }

    // -- Backup ---------------------------------------------------------------------------

    object Backup {
        private const val P = "backup"

        val isEnabled: Boolean get() = store.getBoolean("$P.enabled", false)
        fun setEnabled(v: Boolean) = store.putBoolean("$P.enabled", v)

        val lastBackupTs: Long get() = store.getLong("$P.last_ts", 0L)
        fun setLastBackupTs(v: Long) = store.putLong("$P.last_ts", v)

        val backupKey : String? get() = store.getString("$P.key")
        fun setBackupKey(v: String) = store.putString("$P.key", v)

        val svrMasterKey : String? get() = store.getString("$P.svr_master_key")
        fun setSvrMasterKey(v: String) = store.putString("$P.svr_master_key", v)

        val backupCdnCredentials : String? get() = store.getString("$P.cdn_creds")
        fun setBackupCdnCredentials(v: String) = store.putString("$P.cdn_creds", v)

        val backupTier: Int get() = store.getInt("$P.tier", 0)
        fun setBackupTier(v: Int) = store.putInt("$P.tier", v)

        val isMediaBackupEnabled: Boolean get() = store.getBoolean("$P.media_enabled", false)
        fun setMediaBackupEnabled(v: Boolean) = store.putBoolean("$P.media_enabled", v)

        val lastMediaBackupTs: Long get() = store.getLong("$P.media_last_ts", 0L)
        fun setLastMediaBackupTs(v: Long) = store.putLong("$P.media_last_ts", v)

        val archiveUploadState : String? get() = store.getString("$P.archive_state")
        fun setArchiveUploadState(v: String) = store.putString("$P.archive_state", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.enabled")
                .remove("$P.last_ts")
                .remove("$P.key")
                .remove("$P.svr_master_key")
                .remove("$P.cdn_creds")
                .remove("$P.tier")
                .remove("$P.media_enabled")
                .remove("$P.media_last_ts")
                .remove("$P.archive_state")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = emptyList()
    }

    // -- Settings -------------------------------------------------------------------------

    object Settings {
        private const val P = "settings"

        val readReceipts: Boolean get() = store.getBoolean("$P.read_receipts", true)
        fun setReadReceipts(v: Boolean) = store.putBoolean("$P.read_receipts", v)

        val typingIndicators: Boolean get() = store.getBoolean("$P.typing", true)
        fun setTypingIndicators(v: Boolean) = store.putBoolean("$P.typing", v)

        val linkPreviews: Boolean get() = store.getBoolean("$P.link_previews", true)
        fun setLinkPreviews(v: Boolean) = store.putBoolean("$P.link_previews", v)

        val theme : String? get() = store.getString("$P.theme", "system")
        fun setTheme(v: String) = store.putString("$P.theme", v)

        val fontSize: Float get() = store.getFloat("$P.font_size", 1.0f)
        fun setFontSize(v: Float) = store.putFloat("$P.font_size", v)

        val language : String? get() = store.getString("$P.language")
        fun setLanguage(v: String) = store.putString("$P.language", v)

        val screenLockEnabled: Boolean get() = store.getBoolean("$P.screen_lock", false)
        fun setScreenLockEnabled(v: Boolean) = store.putBoolean("$P.screen_lock", v)

        val screenLockTimeout: Long get() = store.getLong("$P.screen_lock_timeout", 0L)
        fun setScreenLockTimeout(v: Long) = store.putLong("$P.screen_lock_timeout", v)

        val mediaAutoDownload: Int get() = store.getInt("$P.media_auto_dl", 0)
        fun setMediaAutoDownload(v: Int) = store.putInt("$P.media_auto_dl", v)

        val mediaAutoDownloadRoaming: Int get() = store.getInt("$P.media_auto_dl_roaming", 0)
        fun setMediaAutoDownloadRoaming(v: Int) = store.putInt("$P.media_auto_dl_roaming", v)

        val keepScreenOnDuringCalls: Boolean get() = store.getBoolean("$P.keep_screen_on_calls", true)
        fun setKeepScreenOnDuringCalls(v: Boolean) = store.putBoolean("$P.keep_screen_on_calls", v)

        val playInAppSounds: Boolean get() = store.getBoolean("$P.in_app_sounds", true)
        fun setPlayInAppSounds(v: Boolean) = store.putBoolean("$P.in_app_sounds", v)

        val incognitoKeyboard: Boolean get() = store.getBoolean("$P.incognito_keyboard", false)
        fun setIncognitoKeyboard(v: Boolean) = store.putBoolean("$P.incognito_keyboard", v)

        val spellCheck: Boolean get() = store.getBoolean("$P.spell_check", true)
        fun setSpellCheck(v: Boolean) = store.putBoolean("$P.spell_check", v)

        val emojiExtraVariants: Boolean get() = store.getBoolean("$P.emoji_extra", false)
        fun setEmojiExtraVariants(v: Boolean) = store.putBoolean("$P.emoji_extra", v)

        val messageTrimLength: Int get() = store.getInt("$P.trim_length", 0)
        fun setMessageTrimLength(v: Int) = store.putInt("$P.trim_length", v)

        fun onFirstEverAppLaunch() {
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
                .remove("$P.read_receipts")
                .remove("$P.typing")
                .remove("$P.link_previews")
                .remove("$P.theme")
                .remove("$P.font_size")
                .remove("$P.language")
                .remove("$P.screen_lock")
                .remove("$P.screen_lock_timeout")
                .remove("$P.media_auto_dl")
                .remove("$P.media_auto_dl_roaming")
                .remove("$P.keep_screen_on_calls")
                .remove("$P.in_app_sounds")
                .remove("$P.incognito_keyboard")
                .remove("$P.spell_check")
                .remove("$P.emoji_extra")
                .remove("$P.trim_length")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf(
            "$P.read_receipts", "$P.typing", "$P.link_previews", "$P.theme",
            "$P.font_size", "$P.language", "$P.screen_lock", "$P.screen_lock_timeout",
            "$P.media_auto_dl", "$P.media_auto_dl_roaming", "$P.keep_screen_on_calls",
            "$P.in_app_sounds", "$P.incognito_keyboard", "$P.spell_check", "$P.emoji_extra"
        )
    }

    // -- Notifications --------------------------------------------------------------------

    object Notifications {
        private const val P = "notifications"

        val messageNotifications: Boolean get() = store.getBoolean("$P.message", true)
        fun setMessageNotifications(v: Boolean) = store.putBoolean("$P.message", v)

        val showPreview: Boolean get() = store.getBoolean("$P.preview", true)
        fun setShowPreview(v: Boolean) = store.putBoolean("$P.preview", v)

        val sound : String? get() = store.getString("$P.sound")
        fun setSound(v: String) = store.putString("$P.sound", v)

        val vibrate: Boolean get() = store.getBoolean("$P.vibrate", true)
        fun setVibrate(v: Boolean) = store.putBoolean("$P.vibrate", v)

        val callNotifications: Boolean get() = store.getBoolean("$P.calls", true)
        fun setCallNotifications(v: Boolean) = store.putBoolean("$P.calls", v)

        val groupNotifications: Boolean get() = store.getBoolean("$P.groups", true)
        fun setGroupNotifications(v: Boolean) = store.putBoolean("$P.groups", v)

        val silentMembers: Boolean get() = store.getBoolean("$P.silent_members", false)
        fun setSilentMembers(v: Boolean) = store.putBoolean("$P.silent_members", v)

        fun onFirstEverAppLaunch() {
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
                .remove("$P.message")
                .remove("$P.preview")
                .remove("$P.sound")
                .remove("$P.vibrate")
                .remove("$P.calls")
                .remove("$P.groups")
                .remove("$P.silent_members")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf(
            "$P.message", "$P.preview", "$P.sound", "$P.vibrate", "$P.calls", "$P.groups"
        )
    }

    // -- Privacy --------------------------------------------------------------------------

    object Privacy {
        private const val P = "privacy"

        val lastSeenVisibility : String? get() = store.getString("$P.last_seen", "contacts")
        fun setLastSeenVisibility(v: String) = store.putString("$P.last_seen", v)

        val onlineVisibility : String? get() = store.getString("$P.online", "contacts")
        fun setOnlineVisibility(v: String) = store.putString("$P.online", v)

        val avatarVisibility : String? get() = store.getString("$P.avatar", "contacts")
        fun setAvatarVisibility(v: String) = store.putString("$P.avatar", v)

        val aboutVisibility : String? get() = store.getString("$P.about_vis", "contacts")
        fun setAboutVisibility(v: String) = store.putString("$P.about_vis", v)

        val groupsAddPolicy : String? get() = store.getString("$P.groups_add", "everyone")
        fun setGroupsAddPolicy(v: String) = store.putString("$P.groups_add", v)

        val readReceiptsForContactsOnly: Boolean get() = store.getBoolean("$P.rr_contacts_only", false)
        fun setReadReceiptsForContactsOnly(v: Boolean) = store.putBoolean("$P.rr_contacts_only", v)

        val typingIndicatorsForContactsOnly: Boolean get() = store.getBoolean("$P.typing_contacts_only", false)
        fun setTypingIndicatorsForContactsOnly(v: Boolean) = store.putBoolean("$P.typing_contacts_only", v)

        fun onFirstEverAppLaunch() {
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
                .remove("$P.last_seen")
                .remove("$P.online")
                .remove("$P.avatar")
                .remove("$P.about_vis")
                .remove("$P.groups_add")
                .remove("$P.rr_contacts_only")
                .remove("$P.typing_contacts_only")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf(
            "$P.last_seen", "$P.online", "$P.avatar", "$P.about_vis", "$P.groups_add"
        )
    }

    // -- Pin ------------------------------------------------------------------------------

    object Pin {
        private const val P = "pin"

        val hash : String? get() = store.getString("$P.hash")
        fun setHash(v: String) = store.putString("$P.hash", v)

        val salt : String? get() = store.getString("$P.salt")
        fun setSalt(v: String) = store.putString("$P.salt", v)

        val failedAttempts: Int get() = store.getInt("$P.fails", 0)
        fun setFailedAttempts(v: Int) = store.putInt("$P.fails", v)

        val pinLength: Int get() = store.getInt("$P.length", 0)
        fun setPinLength(v: Int) = store.putInt("$P.length", v)

        val isRegistrationLockEnabled: Boolean get() = store.getBoolean("$P.reg_lock", false)
        fun setRegistrationLockEnabled(v: Boolean) = store.putBoolean("$P.reg_lock", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.hash")
                .remove("$P.salt")
                .remove("$P.fails")
                .remove("$P.length")
                .remove("$P.reg_lock")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf(
            "$P.hash", "$P.salt", "$P.length", "$P.reg_lock"
        )
    }

    // -- Onboarding -----------------------------------------------------------------------

    object Onboarding {
        private const val P = "onboarding"

        val isComplete: Boolean get() = store.getBoolean("$P.complete", false)
        fun setComplete(v: Boolean) = store.putBoolean("$P.complete", v)

        val hasSeenWelcome: Boolean get() = store.getBoolean("$P.welcome", false)
        fun setHasSeenWelcome(v: Boolean) = store.putBoolean("$P.welcome", v)

        val hasSeenPermissions: Boolean get() = store.getBoolean("$P.permissions", false)
        fun setHasSeenPermissions(v: Boolean) = store.putBoolean("$P.permissions", v)

        val hasSeenProfileSetup: Boolean get() = store.getBoolean("$P.profile_setup", false)
        fun setHasSeenProfileSetup(v: Boolean) = store.putBoolean("$P.profile_setup", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.complete")
                .remove("$P.welcome")
                .remove("$P.permissions")
                .remove("$P.profile_setup")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf("$P.complete")
    }

    // -- Proxy ----------------------------------------------------------------------------

    object Proxy {
        private const val P = "proxy"

        val host : String? get() = store.getString("$P.host")
        fun setHost(v: String) = store.putString("$P.host", v)

        val port: Int get() = store.getInt("$P.port", 0)
        fun setPort(v: Int) = store.putInt("$P.port", v)

        val isEnabled: Boolean get() = store.getBoolean("$P.enabled", false)
        fun setEnabled(v: Boolean) = store.putBoolean("$P.enabled", v)

        val credentials : String? get() = store.getString("$P.credentials")
        fun setCredentials(v: String) = store.putString("$P.credentials", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.host")
                .remove("$P.port")
                .remove("$P.enabled")
                .remove("$P.credentials")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf("$P.enabled")
    }

    // -- RateLimit ------------------------------------------------------------------------

    object RateLimit {
        private const val P = "ratelimit"

        val lastOtpMs: Long get() = store.getLong("$P.otp", 0L)
        fun setLastOtpMs(v: Long) = store.putLong("$P.otp", v)

        val otpAttempts: Int get() = store.getInt("$P.otp_count", 0)
        fun setOtpAttempts(v: Int) = store.putInt("$P.otp_count", v)

        val lastKeyRegistrationMs: Long get() = store.getLong("$P.key_reg", 0L)
        fun setLastKeyRegistrationMs(v: Long) = store.putLong("$P.key_reg", v)

        val lastProfileUpdateMs: Long get() = store.getLong("$P.profile_update", 0L)
        fun setLastProfileUpdateMs(v: Long) = store.putLong("$P.profile_update", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.otp")
                .remove("$P.otp_count")
                .remove("$P.key_reg")
                .remove("$P.profile_update")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = emptyList()
    }

    // -- PhoneNumberPrivacy ---------------------------------------------------------------

    object PhoneNumberPrivacy {
        private const val P = "phone_privacy"

        val shareWithContacts: Boolean get() = store.getBoolean("$P.share", true)
        fun setShareWithContacts(v: Boolean) = store.putBoolean("$P.share", v)

        val discoverableByPhoneNumber: Boolean get() = store.getBoolean("$P.discoverable", true)
        fun setDiscoverableByPhoneNumber(v: Boolean) = store.putBoolean("$P.discoverable", v)

        val lastDiscoverableCheckTs: Long get() = store.getLong("$P.last_check", 0L)
        fun setLastDiscoverableCheckTs(v: Long) = store.putLong("$P.last_check", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.share")
                .remove("$P.discoverable")
                .remove("$P.last_check")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf("$P.share", "$P.discoverable")
    }

    // -- Emoji ----------------------------------------------------------------------------

    object Emoji {
        private const val P = "emoji"

        val recent : String? get() = store.getString("$P.recent")
        fun setRecent(v: String) = store.putString("$P.recent", v)

        val variantSelector: Int get() = store.getInt("$P.variant", 0)
        fun setVariantSelector(v: Int) = store.putInt("$P.variant", v)

        val keyboardHeight: Float get() = store.getFloat("$P.keyboard_height", 0f)
        fun setKeyboardHeight(v: Float) = store.putFloat("$P.keyboard_height", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.recent")
                .remove("$P.variant")
                .remove("$P.keyboard_height")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf("$P.recent", "$P.variant")
    }

    // -- ChatColors -----------------------------------------------------------------------

    object ChatColors {
        private const val P = "chat_colors"

        val wallpaper : String? get() = store.getString("$P.wallpaper")
        fun setWallpaper(v: String) = store.putString("$P.wallpaper", v)

        val color : String? get() = store.getString("$P.color")
        fun setColor(v: String) = store.putString("$P.color", v)

        val useSystemAccent: Boolean get() = store.getBoolean("$P.system_accent", true)
        fun setUseSystemAccent(v: Boolean) = store.putBoolean("$P.system_accent", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.wallpaper")
                .remove("$P.color")
                .remove("$P.system_accent")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf("$P.wallpaper", "$P.color", "$P.system_accent")
    }

    // -- CallQuality ----------------------------------------------------------------------

    object CallQuality {
        private const val P = "call_quality"

        val useLowBandwidth: Boolean get() = store.getBoolean("$P.low_bw", false)
        fun setUseLowBandwidth(v: Boolean) = store.putBoolean("$P.low_bw", v)

        val alwaysRelayCalls: Boolean get() = store.getBoolean("$P.always_relay", false)
        fun setAlwaysRelayCalls(v: Boolean) = store.putBoolean("$P.always_relay", v)

        val useDirectP2P: Boolean get() = store.getBoolean("$P.direct_p2p", true)
        fun setUseDirectP2P(v: Boolean) = store.putBoolean("$P.direct_p2p", v)

        val dataSavingMode: Boolean get() = store.getBoolean("$P.data_saving", false)
        fun setDataSavingMode(v: Boolean) = store.putBoolean("$P.data_saving", v)

        fun onFirstEverAppLaunch() {
            if (!store.contains("$P.low_bw")) {
                store.putBoolean("$P.low_bw", false)
                store.putBoolean("$P.always_relay", false)
                store.putBoolean("$P.direct_p2p", true)
            }
        }

        fun clear() {
            store.beginWrite()
                .remove("$P.low_bw")
                .remove("$P.always_relay")
                .remove("$P.direct_p2p")
                .remove("$P.data_saving")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf(
            "$P.low_bw", "$P.always_relay", "$P.direct_p2p", "$P.data_saving"
        )
    }

    // -- Labs -----------------------------------------------------------------------------

    object Labs {
        private const val P = "labs"

        val experimentalFeatures: Boolean get() = store.getBoolean("$P.experimental", false)
        fun setExperimentalFeatures(v: Boolean) = store.putBoolean("$P.experimental", v)

        val multiDeviceV2: Boolean get() = store.getBoolean("$P.multi_device_v2", false)
        fun setMultiDeviceV2(v: Boolean) = store.putBoolean("$P.multi_device_v2", v)

        val newStorageService: Boolean get() = store.getBoolean("$P.new_storage", false)
        fun setNewStorageService(v: Boolean) = store.putBoolean("$P.new_storage", v)

        val messageRequests: Boolean get() = store.getBoolean("$P.message_requests", false)
        fun setMessageRequests(v: Boolean) = store.putBoolean("$P.message_requests", v)

        val payments: Boolean get() = store.getBoolean("$P.payments", false)
        fun setPayments(v: Boolean) = store.putBoolean("$P.payments", v)

        val storiesV2: Boolean get() = store.getBoolean("$P.stories_v2", false)
        fun setStoriesV2(v: Boolean) = store.putBoolean("$P.stories_v2", v)

        fun onFirstEverAppLaunch() {
            if (!store.contains("$P.experimental")) {
                store.putBoolean("$P.experimental", false)
                store.putBoolean("$P.multi_device_v2", false)
                store.putBoolean("$P.new_storage", false)
            }
        }

        fun clear() {
            store.beginWrite()
                .remove("$P.experimental")
                .remove("$P.multi_device_v2")
                .remove("$P.new_storage")
                .remove("$P.message_requests")
                .remove("$P.payments")
                .remove("$P.stories_v2")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf(
            "$P.experimental", "$P.multi_device_v2", "$P.new_storage",
            "$P.message_requests", "$P.payments", "$P.stories_v2"
        )
    }

    // -- Stories --------------------------------------------------------------------------

    object Stories {
        private const val P = "stories"

        val myStoriesPrivacy : String? get() = store.getString("$P.privacy", "contacts")
        fun setMyStoriesPrivacy(v: String) = store.putString("$P.privacy", v)

        val hasViewedStoryIntro: Boolean get() = store.getBoolean("$P.intro_viewed", false)
        fun setHasViewedStoryIntro(v: Boolean) = store.putBoolean("$P.intro_viewed", v)

        val lastStorySendTs: Long get() = store.getLong("$P.last_send", 0L)
        fun setLastStorySendTs(v: Long) = store.putLong("$P.last_send", v)

        val viewedReceiptsEnabled: Boolean get() = store.getBoolean("$P.viewed_receipts", true)
        fun setViewedReceiptsEnabled(v: Boolean) = store.putBoolean("$P.viewed_receipts", v)

        fun onFirstEverAppLaunch() {
            if (!store.contains("$P.privacy")) {
                store.putString("$P.privacy", "contacts")
                store.putBoolean("$P.viewed_receipts", true)
            }
        }

        fun clear() {
            store.beginWrite()
                .remove("$P.privacy")
                .remove("$P.intro_viewed")
                .remove("$P.last_send")
                .remove("$P.viewed_receipts")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf(
            "$P.privacy", "$P.intro_viewed", "$P.viewed_receipts"
        )
    }

    // -- Internal -------------------------------------------------------------------------

    object Internal {
        private const val P = "internal"

        val lastDeviceSyncTs: Long get() = store.getLong("$P.sync_ts", 0L)
        fun setLastDeviceSyncTs(v: Long) = store.putLong("$P.sync_ts", v)

        val lastPreKeyRotationTs: Long get() = store.getLong("$P.prekey_ts", 0L)
        fun setLastPreKeyRotationTs(v: Long) = store.putLong("$P.prekey_ts", v)

        val lastMessageTrimTs: Long get() = store.getLong("$P.trim_ts", 0L)
        fun setLastMessageTrimTs(v: Long) = store.putLong("$P.trim_ts", v)

        val hasCompletedFirstSync: Boolean get() = store.getBoolean("$P.first_sync", false)
        fun setHasCompletedFirstSync(v: Boolean) = store.putBoolean("$P.first_sync", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.sync_ts")
                .remove("$P.prekey_ts")
                .remove("$P.trim_ts")
                .remove("$P.first_sync")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = emptyList()
    }

    // -- SVR (Secure Value Recovery) ------------------------------------------------------

    object Svr {
        private const val P = "svr"

        val masterKey : String? get() = store.getString("$P.master_key")
        fun setMasterKey(v: String) = store.putString("$P.master_key", v)

        val backupId : String? get() = store.getString("$P.backup_id")
        fun setBackupId(v: String) = store.putString("$P.backup_id", v)

        val lastRestoreTs: Long get() = store.getLong("$P.last_restore", 0L)
        fun setLastRestoreTs(v: Long) = store.putLong("$P.last_restore", v)

        val isConfigured: Boolean get() = store.getBoolean("$P.configured", false)
        fun setIsConfigured(v: Boolean) = store.putBoolean("$P.configured", v)

        val pinHash : String? get() = store.getString("$P.pin_hash")
        fun setPinHash(v: String) = store.putString("$P.pin_hash", v)

        val salt : String? get() = store.getString("$P.salt")
        fun setSalt(v: String) = store.putString("$P.salt", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.master_key")
                .remove("$P.backup_id")
                .remove("$P.last_restore")
                .remove("$P.configured")
                .remove("$P.pin_hash")
                .remove("$P.salt")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf(
            "$P.backup_id", "$P.configured"
        )
    }

    // -- RemoteConfig ---------------------------------------------------------------------

    object RemoteConfig {
        private const val P = "remote_config"

        val values : String? get() = store.getString("$P.values")
        fun setValues(v: String) = store.putString("$P.values", v)

        val lastFetchTs: Long get() = store.getLong("$P.last_fetch", 0L)
        fun setLastFetchTs(v: Long) = store.putLong("$P.last_fetch", v)

        val eTag : String? get() = store.getString("$P.etag")
        fun setETag(v: String) = store.putString("$P.etag", v)

        fun onFirstEverAppLaunch() {
            if (!store.contains("$P.last_fetch")) {
                store.putLong("$P.last_fetch", 0L)
            }
        }

        fun clear() {
            store.beginWrite()
                .remove("$P.values")
                .remove("$P.last_fetch")
                .remove("$P.etag")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = emptyList()
    }

    // -- StorageService -------------------------------------------------------------------

    object StorageService {
        private const val P = "storage_service"

        val manifestVersion: Int get() = store.getInt("$P.manifest_version", 0)
        fun setManifestVersion(v: Int) = store.putInt("$P.manifest_version", v)

        val lastSyncTs: Long get() = store.getLong("$P.last_sync", 0L)
        fun setLastSyncTs(v: Long) = store.putLong("$P.last_sync", v)

        val storageKey : String? get() = store.getString("$P.storage_key")
        fun setStorageKey(v: String) = store.putString("$P.storage_key", v)

        val isSyncEnabled: Boolean get() = store.getBoolean("$P.sync_enabled", true)
        fun setSyncEnabled(v: Boolean) = store.putBoolean("$P.sync_enabled", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.manifest_version")
                .remove("$P.last_sync")
                .remove("$P.storage_key")
                .remove("$P.sync_enabled")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf(
            "$P.manifest_version", "$P.storage_key", "$P.sync_enabled"
        )
    }

    // -- UiHints --------------------------------------------------------------------------

    object UiHints {
        private const val P = "ui_hints"

        val hasSeenConversationListSwipe: Boolean get() = store.getBoolean("$P.list_swipe", false)
        fun setHasSeenConversationListSwipe(v: Boolean) = store.putBoolean("$P.list_swipe", v)

        val hasSeenReactionHint: Boolean get() = store.getBoolean("$P.reaction_hint", false)
        fun setHasSeenReactionHint(v: Boolean) = store.putBoolean("$P.reaction_hint", v)

        val hasSeenSwipeToReply: Boolean get() = store.getBoolean("$P.swipe_reply", false)
        fun setHasSeenSwipeToReply(v: Boolean) = store.putBoolean("$P.swipe_reply", v)

        val hasSeenProfileNameHint: Boolean get() = store.getBoolean("$P.profile_name_hint", false)
        fun setHasSeenProfileNameHint(v: Boolean) = store.putBoolean("$P.profile_name_hint", v)

        val hasSeenSafetyNumberHint: Boolean get() = store.getBoolean("$P.safety_hint", false)
        fun setHasSeenSafetyNumberHint(v: Boolean) = store.putBoolean("$P.safety_hint", v)

        fun onFirstEverAppLaunch() {
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
                .remove("$P.list_swipe")
                .remove("$P.reaction_hint")
                .remove("$P.swipe_reply")
                .remove("$P.profile_name_hint")
                .remove("$P.safety_hint")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf(
            "$P.list_swipe", "$P.reaction_hint", "$P.swipe_reply",
            "$P.profile_name_hint", "$P.safety_hint"
        )
    }

    // -- Tooltips -------------------------------------------------------------------------

    object Tooltips {
        private const val P = "tooltips"

        val hasSeenChatSearchTooltip: Boolean get() = store.getBoolean("$P.chat_search", false)
        fun setHasSeenChatSearchTooltip(v: Boolean) = store.putBoolean("$P.chat_search", v)

        val hasSeenNoteToSelfTooltip: Boolean get() = store.getBoolean("$P.note_to_self", false)
        fun setHasSeenNoteToSelfTooltip(v: Boolean) = store.putBoolean("$P.note_to_self", v)

        val hasSeenReactionsTooltip: Boolean get() = store.getBoolean("$P.reactions", false)
        fun setHasSeenReactionsTooltip(v: Boolean) = store.putBoolean("$P.reactions", v)

        val hasSeenStoriesTooltip: Boolean get() = store.getBoolean("$P.stories", false)
        fun setHasSeenStoriesTooltip(v: Boolean) = store.putBoolean("$P.stories", v)

        fun onFirstEverAppLaunch() {
            if (!store.contains("$P.chat_search")) {
                store.putBoolean("$P.chat_search", false)
                store.putBoolean("$P.note_to_self", false)
                store.putBoolean("$P.reactions", false)
                store.putBoolean("$P.stories", false)
            }
        }

        fun clear() {
            store.beginWrite()
                .remove("$P.chat_search")
                .remove("$P.note_to_self")
                .remove("$P.reactions")
                .remove("$P.stories")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf(
            "$P.chat_search", "$P.note_to_self", "$P.reactions", "$P.stories"
        )
    }

    // -- Certificate ----------------------------------------------------------------------

    object Certificate {
        private const val P = "certificate"

        val unidentifiedAccessCertificate : String? get() = store.getString("$P.ua_cert")
        fun setUnidentifiedAccessCertificate(v: String) = store.putString("$P.ua_cert", v)

        val certificateExpiration: Long get() = store.getLong("$P.cert_expiry", 0L)
        fun setCertificateExpiration(v: Long) = store.putLong("$P.cert_expiry", v)

        val serverPublicParams : String? get() = store.getString("$P.server_params")
        fun setServerPublicParams(v: String) = store.putString("$P.server_params", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.ua_cert")
                .remove("$P.cert_expiry")
                .remove("$P.server_params")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf(
            "$P.ua_cert", "$P.cert_expiry", "$P.server_params"
        )
    }

    // -- Wallpaper ------------------------------------------------------------------------

    object Wallpaper {
        private const val P = "wallpaper"

        val globalWallpaper : String? get() = store.getString("$P.global")
        fun setGlobalWallpaper(v: String) = store.putString("$P.global", v)

        val useSystemWallpaper: Boolean get() = store.getBoolean("$P.system", false)
        fun setUseSystemWallpaper(v: Boolean) = store.putBoolean("$P.system", v)

        val brightness: Float get() = store.getFloat("$P.brightness", 1.0f)
        fun setBrightness(v: Float) = store.putFloat("$P.brightness", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.global")
                .remove("$P.system")
                .remove("$P.brightness")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf(
            "$P.global", "$P.system", "$P.brightness"
        )
    }

    // -- Payments -------------------------------------------------------------------------

    object Payments {
        private const val P = "payments"

        val isEnabled: Boolean get() = store.getBoolean("$P.enabled", false)
        fun setEnabled(v: Boolean) = store.putBoolean("$P.enabled", v)

        val hasSeenIntro: Boolean get() = store.getBoolean("$P.intro_seen", false)
        fun setHasSeenIntro(v: Boolean) = store.putBoolean("$P.intro_seen", v)

        val lastBalanceFetchTs: Long get() = store.getLong("$P.last_balance", 0L)
        fun setLastBalanceFetchTs(v: Long) = store.putLong("$P.last_balance", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.enabled")
                .remove("$P.intro_seen")
                .remove("$P.last_balance")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf("$P.enabled")
    }

    // -- InAppPayment ---------------------------------------------------------------------

    object InAppPayment {
        private const val P = "in_app_payment"

        val subscriptionTier : String? get() = store.getString("$P.tier")
        fun setSubscriptionTier(v: String) = store.putString("$P.tier", v)

        val lastPaymentTs: Long get() = store.getLong("$P.last_payment", 0L)
        fun setLastPaymentTs(v: Long) = store.putLong("$P.last_payment", v)

        val hasSeenPaymentIntro: Boolean get() = store.getBoolean("$P.intro_seen", false)
        fun setHasSeenPaymentIntro(v: Boolean) = store.putBoolean("$P.intro_seen", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.tier")
                .remove("$P.last_payment")
                .remove("$P.intro_seen")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf("$P.tier")
    }

    // -- ImageEditor ----------------------------------------------------------------------

    object ImageEditor {
        private const val P = "image_editor"

        val lastUsedTool : String? get() = store.getString("$P.last_tool")
        fun setLastUsedTool(v: String) = store.putString("$P.last_tool", v)

        val brushSize: Float get() = store.getFloat("$P.brush_size", 5.0f)
        fun setBrushSize(v: Float) = store.putFloat("$P.brush_size", v)

        val hasSeenEditorIntro: Boolean get() = store.getBoolean("$P.intro_seen", false)
        fun setHasSeenEditorIntro(v: Boolean) = store.putBoolean("$P.intro_seen", v)

        fun onFirstEverAppLaunch() {
            if (!store.contains("$P.brush_size")) {
                store.putFloat("$P.brush_size", 5.0f)
                store.putBoolean("$P.intro_seen", false)
            }
        }

        fun clear() {
            store.beginWrite()
                .remove("$P.last_tool")
                .remove("$P.brush_size")
                .remove("$P.intro_seen")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf("$P.brush_size")
    }

    // -- NotificationProfile --------------------------------------------------------------

    object NotificationProfile {
        private const val P = "notif_profile"

        val customProfiles : String? get() = store.getString("$P.profiles")
        fun setCustomProfiles(v: String) = store.putString("$P.profiles", v)

        val activeProfileId : String? get() = store.getString("$P.active_id")
        fun setActiveProfileId(v: String) = store.putString("$P.active_id", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.profiles")
                .remove("$P.active_id")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf("$P.profiles", "$P.active_id")
    }

    // -- ReleaseChannel -------------------------------------------------------------------

    object ReleaseChannel {
        private const val P = "release_channel"

        val channel : String? get() = store.getString("$P.channel", "stable")
        fun setChannel(v: String) = store.putString("$P.channel", v)

        val lastUpdateCheckTs: Long get() = store.getLong("$P.last_check", 0L)
        fun setLastUpdateCheckTs(v: Long) = store.putLong("$P.last_check", v)

        fun onFirstEverAppLaunch() {
            if (!store.contains("$P.channel")) {
                store.putString("$P.channel", "stable")
            }
        }

        fun clear() {
            store.beginWrite()
                .remove("$P.channel")
                .remove("$P.last_check")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf("$P.channel")
    }

    // -- ApkUpdate ------------------------------------------------------------------------

    object ApkUpdate {
        private const val P = "apk_update"

        val lastCheckTs: Long get() = store.getLong("$P.last_check", 0L)
        fun setLastCheckTs(v: Long) = store.putLong("$P.last_check", v)

        val lastVersionCode: Int get() = store.getInt("$P.last_version", 0)
        fun setLastVersionCode(v: Int) = store.putInt("$P.last_version", v)

        val hasDismissedUpdate: Boolean get() = store.getBoolean("$P.dismissed", false)
        fun setHasDismissedUpdate(v: Boolean) = store.putBoolean("$P.dismissed", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.last_check")
                .remove("$P.last_version")
                .remove("$P.dismissed")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = emptyList()
    }

    // -- Miscellaneous --------------------------------------------------------------------

    object Miscellaneous {
        private const val P = "misc"

        val lastVersionCode: Int get() = store.getInt("$P.last_version", 0)
        fun setLastVersionCode(v: Int) = store.putInt("$P.last_version", v)

        val hasCompletedFirstRun: Boolean get() = store.getBoolean("$P.first_run", false)
        fun setHasCompletedFirstRun(v: Boolean) = store.putBoolean("$P.first_run", v)

        val appStartTime: Long get() = store.getLong("$P.app_start", 0L)
        fun setAppStartTime(v: Long) = store.putLong("$P.app_start", v)

        val hasSeenDatabaseUpgrade: Boolean get() = store.getBoolean("$P.db_upgrade_seen", false)
        fun setHasSeenDatabaseUpgrade(v: Boolean) = store.putBoolean("$P.db_upgrade_seen", v)

        fun clear() {
            store.beginWrite()
                .remove("$P.last_version")
                .remove("$P.first_run")
                .remove("$P.app_start")
                .remove("$P.db_upgrade_seen")
                .apply()
        }

        fun getKeysToIncludeInBackup(): List<String> = listOf("$P.first_run")
    }

    // -- Global operations ----------------------------------------------------------------

    /**
     * Clears all values across all categories.
     * Used on logout or account deletion.
     */
    fun clearAll() {
        Account.clear()
        Registration.clear()
        Backup.clear()
        Settings.clear()
        Notifications.clear()
        Privacy.clear()
        Pin.clear()
        Onboarding.clear()
        Proxy.clear()
        RateLimit.clear()
        PhoneNumberPrivacy.clear()
        Emoji.clear()
        ChatColors.clear()
        CallQuality.clear()
        Labs.clear()
        Stories.clear()
        Internal.clear()
        Svr.clear()
        RemoteConfig.clear()
        StorageService.clear()
        UiHints.clear()
        Tooltips.clear()
        Certificate.clear()
        Wallpaper.clear()
        Payments.clear()
        InAppPayment.clear()
        ImageEditor.clear()
        NotificationProfile.clear()
        ReleaseChannel.clear()
        ApkUpdate.clear()
        Miscellaneous.clear()
    }

    /**
     * Returns all keys that should be included in a backup restore operation.
     */
    fun getAllBackupKeys(): List<String> {
        return mutableListOf<String>().apply {
            addAll(Account.getKeysToIncludeInBackup())
            addAll(Registration.getKeysToIncludeInBackup())
            addAll(Backup.getKeysToIncludeInBackup())
            addAll(Settings.getKeysToIncludeInBackup())
            addAll(Notifications.getKeysToIncludeInBackup())
            addAll(Privacy.getKeysToIncludeInBackup())
            addAll(Pin.getKeysToIncludeInBackup())
            addAll(Onboarding.getKeysToIncludeInBackup())
            addAll(Proxy.getKeysToIncludeInBackup())
            addAll(RateLimit.getKeysToIncludeInBackup())
            addAll(PhoneNumberPrivacy.getKeysToIncludeInBackup())
            addAll(Emoji.getKeysToIncludeInBackup())
            addAll(ChatColors.getKeysToIncludeInBackup())
            addAll(CallQuality.getKeysToIncludeInBackup())
            addAll(Labs.getKeysToIncludeInBackup())
            addAll(Stories.getKeysToIncludeInBackup())
            addAll(Internal.getKeysToIncludeInBackup())
            addAll(Svr.getKeysToIncludeInBackup())
            addAll(RemoteConfig.getKeysToIncludeInBackup())
            addAll(StorageService.getKeysToIncludeInBackup())
            addAll(UiHints.getKeysToIncludeInBackup())
            addAll(Tooltips.getKeysToIncludeInBackup())
            addAll(Certificate.getKeysToIncludeInBackup())
            addAll(Wallpaper.getKeysToIncludeInBackup())
            addAll(Payments.getKeysToIncludeInBackup())
            addAll(InAppPayment.getKeysToIncludeInBackup())
            addAll(ImageEditor.getKeysToIncludeInBackup())
            addAll(NotificationProfile.getKeysToIncludeInBackup())
            addAll(ReleaseChannel.getKeysToIncludeInBackup())
            addAll(ApkUpdate.getKeysToIncludeInBackup())
            addAll(Miscellaneous.getKeysToIncludeInBackup())
        }.distinct()
    }

    // -- Delegate lazy initialization helper ---------------------------------------------

    private inline fun <T : Any?> delegatesLazy(crossinline getter: () -> T?): Lazy<T?> {
        return lazy { getter() }
    }
}
