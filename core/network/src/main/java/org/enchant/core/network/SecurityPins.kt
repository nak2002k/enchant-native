package org.enchant.core.network

import okhttp3.CertificatePinner
import org.enchant.core.base.AppConfig

/**
 * Certificate-pinning configuration (F-C2).
 *
 * The release gateway host must be pinned to SHA-256 SPKI pins so a rogue or
 * user-installed CA cannot MITM the connection and steal JWTs / refresh
 * tokens. Pins are loaded from [pinsFor] and applied to the gateway host.
 *
 * In alpha/dev there is no stable host (the endpoint rotates), so the pin set
 * may be empty — in that case the pinner is a no-op and TLS relies on system
 * trust. The moment pins are configured, they are enforced: any certificate
 * that does not match is rejected. Callers MUST wire [installPins] with the
 * release host's real pins before shipping.
 */
object SecurityPins {
    private val lock = Any()
    private var configured: CertificatePinner = CertificatePinner.Builder().build()

    /**
     * Replaces the active pinner. Safe to call more than once (idempotent).
     *
     * @param host the hostname to pin (e.g. "api.enchant.chat")
     * @param pins SHA-256 SPKI pins, each prefixed with "sha256/".
     */
    fun installPins(host: String, pins: List<String>) {
        synchronized(lock) {
            val builder = CertificatePinner.Builder()
            pins.forEach { pin -> builder.add(host, pin) }
            configured = builder.build()
        }
    }

    /** Returns the currently active pinner (no-op if no pins installed). */
    fun active(): CertificatePinner {
        synchronized(lock) { return configured }
    }

    /**
     * Derives the list of pins for a gateway URL. Empty until a release host
     * with real pins is configured via [installPins]. The gateway host from
     * [AppConfig] is used as the pin target.
     */
    fun pinsForGateway(): Pair<String, List<String>> {
        val host = try {
            AppConfig.gatewayUrl.removePrefix("https://").substringBefore('/').substringBefore(':')
        } catch (_: IllegalStateException) {
            ""
        }
        return host to emptyList()
    }
}
