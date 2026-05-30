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
            _turnUrl = prefs.getString("turn_url", null)
            _turnUsername = prefs.getString("turn_username", null)
            _turnPassword = prefs.getString("turn_password", null)
            _jwtPublicKey = prefs.getString("jwt_public_key", null)
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
        overrideUrl?.let { return it.trimEnd('/') }

        prefs.getString("gateway_url", null)?.let { return it.trimEnd('/') }

        val resId = context.resources.getIdentifier("gateway_url", "string", context.packageName)
        if (resId != 0) {
            try {
                return context.getString(resId).trimEnd('/')
            } catch (_: Exception) {
            }
        }

        return "https://localhost:8080"
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
