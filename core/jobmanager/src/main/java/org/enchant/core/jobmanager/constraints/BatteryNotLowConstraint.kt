package org.enchant.core.jobmanager.constraints

import android.content.Context
import android.os.BatteryManager
import org.enchant.core.jobmanager.Constraint

class BatteryNotLowConstraint(private val context: Context) : Constraint {
    override val factoryKey = "BatteryNotLowConstraint"

    override fun isMet(): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return level > 15
    }

    object Factory : Constraint.Factory<BatteryNotLowConstraint> {
        private lateinit var context: Context

        fun initialize(context: Context) {
            this.context = context.applicationContext
        }

        override fun create(): BatteryNotLowConstraint = BatteryNotLowConstraint(context)
    }
}
