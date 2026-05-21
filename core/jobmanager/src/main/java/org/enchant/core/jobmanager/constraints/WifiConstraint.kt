package org.enchant.core.jobmanager.constraints

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import org.enchant.core.jobmanager.Constraint

class WifiConstraint(private val context: Context) : Constraint {
    override val factoryKey = "WifiConstraint"

    override fun isMet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    object Factory : Constraint.Factory<WifiConstraint> {
        private lateinit var context: Context

        fun initialize(context: Context) {
            this.context = context.applicationContext
        }

        override fun create(): WifiConstraint = WifiConstraint(context)
    }
}
