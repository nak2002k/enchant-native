package org.enchant.core.jobmanager

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobService
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build

internal class JobSchedulerScheduler(private val context: Context) : Scheduler {
    private val jobScheduler = context.getSystemService(JobScheduler::class.java)

    override fun schedule(delayMs: Long, constraints: List<Constraint>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val constraintNames = constraints
            .mapNotNull { it.factoryKey }
            .sorted()
            .joinToString("-")
        val jobId = constraintNames.hashCode() and 0x7FFFFFFF

        val js = jobScheduler ?: return
        if (js.getPendingJob(jobId) != null) return

        val builder = JobInfo.Builder(
            jobId,
            ComponentName(context, JobSchedulerSystemService::class.java)
        )
            .setMinimumLatency(delayMs)
            .setPersisted(true)

        js.schedule(builder.build())
    }
}

internal class JobSchedulerSystemService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        try {
            val listener = object : EmptyQueueListener {
                override fun onQueueEmpty() {
                    JobManager.removeOnEmptyQueueListener(this)
                    jobFinished(params, false)
                }
            }
            JobManager.addOnEmptyQueueListener(listener)

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                JobManager.removeOnEmptyQueueListener(listener)
                jobFinished(params, false)
            }, 5 * 60 * 1000L)

            JobManager.wakeUp()
        } catch (e: IllegalStateException) {
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
