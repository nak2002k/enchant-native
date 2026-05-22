package org.enchant.core.ui.navigation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ResultEventBus")
class ResultEventBusTest {

    private fun createBus(): ResultEventBus = ResultEventBus()

    @Nested
    @DisplayName("sendResult and getResultFlow")
    inner class SendAndReceiveTests {

        @Test
        @DisplayName("sendResult followed by getResultFlow collects the value")
        fun `sendResult followed by getResultFlow collects value`() = runBlocking {
            val bus = createBus()
            bus.sendResult<String>(resultKey = "TestResult", result = "hello")
            val flow = bus.getResultFlow<String>(resultKey = "TestResult")
            assertNotNull(flow)
            assertEquals("hello", flow!!.first())
        }

        @Test
        @DisplayName("multiple results queued before collection are all delivered")
        fun `multiple results are all delivered`() = runBlocking {
            val bus = createBus()
            bus.sendResult<String>(resultKey = "TestResult", result = "first")
            bus.sendResult<String>(resultKey = "TestResult", result = "second")
            bus.sendResult<String>(resultKey = "TestResult", result = "third")
            val flow = bus.getResultFlow<String>(resultKey = "TestResult")
            assertNotNull(flow)
            val allValues = mutableListOf<String>()
            flow!!.take(3).collect { allValues.add(it as String) }
            assertEquals(3, allValues.size)
            assertEquals("first", allValues[0])
            assertEquals("second", allValues[1])
            assertEquals("third", allValues[2])
        }

        @Test
        @DisplayName("sendResult with different keys does not cross-contaminate")
        fun `different keys do not cross-contaminate`() = runBlocking {
            val bus = createBus()
            bus.sendResult<String>(resultKey = "KeyA", result = "valueA")
            bus.sendResult<String>(resultKey = "KeyB", result = "valueB")
            val flowA = bus.getResultFlow<String>(resultKey = "KeyA")
            val flowB = bus.getResultFlow<String>(resultKey = "KeyB")
            assertNotNull(flowA)
            assertNotNull(flowB)
            assertEquals("valueA", flowA!!.first())
            assertEquals("valueB", flowB!!.first())
        }

        @Test
        @DisplayName("getResultFlow with no prior sendResult returns null")
        fun `getResultFlow returns null when no channel exists`() {
            val bus = createBus()
            val flow = bus.getResultFlow<String>(resultKey = "NonExistent")
            assertNull(flow)
        }

        @Test
        @DisplayName("null results are transmitted correctly")
        fun `null results are transmitted`() = runBlocking {
            val bus = createBus()
            bus.sendResult<String?>(resultKey = "NullableResult", result = null)
            val flow = bus.getResultFlow<String?>(resultKey = "NullableResult")
            assertNotNull(flow)
            assertEquals(null, flow!!.first())
        }

        @Test
        @DisplayName("sendResult uses class name as default key")
        fun `class name is used as default key`() = runBlocking {
            val bus = createBus()
            bus.sendResult<MyResult>(result = MyResult("data"))
            val flow = bus.getResultFlow<MyResult>()
            assertNotNull(flow)
            assertEquals("data", flow!!.first().data)
        }
    }

    @Nested
    @DisplayName("removeResult")
    inner class RemoveResultTests {

        @Test
        @DisplayName("removeResult removes the channel so getResultFlow returns null")
        fun `removeResult makes getResultFlow return null`() {
            val bus = createBus()
            bus.sendResult<String>(resultKey = "ToRemove", result = "value")
            bus.removeResult<String>(resultKey = "ToRemove")
            val flow = bus.getResultFlow<String>(resultKey = "ToRemove")
            assertNull(flow)
        }

        @Test
        @DisplayName("removeResult on non-existent key does not throw")
        fun `removeResult on non-existent key does not throw`() {
            val bus = createBus()
            bus.removeResult<String>(resultKey = "NonExistent")
            val flow = bus.getResultFlow<String>(resultKey = "NonExistent")
            assertNull(flow)
        }

        @Test
        @DisplayName("removeResult then sendResult creates fresh channel")
        fun `removeResult then sendResult creates fresh channel`() = runBlocking {
            val bus = createBus()
            bus.sendResult<String>(resultKey = "Key", result = "old")
            bus.removeResult<String>(resultKey = "Key")
            bus.sendResult<String>(resultKey = "Key", result = "new")
            val flow = bus.getResultFlow<String>(resultKey = "Key")
            assertNotNull(flow)
            assertEquals("new", flow!!.first())
        }
    }

    @Nested
    @DisplayName("LocalResultEventBus")
    inner class LocalResultEventBusTests {

        @Test
        @DisplayName("provides infix function returns a ProvidedValue")
        fun `provides returns ProvidedValue`() {
            val bus = createBus()
            val provided = LocalResultEventBus provides bus
            assertNotNull(provided)
        }

        @Test
        @DisplayName("channelMap is accessible and starts empty")
        fun `channelMap is accessible`() {
            val bus = createBus()
            assertNotNull(bus.channelMap)
            assertTrue(bus.channelMap.isEmpty())
        }
    }

    @Nested
    @DisplayName("ResultEventBus instance")
    inner class InstanceTests {

        @Test
        @DisplayName("ResultEventBus can be instantiated")
        fun `can be instantiated`() {
            val bus = createBus()
            assertNotNull(bus)
        }

        @Test
        @DisplayName("channelMap is a MutableMap")
        fun `channelMap is MutableMap`() {
            val bus = createBus()
            @Suppress("UNCHECKED_CAST")
            bus.channelMap["test"] = kotlinx.coroutines.channels.Channel<Any?>(
                kotlinx.coroutines.channels.Channel.Factory.BUFFERED
            )
            assertEquals(1, bus.channelMap.size)
            assertTrue(bus.channelMap.containsKey("test"))
        }
    }

    data class MyResult(val data: String)
}