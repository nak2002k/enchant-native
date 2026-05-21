package org.enchant.core.jobmanager

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JobParametersTest {
    @Test
    fun `builder creates parameters with defaults`() {
        val params = JobParameters.Builder("test-id").build()
        assertEquals("test-id", params.id)
        assertEquals(JobParameters.IMMORTAL, params.lifespan)
        assertEquals(1, params.maxAttempts)
        assertEquals(JobParameters.UNLIMITED, params.maxInstancesForFactory)
        assertEquals(JobParameters.UNLIMITED, params.maxInstancesForQueue)
        assertNull(params.queueKey)
        assertTrue(params.constraintKeys.isEmpty())
        assertFalse(params.memoryOnly)
        assertEquals(JobParameters.PRIORITY_DEFAULT, params.globalPriority)
        assertEquals(JobParameters.PRIORITY_DEFAULT, params.queuePriority)
        assertEquals(0, params.initialDelayMs)
    }

    @Test
    fun `builder with auto-generated id`() {
        val params = JobParameters.Builder().build()
        assertNotNull(params.id)
        assertTrue(params.id.isNotEmpty())
    }

    @Test
    fun `builder sets lifespan`() {
        val params = JobParameters.Builder("test").setLifespan(60000).build()
        assertEquals(60000, params.lifespan)
    }

    @Test
    fun `builder sets maxAttempts`() {
        val params = JobParameters.Builder("test").setMaxAttempts(5).build()
        assertEquals(5, params.maxAttempts)
    }

    @Test
    fun `builder sets maxInstancesForFactory`() {
        val params = JobParameters.Builder("test").setMaxInstancesForFactory(3).build()
        assertEquals(3, params.maxInstancesForFactory)
    }

    @Test
    fun `builder sets maxInstancesForQueue`() {
        val params = JobParameters.Builder("test").setMaxInstancesForQueue(2).build()
        assertEquals(2, params.maxInstancesForQueue)
    }

    @Test
    fun `builder sets queue key`() {
        val params = JobParameters.Builder("test").setQueue("my-queue").build()
        assertEquals("my-queue", params.queueKey)
    }

    @Test
    fun `builder adds single constraint`() {
        val params = JobParameters.Builder("test").addConstraint("NetworkConstraint").build()
        assertEquals(listOf("NetworkConstraint"), params.constraintKeys)
    }

    @Test
    fun `builder sets multiple constraints`() {
        val params = JobParameters.Builder("test")
            .setConstraints(listOf("NetworkConstraint", "WifiConstraint"))
            .build()
        assertEquals(listOf("NetworkConstraint", "WifiConstraint"), params.constraintKeys)
    }

    @Test
    fun `builder sets memoryOnly`() {
        val params = JobParameters.Builder("test").setMemoryOnly(true).build()
        assertTrue(params.memoryOnly)
    }

    @Test
    fun `builder sets globalPriority`() {
        val params = JobParameters.Builder("test").setGlobalPriority(JobParameters.PRIORITY_HIGH).build()
        assertEquals(JobParameters.PRIORITY_HIGH, params.globalPriority)
    }

    @Test
    fun `builder sets queuePriority`() {
        val params = JobParameters.Builder("test").setQueuePriority(JobParameters.PRIORITY_LOW).build()
        assertEquals(JobParameters.PRIORITY_LOW, params.queuePriority)
    }

    @Test
    fun `builder sets initialDelay`() {
        val params = JobParameters.Builder("test").setInitialDelay(5000).build()
        assertEquals(5000, params.initialDelayMs)
    }

    @Test
    fun `builder chaining works`() {
        val params = JobParameters.Builder("test")
            .setLifespan(30000)
            .setMaxAttempts(3)
            .setQueue("queue-1")
            .addConstraint("NetworkConstraint")
            .setGlobalPriority(JobParameters.PRIORITY_HIGH)
            .setInitialDelay(1000)
            .build()
        assertEquals(30000, params.lifespan)
        assertEquals(3, params.maxAttempts)
        assertEquals("queue-1", params.queueKey)
        assertEquals(listOf("NetworkConstraint"), params.constraintKeys)
        assertEquals(JobParameters.PRIORITY_HIGH, params.globalPriority)
        assertEquals(1000, params.initialDelayMs)
    }

    @Test
    fun `parameters are immutable`() {
        val mutableKeys = mutableListOf("A", "B")
        val params = JobParameters.Builder("test")
            .setConstraints(mutableKeys)
            .build()
        mutableKeys.add("C")
        assertEquals(2, params.constraintKeys.size)
        assertFalse(params.constraintKeys.contains("C"))
    }

    @Test
    fun `constants have expected values`() {
        assertEquals(-1L, JobParameters.IMMORTAL)
        assertEquals(-1, JobParameters.UNLIMITED)
        assertEquals(1, JobParameters.PRIORITY_HIGH)
        assertEquals(0, JobParameters.PRIORITY_DEFAULT)
        assertEquals(-1, JobParameters.PRIORITY_LOW)
        assertEquals(-2, JobParameters.PRIORITY_LOWER)
    }

    @Test
    fun `createTime is set at build time`() {
        val before = System.currentTimeMillis()
        val params = JobParameters.Builder("test").build()
        val after = System.currentTimeMillis()
        assertTrue(params.createTime >= before)
        assertTrue(params.createTime <= after)
    }
}
