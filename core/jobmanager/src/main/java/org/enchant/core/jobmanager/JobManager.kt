package org.enchant.core.jobmanager

import kotlinx.coroutines.*
import org.enchant.core.base.SecurePreferences
import java.util.concurrent.ConcurrentLinkedQueue

data class Job(
    val id: String,
    val run: suspend () -> Unit,
    val delayMs: Long = 0,
    val tag: String? = null,
    val maxRetries: Int = 3
)

object JobManager {
    private val queue = ConcurrentLinkedQueue<Job>()
    private var scope: CoroutineScope? = null
    @Volatile
    private var running = false

    fun init() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        restorePersistedJobs()
    }

    private fun restorePersistedJobs() {
        val count = SecurePreferences.getInt("jobmanager.count", 0)
        for (i in 0 until count) {
            val serialized = SecurePreferences.getString("jobmanager.$i") ?: continue
            val parts = serialized.split("|", limit = 3)
            if (parts.size == 3) {
                val id = parts[0]
                val delayMs = parts[1].toLongOrNull() ?: 0L
                if (delayMs > 0) {
                    val remaining = delayMs - System.currentTimeMillis()
                    if (remaining > 0) {
                        enqueue(Job(id = id, delayMs = remaining, run = {}))
                    }
                }
            }
        }
        SecurePreferences.putInt("jobmanager.count", 0)
    }

    fun enqueue(job: Job) {
        if (queue.size >= 50) {
            android.util.Log.w("JobManager", "Max pending jobs (50) reached, rejecting job: ${job.id}")
            return
        }
        queue.add(job)
        if (job.delayMs > 0) {
            persistJob(job)
        }
        processNext()
    }

    private fun persistJob(job: Job) {
        val count = SecurePreferences.getInt("jobmanager.count", 0)
        val data = "${job.id}|${System.currentTimeMillis() + job.delayMs}|${job.tag ?: ""}"
        SecurePreferences.putString("jobmanager.$count", data)
        SecurePreferences.putInt("jobmanager.count", count + 1)
    }

    private fun processNext() {
        if (running) return
        running = true
        scope?.launch {
            while (true) {
                val job = queue.poll() ?: break
                if (job.delayMs > 0) delay(job.delayMs.coerceAtMost(30000L))
                var retries = 0
                while (retries < job.maxRetries) {
                    try {
                        job.run()
                        break
                    } catch (e: Exception) {
                        retries++
                        if (retries >= job.maxRetries) {
                            android.util.Log.w("JobManager", "Job ${job.id} failed after $retries retries")
                        } else {
                            delay(1000L * retries)
                        }
                    }
                }
            }
            running = false
        }
    }

    fun cancelAll() {
        queue.clear()
        SecurePreferences.putInt("jobmanager.count", 0)
    }

    fun cancelJob(jobId: String) {
        queue.removeAll { it.id == jobId }
    }

    val pendingCount: Int get() = queue.size
}
