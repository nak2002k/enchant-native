package org.enchant.core.jobmanager

import android.content.Context

internal class JobController(
    private val context: Context,
    private val storage: JobStorage,
    private val scheduler: Scheduler,
    private val instantiator: JobInstantiator,
    private val constraintInstantiator: ConstraintInstantiator,
    private val jobManager: JobManager,
    private val config: JobManager.Configuration
) {
    private val lock = java.lang.Object()
    private val runners = mutableListOf<JobRunner>()
    private val tracker = JobTracker()

    fun init() {
        storage.updateAllJobsToBePending()
        for (observer in config.constraintObservers) {
            observer.register(object : ConstraintObserver.Notifier {
                override fun onConstraintMet(reason: String) {
                    wakeUp()
                }
            })
        }
    }

    fun startJobRunners() {
        for ((i, predicate) in config.reservedRunnerPredicates.withIndex()) {
            val runner = JobRunner(
                name = "JobRunner-Rsrv-${i + 1}",
                controller = this,
                predicate = predicate,
                idleTimeoutMs = 0
            )
            runners.add(runner)
            runner.start()
        }

        for (i in 0 until config.minGeneralRunners) {
            val runner = JobRunner(
                name = "JobRunner-Core-${i + 1}",
                controller = this,
                predicate = { true },
                idleTimeoutMs = 0
            )
            runners.add(runner)
            runner.start()
        }
    }

    fun submitNewJobChain(segments: List<List<Job>>) {
        synchronized(lock) {
            var prevIds = emptyList<String>()
            for (segment in segments) {
                for (job in segment) {
                    val fullSpec = buildFullSpec(job, dependsOn = prevIds)
                    storage.insertJobs(listOf(fullSpec))
                    tracker.onStateChange(job, JobTracker.JobState.PENDING)
                    job.onSubmit()
                }
                prevIds = segment.map { it.id }
            }
            val firstJob = segments.first().first()
            val constraints = firstJob.parameters.constraintKeys.map {
                constraintInstantiator.instantiate(it)
            }
            scheduler.schedule(firstJob.parameters.initialDelayMs, constraints)
            lock.notifyAll()
            maybeScaleUpRunners()
        }
    }

    fun pullNextEligibleJob(predicate: (MinimalJobSpec) -> Boolean, timeoutMs: Long): Job? {
        synchronized(lock) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val spec = storage.getNextEligibleJob(System.currentTimeMillis()) { spec ->
                    predicate(spec) && constraintsMet(spec)
                }
                if (spec != null) {
                    storage.markJobAsRunning(spec.id, System.currentTimeMillis())
                    tracker.onStateChange(spec.id, JobTracker.JobState.RUNNING)
                    return instantiateJob(spec)
                }
                if (storage.getEligibleJobCount(System.currentTimeMillis()) == 0) {
                    jobManager.onQueueEmpty()
                }
                lock.wait(500)
            }
            return null
        }
    }

    fun wakeUp() {
        synchronized(lock) {
            lock.notifyAll()
        }
    }

    private fun constraintsMet(spec: MinimalJobSpec): Boolean {
        val constraints = storage.getConstraintSpecs(spec.id)
        for (constraintSpec in constraints) {
            val constraint = constraintInstantiator.instantiate(constraintSpec.factoryKey)
            if (!constraint.isMet()) return false
        }
        return true
    }

    private fun instantiateJob(spec: JobSpec): Job {
        val factory = instantiator.getFactory(spec.factoryKey)
            ?: throw IllegalStateException("No factory for ${spec.factoryKey}")
        @Suppress("UNCHECKED_CAST")
        val job = (factory as Job.Factory<Job>).create(spec.id, spec.serializedData)
        job.context = context
        job._runAttempt = spec.runAttempt
        job._lastRunAttemptTime = spec.lastRunAttemptTime
        job._inputData = spec.serializedInputData
        return job
    }

    fun onSuccess(job: Job, outputData: ByteArray?) {
        synchronized(lock) {
            storage.deleteJob(job.id)
            tracker.onStateChange(job.id, JobTracker.JobState.SUCCESS)
            if (outputData != null) {
                val dependents = storage.getDependencySpecsThatDependOnJob(job.id)
                for (dep in dependents) {
                    storage.updateJobInputData(dep.jobId, outputData)
                }
            }
            lock.notifyAll()
        }
    }

    fun onRetry(job: Job, backoffMs: Long) {
        synchronized(lock) {
            storage.updateJobAfterRetry(
                id = job.id,
                currentTime = System.currentTimeMillis(),
                runAttempt = job._runAttempt + 1,
                nextBackoffInterval = backoffMs,
                serializedData = job.serialize()
            )
            val constraints = job.parameters.constraintKeys.map {
                constraintInstantiator.instantiate(it)
            }
            scheduler.schedule(backoffMs, constraints)
            tracker.onStateChange(job.id, JobTracker.JobState.PENDING)
            job.onRetry()
            lock.notifyAll()
        }
    }

    fun onFailure(job: Job): List<Job> {
        synchronized(lock) {
            val dependents = storage.getDependencySpecsThatDependOnJob(job.id)
                .mapNotNull { spec -> storage.getJobSpec(spec.jobId)?.let { instantiateJob(it) } }

            storage.deleteJobs(listOf(job.id) + dependents.map { it.id })

            tracker.onStateChange(job.id, JobTracker.JobState.FAILURE)
            dependents.forEach { dep ->
                tracker.onStateChange(dep.id, JobTracker.JobState.FAILURE)
                dep.markCascadingFailure()
            }

            lock.notifyAll()
            return dependents
        }
    }

    fun cancelJob(jobId: String) {
        synchronized(lock) {
            storage.deleteJob(jobId)
            tracker.onStateChange(jobId, JobTracker.JobState.CANCELED)
        }
    }

    fun cancelAll() {
        synchronized(lock) {
            storage.deleteAll()
            tracker.clear()
        }
    }

    fun addTrackerListener(jobId: String, listener: JobTracker.JobListener) {
        tracker.addListener(jobId, listener)
    }

    fun removeListener(listener: JobTracker.JobListener) {
        tracker.removeListener(listener)
    }

    private fun maybeScaleUpRunners() {
        val eligibleCount = storage.getEligibleJobCount(System.currentTimeMillis())
        val activeRunners = runners.count { it.isRunning }
        val runnersToSpawn = minOf(
            eligibleCount - activeRunners,
            config.maxGeneralRunners - activeRunners
        )
        for (i in 0 until runnersToSpawn.coerceAtLeast(0)) {
            val runner = JobRunner(
                name = "JobRunner-Temp-${runners.size + 1}",
                controller = this,
                predicate = { true },
                idleTimeoutMs = config.runnerIdleTimeoutMs
            )
            runners.add(runner)
            runner.start()
        }
    }

    private fun buildFullSpec(job: Job, dependsOn: List<String>): FullSpec {
        return FullSpec(
            id = job.id,
            factoryKey = job.factoryKey,
            queueKey = job.parameters.queueKey,
            createTime = job.parameters.createTime,
            lastRunAttemptTime = job._lastRunAttemptTime,
            nextBackoffInterval = 0,
            runAttempt = job._runAttempt,
            maxAttempts = job.parameters.maxAttempts,
            lifespan = job.parameters.lifespan,
            serializedData = job.serialize(),
            serializedInputData = null,
            isRunning = false,
            isMemoryOnly = job.parameters.memoryOnly,
            globalPriority = job.parameters.globalPriority,
            queuePriority = job.parameters.queuePriority,
            initialDelay = job.parameters.initialDelayMs
        )
    }
}
