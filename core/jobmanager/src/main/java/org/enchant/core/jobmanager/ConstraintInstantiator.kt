package org.enchant.core.jobmanager

internal class ConstraintInstantiator(
    private val factories: Map<String, Constraint.Factory<out Constraint>>
) {
    fun instantiate(factoryKey: String): Constraint {
        val factory = factories[factoryKey]
            ?: throw IllegalStateException("No constraint factory for $factoryKey")
        return factory.create()
    }
}
