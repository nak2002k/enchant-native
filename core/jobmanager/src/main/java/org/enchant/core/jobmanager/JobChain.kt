package org.enchant.core.jobmanager

class JobChain private constructor(
    private val jobManager: JobManager,
    private val segments: MutableList<MutableList<Job>>
) {
    companion object {
        fun create(jobManager: JobManager, firstJob: Job): JobChain {
            return JobChain(jobManager, mutableListOf(mutableListOf(firstJob)))
        }

        fun create(jobManager: JobManager, firstJobs: List<Job>): JobChain {
            return JobChain(jobManager, mutableListOf(firstJobs.toMutableList()))
        }
    }

    fun then(job: Job): JobChain {
        segments.add(mutableListOf(job))
        return this
    }

    fun then(jobs: List<Job>): JobChain {
        segments.add(jobs.toMutableList())
        return this
    }

    fun enqueue() {
        jobManager.controller.submitNewJobChain(segments.toList())
    }

    fun enqueue(listener: JobTracker.JobListener) {
        val lastJobs = segments.last()
        for (job in lastJobs) {
            jobManager.controller.addTrackerListener(job.id, listener)
        }
        enqueue()
    }
}
