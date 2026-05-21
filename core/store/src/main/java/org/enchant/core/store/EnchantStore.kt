package org.enchant.core.store

import android.content.Context
import org.enchant.core.base.SecurePreferences
import kotlinx.coroutines.flow.Flow

/**
 * Centralized encrypted key-value store for Enchant.
 *
 * Architecture:
 * - SQLite-backed via [KeyValueStore] (encrypted with SQLCipher)
 * - Domain-specific namespace classes (AccountValues, SettingsValues, etc.)
 * - Kotlin property delegates for clean access (`by delegates.booleanValue(...)`)
 * - Reactive [Flow] support for UI observation
 * - Migration framework for schema evolution
 * - Backup awareness via [getAllBackupKeys]
 * - First-launch defaults via [onFirstEverAppLaunch]
 * - Plaintext escape hatch via [plainText] for pre-encryption values
 * - Crash-safe flush via [EnchantCrashHandler]
 * - Versioned application migrations via [ApplicationMigrations]
 *
 * Usage:
 * ```
 * EnchantStore.init(context)
 * EnchantStore.account.userId = "user-123"
 * val id = EnchantStore.account.userId
 *
 * // Reactive:
 * EnchantStore.settings.readReceiptsFlow.collect { enabled -> ... }
 *
 * // Preference library integration:
 * preferenceManager.preferenceDataStore = EnchantStore.getPreferenceDataStore()
 * ```
 */
object EnchantStore {

    private var initialized = false
    internal lateinit var store: KeyValueStorage
        private set
    internal lateinit var delegates: StoreValueDelegates
        private set

    // -- Namespace instances ----------------------------------------------------------------

    lateinit var account: AccountValues
        private set
    lateinit var registration: RegistrationValues
        private set
    lateinit var backup: BackupValues
        private set
    lateinit var settings: SettingsValues
        private set
    lateinit var notifications: NotificationsValues
        private set
    lateinit var privacy: PrivacyValues
        private set
    lateinit var pin: PinValues
        private set
    lateinit var onboarding: OnboardingValues
        private set
    lateinit var proxy: ProxyValues
        private set
    lateinit var rateLimit: RateLimitValues
        private set
    lateinit var phoneNumberPrivacy: PhoneNumberPrivacyValues
        private set
    lateinit var emoji: EmojiValues
        private set
    lateinit var chatColors: ChatColorsValues
        private set
    lateinit var callQuality: CallQualityValues
        private set
    lateinit var labs: LabsValues
        private set
    lateinit var stories: StoriesValues
        private set
    lateinit var internal: InternalValues
        private set
    lateinit var svr: SvrValues
        private set
    lateinit var remoteConfig: RemoteConfigValues
        private set
    lateinit var storageService: StorageServiceValues
        private set
    lateinit var uiHints: UiHintValues
        private set
    lateinit var tooltips: TooltipValues
        private set
    lateinit var certificate: CertificateValues
        private set
    lateinit var wallpaper: WallpaperValues
        private set
    lateinit var payments: PaymentsValues
        private set
    lateinit var inAppPayments: InAppPaymentValues
        private set
    lateinit var imageEditor: ImageEditorValues
        private set
    lateinit var notificationProfile: NotificationProfileValues
        private set
    lateinit var releaseChannel: ReleaseChannelValues
        private set
    lateinit var apkUpdate: ApkUpdateValues
        private set
    lateinit var miscellaneous: MiscellaneousValues
        private set

    lateinit var plainText: PlainTextSharedPrefsDataStore
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
        plainText = PlainTextSharedPrefsDataStore(context)
        migrateFromLegacyPreferences(context)
        ApplicationMigrations.execute(store)
    }

    /**
     * Initializes the store with a custom [KeyValueStorage] implementation.
     * Used for testing with in-memory storage.
     */
    internal fun init(storage: KeyValueStorage, plainTextStore: PlainTextSharedPrefsDataStore? = null) {
        if (initialized) return
        store = storage
        delegates = StoreValueDelegates(storage)
        if (plainTextStore != null) {
            plainText = plainTextStore
        }
        initNamespaces()
        onFirstEverAppLaunch()
        initialized = true
    }

    private fun initNamespaces() {
        account = AccountValues(store, delegates)
        registration = RegistrationValues(store, delegates)
        backup = BackupValues(store, delegates)
        settings = SettingsValues(store, delegates)
        notifications = NotificationsValues(store, delegates)
        privacy = PrivacyValues(store, delegates)
        pin = PinValues(store, delegates)
        onboarding = OnboardingValues(store, delegates)
        proxy = ProxyValues(store, delegates)
        rateLimit = RateLimitValues(store, delegates)
        phoneNumberPrivacy = PhoneNumberPrivacyValues(store, delegates)
        emoji = EmojiValues(store, delegates)
        chatColors = ChatColorsValues(store, delegates)
        callQuality = CallQualityValues(store, delegates)
        labs = LabsValues(store, delegates)
        stories = StoriesValues(store, delegates)
        internal = InternalValues(store, delegates)
        svr = SvrValues(store, delegates)
        remoteConfig = RemoteConfigValues(store, delegates)
        storageService = StorageServiceValues(store, delegates)
        uiHints = UiHintValues(store, delegates)
        tooltips = TooltipValues(store, delegates)
        certificate = CertificateValues(store, delegates)
        wallpaper = WallpaperValues(store, delegates)
        payments = PaymentsValues(store, delegates)
        inAppPayments = InAppPaymentValues(store, delegates)
        imageEditor = ImageEditorValues(store, delegates)
        notificationProfile = NotificationProfileValues(store, delegates)
        releaseChannel = ReleaseChannelValues(store, delegates)
        apkUpdate = ApkUpdateValues(store, delegates)
        miscellaneous = MiscellaneousValues(store, delegates)
    }

    fun flushPendingWrites() {
        store.flushPendingWrites()
    }

    fun blockUntilAllWritesFinished() {
        store.blockUntilAllWritesFinished()
    }

    fun resetCache() {
        store.resetCache()
    }

    /**
     * Called after a backup restore to invalidate the cache and re-read from disk.
     */
    fun onPostBackupRestore() {
        store.resetCache()
    }

    fun getPreferenceDataStore(): EnchantPreferenceDataStore {
        return EnchantPreferenceDataStore(store)
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

    private fun onFirstEverAppLaunch() {
        account.onFirstEverAppLaunch()
        registration.onFirstEverAppLaunch()
        backup.onFirstEverAppLaunch()
        settings.onFirstEverAppLaunch()
        notifications.onFirstEverAppLaunch()
        privacy.onFirstEverAppLaunch()
        pin.onFirstEverAppLaunch()
        onboarding.onFirstEverAppLaunch()
        proxy.onFirstEverAppLaunch()
        rateLimit.onFirstEverAppLaunch()
        phoneNumberPrivacy.onFirstEverAppLaunch()
        emoji.onFirstEverAppLaunch()
        chatColors.onFirstEverAppLaunch()
        callQuality.onFirstEverAppLaunch()
        labs.onFirstEverAppLaunch()
        stories.onFirstEverAppLaunch()
        internal.onFirstEverAppLaunch()
        svr.onFirstEverAppLaunch()
        remoteConfig.onFirstEverAppLaunch()
        storageService.onFirstEverAppLaunch()
        uiHints.onFirstEverAppLaunch()
        tooltips.onFirstEverAppLaunch()
        certificate.onFirstEverAppLaunch()
        wallpaper.onFirstEverAppLaunch()
        payments.onFirstEverAppLaunch()
        inAppPayments.onFirstEverAppLaunch()
        imageEditor.onFirstEverAppLaunch()
        notificationProfile.onFirstEverAppLaunch()
        releaseChannel.onFirstEverAppLaunch()
        apkUpdate.onFirstEverAppLaunch()
        miscellaneous.onFirstEverAppLaunch()
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
        val value = SecurePreferences.getBooleanOrNull(legacyKey, null)
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

    // -- Global operations ----------------------------------------------------------------

    fun clearAll() {
        account.clear()
        registration.clear()
        backup.clear()
        settings.clear()
        notifications.clear()
        privacy.clear()
        pin.clear()
        onboarding.clear()
        proxy.clear()
        rateLimit.clear()
        phoneNumberPrivacy.clear()
        emoji.clear()
        chatColors.clear()
        callQuality.clear()
        labs.clear()
        stories.clear()
        internal.clear()
        svr.clear()
        remoteConfig.clear()
        storageService.clear()
        uiHints.clear()
        tooltips.clear()
        certificate.clear()
        wallpaper.clear()
        payments.clear()
        inAppPayments.clear()
        imageEditor.clear()
        notificationProfile.clear()
        releaseChannel.clear()
        apkUpdate.clear()
        miscellaneous.clear()
    }

    fun getAllBackupKeys(): List<String> {
        return mutableListOf<String>().apply {
            addAll(account.getKeysToIncludeInBackup())
            addAll(registration.getKeysToIncludeInBackup())
            addAll(backup.getKeysToIncludeInBackup())
            addAll(settings.getKeysToIncludeInBackup())
            addAll(notifications.getKeysToIncludeInBackup())
            addAll(privacy.getKeysToIncludeInBackup())
            addAll(pin.getKeysToIncludeInBackup())
            addAll(onboarding.getKeysToIncludeInBackup())
            addAll(proxy.getKeysToIncludeInBackup())
            addAll(rateLimit.getKeysToIncludeInBackup())
            addAll(phoneNumberPrivacy.getKeysToIncludeInBackup())
            addAll(emoji.getKeysToIncludeInBackup())
            addAll(chatColors.getKeysToIncludeInBackup())
            addAll(callQuality.getKeysToIncludeInBackup())
            addAll(labs.getKeysToIncludeInBackup())
            addAll(stories.getKeysToIncludeInBackup())
            addAll(internal.getKeysToIncludeInBackup())
            addAll(svr.getKeysToIncludeInBackup())
            addAll(remoteConfig.getKeysToIncludeInBackup())
            addAll(storageService.getKeysToIncludeInBackup())
            addAll(uiHints.getKeysToIncludeInBackup())
            addAll(tooltips.getKeysToIncludeInBackup())
            addAll(certificate.getKeysToIncludeInBackup())
            addAll(wallpaper.getKeysToIncludeInBackup())
            addAll(payments.getKeysToIncludeInBackup())
            addAll(inAppPayments.getKeysToIncludeInBackup())
            addAll(imageEditor.getKeysToIncludeInBackup())
            addAll(notificationProfile.getKeysToIncludeInBackup())
            addAll(releaseChannel.getKeysToIncludeInBackup())
            addAll(apkUpdate.getKeysToIncludeInBackup())
            addAll(miscellaneous.getKeysToIncludeInBackup())
        }.distinct()
    }
}
