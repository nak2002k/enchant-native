package org.enchant.core.store

import android.content.Context
import org.enchant.core.base.SecurePreferences

/**
 * Centralized encrypted key-value store for Enchant.
 *
 * All preferences are stored in EncryptedSharedPreferences via [SecurePreferences].
 * Each category is a nested object with typed getters/setters and a proper [clear] method.
 *
 * Usage:
 * ```
 * EnchantStore.init(context)
 * EnchantStore.Account.setUserId("user-123")
 * val id = EnchantStore.Account.userId
 * ```
 */
object EnchantStore {

    private var initialized = false

    /**
     * Initializes the store by ensuring SecurePreferences is initialized.
     * Must be called before any getter/setter is used.
     */
    fun init(context: Context) {
        if (initialized) return
        SecurePreferences.init(context)
        initialized = true
    }

    // -- Account --------------------------------------------------------------------------

    object Account {
        private const val P = "enchant.account"

        val userId: String? get() = SecurePreferences.getString("$P.user_id")
        fun setUserId(v: String) = SecurePreferences.putString("$P.user_id", v)

        val deviceId: String? get() = SecurePreferences.getString("$P.device_id")
        fun setDeviceId(v: String) = SecurePreferences.putString("$P.device_id", v)

        val username: String? get() = SecurePreferences.getString("$P.username")
        fun setUsername(v: String) = SecurePreferences.putString("$P.username", v)

        val displayName: String? get() = SecurePreferences.getString("$P.display_name")
        fun setDisplayName(v: String) = SecurePreferences.putString("$P.display_name", v)

        val about: String? get() = SecurePreferences.getString("$P.about")
        fun setAbout(v: String) = SecurePreferences.putString("$P.about", v)

        val registrationId: Int get() = SecurePreferences.getInt("$P.reg_id", 0)
        fun setRegistrationId(v: Int) = SecurePreferences.putInt("$P.reg_id", v)

        val aci: String? get() = SecurePreferences.getString("$P.aci")
        fun setAci(v: String) = SecurePreferences.putString("$P.aci", v)

        val pni: String? get() = SecurePreferences.getString("$P.pni")
        fun setPni(v: String) = SecurePreferences.putString("$P.pni", v)

        fun clear() {
            SecurePreferences.remove("$P.user_id")
            SecurePreferences.remove("$P.device_id")
            SecurePreferences.remove("$P.username")
            SecurePreferences.remove("$P.display_name")
            SecurePreferences.remove("$P.about")
            SecurePreferences.remove("$P.reg_id")
            SecurePreferences.remove("$P.aci")
            SecurePreferences.remove("$P.pni")
        }
    }

    // -- Registration ---------------------------------------------------------------------

    object Registration {
        private const val P = "enchant.reg"

        val isComplete: Boolean get() = SecurePreferences.getBoolean("$P.complete", false)
        fun setComplete(v: Boolean) = SecurePreferences.putBoolean("$P.complete", v)

        val lockPin: String? get() = SecurePreferences.getString("$P.lock_pin")
        fun setLockPin(v: String) = SecurePreferences.putString("$P.lock_pin", v)

        fun clear() {
            SecurePreferences.remove("$P.complete")
            SecurePreferences.remove("$P.lock_pin")
        }
    }

    // -- Backup ---------------------------------------------------------------------------

    object Backup {
        private const val P = "enchant.backup"

        val isEnabled: Boolean get() = SecurePreferences.getBoolean("$P.enabled", false)
        fun setEnabled(v: Boolean) = SecurePreferences.putBoolean("$P.enabled", v)

        val lastBackupTs: Long get() = SecurePreferences.getLong("$P.last_ts", 0L)
        fun setLastBackupTs(v: Long) = SecurePreferences.putLong("$P.last_ts", v)

        val backupKey: String? get() = SecurePreferences.getString("$P.key")
        fun setBackupKey(v: String) = SecurePreferences.putString("$P.key", v)

        fun clear() {
            SecurePreferences.remove("$P.enabled")
            SecurePreferences.remove("$P.last_ts")
            SecurePreferences.remove("$P.key")
        }
    }

    // -- Settings -------------------------------------------------------------------------

    object Settings {
        private const val P = "enchant.settings"

        val readReceipts: Boolean get() = SecurePreferences.getBoolean("$P.read_receipts", true)
        fun setReadReceipts(v: Boolean) = SecurePreferences.putBoolean("$P.read_receipts", v)

        val typingIndicators: Boolean get() = SecurePreferences.getBoolean("$P.typing", true)
        fun setTypingIndicators(v: Boolean) = SecurePreferences.putBoolean("$P.typing", v)

        val linkPreviews: Boolean get() = SecurePreferences.getBoolean("$P.link_previews", true)
        fun setLinkPreviews(v: Boolean) = SecurePreferences.putBoolean("$P.link_previews", v)

        val theme: String? get() = SecurePreferences.getString("$P.theme", "system")
        fun setTheme(v: String) = SecurePreferences.putString("$P.theme", v)

        val fontSize: Float get() = SecurePreferences.getFloat("$P.font_size", 1.0f)
        fun setFontSize(v: Float) = SecurePreferences.putFloat("$P.font_size", v)

        val language: String? get() = SecurePreferences.getString("$P.language")
        fun setLanguage(v: String) = SecurePreferences.putString("$P.language", v)

        fun clear() {
            SecurePreferences.remove("$P.read_receipts")
            SecurePreferences.remove("$P.typing")
            SecurePreferences.remove("$P.link_previews")
            SecurePreferences.remove("$P.theme")
            SecurePreferences.remove("$P.font_size")
            SecurePreferences.remove("$P.language")
        }
    }

    // -- Notifications --------------------------------------------------------------------

    object Notifications {
        private const val P = "enchant.notif"

        val messageNotifications: Boolean get() = SecurePreferences.getBoolean("$P.message", true)
        fun setMessageNotifications(v: Boolean) = SecurePreferences.putBoolean("$P.message", v)

        val showPreview: Boolean get() = SecurePreferences.getBoolean("$P.preview", true)
        fun setShowPreview(v: Boolean) = SecurePreferences.putBoolean("$P.preview", v)

        val sound: String? get() = SecurePreferences.getString("$P.sound")
        fun setSound(v: String) = SecurePreferences.putString("$P.sound", v)

        val vibrate: Boolean get() = SecurePreferences.getBoolean("$P.vibrate", true)
        fun setVibrate(v: Boolean) = SecurePreferences.putBoolean("$P.vibrate", v)

        fun clear() {
            SecurePreferences.remove("$P.message")
            SecurePreferences.remove("$P.preview")
            SecurePreferences.remove("$P.sound")
            SecurePreferences.remove("$P.vibrate")
        }
    }

    // -- Privacy --------------------------------------------------------------------------

    object Privacy {
        private const val P = "enchant.privacy"

        val lastSeenVisibility: String? get() = SecurePreferences.getString("$P.last_seen", "contacts")
        fun setLastSeenVisibility(v: String) = SecurePreferences.putString("$P.last_seen", v)

        val onlineVisibility: String? get() = SecurePreferences.getString("$P.online", "contacts")
        fun setOnlineVisibility(v: String) = SecurePreferences.putString("$P.online", v)

        val avatarVisibility: String? get() = SecurePreferences.getString("$P.avatar", "contacts")
        fun setAvatarVisibility(v: String) = SecurePreferences.putString("$P.avatar", v)

        val aboutVisibility: String? get() = SecurePreferences.getString("$P.about_vis", "contacts")
        fun setAboutVisibility(v: String) = SecurePreferences.putString("$P.about_vis", v)

        val groupsAddPolicy: String? get() = SecurePreferences.getString("$P.groups_add", "everyone")
        fun setGroupsAddPolicy(v: String) = SecurePreferences.putString("$P.groups_add", v)

        fun clear() {
            SecurePreferences.remove("$P.last_seen")
            SecurePreferences.remove("$P.online")
            SecurePreferences.remove("$P.avatar")
            SecurePreferences.remove("$P.about_vis")
            SecurePreferences.remove("$P.groups_add")
        }
    }

    // -- Pin ------------------------------------------------------------------------------

    object Pin {
        private const val P = "enchant.pin"

        val hash: String? get() = SecurePreferences.getString("$P.hash")
        fun setHash(v: String) = SecurePreferences.putString("$P.hash", v)

        val salt: String? get() = SecurePreferences.getString("$P.salt")
        fun setSalt(v: String) = SecurePreferences.putString("$P.salt", v)

        val failedAttempts: Int get() = SecurePreferences.getInt("$P.fails", 0)
        fun setFailedAttempts(v: Int) = SecurePreferences.putInt("$P.fails", v)

        fun clear() {
            SecurePreferences.remove("$P.hash")
            SecurePreferences.remove("$P.salt")
            SecurePreferences.remove("$P.fails")
        }
    }

    // -- Onboarding -----------------------------------------------------------------------

    object Onboarding {
        private const val P = "enchant.onboard"

        val isComplete: Boolean get() = SecurePreferences.getBoolean("$P.complete", false)
        fun setComplete(v: Boolean) = SecurePreferences.putBoolean("$P.complete", v)

        val hasSeenWelcome: Boolean get() = SecurePreferences.getBoolean("$P.welcome", false)
        fun setHasSeenWelcome(v: Boolean) = SecurePreferences.putBoolean("$P.welcome", v)

        fun clear() {
            SecurePreferences.remove("$P.complete")
            SecurePreferences.remove("$P.welcome")
        }
    }

    // -- Proxy ----------------------------------------------------------------------------

    object Proxy {
        private const val P = "enchant.proxy"

        val host: String? get() = SecurePreferences.getString("$P.host")
        fun setHost(v: String) = SecurePreferences.putString("$P.host", v)

        val port: Int get() = SecurePreferences.getInt("$P.port", 0)
        fun setPort(v: Int) = SecurePreferences.putInt("$P.port", v)

        fun clear() {
            SecurePreferences.remove("$P.host")
            SecurePreferences.remove("$P.port")
        }
    }

    // -- RateLimit ------------------------------------------------------------------------

    object RateLimit {
        private const val P = "enchant.ratelimit"

        val lastOtpMs: Long get() = SecurePreferences.getLong("$P.otp", 0L)
        fun setLastOtpMs(v: Long) = SecurePreferences.putLong("$P.otp", v)

        val otpAttempts: Int get() = SecurePreferences.getInt("$P.otp_count", 0)
        fun setOtpAttempts(v: Int) = SecurePreferences.putInt("$P.otp_count", v)

        fun clear() {
            SecurePreferences.remove("$P.otp")
            SecurePreferences.remove("$P.otp_count")
        }
    }

    // -- PhoneNumberPrivacy ---------------------------------------------------------------

    object PhoneNumberPrivacy {
        private const val P = "enchant.phone_privacy"

        val shareWithContacts: Boolean get() = SecurePreferences.getBoolean("$P.share", true)
        fun setShareWithContacts(v: Boolean) = SecurePreferences.putBoolean("$P.share", v)

        fun clear() {
            SecurePreferences.remove("$P.share")
        }
    }

    // -- Emoji ----------------------------------------------------------------------------

    object Emoji {
        private const val P = "enchant.emoji"

        val recent: String? get() = SecurePreferences.getString("$P.recent")
        fun setRecent(v: String) = SecurePreferences.putString("$P.recent", v)

        fun clear() {
            SecurePreferences.remove("$P.recent")
        }
    }

    // -- ChatColors -----------------------------------------------------------------------

    object ChatColors {
        private const val P = "enchant.chat_colors"

        val wallpaper: String? get() = SecurePreferences.getString("$P.wallpaper")
        fun setWallpaper(v: String) = SecurePreferences.putString("$P.wallpaper", v)

        val color: String? get() = SecurePreferences.getString("$P.color")
        fun setColor(v: String) = SecurePreferences.putString("$P.color", v)

        fun clear() {
            SecurePreferences.remove("$P.wallpaper")
            SecurePreferences.remove("$P.color")
        }
    }

    // -- CallQuality ----------------------------------------------------------------------

    object CallQuality {
        private const val P = "enchant.call_quality"

        val useLowBandwidth: Boolean get() = SecurePreferences.getBoolean("$P.low_bw", false)
        fun setUseLowBandwidth(v: Boolean) = SecurePreferences.putBoolean("$P.low_bw", v)

        fun clear() {
            SecurePreferences.remove("$P.low_bw")
        }
    }

    // -- Labs -----------------------------------------------------------------------------

    object Labs {
        private const val P = "enchant.labs"

        val experimentalFeatures: Boolean get() = SecurePreferences.getBoolean("$P.experimental", false)
        fun setExperimentalFeatures(v: Boolean) = SecurePreferences.putBoolean("$P.experimental", v)

        fun clear() {
            SecurePreferences.remove("$P.experimental")
        }
    }

    // -- Stories --------------------------------------------------------------------------

    object Stories {
        private const val P = "enchant.stories"

        val myStoriesPrivacy: String? get() = SecurePreferences.getString("$P.privacy", "contacts")
        fun setMyStoriesPrivacy(v: String) = SecurePreferences.putString("$P.privacy", v)

        fun clear() {
            SecurePreferences.remove("$P.privacy")
        }
    }

    // -- Internal -------------------------------------------------------------------------

    object Internal {
        private const val P = "enchant.internal"

        val lastDeviceSyncTs: Long get() = SecurePreferences.getLong("$P.sync_ts", 0L)
        fun setLastDeviceSyncTs(v: Long) = SecurePreferences.putLong("$P.sync_ts", v)

        val lastPreKeyRotationTs: Long get() = SecurePreferences.getLong("$P.prekey_ts", 0L)
        fun setLastPreKeyRotationTs(v: Long) = SecurePreferences.putLong("$P.prekey_ts", v)

        fun clear() {
            SecurePreferences.remove("$P.sync_ts")
            SecurePreferences.remove("$P.prekey_ts")
        }
    }

    // -- Global clear ---------------------------------------------------------------------

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
    }
}
