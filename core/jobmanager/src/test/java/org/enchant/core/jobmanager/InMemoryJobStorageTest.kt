package org.enchant.core.jobmanager

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class InMemoryJobStorageTest {
    @Test
    fun `init clears running state`() {
        val storage = InMemoryJobStorage()
        val spec = FullSpec(
            id = "job-1",
            factoryKey = "TestJob",
            queueKey = null,
            createTime = System.currentTimeMillis(),
            lastRunAttemptTime = 0,
            nextBackoffInterval = 0,
            runAttempt = 0,
            maxAttempts = 1,
            lifespan = JobParameters.IMMORTAL,
            serializedData = null,
            serializedInputData = null,
            isRunning = true,
            isMemoryOnly = false,
            globalPriority = JobParameters.PRIORITY_DEFAULT,
            queuePriority = JobParameters.PRIORITY_DEFAULT,
            initialDelay = 0
        )
        storage.insertJobs(listOf(spec))
        storage.updateAllJobsToBePending()
        val retrieved = storage.getJobSpec("job-1")
        assertNotNull(retrieved)
        assertFalse(retrieved!!.isRunning)
    }

    @Test
    fun `insert and retrieve job`() {
        val storage = InMemoryJobStorage()
        val spec = createFullSpec("job-1")
        storage.insertJobs(listOf(spec))
        val retrieved = storage.getJobSpec("job-1")
        assertNotNull(retrieved)
        assertEquals("job-1", retrieved!!.id)
        assertEquals("TestJob", retrieved.factoryKey)
    }

    @Test
    fun `getJobSpec returns null for missing job`() {
        val storage = InMemoryJobStorage()
        assertNull(storage.getJobSpec("nonexistent"))
    }

    @Test
    fun `deleteJob removes job`() {
        val storage = InMemoryJobStorage()
        storage.insertJobs(listOf(createFullSpec("job-1")))
        storage.deleteJob("job-1")
        assertNull(storage.getJobSpec("job-1"))
    }

    @Test
    fun `deleteJobs removes multiple jobs`() {
        val storage = InMemoryJobStorage()
        storage.insertJobs(listOf(createFullSpec("job-1"), createFullSpec("job-2")))
        storage.deleteJobs(listOf("job-1", "job-2"))
        assertNull(storage.getJobSpec("job-1"))
        assertNull(storage.getJobSpec("job-2"))
    }

    @Test
    fun `deleteAll removes everything`() {
        val storage = InMemoryJobStorage()
        storage.insertJobs(listOf(createFullSpec("job-1"), createFullSpec("job-2")))
        storage.deleteAll()
        assertNull(storage.getJobSpec("job-1"))
        assertNull(storage.getJobSpec("job-2"))
        assertEquals(0, storage.getJobCount())
    }

    @Test
    fun `markJobAsRunning updates state`() {
        val storage = InMemoryJobStorage()
        storage.insertJobs(listOf(createFullSpec("job-1")))
        storage.markJobAsRunning("job-1", System.currentTimeMillis())
        val retrieved = storage.getJobSpec("job-1")
        assertNotNull(retrieved)
        assertTrue(retrieved!!.isRunning)
    }

    @Test
    fun `updateJobAfterRetry resets running state`() {
        val storage = InMemoryJobStorage()
        storage.insertJobs(listOf(createFullSpec("job-1")))
        storage.markJobAsRunning("job-1", System.currentTimeMillis())
        storage.updateJobAfterRetry(
            id = "job-1",
            currentTime = System.currentTimeMillis(),
            runAttempt = 1,
            nextBackoffInterval = 5000,
            serializedData = null
        )
        val retrieved = storage.getJobSpec("job-1")
        assertNotNull(retrieved)
        assertFalse(retrieved!!.isRunning)
        assertEquals(1, retrieved.runAttempt)
        assertEquals(5000, retrieved.nextBackoffInterval)
    }

    @Test
    fun `updateJobInputData stores input data`() {
        val storage = InMemoryJobStorage()
        storage.insertJobs(listOf(createFullSpec("job-1")))
        val inputData = byteArrayOf(1, 2, 3)
        storage.updateJobInputData("job-1", inputData)
        val retrieved = storage.getJobSpec("job-1")
        assertNotNull(retrieved)
        assertArrayEquals(inputData, retrieved!!.serializedInputData)
    }

    @Test
    fun `getEligibleJobCount returns correct count`() {
        val storage = InMemoryJobStorage()
        storage.insertJobs(
            listOf(
                createFullSpec("job-1"),
                createFullSpec("job-2"),
                createFullSpec("job-3")
            )
        )
        assertEquals(3, storage.getEligibleJobCount(System.currentTimeMillis()))
    }

    @Test
    fun `getNextEligibleJob returns highest priority job`() {
        val storage = InMemoryJobStorage()
        storage.insertJobs(
            listOf(
                createFullSpec("job-1", globalPriority = JobParameters.PRIORITY_LOW),
                createFullSpec("job-2", globalPriority = JobParameters.PRIORITY_HIGH),
                createFullSpec("job-3", globalPriority = JobParameters.PRIORITY_DEFAULT)
            )
        )
        val next = storage.getNextEligibleJob(System.currentTimeMillis()) { true }
        assertNotNull(next)
        assertEquals("job-2", next!!.id)
    }

    @Test
    fun `getNextEligibleJob respects filter`() {
        val storage = InMemoryJobStorage()
        storage.insertJobs(
            listOf(
                createFullSpec("job-1"),
                createFullSpec("job-2")
            )
        )
        val next = storage.getNextEligibleJob(System.currentTimeMillis()) { it.id == "job-2" }
        assertNotNull(next)
        assertEquals("job-2", next!!.id)
    }

    @Test
    fun `getNextEligibleJob skips running jobs`() {
        val storage = InMemoryJobStorage()
        storage.insertJobs(
            listOf(
                createFullSpec("job-1"),
                createFullSpec("job-2")
            )
        )
        storage.markJobAsRunning("job-1", System.currentTimeMillis())
        val next = storage.getNextEligibleJob(System.currentTimeMillis()) { true }
        assertNotNull(next)
        assertEquals("job-2", next!!.id)
    }

    @Test
    fun `insert and retrieve constraint specs`() {
        val storage = InMemoryJobStorage()
        storage.insertConstraintSpecs(
            listOf(
                ConstraintSpec("job-1", "NetworkConstraint", false),
                ConstraintSpec("job-1", "WifiConstraint", false)
            )
        )
        val constraints = storage.getConstraintSpecs("job-1")
        assertEquals(2, constraints.size)
        assertTrue(constraints.any { it.factoryKey == "NetworkConstraint" })
        assertTrue(constraints.any { it.factoryKey == "WifiConstraint" })
    }

    @Test
    fun `insert and retrieve dependency specs`() {
        val storage = InMemoryJobStorage()
        storage.insertDependencySpecs(
            listOf(
                DependencySpec("job-2", "job-1", false),
                DependencySpec("job-3", "job-1", false)
            )
        )
        val deps = storage.getDependencySpecsThatDependOnJob("job-1")
        assertEquals(2, deps.size)
        assertTrue(deps.all { it.dependsOnJobId == "job-1" })
    }

    @Test
    fun `getNextEligibleJob skips jobs with unmet dependencies`() {
        val storage = InMemoryJobStorage()
        storage.insertJobs(
            listOf(
                createFullSpec("job-1"),
                createFullSpec("job-2")
            )
        )
        storage.insertDependencySpecs(
            listOf(DependencySpec("job-2", "job-1", false))
        )
        val next = storage.getNextEligibleJob(System.currentTimeMillis()) { true }
        assertNotNull(next)
        assertEquals("job-1", next!!.id)
    }

    @Test
    fun `expired lifespan jobs are not eligible`() {
        val storage = InMemoryJobStorage()
        val spec = createFullSpec(
            "job-1",
            lifespan = 1000,
            createTime = System.currentTimeMillis() - 2000
        )
        storage.insertJobs(listOf(spec))
        assertNull(storage.getNextEligibleJob(System.currentTimeMillis()) { true })
    }

    @Test
    fun `getNextEligibleJob returns null when no jobs`() {
        val storage = InMemoryJobStorage()
        assertNull(storage.getNextEligibleJob(System.currentTimeMillis()) { true })
    }

    @Test
    fun `getAllJobs returns all jobs`() {
        val storage = InMemoryJobStorage()
        storage.insertJobs(
            listOf(
                createFullSpec("job-1"),
                createFullSpec("job-2")
            )
        )
        val all = storage.getAllJobs()
        assertEquals(2, all.size)
    }

    private fun createFullSpec(
        id: String,
        globalPriority: Int = JobParameters.PRIORITY_DEFAULT,
        lifespan: Long = JobParameters.IMMORTAL,
        createTime: Long = System.currentTimeMillis()
    ) = FullSpec(
        id = id,
        factoryKey = "TestJob",
        queueKey = null,
        createTime = createTime,
        lastRunAttemptTime = 0,
        nextBackoffInterval = 0,
        runAttempt = 0,
        maxAttempts = 1,
        lifespan = lifespan,
        serializedData = null,
        serializedInputData = null,
        isRunning = false,
        isMemoryOnly = false,
        globalPriority = globalPriority,
        queuePriority = JobParameters.PRIORITY_DEFAULT,
        initialDelay = 0
    )
}
