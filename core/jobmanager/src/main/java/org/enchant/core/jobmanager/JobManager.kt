package org.enchant.core.jobmanager

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

data class Job(val id: String, val run: suspend () -> Unit, val delayMs: Long = 0)

object JobManager {
    private val queue = ConcurrentLinkedQueue<Job>()
    private var scope: CoroutineScope? = null
    private var running = false

    fun init() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    fun enqueue(job: Job) {
        queue.add(job)
        processNext()
    }

    private fun processNext() {
        if (running) return
        running = true
        scope?.launch {
            while (true) {
                val job = queue.poll() ?: break
                if (job.delayMs > 0) delay(job.delayMs)
                try {
                    job.run()
                } catch (_: Exception) {
                }
            }
            running = false
        }
    }

    fun cancelAll() {
        queue.clear()
    }
}
