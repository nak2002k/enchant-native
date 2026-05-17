package org.enchant.core.base

import android.content.Context

object AppConfig {
    @Volatile
    private var initialized = false
    var applicationContext: Context? = null
        private set
    private var _gatewayUrl: String = ""
    private var _wsUrl: String = ""
    private var _turnUrl: String? = null
    private var _turnUsername: String? = null
    private var _turnPassword: String? = null
    private var _jwtPublicKey: String? = null
    private var _appVersion: String = "1.0.0"
    private var _userAgent: String = ""

    val gatewayUrl: String
        get() = checkInitialized().let { _gatewayUrl }
    val wsUrl: String
        get() = checkInitialized().let { _wsUrl }
    val turnUrl: String?
        get() = checkInitialized().let { _turnUrl }
    val turnUsername: String?
        get() = checkInitialized().let { _turnUsername }
    val turnPassword: String?
        get() = checkInitialized().let { _turnPassword }
    val jwtPublicKey: String?
        get() = checkInitialized().let { _jwtPublicKey }
    val appVersion: String
        get() = checkInitialized().let { _appVersion }
    val userAgent: String
        get() = checkInitialized().let { _userAgent }

    fun init(context: Context, overrideUrl: String? = null) {
        if (initialized) return
        applicationContext = context
        val prefs = context.getSharedPreferences("enchant_config", Context.MODE_PRIVATE)

        val defaultUrl = overrideUrl ?: prefs.getString("gateway_url", null)
        if (defaultUrl != null) {
            _gatewayUrl = defaultUrl.trimEnd('/')
        } else {
            val resId = context.resources.getIdentifier("gateway_url", "string", context.packageName)
            _gatewayUrl = if (resId != 0) {
                try { context.getString(resId).trimEnd('/') } catch (_: Exception) { "http://localhost:8080" }
            } else {
                "http://localhost:8080"
            }
        }
        _wsUrl = deriveWsUrl(_gatewayUrl)
        _turnUrl = prefs.getString("turn_url", null)
        _turnUsername = prefs.getString("turn_username", null)
        _turnPassword = prefs.getString("turn_password", null)
        _jwtPublicKey = prefs.getString("jwt_public_key", null)
        _appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
        _userAgent = "Enchant-Android/$_appVersion"
        initialized = true
    }

    private fun deriveWsUrl(httpUrl: String): String {
        return httpUrl.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://")
    }

    private fun checkInitialized(): Unit {
        if (!initialized) throw IllegalStateException("AppConfig not initialized. Call AppConfig.init() first.")
    }
}
