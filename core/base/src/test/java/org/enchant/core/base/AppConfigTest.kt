package org.enchant.core.base

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(AndroidJUnit4::class)
class AppConfigTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("enchant_config", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        resetInitialized()
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
        resetInitialized()
    }

    private fun resetInitialized() {
        val field = AppConfig::class.java.getDeclaredField("initialized")
        field.isAccessible = true
        field.set(AppConfig, false)
        // Clear cached values so tests don't leak state across cases.
        for (name in listOf("_gatewayUrl", "_wsUrl", "_turnUrl", "_turnUsername",
                            "_turnPassword", "_jwtPublicKey", "_appVersion", "_userAgent",
                            "_applicationContext")) {
            val f = AppConfig::class.java.getDeclaredField(name)
            f.isAccessible = true
            when (name) {
                "_gatewayUrl", "_wsUrl" -> f.set(AppConfig, "")
                "_turnUrl", "_turnUsername", "_turnPassword", "_jwtPublicKey", "_applicationContext" -> f.set(AppConfig, null)
                "_appVersion" -> f.set(AppConfig, "1.0.0")
                "_userAgent" -> f.set(AppConfig, "")
            }
        }
    }

    @Test
    fun `init with override URL sets gateway and WS URLs`() {
        AppConfig.init(context, overrideUrl = "https://api.example.com")
        assertEquals("https://api.example.com", AppConfig.gatewayUrl)
        assertEquals("wss://api.example.com", AppConfig.wsUrl)
    }

    @Test
    fun `init with SharedPreferences value uses stored URL`() {
        prefs.edit().putString("gateway_url", "https://stored.example.com").commit()
        AppConfig.init(context)
        assertEquals("https://stored.example.com", AppConfig.gatewayUrl)
        assertEquals("wss://stored.example.com", AppConfig.wsUrl)
    }

    @Test
    fun `init with string resource uses resource URL`() {
        AppConfig.init(context)
        assertEquals("http://localhost:8080", AppConfig.gatewayUrl)
        assertEquals("ws://localhost:8080", AppConfig.wsUrl)
    }

    @Test
    fun `init derives WS URL from HTTP`() {
        AppConfig.init(context, overrideUrl = "http://localhost:8080")
        assertEquals("ws://localhost:8080", AppConfig.wsUrl)
    }

    @Test
    fun `init derives WS URL from HTTPS`() {
        AppConfig.init(context, overrideUrl = "https://api.example.com/v1")
        assertEquals("wss://api.example.com/v1", AppConfig.wsUrl)
    }

    @Test
    fun `init preserves port in WS URL`() {
        AppConfig.init(context, overrideUrl = "http://localhost:9090")
        assertEquals("ws://localhost:9090", AppConfig.wsUrl)
    }

    @Test
    fun `init trims trailing slash from gateway URL`() {
        AppConfig.init(context, overrideUrl = "https://api.example.com/")
        assertEquals("https://api.example.com", AppConfig.gatewayUrl)
    }

    @Test
    fun `init loads TURN credentials from SharedPreferences`() {
        prefs.edit()
            .putString("turn_url", "turn:server.example.com")
            .putString("turn_username", "user")
            .putString("turn_password", "pass")
            .commit()
        AppConfig.init(context)
        assertEquals("turn:server.example.com", AppConfig.turnUrl)
        assertEquals("user", AppConfig.turnUsername)
        assertEquals("pass", AppConfig.turnPassword)
    }

    @Test
    fun `init TURN credentials default to null`() {
        AppConfig.init(context, overrideUrl = "https://api.example.com")
        assertNull(AppConfig.turnUrl)
        assertNull(AppConfig.turnUsername)
        assertNull(AppConfig.turnPassword)
    }

    @Test
    fun `init loads JWT public key from SharedPreferences`() {
        prefs.edit().putString("jwt_public_key", "key-data").commit()
        AppConfig.init(context)
        assertEquals("key-data", AppConfig.jwtPublicKey)
    }

    @Test
    fun `init JWT public key defaults to null`() {
        AppConfig.init(context, overrideUrl = "https://api.example.com")
        assertNull(AppConfig.jwtPublicKey)
    }

    @Test
    fun `init sets user agent with app version`() {
        AppConfig.init(context, overrideUrl = "https://api.example.com")
        assertEquals("Enchant-Android/1.0.0", AppConfig.userAgent)
    }

    @Test
    fun `init is idempotent`() {
        AppConfig.init(context, overrideUrl = "https://first.example.com")
        AppConfig.init(context, overrideUrl = "https://second.example.com")
        assertEquals("https://first.example.com", AppConfig.gatewayUrl)
    }

    @Test
    fun `accessing values before init throws IllegalStateException`() {
        assertThrows(IllegalStateException::class.java) {
            AppConfig.gatewayUrl
        }
    }

    @Test
    fun `app version defaults to one`() {
        AppConfig.init(context, overrideUrl = "https://api.example.com")
        assertEquals("1.0.0", AppConfig.appVersion)
    }

    @Test
    fun `non-loopback http gateway is upgraded to https`() {
        AppConfig.init(context, overrideUrl = "http://api.example.com")
        assertEquals("https://api.example.com", AppConfig.gatewayUrl)
        assertEquals("wss://api.example.com", AppConfig.wsUrl)
    }

    @Test
    fun `non-loopback http with port is upgraded to https`() {
        AppConfig.init(context, overrideUrl = "http://api.example.com:8080")
        assertEquals("https://api.example.com:8080", AppConfig.gatewayUrl)
    }

    @Test
    fun `loopback http gateway is preserved for debug`() {
        AppConfig.init(context, overrideUrl = "http://10.0.2.2:8080")
        assertEquals("http://10.0.2.2:8080", AppConfig.gatewayUrl)
        assertEquals("ws://10.0.2.2:8080", AppConfig.wsUrl)
    }

    @Test
    fun `stored non-loopback http gateway is upgraded to https`() {
        prefs.edit().putString("gateway_url", "http://stored.example.com").commit()
        AppConfig.init(context)
        assertEquals("https://stored.example.com", AppConfig.gatewayUrl)
    }
}
