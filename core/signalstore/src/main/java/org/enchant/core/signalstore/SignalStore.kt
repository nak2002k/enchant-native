package org.enchant.core.signalstore

import org.enchant.core.base.SecurePreferences

object SignalStore {
    private const val PREF_ACI = "signal_store.aci"
    private const val PREF_PNI = "signal_store.pni"
    private const val PREF_REG_ID = "signal_store.registration_id"

    fun getAccountId(): String? = SecurePreferences.getString(PREF_ACI)

    fun setAccountId(id: String) = SecurePreferences.putString(PREF_ACI, id)

    fun getPni(): String? = SecurePreferences.getString(PREF_PNI)

    fun setPni(pni: String) = SecurePreferences.putString(PREF_PNI, pni)

    fun getRegistrationId(): Int = SecurePreferences.getInt(PREF_REG_ID, 0)

    fun setRegistrationId(id: Int) = SecurePreferences.putInt(PREF_REG_ID, id)

    fun clearAll() {
        SecurePreferences.remove(PREF_ACI)
        SecurePreferences.remove(PREF_PNI)
        SecurePreferences.remove(PREF_REG_ID)
    }
}
