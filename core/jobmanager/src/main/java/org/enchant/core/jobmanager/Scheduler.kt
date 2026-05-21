package org.enchant.core.jobmanager

interface Scheduler {
    fun schedule(delayMs: Long, constraints: List<Constraint>)
}
