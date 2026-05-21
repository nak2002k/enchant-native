package org.enchant.core.jobmanager.constraints

import android.content.Context
import android.telephony.TelephonyManager
import org.enchant.core.jobmanager.Constraint

class NotInCallConstraint(private val context: Context) : Constraint {
    override val factoryKey = "NotInCallConstraint"

    override fun isMet(): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return tm.callState == TelephonyManager.CALL_STATE_IDLE
    }

    object Factory : Constraint.Factory<NotInCallConstraint> {
        private lateinit var context: Context

        fun initialize(context: Context) {
            this.context = context.applicationContext
        }

        override fun create(): NotInCallConstraint = NotInCallConstraint(context)
    }
}
