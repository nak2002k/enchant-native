package org.enchant.core.jobmanager

import android.content.Context
import java.util.UUID

abstract class Job(
    val id: String = UUID.randomUUID().toString(),
    val parameters: JobParameters
) {
    open fun onAdded() {}
    open fun onRetry() {}
    abstract suspend fun run(): JobResult
    abstract fun onFailure()

    abstract fun serialize(): ByteArray?
    abstract val factoryKey: String

    val runAttempt: Int get() = _runAttempt
    val lastRunAttemptTime: Long get() = _lastRunAttemptTime
    val inputData: ByteArray? get() = _inputData

    val isCanceled: Boolean get() = _canceled
    fun cancel() { _canceled = true }
    fun markCascadingFailure() { _cascadingFailure = true }
    val isCascadingFailure: Boolean get() = _cascadingFailure

    internal var _runAttempt: Int = 0
    internal var _lastRunAttemptTime: Long = 0
    internal var _inputData: ByteArray? = null
    internal var _canceled: Boolean = false
    internal var _cascadingFailure: Boolean = false
    internal lateinit var context: Context

    internal fun onSubmit() { onAdded() }

    protected fun defaultBackoff(attempt: Int, maxBackoffMs: Long = 300_000L): Long {
        val base = (1L shl attempt.coerceAtMost(30)) * 1000
        val jitter = 0.75 + Math.random() * 0.5
        return (minOf(base.toDouble(), maxBackoffMs.toDouble()) * jitter).toLong()
    }

    interface Factory<T : Job> {
        fun create(id: String, serializedData: ByteArray?): T
    }
}
