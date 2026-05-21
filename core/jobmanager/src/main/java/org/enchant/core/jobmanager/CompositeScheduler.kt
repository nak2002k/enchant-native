package org.enchant.core.jobmanager

internal class CompositeScheduler(private val schedulers: List<Scheduler>) : Scheduler {
    override fun schedule(delayMs: Long, constraints: List<Constraint>) {
        for (scheduler in schedulers) {
            scheduler.schedule(delayMs, constraints)
        }
    }
}
