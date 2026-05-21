package org.enchant.core.store

class CertificateValues(
    store: KeyValueStorage,
    delegates: StoreValueDelegates
) : EnchantStoreValues(store, delegates) {

    private val uaCertValue = delegates.stringValue("$P.ua_cert")
    private val certExpiryValue = delegates.longValue("$P.cert_expiry", 0L)
    private val serverParamsValue = delegates.stringValue("$P.server_params")

    var unidentifiedAccessCertificate: String? by uaCertValue
    var certificateExpiration: Long by certExpiryValue
    var serverPublicParams: String? by serverParamsValue

    override fun onFirstEverAppLaunch() {}

    fun clear() {
        store.beginWrite()
            .remove("$P.ua_cert").remove("$P.cert_expiry").remove("$P.server_params")
            .apply()
    }

    override fun getKeysToIncludeInBackup(): List<String> = listOf(
        "$P.ua_cert", "$P.cert_expiry", "$P.server_params"
    )

    private companion object { const val P = "certificate" }
}
