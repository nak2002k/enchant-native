package org.enchant.core.jobmanager.constraints

import android.content.Context
import android.os.BatteryManager
import org.enchant.core.jobmanager.Constraint

class ChargingConstraint(private val context: Context) : Constraint {
    override val factoryKey = "ChargingConstraint"

    override fun isMet(): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
               status == BatteryManager.BATTERY_STATUS_FULL
    }

    object Factory : Constraint.Factory<ChargingConstraint> {
        private lateinit var context: Context

        fun initialize(context: Context) {
            this.context = context.applicationContext
        }

        override fun create(): ChargingConstraint = ChargingConstraint(context)
    }
}
