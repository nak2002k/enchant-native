package org.enchant.core.store

class PinValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val hashValue = delegates.stringValue("$P.hash")
    private val saltValue = delegates.stringValue("$P.salt")
    private val failsValue = delegates.intValue("$P.fails", 0)
    private val lengthValue = delegates.intValue("$P.length", 0)
    private val regLockValue = delegates.booleanValue("$P.reg_lock", false)

    var hash: String? by hashValue
    var salt: String? by saltValue
    var failedAttempts: Int by failsValue
    var pinLength: Int by lengthValue
    var isRegistrationLockEnabled: Boolean by regLockValue

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.hash").remove("$P.salt").remove("$P.fails")
            .remove("$P.length").remove("$P.reg_lock")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf(
        "$P.hash", "$P.salt", "$P.length", "$P.reg_lock"
    )

    private companion object { const val P = "pin" }
}
