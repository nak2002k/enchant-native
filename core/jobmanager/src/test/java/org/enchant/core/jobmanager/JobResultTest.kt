package org.enchant.core.jobmanager

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class JobResultTest {
    @Test
    fun `Success is success`() {
        val result = JobResult.Success
        assertTrue(result.isSuccess)
        assertFalse(result.isRetry)
        assertFalse(result.isFailure)
        assertNull(result.outputData)
        assertEquals(0, result.backoffIntervalMs)
    }

    @Test
    fun `SuccessWithData is success with data`() {
        val data = byteArrayOf(1, 2, 3)
        val result = JobResult.SuccessWithData(data)
        assertTrue(result.isSuccess)
        assertFalse(result.isRetry)
        assertFalse(result.isFailure)
        assertArrayEquals(data, result.outputData)
        assertEquals(0, result.backoffIntervalMs)
    }

    @Test
    fun `Retry is retry with backoff`() {
        val result = JobResult.Retry(5000)
        assertFalse(result.isSuccess)
        assertTrue(result.isRetry)
        assertFalse(result.isFailure)
        assertNull(result.outputData)
        assertEquals(5000, result.backoffIntervalMs)
    }

    @Test
    fun `Failure is failure`() {
        val result = JobResult.Failure
        assertFalse(result.isSuccess)
        assertFalse(result.isRetry)
        assertTrue(result.isFailure)
        assertNull(result.outputData)
        assertEquals(0, result.backoffIntervalMs)
    }

    @Test
    fun `FatalFailure is failure with exception`() {
        val exception = RuntimeException("fatal error")
        val result = JobResult.FatalFailure(exception)
        assertFalse(result.isSuccess)
        assertFalse(result.isRetry)
        assertTrue(result.isFailure)
        assertNull(result.outputData)
        assertEquals(0, result.backoffIntervalMs)
        assertEquals(exception, result.exception)
    }

    @Test
    fun `factory functions create correct types`() {
        assertTrue(success() is JobResult.Success)
        assertTrue(success(byteArrayOf(1)) is JobResult.SuccessWithData)
        assertTrue(retry(1000) is JobResult.Retry)
        assertTrue(failure() is JobResult.Failure)
        assertTrue(fatal(RuntimeException()) is JobResult.FatalFailure)
    }

    @Test
    fun `outputData returns null for non-SuccessWithData`() {
        assertNull(JobResult.Success.outputData)
        assertNull(JobResult.Retry(1000).outputData)
        assertNull(JobResult.Failure.outputData)
        assertNull(JobResult.FatalFailure(RuntimeException()).outputData)
    }

    @Test
    fun `backoffIntervalMs returns 0 for non-Retry`() {
        assertEquals(0, JobResult.Success.backoffIntervalMs)
        assertEquals(0, JobResult.SuccessWithData(byteArrayOf(1)).backoffIntervalMs)
        assertEquals(0, JobResult.Failure.backoffIntervalMs)
        assertEquals(0, JobResult.FatalFailure(RuntimeException()).backoffIntervalMs)
    }

    @Test
    fun `SuccessWithData equality`() {
        val data = byteArrayOf(1, 2, 3)
        val result1 = JobResult.SuccessWithData(data)
        val result2 = JobResult.SuccessWithData(data.copyOf())
        assertEquals(result1, result2)
    }

    @Test
    fun `SuccessWithData hashCode`() {
        val data = byteArrayOf(1, 2, 3)
        val result1 = JobResult.SuccessWithData(data)
        val result2 = JobResult.SuccessWithData(data.copyOf())
        assertEquals(result1.hashCode(), result2.hashCode())
    }
}
