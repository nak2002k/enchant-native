package org.enchant.core.jobmanager.constraints

import org.enchant.core.jobmanager.Constraint

class VeilSenderConstraint : Constraint {
    override val factoryKey = "VeilSenderConstraint"

    override fun isMet(): Boolean {
        return true
    }

    object Factory : Constraint.Factory<VeilSenderConstraint> {
        override fun create(): VeilSenderConstraint = VeilSenderConstraint()
    }
}
