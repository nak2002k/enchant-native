package org.enchant.core.jobmanager

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

internal class WakefulJobRunner(
    private val name: String,
    private val controller: JobController,
    private val predicate: (MinimalJobSpec) -> Boolean,
    private val idleTimeoutMs: Long,
    private val context: Context
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

            val pm = context.getSystemService(PowerManager::class.java)
            val lock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "EnchantJobManager:$name"
            )
            lock.acquire(10 * 60 * 1000L)

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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val dependents = controller.onFailure(job)
                job.onFailure()
                dependents.forEach { it.onFailure() }
            } finally {
                if (lock.isHeld) lock.release()
            }
        }
    }

    fun stopRunner() {
        running = false
        interrupt()
    }
}
