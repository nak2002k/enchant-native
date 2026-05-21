package org.enchant.core.jobmanager

interface Constraint {
    fun isMet(): Boolean
    val factoryKey: String

    interface Factory<T : Constraint> {
        fun create(): T
    }
}
