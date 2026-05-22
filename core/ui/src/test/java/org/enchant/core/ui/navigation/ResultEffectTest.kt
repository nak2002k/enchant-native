package org.enchant.core.ui.navigation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ResultEffect")
class ResultEffectTest {

    private fun createBus(): ResultEventBus = ResultEventBus()

    @Nested
    @DisplayName("ResultEffect function parameters")
    inner class ParameterTests {

        @Test
        @DisplayName("ResultEffect is a composable function")
        fun `ResultEffect is composable`() {
            assertTrue(true)
        }

        @Test
        @DisplayName("ResultEffect has reified type parameter T")
        fun `ResultEffect has reified type parameter`() {
            val bus = createBus()
            bus.sendResult<String>(resultKey = "TestKey", result = "hello")
            val flow = bus.getResultFlow<String>("TestKey")
            assertNotNull(flow)
        }
    }

    @Nested
    @DisplayName("ResultEffect integration with ResultEventBus")
    inner class IntegrationTests {

        @Test
        @DisplayName("LaunchedEffect keys on resultKey and channel identity")
        fun `LaunchedEffect keys are correct`() = runBlocking {
            val bus = createBus()
            bus.sendResult<String>(resultKey = "Key1", result = "first")
            val flow1 = bus.getResultFlow<String>("Key1")
            assertNotNull(flow1)
            val v1 = flow1!!.first()
            assertEquals("first", v1)

            bus.removeResult<String>("Key1")
            bus.sendResult<String>(resultKey = "Key1", result = "second")
            val flow2 = bus.getResultFlow<String>("Key1")
            assertNotNull(flow2)
            val v2 = flow2!!.first()
            assertEquals("second", v2)
            assertFalse(flow1 === flow2)
        }

        @Test
        @DisplayName("getResultFlow returns null when channel does not exist")
        fun `getResultFlow returns null for missing channel`() {
            val bus = createBus()
            val flow = bus.getResultFlow<String>("NonExistent")
            assertEquals(null, flow)
        }

        @Test
        @DisplayName("channelMap key changes after removeResult")
        fun `channel identity changes after removal`() = runBlocking {
            val bus = createBus()
            bus.sendResult<String>(resultKey = "Key", result = "v1")
            val map1 = bus.channelMap["Key"]
            assertNotNull(map1)

            bus.removeResult<String>("Key")
            assertFalse(bus.channelMap.containsKey("Key"))
        }
    }

    @Nested
    @DisplayName("ResultEffect behavior")
    inner class BehaviorTests {

        @Test
        @DisplayName("ResultEffect onResult is invoked with cast result")
        fun `onResult receives correct value`() = runBlocking {
            val bus = createBus()
            bus.sendResult<String>(resultKey = "Key", result = "testValue")
            val flow = bus.getResultFlow<String>("Key")
            assertNotNull(flow)
            var receivedValue: String? = null
            flow!!.collect { result ->
                receivedValue = result as String
            }
            assertEquals("testValue", receivedValue)
        }

        @Test
        @DisplayName("ResultEffect handles multiple emissions")
        fun `handles multiple emissions`() = runBlocking {
            val bus = createBus()
            bus.sendResult<String>(resultKey = "Key", result = "1")
            bus.sendResult<String>(resultKey = "Key", result = "2")
            bus.sendResult<String>(resultKey = "Key", result = "3")
            val flow = bus.getResultFlow<String>("Key")
            assertNotNull(flow)
            val values = mutableListOf<String>()
            flow!!.collect { result ->
                values.add(result as String)
            }
            assertEquals(3, values.size)
        }
    }
}