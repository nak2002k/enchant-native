# Build Phases — Enchant JobManager Rewrite

## Overview

**Current state:** 123-line singleton with `ConcurrentLinkedQueue`, tag-based handlers, and `SecurePreferences` persistence. Max 50 jobs, single runner, no chains, no constraints, no multi-scheduler.

**Target state:** Signal-grade job scheduler with chains, constraints, multi-scheduler (InApp + JobScheduler + AlarmManager), SQLite persistence with LRU cache, reserved runners, cascading failure, dual priority, dynamic runner scaling, versioned migrations, and 184+ job implementations.

**Architecture pattern:** Mirrors Signal's `JobManager` exactly — `Job` abstract class, `Job.Parameters` builder, `Job.Result` outcomes, `JobManager.Chain` for chaining, `Constraint` interface, `Scheduler` interface, `JobStorage` SQLite-backed, `JobController` synchronized queue manager, `JobRunner` worker threads.

---

## Phase 1: Foundation (Week 1-2)

**Goal:** Core class hierarchy, Job base class, Parameters builder, Result type, JobManager singleton, basic queue processing.

### 1.1 Repository Structure

```
core/jobmanager/
├── build.gradle.kts
├── src/main/java/org/enchant/core/jobmanager/
│   ├── Job.kt                    ← Abstract base class
│   ├── JobParameters.kt          ← Parameters + Builder
│   ├── JobResult.kt              ← Result type (SUCCESS, RETRY, FAILURE, FATAL)
│   ├── JobManager.kt             ← Public API, singleton facade
│   ├── JobChain.kt               ← Chain builder (.then() pattern)
│   ├── JobController.kt          ← Internal synchronized queue manager
│   ├── JobRunner.kt              ← Worker thread
│   ├── Scheduler.kt              ← Scheduler interface
│   ├── InAppScheduler.kt         ← In-process Handler scheduler
│   ├── Constraint.kt             ← Constraint interface
│   ├── NetworkConstraint.kt      ← Network connectivity constraint
│   ├── ConstraintObserver.kt     ← Reactive constraint monitoring
│   ├── NetworkConstraintObserver.kt ← Network state observer
│   ├── JobTracker.kt             ← In-memory job state tracking
│   ├── JobLogger.kt              ← Consistent logging
│   ├── JsonJobData.kt            ← Type-safe job serialization
│   ├── EmptyQueueListener.kt     ← Queue drained callback
│   └── BootReceiver.kt           ← Kick off on device boot
├── src/test/java/org/enchant/core/jobmanager/
│   ├── JobTest.kt
│   ├── JobParametersTest.kt
│   ├── JobResultTest.kt
│   ├── JobManagerTest.kt
│   ├── JobChainTest.kt
│   ├── JobControllerTest.kt
│   ├── JobRunnerTest.kt
│   ├── NetworkConstraintTest.kt
│   ├── JsonJobDataTest.kt
│   └── JobTrackerTest.kt
└── src/androidTest/java/org/enchant/core/jobmanager/
    └── JobManagerIntegrationTest.kt
```

### 1.2 Job Base Class

```kotlin
abstract class Job(
    val id: String = UUID.randomUUID().toString(),
    val parameters: JobParameters
) {
    // Lifecycle callbacks (override in subclasses)
    open fun onAdded() {}
    open fun onRetry() {}
    abstract suspend fun run(): JobResult
    abstract fun onFailure()

    // Serialization (override in subclasses)
    abstract fun serialize(): ByteArray?
    abstract val factoryKey: String

    // State accessors
    val runAttempt: Int get() = _runAttempt
    val lastRunAttemptTime: Long get() = _lastRunAttemptTime
    val inputData: ByteArray? get() = _inputData

    // Cancellation & cascading failure
    val isCanceled: Boolean get() = _canceled
    fun cancel() { _canceled = true }
    fun markCascadingFailure() { _cascadingFailure = true }
    val isCascadingFailure: Boolean get() = _cascadingFailure

    // Internal (set by JobController)
    internal var _runAttempt: Int = 0
    internal var _lastRunAttemptTime: Long = 0
    internal var _inputData: ByteArray? = null
    internal var _canceled: Boolean = false
    internal var _cascadingFailure: Boolean = false
    internal lateinit var context: Context

    // Called by JobController when job is first submitted
    internal fun onSubmit() { onAdded() }

    // Exponential backoff with jitter
    protected fun defaultBackoff(attempt: Int, maxBackoffMs: Long = 300_000L): Long {
        val base = (1L shl attempt.coerceAtMost(30)) * 1000
        val jitter = (0.75 + Math.random() * 0.5).toFloat()
        return minOf(base, maxBackoffMs) * jitter.toLong()
    }
}
```

### 1.3 JobParameters Builder

```kotlin
data class JobParameters(
    val id: String,
    val createTime: Long = System.currentTimeMillis(),
    val lifespan: Long = IMMORTAL,            // -1 = no limit
    val maxAttempts: Int = 1,
    val maxInstancesForFactory: Int = UNLIMITED,
    val maxInstancesForQueue: Int = UNLIMITED,
    val queueKey: String? = null,             // Queue for serialized execution
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
```

### 1.4 JobResult Type

```kotlin
sealed class JobResult {
    object Success : JobResult() {
        val outputData: ByteArray? = null
    }

    data class SuccessWithData(val outputData: ByteArray) : JobResult()

    data class Retry(val backoffIntervalMs: Long) : JobResult()

    object Failure : JobResult()

    data class FatalFailure(val exception: RuntimeException) : JobResult()

    val isSuccess: Boolean get() = this is Success || this is SuccessWithData
    val isRetry: Boolean get() = this is Retry
    val isFailure: Boolean get() = this is Failure || this is FatalFailure
    val outputData: ByteArray? get() = when (this) {
        is SuccessWithData -> outputData
        else -> null
    }
    val backoffIntervalMs: Long get() = when (this) {
        is Retry -> backoffIntervalMs
        else -> 0
    }
}

// Factory functions
fun Result.success() = JobResult.Success
fun Result.success(data: ByteArray) = JobResult.SuccessWithData(data)
fun Result.retry(backoffMs: Long) = JobResult.Retry(backoffMs)
fun Result.failure() = JobResult.Failure
fun Result.fatal(e: RuntimeException) = JobResult.FatalFailure(e)
```

### 1.5 JobManager Singleton (Public API)

```kotlin
object JobManager {
    private lateinit var controller: JobController
    private lateinit var storage: JobStorage
    private lateinit var scheduler: Scheduler
    private lateinit var instantiator: JobInstantiator
    private lateinit var constraintInstantiator: ConstraintInstantiator
    private val emptyQueueListeners = CopyOnWriteArrayList<EmptyQueueListener>()

    // Public API
    fun add(job: Job) {
        ensureInitialized()
        controller.submitNewJobChain(listOf(listOf(job)))
    }

    fun startChain(firstJob: Job): JobChain {
        ensureInitialized()
        return JobChain(this, listOf(firstJob))
    }

    fun startChain(firstJobs: List<Job>): JobChain {
        ensureInitialized()
        return JobChain(this, firstJobs)
    }

    fun cancel(jobId: String) {
        ensureInitialized()
        controller.cancelJob(jobId)
    }

    fun cancelAll() {
        ensureInitialized()
        controller.cancelAll()
    }

    fun addOnEmptyQueueListener(listener: EmptyQueueListener) {
        emptyQueueListeners.add(listener)
    }

    fun removeOnEmptyQueueListener(listener: EmptyQueueListener) {
        emptyQueueListeners.remove(listener)
    }

    internal fun onQueueEmpty() {
        emptyQueueListeners.forEach { it.onQueueEmpty() }
    }

    internal fun wakeUp() {
        controller.wakeUp()
    }

    // Initialization
    fun initialize(
        context: Context,
        config: Configuration
    ) {
        storage = config.storage
        storage.init()

        instantiator = JobInstantiator(config.jobFactories)
        constraintInstantiator = ConstraintInstantiator(config.constraintFactories)

        scheduler = if (Build.VERSION.SDK_INT < 26) {
            AlarmManagerScheduler(context)
        } else {
            CompositeScheduler(InAppScheduler(this), JobSchedulerScheduler(context))
        }

        controller = JobController(
            context = context,
            storage = storage,
            scheduler = scheduler,
            instantiator = instantiator,
            constraintInstantiator = constraintInstantiator,
            jobManager = this,
            config = config
        )

        controller.init()
        controller.startJobRunners()
    }

    private fun ensureInitialized() {
        if (!::controller.isInitialized) {
            throw IllegalStateException("JobManager not initialized. Call initialize() first.")
        }
    }

    data class Configuration(
        val jobFactories: Map<String, Job.Factory<out Job>>,
        val constraintFactories: Map<String, Constraint.Factory<out Constraint>>,
        val constraintObservers: List<ConstraintObserver>,
        val storage: JobStorage,
        val minGeneralRunners: Int = 4,
        val maxGeneralRunners: Int = 16,
        val runnerIdleTimeoutMs: Long = 60_000,
        val reservedRunnerPredicates: List<(MinimalJobSpec) -> Boolean> = emptyList()
    ) {
        class Builder {
            private val jobFactories = mutableMapOf<String, Job.Factory<out Job>>()
            private val constraintFactories = mutableMapOf<String, Constraint.Factory<out Constraint>>()
            private val constraintObservers = mutableListOf<ConstraintObserver>()
            private var storage: JobStorage? = null
            private var minGeneralRunners = 4
            private var maxGeneralRunners = 16
            private var runnerIdleTimeoutMs = 60_000L
            private val reservedRunnerPredicates = mutableListOf<(MinimalJobSpec) -> Boolean>()

            fun addJobFactory(key: String, factory: Job.Factory<out Job>) = apply {
                jobFactories[key] = factory
            }
            fun addConstraintFactory(key: String, factory: Constraint.Factory<out Constraint>) = apply {
                constraintFactories[key] = factory
            }
            fun addConstraintObserver(observer: ConstraintObserver) = apply {
                constraintObservers.add(observer)
            }
            fun setStorage(s: JobStorage) = apply { storage = s }
            fun setMinGeneralRunners(n: Int) = apply { minGeneralRunners = n }
            fun setMaxGeneralRunners(n: Int) = apply { maxGeneralRunners = n }
            fun setRunnerIdleTimeout(ms: Long) = apply { runnerIdleTimeoutMs = ms }
            fun addReservedRunner(predicate: (MinimalJobSpec) -> Boolean) = apply {
                reservedRunnerPredicates.add(predicate)
            }
            fun build() = Configuration(
                jobFactories = jobFactories,
                constraintFactories = constraintFactories,
                constraintObservers = constraintObservers,
                storage = storage ?: throw IllegalStateException("Storage is required"),
                minGeneralRunners = minGeneralRunners,
                maxGeneralRunners = maxGeneralRunners,
                runnerIdleTimeoutMs = runnerIdleTimeoutMs,
                reservedRunnerPredicates = reservedRunnerPredicates
            )
        }
    }
}
```

### 1.6 JobChain (.then() pattern)

```kotlin
class JobChain(
    private val jobManager: JobManager,
    private val segments: MutableList<MutableList<Job>>
) {
    constructor(jobManager: JobManager, firstJobs: List<Job>) : this(
        jobManager,
        mutableListOf(firstJobs.toMutableList())
    )

    fun then(job: Job): JobChain {
        segments.add(mutableListOf(job))
        return this
    }

    fun then(jobs: List<Job>): JobChain {
        segments.add(jobs.toMutableList())
        return this
    }

    fun enqueue() {
        // Set up dependencies: each segment depends on all jobs in previous segment
        for (i in 1 until segments.size) {
            val prevIds = segments[i - 1].map { it.id }
            for (job in segments[i]) {
                // Dependencies are stored in JobController, not on Job itself
                // The controller will wire them up during submission
            }
        }
        jobManager.controller.submitNewJobChain(segments.toList())
    }

    fun enqueue(listener: JobTracker.JobListener) {
        val lastJobs = segments.last()
        for (job in lastJobs) {
            jobManager.controller.addTrackerListener(job.id, listener)
        }
        enqueue()
    }
}
```

### 1.7 JobController (Synchronized Queue Manager)

```kotlin
internal class JobController(
    private val context: Context,
    private val storage: JobStorage,
    private val scheduler: Scheduler,
    private val instantiator: JobInstantiator,
    private val constraintInstantiator: ConstraintInstantiator,
    private val jobManager: JobManager,
    private val config: JobManager.Configuration
) {
    private val lock = Any()
    private val runners = mutableListOf<JobRunner>()
    private val tracker = JobTracker()

    fun init() {
        // Reset all running jobs to pending (crash recovery)
        storage.updateAllJobsToBePending()
        // Register constraint observers
        for (observer in config.constraintObservers) {
            observer.register(object : ConstraintObserver.Notifier {
                override fun onConstraintMet(reason: String) {
                    wakeUp()
                }
            })
        }
    }

    fun startJobRunners() {
        // Start reserved runners
        for ((i, predicate) in config.reservedRunnerPredicates.withIndex()) {
            val runner = JobRunner(
                name = "JobRunner-Rsrv-${i + 1}",
                controller = this,
                predicate = predicate,
                idleTimeoutMs = 0
            )
            runners.add(runner)
            runner.start()
        }

        // Start core general runners
        for (i in 0 until config.minGeneralRunners) {
            val runner = JobRunner(
                name = "JobRunner-Core-${i + 1}",
                controller = this,
                predicate = { true },
                idleTimeoutMs = 0
            )
            runners.add(runner)
            runner.start()
        }
    }

    fun submitNewJobChain(segments: List<List<Job>>) {
        synchronized(lock) {
            var prevIds = emptyList<String>()
            for (segment in segments) {
                for (job in segment) {
                    val fullSpec = buildFullSpec(job, dependsOn = prevIds)
                    storage.insertJobs(listOf(fullSpec))
                    tracker.onStateChange(job, JobTracker.JobState.PENDING)
                }
                prevIds = segment.map { it.id }
            }
            // Schedule initial delay
            val firstJob = segments.first().first()
            val constraints = firstJob.parameters.constraintKeys.map {
                constraintInstantiator.instantiate(it)
            }
            scheduler.schedule(firstJob.parameters.initialDelayMs, constraints)
            // Notify runners
            notifyAll()
            maybeScaleUpRunners()
        }
    }

    fun pullNextEligibleJob(predicate: (MinimalJobSpec) -> Boolean, timeoutMs: Long): Job? {
        synchronized(lock) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val spec = storage.getNextEligibleJob(System.currentTimeMillis()) { spec ->
                    predicate(spec) && constraintsMet(spec)
                }
                if (spec != null) {
                    storage.markJobAsRunning(spec.id, System.currentTimeMillis())
                    tracker.onStateChange(spec.id, JobTracker.JobState.RUNNING)
                    return instantiateJob(spec)
                }
                // Check if queue is empty
                if (storage.getEligibleJobCount(System.currentTimeMillis()) == 0) {
                    jobManager.onQueueEmpty()
                }
                lock.wait(500) // Wait for wakeUp()
            }
            return null
        }
    }

    fun wakeUp() {
        synchronized(lock) {
            lock.notifyAll()
        }
    }

    private fun constraintsMet(spec: MinimalJobSpec): Boolean {
        // Check each constraint
        val constraints = storage.getConstraintSpecs(spec.id)
        for (constraintSpec in constraints) {
            val constraint = constraintInstantiator.instantiate(constraintSpec.factoryKey)
            if (!constraint.isMet()) return false
        }
        return true
    }

    private fun instantiateJob(spec: MinimalJobSpec): Job {
        val factory = instantiator.getFactory(spec.factoryKey)
            ?: throw IllegalStateException("No factory for ${spec.factoryKey}")
        val job = factory.create(spec.id, spec.serializedData)
        job.context = context
        job._runAttempt = spec.runAttempt
        job._lastRunAttemptTime = spec.lastRunAttemptTime
        return job
    }

    fun onSuccess(job: Job, outputData: ByteArray?) {
        synchronized(lock) {
            storage.deleteJob(job.id)
            tracker.onStateChange(job.id, JobTracker.JobState.SUCCESS)
            // Pass outputData to dependents
            if (outputData != null) {
                val dependents = storage.getDependencySpecsThatDependOnJob(job.id)
                for (dep in dependents) {
                    storage.updateJobInputData(dep.jobId, outputData)
                }
            }
            lock.notifyAll()
        }
    }

    fun onRetry(job: Job, backoffMs: Long) {
        synchronized(lock) {
            storage.updateJobAfterRetry(
                id = job.id,
                currentTime = System.currentTimeMillis(),
                runAttempt = job._runAttempt + 1,
                nextBackoffInterval = backoffMs,
                serializedData = job.serialize()
            )
            val constraints = job.parameters.constraintKeys.map {
                constraintInstantiator.instantiate(it)
            }
            scheduler.schedule(backoffMs, constraints)
            tracker.onStateChange(job.id, JobTracker.JobState.PENDING)
            job.onRetry()
            lock.notifyAll()
        }
    }

    fun onFailure(job: Job): List<Job> {
        synchronized(lock) {
            // Find all transitive dependents
            val dependents = storage.getDependencySpecsThatDependOnJob(job.id)
                .map { instantiateJob(storage.getJobSpec(it.jobId)!!) }

            // Delete all from storage
            storage.deleteJobs(listOf(job.id) + dependents.map { it.id })

            // Notify tracker
            tracker.onStateChange(job.id, JobTracker.JobState.FAILURE)
            dependents.forEach { dep ->
                tracker.onStateChange(dep.id, JobTracker.JobState.FAILURE)
                dep.markCascadingFailure()
            }

            lock.notifyAll()
            return dependents
        }
    }

    fun cancelJob(jobId: String) {
        synchronized(lock) {
            storage.deleteJob(jobId)
            tracker.onStateChange(jobId, JobTracker.JobState.CANCELED)
        }
    }

    fun cancelAll() {
        synchronized(lock) {
            storage.deleteAll()
            tracker.clear()
        }
    }

    private fun maybeScaleUpRunners() {
        val eligibleCount = storage.getEligibleJobCount(System.currentTimeMillis())
        val activeRunners = runners.count { it.isAlive }
        val runnersToSpawn = minOf(
            eligibleCount - activeRunners,
            config.maxGeneralRunners - activeRunners
        )
        for (i in 0 until runnersToSpawn.coerceAtLeast(0)) {
            val runner = JobRunner(
                name = "JobRunner-Temp-${runners.size + 1}",
                controller = this,
                predicate = { true },
                idleTimeoutMs = config.runnerIdleTimeoutMs
            )
            runners.add(runner)
            runner.start()
        }
    }
}
```

### 1.8 JobRunner (Worker Thread)

```kotlin
internal class JobRunner(
    private val name: String,
    private val controller: JobController,
    private val predicate: (MinimalJobSpec) -> Boolean,
    private val idleTimeoutMs: Long
) : Thread(name) {

    @Volatile
    private var _isAlive = true

    override fun run() {
        while (_isAlive) {
            val job = controller.pullNextEligibleJob(predicate, idleTimeoutMs)
            if (job == null) {
                if (idleTimeoutMs > 0) {
                    _isAlive = false
                    return
                }
                continue
            }

            // Check lifespan expiration
            val elapsed = System.currentTimeMillis() - job.parameters.createTime
            if (job.parameters.lifespan > 0 && elapsed > job.parameters.lifespan) {
                controller.onFailure(job)
                job.onFailure()
                continue
            }

            // Check max attempts
            if (job._runAttempt >= job.parameters.maxAttempts && job.parameters.maxAttempts > 0) {
                val dependents = controller.onFailure(job)
                job.onFailure()
                dependents.forEach { it.onFailure() }
                continue
            }

            try {
                val result = job.run()
                when {
                    result.isSuccess -> controller.onSuccess(job, result.outputData)
                    result.isRetry -> controller.onRetry(job, result.backoffIntervalMs)
                    result.isFailure -> {
                        val dependents = controller.onFailure(job)
                        job.onFailure()
                        dependents.forEach { it.onFailure() }
                        if (result is JobResult.FatalFailure) throw result.exception
                    }
                }
            } catch (e: Exception) {
                val dependents = controller.onFailure(job)
                job.onFailure()
                dependents.forEach { it.onFailure() }
            }
        }
    }
}
```

### 1.9 Scheduler Interface

```kotlin
interface Scheduler {
    fun schedule(delayMs: Long, constraints: List<Constraint>)
}

class InAppScheduler(private val jobManager: JobManager) : Scheduler {
    private val handler = Handler(Looper.getMainLooper())

    override fun schedule(delayMs: Long, constraints: List<Constraint>) {
        if (delayMs > 0 && constraints.all { it.isMet() }) {
            handler.postDelayed({ jobManager.wakeUp() }, delayMs)
        }
    }
}

class CompositeScheduler(private val schedulers: List<Scheduler>) : Scheduler {
    override fun schedule(delayMs: Long, constraints: List<Constraint>) {
        for (scheduler in schedulers) {
            scheduler.schedule(delayMs, constraints)
        }
    }
}
```

### 1.10 Constraint Interface

```kotlin
interface Constraint {
    fun isMet(): Boolean
    val factoryKey: String

    interface Factory<T : Constraint> {
        fun create(): T
    }
}

class NetworkConstraint(private val context: Context) : Constraint {
    override val factoryKey = "NetworkConstraint"

    override fun isMet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
```

### 1.11 JobStorage Interface (SQLite-backed)

```kotlin
interface JobStorage {
    fun init()
    fun insertJobs(fullSpecs: List<FullSpec>)
    fun getJobSpec(id: String): JobSpec?
    fun getNextEligibleJob(currentTime: Long, filter: (MinimalJobSpec) -> Boolean): JobSpec?
    fun getEligibleJobCount(currentTime: Long): Int
    fun markJobAsRunning(id: String, currentTime: Long)
    fun updateJobAfterRetry(id: String, currentTime: Long, runAttempt: Int, nextBackoffInterval: Long, serializedData: ByteArray?)
    fun updateAllJobsToBePending()
    fun updateJobInputData(jobId: String, inputData: ByteArray)
    fun deleteJob(id: String)
    fun deleteJobs(ids: List<String>)
    fun deleteAll()
    fun getConstraintSpecs(jobId: String): List<ConstraintSpec>
    fun getDependencySpecsThatDependOnJob(jobId: String): List<DependencySpec>
}
```

### 1.12 JsonJobData (Type-safe Serialization)

```kotlin
class JsonJobData private constructor(
    private val strings: Map<String, String>,
    private val longs: Map<String, Long>,
    private val ints: Map<String, Int>,
    private val booleans: Map<String, Boolean>,
    private val blobs: Map<String, String>  // Base64
) {
    fun getString(key: String): String? = strings[key]
    fun getLong(key: String): Long? = longs[key]
    fun getInt(key: String): Int? = ints[key]
    fun getBoolean(key: String): Boolean? = booleans[key]
    fun getBlob(key: String): ByteArray? = blobs[key]?.let { Base64.decode(it, Base64.DEFAULT) }

    fun serialize(): ByteArray? {
        if (strings.isEmpty() && longs.isEmpty() && ints.isEmpty() && booleans.isEmpty() && blobs.isEmpty()) return null
        val json = buildJsonObject {
            putJsonObject("strings") { strings.forEach { (k, v) -> put(k, v) } }
            putJsonObject("longs") { longs.forEach { (k, v) -> put(k, v) } }
            putJsonObject("ints") { ints.forEach { (k, v) -> put(k, v) } }
            putJsonObject("booleans") { booleans.forEach { (k, v) -> put(k, v) } }
            putJsonObject("blobs") { blobs.forEach { (k, v) -> put(k, v) } }
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun deserialize(data: ByteArray?): JsonJobData {
            if (data == null) return JsonJobData(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())
            val json = Json.parseToJsonElement(data.decodeToString()).jsonObject
            return JsonJobData(
                strings = json["strings"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap(),
                longs = json["longs"]?.jsonObject?.mapValues { it.value.jsonPrimitive.long } ?: emptyMap(),
                ints = json["ints"]?.jsonObject?.mapValues { it.value.jsonPrimitive.int } ?: emptyMap(),
                booleans = json["booleans"]?.jsonObject?.mapValues { it.value.jsonPrimitive.boolean } ?: emptyMap(),
                blobs = json["blobs"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
            )
        }
    }

    class Builder {
        private val strings = mutableMapOf<String, String>()
        private val longs = mutableMapOf<String, Long>()
        private val ints = mutableMapOf<String, Int>()
        private val booleans = mutableMapOf<String, Boolean>()
        private val blobs = mutableMapOf<String, String>()

        fun putString(key: String, value: String) = apply { strings[key] = value }
        fun putLong(key: String, value: Long) = apply { longs[key] = value }
        fun putInt(key: String, value: Int) = apply { ints[key] = value }
        fun putBoolean(key: String, value: Boolean) = apply { booleans[key] = value }
        fun putBlob(key: String, value: ByteArray) = apply { blobs[key] = Base64.encodeToString(value, Base64.DEFAULT) }

        fun build(): JsonJobData = JsonJobData(strings, longs, ints, booleans, blobs)
    }
}
```

### 1.13 JobTracker (In-memory State Tracking)

```kotlin
internal class JobTracker {
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

    fun removeListener(listener: JobListener) {
        listeners.values.forEach { it.remove(listener) }
    }

    fun getState(jobId: String): JobState? = states[jobId]

    fun clear() {
        states.clear()
        listeners.clear()
    }
}
```

### 1.14 Tests

Write comprehensive tests for each component:

- `JobTest.kt` — Lifecycle callbacks, serialization, cancellation, cascading failure flag
- `JobParametersTest.kt` — Builder pattern, defaults, immutability
- `JobResultTest.kt` — All result types, output data, backoff
- `JobManagerTest.kt` — Add, cancel, cancelAll, empty queue listener
- `JobChainTest.kt` — Sequential chains, parallel segments, dependency wiring
- `JobControllerTest.kt` — Submit, pull eligible, success, retry, failure, cascading
- `JobRunnerTest.kt` — Execution, lifespan check, max attempts, idle timeout
- `NetworkConstraintTest.kt` — Connected, disconnected, no network
- `JsonJobDataTest.kt` — Serialize/deserialize all types, roundtrip
- `JobTrackerTest.kt` — State transitions, listeners, clear

### 1.15 Deliverables

- [ ] Job base class with lifecycle callbacks
- [ ] JobParameters builder with all fields
- [ ] JobResult sealed class
- [ ] JobManager singleton with public API
- [ ] JobChain with .then() pattern
- [ ] JobController with synchronized queue management
- [ ] JobRunner with worker thread
- [ ] Scheduler interface + InAppScheduler + CompositeScheduler
- [ ] Constraint interface + NetworkConstraint
- [ ] ConstraintObserver interface + NetworkConstraintObserver
- [ ] JobStorage interface
- [ ] JobTracker with state tracking
- [ ] JsonJobData with type-safe serialization
- [ ] EmptyQueueListener
- [ ] All tests pass
- [ ] build.gradle.kts updated with Room dependency

---

## Phase 2: SQLite Persistence (Week 3-4)

**Goal:** Full SQLite-backed job storage with LRU cache, eligibility sorting, constraint/dependency tracking.

### 2.1 Room Database Schema

```kotlin
@Database(entities = [JobSpecEntity::class, ConstraintSpecEntity::class, DependencySpecEntity::class], version = 1)
abstract class JobDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun constraintDao(): ConstraintDao
    abstract fun dependencyDao(): DependencyDao
}

@Entity(tableName = "jobs")
data class JobSpecEntity(
    @PrimaryKey val id: String,
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
)

@Entity(tableName = "constraints", primaryKeys = ["jobId", "factoryKey"])
data class ConstraintSpecEntity(
    val jobId: String,
    val factoryKey: String,
    val isMemoryOnly: Boolean
)

@Entity(tableName = "dependencies", primaryKeys = ["jobId", "dependsOnJobId"])
data class DependencySpecEntity(
    val jobId: String,
    val dependsOnJobId: String,
    val isMemoryOnly: Boolean
)
```

### 2.2 FastJobStorage (LRU Cache + SQLite)

```kotlin
class FastJobStorage(private val database: JobDatabase) : JobStorage {
    private val minimalJobs = mutableListOf<MinimalJobSpec>()
    private val eligibleJobs = TreeSet<MinimalJobSpec>(EligibleMinJobComparator)
    private val constraintsByJobId = mutableMapOf<String, MutableList<ConstraintSpec>>()
    private val dependenciesByJobId = mutableMapOf<String, MutableList<DependencySpec>>()

    override fun init() {
        loadFromDatabase()
        detectAndRemoveCircularDependencies()
    }

    private fun loadFromDatabase() {
        // Load all jobs from DB into memory
        val jobSpecs = database.jobDao().getAllMinimalJobs()
        minimalJobs.addAll(jobSpecs)
        for (spec in jobSpecs) {
            if (isEligible(spec)) {
                eligibleJobs.add(spec)
            }
        }
        // Load constraints and dependencies
        val allConstraints = database.constraintDao().getAll()
        for (c in allConstraints) {
            constraintsByJobId.getOrPut(c.jobId) { mutableListOf() }.add(c)
        }
        val allDeps = database.dependencyDao().getAll()
        for (d in allDeps) {
            dependenciesByJobId.getOrPut(d.jobId) { mutableListOf() }.add(d)
        }
    }

    override fun getNextEligibleJob(currentTime: Long, filter: (MinimalJobSpec) -> Boolean): JobSpec? {
        return eligibleJobs
            .asSequence()
            .filter { !it.isRunning }
            .filter { hasEligibleRunTime(it, currentTime) }
            .filter { dependenciesByJobId[it.id].isNullOrEmpty() }
            .firstOrNull { filter(it) }
            ?.let { database.jobDao().getFullSpec(it.id) }
    }

    // ... (implement all other methods)
}
```

### 2.3 Eligibility Comparator

```kotlin
object EligibleMinJobComparator : Comparator<MinimalJobSpec> {
    override fun compare(o1: MinimalJobSpec, o2: MinimalJobSpec): Int {
        return when {
            o1.globalPriority > o2.globalPriority -> -1
            o1.globalPriority < o2.globalPriority -> 1
            o1.createTime < o2.createTime -> -1
            o1.createTime > o2.createTime -> 1
            else -> o1.id.compareTo(o2.id)
        }
    }
}
```

### 2.4 Deliverables

- [ ] Room database with 3 entities (jobs, constraints, dependencies)
- [ ] DAOs with all required queries
- [ ] FastJobStorage with LRU cache
- [ ] Eligibility sorting (TreeSet)
- [ ] Circular dependency detection
- [ ] Time-travel protection
- [ ] All tests pass

---

## Phase 3: Multi-Scheduler & Constraints (Week 5-6)

**Goal:** JobSchedulerScheduler, AlarmManagerScheduler, 18 constraints, constraint observers.

### 3.1 JobSchedulerScheduler (API 26+)

```kotlin
@RequiresApi(26)
class JobSchedulerScheduler(private val context: Context) : Scheduler {
    private val jobScheduler = context.getSystemService(JobScheduler::class.java)

    override fun schedule(delayMs: Long, constraints: List<Constraint>) {
        val constraintNames = constraints
            .mapNotNull { it.getJobSchedulerKeyPart() }
            .sorted()
            .joinToString("-")
        val jobId = constraintNames.hashCode()

        if (jobScheduler.getPendingJob(jobId) != null) return

        val builder = JobInfo.Builder(jobId, ComponentName(context, SystemService::class.java))
            .setMinimumLatency(delayMs)
            .setPersisted(true)

        for (constraint in constraints) {
            constraint.applyToJobInfo(builder)
        }

        jobScheduler.schedule(builder.build())
    }

    @RequiresApi(26)
    class SystemService : JobService() {
        override fun onStartJob(params: JobParameters): Boolean {
            val jm = getJobManager()
            jm.addOnEmptyQueueListener(object : EmptyQueueListener {
                override fun onQueueEmpty() {
                    jm.removeOnEmptyQueueListener(this)
                    jobFinished(params, false)
                }
            })
            jm.wakeUp()
            return true
        }

        override fun onStopJob(params: JobParameters): Boolean = true
    }
}
```

### 3.2 AlarmManagerScheduler (API <26)

```kotlin
class AlarmManagerScheduler(private val context: Context) : Scheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(delayMs: Long, constraints: List<Constraint>) {
        if (delayMs > 0 && constraints.all { it.isMet() }) {
            val intent = Intent(context, RetryReceiver::class.java)
            intent.action = "org.enchant.jobmanager.RETRY_${UUID.randomUUID()}"
            val pending = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
            )
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pending)
        }
    }

    class RetryReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            getJobManager().wakeUp()
        }
    }
}
```

### 3.3 All 18 Constraints

| Constraint | Key | isMet() Logic |
|-----------|-----|---------------|
| NetworkConstraint | NetworkConstraint | ConnectivityManager.isConnected() |
| WifiConstraint | WifiConstraint | WiFi connected |
| ChargingConstraint | ChargingConstraint | BatteryManager.isCharging() |
| BatteryNotLowConstraint | BatteryNotLowConstraint | Not low battery |
| NotInCallConstraint | NotInCallConstraint | No active telecom call |
| RegisteredConstraint | RegisteredConstraint | User is registered |
| DiskSpaceNotLowConstraint | DiskSpaceNotLowConstraint | Sufficient storage |
| SealedSenderConstraint | SealedSenderConstraint | Sealed sender certs available |
| NetworkOrCellServiceConstraint | NetworkOrCellServiceConstraint | Network OR cell service |
| ChangeNumberConstraint | ChangeNumberConstraint | No number change in progress |
| DataRestoreConstraint | DataRestoreConstraint | Data restore complete |
| RestoreAttachmentConstraint | RestoreAttachmentConstraint | Restore system ready |
| BackupMessagesConstraint | BackupMessagesConstraint | Backup system ready |
| DecryptionsDrainedConstraint | DecryptionsDrainedConstraint | Decrypt queue drained |
| StickersNotDownloadingConstraint | StickersNotDownloadingConstraint | No sticker downloads active |
| AutoDownloadEmojiConstraint | AutoDownloadEmojiConstraint | Emoji auto-download permitted |
| DeletionNotAwaitingMediaDownloadConstraint | DeletionNotAwaitingMediaDownloadConstraint | No pending media downloads |
| NoRemoteArchiveGarbageCollectionPendingConstraint | NoRemoteArchiveGarbageCollectionPendingConstraint | No GC pending |

### 3.4 Deliverables

- [ ] JobSchedulerScheduler with OS-level constraints
- [ ] AlarmManagerScheduler with exact alarms
- [ ] CompositeScheduler
- [ ] All 18 constraint implementations
- [ ] All constraint observers
- [ ] Constraint evaluation in JobController
- [ ] All tests pass

---

## Phase 4: Migrations & Polish (Week 7-8)

**Goal:** Versioned job migrations, runSynchronously, WakeLock, BootReceiver, comprehensive tests.

### 4.1 JobMigration System

```kotlin
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
        fun withData(data: ByteArray?) = copy(data = data)
    }
}

class JobMigrator(
    private val lastSeenVersion: Int,
    private val currentVersion: Int,
    private val migrations: List<JobMigration>
) {
    init {
        require(migrations.size == currentVersion - 1) {
            "Must have exactly ${currentVersion - 1} migrations, have ${migrations.size}"
        }
    }

    fun migrate(storage: JobStorage): Int {
        for (i in lastSeenVersion until currentVersion) {
            val migration = migrations[i + 1]
            storage.transformJobs { spec ->
                val original = JobData(spec.factoryKey, spec.queueKey, spec.maxAttempts, spec.lifespan, spec.serializedData)
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
```

### 4.2 runSynchronously

```kotlin
fun JobManager.runSynchronously(job: Job, timeoutMs: Long): JobTracker.JobState? {
    val latch = CountDownLatch(1)
    var resultState: JobTracker.JobState? = null

    controller.addTrackerListener(job.id, object : JobTracker.JobListener {
        override fun onStateChanged(jobId: String, state: JobTracker.JobState) {
            if (state == JobTracker.JobState.SUCCESS || state == JobTracker.JobState.FAILURE || state == JobTracker.JobState.CANCELED) {
                controller.removeListener(this)
                resultState = state
                latch.countDown()
            }
        }
    })

    add(job)

    if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) return null
    return resultState
}
```

### 4.3 WakeLock Protection

```kotlin
// In JobRunner.run(job):
val wakeLock = PowerManager.PARTIAL_WAKE_LOCK
val pm = context.getSystemService(PowerManager::class.java)
val lock = pm.newWakeLock(wakeLock, "EnchantJobManager:$name")
lock.acquire(10 * 60 * 1000L) // 10 minute timeout
try {
    val result = job.run()
    // ... handle result
} finally {
    if (lock.isHeld) lock.release()
}
```

### 4.4 BootReceiver

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            getJobManager().wakeUp()
        }
    }
}
```

### 4.5 Deliverables

- [ ] JobMigration abstract class
- [ ] JobMigrator with version validation
- [ ] runSynchronously with timeout
- [ ] WakeLock per job execution
- [ ] BootReceiver
- [ ] 184+ job implementations (or at least the core 20)
- [ ] All tests pass
- [ ] Integration tests with real Room database

---

## Critical Rules (Same as libenchantcrypto/libenchantcall)

1. **NO STUBS, NO PLACEHOLDERS, NO TODOS** — Every function must be complete and working
2. **Update BUILD_PHASES** on every change
3. **Commit and push** after every logical change
4. **Test security** — every test must also test attack vectors
5. **No raw new/delete** — use RAII, smart pointers
6. **All secret material zeroed** — before any free
7. **Structured error codes** — never throw exceptions across FFI boundary
