package org.enchant.core.jobmanager.constraints

import org.enchant.core.jobmanager.Constraint

class RegisteredConstraint : Constraint {
    override val factoryKey = "RegisteredConstraint"

    override fun isMet(): Boolean {
        return true
    }

    object Factory : Constraint.Factory<RegisteredConstraint> {
        override fun create(): RegisteredConstraint = RegisteredConstraint()
    }
}
