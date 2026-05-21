package org.enchant.core.jobmanager.constraints

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import org.enchant.core.jobmanager.Constraint

class NetworkOrCellServiceConstraint(private val context: Context) : Constraint {
    override val factoryKey = "NetworkOrCellServiceConstraint"

    override fun isMet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        if (network != null) {
            val caps = cm.getNetworkCapabilities(network)
            if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) return true
        }
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return tm.dataState == TelephonyManager.DATA_CONNECTED
    }

    object Factory : Constraint.Factory<NetworkOrCellServiceConstraint> {
        private lateinit var context: Context

        fun initialize(context: Context) {
            this.context = context.applicationContext
        }

        override fun create(): NetworkOrCellServiceConstraint = NetworkOrCellServiceConstraint(context)
    }
}
