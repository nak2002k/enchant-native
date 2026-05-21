package org.enchant.core.jobmanager.migration

import org.enchant.core.jobmanager.FullSpec
import org.enchant.core.jobmanager.InMemoryJobStorage
import org.enchant.core.jobmanager.JobParameters
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JobMigrationTest {
    private lateinit var storage: InMemoryJobStorage

    @BeforeEach
    fun setup() {
        storage = InMemoryJobStorage()
        storage.init()
    }

    @Test
    fun `migrator requires correct number of migrations`() {
        assertThrows(IllegalArgumentException::class.java) {
            JobMigrator(0, 2, listOf())
        }
    }

    @Test
    fun `migrator requires lastSeenVersion less than currentVersion`() {
        val migrations = listOf(
            object : JobMigration(1) {
                override fun migrate(jobData: JobMigration.JobData): JobMigration.JobData = jobData
            }
        )
        assertThrows(IllegalArgumentException::class.java) {
            JobMigrator(1, 1, migrations)
        }
    }

    @Test
    fun `migrator runs migrations in order`() {
        val migration1 = object : JobMigration(1) {
            override fun migrate(jobData: JobMigration.JobData): JobMigration.JobData {
                return jobData.withFactoryKey("migrated-v1")
            }
        }
        val migration2 = object : JobMigration(2) {
            override fun migrate(jobData: JobMigration.JobData): JobMigration.JobData {
                return jobData.withMaxAttempts(jobData.maxAttempts + 1)
            }
        }

        val migrator = JobMigrator(0, 2, listOf(migration1, migration2))

        storage.insertJobs(listOf(createFullSpec("job-1", factoryKey = "original")))
        val newVersion = migrator.migrate(storage)

        assertEquals(2, newVersion)
        val job = storage.getJobSpec("job-1")
        assertNotNull(job)
        assertEquals("migrated-v1", job!!.factoryKey)
        assertEquals(2, job.maxAttempts)
    }

    @Test
    fun `migration that returns same instance does not modify job`() {
        val noOpMigration = object : JobMigration(1) {
            override fun migrate(jobData: JobMigration.JobData): JobMigration.JobData = jobData
        }

        val migrator = JobMigrator(0, 1, listOf(noOpMigration))

        storage.insertJobs(listOf(createFullSpec("job-1", factoryKey = "original")))
        migrator.migrate(storage)

        val job = storage.getJobSpec("job-1")
        assertNotNull(job)
        assertEquals("original", job!!.factoryKey)
    }

    @Test
    fun `migration can modify all fields`() {
        val fullMigration = object : JobMigration(1) {
            override fun migrate(jobData: JobMigration.JobData): JobMigration.JobData {
                return jobData.copy(
                    factoryKey = "new-factory",
                    queueKey = "new-queue",
                    maxAttempts = 10,
                    lifespan = 60000,
                    data = byteArrayOf(9, 9, 9)
                )
            }
        }

        val migrator = JobMigrator(0, 1, listOf(fullMigration))

        storage.insertJobs(listOf(createFullSpec("job-1")))
        migrator.migrate(storage)

        val job = storage.getJobSpec("job-1")
        assertNotNull(job)
        assertEquals("new-factory", job!!.factoryKey)
        assertEquals("new-queue", job.queueKey)
        assertEquals(10, job.maxAttempts)
        assertEquals(60000, job.lifespan)
        assertArrayEquals(byteArrayOf(9, 9, 9), job.serializedData)
    }

    @Test
    fun `multiple jobs are all migrated`() {
        val migration = object : JobMigration(1) {
            override fun migrate(jobData: JobMigration.JobData): JobMigration.JobData {
                return jobData.withFactoryKey("migrated")
            }
        }

        val migrator = JobMigrator(0, 1, listOf(migration))

        storage.insertJobs(
            listOf(
                createFullSpec("job-1", factoryKey = "a"),
                createFullSpec("job-2", factoryKey = "b"),
                createFullSpec("job-3", factoryKey = "c")
            )
        )
        migrator.migrate(storage)

        assertEquals("migrated", storage.getJobSpec("job-1")!!.factoryKey)
        assertEquals("migrated", storage.getJobSpec("job-2")!!.factoryKey)
        assertEquals("migrated", storage.getJobSpec("job-3")!!.factoryKey)
    }

    @Test
    fun `JobData withFactoryKey creates new instance`() {
        val original = JobMigration.JobData("old", null, 1, -1, null)
        val updated = original.withFactoryKey("new")
        assertNotSame(original, updated)
        assertEquals("new", updated.factoryKey)
        assertEquals("old", original.factoryKey)
    }

    @Test
    fun `JobData withQueueKey creates new instance`() {
        val original = JobMigration.JobData("factory", null, 1, -1, null)
        val updated = original.withQueueKey("queue-1")
        assertNotSame(original, updated)
        assertEquals("queue-1", updated.queueKey)
        assertNull(original.queueKey)
    }

    @Test
    fun `JobData withMaxAttempts creates new instance`() {
        val original = JobMigration.JobData("factory", null, 1, -1, null)
        val updated = original.withMaxAttempts(5)
        assertNotSame(original, updated)
        assertEquals(5, updated.maxAttempts)
        assertEquals(1, original.maxAttempts)
    }

    @Test
    fun `JobData withLifespan creates new instance`() {
        val original = JobMigration.JobData("factory", null, 1, -1, null)
        val updated = original.withLifespan(30000)
        assertNotSame(original, updated)
        assertEquals(30000, updated.lifespan)
        assertEquals(-1L, original.lifespan)
    }

    @Test
    fun `JobData withData creates new instance`() {
        val original = JobMigration.JobData("factory", null, 1, -1, null)
        val data = byteArrayOf(1, 2, 3)
        val updated = original.withData(data)
        assertNotSame(original, updated)
        assertArrayEquals(data, updated.data)
        assertNull(original.data)
    }

    @Test
    fun `JobData equality checks all fields`() {
        val data1 = JobMigration.JobData("factory", "queue", 3, 60000, byteArrayOf(1, 2))
        val data2 = JobMigration.JobData("factory", "queue", 3, 60000, byteArrayOf(1, 2))
        val data3 = JobMigration.JobData("factory", "queue", 3, 60000, byteArrayOf(3, 4))
        assertEquals(data1, data2)
        assertNotEquals(data1, data3)
    }

    @Test
    fun `JobData hashCode is consistent`() {
        val data1 = JobMigration.JobData("factory", "queue", 3, 60000, byteArrayOf(1, 2))
        val data2 = JobMigration.JobData("factory", "queue", 3, 60000, byteArrayOf(1, 2))
        assertEquals(data1.hashCode(), data2.hashCode())
    }

    private fun createFullSpec(
        id: String,
        factoryKey: String = "TestJob"
    ) = FullSpec(
        id = id,
        factoryKey = factoryKey,
        queueKey = null,
        createTime = System.currentTimeMillis(),
        lastRunAttemptTime = 0,
        nextBackoffInterval = 0,
        runAttempt = 0,
        maxAttempts = 1,
        lifespan = JobParameters.IMMORTAL,
        serializedData = null,
        serializedInputData = null,
        isRunning = false,
        isMemoryOnly = false,
        globalPriority = JobParameters.PRIORITY_DEFAULT,
        queuePriority = JobParameters.PRIORITY_DEFAULT,
        initialDelay = 0
    )
}
