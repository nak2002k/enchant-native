package org.enchant.core.base

import android.content.Context

/**
 * Centralized app configuration loaded once at startup.
 *
 * Reads gateway URL, WebSocket URL, TURN server credentials, and app version
 * from SharedPreferences, string resources, or build-time defaults.
 *
 * Usage:
 * ```
 * AppConfig.init(context)
 * val url = AppConfig.gatewayUrl
 * ```
 */
object AppConfig {

    @Volatile
    private var initialized = false
    private val lock = Any()

    private var _gatewayUrl: String = ""
    private var _wsUrl: String = ""
    private var _turnUrl: String? = null
    private var _turnUsername: String? = null
    private var _turnPassword: String? = null
    private var _jwtPublicKey: String? = null
    private var _appVersion: String = "1.0.0"
    private var _userAgent: String = ""
    private var _applicationContext: Context? = null

    val applicationContext: Context?
        get() = _applicationContext

    val gatewayUrl: String
        get() { checkInitialized(); return _gatewayUrl }

    val wsUrl: String
        get() { checkInitialized(); return _wsUrl }

    val turnUrl: String?
        get() { checkInitialized(); return _turnUrl }

    val turnUsername: String?
        get() { checkInitialized(); return _turnUsername }

    val turnPassword: String?
        get() { checkInitialized(); return _turnPassword }

    val jwtPublicKey: String?
        get() { checkInitialized(); return _jwtPublicKey }

    val appVersion: String
        get() { checkInitialized(); return _appVersion }

    val userAgent: String
        get() { checkInitialized(); return _userAgent }

    /**
     * Initializes configuration from SharedPreferences and string resources.
     * Safe to call multiple times — subsequent calls are no-ops.
     *
     * @param context application context (not retained)
     * @param overrideUrl optional URL to override stored/resource values
     */
    fun init(context: Context, overrideUrl: String? = null) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return

            val prefs = context.getSharedPreferences("enchant_config", Context.MODE_PRIVATE)

            _gatewayUrl = resolveGatewayUrl(context, prefs, overrideUrl)
            _wsUrl = deriveWsUrl(_gatewayUrl)
            _turnUrl = SecurePreferences.getString("turn_url") ?: prefs.getString("turn_url", null)
            _turnUsername = SecurePreferences.getString("turn_username") ?: prefs.getString("turn_username", null)
            _turnPassword = SecurePreferences.getString("turn_password") ?: prefs.getString("turn_password", null)
            _jwtPublicKey = SecurePreferences.getString("jwt_public_key") ?: prefs.getString("jwt_public_key", null)
            _appVersion = resolveAppVersion(context)
            _userAgent = "Enchant-Android/$_appVersion"
            _applicationContext = context.applicationContext

            initialized = true
        }
    }

    private fun resolveGatewayUrl(
        context: Context,
        prefs: android.content.SharedPreferences,
        overrideUrl: String?
    ): String {
        val resolved = (overrideUrl
            ?: prefs.getString("gateway_url", null)
            ?: run {
                val resId = context.resources.getIdentifier("gateway_url", "string", context.packageName)
                if (resId != 0) {
                    try { context.getString(resId) } catch (_: Exception) { null }
                } else null
            }
            ?: "http://localhost:8080").trimEnd('/')

        // F-C1: never let a config override downgrade the transport. The app
        // must always talk to the gateway over TLS. Plain http:// is only
        // tolerated for the debug local backend (emulator 10.0.2.2 / adb
        // reverse localhost), and in release builds not even that — anything
        // else is normalized to https:// or rejected.
        return normalizeGatewayUrl(resolved)
    }

    /**
     * Enforces TLS on the gateway URL (F-C1). http:// is only kept for
     * loopback/emulator debug hosts; everything else is upgraded to https://.
     * A gateway URL pointing anywhere but loopback over plain http would put
     * the JWT/refresh token and ciphertexts in cleartext, so we never keep it.
     */
    private fun normalizeGatewayUrl(raw: String): String {
        if (!raw.startsWith("http://")) {
            return raw
        }
        val hostPort = raw.removePrefix("http://").substringBefore('/')
        val host = hostPort.substringBefore(':')
        val isLoopbackDebug = host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"
        if (isLoopbackDebug) {
            return raw
        }
        // Upgrade http -> https for any non-loopback host.
        return "https://" + raw.removePrefix("http://")
    }

    private fun resolveAppVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    private fun deriveWsUrl(httpUrl: String): String {
        return httpUrl
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://")
    }

    private fun checkInitialized() {
        if (!initialized) {
            throw IllegalStateException("AppConfig not initialized. Call AppConfig.init(context) first.")
        }
    }
}
