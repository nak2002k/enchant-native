package org.enchant.core.jobmanager

interface ConstraintObserver {
    interface Notifier {
        fun onConstraintMet(reason: String)
    }

    fun register(notifier: Notifier)
    fun unregister()
}
