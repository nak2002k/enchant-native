package org.enchant.core.ui.navigation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ResultEffect")
class ResultEffectTest {

    private fun createBus(): ResultEventBus = ResultEventBus()

    @Nested
    @DisplayName("ResultEventBus integration")
    inner class IntegrationTests {

        @Test
        @DisplayName("getResultFlow returns flow when channel exists")
        fun `getResultFlow returns flow when channel exists`() = runBlocking {
            val bus = createBus()
            bus.sendResult<String>(resultKey = "Key1", result = "first")
            val flow = bus.getResultFlow<String>("Key1")
            assertNotNull(flow)
            assertEquals("first", flow!!.first())
        }

        @Test
        @DisplayName("getResultFlow returns null when channel does not exist")
        fun `getResultFlow returns null when no channel exists`() {
            val bus = createBus()
            val flow = bus.getResultFlow<String>("NonExistent")
            assertNull(flow)
        }

        @Test
        @DisplayName("removeResult removes channel so getResultFlow returns null")
        fun `removeResult removes channel`() = runBlocking {
            val bus = createBus()
            bus.sendResult<String>(resultKey = "Key", result = "v1")
            assertNotNull(bus.channelMap["Key"])

            bus.removeResult<String>("Key")
            assertFalse(bus.channelMap.containsKey("Key"))
            assertNull(bus.getResultFlow<String>("Key"))
        }

        @Test
        @DisplayName("sendResult auto-creates channel on first call")
        fun `sendResult auto-creates channel`() = runBlocking {
            val bus = createBus()
            assertTrue(bus.channelMap.isEmpty())

            bus.sendResult<String>(resultKey = "Test", result = "value")
            assertTrue(bus.channelMap.containsKey("Test"))

            val flow = bus.getResultFlow<String>("Test")
            assertNotNull(flow)
            assertEquals("value", flow!!.first())
        }

        @Test
        @DisplayName("getResultFlow default key uses class name")
        fun `getResultFlow uses class name as default key`() = runBlocking {
            val bus = createBus()
            bus.sendResult<MyData>(result = MyData("hello"))
            val flow = bus.getResultFlow<MyData>()
            assertNotNull(flow)
            assertEquals("hello", flow!!.first().value)
        }
    }

    @Nested
    @DisplayName("Channel behavior")
    inner class ChannelBehaviorTests {

        @Test
        @DisplayName("trySend does not suspend")
        fun `trySend does not suspend`() = runBlocking {
            val bus = createBus()
            bus.sendResult<String>(resultKey = "Key", result = "test")
            val flow = bus.getResultFlow<String>("Key")
            assertNotNull(flow)
        }

        @Test
        @DisplayName("channelMap stores multiple channels independently")
        fun `multiple channels independent`() = runBlocking {
            val bus = createBus()
            bus.sendResult<String>(resultKey = "Key1", result = "one")
            bus.sendResult<String>(resultKey = "Key2", result = "two")
            bus.sendResult<String>(resultKey = "Key3", result = "three")

            assertEquals("one", bus.getResultFlow<String>("Key1")!!.first())
            assertEquals("two", bus.getResultFlow<String>("Key2")!!.first())
            assertEquals("three", bus.getResultFlow<String>("Key3")!!.first())
        }

        @Test
        @DisplayName("result type is preserved through channel")
        fun `result type is preserved`() = runBlocking {
            val bus = createBus()
            bus.sendResult<Int>(resultKey = "NumKey", result = 42)
            val flow = bus.getResultFlow<Int>("NumKey")
            assertNotNull(flow)
            assertEquals(42, flow!!.first())
        }
    }

    data class MyData(val value: String)
}