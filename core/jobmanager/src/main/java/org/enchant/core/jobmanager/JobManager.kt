package org.enchant.core.jobmanager

import android.content.Context
import android.os.Build
import java.util.concurrent.CopyOnWriteArrayList

object JobManager {
    private lateinit var internalController: JobController
    private lateinit var storage: JobStorage
    private lateinit var scheduler: Scheduler
    private lateinit var instantiator: JobInstantiator
    private lateinit var constraintInstantiator: ConstraintInstantiator
    private val emptyQueueListeners = CopyOnWriteArrayList<EmptyQueueListener>()
    private val registeredObservers = CopyOnWriteArrayList<ConstraintObserver>()

    fun add(job: Job) {
        ensureInitialized()
        internalController.submitNewJobChain(listOf(listOf(job)))
    }

    fun startChain(firstJob: Job): JobChain {
        ensureInitialized()
        return JobChain.create(this, firstJob)
    }

    fun startChain(firstJobs: List<Job>): JobChain {
        ensureInitialized()
        return JobChain.create(this, firstJobs)
    }

    fun cancel(jobId: String) {
        ensureInitialized()
        internalController.cancelJob(jobId)
    }

    fun cancelAll() {
        ensureInitialized()
        internalController.cancelAll()
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

    fun shutdown() {
        if (!::internalController.isInitialized) return
        for (observer in registeredObservers) {
            observer.unregister()
        }
        registeredObservers.clear()
        emptyQueueListeners.clear()
    }

    internal fun wakeUp() {
        ensureInitialized()
        internalController.wakeUp()
    }

    internal val controller: JobController
        get() = internalController

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
            CompositeScheduler(
                listOf(
                    InAppScheduler(this),
                    JobSchedulerScheduler(context)
                )
            )
        }

        internalController = JobController(
            context = context,
            storage = storage,
            scheduler = scheduler,
            instantiator = instantiator,
            constraintInstantiator = constraintInstantiator,
            jobManager = this,
            config = config
        )

        internalController.init()
        registeredObservers.clear()
        registeredObservers.addAll(internalController.getRegisteredObservers())
        internalController.startJobRunners()
    }

    private fun ensureInitialized() {
        if (!::internalController.isInitialized) {
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
