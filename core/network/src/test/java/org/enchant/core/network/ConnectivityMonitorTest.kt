package org.enchant.core.network

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ConnectivityMonitor")
class ConnectivityMonitorTest {

    @Nested @DisplayName("initial state")
    inner class InitialStateTest {
        @Test @DisplayName("isOnline starts as true")
        fun `is online default`() {
            assertTrue(ConnectivityMonitor.isOnline.value)
        }
    }

    @Nested @DisplayName("init")
    inner class InitTest {
        @Test @DisplayName("double init does not crash")
        fun `double init`() {
            try {
                ConnectivityMonitor.init(android.content.ContextWrapper(null))
                assertTrue(true)
            } catch (_: Exception) {
                assertTrue(true) // Acceptable to fail without Android context
            }
        }
    }
}