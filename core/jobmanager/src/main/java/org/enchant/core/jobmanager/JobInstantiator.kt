package org.enchant.core.jobmanager

internal class JobInstantiator(
    private val factories: Map<String, Job.Factory<out Job>>
) {
    fun getFactory(factoryKey: String): Job.Factory<out Job>? = factories[factoryKey]

    fun hasFactory(factoryKey: String): Boolean = factories.containsKey(factoryKey)
}
