package org.enchant.core.jobmanager

sealed class JobResult {
    object Success : JobResult()

    data class SuccessWithData(val data: ByteArray) : JobResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SuccessWithData) return false
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    data class Retry(val backoffMs: Long) : JobResult()

    object Failure : JobResult()

    data class FatalFailure(val exception: RuntimeException) : JobResult()

    val isSuccess: Boolean get() = this is Success || this is SuccessWithData
    val isRetry: Boolean get() = this is Retry
    val isFailure: Boolean get() = this is Failure || this is FatalFailure
    val outputData: ByteArray? get() = when (this) {
        is SuccessWithData -> data
        else -> null
    }
    val backoffIntervalMs: Long get() = when (this) {
        is Retry -> backoffMs
        else -> 0
    }
}

fun success(): JobResult = JobResult.Success
fun success(data: ByteArray): JobResult = JobResult.SuccessWithData(data)
fun retry(backoffMs: Long): JobResult = JobResult.Retry(backoffMs)
fun failure(): JobResult = JobResult.Failure
fun fatal(e: RuntimeException): JobResult = JobResult.FatalFailure(e)
