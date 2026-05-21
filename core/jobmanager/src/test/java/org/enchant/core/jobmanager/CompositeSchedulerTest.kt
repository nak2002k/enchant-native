package org.enchant.core.jobmanager

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CompositeSchedulerTest {
    @Test
    fun `schedule delegates to all schedulers`() {
        var callCount1 = 0
        var callCount2 = 0
        val scheduler1 = object : Scheduler {
            override fun schedule(delayMs: Long, constraints: List<Constraint>) { callCount1++ }
        }
        val scheduler2 = object : Scheduler {
            override fun schedule(delayMs: Long, constraints: List<Constraint>) { callCount2++ }
        }
        val composite = CompositeScheduler(listOf(scheduler1, scheduler2))
        composite.schedule(1000, emptyList())
        assertEquals(1, callCount1)
        assertEquals(1, callCount2)
    }

    @Test
    fun `schedule with empty list does nothing`() {
        val composite = CompositeScheduler(emptyList())
        composite.schedule(1000, emptyList())
        assertTrue(true)
    }

    @Test
    fun `schedule passes correct parameters`() {
        var receivedDelay: Long = -1
        var receivedConstraints: List<Constraint>? = null
        val scheduler = object : Scheduler {
            override fun schedule(delayMs: Long, constraints: List<Constraint>) {
                receivedDelay = delayMs
                receivedConstraints = constraints
            }
        }
        val composite = CompositeScheduler(listOf(scheduler))
        val constraint = object : Constraint {
            override val factoryKey = "TestConstraint"
            override fun isMet(): Boolean = true
        }
        composite.schedule(5000, listOf(constraint))
        assertEquals(5000, receivedDelay)
        assertEquals(1, receivedConstraints?.size)
    }
}
