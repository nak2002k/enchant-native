package org.enchant.core.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("RemoteConfig — Full Coverage")
class RemoteConfigTest {

    @Nested @DisplayName("getString")
    inner class GetStringTests {
        @Test @DisplayName("returns default for unknown key")
        fun `getString unknown key returns default`() {
            val result = RemoteConfig.getString("unknown_key", "default")
            assertEquals("default", result)
        }

        @Test @DisplayName("returns known value for known key")
        fun `getString known key returns value`() {
            val result = RemoteConfig.getString("message_retention_days")
            assertEquals("30", result)
        }
    }

    @Nested @DisplayName("getInt")
    inner class GetIntTests {
        @Test @DisplayName("returns default for unknown key")
        fun `getInt unknown key returns default`() {
            val result = RemoteConfig.getInt("unknown_key", 999)
            assertEquals(999, result)
        }

        @Test @DisplayName("returns parsed int for known key")
        fun `getInt known key returns parsed`() {
            val result = RemoteConfig.getInt("message_retention_days", 0)
            assertEquals(30, result)
        }
    }

    @Nested @DisplayName("getLong")
    inner class GetLongTests {
        @Test @DisplayName("returns default for unknown key")
        fun `getLong unknown key returns default`() {
            val result = RemoteConfig.getLong("unknown_key", 12345L)
            assertEquals(12345L, result)
        }

        @Test @DisplayName("returns parsed long for known key")
        fun `getLong known key returns parsed`() {
            val result = RemoteConfig.getLong("disappear_timer_max", 0L)
            assertEquals(7776000L, result)
        }
    }
}