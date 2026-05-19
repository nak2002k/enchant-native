package org.enchant.core.base

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

class CoroutineDispatchersTest {

    @Test
    fun `default io dispatcher is Dispatchers IO`() {
        assertSame(Dispatchers.IO, CoroutineDispatchers.io)
    }

    @Test
    fun `default network dispatcher is derived from IO`() {
        assertNotSame(Dispatchers.IO, CoroutineDispatchers.network)
    }

    @Test
    fun `default crypto dispatcher is derived from Default`() {
        assertNotSame(Dispatchers.Default, CoroutineDispatchers.crypto)
    }

    @Test
    fun `default computation dispatcher is Dispatchers Default`() {
        assertSame(Dispatchers.Default, CoroutineDispatchers.computation)
    }

    @Test
    fun `default main dispatcher is Dispatchers Main`() {
        assertSame(Dispatchers.Main, CoroutineDispatchers.main)
    }

    @Test
    fun `setProvider injects custom dispatchers`() {
        val testDispatcher = UnconfinedTestDispatcher()
        val customProvider = object : CoroutineDispatchers.DispatcherProvider {
            override val io = testDispatcher
            override val network = testDispatcher
            override val crypto = testDispatcher
            override val computation = testDispatcher
            override val main = testDispatcher
        }

        CoroutineDispatchers.setProvider(customProvider)
        try {
            assertSame(testDispatcher, CoroutineDispatchers.io)
            assertSame(testDispatcher, CoroutineDispatchers.network)
            assertSame(testDispatcher, CoroutineDispatchers.crypto)
            assertSame(testDispatcher, CoroutineDispatchers.computation)
            assertSame(testDispatcher, CoroutineDispatchers.main)
        } finally {
            CoroutineDispatchers.setProvider()
        }
    }

    @Test
    fun `reset to default restores original dispatchers`() {
        val testDispatcher = UnconfinedTestDispatcher()
        val customProvider = object : CoroutineDispatchers.DispatcherProvider {
            override val io = testDispatcher
            override val network = testDispatcher
            override val crypto = testDispatcher
            override val computation = testDispatcher
            override val main = testDispatcher
        }

        CoroutineDispatchers.setProvider(customProvider)
        CoroutineDispatchers.setProvider()
        assertSame(Dispatchers.IO, CoroutineDispatchers.io)
    }
}
