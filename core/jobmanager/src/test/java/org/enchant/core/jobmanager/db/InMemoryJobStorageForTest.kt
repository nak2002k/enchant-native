package org.enchant.core.jobmanager.db

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

class InMemoryJobStorageForTest : JobStorage {
    private val jobs = ConcurrentHashMap<String, JobSpec>()
    private val constraints = ConcurrentHashMap<String, CopyOnWriteArrayList<ConstraintSpec>>()
    private val dependencies = ConcurrentHashMap<String, CopyOnWriteArrayList<DependencySpec>>()
    private val eligibleJobs = TreeSet<MinimalJobSpec>(EligibleMinJobComparator)

    override fun init() {
        detectAndRemoveCircularDependencies()
    }

    override fun insertJobs(fullSpecs: List<FullSpec>) {
        for (spec in fullSpecs) {
            val jobSpec = spec.toJobSpec()
            jobs[spec.id] = jobSpec
            if (isEligible(jobSpec)) {
                eligibleJobs.add(jobSpec.toMinimal())
            }
        }
    }

    override fun getJobSpec(id: String): JobSpec? = jobs[id]

    override fun getNextEligibleJob(
        currentTime: Long,
        filter: (MinimalJobSpec) -> Boolean
    ): JobSpec? {
        return eligibleJobs
            .asSequence()
            .filter { !it.isRunning }
            .filter { hasEligibleRunTime(it, currentTime) }
            .filter { dependencies[it.id].isNullOrEmpty() }
            .firstOrNull { filter(it) }
            ?.let { jobs[it.id] }
    }

    override fun getEligibleJobCount(currentTime: Long): Int {
        return eligibleJobs.count { !it.isRunning && hasEligibleRunTime(it, currentTime) }
    }

    override fun markJobAsRunning(id: String, currentTime: Long) {
        val spec = jobs[id] ?: return
        val updated = spec.copy(isRunning = true, lastRunAttemptTime = currentTime)
        jobs[id] = updated
        eligibleJobs.remove(spec.toMinimal())
    }

    override fun updateJobAfterRetry(
        id: String,
        currentTime: Long,
        runAttempt: Int,
        nextBackoffInterval: Long,
        serializedData: ByteArray?
    ) {
        val spec = jobs[id] ?: return
        val updated = spec.copy(
            isRunning = false,
            runAttempt = runAttempt,
            nextBackoffInterval = nextBackoffInterval,
            lastRunAttemptTime = currentTime,
            serializedData = serializedData
        )
        jobs[id] = updated
        if (isEligible(updated)) {
            eligibleJobs.add(updated.toMinimal())
        }
    }

    override fun updateAllJobsToBePending() {
        val updated = jobs.mapValues { (_, spec) ->
            spec.copy(isRunning = false)
        }
        jobs.clear()
        jobs.putAll(updated)
        eligibleJobs.clear()
        jobs.values.forEach { spec ->
            if (isEligible(spec)) {
                eligibleJobs.add(spec.toMinimal())
            }
        }
    }

    override fun updateJobInputData(jobId: String, inputData: ByteArray) {
        val spec = jobs[jobId] ?: return
        val updated = spec.copy(serializedInputData = inputData)
        jobs[jobId] = updated
    }

    override fun deleteJob(id: String) {
        val spec = jobs.remove(id)
        spec?.let { eligibleJobs.remove(it.toMinimal()) }
        constraints.remove(id)
        dependencies.remove(id)
    }

    override fun deleteJobs(ids: List<String>) {
        for (id in ids) {
            deleteJob(id)
        }
    }

    override fun deleteAll() {
        jobs.clear()
        constraints.clear()
        dependencies.clear()
        eligibleJobs.clear()
    }

    override fun getConstraintSpecs(jobId: String): List<ConstraintSpec> {
        return constraints[jobId]?.toList() ?: emptyList()
    }

    override fun getDependencySpecsThatDependOnJob(jobId: String): List<DependencySpec> {
        return dependencies.values.flatten().filter { it.dependsOnJobId == jobId }
    }

    fun insertConstraintSpecs(specs: List<ConstraintSpec>) {
        for (spec in specs) {
            constraints.getOrPut(spec.jobId) { CopyOnWriteArrayList() }.add(spec)
        }
    }

    fun insertDependencySpecs(specs: List<DependencySpec>) {
        for (spec in specs) {
            dependencies.getOrPut(spec.jobId) { CopyOnWriteArrayList() }.add(spec)
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

            val deps = dependencies[jobId] ?: emptyList()
            for (dep in deps) {
                if (hasCycle(dep.dependsOnJobId)) {
                    dependencies[jobId]?.remove(dep)
                    return true
                }
            }

            recursionStack.remove(jobId)
            return false
        }

        for (jobId in jobs.keys) {
            if (!visited.contains(jobId)) {
                hasCycle(jobId)
            }
        }
    }

    fun getAllJobs(): List<JobSpec> = jobs.values.toList()

    fun getJobCount(): Int = jobs.size

    override fun transformJobs(transform: (JobSpec) -> JobSpec) {
        val updated = jobs.mapValues { (_, spec) -> transform(spec) }
        jobs.clear()
        jobs.putAll(updated)
        eligibleJobs.clear()
        jobs.values.forEach { spec ->
            if (isEligible(spec)) {
                eligibleJobs.add(spec.toMinimal())
            }
        }
    }
}

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
