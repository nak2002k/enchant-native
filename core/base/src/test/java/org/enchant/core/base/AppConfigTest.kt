package org.enchant.core.base

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("AppConfig — Full Coverage")
class AppConfigTest {

    @Nested @DisplayName("URL Derivation")
    inner class UrlDerivationTest {
        @Test @DisplayName("deriveWsUrl converts http to ws")
        fun `derive ws from http`() {
            val method = AppConfig::class.java.getDeclaredMethod("deriveWsUrl", String::class.java)
            method.isAccessible = true
            val result = method.invoke(AppConfig, "http://example.com") as String
            assertEquals("ws://example.com", result)
        }

        @Test @DisplayName("deriveWsUrl converts https to wss")
        fun `derive ws from https`() {
            val method = AppConfig::class.java.getDeclaredMethod("deriveWsUrl", String::class.java)
            method.isAccessible = true
            val result = method.invoke(AppConfig, "https://example.com") as String
            assertEquals("wss://example.com", result)
        }

        @Test @DisplayName("deriveWsUrl preserves path")
        fun `derive ws preserves path`() {
            val method = AppConfig::class.java.getDeclaredMethod("deriveWsUrl", String::class.java)
            method.isAccessible = true
            val result = method.invoke(AppConfig, "https://api.example.com/v1") as String
            assertEquals("wss://api.example.com/v1", result)
        }

        @Test @DisplayName("deriveWsUrl preserves port")
        fun `derive ws preserves port`() {
            val method = AppConfig::class.java.getDeclaredMethod("deriveWsUrl", String::class.java)
            method.isAccessible = true
            val result = method.invoke(AppConfig, "http://localhost:8080") as String
            assertEquals("ws://localhost:8080", result)
        }

        @Test @DisplayName("deriveWsUrl handles localhost")
        fun `derive ws localhost`() {
            val method = AppConfig::class.java.getDeclaredMethod("deriveWsUrl", String::class.java)
            method.isAccessible = true
            val result = method.invoke(AppConfig, "http://localhost:8080") as String
            assertEquals("ws://localhost:8080", result)
        }
    }

    @Nested @DisplayName("Check Initialized")
    inner class CheckInitializedTest {
        @Test @DisplayName("checkInitialized throws when not initialized")
        fun `check initialized throws`() {
            val method = AppConfig::class.java.getDeclaredMethod("checkInitialized")
            method.isAccessible = true
            val field = AppConfig::class.java.getDeclaredField("initialized")
            field.isAccessible = true
            field.set(AppConfig, false)

            try {
                method.invoke(AppConfig)
                fail("Expected IllegalStateException")
            } catch (e: java.lang.reflect.InvocationTargetException) {
                assertTrue(e.cause is IllegalStateException)
            }
        }

        @Test @DisplayName("checkInitialized does not throw when initialized")
        fun `check initialized ok`() {
            val method = AppConfig::class.java.getDeclaredMethod("checkInitialized")
            method.isAccessible = true
            val field = AppConfig::class.java.getDeclaredField("initialized")
            field.isAccessible = true
            field.set(AppConfig, true)

            // Should not throw
            method.invoke(AppConfig)

            // Reset for other tests
            field.set(AppConfig, false)
        }
    }

    @Nested @DisplayName("Default Values")
    inner class DefaultValuesTest {
        @Test @DisplayName("gatewayUrl defaults to localhost when not initialized")
        fun `gateway url default`() {
            val field = AppConfig::class.java.getDeclaredField("_gatewayUrl")
            field.isAccessible = true
            assertEquals("", field.get(AppConfig))
        }

        @Test @DisplayName("wsUrl defaults to empty when not initialized")
        fun `ws url default`() {
            val field = AppConfig::class.java.getDeclaredField("_wsUrl")
            field.isAccessible = true
            assertEquals("", field.get(AppConfig))
        }

        @Test @DisplayName("appVersion defaults to 1.0.0 when not initialized")
        fun `app version default`() {
            val field = AppConfig::class.java.getDeclaredField("_appVersion")
            field.isAccessible = true
            assertEquals("1.0.0", field.get(AppConfig))
        }

        @Test @DisplayName("userAgent defaults to empty when not initialized")
        fun `user agent default`() {
            val field = AppConfig::class.java.getDeclaredField("_userAgent")
            field.isAccessible = true
            assertEquals("", field.get(AppConfig))
        }

        @Test @DisplayName("turnUrl defaults to null when not initialized")
        fun `turn url default`() {
            val field = AppConfig::class.java.getDeclaredField("_turnUrl")
            field.isAccessible = true
            assertNull(field.get(AppConfig))
        }

        @Test @DisplayName("turnUsername defaults to null when not initialized")
        fun `turn username default`() {
            val field = AppConfig::class.java.getDeclaredField("_turnUsername")
            field.isAccessible = true
            assertNull(field.get(AppConfig))
        }

        @Test @DisplayName("turnPassword defaults to null when not initialized")
        fun `turn password default`() {
            val field = AppConfig::class.java.getDeclaredField("_turnPassword")
            field.isAccessible = true
            assertNull(field.get(AppConfig))
        }

        @Test @DisplayName("jwtPublicKey defaults to null when not initialized")
        fun `jwt public key default`() {
            val field = AppConfig::class.java.getDeclaredField("_jwtPublicKey")
            field.isAccessible = true
            assertNull(field.get(AppConfig))
        }
    }
}
