package org.enchant.core.jobmanager

import java.util.concurrent.ConcurrentHashMap

class JobTracker {
    private val states = ConcurrentHashMap<String, JobState>()
    private val listeners = ConcurrentHashMap<String, MutableList<JobListener>>()

    enum class JobState { PENDING, RUNNING, SUCCESS, FAILURE, CANCELED, IGNORED }

    interface JobListener {
        fun onStateChanged(jobId: String, state: JobState)
    }

    fun onStateChange(jobId: String, state: JobState) {
        states[jobId] = state
        listeners[jobId]?.forEach { it.onStateChanged(jobId, state) }
    }

    fun onStateChange(job: Job, state: JobState) = onStateChange(job.id, state)

    fun addListener(jobId: String, listener: JobListener) {
        listeners.getOrPut(jobId) { mutableListOf() }.add(listener)
    }

    fun removeListener(jobId: String, listener: JobListener) {
        listeners[jobId]?.remove(listener)
    }

    fun removeListener(listener: JobListener) {
        listeners.values.forEach { it.remove(listener) }
    }

    fun getState(jobId: String): JobState? = states[jobId]

    fun clear() {
        states.clear()
        listeners.clear()
    }
}
