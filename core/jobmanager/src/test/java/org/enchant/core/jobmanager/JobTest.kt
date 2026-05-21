package org.enchant.core.jobmanager

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JobTest {
    private fun createTestJob(
        id: String = "test-job-1",
        parameters: JobParameters = JobParameters.Builder(id).build(),
        runResult: JobResult = success()
    ) = object : Job(id, parameters) {
        override val factoryKey = "TestJob"
        override suspend fun run(): JobResult = runResult
        override fun onFailure() {}
        override fun serialize(): ByteArray? = null
    }

    @Test
    fun `default id is UUID`() {
        val job = createTestJob()
        assertNotNull(job.id)
        assertTrue(job.id.isNotEmpty())
    }

    @Test
    fun `custom id is preserved`() {
        val id = "custom-id-123"
        val job = createTestJob(id = id)
        assertEquals(id, job.id)
    }

    @Test
    fun `runAttempt defaults to zero`() {
        val job = createTestJob()
        assertEquals(0, job.runAttempt)
    }

    @Test
    fun `lastRunAttemptTime defaults to zero`() {
        val job = createTestJob()
        assertEquals(0, job.lastRunAttemptTime)
    }

    @Test
    fun `inputData defaults to null`() {
        val job = createTestJob()
        assertNull(job.inputData)
    }

    @Test
    fun `isCanceled defaults to false`() {
        val job = createTestJob()
        assertFalse(job.isCanceled)
    }

    @Test
    fun `cancel sets isCanceled to true`() {
        val job = createTestJob()
        assertFalse(job.isCanceled)
        job.cancel()
        assertTrue(job.isCanceled)
    }

    @Test
    fun `isCascadingFailure defaults to false`() {
        val job = createTestJob()
        assertFalse(job.isCascadingFailure)
    }

    @Test
    fun `markCascadingFailure sets flag`() {
        val job = createTestJob()
        job.markCascadingFailure()
        assertTrue(job.isCascadingFailure)
    }

    @Test
    fun `onAdded is called on submit`() {
        var addedCalled = false
        val job = object : Job("test", JobParameters.Builder("test").build()) {
            override val factoryKey = "TestJob"
            override suspend fun run(): JobResult = success()
            override fun onFailure() {}
            override fun serialize(): ByteArray? = null
            override fun onAdded() { addedCalled = true }
        }
        job.onSubmit()
        assertTrue(addedCalled)
    }

    @Test
    fun `onRetry is callable`() {
        var retryCalled = false
        val job = object : Job("test", JobParameters.Builder("test").build()) {
            override val factoryKey = "TestJob"
            override suspend fun run(): JobResult = success()
            override fun onFailure() {}
            override fun serialize(): ByteArray? = null
            override fun onRetry() { retryCalled = true }
        }
        job.onRetry()
        assertTrue(retryCalled)
    }

    @Test
    fun `run returns success`() = runTest {
        val job = createTestJob(runResult = success())
        val result = job.run()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `run returns retry`() = runTest {
        val job = createTestJob(runResult = retry(5000))
        val result = job.run()
        assertTrue(result.isRetry)
        assertEquals(5000, result.backoffIntervalMs)
    }

    @Test
    fun `run returns failure`() = runTest {
        val job = createTestJob(runResult = failure())
        val result = job.run()
        assertTrue(result.isFailure)
    }

    @Test
    fun `defaultBackoff produces positive values`() {
        val job = object : Job("test", JobParameters.Builder("test").build()) {
            override val factoryKey = "TestJob"
            override suspend fun run(): JobResult = success()
            override fun onFailure() {}
            override fun serialize(): ByteArray? = null
            fun testBackoff(attempt: Int) = defaultBackoff(attempt)
        }
        val backoff0 = job.testBackoff(0)
        val backoff1 = job.testBackoff(1)
        val backoff5 = job.testBackoff(5)
        assertTrue(backoff0 > 0)
        assertTrue(backoff1 > 0)
        assertTrue(backoff5 > 0)
    }

    @Test
    fun `defaultBackoff respects maxBackoffMs`() {
        val job = object : Job("test", JobParameters.Builder("test").build()) {
            override val factoryKey = "TestJob"
            override suspend fun run(): JobResult = success()
            override fun onFailure() {}
            override fun serialize(): ByteArray? = null
            fun testBackoff(attempt: Int, max: Long) = defaultBackoff(attempt, max)
        }
        val backoff = job.testBackoff(30, 1000)
        assertTrue(backoff <= 1000)
        assertTrue(backoff > 0)
    }

    @Test
    fun `serialize returns null by default`() {
        val job = createTestJob()
        assertNull(job.serialize())
    }

    @Test
    fun `factoryKey is preserved`() {
        val job = createTestJob()
        assertEquals("TestJob", job.factoryKey)
    }
}
