package org.enchant.core.jobmanager

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JobManagerTest {
    @Test
    fun `throws if not initialized`() {
        assertThrows(IllegalStateException::class.java) {
            JobManager.add(createTestJob("job-1"))
        }
    }

    @Test
    fun `cancel throws if not initialized`() {
        assertThrows(IllegalStateException::class.java) {
            JobManager.cancel("job-1")
        }
    }

    @Test
    fun `cancelAll throws if not initialized`() {
        assertThrows(IllegalStateException::class.java) {
            JobManager.cancelAll()
        }
    }

    @Test
    fun `startChain throws if not initialized`() {
        assertThrows(IllegalStateException::class.java) {
            JobManager.startChain(createTestJob("job-1"))
        }
    }

    @Test
    fun `wakeUp throws if not initialized`() {
        assertThrows(IllegalStateException::class.java) {
            JobManager.wakeUp()
        }
    }

    @Test
    fun `Configuration builder requires storage`() {
        assertThrows(IllegalStateException::class.java) {
            JobManager.Configuration.Builder().build()
        }
    }

    @Test
    fun `Configuration builder builds with storage`() {
        val config = JobManager.Configuration.Builder()
            .setStorage(InMemoryJobStorage())
            .build()
        assertNotNull(config.storage)
        assertEquals(4, config.minGeneralRunners)
        assertEquals(16, config.maxGeneralRunners)
        assertEquals(60_000, config.runnerIdleTimeoutMs)
        assertTrue(config.jobFactories.isEmpty())
        assertTrue(config.constraintFactories.isEmpty())
        assertTrue(config.constraintObservers.isEmpty())
        assertTrue(config.reservedRunnerPredicates.isEmpty())
    }

    @Test
    fun `Configuration builder sets all values`() {
        val storage = InMemoryJobStorage()
        val config = JobManager.Configuration.Builder()
            .setStorage(storage)
            .setMinGeneralRunners(2)
            .setMaxGeneralRunners(8)
            .setRunnerIdleTimeout(30_000)
            .build()
        assertEquals(2, config.minGeneralRunners)
        assertEquals(8, config.maxGeneralRunners)
        assertEquals(30_000, config.runnerIdleTimeoutMs)
    }

    @Test
    fun `Configuration builder adds job factory`() {
        val factory = object : Job.Factory<TestJob> {
            override fun create(id: String, serializedData: ByteArray?): TestJob = TestJob(id)
        }
        val config = JobManager.Configuration.Builder()
            .setStorage(InMemoryJobStorage())
            .addJobFactory("TestJob", factory)
            .build()
        assertEquals(1, config.jobFactories.size)
        assertTrue(config.jobFactories.containsKey("TestJob"))
    }

    @Test
    fun `Configuration builder adds constraint factory`() {
        val factory = object : Constraint.Factory<TestConstraint> {
            override fun create(): TestConstraint = TestConstraint()
        }
        val config = JobManager.Configuration.Builder()
            .setStorage(InMemoryJobStorage())
            .addConstraintFactory("TestConstraint", factory)
            .build()
        assertEquals(1, config.constraintFactories.size)
        assertTrue(config.constraintFactories.containsKey("TestConstraint"))
    }

    @Test
    fun `Configuration builder adds reserved runner`() {
        val predicate: (MinimalJobSpec) -> Boolean = { it.factoryKey == "PriorityJob" }
        val config = JobManager.Configuration.Builder()
            .setStorage(InMemoryJobStorage())
            .addReservedRunner(predicate)
            .build()
        assertEquals(1, config.reservedRunnerPredicates.size)
    }

    private class TestJob(
        id: String,
        parameters: JobParameters = JobParameters.Builder(id).build()
    ) : Job(id, parameters) {
        override val factoryKey = "TestJob"
        override suspend fun run(): JobResult = success()
        override fun onFailure() {}
        override fun serialize(): ByteArray? = null
    }

    private class TestConstraint : Constraint {
        override val factoryKey = "TestConstraint"
        override fun isMet(): Boolean = true
    }

    private fun createTestJob(id: String) = TestJob(id)
}
