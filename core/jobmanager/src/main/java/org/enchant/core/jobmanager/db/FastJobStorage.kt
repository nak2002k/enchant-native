package org.enchant.core.jobmanager.db

import kotlinx.coroutines.runBlocking
import org.enchant.core.jobmanager.ConstraintSpec
import org.enchant.core.jobmanager.DependencySpec
import org.enchant.core.jobmanager.FullSpec
import org.enchant.core.jobmanager.JobSpec
import org.enchant.core.jobmanager.JobStorage
import org.enchant.core.jobmanager.MinimalJobSpec
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import org.enchant.core.jobmanager.EligibleMinJobComparator

class FastJobStorage(private val database: JobDatabase) : JobStorage {
    private val jobDao = database.jobDao()
    private val constraintDao = database.constraintDao()
    private val dependencyDao = database.dependencyDao()

    private val minimalJobs = ConcurrentHashMap<String, MinimalJobSpec>()
    private val eligibleJobs = TreeSet<MinimalJobSpec>(EligibleMinJobComparator)
    private val constraintsByJobId = ConcurrentHashMap<String, CopyOnWriteArrayList<ConstraintSpec>>()
    private val dependenciesByJobId = ConcurrentHashMap<String, CopyOnWriteArrayList<DependencySpec>>()
    private val fullSpecCache = ConcurrentHashMap<String, JobSpec>()

    override fun init() {
        runBlocking {
            loadFromDatabase()
            detectAndRemoveCircularDependencies()
        }
    }

    private suspend fun loadFromDatabase() {
        val jobSpecs = jobDao.getAllJobs()
        for (entity in jobSpecs) {
            val spec = entity.toJobSpec()
            fullSpecCache[entity.id] = spec
            val minimal = spec.toMinimal()
            minimalJobs[entity.id] = minimal
            if (isEligible(spec)) {
                eligibleJobs.add(minimal)
            }
        }

        val allConstraints = constraintDao.getAll()
        for (c in allConstraints) {
            val spec = c.toConstraintSpec()
            constraintsByJobId.getOrPut(spec.jobId) { CopyOnWriteArrayList() }.add(spec)
        }

        val allDeps = dependencyDao.getAll()
        for (d in allDeps) {
            val spec = d.toDependencySpec()
            dependenciesByJobId.getOrPut(spec.jobId) { CopyOnWriteArrayList() }.add(spec)
        }
    }

    override fun insertJobs(fullSpecs: List<FullSpec>) {
        runBlocking {
            val entities = fullSpecs.map { it.toEntity() }
            jobDao.insertJobs(entities)

            for (spec in fullSpecs) {
                val jobSpec = spec.toJobSpec()
                fullSpecCache[spec.id] = jobSpec
                val minimal = jobSpec.toMinimal()
                minimalJobs[spec.id] = minimal
                if (isEligible(jobSpec)) {
                    eligibleJobs.add(minimal)
                }
            }
        }
    }

    override fun getJobSpec(id: String): JobSpec? {
        return fullSpecCache[id]
    }

    override fun getNextEligibleJob(
        currentTime: Long,
        filter: (MinimalJobSpec) -> Boolean
    ): JobSpec? {
        return eligibleJobs
            .asSequence()
            .filter { !it.isRunning }
            .filter { hasEligibleRunTime(it, currentTime) }
            .filter { dependenciesByJobId[it.id].isNullOrEmpty() }
            .firstOrNull { filter(it) }
            ?.let { fullSpecCache[it.id] }
    }

    override fun getEligibleJobCount(currentTime: Long): Int {
        return eligibleJobs.count { !it.isRunning && hasEligibleRunTime(it, currentTime) }
    }

    override fun markJobAsRunning(id: String, currentTime: Long) {
        runBlocking {
            jobDao.markJobAsRunning(id, currentTime)
        }
        val spec = fullSpecCache[id] ?: return
        val updated = spec.copy(isRunning = true, lastRunAttemptTime = currentTime)
        fullSpecCache[id] = updated
        minimalJobs[id] = updated.toMinimal()
        eligibleJobs.remove(spec.toMinimal())
    }

    override fun updateJobAfterRetry(
        id: String,
        currentTime: Long,
        runAttempt: Int,
        nextBackoffInterval: Long,
        serializedData: ByteArray?
    ) {
        runBlocking {
            jobDao.updateJobAfterRetry(id, currentTime, runAttempt, nextBackoffInterval, serializedData)
        }
        val spec = fullSpecCache[id] ?: return
        val updated = spec.copy(
            isRunning = false,
            runAttempt = runAttempt,
            nextBackoffInterval = nextBackoffInterval,
            lastRunAttemptTime = currentTime,
            serializedData = serializedData
        )
        fullSpecCache[id] = updated
        minimalJobs[id] = updated.toMinimal()
        if (isEligible(updated)) {
            eligibleJobs.add(updated.toMinimal())
        }
    }

    override fun updateAllJobsToBePending() {
        runBlocking {
            jobDao.updateAllJobsToBePending()
        }
        val updated = fullSpecCache.mapValues { (_, spec) ->
            spec.copy(isRunning = false)
        }
        fullSpecCache.clear()
        fullSpecCache.putAll(updated)
        minimalJobs.clear()
        eligibleJobs.clear()
        fullSpecCache.values.forEach { spec ->
            val minimal = spec.toMinimal()
            minimalJobs[spec.id] = minimal
            if (isEligible(spec)) {
                eligibleJobs.add(minimal)
            }
        }
    }

    override fun updateJobInputData(jobId: String, inputData: ByteArray) {
        runBlocking {
            jobDao.updateJobInputData(jobId, inputData)
        }
        val spec = fullSpecCache[jobId] ?: return
        val updated = spec.copy(serializedInputData = inputData)
        fullSpecCache[jobId] = updated
    }

    override fun deleteJob(id: String) {
        runBlocking {
            jobDao.deleteJob(id)
            constraintDao.deleteConstraintsForJob(id)
            dependencyDao.deleteDependenciesForJob(id)
            dependencyDao.deleteDependentsOfJob(id)
        }
        val spec = fullSpecCache.remove(id)
        spec?.let {
            minimalJobs.remove(id)
            eligibleJobs.remove(it.toMinimal())
        }
        constraintsByJobId.remove(id)
        dependenciesByJobId.remove(id)
    }

    override fun deleteJobs(ids: List<String>) {
        runBlocking {
            jobDao.deleteJobs(ids)
            for (id in ids) {
                constraintDao.deleteConstraintsForJob(id)
                dependencyDao.deleteDependenciesForJob(id)
                dependencyDao.deleteDependentsOfJob(id)
            }
        }
        for (id in ids) {
            val spec = fullSpecCache.remove(id)
            spec?.let {
                minimalJobs.remove(id)
                eligibleJobs.remove(it.toMinimal())
            }
            constraintsByJobId.remove(id)
            dependenciesByJobId.remove(id)
        }
    }

    override fun deleteAll() {
        runBlocking {
            jobDao.deleteAll()
            constraintDao.deleteAll()
            dependencyDao.deleteAll()
        }
        fullSpecCache.clear()
        minimalJobs.clear()
        eligibleJobs.clear()
        constraintsByJobId.clear()
        dependenciesByJobId.clear()
    }

    override fun getConstraintSpecs(jobId: String): List<ConstraintSpec> {
        return constraintsByJobId[jobId]?.toList() ?: emptyList()
    }

    override fun getDependencySpecsThatDependOnJob(jobId: String): List<DependencySpec> {
        return dependenciesByJobId.values.flatten().filter { it.dependsOnJobId == jobId }
    }

    fun insertConstraintSpecs(specs: List<ConstraintSpec>) {
        runBlocking {
            val entities = specs.map { it.toEntity() }
            constraintDao.insertConstraints(entities)
        }
        for (spec in specs) {
            constraintsByJobId.getOrPut(spec.jobId) { CopyOnWriteArrayList() }.add(spec)
        }
    }

    fun insertDependencySpecs(specs: List<DependencySpec>) {
        runBlocking {
            val entities = specs.map { it.toEntity() }
            dependencyDao.insertDependencies(entities)
        }
        for (spec in specs) {
            dependenciesByJobId.getOrPut(spec.jobId) { CopyOnWriteArrayList() }.add(spec)
        }
    }

    private fun isEligible(spec: JobSpec): Boolean {
        return !spec.isRunning &&
            (spec.lifespan <= 0 || System.currentTimeMillis() - spec.createTime < spec.lifespan)
    }

    private fun hasEligibleRunTime(spec: MinimalJobSpec, currentTime: Long): Boolean {
        val readyTime = spec.lastRunAttemptTime + spec.nextBackoffInterval + spec.initialDelay
        return currentTime >= readyTime
    }

    private fun detectAndRemoveCircularDependencies() {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()

        fun hasCycle(jobId: String): Boolean {
            if (recursionStack.contains(jobId)) return true
            if (visited.contains(jobId)) return false

            visited.add(jobId)
            recursionStack.add(jobId)

            val deps = dependenciesByJobId[jobId] ?: emptyList()
            for (dep in deps) {
                if (hasCycle(dep.dependsOnJobId)) {
                    dependenciesByJobId[jobId]?.remove(dep)
                    return true
                }
            }

            recursionStack.remove(jobId)
            return false
        }

        for (jobId in fullSpecCache.keys) {
            if (!visited.contains(jobId)) {
                hasCycle(jobId)
            }
        }
    }

    fun getAllJobs(): List<JobSpec> = fullSpecCache.values.toList()

    fun getJobCount(): Int = fullSpecCache.size

    override fun transformJobs(transform: (JobSpec) -> JobSpec) {
        runBlocking {
            val updated = fullSpecCache.mapValues { (_, spec) -> transform(spec) }
            val entities = updated.values.map { spec ->
                JobSpecEntity(
                    id = spec.id,
                    factoryKey = spec.factoryKey,
                    queueKey = spec.queueKey,
                    createTime = spec.createTime,
                    lastRunAttemptTime = spec.lastRunAttemptTime,
                    nextBackoffInterval = spec.nextBackoffInterval,
                    runAttempt = spec.runAttempt,
                    maxAttempts = spec.maxAttempts,
                    lifespan = spec.lifespan,
                    serializedData = spec.serializedData,
                    serializedInputData = spec.serializedInputData,
                    isRunning = spec.isRunning,
                    isMemoryOnly = spec.isMemoryOnly,
                    globalPriority = spec.globalPriority,
                    queuePriority = spec.queuePriority,
                    initialDelay = spec.initialDelay
                )
            }
            jobDao.insertJobs(entities)

            fullSpecCache.clear()
            fullSpecCache.putAll(updated)
            minimalJobs.clear()
            eligibleJobs.clear()
            fullSpecCache.values.forEach { spec ->
                val minimal = spec.toMinimal()
                minimalJobs[spec.id] = minimal
                if (isEligible(spec)) {
                    eligibleJobs.add(minimal)
                }
            }
        }
    }
}

private fun JobSpecEntity.toJobSpec() = JobSpec(
    id = id,
    factoryKey = factoryKey,
    queueKey = queueKey,
    createTime = createTime,
    lastRunAttemptTime = lastRunAttemptTime,
    nextBackoffInterval = nextBackoffInterval,
    runAttempt = runAttempt,
    maxAttempts = maxAttempts,
    lifespan = lifespan,
    serializedData = serializedData,
    serializedInputData = serializedInputData,
    isRunning = isRunning,
    isMemoryOnly = isMemoryOnly,
    globalPriority = globalPriority,
    queuePriority = queuePriority,
    initialDelay = initialDelay
)

private fun FullSpec.toEntity() = JobSpecEntity(
    id = id,
    factoryKey = factoryKey,
    queueKey = queueKey,
    createTime = createTime,
    lastRunAttemptTime = lastRunAttemptTime,
    nextBackoffInterval = nextBackoffInterval,
    runAttempt = runAttempt,
    maxAttempts = maxAttempts,
    lifespan = lifespan,
    serializedData = serializedData,
    serializedInputData = serializedInputData,
    isRunning = isRunning,
    isMemoryOnly = isMemoryOnly,
    globalPriority = globalPriority,
    queuePriority = queuePriority,
    initialDelay = initialDelay
)

private fun FullSpec.toJobSpec() = JobSpec(
    id = id,
    factoryKey = factoryKey,
    queueKey = queueKey,
    createTime = createTime,
    lastRunAttemptTime = lastRunAttemptTime,
    nextBackoffInterval = nextBackoffInterval,
    runAttempt = runAttempt,
    maxAttempts = maxAttempts,
    lifespan = lifespan,
    serializedData = serializedData,
    serializedInputData = serializedInputData,
    isRunning = isRunning,
    isMemoryOnly = isMemoryOnly,
    globalPriority = globalPriority,
    queuePriority = queuePriority,
    initialDelay = initialDelay
)

private fun ConstraintSpecEntity.toConstraintSpec() = ConstraintSpec(
    jobId = jobId,
    factoryKey = factoryKey,
    isMemoryOnly = isMemoryOnly
)

private fun ConstraintSpec.toEntity() = ConstraintSpecEntity(
    jobId = jobId,
    factoryKey = factoryKey,
    isMemoryOnly = isMemoryOnly
)

private fun DependencySpecEntity.toDependencySpec() = DependencySpec(
    jobId = jobId,
    dependsOnJobId = dependsOnJobId,
    isMemoryOnly = isMemoryOnly
)

private fun DependencySpec.toEntity() = DependencySpecEntity(
    jobId = jobId,
    dependsOnJobId = dependsOnJobId,
    isMemoryOnly = isMemoryOnly
)
