package org.enchant.core.jobmanager.constraints

import android.content.Context
import android.os.StatFs
import org.enchant.core.jobmanager.Constraint

class DiskSpaceNotLowConstraint(private val context: Context) : Constraint {
    override val factoryKey = "DiskSpaceNotLowConstraint"

    override fun isMet(): Boolean {
        val path = context.filesDir.absolutePath
        val stat = StatFs(path)
        val availableBytes = stat.availableBytes
        val minimumBytes = 100L * 1024 * 1024
        return availableBytes > minimumBytes
    }

    object Factory : Constraint.Factory<DiskSpaceNotLowConstraint> {
        private lateinit var context: Context

        fun initialize(context: Context) {
            this.context = context.applicationContext
        }

        override fun create(): DiskSpaceNotLowConstraint = DiskSpaceNotLowConstraint(context)
    }
}
