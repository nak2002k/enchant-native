package org.enchant.core.store

/**
 * Account-related settings.
 *
 * Stores: user identity, device info, registration state, FCM token,
 * username, display name, ACI/PNI, capabilities, multi-device flags.
 */
class AccountValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val userIdValue by lazy { delegates.stringValue("$P.user_id") }
    private val deviceIdValue by lazy { delegates.stringValue("$P.device_id") }
    private val usernameValue by lazy { delegates.stringValue("$P.username") }
    private val displayNameValue by lazy { delegates.stringValue("$P.display_name") }
    private val aboutValue by lazy { delegates.stringValue("$P.about") }
    private val registrationIdValue by lazy { delegates.intValue("$P.reg_id", 0) }
    private val aciValue by lazy { delegates.stringValue("$P.aci") }
    private val pniValue by lazy { delegates.stringValue("$P.pni") }
    private val fcmTokenValue by lazy { delegates.stringValue("$P.fcm_token") }
    private val registeredValue by lazy { delegates.booleanValue("$P.registered", false) }
    private val usernameLinkValue by lazy { delegates.stringValue("$P.username_link") }
    private val entitlementsValue by lazy { delegates.stringValue("$P.entitlements") }
    private val numberValue by lazy { delegates.stringValue("$P.number") }
    private val uaKeyValue by lazy { delegates.stringValue("$P.ua_key") }
    private val unrestrictedUaValue by lazy { delegates.booleanValue("$P.unrestricted_ua", false) }
    private val hasSeenOnboardingValue by lazy { delegates.booleanValue("$P.has_seen_onboarding", false) }
    private val multiDeviceValue by lazy { delegates.booleanValue("$P.multi_device", false) }
    private val capabilitiesValue by lazy { delegates.stringValue("$P.capabilities") }

    var userId: String? by userIdValue
    var deviceId: String? by deviceIdValue
    var username: String? by usernameValue
    var displayName: String? by displayNameValue
    var about: String? by aboutValue
    var registrationId: Int by registrationIdValue
    var aci: String? by aciValue
    var pni: String? by pniValue
    var fcmToken: String? by fcmTokenValue
    var isRegistered: Boolean by registeredValue
    var usernameLinkHandle: String? by usernameLinkValue
    var entitlements: String? by entitlementsValue
    var number: String? by numberValue
    var unidentifiedAccessKey: String? by uaKeyValue
    var unrestrictedUnidentifiedAccess: Boolean by unrestrictedUaValue
    var hasSeenOnboarding: Boolean by hasSeenOnboardingValue
    var multiDevice: Boolean by multiDeviceValue
    var capabilities: String? by capabilitiesValue

    val userIdFlow = userIdValue.toFlow()
    val isRegisteredFlow = registeredValue.toFlow()
    val multiDeviceFlow = multiDeviceValue.toFlow()

    override fun onFirstEverAppLaunch() {
        if (!store.contains("$P.registered")) {
            store.putBoolean("$P.registered", false)
        }
    }

    fun clear() {
        store.beginWrite()
            .remove("$P.user_id").remove("$P.device_id").remove("$P.username")
            .remove("$P.display_name").remove("$P.about").remove("$P.reg_id")
            .remove("$P.aci").remove("$P.pni").remove("$P.fcm_token")
            .remove("$P.registered").remove("$P.username_link").remove("$P.entitlements")
            .remove("$P.number").remove("$P.ua_key").remove("$P.unrestricted_ua")
            .remove("$P.has_seen_onboarding").remove("$P.multi_device").remove("$P.capabilities")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf(
        "$P.user_id", "$P.device_id", "$P.username", "$P.display_name", "$P.about",
        "$P.reg_id", "$P.aci", "$P.pni", "$P.registered", "$P.number",
        "$P.ua_key", "$P.unrestricted_ua", "$P.multi_device", "$P.capabilities"
    )

    private companion object { const val P = "account" }
}
