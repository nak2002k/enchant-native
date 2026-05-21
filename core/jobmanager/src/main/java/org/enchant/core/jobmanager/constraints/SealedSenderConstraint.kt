package org.enchant.core.jobmanager.constraints

import org.enchant.core.jobmanager.Constraint

class SealedSenderConstraint : Constraint {
    override val factoryKey = "SealedSenderConstraint"

    override fun isMet(): Boolean {
        return true
    }

    object Factory : Constraint.Factory<SealedSenderConstraint> {
        override fun create(): SealedSenderConstraint = SealedSenderConstraint()
    }
}
