package org.enchant.core.jobmanager

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JobTrackerTest {
    @Test
    fun `initial state is null`() {
        val tracker = JobTracker()
        assertNull(tracker.getState("job-1"))
    }

    @Test
    fun `onStateChange updates state`() {
        val tracker = JobTracker()
        tracker.onStateChange("job-1", JobTracker.JobState.PENDING)
        assertEquals(JobTracker.JobState.PENDING, tracker.getState("job-1"))
    }

    @Test
    fun `onStateChange with job object`() {
        val tracker = JobTracker()
        val job = createTestJob("job-1")
        tracker.onStateChange(job, JobTracker.JobState.RUNNING)
        assertEquals(JobTracker.JobState.RUNNING, tracker.getState("job-1"))
    }

    @Test
    fun `listener is notified on state change`() {
        val tracker = JobTracker()
        var notifiedState: JobTracker.JobState? = null
        var notifiedJobId: String? = null
        val listener = object : JobTracker.JobListener {
            override fun onStateChanged(jobId: String, state: JobTracker.JobState) {
                notifiedJobId = jobId
                notifiedState = state
            }
        }
        tracker.addListener("job-1", listener)
        tracker.onStateChange("job-1", JobTracker.JobState.SUCCESS)
        assertEquals("job-1", notifiedJobId)
        assertEquals(JobTracker.JobState.SUCCESS, notifiedState)
    }

    @Test
    fun `listener is not notified for other jobs`() {
        val tracker = JobTracker()
        var notified = false
        val listener = object : JobTracker.JobListener {
            override fun onStateChanged(jobId: String, state: JobTracker.JobState) {
                notified = true
            }
        }
        tracker.addListener("job-1", listener)
        tracker.onStateChange("job-2", JobTracker.JobState.SUCCESS)
        assertFalse(notified)
    }

    @Test
    fun `removeListener by jobId stops notifications`() {
        val tracker = JobTracker()
        var notified = false
        val listener = object : JobTracker.JobListener {
            override fun onStateChanged(jobId: String, state: JobTracker.JobState) {
                notified = true
            }
        }
        tracker.addListener("job-1", listener)
        tracker.removeListener("job-1", listener)
        tracker.onStateChange("job-1", JobTracker.JobState.SUCCESS)
        assertFalse(notified)
    }

    @Test
    fun `removeListener globally stops all notifications`() {
        val tracker = JobTracker()
        var notified = false
        val listener = object : JobTracker.JobListener {
            override fun onStateChanged(jobId: String, state: JobTracker.JobState) {
                notified = true
            }
        }
        tracker.addListener("job-1", listener)
        tracker.addListener("job-2", listener)
        tracker.removeListener(listener)
        tracker.onStateChange("job-1", JobTracker.JobState.SUCCESS)
        tracker.onStateChange("job-2", JobTracker.JobState.SUCCESS)
        assertFalse(notified)
    }

    @Test
    fun `clear removes all states and listeners`() {
        val tracker = JobTracker()
        var notified = false
        val listener = object : JobTracker.JobListener {
            override fun onStateChanged(jobId: String, state: JobTracker.JobState) {
                notified = true
            }
        }
        tracker.addListener("job-1", listener)
        tracker.onStateChange("job-1", JobTracker.JobState.PENDING)
        tracker.clear()
        assertNull(tracker.getState("job-1"))
        tracker.addListener("job-1", listener)
        tracker.onStateChange("job-1", JobTracker.JobState.SUCCESS)
        assertTrue(notified)
    }

    @Test
    fun `multiple listeners are all notified`() {
        val tracker = JobTracker()
        var notified1 = false
        var notified2 = false
        val listener1 = object : JobTracker.JobListener {
            override fun onStateChanged(jobId: String, state: JobTracker.JobState) { notified1 = true }
        }
        val listener2 = object : JobTracker.JobListener {
            override fun onStateChanged(jobId: String, state: JobTracker.JobState) { notified2 = true }
        }
        tracker.addListener("job-1", listener1)
        tracker.addListener("job-1", listener2)
        tracker.onStateChange("job-1", JobTracker.JobState.SUCCESS)
        assertTrue(notified1)
        assertTrue(notified2)
    }

    @Test
    fun `state transitions are tracked`() {
        val tracker = JobTracker()
        val states = mutableListOf<JobTracker.JobState>()
        val listener = object : JobTracker.JobListener {
            override fun onStateChanged(jobId: String, state: JobTracker.JobState) {
                states.add(state)
            }
        }
        tracker.addListener("job-1", listener)
        tracker.onStateChange("job-1", JobTracker.JobState.PENDING)
        tracker.onStateChange("job-1", JobTracker.JobState.RUNNING)
        tracker.onStateChange("job-1", JobTracker.JobState.SUCCESS)
        assertEquals(
            listOf(
                JobTracker.JobState.PENDING,
                JobTracker.JobState.RUNNING,
                JobTracker.JobState.SUCCESS
            ),
            states
        )
    }

    @Test
    fun `all job states are tracked`() {
        val tracker = JobTracker()
        for (state in JobTracker.JobState.values()) {
            tracker.onStateChange("job-1", state)
            assertEquals(state, tracker.getState("job-1"))
        }
    }

    private fun createTestJob(id: String) = object : Job(id, JobParameters.Builder(id).build()) {
        override val factoryKey = "TestJob"
        override suspend fun run(): JobResult = success()
        override fun onFailure() {}
        override fun serialize(): ByteArray? = null
    }
}
