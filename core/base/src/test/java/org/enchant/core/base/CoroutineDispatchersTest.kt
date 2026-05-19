package org.enchant.core.base

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class CoroutineDispatchersTest {

    @Test
    fun `io dispatcher is Dispatchers IO`() {
        assertSame(Dispatchers.IO, CoroutineDispatchers.io)
    }

    @Test
    fun `network dispatcher is derived from IO`() {
        assertNotSame(Dispatchers.IO, CoroutineDispatchers.network)
    }

    @Test
    fun `crypto dispatcher is derived from Default`() {
        assertNotSame(Dispatchers.Default, CoroutineDispatchers.crypto)
    }

    @Test
    fun `computation dispatcher is Dispatchers Default`() {
        assertSame(Dispatchers.Default, CoroutineDispatchers.computation)
    }

    @Test
    fun `main dispatcher is Dispatchers Main`() {
        assertSame(Dispatchers.Main, CoroutineDispatchers.main)
    }

    @Test
    fun `network and crypto are distinct dispatchers`() {
        assertNotSame(CoroutineDispatchers.network, CoroutineDispatchers.crypto)
    }
}
