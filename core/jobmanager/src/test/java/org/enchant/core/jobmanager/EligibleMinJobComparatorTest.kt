package org.enchant.core.jobmanager

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EligibleMinJobComparatorTest {
    private val comparator = EligibleMinJobComparator

    @Test
    fun `higher global priority comes first`() {
        val job1 = createMinimalSpec("job-1", globalPriority = JobParameters.PRIORITY_LOW)
        val job2 = createMinimalSpec("job-2", globalPriority = JobParameters.PRIORITY_HIGH)
        assertTrue(comparator.compare(job2, job1) < 0)
    }

    @Test
    fun `lower global priority comes later`() {
        val job1 = createMinimalSpec("job-1", globalPriority = JobParameters.PRIORITY_HIGH)
        val job2 = createMinimalSpec("job-2", globalPriority = JobParameters.PRIORITY_LOW)
        assertTrue(comparator.compare(job1, job2) < 0)
    }

    @Test
    fun `equal priority uses createTime`() {
        val job1 = createMinimalSpec("job-1", createTime = 1000)
        val job2 = createMinimalSpec("job-2", createTime = 2000)
        assertTrue(comparator.compare(job1, job2) < 0)
    }

    @Test
    fun `equal priority and createTime uses id`() {
        val job1 = createMinimalSpec("job-a", createTime = 1000)
        val job2 = createMinimalSpec("job-b", createTime = 1000)
        assertTrue(comparator.compare(job1, job2) < 0)
    }

    @Test
    fun `equal jobs return zero`() {
        val job1 = createMinimalSpec("job-1", createTime = 1000)
        val job2 = createMinimalSpec("job-1", createTime = 1000)
        assertEquals(0, comparator.compare(job1, job2))
    }

    private fun createMinimalSpec(
        id: String,
        globalPriority: Int = JobParameters.PRIORITY_DEFAULT,
        createTime: Long = System.currentTimeMillis()
    ) = MinimalJobSpec(
        id = id,
        factoryKey = "TestJob",
        queueKey = null,
        createTime = createTime,
        lastRunAttemptTime = 0,
        nextBackoffInterval = 0,
        runAttempt = 0,
        maxAttempts = 1,
        lifespan = JobParameters.IMMORTAL,
        isRunning = false,
        isMemoryOnly = false,
        globalPriority = globalPriority,
        queuePriority = JobParameters.PRIORITY_DEFAULT,
        initialDelay = 0
    )
}
