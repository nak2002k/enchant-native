package org.enchant.core.store

class RegistrationValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val completeValue = delegates.booleanValue("$P.complete", false)
    private val lockPinValue = delegates.stringValue("$P.lock_pin")
    private val restoreStateValue = delegates.stringValue("$P.restore_state")
    private val sessionIdValue = delegates.stringValue("$P.session_id")
    private val localRegIdValue = delegates.intValue("$P.local_reg_id", 0)

    var isComplete: Boolean by completeValue
    var lockPin: String? by lockPinValue
    var restoreDecisionState: String? by restoreStateValue
    var sessionId: String? by sessionIdValue
    var localRegistrationId: Int by localRegIdValue

    val isCompleteFlow = completeValue.toFlow()

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.complete").remove("$P.lock_pin").remove("$P.restore_state")
            .remove("$P.session_id").remove("$P.local_reg_id")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf("$P.complete", "$P.local_reg_id")

    private companion object { const val P = "registration" }
}
