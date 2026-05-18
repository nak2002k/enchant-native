package org.enchant.core.jobmanager

import kotlinx.coroutines.*
import org.enchant.core.base.SecurePreferences
import java.util.concurrent.ConcurrentHashMap
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
    private val handlers = ConcurrentHashMap<String, suspend (Job) -> Unit>()

    fun registerHandler(tag: String, handler: suspend (Job) -> Unit) {
        handlers[tag] = handler
    }

    fun init() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        restorePersistedJobs()
    }

    private fun restorePersistedJobs() {
        val count = SecurePreferences.getInt("jobmanager.count", 0)
        for (i in 0 until count) {
            val serialized = SecurePreferences.getString("jobmanager.$i") ?: continue
            val parts = serialized.split("|", limit = 4)
            if (parts.size >= 3) {
                val id = parts[0]
                val fireAt = parts[1].toLongOrNull() ?: 0L
                val tag = parts.getOrElse(2) { "" }
                val data = parts.getOrElse(3) { "" }
                val remaining = fireAt - System.currentTimeMillis()
                if (remaining > 0 && tag.isNotEmpty()) {
                    val restoredJob = Job(id = id, delayMs = remaining, tag = tag, run = {})
                    enqueue(restoredJob.copy(run = {
                        handlers[tag]?.invoke(restoredJob)
                    }))
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
        if (job.delayMs > 0 || job.tag != null) {
            persistJob(job)
        }
        processNext()
    }

    private fun persistJob(job: Job) {
        val count = SecurePreferences.getInt("jobmanager.count", 0)
        val data = "${job.id}|${System.currentTimeMillis() + job.delayMs}|${job.tag ?: ""}|"
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
                        removePersistedJob(job.id)
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

    private fun removePersistedJob(jobId: String) {
        val count = SecurePreferences.getInt("jobmanager.count", 0)
        val remaining = (0 until count).mapNotNull { i ->
            SecurePreferences.getString("jobmanager.$i")?.takeIf { !it.startsWith("$jobId|") }
        }
        SecurePreferences.putInt("jobmanager.count", 0)
        remaining.forEachIndexed { i, data ->
            SecurePreferences.putString("jobmanager.$i", data)
        }
        SecurePreferences.putInt("jobmanager.count", remaining.size)
    }

    fun cancelAll() {
        queue.clear()
        SecurePreferences.putInt("jobmanager.count", 0)
    }

    fun cancelJob(jobId: String) {
        queue.removeAll { it.id == jobId }
        removePersistedJob(jobId)
    }

    val pendingCount: Int get() = queue.size
}
