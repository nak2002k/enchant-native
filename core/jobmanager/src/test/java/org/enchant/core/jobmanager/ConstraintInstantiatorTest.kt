package org.enchant.core.jobmanager

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConstraintInstantiatorTest {
    @Test
    fun `instantiate returns constraint from factory`() {
        val factory = object : Constraint.Factory<TestConstraint> {
            override fun create(): TestConstraint = TestConstraint()
        }
        val instantiator = ConstraintInstantiator(mapOf("TestConstraint" to factory))
        val constraint = instantiator.instantiate("TestConstraint")
        assertNotNull(constraint)
        assertEquals("TestConstraint", constraint.factoryKey)
    }

    @Test
    fun `instantiate throws for unknown factory key`() {
        val instantiator = ConstraintInstantiator(emptyMap())
        assertThrows(IllegalStateException::class.java) {
            instantiator.instantiate("UnknownConstraint")
        }
    }

    @Test
    fun `each call creates new instance`() {
        var createCount = 0
        val factory = object : Constraint.Factory<TestConstraint> {
            override fun create(): TestConstraint {
                createCount++
                return TestConstraint()
            }
        }
        val instantiator = ConstraintInstantiator(mapOf("TestConstraint" to factory))
        instantiator.instantiate("TestConstraint")
        instantiator.instantiate("TestConstraint")
        assertEquals(2, createCount)
    }

    private class TestConstraint : Constraint {
        override val factoryKey = "TestConstraint"
        override fun isMet(): Boolean = true
    }
}
