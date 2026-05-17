package org.enchant.core.signalstore

import org.enchant.core.base.SecurePreferences

object SignalStore {
    private fun String.get() = SecurePreferences.getString(this, null)
    private fun String.getInt(default: Int = 0) = SecurePreferences.getInt(this, default)
    private fun String.getLong(default: Long = 0L) = SecurePreferences.getLong(this, default)
    private fun String.getBool(default: Boolean = false) = SecurePreferences.getBoolean(this, default)
    private fun String.set(value: String) = SecurePreferences.putString(this, value)
    private fun String.set(value: Int) = SecurePreferences.putInt(this, value)
    private fun String.set(value: Long) = SecurePreferences.putLong(this, value)
    private fun String.set(value: Boolean) = SecurePreferences.putBoolean(this, value)
    private fun String.clear() = SecurePreferences.remove(this)

    object Account {
        private val P = "signal.account"
        val userId: String? get() = "$P.user_id".get()
        fun setUserId(v: String) = "$P.user_id".set(v)
        val deviceId: String? get() = "$P.device_id".get()
        fun setDeviceId(v: String) = "$P.device_id".set(v)
        val username: String? get() = "$P.username".get()
        fun setUsername(v: String) = "$P.username".set(v)
        val displayName: String? get() = "$P.display_name".get()
        fun setDisplayName(v: String) = "$P.display_name".set(v)
        val about: String? get() = "$P.about".get()
        fun setAbout(v: String) = "$P.about".set(v)
        val registrationId: Int get() = "$P.reg_id".getInt()
        fun setRegistrationId(v: Int) = "$P.reg_id".set(v)
        val aci: String? get() = "$P.aci".get()
        fun setAci(v: String) = "$P.aci".set(v)
        val pni: String? get() = "$P.pni".get()
        fun setPni(v: String) = "$P.pni".set(v)
        fun clear() { (1..10).forEach { SecurePreferences.remove("$P.$it") } }
    }

    object Registration {
        private val P = "signal.reg"
        val isComplete: Boolean get() = "$P.complete".getBool()
        fun setComplete(v: Boolean) = "$P.complete".set(v)
        val lockPin: String? get() = "$P.lock_pin".get()
        fun setLockPin(v: String) = "$P.lock_pin".set(v)
        fun clear() { SecurePreferences.remove("$P.complete"); SecurePreferences.remove("$P.lock_pin") }
    }

    object Backup {
        private val P = "signal.backup"
        val isEnabled: Boolean get() = "$P.enabled".getBool()
        fun setEnabled(v: Boolean) = "$P.enabled".set(v)
        val lastBackupTs: Long get() = "$P.last_ts".getLong()
        fun setLastBackupTs(v: Long) = "$P.last_ts".set(v)
        val backupKey: String? get() = "$P.key".get()
        fun setBackupKey(v: String) = "$P.key".set(v)
        fun clear() { SecurePreferences.remove("$P.enabled"); SecurePreferences.remove("$P.last_ts"); SecurePreferences.remove("$P.key") }
    }

    object Settings {
        private val P = "signal.settings"
        val readReceipts: Boolean get() = "$P.read_receipts".getBool(true)
        fun setReadReceipts(v: Boolean) = "$P.read_receipts".set(v)
        val typingIndicators: Boolean get() = "$P.typing".getBool(true)
        fun setTypingIndicators(v: Boolean) = "$P.typing".set(v)
        val linkPreviews: Boolean get() = "$P.link_previews".getBool(true)
        fun setLinkPreviews(v: Boolean) = "$P.link_previews".set(v)
        val theme: String? get() = "$P.theme".get()
        fun setTheme(v: String) = "$P.theme".set(v)
        val fontSize: Float get() = "$P.font_size".getInt(100) / 100f
        fun setFontSize(v: Float) = "$P.font_size".set((v * 100).toInt())
        val language: String? get() = "$P.language".get()
        fun setLanguage(v: String) = "$P.language".set(v)
        fun clear() { SecurePreferences.remove("$P.read_receipts"); SecurePreferences.remove("$P.typing"); SecurePreferences.remove("$P.link_previews") }
    }

    object Notifications {
        private val P = "signal.notif"
        val messageNotifications: Boolean get() = "$P.message".getBool(true)
        fun setMessageNotifications(v: Boolean) = "$P.message".set(v)
        val showPreview: Boolean get() = "$P.preview".getBool(true)
        fun setShowPreview(v: Boolean) = "$P.preview".set(v)
        val sound: String? get() = "$P.sound".get()
        fun setSound(v: String) = "$P.sound".set(v)
        val vibrate: Boolean get() = "$P.vibrate".getBool(true)
        fun setVibrate(v: Boolean) = "$P.vibrate".set(v)
        fun clear() { SecurePreferences.remove("$P.message"); SecurePreferences.remove("$P.preview") }
    }

    object Privacy {
        private val P = "signal.privacy"
        val lastSeenVisibility: String? get() = "$P.last_seen".get()
        fun setLastSeenVisibility(v: String) = "$P.last_seen".set(v)
        val onlineVisibility: String? get() = "$P.online".get()
        fun setOnlineVisibility(v: String) = "$P.online".set(v)
        val avatarVisibility: String? get() = "$P.avatar".get()
        fun setAvatarVisibility(v: String) = "$P.avatar".set(v)
        val aboutVisibility: String? get() = "$P.about".get()
        fun setAboutVisibility(v: String) = "$P.about".set(v)
        val groupsAddPolicy: String? get() = "$P.groups_add".get()
        fun setGroupsAddPolicy(v: String) = "$P.groups_add".set(v)
        fun clear() { (1..5).forEach { SecurePreferences.remove("$P.$it") } }
    }

    object Pin {
        private val P = "signal.pin"
        val hash: String? get() = "$P.hash".get()
        fun setHash(v: String) = "$P.hash".set(v)
        val salt: String? get() = "$P.salt".get()
        fun setSalt(v: String) = "$P.salt".set(v)
        val failedAttempts: Int get() = "$P.fails".getInt()
        fun setFailedAttempts(v: Int) = "$P.fails".set(v)
        fun clear() { SecurePreferences.remove("$P.hash"); SecurePreferences.remove("$P.salt"); SecurePreferences.remove("$P.fails") }
    }

    object Onboarding {
        private val P = "signal.onboard"
        val isComplete: Boolean get() = "$P.complete".getBool()
        fun setComplete(v: Boolean) = "$P.complete".set(v)
        val hasSeenWelcome: Boolean get() = "$P.welcome".getBool()
        fun setHasSeenWelcome(v: Boolean) = "$P.welcome".set(v)
        fun clear() { SecurePreferences.remove("$P.complete"); SecurePreferences.remove("$P.welcome") }
    }

    object Proxy {
        private val P = "signal.proxy"
        val host: String? get() = "$P.host".get()
        fun setHost(v: String) = "$P.host".set(v)
        val port: Int get() = "$P.port".getInt()
        fun setPort(v: Int) = "$P.port".set(v)
        fun clear() { SecurePreferences.remove("$P.host"); SecurePreferences.remove("$P.port") }
    }

    object RateLimit {
        private val P = "signal.ratelimit"
        val lastOtpMs: Long get() = "$P.otp".getLong()
        fun setLastOtpMs(v: Long) = "$P.otp".set(v)
        val otpAttempts: Int get() = "$P.otp_count".getInt()
        fun setOtpAttempts(v: Int) = "$P.otp_count".set(v)
        fun clear() { SecurePreferences.remove("$P.otp"); SecurePreferences.remove("$P.otp_count") }
    }

    object PhoneNumberPrivacy {
        private val P = "signal.phone_privacy"
        val shareWithContacts: Boolean get() = "$P.share".getBool(true)
        fun setShareWithContacts(v: Boolean) = "$P.share".set(v)
        fun clear() { SecurePreferences.remove("$P.share") }
    }

    object Emoji {
        private val P = "signal.emoji"
        val recent: String? get() = "$P.recent".get()
        fun setRecent(v: String) = "$P.recent".set(v)
        fun clear() { SecurePreferences.remove("$P.recent") }
    }

    object ChatColors {
        private val P = "signal.chat_colors"
        val wallpaper: String? get() = "$P.wallpaper".get()
        fun setWallpaper(v: String) = "$P.wallpaper".set(v)
        val color: String? get() = "$P.color".get()
        fun setColor(v: String) = "$P.color".set(v)
        fun clear() { SecurePreferences.remove("$P.wallpaper"); SecurePreferences.remove("$P.color") }
    }

    object CallQuality {
        private val P = "signal.call_quality"
        val useLowBandwidth: Boolean get() = "$P.low_bw".getBool()
        fun setUseLowBandwidth(v: Boolean) = "$P.low_bw".set(v)
        fun clear() { SecurePreferences.remove("$P.low_bw") }
    }

    object Labs {
        private val P = "signal.labs"
        val experimentalFeatures: Boolean get() = "$P.experimental".getBool()
        fun setExperimentalFeatures(v: Boolean) = "$P.experimental".set(v)
        fun clear() { SecurePreferences.remove("$P.experimental") }
    }

    object Stories {
        private val P = "signal.stories"
        val myStoriesPrivacy: String? get() = "$P.privacy".get()
        fun setMyStoriesPrivacy(v: String) = "$P.privacy".set(v)
        fun clear() { SecurePreferences.remove("$P.privacy") }
    }

    object Internal {
        private val P = "signal.internal"
        val lastDeviceSyncTs: Long get() = "$P.sync_ts".getLong()
        fun setLastDeviceSyncTs(v: Long) = "$P.sync_ts".set(v)
        val lastPreKeyRotationTs: Long get() = "$P.prekey_ts".getLong()
        fun setLastPreKeyRotationTs(v: Long) = "$P.prekey_ts".set(v)
        fun clear() { SecurePreferences.remove("$P.sync_ts"); SecurePreferences.remove("$P.prekey_ts") }
    }

    fun clearAll() {
        Account.clear(); Registration.clear(); Backup.clear(); Settings.clear()
        Notifications.clear(); Privacy.clear(); Pin.clear(); Onboarding.clear()
        Proxy.clear(); RateLimit.clear(); PhoneNumberPrivacy.clear(); Emoji.clear()
        ChatColors.clear(); CallQuality.clear(); Labs.clear(); Stories.clear(); Internal.clear()
    }
}
