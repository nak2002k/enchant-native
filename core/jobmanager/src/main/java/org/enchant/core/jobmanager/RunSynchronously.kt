package org.enchant.core.jobmanager

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

fun JobManager.runSynchronously(job: Job, timeoutMs: Long): JobTracker.JobState? {
    val latch = CountDownLatch(1)
    var resultState: JobTracker.JobState? = null

    controller.addTrackerListener(job.id, object : JobTracker.JobListener {
        override fun onStateChanged(jobId: String, state: JobTracker.JobState) {
            if (state == JobTracker.JobState.SUCCESS ||
                state == JobTracker.JobState.FAILURE ||
                state == JobTracker.JobState.CANCELED
            ) {
                controller.removeListener(this)
                resultState = state
                latch.countDown()
            }
        }
    })

    add(job)

    if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) return null
    return resultState
}
