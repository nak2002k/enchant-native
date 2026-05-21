package org.enchant.core.jobmanager

import kotlinx.coroutines.runBlocking

internal class JobRunner(
    private val name: String,
    private val controller: JobController,
    private val predicate: (MinimalJobSpec) -> Boolean,
    private val idleTimeoutMs: Long
) : Thread(name) {

    @Volatile
    private var running = true

    val isRunning: Boolean get() = running

    override fun run() {
        while (running) {
            val job = controller.pullNextEligibleJob(predicate, idleTimeoutMs)
            if (job == null) {
                if (idleTimeoutMs > 0) {
                    running = false
                    return
                }
                continue
            }

            if (job.isCanceled) {
                controller.onFailure(job)
                job.onFailure()
                continue
            }

            val elapsed = System.currentTimeMillis() - job.parameters.createTime
            if (job.parameters.lifespan > 0 && elapsed > job.parameters.lifespan) {
                controller.onFailure(job)
                job.onFailure()
                continue
            }

            if (job.parameters.maxAttempts > 0 && job._runAttempt >= job.parameters.maxAttempts) {
                val dependents = controller.onFailure(job)
                job.onFailure()
                dependents.forEach { it.onFailure() }
                continue
            }

            try {
                val result = runBlocking { job.run() }
                when {
                    result.isSuccess -> controller.onSuccess(job, result.outputData)
                    result.isRetry -> controller.onRetry(job, result.backoffIntervalMs)
                    result.isFailure -> {
                        val dependents = controller.onFailure(job)
                        job.onFailure()
                        dependents.forEach { it.onFailure() }
                    }
                }
            } catch (e: Exception) {
                val dependents = controller.onFailure(job)
                job.onFailure()
                dependents.forEach { it.onFailure() }
                if (e is RuntimeException) {
                    throw e
                }
            }
        }
    }

    fun stopRunner() {
        running = false
        interrupt()
    }
}
