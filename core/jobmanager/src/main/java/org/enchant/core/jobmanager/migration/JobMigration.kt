package org.enchant.core.jobmanager.migration

abstract class JobMigration(val endVersion: Int) {
    abstract fun migrate(jobData: JobData): JobData

    data class JobData(
        val factoryKey: String,
        val queueKey: String?,
        val maxAttempts: Int,
        val lifespan: Long,
        val data: ByteArray?
    ) {
        fun withFactoryKey(key: String) = copy(factoryKey = key)
        fun withQueueKey(key: String?) = copy(queueKey = key)
        fun withMaxAttempts(attempts: Int) = copy(maxAttempts = attempts)
        fun withLifespan(lifespan: Long) = copy(lifespan = lifespan)
        fun withData(data: ByteArray?) = copy(data = data)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is JobData) return false
            return factoryKey == other.factoryKey && queueKey == other.queueKey &&
                maxAttempts == other.maxAttempts && lifespan == other.lifespan &&
                data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = factoryKey.hashCode()
            result = 31 * result + (queueKey?.hashCode() ?: 0)
            result = 31 * result + maxAttempts
            result = 31 * result + lifespan.hashCode()
            result = 31 * result + (data?.contentHashCode() ?: 0)
            return result
        }
    }
}
