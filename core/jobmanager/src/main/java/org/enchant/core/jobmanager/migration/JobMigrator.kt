package org.enchant.core.jobmanager.migration

import org.enchant.core.jobmanager.JobStorage

class JobMigrator(
    private val lastSeenVersion: Int,
    private val currentVersion: Int,
    private val migrations: List<JobMigration>
) {
    init {
        require(migrations.size == currentVersion) {
            "Must have exactly $currentVersion migrations, have ${migrations.size}"
        }
        require(lastSeenVersion < currentVersion) {
            "lastSeenVersion ($lastSeenVersion) must be less than currentVersion ($currentVersion)"
        }
    }

    fun migrate(storage: JobStorage): Int {
        for (i in lastSeenVersion until currentVersion) {
            val migration = migrations[i]
            storage.transformJobs { spec ->
                val original = JobMigration.JobData(
                    factoryKey = spec.factoryKey,
                    queueKey = spec.queueKey,
                    maxAttempts = spec.maxAttempts,
                    lifespan = spec.lifespan,
                    data = spec.serializedData
                )
                val updated = migration.migrate(original)
                if (updated === original) spec
                else spec.copy(
                    factoryKey = updated.factoryKey,
                    queueKey = updated.queueKey,
                    maxAttempts = updated.maxAttempts,
                    lifespan = updated.lifespan,
                    serializedData = updated.data
                )
            }
        }
        return currentVersion
    }
}
