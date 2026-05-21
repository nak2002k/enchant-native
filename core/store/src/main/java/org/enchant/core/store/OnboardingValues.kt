package org.enchant.core.store

class OnboardingValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val completeValue = delegates.booleanValue("$P.complete", false)
    private val welcomeValue = delegates.booleanValue("$P.welcome", false)
    private val permissionsValue = delegates.booleanValue("$P.permissions", false)
    private val profileSetupValue = delegates.booleanValue("$P.profile_setup", false)

    var isComplete: Boolean by completeValue
    var hasSeenWelcome: Boolean by welcomeValue
    var hasSeenPermissions: Boolean by permissionsValue
    var hasSeenProfileSetup: Boolean by profileSetupValue

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.complete").remove("$P.welcome").remove("$P.permissions")
            .remove("$P.profile_setup")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf("$P.complete")

    private companion object { const val P = "onboarding" }
}
