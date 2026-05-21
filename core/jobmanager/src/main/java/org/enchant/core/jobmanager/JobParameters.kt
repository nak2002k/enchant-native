package org.enchant.core.jobmanager

import java.util.UUID

data class JobParameters(
    val id: String,
    val createTime: Long = System.currentTimeMillis(),
    val lifespan: Long = IMMORTAL,
    val maxAttempts: Int = 1,
    val maxInstancesForFactory: Int = UNLIMITED,
    val maxInstancesForQueue: Int = UNLIMITED,
    val queueKey: String? = null,
    val constraintKeys: List<String> = emptyList(),
    val memoryOnly: Boolean = false,
    val globalPriority: Int = PRIORITY_DEFAULT,
    val queuePriority: Int = PRIORITY_DEFAULT,
    val initialDelayMs: Long = 0
) {
    companion object {
        const val IMMORTAL = -1L
        const val UNLIMITED = -1
        const val PRIORITY_HIGH = 1
        const val PRIORITY_DEFAULT = 0
        const val PRIORITY_LOW = -1
        const val PRIORITY_LOWER = -2
    }

    class Builder(private val id: String = UUID.randomUUID().toString()) {
        private var lifespan = IMMORTAL
        private var maxAttempts = 1
        private var maxInstancesForFactory = UNLIMITED
        private var maxInstancesForQueue = UNLIMITED
        private var queueKey: String? = null
        private var constraintKeys = mutableListOf<String>()
        private var memoryOnly = false
        private var globalPriority = PRIORITY_DEFAULT
        private var queuePriority = PRIORITY_DEFAULT
        private var initialDelayMs = 0L

        fun setLifespan(ms: Long) = apply { lifespan = ms }
        fun setMaxAttempts(n: Int) = apply { maxAttempts = n }
        fun setMaxInstancesForFactory(n: Int) = apply { maxInstancesForFactory = n }
        fun setMaxInstancesForQueue(n: Int) = apply { maxInstancesForQueue = n }
        fun setQueue(key: String?) = apply { queueKey = key }
        fun addConstraint(key: String) = apply { constraintKeys.add(key) }
        fun setConstraints(keys: List<String>) = apply { constraintKeys = keys.toMutableList() }
        fun setMemoryOnly(b: Boolean) = apply { memoryOnly = b }
        fun setGlobalPriority(p: Int) = apply { globalPriority = p }
        fun setQueuePriority(p: Int) = apply { queuePriority = p }
        fun setInitialDelay(ms: Long) = apply { initialDelayMs = ms }

        fun build() = JobParameters(
            id = id,
            lifespan = lifespan,
            maxAttempts = maxAttempts,
            maxInstancesForFactory = maxInstancesForFactory,
            maxInstancesForQueue = maxInstancesForQueue,
            queueKey = queueKey,
            constraintKeys = constraintKeys.toList(),
            memoryOnly = memoryOnly,
            globalPriority = globalPriority,
            queuePriority = queuePriority,
            initialDelayMs = initialDelayMs
        )
    }
}
