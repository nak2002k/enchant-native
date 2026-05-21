package org.enchant.core.jobmanager.constraints

import org.enchant.core.jobmanager.Constraint

class ChangeNumberConstraint : Constraint {
    override val factoryKey = "ChangeNumberConstraint"

    override fun isMet(): Boolean {
        return true
    }

    object Factory : Constraint.Factory<ChangeNumberConstraint> {
        override fun create(): ChangeNumberConstraint = ChangeNumberConstraint()
    }
}
