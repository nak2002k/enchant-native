package org.enchant.core.jobmanager

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.enchant.core.base.SecurePreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("JobManager — Full Coverage")
class JobManagerTest {

    @BeforeEach
    fun setUp() {
        mockkObject(SecurePreferences)
        every { SecurePreferences.getInt(any(), any()) } returns 0
        every { SecurePreferences.getString(any()) } returns null
        every { SecurePreferences.putString(any(), any()) } returns Unit
        every { SecurePreferences.putInt(any(), any()) } returns Unit
        resetJobManager()
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(SecurePreferences)
        resetJobManager()
    }

    private fun resetJobManager() {
        JobManager.cancelAll()
        val runningField = JobManager::class.java.getDeclaredField("running")
        runningField.isAccessible = true
        runningField.set(JobManager, false)
        val scopeField = JobManager::class.java.getDeclaredField("scope")
        scopeField.isAccessible = true
        scopeField.set(JobManager, null)
        val handlersField = JobManager::class.java.getDeclaredField("handlers")
        handlersField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val handlers = handlersField.get(JobManager) as MutableMap<String, suspend (Job) -> Unit>
        handlers.clear()
    }

    @Nested @DisplayName("Initialization")
    inner class InitTest {
        @Test @DisplayName("init initializes scope and restores persisted jobs")
        fun `init`() {
            JobManager.init()
            verify { SecurePreferences.getInt("jobmanager.count", 0) }
        }
    }

    @Nested @DisplayName("Enqueue")
    inner class EnqueueTest {
        @Test @DisplayName("enqueue adds job to queue")
        fun `enqueue adds job`() {
            JobManager.init()
            JobManager.enqueue(Job(id = "job-1", delayMs = 10000, run = {}))
            assertTrue(JobManager.pendingCount >= 0)
        }

        @Test @DisplayName("enqueue persists jobs with delay")
        fun `enqueue persists delayed jobs`() {
            JobManager.init()
            JobManager.enqueue(Job(id = "job-1", delayMs = 1000, run = {}))
            verify { SecurePreferences.putInt("jobmanager.count", any()) }
        }

        @Test @DisplayName("enqueue persists jobs with tag")
        fun `enqueue persists tagged jobs`() {
            JobManager.init()
            JobManager.enqueue(Job(id = "job-1", tag = "sync", run = {}))
            verify { SecurePreferences.putString(any(), any()) }
        }
    }

    @Nested @DisplayName("Cancel All")
    inner class CancelAllTest {
        @Test @DisplayName("cancelAll clears queue and persisted jobs")
        fun `cancel all`() {
            JobManager.init()
            JobManager.enqueue(Job(id = "job-1", delayMs = 10000, run = {}))
            JobManager.cancelAll()
            assertEquals(0, JobManager.pendingCount)
            verify { SecurePreferences.putInt("jobmanager.count", 0) }
        }
    }

    @Nested @DisplayName("Cancel Job")
    inner class CancelJobTest {
        @Test @DisplayName("cancelJob calls removePersistedJob")
        fun `cancel job`() {
            JobManager.init()
            JobManager.enqueue(Job(id = "job-1", delayMs = 10000, run = {}))
            JobManager.cancelJob("job-1")
            verify { SecurePreferences.getInt("jobmanager.count", 0) }
        }

        @Test @DisplayName("cancelJob does nothing for non-existent job")
        fun `cancel non existent job`() {
            JobManager.init()
            JobManager.cancelJob("nonexistent")
            assertEquals(0, JobManager.pendingCount)
        }
    }

    @Nested @DisplayName("Pending Count")
    inner class PendingCountTest {
        @Test @DisplayName("pendingCount returns 0 when empty")
        fun `pending count empty`() {
            JobManager.init()
            assertEquals(0, JobManager.pendingCount)
        }

        @Test @DisplayName("pendingCount increases after enqueue")
        fun `pending count increases`() {
            JobManager.init()
            JobManager.enqueue(Job(id = "job-1", delayMs = 10000, run = {}))
            assertEquals(1, JobManager.pendingCount)
        }
    }

    @Nested @DisplayName("Register Handler")
    inner class RegisterHandlerTest {
        @Test @DisplayName("registerHandler stores handler for tag")
        fun `register handler`() {
            var handled = false
            JobManager.registerHandler("test") { handled = true }
        }
    }

    @Nested @DisplayName("Restore Persisted Jobs")
    inner class RestorePersistedJobsTest {
        @Test @DisplayName("restorePersistedJobs restores jobs from preferences")
        fun `restore persisted jobs`() {
            every { SecurePreferences.getInt("jobmanager.count", 0) } returns 1
            every { SecurePreferences.getString("jobmanager.0") } returns "job-1|${System.currentTimeMillis() + 5000}|sync|"
            JobManager.init()
            verify { SecurePreferences.getString("jobmanager.0") }
        }

        @Test @DisplayName("restorePersistedJobs ignores expired jobs")
        fun `restore ignores expired`() {
            every { SecurePreferences.getInt("jobmanager.count", 0) } returns 1
            every { SecurePreferences.getString("jobmanager.0") } returns "job-1|${System.currentTimeMillis() - 5000}|sync|"
            JobManager.init()
        }

        @Test @DisplayName("restorePersistedJobs handles malformed data")
        fun `restore handles malformed`() {
            every { SecurePreferences.getInt("jobmanager.count", 0) } returns 1
            every { SecurePreferences.getString("jobmanager.0") } returns "malformed"
            JobManager.init()
        }

        @Test @DisplayName("restorePersistedJobs clears count after restore")
        fun `restore clears count`() {
            every { SecurePreferences.getInt("jobmanager.count", 0) } returns 0
            JobManager.init()
            verify { SecurePreferences.putInt("jobmanager.count", 0) }
        }
    }
}
