package org.enchant.core.jobmanager

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DisappearingMessagesWorkerTest {
    @Test
    fun `factoryKey is correct`() {
        val worker = DisappearingMessagesWorker()
        assertEquals("DisappearingMessagesWorker", worker.factoryKey)
    }

    @Test
    fun `run returns success`() = runTest {
        val worker = DisappearingMessagesWorker()
        val result = worker.run()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `onFailure does not crash`() {
        val worker = DisappearingMessagesWorker()
        worker.onFailure()
        assertTrue(true)
    }

    @Test
    fun `serialize returns null`() {
        val worker = DisappearingMessagesWorker()
        assertNull(worker.serialize())
    }

    @Test
    fun `factory creates worker with id`() {
        val worker = DisappearingMessagesWorker.Factory.create("custom-id", null)
        assertEquals("custom-id", worker.id)
        assertEquals("DisappearingMessagesWorker", worker.factoryKey)
    }

    @Test
    fun `factory creates worker with serialized data`() {
        val worker = DisappearingMessagesWorker.Factory.create("custom-id", byteArrayOf(1, 2, 3))
        assertEquals("custom-id", worker.id)
    }

    @Test
    fun `custom parameters are preserved`() {
        val params = JobParameters.Builder("custom-id")
            .setMaxAttempts(5)
            .setLifespan(60000)
            .build()
        val worker = DisappearingMessagesWorker("custom-id", params)
        assertEquals(5, worker.parameters.maxAttempts)
        assertEquals(60000, worker.parameters.lifespan)
    }
}
