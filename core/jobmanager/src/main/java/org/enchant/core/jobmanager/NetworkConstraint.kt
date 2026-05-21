package org.enchant.core.jobmanager

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class NetworkConstraint(private val context: Context) : Constraint {
    override val factoryKey = "NetworkConstraint"

    override fun isMet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    object Factory : Constraint.Factory<NetworkConstraint> {
        private lateinit var context: Context

        fun initialize(context: Context) {
            this.context = context.applicationContext
        }

        override fun create(): NetworkConstraint = NetworkConstraint(context)
    }
}
