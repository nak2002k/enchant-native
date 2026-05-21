package org.enchant.core.jobmanager

import java.util.UUID

data class MinimalJobSpec(
    val id: String = UUID.randomUUID().toString(),
    val factoryKey: String,
    val queueKey: String?,
    val createTime: Long,
    val lastRunAttemptTime: Long,
    val nextBackoffInterval: Long,
    val runAttempt: Int,
    val maxAttempts: Int,
    val lifespan: Long,
    val isRunning: Boolean,
    val isMemoryOnly: Boolean,
    val globalPriority: Int,
    val queuePriority: Int,
    val initialDelay: Long
)

data class FullSpec(
    val id: String,
    val factoryKey: String,
    val queueKey: String?,
    val createTime: Long,
    val lastRunAttemptTime: Long,
    val nextBackoffInterval: Long,
    val runAttempt: Int,
    val maxAttempts: Int,
    val lifespan: Long,
    val serializedData: ByteArray?,
    val serializedInputData: ByteArray?,
    val isRunning: Boolean,
    val isMemoryOnly: Boolean,
    val globalPriority: Int,
    val queuePriority: Int,
    val initialDelay: Long
) {
    fun toMinimal(): MinimalJobSpec = MinimalJobSpec(
        id = id,
        factoryKey = factoryKey,
        queueKey = queueKey,
        createTime = createTime,
        lastRunAttemptTime = lastRunAttemptTime,
        nextBackoffInterval = nextBackoffInterval,
        runAttempt = runAttempt,
        maxAttempts = maxAttempts,
        lifespan = lifespan,
        isRunning = isRunning,
        isMemoryOnly = isMemoryOnly,
        globalPriority = globalPriority,
        queuePriority = queuePriority,
        initialDelay = initialDelay
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FullSpec) return false
        return id == other.id && factoryKey == other.factoryKey && queueKey == other.queueKey &&
            createTime == other.createTime && lastRunAttemptTime == other.lastRunAttemptTime &&
            nextBackoffInterval == other.nextBackoffInterval && runAttempt == other.runAttempt &&
            maxAttempts == other.maxAttempts && lifespan == other.lifespan &&
            serializedData.contentEquals(other.serializedData) &&
            serializedInputData.contentEquals(other.serializedInputData) &&
            isRunning == other.isRunning && isMemoryOnly == other.isMemoryOnly &&
            globalPriority == other.globalPriority && queuePriority == other.queuePriority &&
            initialDelay == other.initialDelay
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + factoryKey.hashCode()
        result = 31 * result + (queueKey?.hashCode() ?: 0)
        result = 31 * result + createTime.hashCode()
        result = 31 * result + lastRunAttemptTime.hashCode()
        result = 31 * result + nextBackoffInterval.hashCode()
        result = 31 * result + runAttempt
        result = 31 * result + maxAttempts
        result = 31 * result + lifespan.hashCode()
        result = 31 * result + (serializedData?.contentHashCode() ?: 0)
        result = 31 * result + (serializedInputData?.contentHashCode() ?: 0)
        result = 31 * result + isRunning.hashCode()
        result = 31 * result + isMemoryOnly.hashCode()
        result = 31 * result + globalPriority
        result = 31 * result + queuePriority
        result = 31 * result + initialDelay.hashCode()
        return result
    }
}

data class JobSpec(
    val id: String,
    val factoryKey: String,
    val queueKey: String?,
    val createTime: Long,
    val lastRunAttemptTime: Long,
    val nextBackoffInterval: Long,
    val runAttempt: Int,
    val maxAttempts: Int,
    val lifespan: Long,
    val serializedData: ByteArray?,
    val serializedInputData: ByteArray?,
    val isRunning: Boolean,
    val isMemoryOnly: Boolean,
    val globalPriority: Int,
    val queuePriority: Int,
    val initialDelay: Long
) {
    fun toMinimal(): MinimalJobSpec = MinimalJobSpec(
        id = id,
        factoryKey = factoryKey,
        queueKey = queueKey,
        createTime = createTime,
        lastRunAttemptTime = lastRunAttemptTime,
        nextBackoffInterval = nextBackoffInterval,
        runAttempt = runAttempt,
        maxAttempts = maxAttempts,
        lifespan = lifespan,
        isRunning = isRunning,
        isMemoryOnly = isMemoryOnly,
        globalPriority = globalPriority,
        queuePriority = queuePriority,
        initialDelay = initialDelay
    )
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JobSpec) return false
        return id == other.id && factoryKey == other.factoryKey && queueKey == other.queueKey &&
            createTime == other.createTime && lastRunAttemptTime == other.lastRunAttemptTime &&
            nextBackoffInterval == other.nextBackoffInterval && runAttempt == other.runAttempt &&
            maxAttempts == other.maxAttempts && lifespan == other.lifespan &&
            serializedData.contentEquals(other.serializedData) &&
            serializedInputData.contentEquals(other.serializedInputData) &&
            isRunning == other.isRunning && isMemoryOnly == other.isMemoryOnly &&
            globalPriority == other.globalPriority && queuePriority == other.queuePriority &&
            initialDelay == other.initialDelay
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + factoryKey.hashCode()
        result = 31 * result + (queueKey?.hashCode() ?: 0)
        result = 31 * result + createTime.hashCode()
        result = 31 * result + lastRunAttemptTime.hashCode()
        result = 31 * result + nextBackoffInterval.hashCode()
        result = 31 * result + runAttempt
        result = 31 * result + maxAttempts
        result = 31 * result + lifespan.hashCode()
        result = 31 * result + (serializedData?.contentHashCode() ?: 0)
        result = 31 * result + (serializedInputData?.contentHashCode() ?: 0)
        result = 31 * result + isRunning.hashCode()
        result = 31 * result + isMemoryOnly.hashCode()
        result = 31 * result + globalPriority
        result = 31 * result + queuePriority
        result = 31 * result + initialDelay.hashCode()
        return result
    }
}

data class ConstraintSpec(
    val jobId: String,
    val factoryKey: String,
    val isMemoryOnly: Boolean
)

data class DependencySpec(
    val jobId: String,
    val dependsOnJobId: String,
    val isMemoryOnly: Boolean
)
